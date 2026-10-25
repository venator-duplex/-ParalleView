# Parallel View (LSPosed)

这是一个由ai制作的lsposed模块，它可以实现让平板上的 Android 应用**强行开启应用内左右分栏（平行视界）** —— 不是分屏，也不是自由窗口：
同一个应用的「列表」和「详情」两个 Activity 并排在同一个窗口里，和澎湃 OS / One UI 一样。

## 原理

不依赖 `androidx.window`（它在部分 ROM 上会判定 `UNAVAILABLE`），
而是**直连系统扩展**：

```
WindowExtensionsProvider.getWindowExtensions()
  └─ getActivityEmbeddingComponent()          → SplitController
SplitPairRule$Builder(Predicate×3).setSplitRatio(float).build()
component.setEmbeddingRules(Set<EmbeddingRule>)
```

在目标应用的进程里（`Instrumentation.callApplicationOnCreate` 之后）注册规则，
由系统把它自己的两个 Activity 并排摆放。

## 兼容性

**必须满足两个条件**：

1. **Android 13+**（模块 `minSdk = 33`，用的是 13+ 的扩展 API 形态）
2. **应用内部用「新的 Activity」跳详情**（不是 Fragment / Compose 导航 / WebView）

详细说明见 `deliverables/PARALLEL-VIEW-兼容性.md`（或仓库内的 `COMPATIBILITY.md`）。

| 例子 | 能否 |
|---|---|
| 哔哩哔哩 `com.bilibili.app.in` | ✅ 实测成功 |
| 酷安 `com.coolapk.market` | ✅ 预期可用（官方适配过） |
| 米游社 `com.mihoyo.hyperion` | ❌ 运行时只有一个 Activity |
| PixelPlayer | ❌ 单 Activity |
| 智慧职教+ | ❌ WebView 壳 |
| Gboard | ❌ 输入法 |

## 使用

1. 安装 APK；
2. 在 **LSPosed 管理器**里启用本模块，并把需要分栏的应用加入**作用域**；
3. 重启设备（LSPosed 作用域变更必须重启）；
4. 打开目标应用，点进详情页 —— 应该左右分栏。
PS:本模块无法在应用内提供两边画面比例调节，但你可以在模块内设置你想要的两边画面显示比例

## 构建

```bash
# 需要 Android SDK (compileSdk 37) 与 JDK 17
./gradlew :app:assembleRelease

# 签名
apksigner sign --ks your.keystore --out ParallelView.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

依赖 `androidx.compose.material:material-icons-extended`（约 37MB）没有随仓库提交，
首次构建前请先放好：

```bash
curl -L -o app/material-icons-extended.jar \
  https://maven.aliyun.com/repository/google/androidx/compose/material/material-icons-extended-android/1.7.0/material-icons-extended-android-1.7.0.aar
# 或直接用 Gradle 依赖替换 build.gradle.kts 里的 implementation(files(...))
```

`app/stubs.jar` 是 Xposed API 的编译期桩（运行时由 LSPosed 提供），必须保留。

```

## 许可

仅供学习研究。
