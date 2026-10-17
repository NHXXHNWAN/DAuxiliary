# 更新日志

## 2026-10-18

### REA Android 逆向分析链路恢复

- 补齐 REA 使用 JADX 进行 Android 静态分析所需的 `jadx-headless-mcp` 引擎，使用 `jadx-headless-mcp-0.7.1-all.jar`。
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
- 接入 Miuix 官方 `FloatingNavigationBar` 及对应导航项，移除 admin 原有自绘 `AdminLiquidNavigationBar` 实现。
- 将 admin 页面收敛为单一滚动内容结构，处理底栏与页面内容之间的间距，降低切换时的闪退、遮挡和布局粘连风险。
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
