package com.kj2431.pielauncher.model

import android.graphics.drawable.Drawable

/**
 * One slice of the pie.
 * [action]/[icon]/[label] are the short-press face. When a long-press is armed
 * the slice swaps to [longLabel]/[longIcon] and runs [longAction] on release.
 */
data class PieItem(
    val label: String,
    val icon: Drawable? = null,
    val longLabel: String? = null,
    val longIcon: Drawable? = null,
    val longAction: (() -> Unit)? = null,
    val action: () -> Unit
)
