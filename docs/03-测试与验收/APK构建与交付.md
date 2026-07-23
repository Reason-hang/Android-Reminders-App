# APK 构建与交付

> 状态：当前构建记录
> 更新时间：2026-07-23
> 版本：v1.4（versionCode 5）

## 目录

- [当前产物](#当前产物)
- [验证命令](#验证命令)
- [签名与交付边界](#签名与交付边界)

## 当前产物

2026-07-23 已记录的 Release 产物信息：

| 项目 | 结果 |
|---|---|
| 包名 | `com.reminder.local` |
| 版本 | `1.4 (5)` |
| minSdk / targetSdk | 31 / 36 |
| 文件大小 | 48,418,233 bytes |
| 签名 | v2 有效，Android Debug 证书 |
| SHA-256 | `517b9924cc6200525a20aa6826e50abe77ee2f23079dd7bcb71d4ca16d0a099e` |
| 对外交付名 | `ReminderApp-v1.4.apk` |

## 验证命令

```bash
./gradlew testDebugUnitTest lintDebug assembleRelease --rerun-tasks --console=plain

$ANDROID_HOME/build-tools/36.0.0/apksigner verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk

$ANDROID_HOME/build-tools/36.0.0/aapt dump badging \
  app/build/outputs/apk/release/app-release.apk

shasum -a 256 app/build/outputs/apk/release/app-release.apk
```

## 签名与交付边界

当前 Debug 签名只适合个人直装和覆盖升级测试，不适合商店发布或建立长期生产信任链。正式发布前必须使用用户安全保管的 keystore，并在受控环境注入密码；不得将 APK、keystore、密码、token 或 `local.properties` 提交到仓库。
