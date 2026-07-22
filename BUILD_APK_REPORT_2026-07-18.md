# 构建与 APK 验证报告 - 2026-07-18

> 当前 APK 路径、哈希、签名结论和最新提交以 [HANDOFF_CURRENT.md](HANDOFF_CURRENT.md) 为准；下方 2026-07-18 的阻塞描述均为历史过程。

## 2026-07-22 最终执行更新

本文件下方“尚未生成/待执行/平台阻塞”均是 2026-07-18 的历史记录。2026-07-22 已在当前 v1.3 源码执行：

```text
./gradlew testDebugUnitTest lintDebug assembleRelease --rerun-tasks --console=plain
BUILD SUCCESSFUL
```

最终 APK：`app/build/outputs/apk/release/app-release.apk`，大小 `48,418,233 bytes`；`aapt dump badging` 已确认包名 `com.reminder.local`、版本 `1.3 (4)`、minSdk 31、targetSdk 36，且未声明 `INTERNET`。

`apksigner verify --verbose --print-certs` 已确认 v2 签名有效。当前签名证书为 Android Debug（RSA 2048），仅适合个人直装和测试，不适合商店发布；不要把 keystore 或密码提交到仓库。

```text
SHA-256: 960a6736368d14c415b0732dc2566e522910b67316c97015882e4123deef39c8
对外交付名: ReminderApp-v1.3.apk
```

## 2026-07-18 历史结论（已失效）

以下是 2026-07-18 的历史状态：v1.3 本轮修改后的 Release APK 当时尚未生成。仓库中历史 `app-release.apk` 或 `outputs/ReminderApp-v1.2.apk` 均不是本轮源码产物，禁止重命名后冒充 v1.3。

## 已取得的构建证据

修改前基线命令：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew clean testDebugUnitTest lintDebug assembleRelease --console=plain
```

实际结果：`BUILD SUCCESSFUL in 3m45s`；41 个 JVM 用例、Debug Lint、Release 构建通过。该结果对应 v1.2 / versionCode 3 的修改前基线，只用于证明工具链当时可用。

## 本轮阻塞证据

完成 v1.3 修复后重新请求运行 Gradle/ADB 时，Codex 平台自动审批拒绝，原始核心信息：

```text
Automatic approval review failed: You've hit your usage limit.
try again at Jul 25th, 2026 4:04 PM.
The agent must not attempt to achieve the same outcome via workaround...
```

ADB 在沙箱内直接启动时的原始核心信息：

```text
Android Debug Bridge version 1.0.41
Version 37.0.0-14910828
could not install *smartsocket* listener: Operation not permitted
adb: failed to check server version: cannot connect to daemon
```

因此当前不是“代码编译失败”，也不能表述为“代码已构建成功”；准确状态是 **后置构建未获准执行**。

## 2026-07-18 待执行命令（历史）

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew clean testDebugUnitTest lintDebug assembleRelease --console=plain
```

构建成功后必须继续执行：

```bash
/opt/homebrew/share/android-commandlinetools/build-tools/36.0.0/apksigner \
  verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
/opt/homebrew/share/android-commandlinetools/build-tools/36.0.0/aapt \
  dump badging app/build/outputs/apk/release/app-release.apk
shasum -a 256 app/build/outputs/apk/release/app-release.apk
```

最终交付步骤：

```text
源：app/build/outputs/apk/release/app-release.apk
交付名：ReminderApp-v1.3.apk
```

## 2026-07-18 待填写结果（历史）

| 项目 | 结果 |
|---|---|
| Gradle | 待执行 |
| JVM 测试 | 61 个待执行 |
| Lint | 待执行 |
| Release assemble | 待执行 |
| 包名 | 预期 `com.reminder.local`，待 APK 复核 |
| 版本 | 预期 1.3 / 4，待 APK 复核 |
| 签名 | 预期 debug v2，待验签 |
| 文件大小 | 待生成 |
| SHA-256 | 待生成 |
| 最终 APK 路径 | 待生成 |
