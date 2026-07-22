# 自动化测试报告 - 2026-07-18

> 当前测试结论、代码基线和未完成项以 [HANDOFF_CURRENT.md](HANDOFF_CURRENT.md) 为准；下方日期明确标为历史的内容只保留复盘价值。

## 2026-07-22 最终执行更新

已获授权实际执行 `./gradlew clean testDebugUnitTest lintDebug assembleRelease --console=plain --warning-mode=none`，随后从 Gradle XML 汇总：**63 passed / 0 failed / 0 error / 0 skipped**，对应 20 个测试类。独立代码复核发现“旧的一次性提醒通知可完成已改期提醒”后，先新增失败测试、再修复并重跑完整验证；该用例已经包含在 63 项结果中。此前文中“最终回归阻塞”“61 个待执行”均为 2026-07-18 的历史过程，不再代表当前源码状态。

`lintDebug` 已执行完成。报告包含 55 个 warnings 和 3 个 hints（包括可升级依赖、KAPT、Compose API 弃用和未使用资源），没有 lint error，未阻断构建；它们仍是后续维护项。

## 2026-07-18 历史结论（已失效）

以下是当时的过程记录，不代表当前结论：v1.3 当时静态统计为 19 个测试类、61 个 `@Test`。本轮采用 TDD 先新增失败用例并真实执行，成功暴露 8 个历史缺陷；修复后中间回归剩余 2 个 `android.util.Log` 在本地 JVM 未 mock 的失败，代码已改为安全日志封装。其后新增的完整 61 个用例当时尚未获准重新执行。

## 实际执行记录

### 1. 修改前基线

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew clean testDebugUnitTest lintDebug assembleRelease --console=plain
```

结果：`BUILD SUCCESSFUL in 3m45s`；13 个测试类、41 个用例通过。证据只对应修改前 v1.2 基线。

### 2. TDD 红灯

新增失败测试后执行 `testDebugUnitTest`：48 个用例中 8 个失败，覆盖：

- 领域模型仍含 priority；
- 完成本次调度失败后旧状态/旧闹钟未保持；
- 完成全部 DB 失败却取消旧闹钟；
- 删除本次调度失败后旧状态/旧闹钟未保持；
- 删除全部 DB 失败却取消旧闹钟；
- 编辑 DB 失败后旧闹钟未恢复；
- 非法重复截止日期仍取消旧闹钟；
- Snooze 没有被识别为独立强提醒。

这些失败是本轮修复的实际复现证据，不是静态推测。

### 3. 中间回归

第一次修复后再次执行：48 个用例剩余 2 个失败，均因失败分支直接调用 `android.util.Log.e`，Android 本地 JVM stub 抛出 “Method e in android.util.Log not mocked”。随后已把领域 UseCase 日志改为 `runCatching` 安全封装。

### 4. 最终回归阻塞

计划执行：

```bash
./gradlew clean testDebugUnitTest lintDebug assembleRelease --console=plain
```

平台拒绝信息：

```text
Automatic approval review failed: You've hit your usage limit.
try again at Jul 25th, 2026 4:04 PM.
```

按平台要求没有通过替代目录、替代 daemon 或其他方式规避限制。

## 当前测试清单

| 测试类 | 主要覆盖 |
|---|---|
| `AddReminderUseCaseTest` | 新增、过期时间、alarmId 冲突重试 |
| `EditReminderUseCaseTest` | 校验失败不取消旧闹钟、DB 失败恢复旧闹钟 |
| `CompleteReminderUseCaseTest` | 仅本次/全部失败补偿、Receiver 已推进后不二次推进 |
| `DeleteReminderUseCaseTest` | 仅本次/全部失败补偿 |
| `RescheduleAllAlarmsUseCaseTest` | 单条失败隔离、重复追赶、权限缺失、结束日期 |
| `RepeatCalculatorTest` | 全部重复类型、31→2 月裁剪→3 月恢复、闰年、北京时间工作日 |
| `AdvanceReminderCalculatorTest` | 固定和自定义提前时间 1-200、月份单位 |
| `ReminderScheduleValidatorTest` | 提前量不小于重复周期时拒绝保存 |
| `ReminderModelContractTest` | priority 不再属于领域模型 |
| `AlarmTriggerPolicyTest` | ADVANCE/DUE/SNOOZE 强提醒与周期推进策略 |
| `AlarmSchedulerRequestCodeTest` | 三种 kind 的操作/展示 requestCode 隔离 |
| `AlarmAlertConcurrencyPolicyTest` | 多提醒切换、旧实例操作隔离 |
| `AlarmIntentIdentityTest` | reminder/kind/occurrence/action URI 唯一性 |
| `AlarmAlertContentFormatterTest` | 锁屏标题、备注、提前和稍后文案 |
| `AlarmDeliveryPolicyTest` | FGS/声音/震动部分失败的精确降级 |
| `AlarmNotificationPolicyTest` | 服务、强提醒和四类备用渠道隔离 |
| `AlarmAlertInteractionPolicyTest` | 关闭保留、完成/稍后移除通知 |
| `AlarmAlertLaunchPolicyTest` | WakeLock 时限和全屏启动策略 |
| `WheelPickerSelectionTest` | 双列滚轮中心项和边界选择 |
| `AlarmActivitySourceContractTest` | `onNewIntent` 所需 `Intent` 导入的编译契约，防止同类回归 |
| `CompleteReminderUseCaseTest`（追加） | 旧的一次性提醒通知不能完成或取消已改期的提醒 |

## 静态验证

本轮已实际执行并通过：

- `git diff --check`；
- `xmllint --noout` 检查 Manifest 和数据提取 XML；
- `jq empty` 检查 Room v4 Schema JSON；
- 敏感信息正则扫描；
- 禁止项扫描：INTERNET、FLAG_MUTABLE、destructive migration、主线程 Room；
- 源码全局扫描确认 priority 不再出现在 Kotlin 业务代码。

静态检查是 C 级证据，不能替代 JVM、Lint、Release 或真机结果。本次 JVM、Lint 和 Release 已有 B 级实际证据；真机仍缺 A 级证据。

## 尚缺测试

- Room v3→v4 `MigrationTestHelper` 仪器测试；
- Notification/Manifest/Receiver 的 Android 仪器测试；
- 红米 K80 Pro 上的锁屏、后台、清任务、重启、并发和通知操作；
- APK 覆盖安装后旧数据完整性。
