# APK 构建与交付

> 状态：v1.17 当前构建记录
> 构建日期：2026-08-22

## 当前产物

| 项目 | 结果 |
|---|---|
| 对外交付文件 | `outputs/ReminderApp-v1.17.apk` |
| 原始构建文件 | `app/build/outputs/apk/release/app-release.apk` |
| 包名 / 版本 | `com.reminder.local` / `1.17 (18)` |
| minSdk / targetSdk | 31 / 36 |
| 文件大小 | 48,730,077 bytes |
| APK SHA-256 | `d7407737253afa5064fe4d531a36404ecd185494987ca0e262b485164a8a96f5` |
| 签名 | v2 有效，Android Debug 证书；不适合应用商店正式发布 |
| 网络权限 | `aapt dump permissions` 未发现 `INTERNET` |

## 验证命令

```bash
./gradlew assembleRelease --no-daemon --console=plain \
  -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false

aapt dump badging outputs/ReminderApp-v1.17.apk
aapt dump permissions outputs/ReminderApp-v1.17.apk
apksigner verify --verbose --print-certs outputs/ReminderApp-v1.17.apk
shasum -a 256 outputs/ReminderApp-v1.17.apk
```

## 交付边界

- APK、AAB、keystore、密码、token 和 `local.properties` 不提交仓库。
- 本轮未执行 ADB，未覆盖安装到红米；安装后必须读回 `versionName=1.17`、`versionCode=18`，并按 [真机验收清单](./02-真机验收清单.md) 执行数据升级、排序和强提醒场景。
- 用户提供正式 keystore 后再建立正式签名、覆盖升级和回滚验证流程。
