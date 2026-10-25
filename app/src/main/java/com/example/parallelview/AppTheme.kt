/*
 * 信号猎手 (Signal Hunter)
 * Copyright (C) 2026
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.example.parallelview

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor as M3ContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import kotlin.math.abs

/** 用户可选的明暗模式。自动 = 跟随系统（Android 12+ 也跟随系统深色计划）。 */
enum class ThemeMode(val label: String) {
    SYSTEM("自动（跟随系统）"),
    LIGHT("浅色"),
    DARK("深色");

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** 两套并存的设计语言。 */
enum class UiStyle(val label: String, val description: String) {
    MATERIAL("Material You", "MD3 Expressive · 可自定义取色"),
    MIUIX("Miuix", "小米澎湃风格 · Apple 液态玻璃悬浮底栏");

    companion object {
        fun fromName(name: String?): UiStyle =
            entries.firstOrNull { it.name == name } ?: MATERIAL
    }
}

val LocalUiStyle = staticCompositionLocalOf { UiStyle.MATERIAL }

/** 悬浮底栏在 MiuiX 模式下占用的内容底部空间。 */
val LocalFloatingBottomInset = staticCompositionLocalOf { 0.dp }

/** Material 3 Expressive 的圆角体系：更大、更柔和的容器。 */
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

/**
 * 根据用户选择的色相/饱和度生成一套完整的 MD3 色彩角色。
 * 不依赖壁纸，因此低版本 Android 与纯本地取色都能保持一致。
 */
fun expressiveColorScheme(dark: Boolean, hue: Float, saturation: Float): ColorScheme {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0.25f, 0.90f)
    fun c(lightness: Float, satScale: Float = 1f, hueShift: Float = 0f): Color =
        Color.hsl(((h + hueShift) % 360f + 360f) % 360f, (s * satScale).coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))

    return if (dark) {
        darkColorScheme(
            primary = c(0.80f, 0.82f),
            onPrimary = c(0.19f, 0.90f),
            primaryContainer = c(0.31f, 0.72f),
            onPrimaryContainer = c(0.92f, 0.55f),
            inversePrimary = c(0.40f, 0.85f),
            secondary = c(0.78f, 0.42f, 28f),
            onSecondary = c(0.18f, 0.55f, 28f),
            secondaryContainer = c(0.30f, 0.42f, 28f),
            onSecondaryContainer = c(0.91f, 0.38f, 28f),
            tertiary = c(0.79f, 0.55f, -55f),
            onTertiary = c(0.19f, 0.65f, -55f),
            tertiaryContainer = c(0.31f, 0.55f, -55f),
            onTertiaryContainer = c(0.92f, 0.48f, -55f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            background = c(0.085f, 0.12f),
            onBackground = c(0.92f, 0.08f),
            surface = c(0.095f, 0.10f),
            onSurface = c(0.93f, 0.06f),
            surfaceVariant = c(0.22f, 0.14f),
            onSurfaceVariant = c(0.80f, 0.14f),
            surfaceTint = c(0.80f, 0.82f),
            inverseSurface = c(0.92f, 0.07f),
            inverseOnSurface = c(0.20f, 0.10f),
            outline = c(0.60f, 0.15f),
            outlineVariant = c(0.31f, 0.12f),
            scrim = Color.Black,
            surfaceBright = c(0.24f, 0.11f),
            surfaceDim = c(0.075f, 0.11f),
            surfaceContainerLowest = c(0.055f, 0.10f),
            surfaceContainerLow = c(0.11f, 0.11f),
            surfaceContainer = c(0.145f, 0.11f),
            surfaceContainerHigh = c(0.185f, 0.12f),
            surfaceContainerHighest = c(0.235f, 0.13f)
        )
    } else {
        lightColorScheme(
            primary = c(0.38f, 0.82f),
            onPrimary = Color.White,
            primaryContainer = c(0.91f, 0.72f),
            onPrimaryContainer = c(0.19f, 0.90f),
            inversePrimary = c(0.79f, 0.82f),
            secondary = c(0.43f, 0.42f, 28f),
            onSecondary = Color.White,
            secondaryContainer = c(0.93f, 0.52f, 28f),
            onSecondaryContainer = c(0.22f, 0.52f, 28f),
            tertiary = c(0.43f, 0.55f, -55f),
            onTertiary = Color.White,
            tertiaryContainer = c(0.93f, 0.62f, -55f),
            onTertiaryContainer = c(0.20f, 0.65f, -55f),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            background = c(0.985f, 0.16f),
            onBackground = c(0.12f, 0.14f),
            surface = c(0.985f, 0.16f),
            onSurface = c(0.12f, 0.14f),
            surfaceVariant = c(0.93f, 0.18f),
            onSurfaceVariant = c(0.36f, 0.16f),
            surfaceTint = c(0.38f, 0.82f),
            inverseSurface = c(0.20f, 0.12f),
            inverseOnSurface = c(0.96f, 0.12f),
            outline = c(0.53f, 0.12f),
            outlineVariant = c(0.83f, 0.14f),
            scrim = Color.Black,
            surfaceBright = c(0.99f, 0.12f),
            surfaceDim = c(0.89f, 0.14f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = c(0.975f, 0.16f),
            surfaceContainer = c(0.955f, 0.17f),
            surfaceContainerHigh = c(0.925f, 0.18f),
            surfaceContainerHighest = c(0.895f, 0.19f)
        )
    }
}

/** 深色纯黑背景（AMOLED）用于 MiuiX 模式，保持与系统深色一致。 */
fun isNight(settings: SettingsStore, systemDark: Boolean): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun SignalHunterTheme(settings: SettingsStore, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = isNight(settings, systemDark)
    val seedHue = settings.seedHue
    val seedSat = settings.seedSaturation

    val materialScheme = remember(dark, settings.dynamicColor, settings.customColorEnabled, seedHue, seedSat) {
        when {
            settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            settings.customColorEnabled -> expressiveColorScheme(dark, seedHue, seedSat)
            else -> if (dark) expressiveColorScheme(true, 123f, 0.54f)
            else expressiveColorScheme(false, 123f, 0.54f)
        }
    }

    // Miuix 主题使用其原生固定配色（Light / Dark），不做莫奈（Monet）取色：
    // 避免 Miuix 风格下仍随种子色 / 壁纸动态改变色调。
    val miuixController = remember(dark) {
        ThemeController(
            colorSchemeMode = if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
            colorSpec = ThemeColorSpec.Spec2025,
            paletteStyle = ThemePaletteStyle.Expressive
        )
    }

    MaterialTheme(
        colorScheme = materialScheme,
        shapes = ExpressiveShapes,
        typography = Typography(),
    ) {
        CompositionLocalProvider(
            LocalUiStyle provides settings.uiStyle,
            // MaterialTheme 自身不提供 LocalContentColor；详情页在 Scaffold 之外，
            // 这里补上 onBackground，避免深色模式正文变黑。
            M3ContentColor provides materialScheme.onBackground
        ) {
            if (settings.uiStyle == UiStyle.MIUIX) {
                MiuixTheme(controller = miuixController) {
                    // miuix-ui 0.9.0 的 MiuixTheme 未提供 LocalContentColor（0.9.4 才加入），
                    // 这里按 onBackground 补齐，避免深色模式正文默认变黑。
                    CompositionLocalProvider(
                        MiuixContentColor provides MiuixTheme.colorScheme.onBackground
                    ) {
                        content()
                    }
                }
            } else {
                // Material 模式不注入 Miuix 的 LocalIndication / LocalContentColor，
                // 保证两套设计语言互不污染。
                content()
            }
        }
    }
}

/** 简单的对比度辅助：给主色选择合适的文字色。 */
fun contrastingOn(color: Color): Color {
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return if (luminance > 0.58f) Color(0xFF1A1C19) else Color.White
}

/** 给色相环选择器用的标准色相颜色。 */
fun hueColor(hue: Float): Color = Color.hsl(((hue % 360f) + 360f) % 360f, 0.72f, 0.52f)

/** 色相与基准色差值（仅用于文案展示）。 */
fun hueDistance(a: Float, b: Float): Float {
    val d = abs(a - b) % 360f
    return if (d > 180f) 360f - d else d
}
