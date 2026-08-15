# APK 构建与交付

> 状态：v1.13 当前构建记录
> 构建日期：2026-08-16

## 当前产物

| 项目 | 结果 |
|---|---|
| 对外交付文件 | `outputs/ReminderApp-v1.13.apk` |
| 原始构建文件 | `app/build/outputs/apk/release/app-release.apk` |
| 包名 / 版本 | `com.reminder.local` / `1.13 (14)` |
| minSdk / targetSdk | 31 / 36 |
| 文件大小 | 48,680,925 bytes |
| APK SHA-256 | `5abbcadf6aa3c0ae6d7ba144b1c83a5d6a95ae35f144c9216d7fe23a7849ad6a` |
| 签名 | v2 有效，Android Debug 证书；不适合应用商店正式发布 |
| 网络权限 | `aapt dump permissions` 未发现 `INTERNET` |

## 验证命令

```bash
./gradlew assembleRelease --no-daemon --console=plain \
  -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false

aapt dump badging outputs/ReminderApp-v1.13.apk
aapt dump permissions outputs/ReminderApp-v1.13.apk
apksigner verify --verbose --print-certs outputs/ReminderApp-v1.13.apk
shasum -a 256 outputs/ReminderApp-v1.13.apk
```

## 交付边界

- APK、AAB、keystore、密码、token 和 `local.properties` 不提交仓库。
- 当前没有 ADB 连接，未覆盖安装到红米；安装后必须读回 `versionName=1.13`、`versionCode=14`，并按 [真机验收清单](./02-真机验收清单.md) 执行 D1–D8 和强提醒场景。
- 用户提供正式 keystore 后再建立正式签名、覆盖升级和回滚验证流程。
