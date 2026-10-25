/*
 * 信号猎手 (Signal Hunter)
 * Copyright (C) 2026
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * 液态玻璃悬浮底栏。视觉与交互结构移植自 KernelSU（ReSukiSU）管理端的
 * FloatingBottomBar（其上游为 compose-miuix-ui 示例 IosLiquidGlassNavigationBar，
 * Apache 2.0），并针对本项目做了：
 * - 颜色全部走 MiuixTheme 色彩体系；
 * - 液滴胶囊与 HorizontalPager 的连续偏移 1:1 同步（页面滑动时胶囊跟手）；
 * - 在底栏上拖动胶囊可实时带动页面滚动；
 * - 无法使用 RuntimeShader / 关闭模糊时自动降级为半透明磨砂面板。
 */

package com.example.parallelview

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.example.parallelview.liquidglass.DampedDragAnimation
import com.example.parallelview.liquidglass.InnerShadow
import com.example.parallelview.liquidglass.InteractiveHighlight
import com.example.parallelview.liquidglass.innerShadow
import com.example.parallelview.liquidglass.lens
import com.example.parallelview.liquidglass.rememberCombinedBackdrop
import com.example.parallelview.liquidglass.vibrancy
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class AppTab(val label: String, val icon: ImageVector)

/** 供 Tab 内容读取当前按压缩放（基础层 1f，胶囊内的强调色副本 1→1.2）。 */
val LocalLiquidTabScale = staticCompositionLocalOf { { 1f } }

// Mirrors miuix-blur HighlightStyle's LIGHT_REF — keep in sync.
private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f
private const val GRAVITY_DIR_THRESHOLD_SQ = 0.01f // |g_xy| > 0.1, ≈ 6° tilt
private const val GRAVITY_ANGLE_STEP_RAD = (3.0 * PI / 180.0).toFloat()

private val iosIndicatorSpecular: Highlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.0.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

/** 跟踪设备重力方向（量化到 3° 一档），双峰高光的"光源"会随握持角度旋转。 */
@Composable
private fun rememberQuantizedGravityAngle(): State<Float> {
    val tiltState = rememberDeviceTilt()
    return remember(tiltState) {
        derivedStateOf {
            val tilt = tiltState.value
            val magnitudeSquared = tilt.gravityX * tilt.gravityX + tilt.gravityY * tilt.gravityY
            if (magnitudeSquared > GRAVITY_DIR_THRESHOLD_SQ) {
                (atan2(tilt.gravityY, tilt.gravityX) / GRAVITY_ANGLE_STEP_RAD).roundToInt() * GRAVITY_ANGLE_STEP_RAD
            } else {
                (-PI / 2).toFloat()
            }
        }
    }
}

@Composable
private fun rememberGravityRotatedHighlight(
    base: Highlight,
    extraDegrees: Float = 0f,
): State<Highlight> {
    val baseStyle = base.style as BloomStroke
    val angle = rememberQuantizedGravityAngle()
    return remember(angle, base, extraDegrees) {
        derivedStateOf {
            val basePrimary = baseStyle.primaryLight
            val rad = angle.value + (extraDegrees * PI / 180.0).toFloat()
            base.copy(
                style = baseStyle.copy(
                    primaryLight = basePrimary.copy(
                        position = LightPosition(
                            x = LIGHT_REF_X + cos(rad),
                            y = LIGHT_REF_Y + sin(rad),
                            z = basePrimary.position.z,
                        ),
                    ),
                ),
            )
        }
    }
}

/**
 * MiuiX 模式下的 Apple 风格悬浮底栏。
 *
 * 视觉效果：
 * - 真实背景模糊 + 色彩增强（vibrancy）+ 边缘折射透镜（miuix-blur / RuntimeShader，Android 13+）；
 * - 随重力旋转的镜面高光（BloomStroke 双峰）；
 * - 液滴胶囊跟随阻尼动画，拖动底栏时与手指 1:1 同步，点选 / 翻页时弹簧对齐；
 * - 按压时玻璃被"压扁"（press scale + 内阴影 + 透镜加深），松手弹簧回弹；
 * - 低版本 / 关闭模糊时自动降级为半透明磨砂胶囊，文字始终可读。
 */
