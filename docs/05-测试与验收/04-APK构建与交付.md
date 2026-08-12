# APK 构建与交付

> 状态：v1.12 当前构建记录
> 构建日期：2026-08-12

## 目录

- [当前产物](#当前产物)
- [验证命令](#验证命令)
- [交付边界](#交付边界)

## 当前产物

| 项目 | 结果 |
|---|---|
| 构建产物 | `app/build/outputs/apk/release/app-release.apk`（对外交付文件为 `outputs/ReminderApp-v1.12.apk`） |
| 包名 | `com.reminder.local` |
| 版本 | `1.12 (13)` |
| minSdk / targetSdk | 31 / 36 |
| 文件大小 | 48,549,853 bytes |
| APK SHA-256 | `aed9cd9b0050a2b3b230b441c151629a0d2231055d70c5aeb2533061bd2dc49d` |
| 签名、包元数据、网络权限 | v2 Debug 签名有效；包名为 `com.reminder.local`，未声明 `INTERNET` |

`*.apk` 已被 `.gitignore` 排除，不进入源码仓库。

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
