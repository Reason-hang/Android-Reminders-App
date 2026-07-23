# Android Reminders App

> 状态：当前开发基线
> 更新时间：2026-07-23
> 版本：v1.6（versionCode 7）
> 包名：`com.reminder.local`

Android 本地离线强提醒应用，目标设备为红米 K80 Pro / HyperOS / Android 16（API 36）。当前 v1.6 已完成代码层验证和 Release APK 打包，尚未完成目标真机产品验收。

## 目录

- [产品边界](#产品边界)
- [核心语义](#核心语义)
- [架构与工程约束](#架构与工程约束)
- [本次审计修复](#本次审计修复)
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
- 所有重复规则按设备当前时区计算；`triggerTime` 是重复模板，`nextTriggerTime` 是实际下一次时间。
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
- Room 结构变化必须同步 Model、Entity、DAO、Mapper、Migration、Schema 和测试。
- 不增加 `INTERNET`；PendingIntent 使用 immutable；业务组件默认不导出；日志不记录标题和备注正文。
- Release 当前使用 Debug 签名，只适合个人直装和测试。

## 本次审计修复

2026-07-23 审计确认并修复六项问题：

- 工作日规则不再单独硬编码北京时间，所有重复规则统一跟随设备时区。
- 标题最多 50 字符、备注最多 200 字符的限制下沉到领域 UseCase，避免未来非 UI 入口绕过。
- 列表页在首次进入及 `ON_RESUME` 时刷新精确闹钟权限状态，授权返回后横幅可立即更新。
- 从仓库 v3 历史源码生成并纳入 `3.json` Schema，新增 v3→v4 仪器迁移测试，覆盖 priority 移除和重复 `alarmId` 修复。
- v3→v4 脏数据迁移对所有非正或重复 `alarmId` 按主键顺序分配连续负值，避免取模、主键取反和已有负值造成唯一索引碰撞。
- Room 枚举 Converter 对未知持久化文本作保守降级，避免一条损坏或未迁移的旧记录终止整个查询 Flow。

## 当前验证状态

证据记录日期为 2026-07-23，对应当前 v1.6 源码：

| 证据层 | 当前结论 |
|---|---|
| JVM 测试 | 23 个测试类，79/79 通过，失败/错误/跳过均为 0 |
| Lint | `lintDebug` 无 error；保留 59 warnings、3 hints |
| Release | `assembleRelease` 实际通过 |
| APK | 包名 `com.reminder.local`、版本 `1.6 (7)`、未声明 `INTERNET`、v2 签名和 SHA-256 已复核 |
| Room 迁移仪器测试 | 已编译；无真机或模拟器，尚未实际执行 |
| 目标真机 | ADB 无设备；锁屏、后台、声音、震动、全屏、重启和并发场景未验收 |

因此当前状态是“代码层验证通过、完整产品验收未通过也未失败”，不能把自动化结果或 APK 验签写成红米 K80 Pro 真机通过。

## 构建与交付

需要 JDK 17 和 Android SDK 36：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew clean testDebugUnitTest lintDebug assembleRelease --console=plain
```

对外交付文件统一命名为 `ReminderApp-v1.6.apk`，并记录包名、版本、大小、签名方案和 SHA-256。不得提交 APK、keystore、密码、token 或 `local.properties`。

## 文档入口

完整文档按主题维护于 [文档总索引](docs/00-文档总索引.md)。接手任务先读 [当前交接](docs/04-项目交接/当前交接.md)，再按任务阅读审计、测试、验收和安全文档。