@Composable
fun LiquidGlassNavBar(
    tabs: List<AppTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: LayerBackdrop,
    glassEnabled: Boolean,
    showLabels: Boolean,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    /* `dark` was already a parameter but unused: the bar always took Miuix's
     * surfaceContainer, which is a DARK grey when the Miuix theme did not follow
     * the app's light mode - that is the dark pill seen on a white page. */
    val isLightBar = !dark
    val accentColor = if (isLightBar) Color(0xFF1A5FD0) else colors.primary
    val tabContentColor = if (isLightBar) Color(0xFF1B1B1F) else colors.onSurface
    val surfaceContainer = if (isLightBar) {
        androidx.compose.ui.graphics.Color.White
    } else {
        colors.surfaceContainer
    }
    val isBlurEnabled = glassEnabled && isRuntimeShaderSupported()
    val containerColor =
        if (isBlurEnabled) surfaceContainer.copy(alpha = if (isLightBar) 0.35f else 0.4f)
        else surfaceContainer.copy(alpha = if (isLightBar) 0.88f else 0.96f)
    android.util.Log.d("PVBAR", "dark=" + dark + " light=" + isLightBar +
        " blur=" + isBlurEnabled + " glass=" + glassEnabled +
        " container=" + Integer.toHexString(containerColor.value.toLong().toInt()))
    val pillShape = remember { CircleShape }

    val pageBackdrop: Backdrop = backdrop
    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember { mutableIntStateOf(selected) }
    val onSelectedUpdated by rememberUpdatedState(onSelect)

    fun indexAt(positionX: Float): Int {
        if (tabWidthPx == 0f) return currentIndex
        val horizontalPaddingPx = with(density) { 4.dp.toPx() }
        val logicalX = if (isLtr) positionX else totalWidthPx - positionX
        return ((logicalX - horizontalPaddingPx) / tabWidthPx)
            .toInt()
            .coerceIn(0, tabs.size - 1)
    }

    val dampedDragAnimation = remember(animationScope, tabs.size, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selected.toFloat(),
            valueRange = 0f..(tabs.size - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { offset ->
                offset.x in 0f..totalWidthPx
            },
            onDragStarted = { position ->
                updateValue(indexAt(position.x).toFloat())
            },
            onDragStopped = {
                val targetIndex = targetValue.roundToInt().coerceIn(0, tabs.size - 1)
                if (currentIndex != targetIndex) {
                    currentIndex = targetIndex
                    onSelectedUpdated(targetIndex)
                }
                updateValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDragCancelled = {
                updateValue(currentIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f && dragAmount.x != 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .coerceIn(0f, (tabs.size - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                    // 不在拖动中逐事件驱动 pager：那会抢占滚动锁使胶囊滞后，
                    // 且跨页边界会回灌 selected 与手指打架。内容在 onDragStopped 后对齐。
                }
            },
        )
    }

    // 胶囊位置：始终 1:1 跟随阻尼动画值（与手指同步）。
    // 拖动底栏时由 onDrag 直接更新；滑动内容页 / 点选时由 selected→animateToValue 驱动。
    // 让胶囊读取 pager 连续偏移会因逐事件抢占 pager 滚动锁而滞后，故不采用。
    fun pillValue(): Float = dampedDragAnimation.value

    LaunchedEffect(selected) {
        if (currentIndex != selected) {
            currentIndex = selected
            dampedDragAnimation.animateToValue(selected.toFloat())
        }
    }

    fun activateTab(index: Int) {
        if (index !in 0 until tabs.size) return
        if (currentIndex != index) {
            currentIndex = index
            onSelectedUpdated(index)
        }
        dampedDragAnimation.animateToValue(index.toFloat())
    }

    val interactiveHighlight = remember(animationScope, tabWidthPx, dampedDragAnimation) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { size, _ ->
                Offset(
                    if (isLtr) {
                        (pillValue() + 0.5f) * tabWidthPx + panelOffset
                    } else {
                        size.width - (pillValue() + 0.5f) * tabWidthPx + panelOffset
                    },
                    size.height / 2f,
                )
            },
        )
    }

    val baseHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = -45f)
    val pillHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = 90f)

    val combinedBackdrop = rememberCombinedBackdrop(pageBackdrop, tabsBackdrop)

    val tabRowContent: @Composable RowScope.((Int) -> Unit) -> Unit = { activate ->
        tabs.forEachIndexed { index, tab ->
            LiquidGlassNavBarItem(
                selected = index == selected,
                onClick = { activate(index) },
                tab = tab,
                showLabels = showLabels,
            )
        }
    }

    val currentContent by rememberUpdatedState(tabRowContent)
    // 强调色副本在两层之间保持同一实例（movable），避免重复创建状态。
    val highlightedContent = remember {
        movableContentOf<RowScope, (Int) -> Unit> { rowScope, onActivate ->
            currentContent(rowScope, onActivate)
        }
    }

    // 每帧胶囊位移 → 速度，用于液滴的挤压拉伸形变。
    val lastPillPos = remember { mutableFloatStateOf(-1f) }

    Box(
        modifier = modifier
            .padding(
                start = 20.dp,
                end = 20.dp,
                bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            )
            .width(IntrinsicSize.Min),
        // 竖直居中、水平起始：胶囊以整栏左边缘为基准用 translationX 定位，
        // 若用 Center 会因胶囊窄于整栏而整体右偏（约 1.5 个 tab 宽）。
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                    tabWidthPx = (contentWidthPx / tabs.size).coerceAtLeast(0f)
                }
                .selectableGroup()
                .graphicsLayer { translationX = panelOffset }
                .dropShadow(
                    shape = pillShape,
                    shadow = Shadow(
                        radius = 10.dp,
                        color = Color.Black,
                        alpha = if (dark) 0.2f else 0.1f,
                    ),
                )
                .then(
                    if (isBlurEnabled) {
                        Modifier.drawBackdrop(
                            backdrop = pageBackdrop,
                            shape = { pillShape },
                            effects = {
                                padding = maxOf(padding, 40.dp.toPx())
                                vibrancy()
                                blur(4.dp.toPx(), 4.dp.toPx())
                                lens(
                                    refractionHeight = 24.dp.toPx(),
                                    refractionAmount = 24.dp.toPx(),
                                )
                            },
                            highlight = { baseHighlight.value.copy(alpha = 0.75f) },
                            layerBlock = {
                                val width = size.width.coerceAtLeast(1f)
                                val s = lerp(
                                    1f,
                                    1f + 16.dp.toPx() / width,
                                    dampedDragAnimation.pressProgress,
                                )
                                scaleX = s
                                scaleY = s
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                    } else {
                        Modifier.background(containerColor, pillShape)
                    },
                )
                .then(
                    if (isBlurEnabled) {
                        interactiveHighlight.modifier.then(interactiveHighlight.gestureModifier)
                    } else {
                        Modifier
                    },
                )
                .then(dampedDragAnimation.modifier)
                .height(64.dp)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides tabContentColor) {
                tabRowContent(this, ::activateTab)
            }
        }

        if (isBlurEnabled) {
            CompositionLocalProvider(
                LocalLiquidTabScale provides {
                    lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                },
                LocalContentColor provides accentColor,
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = pageBackdrop,
                            shape = { pillShape },
                            effects = {
                                vibrancy()
                                blur(4.dp.toPx(), 4.dp.toPx())
                                lens(
                                    refractionHeight = 24.dp.toPx(),
                                    refractionAmount = 24.dp.toPx(),
                                )
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .then(interactiveHighlight.modifier)
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = { highlightedContent(this, ::activateTab) },
                )
            }
        }

        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            if (isBlurEnabled) {
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            val progressOffset = pillValue() * tabWidthPx
                            translationX =
                                if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                        }
                        .drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { pillShape },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                lens(
                                    refractionHeight = 10.dp.toPx() * progress,
                                    refractionAmount = 14.dp.toPx() * progress,
                                    depthEffect = true,
                                    chromaticAberration = 0.5f,
                                )
                            },
                            highlight = { pillHighlight.value.copy(alpha = dampedDragAnimation.pressProgress) },
                            layerBlock = {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY
                                val pos = pillValue()
                                val velocity = if (lastPillPos.floatValue < 0f) 0f else pos - lastPillPos.floatValue
                                lastPillPos.floatValue = pos
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                val progress = dampedDragAnimation.pressProgress
                                drawRect(
                                    color = if (!dark) {
                                        Color.Black.copy(alpha = 0.1f)
                                    } else {
                                        Color.White.copy(
                                            alpha = 0.1f,
                                        )
                                    },
                                    alpha = 1f - progress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                        .innerShadow(shape = pillShape) {
                            InnerShadow(
                                radius = 8.dp * dampedDragAnimation.pressProgress,
                                color = Color.Black.copy(alpha = 0.15f),
                                alpha = dampedDragAnimation.pressProgress,
                            )
                        }
                        .height(56.dp)
                        .width(tabWidthDp),
                )
            } else {
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            val progressOffset = pillValue() * tabWidthPx
                            translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                        }
                        .clip(pillShape)
                        .background(accentColor.copy(alpha = 0.15f), pillShape)
                        .height(56.dp)
                        .width(tabWidthDp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    CompositionLocalProvider(LocalContentColor provides accentColor) {
                        Row(
                            Modifier
                                .clearAndSetSemantics {}
                                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                                .requiredWidth(with(density) { (totalWidthPx - 8.dp.toPx()).toDp() })
                                .height(56.dp)
                                .graphicsLayer {
                                    val progressOffset = pillValue() * tabWidthPx
                                    translationX = if (isLtr) -progressOffset else progressOffset
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            content = { highlightedContent(this, ::activateTab) },
                        )
                    }
                }
            }
        }
    }
}

/** 底栏单个 Tab：图标 + 可选文字，按压时随 [LocalLiquidTabScale] 放大。 */
@Composable
fun RowScope.LiquidGlassNavBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    tab: AppTab,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale = LocalLiquidTabScale.current
    val contentColor = LocalContentColor.current
    Column(
        modifier
            .semantics(mergeDescendants = true) {
                this.selected = selected
                role = Role.Tab
                onClick {
                    onClick()
                    true
                }
            }
            .onKeyEvent { event ->
                val activationKey = event.key == Key.Enter ||
                    event.key == Key.NumPadEnter || event.key == Key.Spacebar
                if (activationKey) {
                    if (event.type == KeyEventType.KeyUp) onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .fillMaxHeight()
            .weight(1f)
            .defaultMinSize(minWidth = if (showLabels) 76.dp else 60.dp)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = contentColor,
            modifier = Modifier.height(23.dp).width(23.dp),
        )
        if (showLabels) {
            AppText(
                text = tab.label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}
