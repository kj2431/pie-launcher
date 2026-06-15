package com.kj2431.pielauncher.root

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import kotlin.concurrent.thread

/**
 * Holds ONE persistent `su` shell for the whole process lifetime and runs every
 * command inside it. This means the superuser manager prompts/grants root only
 * once (at [prewarm], i.e. launcher activation) instead of on every command —
 * no repeated "granted Superuser rights" popups.
 *
 * All shell access is serialized through [lock]; root work is already off the
 * main thread (see ActionRunner), so blocking reads here are safe.
 */
object RootHelper {

    private const val TAG = "RootHelper"

    private val lock = Any()
    private var process: Process? = null
    private var stdin: DataOutputStream? = null
    private var stdout: BufferedReader? = null

    @Volatile private var available: Boolean? = null

    /** Open the shell ahead of time (e.g. when the service starts). */
    fun prewarm() = thread(name = "root-prewarm") { isAvailable() }

    /** True if a root shell is (or can be) opened. Result cached after first call. */
    fun isAvailable(): Boolean {
        available?.let { if (it && alive()) return true }
        synchronized(lock) {
            val ok = ensureOpen()
            available = ok
            return ok
        }
    }

    /** Run one or more commands in the persistent shell. Returns last exit == 0. */
    fun exec(vararg commands: String): Boolean = synchronized(lock) {
        if (!ensureOpen()) return false
        val os = stdin ?: return false
        val reader = stdout ?: return false
        val marker = "PIE_DONE_" + System.nanoTime()
        return try {
            commands.forEach { os.writeBytes(it + "\n") }
            os.writeBytes("echo $marker $?\n")
            os.flush()
            var success = false
            while (true) {
                val line = reader.readLine() ?: break          // shell died
                if (line.startsWith(marker)) { success = line.trim().endsWith(" 0"); break }
            }
            success
        } catch (e: Exception) {
            Log.w(TAG, "exec failed; closing shell", e); closeLocked(); false
        }
    }

    fun pressKey(keyevent: Int) = exec("input keyevent $keyevent")

    /** Close the shell (e.g. on service destroy). */
    fun shutdown() = synchronized(lock) { closeLocked(); available = null }

    // ---- internals (must hold lock) ----------------------------------------
    private fun alive(): Boolean = process?.isAlive == true

    private fun ensureOpen(): Boolean {
        if (alive()) return true
        closeLocked()
        return try {
            val p = ProcessBuilder("su").redirectErrorStream(true).start()
            val os = DataOutputStream(p.outputStream)
            val rd = BufferedReader(InputStreamReader(p.inputStream))
            process = p; stdin = os; stdout = rd
            // Verify we truly have root via a marker round-trip.
            val marker = "PIE_READY_" + System.nanoTime()
            os.writeBytes("echo $marker\n"); os.flush()
            var granted = false
            while (true) {
                val line = rd.readLine() ?: break
                if (line.contains(marker)) { granted = true; break }
            }
            if (!granted) { closeLocked(); false } else true
        } catch (e: Exception) {
            Log.w(TAG, "could not open su shell", e); closeLocked(); false
        }
    }

    private fun closeLocked() {
        runCatching { stdin?.writeBytes("exit\n"); stdin?.flush() }
        runCatching { stdin?.close() }
        runCatching { stdout?.close() }
        runCatching { process?.destroy() }
        stdin = null; stdout = null; process = null
    }
}
