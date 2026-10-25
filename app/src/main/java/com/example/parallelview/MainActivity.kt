@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.parallelview

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.contentColorFor
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.NavigationBar
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * Parallel View - module UI.
 *
 * Two ways to navigate (chosen in 外观):
 *   - 底栏: floating liquid-glass pill (KernelSU style, miuix-blur backdrop)
 *   - 侧栏: a left navigation rail
 *
 * Two design languages (also in 外观):
 *   - Material 3 Expressive: Monet / dynamic colour enabled
 *   - Miuix: fixed Miuix palette, deliberately WITHOUT Monet
 * The theme layer (AppTheme.kt) already enforces exactly that split.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

private const val KEY_NAV = "parallelview_nav"

/**
 * Apps confirmed NOT to work even though they declare several Activities:
 * WebView shells, single-Activity UIs and input methods. ActivityEmbedding
 * needs two real Activities that the app itself navigates between.
 */
private val KNOWN_UNSPLITTABLE = setOf(
    "com.theveloper.pixelplay",              // single-Activity Compose UI
    "com.mihoyo.hyperion",                    // 米游社: runtime single Activity
    "com.zhjy.study",                         // 智慧职教+: WebView shell
    "com.google.android.inputmethod.latin",   // Gboard: IME
)

/** 1 = animate page changes triggered from the nav bar, 0 = jump instantly. */
private const val KEY_ANIM = "parallelview_anim"

private fun animEnabled(context: android.content.Context): Boolean =
    try {
        Settings.Global.getInt(context.contentResolver, KEY_ANIM, 1) == 1
    } catch (_: Throwable) {
        true
    }

private fun setAnimEnabled(context: android.content.Context, value: Boolean) {
    try {
        Settings.Global.putInt(context.contentResolver, KEY_ANIM, if (value) 1 else 0)
    } catch (_: Throwable) {
    }
}

private fun navMode(context: android.content.Context): Int =
    try {
        Settings.Global.getInt(context.contentResolver, KEY_NAV, 1)
    } catch (_: Throwable) {
        1
    }

