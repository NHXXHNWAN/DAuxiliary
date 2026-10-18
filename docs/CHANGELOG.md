# 更新日志

## 2026-10-18

### 官方 iOS-like 液态玻璃底栏移植

- **从 Miuix example 移植完整 iOS-like 底栏实现**：
  - 移植 `IosLiquidGlassNavigationBar` 及全部依赖组件（`DampedDragAnimation`、`InteractiveHighlight`、`CombinedBackdrop`、`InnerShadow`、`Lens`、`Vibrancy`）到 `app` 与 `admin` 模块
  - 包名统一为 `com.dauxiliary.ui.miuix.component.{liquid,animation}`
  - 替换 `import ui.isInDarkTheme` 为项目内 `@Composable private fun isInDarkTheme(): Boolean = isSystemInDarkTheme()`
  - 完全符合官方示例 (`github.com/compose-miuix-ui/miuix/example`) 的结构与视觉规范
  
- **三端统一接入 iOS-like 底栏**：
  - 主应用 (`DAuxiliaryApp`)：保留 `navigationBarStyle` 切换逻辑，`== 1` 时使用 `IosLiquidGlassNavigationBar`
  - 后台管理 (`admin/AdminScreens`)：三页面 Tab (总览/用户授权/Bot 角色) 全部使用液态玻璃底栏
  - 注入设置 (`InjectedModuleSettings`)：Telegram 宿主内三页面 Tab (首页/功能/设置) 使用液态玻璃底栏
  - 全部调用点统一遵循官方底栏 API：`items: List<NavigationItem>`, `selectedIndex`, `backdrop`, `isBlurActive`
  
- **底部边距动态适配**：
  - 组件内部 `bottomPaddingValue = when (platform()) { Platform.IOS -> 20.dp; else -> if (navBarBottomPadding != 0.dp) 8.dp + navBarBottomPadding else 36.dp }`
  - `navBarBottomPadding` 通过 `WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()` 动态读取
  - 胶囊水平边距 `start/end = 24.dp`，高度 `64.dp`，内 padding `4.dp`
  - 选中态通过 `LocalContentColor` + 背景透明度控制，不遮挡图标
  
- **验证完成**：
  - `:app:assembleDebug` ✓ (74 tasks)
  - `:admin:assembleDebug` ✓ (74 tasks)
  - 编译耗时 8m 35s，12 executed, 62 up-to-date

修复问题：
- 底部边距不协调 → 现动态读取系统导航栏 insets
- 选中图标被遮挡 → 官方实现通过 `CompositionLocalProvider(LocalContentColor)` + 低透明度背景规避
- admin 底栏未改 → 已完整移植并接入液态玻璃底栏

### 本轮底栏规范化与 UI 简化

- **改用 Miuix 官方底栏 API**：
  - 应用主界面、admin 管理页、注入设置页全部改为使用官方 `FloatingNavigationBar` + `FloatingNavigationBarItem`
  - 删除所有自定义参数（`horizontalOutSidePadding`等），使用 Miuix 默认值
  - 通过 `Scaffold` 的 `bottomBar` 参数正确集成，自动处理底部 padding
  - 删除手动计算底栏高度、动态 insets、重复 padding 注入等所有 workaround 代码

- **移除过度图标装饰**：
  - 主应用首页删除圆形图标容器（48dp Box + Favorites/Info 图标）
  - 状态改为纯排版：28sp Bold 标题 + 15sp Medium 版本号单行
  - Telegram 授权页删除说明卡片（Card + Row + Icon），改为 14sp 单段文字
  - 注入功能页删除每项功能的分类图标（Home/Messages/Lock/Tune/Info）
  - 统一视觉语言：靠字号、字重、留白和语义色构建层次

- **修复底栏选中态同步**：
  - 删除独立的 `selectedPage` 状态，底栏 `selected` 直接绑定 `pagerState.currentPage`
  - 用户滑动切换页面时，底栏选中态实时跟随

- **修复底栏 padding 叠加**：
  - `InjectedGroupedPage` 不再注入 `LocalNavigationPadding`（76dp）
  - `HorizontalPager` 直接使用 `Scaffold` 传入的 `padding`（`.padding(padding)`）
  - 避免 Scaffold padding + 手动 padding + LocalNavigationPadding 三重叠加造成底部大块空白

- **增加注入设置"设置"标签页内容**：
  - 此前第 3 个标签页是空白 `Unit`
  - 现在显示宿主应用名称、模块版本、已启用功能数量

