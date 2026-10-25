# 信号猎手 3.1 改造说明

> 本文记录四项需求的实现位置、设计取舍与验证结果，便于后续维护。

## 1. 夜间模式：自动 / 手动

- 入口：`AppTheme.kt` 的 `ThemeMode` 与 `SettingsStore.themeMode`。
- 三种模式：
  - `SYSTEM`：`isSystemInDarkTheme()` 自动跟随系统（Android 12+ 同时跟随系统深色计划）；
  - `LIGHT`：手动锁定浅色；
  - `DARK`：手动锁定深色。
- `SignalHunterTheme` 在组合阶段实时计算 `dark`，Material 与 Miuix 两套主题同时切换；
  状态栏 / 导航栏图标明暗由 `WindowCompat.getInsetsController` 同步。
- 选择立即生效并写入 `SharedPreferences`，重启后保留。
- `values/themes.xml` 与 `values-night/themes.xml` 分别提供窗口启动背景，避免冷启动闪白。

## 2. Miuix UI 与 Material You MD3 Expressive 并存

- 依赖：`top.yukonga.miuix.kmp:miuix-ui:0.9.4`、`miuix-blur:0.9.4`。
- `UiStyle` 提供 `MATERIAL` / `MIUIX` 两种设计语言，设置页“设计语言”卡片即时切换。
- `AdaptiveComponents.kt` 对 `Text`、`Card`、`Button`、`OutlinedButton`、`Slider`、`Divider`、
  `Switch` 做双实现：Material 模式走 Material3，Miuix 模式走 MiuiX。
- `AdaptiveStyle.kt` 新增 `appPalette()` / `appType()` 双主题色彩 / 排版映射，以及
  `AppPageHeader` / `AppSectionHeader` / `appOverScroll()` 等页面级构件，页面代码不再直接
  依赖 `MaterialTheme`，Miuix 模式下取到的是 `MiuixTheme` 角色与澎湃排版。
- `SignalHunterTheme` 仅在 Miuix 模式注入 `MiuixTheme`，Material 模式不会受到 Miuix 的
  `LocalIndication` / `LocalContentColor` 污染。
- **Miuix 主题取消莫奈取色**：`ThemeController` 改用 `ColorSchemeMode.Light / Dark`
  （miuix 原生固定配色，强调色为澎湃蓝），不再用 `MonetLight / MonetDark` 随种子色 /
  壁纸动态生成色板；Miuix 模式下也不显示任何取色控件。
- miuix-ui 0.9.4 的 `MiuixTheme` 已内置 `LocalContentColor`，主题层仍按 `onBackground`
  显式补齐以兼容旧行为。
- `minSdk` 由 31 调整为 33（`miuix-blur` 0.9.4 的 AAR 最低要求，其 highlight / sensor
  API 依赖 Android 13）。

## 3. Apple 风格液态玻璃悬浮底栏

- 入口：`LiquidGlassNavBar.kt` + `liquidglass/` 目录（自 KernelSU/ReSukiSU 管理端移植，
  保留 Apache-2.0 出处头注释）：`Lens`、`Vibrancy`、`InnerShadow`、`CombinedBackdrop`、
  `DragGestureInspector`、`DampedDragAnimation`、`InteractiveHighlight`。
- 结构（对齐参考 `FloatingBottomBar`）：
  - 底层 `Row`：`drawBackdrop` 做 vibrancy + blur + 圆角折射透镜，叠加随重力旋转的
    镜面高光（`BloomStroke`）、`InteractiveHighlight` 按压辉光、`DampedDragAnimation`
    按压 / 缩放；
  - 隐藏强调色副本 `Row`（`alpha(0f) + layerBackdrop`）供液滴胶囊合成；
  - 滑动液滴胶囊 `Box`：`drawBackdrop(CombinedBackdrop)`，按压时透镜加深 +
    色散（chromaticAberration）+ 内阴影，随速度挤压拉伸（scaleX/scaleY）；
  - 无 `RuntimeShader` 时降级为实色容器 + 15% 强调色胶囊。
