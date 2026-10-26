package com.example.parallelview

import android.content.Context
import android.provider.Settings

/** Keys shared with the hook and the UI, both read from Settings.Global. */
object Targets {
    const val KEY_TARGETS = "parallelview_targets"
    const val KEY_RATIO = "parallelview_split_ratio"

    /**
     * Read the ratio from the module's own provider first (works without any
     * permission), then fall back to Settings.Global for older installs.
     */
    fun ratio(context: Context): Float {
        try {
            val b = context.contentResolver.call(
                android.net.Uri.parse("content://com.example.parallelview.config"),
                "ratio", null, null
            )
            val v = b?.getFloat("ratio", -1f) ?: -1f
            if (v > 0f) return v.coerceIn(0.15f, 0.85f)
        } catch (_: Throwable) {
        }
        return try {
            Settings.Global.getFloat(context.contentResolver, KEY_RATIO, 0.38f)
                .coerceIn(0.15f, 0.85f)
        } catch (_: Throwable) {
            0.38f
        }
    }
}