- **清理过时 import**：
  - 删除 `IosLiquidGlassNavigationBar`、`rememberLayerBackdrop`、`MiuixTheme`、`NavigationItem`
  - 删除 `WindowInsets.navigationBars`、`CompositionLocalProvider`、`LocalNavigationPadding`
  - 删除 `Icon`、`MiuixIcons.*`、`FeatureCategory`、`Row`、`Alignment`等装饰性组件 import

### 本轮 UI 简化与底栏同步修复

- **移除过度图标装饰，恢复简洁高级视觉**：
  - 主应用首页删除圆形图标容器与 Favorites/Info 图标，改为纯排版驱动（28sp Bold 标题 + 留白）
  - 状态描述简化为"正在为 N 个宿主提供服务" / "请在 LSPosed 中激活模块并重启目标应用"
  - 版本号改为单行显示（15sp Medium）
  - Telegram 授权页删除说明卡片（Card + Row + Icon），改为单段 14sp 文字说明
  - 注入功能页删除每项功能的分类图标（Home/Messages/Lock/Tune/Info），改为纯 `BasicComponent` 列表
  - 统一视觉语言：靠字号、字重、留白和语义色构建层次，不再使用装饰性图标

- **修复底栏高亮不跟随滑动**：
  - 删除独立的 `selectedPage` 状态，底栏 `selectedIndex` 直接绑定到 `pagerState.currentPage`
  - 用户滑动切换页面时，底栏选中态现在会同步跟随

- **修复底栏动态高度计算**：
  - `InjectedGroupedPage` 改为动态读取系统导航栏 insets：`64.dp + 8.dp + WindowInsets.navigationBars.bottom`
  - 此前硬编码 76dp 会在有导航栏的设备上被底栏遮挡内容（实际高度可达 120dp）
  - 现在底部 padding 会根据导航栏实际高度自适应

- **增加注入设置页"设置"标签页内容**：
  - 此前第 3 个标签页是空白 `Unit`，现在显示宿主应用名称、模块版本、已启用功能数量
  - 三个底栏页面（首页/功能/设置）现在都有实际内容

- **清理过时 import**：
  - `HostSpecificPages.kt` 删除 `Icon`、`MiuixIcons.*`、`FeatureCategory`、`Row`、`Alignment`、`ImageVector`
  - `InjectedModuleSettings.kt` 删除 `mutableIntStateOf`、`getValue`、`setValue`

### 本轮全面 UI 优化

- **主应用首页视觉升级**：
  - 模块状态卡片增加图标装饰（已激活显示 Favorites，未激活显示 Info），提升视觉层次
  - 状态卡片右上角增加圆形图标容器，带 15% 透明背景和主题色图标
  - 底部状态指示改为圆点+文字组合样式（运行中/未激活 · 版本号）
  - 颜色系统优化：激活态使用绿色系（#1A3A2E / #E8F5E9），未激活使用红色系（#3A2428 / #FCE4EC）
  - 增加已启用宿主数量显示：`DAuxiliary 正在为 N 个已启用宿主提供服务`
  - 提示文案更加友好：未激活时提示检查 LSPosed/KernelSU 配置

- **Telegram 授权页重构**：
  - 标题改为"Telegram 功能授权"，更准确描述功能范围
  - 新增说明卡片：使用 `surfaceContainerHigh` 背景色，包含 Info 图标+标题+说明文案
  - 说明文案："填入授权码后，DAuxiliary 将兑换为模块凭据，用于 Telegram 自动签到等功能"
  - 授权接口标签改为"授权接口（可选）"，明确可选性
  - 成功/失败消息使用主题色/错误色区分，字号 14sp，Medium 字重
  - 失败消息更详细："授权失败，请检查授权码"

- **注入功能页全面重做**（已在上一轮完成）：
  - 按类别分组展示功能（HOME/CHAT/PRIVACY/BEAUTIFY/DEBUG）
  - 每项功能显示图标（Home/Messages/Lock/Tune/Info）+ 标题 + 说明 + 开关
  - 空状态处理：无功能时显示"暂无功能 · 该宿主应用的功能正在开发中"
  - 开关状态正确同步 remotePreferences，修复开关失效问题

- **注入设置页底栏修复**（已在上一轮完成）：
  - 移除 `Scaffold`，改为 `Box` 布局避免 insets 双重叠加
  - 底栏使用动态 insets：有导航栏时 `8.dp + navBarBottomPadding`，无导航栏时 `12.dp`
  - 功能页注入导航栏高度 `PaddingValues(bottom = 76.dp)`，避免内容被底栏遮挡
  - 底栏图标统一使用 `extended` 包：Home/Layers/Settings

