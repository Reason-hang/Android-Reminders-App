# APK 构建与交付

> 状态：当前构建记录
> 更新时间：2026-07-22
> 版本：v1.3（versionCode 4）

## 目录

- [当前产物](#当前产物)
- [验证命令](#验证命令)
- [签名与交付边界](#签名与交付边界)

## 当前产物

2026-07-22 已记录的 Release 产物信息：

| 项目 | 结果 |
|---|---|
| 包名 | `com.reminder.local` |
| 版本 | `1.3 (4)` |
| minSdk / targetSdk | 31 / 36 |
| 文件大小 | 48,418,233 bytes |
| 签名 | v2 有效，Android Debug 证书 |
| SHA-256 | `960a6736368d14c415b0732dc2566e522910b67316c97015882e4123deef39c8` |
| 对外交付名 | `ReminderApp-v1.3.apk` |

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
