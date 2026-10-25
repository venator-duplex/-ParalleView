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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 跨设计语言的调色板。
 *
 * Miuix 模式下映射到 [MiuixTheme.colorScheme]（对齐 KernelSU 管理端使用的
 * 澎湃 OS 色彩角色：正文摘要用 onSurfaceVariantSummary、分隔线用 dividerLine、
 * 卡片容器用 surfaceContainer 系列）；Material 模式下映射到 MaterialTheme。
 */
@Immutable
class AppPalette(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val primaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val onBackgroundVariant: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainerHigh: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
    val tertiary: Color,
)

@Composable
fun appPalette(): AppPalette =
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        val c = MiuixTheme.colorScheme
        AppPalette(
            primary = c.primary,
            onPrimary = c.onPrimary,
            secondary = c.secondary,
            primaryContainer = c.primaryContainer,
            background = c.background,
            onBackground = c.onBackground,
            onBackgroundVariant = c.onBackgroundVariant,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceVariant,
            onSurfaceVariant = c.onSurfaceVariantSummary,
            surfaceContainerHigh = c.surfaceContainerHigh,
            outline = c.outline,
            outlineVariant = c.dividerLine,
            error = c.error,
            tertiary = c.secondary,
        )
    } else {
        val c = MaterialTheme.colorScheme
        AppPalette(
            primary = c.primary,
            onPrimary = c.onPrimary,
            secondary = c.secondary,
            primaryContainer = c.primaryContainer,
            background = c.background,
            onBackground = c.onBackground,
            onBackgroundVariant = c.onSurfaceVariant,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceVariant,
            onSurfaceVariant = c.onSurfaceVariant,
            surfaceContainerHigh = c.surfaceContainerHigh,
            outline = c.outline,
            outlineVariant = c.outlineVariant,
            error = c.error,
            tertiary = c.tertiary,
        )
    }

/** 跨设计语言的排版。Miuix 模式使用澎湃 OS 字号体系（body1=16 / body2=14 / 标题 24）。 */
@Immutable
class AppType(
    val headline: TextStyle,
    val sectionTitle: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val label: TextStyle,
)

@Composable
fun appType(): AppType =
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        val t = MiuixTheme.textStyles
        AppType(
            headline = t.title2.copy(fontWeight = FontWeight.Bold),
            sectionTitle = t.subtitle,
            body = t.body1,
            caption = t.body2,
            label = t.footnote1,
        )
    } else {
        val t = MaterialTheme.typography
        AppType(
            headline = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
            sectionTitle = t.titleMedium.copy(fontWeight = FontWeight.Bold),
            body = t.bodyMedium,
            caption = t.bodySmall,
            label = t.labelSmall,
        )
    }

/**
 * 页面大标题（Miuix 模式下参考 KernelSU 管理端的页面头部：
 * 24sp 粗体标题 + 14sp 摘要）。Material 模式沿用原紧凑样式。
 */
@Composable
fun AppPageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val type = appType()
    val palette = appPalette()
    Column(modifier = modifier.fillMaxWidth()) {
        AppText(title, style = type.headline)
        Spacer(Modifier.height(2.dp))
        AppText(
            subtitle,
            style = type.caption,
            color = palette.onSurfaceVariant,
        )
    }
}

/** 分区小标题。Miuix 模式对齐 miuix SmallTitle（14sp 粗体、次要色、12dp 起始缩进）。 */
@Composable
fun AppSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        AppText(
            text,
            modifier = modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
            style = MiuixTheme.textStyles.subtitle,
            color = color ?: MiuixTheme.colorScheme.onBackgroundVariant,
        )
    } else {
        AppText(
            text,
            modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color ?: MaterialTheme.colorScheme.primary,
        )
    }
}

/** Miuix 模式下为可滚动容器加上澎湃 OS 风格的回弹（overscroll）效果。 */
@Composable
fun Modifier.appOverScroll(enabled: Boolean = LocalUiStyle.current == UiStyle.MIUIX): Modifier =
    if (enabled) this.then(Modifier.overScrollVertical()) else this

/** 分区标题 + 副标题（设置页用）。 */
@Composable
fun AppSectionTitle(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val type = appType()
    val palette = appPalette()
    Column(modifier = modifier.fillMaxWidth()) {
        AppText(title, style = type.sectionTitle)
        Spacer(Modifier.height(2.dp))
        AppText(
            subtitle,
            style = type.caption,
            color = palette.onSurfaceVariant,
        )
    }
}

/** 设置行右侧开关 / 摘要的通用行容器（保持两种模式一致的间距节奏）。 */
@Composable
fun AppRowContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        content = { content() },
    )
}

/** 卡片内部分隔线（Miuix 使用 dividerLine，Material 使用 outlineVariant）。 */
@Composable
fun AppCardDivider(modifier: Modifier = Modifier, startPad: Dp = 14.dp) {
    Spacer(modifier = modifier.height(10.dp))
    AppDivider(modifier = Modifier.padding(horizontal = 0.dp), thickness = 1.dp)
    Spacer(modifier = Modifier.height(10.dp))
}

/** 统一的内容水平内边距（Miuix 页面 12dp，Material 页面 12dp，留作扩展）。 */
val AppPagePadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