### 本次回归修复

- 修复 admin 页面误用默认 Miuix `FloatingNavigationBar` 的回归，恢复管理端的 iOS-like 液态玻璃悬浮底栏、拖拽选中动画和模糊背景效果。
- 修复 NagramXF 点击 DAuxiliary 设置入口后的崩溃：注入层级的 `FrameLayout` 现在同时绑定 `ViewTreeLifecycleOwner` 和 `ViewTreeSavedStateRegistryOwner`，解决 NagramXF 在测量阶段从父容器查找 owner 时抛出的 `ViewTreeLifecycleOwner not found`。
- 使用 NagramXF 崩溃日志完成针对性验证；日志根因位于注入 `FrameLayout` 的 ViewTree owner 缺失，而不是设置入口反射或 Compose 内容路由错误。
### 本轮设置页与底栏修复

- 移除 app 与 admin 液态玻璃底栏在缺少导航栏 inset 时的固定 `36.dp` 底部占位，避免底栏下方空白区域遮挡注入页面和 admin 页面。
- 修复 NagramXF 环境中通过模块包名读取图标导致的 `PackageManager.NameNotFoundException`，入口图标改为直接读取宿主应用图标；配置读取增加模块包不可解析时的安全回退。
- Telegram 注入设置页收敛为三个底栏页面：首页仅显示版本信息并在未授权时弹出授权 BottomSheet，功能页仅保留功能名称与开关，设置页暂为空白。
- 删除设置页中与当前结构无关的说明文案、签到状态罗列和分类标题；功能开关直接写入对应宿主配置。
- 按 Miuix `0.9.4-rc01` 的实际 API 修正 `BasicComponent.endActions` 与 `MiuixIcons.Settings` 的调用。

### 本轮注入宿主与导航层修复

- 修复 NagramXF 最新崩溃：为注入的 Compose 根提供 `NavigationEventDispatcherOwner`，解决 `No NavigationEventDispatcher was provided via LocalNavigationEventDispatcherOwner`。
- 保持 dispatcher 与注入视图生命周期一致，在注入页面销毁时释放导航事件分发器。
- 重新整理 admin 与 Telegram 注入页的 iOS-like 悬浮底栏绘制层，移除造成双层观感的透明背景捕获副本，只保留单一导航胶囊和单一选中态。
- 恢复底栏合理的悬浮距离与横向留白，避免上一版贴近屏幕底部、视觉高度过低的问题。

### REA Android 逆向分析链路恢复
- 引擎 SHA-256 为 `6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75`，与 GitHub Release v0.7.1 官方校验值一致。
- 将 `REA_JADX_MCP_JAR` 固化为 `/root/.local/share/rea/jadx-headless-mcp-0.7.1-all.jar`，REA 新会话可直接发现 JADX 引擎。
- 通过 `rea setup --skill --yes` 安装并对齐 `reverse-engineer-anything` skill；skill version 42，包含 207 个工具及 Android、证据工作流、连接恢复、原生产物等参考文档。
- 通过 `rea doctor --provider jadx --skill` 和目标 APK 检查，确认 JADX 与 skill 均为 healthy。
- 使用生成的 `app-debug.apk` 执行端到端 `rea inspect-android-package`，成功读取包名、版本、SDK、权限、Manifest、类与资源信息：`com.dauxiliary`、version `0.1.0`、versionCode `1`、minSdk `33`、targetSdk `37`、class count `7656`、resource count `138`。
- 记录可复核的 REA evidence：`ev_50266b1ab812d68fda770286f21749e300ed197df4e7ef8df056b27e36677a65`。
- 说明 REA 健康检查的范围语义：Android/JADX 定向检查已经可用；Hopper、Ghidra、IDA 仍缺少各自的原生分析引擎或配置，因此全量 audit-wide 检查仍可能报告 `healthy: false`。这些可选后端不影响 APK 的 JADX 静态分析。
- 记录命令行限制：`rea doctor` 使用 `--provider`、`--skill`、`--target` 等定向参数，不支持 `--scope`；结构化 JSON 输出不能与 token window 参数同时使用。

### Admin 独立管理应用重构

