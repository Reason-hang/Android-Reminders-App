# 专业代码审计报告 - 2026-07-18

## 2026-07-22 验证更新

v1.3 已达到**代码层面验收通过**：20 个测试类、63 个 JVM 用例全部通过，`lintDebug` 与 `assembleRelease` 通过，APK 的包名、版本、权限、SHA-256 和 v2 签名已复核。本报告下方“待全量回归/待构建/待验签”的文字是 2026-07-18 的历史审计过程，应以本节为准。

未达到完整产品验收：`adb devices -l` 当前无设备，尚没有红米 K80 Pro 的覆盖安装、锁屏、后台、清任务、重启、声音/震动和全屏通知实测证据。代码层 B/C 级结论不能取代该 A 级验收。

独立复核还保留两项非阻断风险：完成/删除全部后若 `AlarmManager.cancel()` 极端抛错，当前实现会记录错误但数据库状态已提交，需在后续版本加入持久化取消补偿；v3→v4 Room 迁移已做代码和 Schema 审查，但缺少 `MigrationTestHelper` 仪器测试。两项均不得表述为“已真机验证”。

## 2026-07-18 历史结论（已失效）

以下是 2026-07-18 的历史结论，已被 2026-07-22 验证更新取代：**“源码已修复，尚未达到代码层面验收通过”**。

原因不是用一句“环境问题”概括：修改前基线曾实际通过 41 个 JVM 用例、Lint 和 Release 构建；本轮 TDD 新测试实际复现 8 个失败，修复到中间阶段剩余 2 个 JVM Log mock 失败，随后又完成代码和测试修改。目前 61 个用例、Lint、Release、APK 验签尚未重新执行，因为 Codex 平台在 2026-07-18 拒绝了 Gradle/ADB 所需的提权执行，返回“usage limit，2026-07-25 16:04 后重试”。因此不能把旧 APK 或旧构建证据当作 v1.3 交付证据。

证据等级：A=红米 K80 Pro 真机；B=自动化/构建/APK；C=完整代码路径和静态证据；D=高置信推断；E=待确认。

## 审计基线

| 项目 | 结果 |
|---|---|
| 仓库 | `Reason-hang/Android-Reminders-App`，`main` |
| 修改前提交 | `a1ed185` |
| 包名 | `com.reminder.local` |
| 当前版本 | v1.3，versionCode 4 |
| 数据库 | Room v4，Schema 已导出 |
| 主源码 | 66 个 Kotlin 文件 |
| 测试 | 20 个测试类，63 个 `@Test`；2026-07-22 实际通过 63/63 |
| 网络权限 | 无 `INTERNET` |
| 真机 | 2026-07-22 已获 ADB 授权，但 `adb devices -l` 无设备；尚未验收 |

## 问题统计

| 级别 | 数量 | 状态 |
|---|---:|---|
| S0 阻断 | 0 | 未发现 |
| S1 严重 | 8 | 源码已修改，待全量回归 |
| S2 高危 | 8 | 源码已修改，运行时项待真机 |
| S3 中危 | 6 | 源码已修改或明确接受 |
| S4 低危 | 3 | 源码已修改 |
| 建议项 | 4 | 记录为后续工作 |

## 关键问题矩阵

