# APK 构建与交付

> 状态：v1.7 当前构建记录
> 构建日期：2026-08-10

## 目录

- [当前产物](#当前产物)
- [验证命令](#验证命令)
- [交付边界](#交付边界)

## 当前产物

| 项目 | 结果 |
|---|---|
| 对外交付名 | `ReminderApp-v1.7.apk` |
| 包名 | `com.reminder.local` |
| 版本 | `1.7 (8)` |
| minSdk / targetSdk | 31 / 36 |
| 文件大小 | 48,434,617 bytes |
| 签名 | APK Signature Scheme v2，Android Debug 证书 |
| 证书 SHA-256 | `75f7f5d3d941a646abdd9c981afedbdba6215a8e0533a94dfebd3d950fee2b1c` |
| APK SHA-256 | `3ed7146d91c10ecccac4398e63deebba14e36d5014d94ccffb4003b70fb81aa6` |
| 网络权限 | 未声明 `INTERNET` |

本地交付文件位于仓库工作目录的 `outputs/ReminderApp-v1.7.apk`，但 `*.apk` 已被 `.gitignore` 排除，不进入源码仓库。

## 验证命令

```bash
./gradlew testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleRelease
```

```bash
/opt/homebrew/share/android-commandlinetools/build-tools/36.0.0/aapt \
  dump badging app/build/outputs/apk/release/app-release.apk

/opt/homebrew/share/android-commandlinetools/build-tools/36.0.0/aapt \
  dump permissions app/build/outputs/apk/release/app-release.apk

/opt/homebrew/share/android-commandlinetools/build-tools/36.0.0/apksigner \
  verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk

shasum -a 256 app/build/outputs/apk/release/app-release.apk
```

## 交付边界

- Debug 签名适合当前个人直装和覆盖升级测试，不适合应用商店或长期生产信任链。
- 正式签名必须由用户保管 keystore，并通过受控环境注入密码。
- APK、AAB、keystore、密码、token、`.env` 和 `local.properties` 不提交仓库。
- 对外发送 APK 时同时提供版本、包名、文件大小和 SHA-256；安装后再从设备侧确认版本。
