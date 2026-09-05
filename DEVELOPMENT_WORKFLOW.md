# DAuxiliary Development Workflow

这份文档用于后续长期开发和 GitHub 协作。DAuxiliary 的代码同时运行在两个环境：模块配置页运行在 `com.dauxiliary` 进程，Xposed Hook 和抖音内入口运行在 `com.ss.android.ugc.aweme` 进程。提交代码前必须明确改动影响哪一个环境。

## 1. 分支流程

推荐使用以下分支：

- `main`：可构建、可测试的稳定主线。
- `feature/<name>`：新功能或新 Hook。
- `fix/<name>`：问题修复。
- `build/<name>`：依赖、Gradle 或 CI 调整。
- `docs/<name>`：仅文档修改。

开发步骤：

1. 从最新 `main` 创建工作分支。
2. 阅读 `AI_DEVELOPMENT_NOTES.md`，确认 Miuix 和 LSPosed 约束。
3. 先定位目标代码和调用关系，再做小范围修改。
4. 本地运行 Debug 构建。
5. 检查 diff，确认没有本地路径、密钥、日志、APK 或抖音资源。
6. 推送分支并创建 Pull Request。
7. 等待 GitHub Actions 构建成功，再合并到 `main`。

## 2. 功能 Hook 流程

新增一个抖音功能时按以下顺序进行：

1. 明确目标功能、目标抖音版本和目标进程。
2. 在独立的 feature/hook 类中实现，不把业务逻辑直接写入 `EntryHook`。
3. 给功能定义稳定的配置 key 和独立开关。
4. 在 Hook 安装前检查开关、包名、进程和版本条件。
5. 保护重复安装，避免 Activity 重建导致重复 Hook 或重复 View。
6. 使用异常边界包裹反射、第三方对象调用和 UI 注入。
7. 记录失败原因并安全降级，不能因为模块异常阻塞抖音启动。
8. 验证关闭开关、重启抖音、切换 Activity 和多进程场景。
9. 更新 README 的当前状态、兼容性和已知限制。

## 3. UI 开发流程

模块配置 Activity 和抖音内控制入口是两个不同的 UI 场景：

- 配置 Activity：使用 Miuix Compose 组件和主题；底部导航使用官方 Miuix `FloatingNavigationBar` API。
- 抖音内入口：保持轻量，只负责打开控制面板和修改配置，不承载完整的模块配置应用。

新增或修改 UI 时：

1. 先确认当前 Miuix 版本的真实 API 签名。
2. 优先使用已有 Miuix basic、preference、icon 和 theme 组件。
3. 不用 Material 导航组件替代 Miuix 悬浮底栏。
4. 检查系统 Insets、深色模式、Activity 重建和不同屏幕尺寸。
5. 不能在抖音进程中依赖模块 Activity 的生命周期或状态。

## 4. 本地验证

最小构建：

```bash
./gradlew :app:assembleDebug --console=plain
```

判断标准：

- 必须看到最终的 `BUILD SUCCESSFUL`。
- `BUILD FAILED`、超时、被取消或缺少最终输出都不能视为成功。
- APK 必须是本次构建生成的文件，不能只检查旧产物。

可选验证：

```bash
./gradlew :app:check
./gradlew :app:lintDebug
```

涉及 Xposed 的验证必须在真实 LSPosed 设备或明确的测试环境中进行。普通 Gradle 构建只能证明 APK 能打包，不能证明 Hook 能适配当前抖音版本。

## 5. GitHub Actions 流程

`.github/workflows/build.yml` 会在以下情况执行 Debug 构建：

- 推送到 `main` 或 `master`。
- 创建或更新 Pull Request。
- 在 Actions 页面手动执行 `workflow_dispatch`。

构建成功后，Actions 页面会生成 `DAuxiliary-debug` artifact。该 artifact 是未发布的 Debug APK，只用于测试，不代表 Release 版本。

## 6. 发布前流程

Release 前必须单独完成：

1. 确认目标抖音版本和 Android/LSPosed 兼容范围。
2. 完成真机回归和禁用/卸载测试。
3. 配置 GitHub Secrets 中的签名信息，不把密钥提交到仓库。
4. 增加受保护的 Release 构建 workflow。
5. 检查 APK 签名、版本号、作用域元数据和 changelog。
6. 明确许可证和第三方依赖声明。

在这些步骤完成前，不要把 Debug artifact 当作正式发行包。