# 项目故障记忆与防复发规则 - 2026-07-18

本文件是提醒 App 的工程记忆。后续任何 AI 或开发者改动 Alarm、通知、重复、Room 或 APK 交付前都应先读。

> 当前状态以 [HANDOFF_CURRENT.md](HANDOFF_CURRENT.md) 为准。2026-07-22 的当前代码证据为 63/63 JVM 测试、`lintDebug`、`assembleRelease` 和 APK v2 验签通过；红米真机尚未连接，不能将这些证据写成锁屏/后台验收通过。

## 已发生过的错误

1. 把“通知中心有记录”误当成强提醒链路成功，漏查声音、震动、亮屏、全屏页。
2. 提前提醒和正式到点复用错误类型，提前事件推进了重复周期。
3. Receiver 已推进重复后，通知/全屏页“完成本次”再次推进，跳过下一周期。
4. Snooze 被当成 DUE，再次推进重复。
5. 编辑、完成、删除先取消旧闹钟，后续校验或 DB 失败使提醒永久丢失。
6. DB 已写入下一次时间，但 AlarmManager 注册失败且异常被吞，形成僵尸提醒。
7. 旧通知动作无条件停止 Service，误杀后来正在响的提醒。
8. 只用随机 alarmId，没有数据库唯一约束，仍存在覆盖风险。
9. 修改了 UI 字段却没有同步 Entity/Mapper/Migration，导致保存或升级失败。
10. 通知渠道 ID 不变，旧渠道设置被 Android 持久化，代码修改不生效。
11. 用前台服务通知同时承担用户锁屏预览，HyperOS 只显示服务记录或不显示内容。
12. 单个系统副作用抛错后阻断整个提醒链，且没有足够日志。
13. README 用历史构建结果覆盖当前状态，造成“文档说通过、实际未验证”。
14. 交付通用名 APK，无法判断用户安装的是哪个版本。
15. 旧的一次性提醒通知在用户改期后仍可完成或取消新提醒。

## 强制规则

### 触发类型

- ADVANCE、DUE、SNOOZE 必须是独立 kind、独立 requestCode。
- 只有 DUE 允许推进重复。
- SNOOZE 不修改 `triggerTime`、`nextTriggerTime` 或重复模板。
- 提前提醒量必须小于重复周期，否则保存时明确拒绝。

### 实例隔离

- 所有 Alarm/Activity/Notification Action 使用稳定 URI 身份。
- 用户操作至少携带 `reminderId + alarmId + kind + occurrenceTime`。
- 禁止无实例的 `stopService()`；旧 occurrence 操作不得停止当前 occurrence。
- 同一通知 ID 下，取消动作应由实例感知的服务执行，不能在 Action Receiver 里无条件取消。
- 非重复提醒也必须校验通知的 `occurrenceTime == current.effectiveTime`；改期后的旧操作不准改数据库或取消新闹钟。

### 数据一致性

- 任何校验必须在取消旧闹钟之前完成。
- DB 与 AlarmManager 使用补偿事务：一边失败要恢复/撤销另一边。
- 调度失败必须返回失败给 UI，不能关闭页面伪装成功。
- Receiver 推进必须使用 occurrence 条件更新，防止重复广播和并发操作二次推进。
- 开机恢复按单条隔离异常，一条坏数据不得阻断后续提醒。
- 用户打开 MainActivity 时执行一次重建，用于修复强行停止后被系统清掉的闹钟；禁止在 `Application.onCreate` 异步重建，以免 AlarmReceiver 拉起进程时并发覆盖 occurrence。

### Room 变更

- 同步检查 Model、Entity、Converter、Mapper、DAO、Repository、UseCase、ViewModel、UI、Migration、Schema、测试。
- 禁止 `fallbackToDestructiveMigration`。
- 字段删除也必须升级数据库版本并提供迁移。
- alarmId 必须有数据库唯一索引；随机数只能降低概率，不能替代约束。

### 强提醒验收

每种触发类型分别检查：

```text
Alarm 注册 → Receiver → FGS → 响铃 → 震动 → WakeLock → FSI/Activity → 锁屏通知
```

构建成功不能替代真机。没有红米 K80 Pro 的截图/录屏/logcat，不得把锁屏、后台、清任务、重启等场景标为通过。

### 通知

- 保活通知与用户业务通知分离。
- 锁屏业务通知需要 public visibility、publicVersion、BigText、alarm category、高重要性渠道。
- 通知渠道行为变化时升级 channel ID；旧渠道不会被代码可靠覆盖。
- “关闭”保留静音通知记录；“稍后/完成”移除当前通知。

### 安全与交付

- 不增加 INTERNET，除非产品定义正式改变并完成隐私审计。
- PendingIntent 默认 immutable；组件默认不导出。
- 不记录标题、备注等用户正文到日志。
- 不提交 keystore、密码、token、local.properties、build、APK。
- APK 对外交付名必须为 `ReminderApp-v<version>.apk`。
- 每次交付记录包名、版本、大小、签名方案、SHA-256 和构建命令。

## 证据口径

- 源码已改、单测已过、Release 已构建、APK 已验签、普通真机已测、红米 K80 Pro 已测是六个不同状态。
- 任何报告都必须写明对应版本和提交，不能借用旧版本证据。
- 平台/权限阻塞要附原始命令和错误，不得写模糊的“环境原因”。
