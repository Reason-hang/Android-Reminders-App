# Android Reminders App

> 状态：当前开发基线
> 更新时间：2026-07-22
> 版本：v1.3（versionCode 4）
> 包名：`com.reminder.local`

Android 本地离线强提醒应用，目标设备为红米 K80 Pro / HyperOS / Android 16（API 36）。当前代码已完成代码层验证，但尚未完成目标真机产品验收。

## 目录

- [产品边界](#产品边界)
- [核心语义](#核心语义)
- [架构与工程约束](#架构与工程约束)
- [当前验证状态](#当前验证状态)
- [构建与交付](#构建与交付)
- [文档入口](#文档入口)

## 产品边界

- 简体中文、单用户、完全离线。
- 数据保存在本机 Room/SQLite，不包含账号、网络、广告、统计、云同步或远程服务。
- 支持提醒 CRUD、分类、重复、提前提醒、稍后 10 分钟、完成/删除、开机和权限变化后的闹钟恢复。
- 不包含账号、多端同步、云备份、桌面 Widget、自定义铃声、语音输入和节假日调休日历。

## 核心语义

- `ADVANCE`、`DUE`、`SNOOZE` 是独立事件；只有 `DUE` 可以推进重复周期。
- `triggerTime` 是重复模板，`nextTriggerTime` 是实际下一次时间；月末重复不能永久漂移。
- 所有闹钟、通知和全屏操作携带 `reminderId + alarmId + kind + occurrenceTime`，旧事件不能停止或修改新事件。
- “关闭”停止当前打断但保留静音通知；“稍后”和“完成”结束当前打断并移除当前通知。
- Room 与 AlarmManager 不构成跨系统事务，新增、编辑、完成、删除和恢复必须有失败补偿与日志。

## 架构与工程约束

```text
Compose UI → ViewModel → UseCase → Repository interface → Room DAO / SQLite

AlarmScheduler → AlarmManager → AlarmReceiver → AlarmAlertService
                                      ├─ 声音与震动
                                      ├─ WakeLock
                                      ├─ 锁屏业务通知
                                      └─ FullScreenIntent → AlarmActivity
```

- 模块单向依赖，内容、配置和逻辑分离。
- Room v4 结构变化必须同步 Model、Entity、DAO、Mapper、Migration、Schema 和测试。
- 不增加 `INTERNET`；PendingIntent 使用 immutable；业务组件默认不导出；日志不记录标题和备注正文。
- Release 当前使用 Debug 签名，只适合个人直装和测试。

## 当前验证状态

证据记录日期为 2026-07-22，对应当前 v1.3 代码基线：

| 证据层 | 当前结论 |
|---|---|
| JVM 测试 | 20 个测试类，63/63 通过 |
| Lint | `lintDebug` 通过；保留 55 warnings、3 hints |
| Release | `assembleRelease` 通过 |
| APK | 包名、版本、未声明 `INTERNET`、v2 签名和 SHA-256 已复核 |
| 目标真机 | ADB 无设备；锁屏、后台、声音、震动、全屏、重启和并发场景未验收 |

因此当前状态是“代码层验证通过、完整产品验收未通过也未失败”，不能把自动化结果写成红米 K80 Pro 真机通过。

## 构建与交付

需要 JDK 17 和 Android SDK 36：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew clean testDebugUnitTest lintDebug assembleRelease --console=plain
```

对外交付文件统一命名为 `ReminderApp-v1.3.apk`，并记录包名、版本、大小、签名方案和 SHA-256。不得提交 APK、keystore、密码、token 或 `local.properties`。

## 文档入口

完整文档按主题维护于 [文档总索引](docs/00-文档总索引.md)。接手任务先读 [当前交接](docs/04-项目交接/当前交接.md)，再按任务阅读测试、验收和安全文档。
