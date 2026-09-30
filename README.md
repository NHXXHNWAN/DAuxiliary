# DAuxiliary

DAuxiliary 是一个基于 LibXposed API 102 的 Android 宿主增强模块，使用 Kotlin、Jetpack Compose 与 Miuix 构建。

## 特性

- 多宿主配置：抖音、微信、QQ 分离管理。
- 宿主内设置入口：设置页面在宿主进程内打开，不跨应用跳转。
- QQNT 防撤回：实验性功能，仅拦截已识别的撤回系统推送。
- API 102 生命周期：支持模块加载、宿主分发与热重载状态清理。
- Monet 图标：提供自适应图标与单色图标资源，支持系统动态取色。

## 支持范围

| 宿主 | 包名 | 状态 |
| --- | --- | --- |
| 抖音 | `com.ss.android.ugc.aweme` | 宿主入口框架 |
| 微信 | `com.tencent.mm` | 宿主入口框架 |
| QQ | `com.tencent.mobileqq` | 以 QQ 9.3.60 为分析基线 |

具体功能是否可用取决于宿主版本、LSPosed、ROM 和作用域配置。未经真机验证的功能不视为稳定支持。

## 安装

1. 安装 GitHub Actions 生成的 APK，或自行使用 JDK 21 构建。
2. 在 LSPosed 中启用 DAuxiliary，并限定到目标宿主。
3. 强制停止并重新启动宿主应用。
4. 从宿主原生设置入口打开 DAuxiliary 页面。

## 构建

```bash
chmod +x ./gradlew
./gradlew :app:assembleDebug
```

Release 构建由 CI 签名并启用 R8 与资源压缩。目标分支为 `Test`。

## 目录

```text
app/src/main/kotlin/com/dauxiliary/
├── core/config/       # 配置与远程偏好
├── core/feature/      # 功能注册与分发
├── core/xposed/       # LibXposed 入口与宿主 Hook
└── ui/                # 模块页与宿主内 Compose 页面
```

## 注意

- 关闭功能后需要重启对应宿主进程。
- QQ 防撤回属于实验性功能，请使用测试账号验证。
- 日志提交前请删除账号、聊天内容、群组和设备标识。
- 仓库不保存签名材料、APK 或访问令牌。