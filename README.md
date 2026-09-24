# DAuxiliary

DAuxiliary 是一个面向抖音 Android 客户端的 LSPosed 模块。项目的长期目标是在抖音进程内提供可扩展的功能 Hook，并通过模块自身的配置页管理开关。

> 当前阶段是基础骨架：已接入 Xposed/LSPosed 入口、抖音内测试入口、配置读写和 Miuix 配置界面；具体抖音功能尚未实现。

## 项目定位

DAuxiliary 不是一个独立的抖音客户端，也不修改或重新分发抖音本体。APK 主要承担两部分职责：

1. 作为 LSPosed 模块安装，并将 `EntryHook` 注册到抖音进程。
2. 提供模块配置页；实际功能代码在抖音进程中按开关加载。

目标包名：`com.ss.android.ugc.aweme`

模块包名：`com.dauxiliary`

## 当前状态

- [x] Gradle Android 项目骨架
- [x] LSPosed/Xposed 元数据与 `assets/xposed_init`
- [x] 仅匹配抖音包名的 Xposed 入口
- [x] 抖音进程内测试入口与配置面板骨架
- [x] 模块进程与抖音进程之间的配置读写骨架
- [x] Miuix Compose 主题和配置页面
- [x] Miuix `FloatingNavigationBar` / `FloatingNavigationBarItem` 配置页导航
- [ ] 实际抖音功能 Hook
- [ ] 功能注册表和按功能隔离的 Hook 生命周期
- [ ] 真机验证不同抖音版本、进程和 ROM 行为
- [x] GitHub Actions Debug/Release 构建与 Tag 发布流程
- [ ] Release 签名配置（需要通过 GitHub Secrets 提供 keystore）

## 技术栈

- Kotlin
- Android Gradle Plugin + Gradle Wrapper
- Jetpack Compose
- Miuix Compose UI
- LSPosed/Xposed API（`compileOnly`，运行时由 LSPosed 提供）
- Android SDK 37（当前 Compose/Miuix 依赖的 AAR 元数据要求）

版本以 `gradle/libs.versions.toml` 为准。Miuix API 仍可能更新，升级版本时必须先核对官方 API 和 Android 编译要求，再修改依赖。

## 目录结构

```text
app/src/main/
├── AndroidManifest.xml
├── assets/xposed_init
├── kotlin/com/dauxiliary/
│   ├── core/config/ConfigStore.kt
│   ├── core/xposed/EntryHook.kt
│   ├── core/xposed/DouyinEntryHook.kt
│   └── ui/
│       ├── MainActivity.kt
│       ├── page/
│       ├── theme/
│       └── widgets/DAuxiliaryApp.kt
└── res/

.github/workflows/build.yml       # GitHub Actions Debug 构建
AI_DEVELOPMENT_NOTES.md            # 长期开发约束
CONTRIBUTING.md                     # 开发和提交规范
gradle/libs.versions.toml          # 依赖版本目录
```

## 构建与发布

推送到 `Test` 分支会自动构建 Debug 和 Release APK，并将 APK 保存为 GitHub Actions artifact。

要发布 GitHub Release，创建并推送一个版本标签：

```bash
git tag v0.1.0
git push origin v0.1.0
```

标签发布会自动创建同名 GitHub Release，并上传 Debug/Release APK。当前 Release APK 尚未配置签名，只适合测试；正式分发前应通过 GitHub Secrets 配置 keystore，避免将签名私钥提交到仓库。

环境要求：

- JDK 17 或更高版本
- Android SDK，至少安装当前 `compileSdk` 对应的平台
- 可访问 Google Maven、Maven Central 或项目配置的镜像仓库

Linux/macOS：

```bash
chmod +x ./gradlew
./gradlew :app:assembleDebug
```

Windows：

```bat
gradlew.bat :app:assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

如果在 ARM64 Linux/proot 环境构建，可按需执行 `setup_android_env.sh`。该脚本用于准备 Android SDK、Gradle 和 ARM64 AAPT2；它会修改本机环境，不建议在 GitHub Actions 中执行。

## 安装和使用边界

1. 在已安装 LSPosed 的测试设备上安装 Debug APK。
2. 在 LSPosed 管理器中启用 DAuxiliary，并将作用域限定为抖音。
3. 强制停止并重新打开抖音，观察模块入口是否出现。
4. 具体功能开关只在后续功能实现并完成真机验证后启用。

当前代码不会保证兼容所有抖音版本。抖音的类名、Activity 层级、混淆结果和进程结构可能变化；任何新 Hook 都必须提供版本判断、重复安装保护、异常隔离和关闭开关后的安全行为。

## 安全与开发原则

- 只在目标包 `com.ss.android.ugc.aweme` 中安装模块 Hook。
- Xposed 入口保持轻量；具体功能放入独立 Hook/feature，并避免互相耦合。
- Hook 代码必须使用 `runCatching` 或等效异常边界，不能因模块异常导致抖音启动崩溃。
- 不在仓库提交 `local.properties`、签名密钥、设备信息、构建缓存或本地日志。
- 不提交抖音 APK、反编译产物、用户数据或任何受版权保护的应用资源。
- 每个功能都应有明确的开关、禁用路径和最小化的 Hook 范围。

## License

当前尚未确定开源许可证。许可证确定前，不要在仓库页面声明项目已按某个许可证发布。