private fun setNavMode(context: android.content.Context, value: Int) {
    try {
        Settings.Global.putInt(context.contentResolver, KEY_NAV, value)
    } catch (_: Throwable) {
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    /* pagerState is the single source of truth for the page. `tab` is derived
     * from it instead of being a second state, so a feedback loop between the
     * two is structurally impossible - that loop is what occasionally left the
     * pager stuck between pages. */
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val tab = pagerState.currentPage
    var showTheme by remember { mutableStateOf(false) }
    var nav by remember { mutableIntStateOf(navMode(context)) }
    val tabs = listOf(
        AppTab("概览", Icons.Rounded.Apps),
        AppTab("分栏", Icons.Rounded.Tune),
        AppTab("外观", Icons.Rounded.Settings)
    )

    /* One animation at a time: the liquid-glass bar invokes onSelect repeatedly
     * while it is being dragged, and stacking several animateScrollToPage calls
     * made the pager fight itself and appear stuck between two pages. */
    var navJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun go(index: Int) {
        showTheme = false
        navJob?.cancel()
        navJob = scope.launch {
            if (animEnabled(context)) pagerState.animateScrollToPage(index)
            else pagerState.scrollToPage(index)
        }
    }

    SignalHunterTheme(settings) {
        /* The backdrop's base colour is what the glass samples where the content
         * is transparent. A hardcoded black base turned the bar dark grey in
         * light mode - use the theme background instead. */
        val backdropBase = if (isNight(settings, androidx.compose.foundation.isSystemInDarkTheme())) {
            androidx.compose.ui.graphics.Color.Black
        } else {
            appPalette().background
        }
        val backdrop = rememberLayerBackdrop {
            drawRect(backdropBase)
            drawContent()
        }
        /* Portrait always uses a bottom navigation so the rail never eats the
         * narrow side. Landscape keeps the user's choice, except that Material 3
         * Expressive is rail-only (no liquid glass bar there - it belongs to
         * Miuix, as in the original requirement). */
        val portrait = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
                android.content.res.Configuration.ORIENTATION_PORTRAIT
        val effectiveNav = when {
            portrait -> 1
            settings.uiStyle == UiStyle.MATERIAL -> 0
            else -> nav
        }
        /* Light mode needs an explicit page colour, otherwise the window stays
         * black and every onBackground text turns invisible. Dark mode stays
         * transparent so the liquid-glass bar can sample the content behind it. */
        val dark = isNight(settings, isSystemInDarkTheme())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (dark) androidx.compose.ui.graphics.Color.Transparent
                    else appPalette().background
                )
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                if (effectiveNav == 0) {
                    val palette = appPalette()
                    Column(
                        Modifier
                            .fillMaxHeight()
                            .width(196.dp)
                            .padding(8.dp)
                    ) {
                        Spacer(Modifier.height(28.dp))
                        AppText(
                            "Parallel View",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        tabs.forEachIndexed { index, t ->
                            val sel = tab == index
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                                    .background(
                                        if (sel) palette.primary.copy(alpha = 0.15f)
                                        else androidx.compose.ui.graphics.Color.Transparent
                                    )
                                    .selectable(selected = sel, onClick = { go(index) })
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    t.icon,
                                    contentDescription = t.label,
                                    tint = if (sel) palette.primary else palette.onBackgroundVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                AppText(
                                    t.label,
                                    color = if (sel) palette.primary else palette.onBackgroundVariant
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    val inset = if (effectiveNav == 1) 132.dp else 24.dp
                    if (showTheme) {
                        ThemeScreen(
                            settings = settings,
                            context = context,
                            nav = nav,
                            onNav = { nav = it; setNavMode(context, it) },
                            onBack = { showTheme = false },
                            bottomInset = inset
                        )
                    } else {
                        androidx.compose.foundation.pager.HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            /* Keep neighbouring pages composed. Without this the
                             * overview page was disposed on every swipe away and
                             * rebuilt on the way back (icons + PackageManager
                             * scan), which is what felt like jank. */
                            beyondViewportPageCount = 2
                        ) { page ->
                            when (page) {
                                0 -> OverviewScreen(context, settings, nav, bottomInset = inset)
                                1 -> RatioScreen(context, bottomInset = inset)
                                else -> LookScreen(
                                    context = context,
                                    settings = settings,
                                    nav = nav,
                                    onOpenTheme = { showTheme = true },
                                    bottomInset = inset
                                )
                            }
                        }
                    }
                }
            }

            if (effectiveNav == 1 && settings.uiStyle == UiStyle.MIUIX) {
                LiquidGlassNavBar(
                    tabs = tabs,
                    selected = tab,
                    onSelect = { go(it) },
                    backdrop = backdrop,
                    glassEnabled = settings.liquidGlass,
                    showLabels = settings.showNavLabels,
                    /* The bar must follow the APP's theme, not the system one:
                     * with a light app on a dark system the bar rendered dark on
                     * a white page. */
                    dark = isNight(settings, isSystemInDarkTheme()),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                )
            }
            if (effectiveNav == 1 && settings.uiStyle == UiStyle.MATERIAL) {
                androidx.compose.material3.NavigationBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                ) {
                    tabs.forEachIndexed { index, t ->
                        val sel = tab == index
                        val accent = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        val idle = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                                .selectable(selected = sel, onClick = { go(index) })
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(t.icon, contentDescription = t.label,
                                tint = if (sel) accent else idle, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(t.label, color = if (sel) accent else idle, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Overview page, modelled on KernelSU's home screen: one tinted status card and
 * a plain information card. The app allow-list is gone - LSPosed's scope is the
 * only place an app is authorized, so there is nothing to pick here.
 */
/** Cached across recompositions and page switches: the PackageManager scan and the
 *  icon decoding are the expensive part of this screen. */
private var supportedCache: List<Triple<String, String, ImageBitmap?>>? = null

@Composable
private fun OverviewScreen(
    context: android.content.Context,
    settings: SettingsStore,
    nav: Int,
    bottomInset: androidx.compose.ui.unit.Dp
) {
    val miuix = LocalUiStyle.current == UiStyle.MIUIX
    val pkgInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Throwable) {
            null
        }
    }
    val version = pkgInfo?.versionName ?: "-"
    val ratio = try {
        Math.round(android.provider.Settings.Global.getFloat(
            context.contentResolver, Targets.KEY_RATIO, 0.38f) * 100f)
    } catch (_: Throwable) {
        38
    }
    val rows = listOf(
        "设备" to (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL),
        "系统" to ("Android " + android.os.Build.VERSION.RELEASE + " · API " + android.os.Build.VERSION.SDK_INT),
        "模块版本" to version,
        "分栏比例" to ("左侧 " + ratio + "% · 右侧 " + (100 - ratio) + "%"),
    )

    /* Only the first few apps are shown: building a long list of app icons is
     * what made the pager stutter when swiping back to this page. */
    val supported = remember { supportedCache ?: run {
        val pm = context.packageManager
        val launcher = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, 0)
            .mapNotNull { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .filter { !KNOWN_UNSPLITTABLE.contains(it.packageName) }
            .filter {
                /* Skip system apps: they are not the user's use case and they
                 * inflated the count. */
                try {
                    val ai = pm.getApplicationInfo(it.packageName, 0)
                    (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
                } catch (_: Throwable) {
                    true
                }
            }
            .filter {
                try {
                    (pm.getPackageInfo(it.packageName,
                        android.content.pm.PackageManager.GET_ACTIVITIES)
                        .activities?.size ?: 0) >= 2
                } catch (_: Throwable) {
                    false
                }
            }
            .map {
                Triple(it.packageName, it.loadLabel(pm).toString(),
                    appIcon(pm, it.packageName, 144))
            }
            .sortedBy { it.second.lowercase() }
            .also { supportedCache = it }
    } }
    val shown = supported

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomInset)
    ) {
        item { Spacer(Modifier.height(16.dp)); AppPageHeader("概览", "") }

        item {
            /* Always show the enabled state: the heartbeat approach needs the
             * module injected into system_server, which this LSPosed build does
             * not do, so an "inactive" state would be a false signal. */
            StatusCard(
                title = "平行视界运行中",
                summary = "应用授权由 LSPosed 作用域管理",
                mini = "已启用",
                active = true,
                isDark = isNight(settings, androidx.compose.foundation.isSystemInDarkTheme())
                        )
        }

        item { AppSectionHeader("信息") }
        item {
            AppCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    rows.forEach { (k, v) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                AppText(k)
                                AppText(v, color = appPalette().onBackgroundVariant, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        item { AppSectionHeader("支持平行视界的应用 · 共 " + supported.size + " 个") }
        item {
            if (shown.isEmpty()) {
                AppCard {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        AppText("未检测到可用的应用")
                    }
                }
            } else {
                AppCard {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        shown.forEach { (pkg, label, icon) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (icon != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = icon,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
                                    )
                                    Spacer(Modifier.width(12.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    AppText(label)
                                    AppText(pkg, fontSize = 11.sp, color = appPalette().onBackgroundVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ThemeScreen(
    settings: SettingsStore,
    context: android.content.Context,
    nav: Int,
    onNav: (Int) -> Unit,
    onBack: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp
) {
    val miuix = LocalUiStyle.current == UiStyle.MIUIX
    var animOn by remember { mutableStateOf(animEnabled(context)) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomInset)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            /* A real TopAppBar keeps the navigation icon and the title on the same
             * baseline - hand-aligning them in a Row is what looked off. */
            androidx.compose.material3.TopAppBar(
                title = { Text("主题", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        }

        item { AppSectionHeader("界面风格") }
        item {
            if (miuix) {
                top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
                    top.yukonga.miuix.kmp.basic.BasicComponent(
                        title = "Material 3 Expressive",
                        summary = "支持莫奈取色",
                        startAction = { Icon(Icons.Rounded.Palette, null,
                            modifier = Modifier.padding(end = 6.dp),
                            tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onBackground) },
                        endActions = {
                            if (settings.uiStyle == UiStyle.MATERIAL) {
                                Icon(Icons.Rounded.Check, null,
                                    tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary)
                            }
                        },
                        onClick = { settings.uiStyle = UiStyle.MATERIAL }
                    )
                    top.yukonga.miuix.kmp.basic.BasicComponent(
                        title = "Miuix",
                        summary = "固定配色，不取色",
                        startAction = { Icon(Icons.Rounded.Tune, null,
                            modifier = Modifier.padding(end = 6.dp),
                            tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onBackground) },
                        endActions = {
                            if (settings.uiStyle == UiStyle.MIUIX) {
                                Icon(Icons.Rounded.Check, null,
                                    tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary)
                            }
                        },
                        onClick = { settings.uiStyle = UiStyle.MIUIX }
                    )
                }
            } else {
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    StyleRowM3("Material 3 Expressive", "支持莫奈取色",
                        settings.uiStyle == UiStyle.MATERIAL,
                        Icons.Rounded.Palette) { settings.uiStyle = UiStyle.MATERIAL }
                    StyleRowM3("Miuix", "固定配色，不取色",
                        settings.uiStyle == UiStyle.MIUIX,
                        Icons.Rounded.Tune) { settings.uiStyle = UiStyle.MIUIX }
                }
            }
        }

        item { AppSectionHeader("交互") }
        item {
            if (miuix) {
                top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
                    top.yukonga.miuix.kmp.basic.BasicComponent(
                        title = "切换动画",
                        summary = "底栏/侧栏切页时的过渡动画",
                        endActions = {
                            top.yukonga.miuix.kmp.basic.Switch(
                                checked = animOn,
                                onCheckedChange = { animOn = it; setAnimEnabled(context, it) }
                            )
                        },
                        onClick = { animOn = !animOn; setAnimEnabled(context, animOn) }
                    )
                }
            } else {
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text("切换动画") },
                        supportingContent = { Text("底栏/侧栏切页时的过渡动画") },
                        trailingContent = {
                            AppSwitch(checked = animOn, onCheckedChange = { animOn = it; setAnimEnabled(context, it) })
                        },
                        modifier = Modifier.clickable { animOn = !animOn; setAnimEnabled(context, animOn) }
                    )
                }
            }
        }

        if (settings.uiStyle == UiStyle.MATERIAL) {
            item { AppSectionHeader("莫奈取色") }
            item {
                androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text("跟随壁纸取色") },
                        supportingContent = { Text("使用系统 Monet 调色板") },
                        trailingContent = {
                            AppSwitch(checked = settings.dynamicColor,
                                onCheckedChange = { settings.setDynamicColors(it) })
                        },
                        modifier = Modifier.clickable { settings.setDynamicColors(!settings.dynamicColor) }
                    )
                }
            }
        }

        if (settings.uiStyle == UiStyle.MATERIAL) {
            item { AppSectionHeader("自定义取色") }
            item {
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text("自定义取色") },
                        supportingContent = { Text("按色相/饱和度生成 MD3 配色") },
                        trailingContent = {
                            AppSwitch(checked = settings.customColorEnabled,
                                onCheckedChange = { settings.setCustomColor(it) })
                        },
                        modifier = Modifier.clickable {
                            settings.setCustomColor(!settings.customColorEnabled)
                        }
                    )
                    if (settings.customColorEnabled) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                            Text("色相 ${settings.seedHue.toInt()}°", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                            androidx.compose.material3.Slider(
                                value = settings.seedHue,
                                onValueChange = { settings.setHue(it) },
                                valueRange = 0f..360f
                            )
                            Text("饱和度 ${(settings.seedSaturation * 100).toInt()}%", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                            androidx.compose.material3.Slider(
                                value = settings.seedSaturation,
                                onValueChange = { settings.setSaturation(it) },
                                valueRange = 0.25f..0.9f
                            )
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val hue = settings.seedHue
                                Box(Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(androidx.compose.ui.graphics.Color.hsl(((hue % 360f) + 360f) % 360f, settings.seedSaturation, 0.5f)))
                                Text("预览色", style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.align(Alignment.CenterVertically))
                            }
                        }
                    }
                }
            }
        }

        item { AppSectionHeader("导航方式") }
        item {
            if (miuix) {
                top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
                    top.yukonga.miuix.kmp.basic.BasicComponent(
                        title = "侧栏",
                        summary = "左侧导航轨",
                        startAction = { Icon(Icons.Rounded.ViewSidebar, null,
                            modifier = Modifier.padding(end = 6.dp),
                            tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onBackground) },
                        endActions = {
                            if (nav == 0) Icon(Icons.Rounded.Check, null,
                                tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary)
                        },
                        onClick = { onNav(0) }
                    )
                    top.yukonga.miuix.kmp.basic.BasicComponent(
                        title = "底栏",
                        summary = "悬浮液态玻璃",
                        startAction = { Icon(Icons.Rounded.BlurOn, null,
                            modifier = Modifier.padding(end = 6.dp),
                            tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onBackground) },
                        endActions = {
                            if (nav == 1) Icon(Icons.Rounded.Check, null,
                                tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary)
                        },
                        onClick = { onNav(1) }
                    )
                }
            } else {
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    StyleRowM3("侧栏", "左侧导航轨", nav == 0, Icons.Rounded.ViewSidebar) { onNav(0) }
                    StyleRowM3("底栏", "悬浮液态玻璃", nav == 1, Icons.Rounded.BlurOn) { onNav(1) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Material 3 ListItem + RadioButton - the spec way to present a single choice. */
@Composable
private fun StyleRowM3(
    title: String,
    summary: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    androidx.compose.material3.ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            AppSwitch(checked = selected, onCheckedChange = { onClick() })
        },
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
private fun LookScreen(
    context: android.content.Context,
    settings: SettingsStore,
    nav: Int,
    onOpenTheme: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp
) {
    val miuix = LocalUiStyle.current == UiStyle.MIUIX
    val themeSummary = (if (settings.uiStyle == UiStyle.MIUIX) "Miuix" else "Material 3 Expressive") +
        " · " + (if (nav == 0) "侧栏" else "底栏")

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomInset)
    ) {
        item { Spacer(Modifier.height(16.dp)); AppPageHeader("外观", "") }

        item { AppSectionHeader("夜间模式") }
        item {
            if (miuix) {
                top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEach { mode ->
                        top.yukonga.miuix.kmp.basic.BasicComponent(
                            title = mode.label,
                            summary = null,
                            endActions = {
                                if (settings.themeMode == mode) {
                                    Icon(Icons.Rounded.Check, contentDescription = null,
                                        tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary)
                                }
                            },
                            onClick = { settings.themeMode = mode }
                        )
                    }
                }
            } else {
                androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEach { mode ->
                        androidx.compose.material3.ListItem(
                            headlineContent = { Text(mode.label) },
                            trailingContent = {
                                androidx.compose.material3.Switch(
                                    checked = settings.themeMode == mode,
                                    onCheckedChange = { settings.themeMode = mode },
                                    thumbContent = {
                                        Icon(
                                            if (settings.themeMode == mode) Icons.Rounded.Check
                                            else Icons.Rounded.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(
                                                androidx.compose.material3.SwitchDefaults.IconSize)
                                        )
                                    }
                                )
                            },
                            modifier = Modifier.clickable { settings.themeMode = mode }
                        )
                    }
                }
            }
        }

        item { AppSectionHeader("主题") }
        item {
            if (miuix) {
                top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
                    top.yukonga.miuix.kmp.basic.BasicComponent(
                        title = "主题设置",
                        summary = themeSummary,
                        startAction = {
                            Icon(Icons.Rounded.Palette, contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                                tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onBackground)
                        },
                        endActions = {
                            Icon(Icons.Filled.ArrowForward, contentDescription = null,
                                tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onBackground)
                        },
                        onClick = onOpenTheme
                    )
                }
            } else {
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text("主题设置") },
                        supportingContent = { Text(themeSummary) },
                        leadingContent = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                        trailingContent = { Icon(Icons.Filled.ArrowForward, contentDescription = null) },
                        modifier = Modifier.clickable { onOpenTheme() }
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Split ratio has its own page: slider + presets, each theme's native controls. */
@Composable
private fun RatioScreen(
    context: android.content.Context,
    bottomInset: androidx.compose.ui.unit.Dp
) {
    var ratio by remember {
        mutableStateOf(
            try {
                Settings.Global.getFloat(context.contentResolver, Targets.KEY_RATIO, 0.38f)
            } catch (_: Throwable) {
                0.38f
            }
        )
    }
    fun persist(v: Float) {
        try {
            Settings.Global.putFloat(context.contentResolver, Targets.KEY_RATIO, v)
        } catch (_: Throwable) {
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomInset)
    ) {
        item { Spacer(Modifier.height(16.dp)); AppPageHeader("分栏", "") }
        item { AppSectionHeader("比例") }
        item {
            AppCard {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tune, contentDescription = null,
                            tint = appPalette().onBackgroundVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        AppText("左侧 ${Math.round(ratio * 100f)}%  ·  右侧 ${Math.round((1f - ratio) * 100f)}%")
                    }
                    AppSlider(
                        value = ratio,
                        onValueChange = { ratio = it; persist(it) },
                        valueRange = 0.2f..0.6f
                    )
                }
            }
        }
        item { AppSectionHeader("预设") }
        item {
            /* No outer card here on purpose: the buttons bring their own corner
             * radius, and nesting them in a card produced two different radii. */
            val presets = listOf(0.30f, 0.38f, 0.50f, 0.60f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    AppOutlinedButton(
                        onClick = { ratio = preset; persist(preset) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("${Math.round(preset * 100f)}:" + "${Math.round((1f - preset) * 100f)}")
                    }
                }
            }
        }
        item {
            AppOutlinedButton(
                onClick = { ratio = 0.38f; persist(0.38f) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("恢复默认 38:62") }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * Miuix mode: the green panel is Miuix's own Card with
 * CardDefaults.defaultColors(color = 0xFF1A3825) - exactly KernelSU's
 * HomeMiuix.kt StatusCard. It is not a bespoke view.
 *
 * Material mode: M3 Card with secondaryContainer + contentColorFor.
 */
/**
 * KernelSU-style status panel: Miuix Card + CardDefaults.defaultColors(dark green),
 * an oversized OUTLINE check bleeding past the bottom-right corner.
 */
/**
 * KernelSU-style status panel.
 *  active:   green Card, oversized OUTLINE check bleeding off the corner.
 *  inactive: errorContainer Card with a cross - the "not working" state.
 * Miuix keeps KSU's own three-way colours (dark green / pale green), and the
 * check switches to the dark green on a pale background so it stays readable.
 */
@Composable
private fun StatusCard(
    title: String = "",
    summary: String = "",
    mini: String = "",
    active: Boolean = true,
    isDark: Boolean = true
) {
    val miuix = LocalUiStyle.current == UiStyle.MIUIX

    val container = when {
        miuix && active && isDark -> Color(0xFF1A3825)
        miuix && active -> Color(0xFFDFFAE4)
        miuix -> if (isDark) Color(0xFF5C1A16) else Color(0xFFFFDAD6)
        else -> androidx.compose.material3.MaterialTheme.colorScheme.run {
            if (active) secondaryContainer else errorContainer
        }
    }
    val onContainer = when {
        miuix && active && isDark -> Color.White
        miuix && active -> Color(0xFF14301F)
        miuix -> if (isDark) Color(0xFFFFDAD6) else Color(0xFF410002)
        else -> androidx.compose.material3.MaterialTheme.colorScheme.contentColorFor(container)
    }
    /* Check/cross tint: on the pale background the dark green is the readable one. */
    val markTint = when {
        miuix && active && isDark -> Color(0xFF36D167)
        miuix && active -> Color(0xFF1A3825)
        miuix -> if (isDark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)
        else -> androidx.compose.material3.MaterialTheme.colorScheme.run {
            if (active) primary else error
        }
    }
    val mark = if (active) Icons.Outlined.CheckCircleOutline else Icons.Outlined.Cancel

    if (miuix) {
        top.yukonga.miuix.kmp.basic.Card(
            modifier = Modifier.fillMaxWidth(),
            colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(container, onContainer)
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().height(168.dp).clipToBounds()
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().offset(27.dp, 31.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Icon(mark, contentDescription = null, tint = markTint,
                        modifier = Modifier.size(110.dp))
                }
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().padding(start = 16.dp, bottom = 10.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    AppText(mini, color = onContainer.copy(alpha = 0.62f), fontSize = 16.sp)
                }
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    AppText(title, color = onContainer, fontSize = 20.sp)
                    if (summary.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        AppText(summary, color = onContainer.copy(alpha = 0.82f), fontSize = 13.sp)
                    }
                }
            }
        }
    } else {
        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = container)
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().height(168.dp).clipToBounds()
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().offset(27.dp, 31.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Icon(mark, contentDescription = null, tint = markTint,
                        modifier = Modifier.size(110.dp))
                }
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().padding(start = 16.dp, bottom = 10.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    AppText(mini, color = onContainer.copy(alpha = 0.62f), fontSize = 16.sp)
                }
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    AppText(title, color = onContainer, fontSize = 20.sp)
                    if (summary.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        AppText(summary, color = onContainer.copy(alpha = 0.82f), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun appIcon(
    pm: android.content.pm.PackageManager,
    pkg: String,
    sizePx: Int
): ImageBitmap? = try {
    val d = pm.getApplicationIcon(pkg)
    val bmp = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    d.setBounds(0, 0, sizePx, sizePx)
    d.draw(c)
    bmp.asImageBitmap()
} catch (_: Throwable) {
    null
}
