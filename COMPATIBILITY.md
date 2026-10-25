# Parallel View 兼容性说明

> 模块：`com.example.parallelview`（LSPosed）
> 功能：在**同一个应用内部**做左右分栏（平行视界），左侧保留列表，右侧打开详情。
> 本文件说明 **哪些 Android 版本**、**哪些应用**可以用。

---

## 1. 速览结论

| 维度 | 支持情况 |
|---|---|
| **Android 13（API 33）** | ✅ 可用（模块 `minSdk = 33`） |
| **Android 14 / 15 / 16** | ✅ 可用（本机 Android 16 实测通过） |
| **Android 12（API 31）** | ❌ 不支持（原因见 §2） |
| **Android 11 及以下** | ❌ 不支持（系统没有该能力） |
| **多 Activity 导航的应用** | ✅ 可分栏（如 B 站） |
| **单 Activity（Fragment/Compose）应用** | ❌ 不可分栏（如 PixelPlayer） |
| **WebView 壳应用** | ❌ 不可分栏（如智慧职教+） |
| **输入法（IME）** | ❌ 不可分栏（如 Gboard） |

---

## 2. Android 版本支持

### ✅ Android 13 及以上：支持

模块直连系统扩展：

```
androidx.window.extensions.WindowExtensionsProvider
  └─ getWindowExtensions()
       ├─ getActivityEmbeddingComponent()          ← 返回 SplitController
       └─ getVendorApiLevel()
SplitPairRule$Builder(Predicate, Predicate, Predicate)
  ├─ setSplitRatio(float)
  └─ build()
component.setEmbeddingRules(Set<EmbeddingRule>)
```

这套 API 形态（**三谓词 Builder + `setEmbeddingRules`**）是 **Android 13 / 14 之后**的稳定形态。

实测：本机 **Android 16（API 36）**，`vendorApiLevel = 9`，B 站分栏成功。

### ❌ Android 12：不支持

三个原因，任一都足以失败：

1. **模块 `minSdk = 33`** —— Android 12 上装不上。
2. **API 形态不同** —— Android 12 的嵌入 API 是 `SplitController.setSplitRules(List)` +
   `SplitPairRule.Builder(Set<SplitPairFilter>)`；模块用的 `Builder(Predicate×3)` 在 12 上
   **不存在**，反射会直接找不到方法。
3. **Android 12 的嵌入只是实验性预览** —— 受系统 flag 门控，且多数 12 设备
   **系统里没有带** `androidx.window.extensions` 扩展，连
   `getActivityEmbeddingComponent()` 都取不到。

### 如需支持 Android 12

需要三件事（目前**没有做**）：

1. `minSdk` 降到 **31**；
2. 加**双路径探测**：先看扩展里的 `SplitPairRule$Builder` 是 `(Predicate×3)` 还是
   `(Set<SplitPairFilter>)`，再分别走 `setEmbeddingRules` / `setSplitRules`；
3. 加**能力检测**：拿不到 `ActivityEmbeddingComponent` 时安静跳过。

> 即便如此也不保证可用：取决于该 ROM 是否带了扩展、以及系统 flag 是否放开。

---

## 3. 应用能否使用

### 3.1 判断标准（三条，全部满足才行）

1. **应用内部用「新的 Activity」跳详情**
   （不是 Fragment、不是 Compose 导航、不是 WebView 里的网页跳转）
2. **窗口足够宽**
   （模块已把规则最小尺寸放到 200dp，正常平板窗口都满足）
3. **不是输入法 / 悬浮窗 / 系统组件**
   （这些不是应用 Activity 的导航）

> 本质：**ActivityEmbedding 只能拆「同一个应用的两个 Activity」**。
> 应用内部若只有一个 Activity，系统没有任何东西可拆。

### 3.2 已实测

