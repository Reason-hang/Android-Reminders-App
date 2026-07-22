# 主集成验收报告 - 2026-07-14（历史 v1.1/v1.2 记录）

> 本文件是历史集成证据，不代表当前 v1.3 状态。当前结论请先阅读 [HANDOFF_CURRENT.md](HANDOFF_CURRENT.md)，再按其中顺序阅读审计、测试、构建和真机验收文档。

## 输入与范围

- 输入包：`其他AI完成的提醒事项APP研发.zip`。
- 源码包 SHA-256：`cc2874f46652fb83186a1284c24b9252d46829b89dacb32955f0b2403d6cfff5`。
- 集成基线：Git `main`，提交 `4b119d5`。
- 合并内容：强提醒链路诊断与降级通知、HyperOS 权限引导、顶部保存按钮、重复提醒“仅本次完成”、调度失败日志、通知遗留代码清理，以及项目文档。

## 主集成复核

| 项目 | 结果 | 证据 |
|---|---|---|
| 外部包完整性 | 通过 | 源码包 SHA-256 与随包 `CHECKSUMS_2026-07-10.txt` 一致 |
| Kotlin / Hilt / Room 编译 | 通过 | `./gradlew testDebugUnitTest assembleRelease` 返回 `BUILD SUCCESSFUL` |
| JVM 单元测试 | 通过 | `testDebugUnitTest` 成功执行 |
| Release 构建 | 通过 | `assembleRelease` 成功生成 APK |
| APK 签名 | 通过 | `apksigner verify --verbose --print-certs`：v2 为 `true` |
| 明文密钥与网络权限 | 通过静态复核 | 无 `INTERNET` 权限，未发现硬编码密钥 |
| 真机锁屏强提醒 | 待验收 | 本次 ADB 未发现已授权设备 |

## 主集成补充修复

外部交付把 `alarmId` 改为随机数，降低了时间戳同毫秒碰撞概率，但没有确认该随机值是否已被其他提醒使用。主集成新增 `ReminderDao.isAlarmIdInUse()`、Repository 对应接口和 `nextAvailableAlarmId()`：候选值已存在时重新生成，最多 100 次；并增加 `availableAlarmIdSkipsAnIdAlreadyUsedByAnotherReminder` 回归测试。

## 交付 APK

- 路径：`../outputs/app-release.apk`
- SHA-256：`8578719d7fe5ed3863a804bab8d16081c64f2e38033588275abf5f1f41f9c6ce`
- 签名：Android Debug（个人直装测试可用，不适合上架）。

## 未关闭项

真机需要按 `REAL_DEVICE_VERIFICATION_2026-07-10.md` 验收：后台与锁屏场景下的亮屏、全屏页、锁屏内容预览、响铃、震动、提前提醒及正式提醒两次触发、重启恢复。若失败，按 README 的 logcat 命令采集 `AlarmReceiver` 与 `AlarmAlertService` 日志后再定位。