| ID | 级别 | 问题与用户现象 | 根因 | 实际修改 | 证据 |
|---|---|---|---|---|---|
| R-01 | S1 | 稍后提醒会再次推进重复周期，可能跳过下一次 | Snooze 被当成 `DUE` | 新增 `KIND_SNOOZE`，策略只允许 DUE 推进 | C；策略测试待重跑 |
| R-02 | S1 | 重复提醒到点后点“完成本次”会再推进一次 | Receiver 已推进，Action 又按新行推进 | Action 携带 occurrence；UseCase 识别已推进事件并保持下一周期 | C；新增 UseCase 测试待重跑 |
| R-03 | S1 | 旧通知的关闭可能停掉正在响的新提醒 | 无条件停止 Service，只比较 alarmId 或不比较实例 | 实例键扩展为 `alarmId + kind + occurrenceTime`；停止操作作用域化 | C；并发策略测试待重跑 |
| R-04 | S1 | 编辑失败后旧提醒可能彻底不再响 | 校验/DB 成功前先取消旧闹钟 | 先校验；替换调度失败恢复旧计划；DB 失败执行补偿 | C；失败用例已红灯复现，修复后待重跑 |
| R-05 | S1 | 完成/删除失败可能产生 PENDING 但无闹钟 | 先取消再持久化，失败缺少补偿 | 调整副作用顺序；返回 Boolean；失败不关闭 UI；恢复旧调度 | C；失败用例已红灯复现，修复后待重跑 |
| R-06 | S1 | 重复提醒列表正常但背后没有系统闹钟 | Receiver 先推进 DB，后续调度失败被吞 | occurrence 条件更新；调度失败取消残留并回滚 DB；记录日志 | C；真机调度失败仍需 A |
| R-07 | S1 | 通知存在但声音/震动/全屏后续步骤被跳过 | 单个 Android 副作用抛错中断整条链 | startForeground、PI、WakeLock、播放、通知、Activity 分段隔离并记录结果 | C；运行时需 A |
| R-08 | S1 | 提前量大于重复周期，第二次起提前提醒永远无法注册 | 下一次 advance 已落在当前 occurrence 之前 | 保存前校验并阻止无闭环配置 | C；新增校验测试待重跑 |
| R-09 | S2 | 两条提醒 alarmId 冲突会互相覆盖 | 随机生成只有查询，无数据库唯一约束 | v4 增加唯一索引；候选最多重试 100 次；迁移修复重复/零值 | C；迁移安装需 A/B |
| R-10 | S2 | 取消提醒后 Snooze 仍可能触发 | `cancel()` 只取消 due/advance | 调度器统一取消 due/advance/snooze | C；RequestCode 测试待重跑 |
| R-11 | S2 | 取消一条通知可能误删另一条 | 把 PendingIntent 的 `alarmId + 2` 当作 Notification ID 取消 | 删除错误取消；通知只按自身 ID 管理 | C |
| R-12 | S2 | “优先级”已从 UI 删除但仍污染数据模型 | Model/Entity/Mapper/Converter 遗留字段 | 完整删除并提供 v3→v4 非破坏迁移 | C；迁移需 A/B |
| R-13 | S2 | 锁屏亮屏但看不到标题/备注 | 服务通知和用户提醒通知职责混用；渠道/系统开关影响 | 服务通知私密低优先级；业务通知公开高优先级、BigText、publicVersion、FSI | C；实际锁屏需 A |
| R-14 | S2 | 前台服务失败时只有安静记录 | 降级路径没有保持响铃/震动产品语义 | 四个备用渠道按每条提醒开关补偿声音/震动并保留动作 | C；系统拒绝场景需 A |
| R-15 | S2 | 重启/强行停止后可能漏建，且一条失败可影响后续 | 批处理缺少每条异常隔离；恢复入口不足 | 每条独立捕获；增加应用更新、权限变化和用户打开 App 的恢复入口 | C；重启需 A |
| R-16 | S3 | 删除分类中断会留下半完成状态 | 重分配提醒与删除分类是两个事务 | DAO `@Transaction deleteAndReassign` | C |
| R-17 | S3 | 外部 Intent 可能尝试指定 reminderId | Launcher MainActivity 曾读取导航参数 | MainActivity 只打开列表；内部通知使用不导出的入口 Activity | C |
| R-18 | S3 | 本地数据可能被系统备份/迁移 | 只有 `allowBackup=false`，缺少新式规则 | 增加 `dataExtractionRules`，云备份和设备迁移全部排除 | C；Manifest 构建待 B |
| R-19 | S3 | 设置页底部权限入口不可达 | 长 Column 没有滚动 | 设置页增加 `verticalScroll` | C；布局需 A |
| R-20 | S3 | 连续点击不同通知仍打开旧提醒 | `singleTop` 入口未处理 `onNewIntent` | 使用状态驱动 reminderId，并处理新 Intent | C；交互需 A |
| R-21 | S3 | “工作日”和“周末”跨时区定义不一致 | 工作日固定北京时间，周末跟随设备时区 | 保留现有产品决定并写入风险；目标机北京时间无影响 | C，接受风险 |
| R-22 | S4 | 通知通道死代码误导排查 | 旧方案方法和渠道无调用者 | 删除无效渠道和方法，只保留实际链路 | C |
| R-23 | S4 | 调度失败没有诊断证据 | 多处仅处理成功分支 | 核心调度、播放、Receiver、恢复链增加结构化 Log | C |
| R-24 | S4 | README 将历史结果描述成当前事实 | 文档没有绑定版本和证据层级 | README 分离 v1.2 基线、v1.3 当前、构建和真机状态 | C |
| R-25 | S2 | 到点 Receiver 与编辑/完成/删除并发会整行覆盖 | 多入口基于旧 Reminder 直接 `@Update` | 当前行按 expected occurrence 条件更新；冲突提示重试，新增三条竞态回归测试 | C；测试待重跑 |
| R-26 | S1 | 旧的一次性提醒通知会完成已改期的新提醒 | 非重复分支未比较通知 occurrence 与当前有效时间 | 所有带 occurrence 的完成操作先比较 `occurrenceTime == current.effectiveTime`；旧事件仅结束自身打断，不改数据库或新闹钟 | B；回归测试已通过 |

## 重点问题说明

关键代码定位（行号对应本报告生成时的 v1.3 工作区）：

