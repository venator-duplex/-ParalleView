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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.Card as M3Card
import androidx.compose.material3.HorizontalDivider as M3Divider
import androidx.compose.material3.LocalContentColor as M3ContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton as M3OutlinedButton
import androidx.compose.material3.Slider as M3Slider
import androidx.compose.material3.Text as M3Text
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixDivider
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 跨设计语言的文字组件。两种模式下保持同一套排版参数，
 * 但分别使用 Material 与 Miuix 的 Text 实现和内容色。
 */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixText(
            text = text,
            modifier = modifier,
            color = if (color == Color.Unspecified) MiuixContentColor.current else color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            overflow = overflow,
            maxLines = maxLines,
            style = style
        )
    } else {
        M3Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            overflow = overflow,
            maxLines = maxLines,
            style = style
        )
    }
}

/** 跨设计语言的卡片。儿童/扫描列表都使用它，避免深色模式下写死白底。 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    contentColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) {
    val miuixStyle = LocalUiStyle.current == UiStyle.MIUIX
    val framed = modifier
    if (miuixStyle) {
        MiuixCard(
            modifier = framed,
            /* Keep Miuix's default insideMargin / corner radius. Overriding them
             * with 0.dp made AppCard look different from the plain Card() used on
             * the appearance screens - that was the colour/padding mismatch. */
            cornerRadius = MiuixCardDefaults.CornerRadius,
            colors = if (containerColor == androidx.compose.ui.graphics.Color.Unspecified) {
                MiuixCardDefaults.defaultColors()
            } else {
                MiuixCardDefaults.defaultColors(
                    containerColor,
                    if (contentColor == androidx.compose.ui.graphics.Color.Unspecified) {
                        androidx.compose.ui.graphics.Color.White
                    } else contentColor
                )
            },
            onClick = onClick,
            content = content
        )
    } else {
        val colors = if (containerColor == androidx.compose.ui.graphics.Color.Unspecified) {
            androidx.compose.material3.CardDefaults.cardColors()
        } else {
            androidx.compose.material3.CardDefaults.cardColors(containerColor = containerColor)
        }
        if (onClick != null) {
            M3Card(onClick = onClick, modifier = framed, colors = colors) { content() }
        } else {
            M3Card(modifier = framed, colors = colors) { content() }
        }
    }
}

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = MiuixButtonDefaults.buttonColorsPrimary(),
            content = content
        )
    } else {
        M3Button(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    }
}

@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixButton(
            onClick = onClick,
            modifier = modifier.border(
                BorderStroke(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.5f)),
                RoundedCornerShape(16.dp)
            ),
            enabled = enabled,
            colors = MiuixButtonDefaults.buttonColors(
                color = Color.Transparent,
                contentColor = MiuixTheme.colorScheme.primary
            ),
            content = content
        )
    } else {
        M3OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    }
}

@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps
        )
    } else {
        M3Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
fun AppDivider(modifier: Modifier = Modifier, thickness: Dp = 1.dp) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixDivider(modifier = modifier, thickness = thickness)
    } else {
        M3Divider(modifier = modifier, thickness = thickness)
    }
}

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        top.yukonga.miuix.kmp.basic.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled
        )
    } else {
        /* Material 3 "switch with icon" - the official API: the thumb shows a
         * check when on and a cross when off. Doing it here keeps EVERY switch in
         * the app consistent instead of only the ones on the theme screen. */
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            thumbContent = {
                androidx.compose.material3.Icon(
                    imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = androidx.compose.ui.Modifier.size(
                        androidx.compose.material3.SwitchDefaults.IconSize)
                )
            }
        )
    }
}