| 应用 | 包名 | 结果 | 原因 |
|---|---|---|---|
| **哔哩哔哩（国际版）** | `com.bilibili.app.in` | ✅ **分栏成功** | 首页与视频详情是两个 Activity，同任务启动 |
| **PixelPlayer** | `com.theveloper.pixelplay` | ❌ 不可用 | 全应用只有 `MainActivity`（另有 17 个 Activity 都是分享/Widget 之类） |
| **智慧职教+** | `com.zhjy.study` | ❌ 不可用 | 仅 `MoocWebViewActivityX5` 一个 WebView 界面 |
| **米游社** | `com.mihoyo.hyperion` | ❌ 不可用 | 声明 43 个 Activity，但运行时只用 `HyperionMainActivity` 一个；点进帖子**不新开 Activity**（WebView/Fragment 内渲染） |
| **Gboard** | `com.google.android.inputmethod.latin` | ❌ 不可用 | 输入法，不是应用导航 |

> **重要**：`Activity 数量 ≥ 2` 只是**必要条件**，不是充分条件。
> 米游社就是典型反例：它有 **43** 个 Activity，但主界面是网页，系统无内容可拆。
> 真正可靠的办法只有**运行时观察**（见 §4.2）。

### 3.3 未实测但**预期可用**

| 应用 | 包名 | 说明 |
|---|---|---|
| 酷安 | `com.coolapk.market` | 官方主动适配过 ActivityEmbedding，理论上最稳 |

### 3.4 大规模不适用

以下类型**基本都不行**（国产 App 尤其集中）：

- 资讯/教育/电商类 **WebView 壳**应用（页面是网页）
- 用 **Compose / 单 Activity + Fragment** 的现代应用
- 输入法、悬浮窗、桌面、系统设置类

---

## 4. 如何自查某个应用能不能用

### 4.1 看它有几个 Activity

```bash
# 列出该应用声明的 Activity（只看应用自己的，过滤掉 Provider/Receiver）
adb shell pm dump <包名> | grep -oE '<包名>/[A-Za-z0-9_.$]+' | sort -u
```

判断：

- **只有 1 个 Activity** → ❌ 不可能分栏
- **有 2 个以上真正的界面 Activity** → ✅ 有机会（继续看导航方式）

### 4.2 运行时看它是否真的换了 Activity

```bash
# 在应用里点进详情页，然后看栈顶
adb shell dumpsys activity activities | grep -E 'topResumedActivity|Hist #'
```

- 点进详情后 **栈顶还是同一个 Activity** → ❌ 不是 Activity 导航，无法分栏
- **出现了新的 Activity** → ✅ 可以

### 4.3 看模块日志（确认规则是否装上）

```bash
adb shell su -c 'grep -ah "ParallelView/Ext" /data/adb/lspd/log/modules_*.log | tail -5'
```

预期看到：

```
ParallelView: enabling for <包名>
ParallelView/Ext: vendorApiLevel = 9
ParallelView/Ext: <包名>: embedding rule installed, ratio=0.38
```

- 有这几行 → 模块侧**已经尽力**，不分栏是应用结构问题
- 没有这几行 → 该应用**还没被加入 LSPosed 作用域**（见 §5）

---

## 5. 使用步骤与生效条件

| 改动 | 需要什么才生效 |
|---|---|
| 勾选/取消目标应用、改分栏比例 | **只重启那个应用** |
| 新增应用到 LSPosed 作用域 | **必须重启系统**（LSPosed 的机制，无法绕过） |

步骤：

1. 打开「Parallel View」→ **应用**页勾选目标应用；
2. 打开 LSPosed 管理器 → 模块 → 作用域 → 勾上同一个应用；
3. 重启系统（仅第一次为某应用添加时需要）；
4. 启动目标应用，点进详情页观察是否左右分栏。

---

## 6. 已知限制

1. **只能拆 Activity** —— 这是系统能力边界，不是模块缺陷；
2. **WebView 内的页面跳转完全无法被拆**；
3. **分栏比例**只影响左右宽度分配，不能改变应用内部布局；
4. **左右分栏需要窗口足够宽** —— 手机竖屏或很窄的浮窗可能不触发；
5. **部分系统应用/输入法**永远不适用。

---

## 7. 一句话总结

> **Android 13 起**、且**应用内部用 Activity 跳详情**时，这个模块能把平行视界强行开起来；
> 否则（Android 12、WebView 应用、单 Activity 应用、输入法）**在系统层面就做不到**。