- Snooze 与三类触发：`AlarmSchedulerImpl.kt:126`、`AlarmTriggerPolicy.kt:7`、`AlarmReceiver.kt:67`。
- 重复 occurrence 条件推进：`AlarmReceiver.kt:112`、`ReminderDao.kt:29`。
- 完成/删除/编辑补偿：`CompleteReminderUseCase.kt:20`、`DeleteReminderUseCase.kt:18`、`EditReminderUseCase.kt:26`。
- 提前量与重复周期校验：`ReminderScheduleValidator.kt:6`。
- 旧操作实例隔离：`AlarmAlertConcurrencyPolicy.kt:9`、`AlarmAlertService.kt:466`、`NotificationActionReceiver.kt:27`。
- v3→v4 迁移：`AppDatabase.kt:47`、`DatabaseModule.kt:27`。
- 锁屏保留通知：`NotificationHelper.kt:146`、`AlarmReceiver.kt:172`。
- 开机恢复单条隔离：`RescheduleAllAlarmsUseCase.kt:32`。
- 分类事务：`CategoryDao.kt:38`。
- 内部通知入口：`ReminderEntryActivity.kt:14`、`AndroidManifest.xml:64`。
- 备份关闭：`AndroidManifest.xml:38`、`res/xml/data_extraction_rules.xml:1`。
- 设置页滚动：`SettingsScreen.kt:72`。

### [R-01][S1] Snooze 错误推进重复周期

- 文件：`AlarmSchedulerImpl.scheduleSnooze`、`AlarmTriggerPolicy`、`AlarmReceiver`。
- 旧行为：Snooze 使用 DUE 类型，触发后进入重复推进分支。
- 正确语义：Snooze 只产生一次新的强提醒，不修改正式时间或重复模板。
- 修复：独立 `KIND_SNOOZE`、独立 requestCode、独立展示文案；只有 DUE 返回 `shouldProgressRepeatingReminder=true`。
- 测试：`AlarmTriggerPolicyTest`、`AlarmSchedulerRequestCodeTest`。
- 真机：仍需验证稍后 10 分钟后能响，且数据库下一次不跳周期。

### [R-03][S1] 旧操作误停新提醒

- 文件：`AlarmAlertService`、`AlarmAlertConcurrencyPolicy`、`AlarmIntentIdentity`、`NotificationActionReceiver`、`AlarmActivity`。
- 触发：A 提醒未处理，B 或同提醒的新 occurrence 已开始；用户点击 A 的旧关闭动作。
- 修复：每个动作 URI 和 Service stop Intent 都带完整实例键。Service 仅在键完全相等时停止当前提醒。Action/Activity 不再直接无条件取消通知，由实例感知的 Service 处理。
- 回归风险：进程已死亡、系统仅保留旧通知时没有内存中的 currentInstance；需要真机测试快速连续点击和进程重建场景。

### [R-04/R-05/R-06][S1] 数据库与系统闹钟一致性

- 原因：Room 和 AlarmManager 不属于同一个事务，错误顺序或吞异常会留下僵尸提醒。
- 修复：校验先行；副作用返回明确成功/失败；替换失败恢复旧调度；DB 失败取消新调度；Receiver 用 occurrence 条件更新避免重复广播并发推进。
- 事实边界：这是补偿式一致性，不是 ACID 跨系统事务。极端系统崩溃窗口仍需靠开机/应用更新恢复和日志诊断。

### [R-13/R-14][S2] 锁屏内容与强提醒降级

- 用户通知设置为 `CATEGORY_ALARM`、`VISIBILITY_PUBLIC`、BigText、publicVersion、FullScreenIntent。
- 前台保活通知使用独立低优先级私密渠道，避免锁屏只显示“服务正在运行”。
- 手动声音或震动失败时，只对缺失媒介启用对应备用渠道，避免双重响铃。
- Android/HyperOS 最终展示受系统权限与策略控制，所以当前证据为 C，不是 A。

## 安全审计

已确认：无 INTERNET、无硬编码私钥/API Key/密码；PendingIntent 均为 immutable；业务组件最小导出；Room 无 destructive fallback；备份关闭；日志不记录标题和备注正文。

接受风险：锁屏公开标题/备注是产品要求；release 使用 debug 签名仅限个人直装。BootReceiver 已改为不导出，仅保留标准系统恢复 action 和 MIUI best-effort action；其在目标 HyperOS 上的实际送达仍需真机验证。

依赖 CVE 完整扫描未执行：当前环境无可用的联网依赖漏洞数据库。不能据此声称依赖“零漏洞”。

## 建议项

1. 增加 Room `MigrationTestHelper` 仪器测试，覆盖 v3 数据升级、优先级移除和 alarmId 冲突修复。
2. 使用用户自行保管的正式 keystore；不要把密钥或密码提交仓库。
3. 为 Android 组件增加 Robolectric/仪器测试，校验 Notification Action、Manifest 合并和 Intent extras。
4. 后续若用户跨时区使用，统一定义工作日/周末时区；若需要中国法定调休，必须引入本地节假日数据版本管理。

## 当前验收缺口

- 全量 61 个 JVM 测试尚未重跑。
- `lintDebug`、`assembleRelease`、APK 验签、badging、SHA-256 尚未执行。
- v3→v4 覆盖安装迁移尚未验证。
- 红米 K80 Pro 锁屏、后台、清任务、重启、权限变化、同时提醒尚未验证。

在这些证据补齐前，禁止写“代码层面验收通过”或“完整产品验收通过”。
