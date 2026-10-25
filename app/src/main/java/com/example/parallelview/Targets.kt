package com.example.parallelview

import android.content.Context
import android.provider.Settings

/** Keys shared with the hook and the UI, both read from Settings.Global. */
object Targets {
    const val KEY_TARGETS = "parallelview_targets"
    const val KEY_RATIO = "parallelview_split_ratio"

    private val DEFAULTS = setOf("com.bilibili.app.in", "tv.danmaku.bili", "com.coolapk.market")

    fun isParallel(context: Context?, pkg: String): Boolean {
        if (context == null) return DEFAULTS.contains(pkg)
        val raw = try {
            Settings.Global.getString(context.contentResolver, KEY_TARGETS)
        } catch (_: Throwable) {
            null
        } ?: return DEFAULTS.contains(pkg)
        if (raw.isBlank()) return false
        return raw.split(',').map { it.trim() }.contains(pkg)
    }

    fun ratio(context: Context): Float = try {
        Settings.Global.getFloat(context.contentResolver, KEY_RATIO, 0.38f).coerceIn(0.15f, 0.85f)
    } catch (_: Throwable) {
        0.38f
    }
}