- 重构 admin 入口与页面路由，新增 `AdminRoute`，将管理应用组织为清晰的页面状态和底栏导航结构。
- 接入 admin 模块的 iOS-like 液态玻璃悬浮底栏，包含拖拽选中动画、液态高光、模糊背景和底部安全区处理。
- 修正此前误将 admin 底栏切换回默认 `FloatingNavigationBar` 的回归；admin 不再使用默认 Miuix 底栏作为管理端主导航。
- 管理端底栏拆分为三个明确页面：`总览`、`用户授权`、`Bot 角色`。
- 为三个页面使用差异化 Miuix 图标，避免导航项视觉同质化，并修正图标 API 的导入与调用方式以匹配 Miuix `0.9.4-rc01`。
- 完善总览数据、用户授权操作、Bot 角色选择和授权撤销交互；管理页面保持宿主与功能项固定启用的产品约束。
- 新增并整理 `CombinedBackdrop`、`DampedDragAnimation`、`InnerShadow`、`InteractiveHighlight`、`Lens`、`Vibrancy`、`LiquidGlassNavigationBar` 等主应用仍使用的视觉资源，避免误删 app 侧液态玻璃实现。
- 调整 admin Gradle 配置以支持当前管理端页面与组件依赖。

### Telegram 客户端支持与自动签到

- 扩展 Telegram 宿主识别，覆盖官方客户端、官网版及已适配的第三方 Telegram-Android 变体；同时支持通过运行时类标记识别未知包名的兼容客户端。
- 管理页面根据实际发现的 Telegram 包读取显示名称与图标，允许多个 Telegram 客户端分别展示和管理。
- 增加运行时作用域申请逻辑，使已发现的 Telegram 客户端能够加入 LibXposed 注入范围。
- 重构 Telegram 设置入口注入：优先使用较新版本的原生设置项插入路径，兼容旧版 ProfileActivity 路径，并提供按语言设置行定位的视图回退路径。
- 增加入口去重和重复构建保护，避免 NagramXF 等客户端反复刷新设置列表时插入多个 DAuxiliary 入口。
- 将注入设置页面限制在当前 Telegram Activity 内打开，避免跨应用跳转到模块独立 Activity。
- 为注入的 Compose 内容提供独立且正确的 LifecycleOwner、ViewTreeSavedStateRegistryOwner 与 Compose 宿主状态，修复 NagramXF 中 ComposeView 生命周期和 SavedStateRegistryOwner 缺失导致的页面无法打开问题。
- 为 Telegram 请求发送、按钮学习、签到回调识别和消息结果处理补齐运行时链路，新增 `TelegramRequestSender`。
- 移植并整理 TGAutoSign 风格的纯决策逻辑到 `SignLogic`，识别签到按钮、规范对话 ID、判断成功/失败结果，并对失败请求执行退避重试。
- 将 `MAX_RETRIES` 提升到 `TelegramAutoSignHook.kt` 文件顶层，修复运行时状态类无法访问常量导致的编译错误，最大重试次数固定为 5 次。
- 按 Telegram 账号隔离授权、学习按钮、重试、进行中和待确认状态，避免多个账号之间共享签到状态。
- 增加 Telegram 专用偏好存储与授权状态同步；Telegram 授权仍是唯一需要模块授权的功能，其他宿主功能不受该授权门控影响。
- 增加授权码兑换、远程校验、授权撤销和已绑定 Telegram ID 的页面状态反馈，并明确区分模块授权与 Bot 管理员授权。
- 保留未知接口和无法确认的响应安全停止逻辑，避免对不兼容 Telegram 变体进行误操作。

### 测试、构建与产物

- 新增并扩展 `SignLogicTest`，覆盖签到按钮识别、成功与失败结果、ID 规范化、重试退避及耗尽状态等纯逻辑场景。
- 使用 JDK 21 完成 `:admin:assembleDebug`、`:app:assembleDebug` 和 `:app:testDebugUnitTest` 全量回归。
- 构建结果为 `BUILD SUCCESSFUL`；测试报告显示 `SignLogicTest` 共 5 项测试，failures `0`，errors `0`。
- 生成 admin Debug APK：`admin/build/outputs/apk/debug/admin-debug.apk`，约 32 MB。
- 生成主应用 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，约 35 MB。
- `git diff --check` 通过，当前发布目标分支为 `Test`。

### 已知限制

- 当前已验证的是 REA 的 Android/JADX 静态分析链路。Hopper 需要 `HOPPER_LAUNCHER_PATH`，Ghidra 需要匹配当前 Linux/arm64 环境的安装目录及 native decompiler，IDA 需要 `REA_IDA_MCP_CONFIG`；三者尚未安装或配置。
- APK 与 admin 已完成构建和单元测试验证；NagramXF 设置入口、注入页面生命周期以及 admin 三个底栏页面仍需要在实际目标设备上进行手工点击和显示验证。
