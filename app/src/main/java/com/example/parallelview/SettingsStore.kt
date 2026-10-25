/*
 * 信号猎手 (Signal Hunter)
 * Copyright (C) 2026
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.example.parallelview

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 参数设置持久化（SharedPreferences）。
 * 主题相关字段使用 Compose State，修改后立即重组并同步落盘。
 */
class SettingsStore(context: Context) {
    private val prefs =
        context.getSharedPreferences("btlocator_settings", Context.MODE_PRIVATE)

    /** 1 米处参考 RSSI (dBm) */
    var ref1m: Double
        get() = prefs.getFloat("ref1m", -59f).toDouble()
        set(v) = prefs.edit().putFloat("ref1m", v.toFloat()).apply()

    /** 环境路径损耗指数 n */
    var pathLoss: Double
        get() = prefs.getFloat("pathLoss", 2.5f).toDouble()
        set(v) = prefs.edit().putFloat("pathLoss", v.toFloat()).apply()

    /** 平滑系数 alpha (0~1) */
    var alpha: Double
        get() = prefs.getFloat("alpha", 0.3f).toDouble()
        set(v) = prefs.edit().putFloat("alpha", v.toFloat()).apply()

    /** 趋势判定阈值 (dBm/s) */
    var trendThreshold: Double
        get() = prefs.getFloat("trendThreshold", 0.25f).toDouble()
        set(v) = prefs.edit().putFloat("trendThreshold", v.toFloat()).apply()

    // ------------------------- 外观 / 主题 -------------------------

    private var themeModeState by mutableStateOf(
        ThemeMode.fromName(prefs.getString("themeMode", ThemeMode.SYSTEM.name))
    )

    var themeMode: ThemeMode
        get() = themeModeState
        set(value) {
            themeModeState = value
            prefs.edit().putString("themeMode", value.name).apply()
        }

    private var uiStyleState by mutableStateOf(
        UiStyle.fromName(prefs.getString("uiStyle", UiStyle.MATERIAL.name))
    )

    var uiStyle: UiStyle
        get() = uiStyleState
        set(value) {
            uiStyleState = value
            prefs.edit().putString("uiStyle", value.name).apply()
        }

    /** MD3 Expressive 自定义取色开关。 */
    var customColorEnabled by mutableStateOf(prefs.getBoolean("customColor", false))

    fun setCustomColor(value: Boolean) {
        customColorEnabled = value
        prefs.edit().putBoolean("customColor", value).apply()
    }

    /** Android 12+ 壁纸动态取色（Material You）。开启时优先于自定义色相。 */
    var dynamicColor by mutableStateOf(prefs.getBoolean("dynamicColor", true))

    fun setDynamicColors(value: Boolean) {
        dynamicColor = value
        prefs.edit().putBoolean("dynamicColor", value).apply()
    }

    /** 自定义色相 0..360。 */
    var seedHue by mutableStateOf(prefs.getFloat("seedHue", 123f))

    fun setHue(value: Float) {
        seedHue = value.coerceIn(0f, 360f)
        prefs.edit().putFloat("seedHue", seedHue).apply()
    }

    /** 自定义饱和度 0..1。 */
    var seedSaturation by mutableStateOf(prefs.getFloat("seedSaturation", 0.58f))

    fun setSaturation(value: Float) {
        seedSaturation = value.coerceIn(0.25f, 0.9f)
        prefs.edit().putFloat("seedSaturation", seedSaturation).apply()
    }

    /** MiuiX 悬浮底栏是否启用液态玻璃模糊。 */
    var liquidGlass by mutableStateOf(prefs.getBoolean("liquidGlass", true))

    fun setGlass(value: Boolean) {
        liquidGlass = value
        prefs.edit().putBoolean("liquidGlass", value).apply()
    }

    /** 悬浮底栏是否显示文字标签。 */
    var showNavLabels by mutableStateOf(prefs.getBoolean("showNavLabels", true))

    fun setNavLabels(value: Boolean) {
        showNavLabels = value
        prefs.edit().putBoolean("showNavLabels", value).apply()
    }
}
