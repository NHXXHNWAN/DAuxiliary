# Contributing to DAuxiliary

感谢参与 DAuxiliary 开发。这个项目是一个面向抖音的 LSPosed 模块，修改目标应用时必须优先保证可禁用、可回滚和不影响抖音正常启动。

## 开发前

- 阅读 `README.md` 和 `AI_DEVELOPMENT_NOTES.md`。
- 确认改动属于模块配置页、抖音进程入口、配置层或具体功能 Hook。
- 查阅当前 Miuix 依赖的实际 API，不凭旧版本示例猜测组件签名。
- 不提交抖音 APK、反编译文件、用户数据、设备日志或签名文件。

## 架构约束

### 配置页

- 模块自身 Activity 使用 Miuix Compose UI。
- 页面导航使用官方 Miuix `FloatingNavigationBar` 和 `FloatingNavigationBarItem`。
- 新增设置优先复用现有 Miuix preference 组件和主题，不混入 Material UI。

### Xposed Hook

- `EntryHook` 只做目标包过滤、基础初始化和功能分发。
- 目标包固定为 `com.ss.android.ugc.aweme`，除非需求明确变更。
- 每个功能使用独立类或模块，不能把大量业务逻辑塞进入口类。
- Hook 安装必须幂等，Activity 重建或多进程加载不能重复添加 View 或重复 Hook。
- 反射和第三方应用调用必须有异常边界；模块出错不能让抖音崩溃。
- 每个功能必须有明确的关闭行为，并在关闭时尽量不安装对应 Hook。

### 配置同步

- 配置通过 `ConfigStore` 管理。
- 写入和读取必须使用稳定的 key，并考虑模块进程与抖音进程的可见性。
- 修改配置后说明是否需要重启抖音或重新加载 Hook。

## 验证流程

本地最小验证：

```bash
./gradlew :app:assembleDebug --console=plain
```

必须根据 Gradle 最终输出确认 `BUILD SUCCESSFUL`；超时、被取消或只有旧 APK 都不能视为构建成功。

涉及 UI 时还应检查：

- 模块 Activity 能正常启动。
- 深色/浅色模式没有明显崩溃或布局问题。
- 悬浮底栏和页面内容不被系统 Insets 遮挡。

涉及 Hook 时还应检查：

- 非目标包不会执行模块逻辑。
- 抖音冷启动、Activity 切换和旋转/重建不会重复注入。
- 关闭开关后功能不生效。
- Hook 异常只记录日志，不阻止抖音启动。
- 至少在一个真实 LSPosed 测试环境中验证。

## 提交规范

建议使用清晰的 Conventional Commits 风格：

```text
feat: add a Douyin feature hook
fix: prevent duplicate in-process overlay
refactor: isolate config access
build: update Miuix dependency
docs: update development workflow
```

提交前：

1. 查看 `git diff`，确认没有本地路径、密钥或大文件。
2. 运行 Debug 构建。
3. 更新相关文档和待办状态。
4. 一个提交只解决一个主题，避免把无关格式化混入功能改动。

## Pull Request 建议

PR 描述至少包括：

- 改动目的和影响范围。
- 涉及的抖音版本、Android 版本和 LSPosed 版本（如适用）。
- 构建命令及结果。
- 真机验证步骤和已知限制。
- 是否需要重启抖音或重新启用作用域。

## 版本兼容

抖音内部实现会变化。不要把某个版本的混淆类名、资源 ID 或 Activity 层级当作永久 API。新 Hook 应尽量使用版本检测、候选类名、特征探测和失败降级，并在文档中记录验证范围。