- **流畅度**：`MiuixShell` 用 `HorizontalPager` 承载四个页面，`flingBehavior` 使用
  miuix `PagerNavigationSpringSpec`；底栏与 Pager 通过 `selected` 双向同步。
- **胶囊定位 = 阻尼动画值（1:1 跟手）**：拖动底栏时 `onDrag` 直接更新
  `dampedDragAnimation.value`，胶囊实时跟手；点选 / 翻页时经 `selected → animateToValue`
  弹簧对齐。
- 已修复的三个问题：
  1. **水平错位**：外层 `Box` 的 `contentAlignment` 由 `Center` 改为 `CenterStart`
     （竖直居中、水平起始），避免胶囊窄于整栏时被居中而整体右偏约 1.5 个 tab 宽；
  2. **拖动滞后**：胶囊不再读取滞后的 `pagerState` 连续偏移，改为只读阻尼值，并移除
     `onDrag` 中逐事件 `pagerState.scroll { scrollBy }`（会抢占滚动锁并与手指打架）；
  3. **反复横跳**：`MiuixShell` 增加 `animating` 守卫，程序化 `springAnimateToPage`
     途经的中间页不再回灌 `onSelect`，消除 3→1→2→3 抖动。
- 设置页提供“液态玻璃底栏”“显示底栏文字”两个开关。

## 4. MD3 Expressive 自定义颜色

- 入口：`AppTheme.kt` 的 `expressiveColorScheme()`、`SettingsUi.kt` 的颜色区域。
- 用色相（0–360°）+ 饱和度（25%–90%）生成完整 MD3 色彩角色，包括
  primary / secondary / tertiary、container、surface container 系列、
  outline、inverse 等；不是简单替换一个强调色。
- 快捷色板：森林、海洋、紫罗兰、日落、樱花、青柠；同时提供色相渐变滑条与饱和度滑条。
- Android 12+ 支持“跟随壁纸动态取色”，优先级高于自定义色相。
- 主题使用 `ExpressiveShapes`（8/12/18/26/34dp 圆角）与 Material3 Expressive 色板。
- 该“MD3 Expressive 颜色”区域（含“跟随壁纸动态取色”）**仅在 Material 模式下显示**；
  Miuix 模式使用原生固定配色，不再有任何莫奈 / 取色入口。

## 5. 预测性返回手势

- `AndroidManifest.xml`：`android:enableOnBackInvokedCallback="true"`。
- 根导航：非首页 Tab 注册 `PredictiveBackHandler`，手指拖动时内容按进度位移、缩放、
  淡出；取消手势自动回弹，完成后回到蓝牙首页。
- 追踪页：蓝牙追踪与 WiFi/基站追踪都注册 `PredictiveBackHandler`，进度映射为
  `translationX` / `alpha` / `scale`，完成后返回列表。
- Android 13 以下由 activity-compose 的兼容路径回退为普通返回。

## 6. 构建与验证

- Gradle 9.3.1、AGP 9.1.0、Kotlin 2.4.20、compileSdk 37、targetSdk 36、minSdk 33。
- 验证环境：一加 Ace 5（PKG110，Android 16）真机 + Android 36 x86_64 模拟器。
- 已验证（真机截图见 `screenshots/`）：
  - Material / Miuix 双向切换，切换时保留当前 Tab；
  - 自动模式随系统深色即时切换；手动浅色 / 深色；
  - MD3 自定义色板即时改变整套配色（仅 Material 模式）；
  - Miuix 原生固定配色（澎湃蓝），无莫奈取色；
  - Miuix 液态玻璃底栏：折射 / 高光 / 色彩增强、点选弹簧对齐、拖动 1:1 跟手、
    跨多页点选无横跳；
  - 从设置页返回首页、从追踪页返回列表的预测性返回；
  - `assembleDebug` 构建成功并 `adb install` 至真机运行。

## 7. 产物

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Release（未签名）：`app/build/outputs/apk/release/app-release-unsigned.apk`
