# 变更说明 — 2026-07-10

本轮由 Claude 处理，输入是用户反馈的 3 个问题：

1. 提前 5 分钟提醒到点时，只有通知中心一条记录，没有响铃/震动/弹窗，只有手动打开 App 才看得到。
2. 锁屏后到点提醒，有亮屏/震动/响铃，但锁屏界面没有通知预览内容。
3. 每次保存提醒都要滑动到表单底部才能点到保存按钮。

排查方式：完整审阅 `part-01-source` 源码（未接触真机、未拿到 logcat，详见 README「已知限制」）。
详细根因分析和防复发规则见 `README.md` 「故障复盘与修复记录」第 9、10 条，这里只列改了什么、为什么改。

## 改了什么

### 1. `app/src/main/java/com/reminder/local/notification/NotificationHelper.kt`

- 删除 `showReminderNotification()` / `showFullScreenReminderNotification()` /
  `showAdvanceReminderNotification()` 三个方法——经 `grep` 全项目确认从未被任何调用方引用。
- 删除 `CHANNEL_SOUND_VIBRATE` / `CHANNEL_SOUND_ONLY` / `CHANNEL_VIBRATE_ONLY` /
  `CHANNEL_SILENT` / `CHANNEL_ADVANCE` 五个从未使用的通知渠道，只保留真正在用的
  `CHANNEL_FULLSCREEN_ALERT`。
- 移除构造函数里未被使用的 `CategoryRepository` 依赖（原本只服务于被删除的死代码）。

为什么改：这些方法/渠道是早期方案（提前提醒走"普通通知"分支）的遗留物，替换成
`AlarmAlertService` 强提醒链路之后没有被清理。风险是 `CHANNEL_ADVANCE` 在系统设置里显示名
就是"提前提醒"，排查问题时极易被误认成生效中的渠道，浪费排查时间；同时也是明确的死代码，
不符合代码质量要求。

### 2. `app/src/main/java/com/reminder/local/service/AlarmAlertService.kt`

- `onStartCommand()` 里 `startForeground()` / `wakeScreen()` / `startAlert()` /
  `launchAlarmActivity()` 四步分别包一层 `runCatching + Log.e`，互不影响。
- 去掉主通知上的 `.setSilent(true)`。
- 新增 `TAG` 常量和多处 `Log.d`/`Log.e`。

为什么改：四步之前是裸调用，任何一步抛异常都会让后面的步骤全部静默跳过，且没有任何日志，
这能直接解释"通知栏有记录但没有其他反应"的症状。`setSilent(true)` 在不同厂商 ROM 上
对横幅/全屏优先级的处理不完全一致，属于不必要的风险点，去掉后不会引入双重响铃
（渠道本身已经是无声音/无震动）。

### 3. `app/src/main/java/com/reminder/local/receiver/AlarmReceiver.kt`

- `onReceive()` 增加关键节点日志。
- `startForegroundService()` 包一层 `runCatching`，失败时调用新增的
  `postFallbackNotification()` 发一条锁屏可见、带全屏 Intent 的兜底通知。
- 移除从未被使用过的 `notificationHelper` 注入字段。

为什么改：Android 12+ 在少数系统状态下会拒绝后台启动前台服务
（`ForegroundServiceStartNotAllowedException`），之前完全没有保护，一旦被拒绝用户什么反馈都收不到。

### 4. `app/src/main/java/com/reminder/local/util/PermissionUtils.kt`

- 新增 `notificationSettingsIntent()`：跳转系统标准的"应用通知设置"页
  （`ACTION_APP_NOTIFICATION_SETTINGS`），锁屏通知显示开关通常在这里。
- 新增 `miuiAppPermissionEditorIntent()`：跳转 MIUI/HyperOS 的权限管理页（锁屏显示、悬浮窗等）。

为什么改：设置页之前只引导用户开"精确闹钟""全屏提醒""省电策略""自启动"，
唯独漏了"锁屏显示通知"和 MIUI 专属的"锁屏显示/悬浮窗"权限，而这几项和电池策略/自启动
是完全独立的开关。

### 5. `app/src/main/java/com/reminder/local/ui/screen/settings/SettingsScreen.kt`

- 新增"锁屏通知与后台弹窗"引导区块，说明文案+两个跳转按钮。
- "小米/红米机型提示"文案补充说明"省电策略/自启动"不能替代"锁屏显示/悬浮窗"。

### 6. `app/src/main/java/com/reminder/local/ui/screen/edit/EditReminderScreen.kt`

- 删除表单底部与顶部栏功能重复的"保存"按钮。
- 顶部栏"保存"按钮从纯文字 `TextButton` 改为带勾选图标的实心 `Button`，提升可见度。
- 新增 `SnackbarHost`，保存失败提示（`generalError`）从页面底部文字改为 `Snackbar`，
  无论滚动到哪里都能看到。

为什么改：之前"顶部栏 + 底部"两个保存按钮同时存在（README 里记录为"兼容原使用习惯"），
用户反馈说明这个"兼容"恰恰是问题所在——两个按钮都能保存时，用户大概率还是在用回底部那个。

### 7. `README.md`

- 新增「已知限制」「给下一轮排查的诊断信息」两个章节。
- 「构建与安装」下新增「2026-07-10：本轮（Claude 沙箱环境）构建结果」，如实记录
  `./gradlew` 因网络白名单限制无法下载 Gradle 发行版的证据。
- 「故障复盘与修复记录」新增第 9 条（本轮具体排查记录）和第 10 条（抽象出的通用防复发规则）。

## 未改动，但排查中确认没问题的部分

- `AlarmSchedulerImpl`：`setAlarmClock()` + 双闹钟（到点/提前）调度逻辑正确，未发现问题。
- `AlarmTriggerPolicy`：`shouldStartStrongAlert()` 对到点/提前都返回 `true`，逻辑正确。
- `BootReceiver` / `RescheduleAllAlarmsUseCase`：开机重启恢复闹钟逻辑正确，含重复提醒
  "追赶到未来"处理。
- `AdvanceReminderCalculator`：提前时间计算逻辑正确。
- 所有现有单元测试文件（`grep` 确认）都不引用本轮删除的死代码，不受影响。

## 未做的事情（诚实说明）

- 没有编译验证（沙箱无 Android SDK、无法访问 Google/Gradle 仓库，见 README「已知限制」）。
- 没有产出新的 `app-release.apk`。
- 没有真机安装/真机场景验证。
- 没有新增单元测试（本轮改动集中在系统交互相关代码，超出纯 JVM 单元测试可覆盖范围）。

以上几项需要用户在有 Android SDK 和网络访问的机器上执行
`./gradlew testDebugUnitTest assembleRelease`，安装到真机后按 README「给下一轮排查的诊断信息」
里的步骤验证。

---

# 第二轮变更 — 2026-07-10（对照完整 README 的全项目复查）

用户要求"对照整份 README 文档，全面检查是否有 bug、故障或风险"。这一轮把 58 个源文件基本过了
一遍，详细发现和根因见 `README.md`「故障复盘与修复记录」第 11、12 条，这里只列改了什么文件。

## 改了什么

### 1. `AlarmActivity.kt`
- `markDoneAndClose()`：`completeReminderUseCase.markDone(reminder)` 改成显式传
  `RepeatActionScope.ONCE`（原来用默认值 `ALL`，会把重复提醒整个停掉）。
- `snoozeAndClose()`：`scheduleSnooze` 失败时补 `Log.e`。

### 2. `receiver/NotificationActionReceiver.kt`
- `ACTION_MARK_DONE` 同上，改成显式传 `RepeatActionScope.ONCE`。
- `ACTION_SNOOZE` 调度失败补 `Log.e`。

### 3. `domain/usecase/CompleteReminderUseCase.kt`
- `markDone()`"仅本次"分支、`markPending()`：调度失败补 `Log.e`（原来只有
  `onSuccess`，失败时完全静默）。

### 4. `domain/usecase/DeleteReminderUseCase.kt`
- "仅删除本次"分支：调度失败补 `Log.e`。

### 5. `domain/usecase/RescheduleAllAlarmsUseCase.kt`
- 整个重建流程补日志（开始/结束、精确闹钟权限缺失时跳过了哪条、非重复/重复提醒各自的
  调度失败）。

### 6. `domain/alarm/AlarmSchedulerImpl.kt`
- `generateAlarmId()`：`System.currentTimeMillis() % Int.MAX_VALUE` 改成
  `Random.nextInt(1, Int.MAX_VALUE)`，避免同一毫秒创建两条提醒时 alarmId 碰撞。
- `buildShowPendingIntent()`：新增 `kind` 参数，状态栏"下一个闹钟"预览按实际
  到点/提前/稍后提醒传值，不再写死 DUE。

## 为什么改

详见 README 第 11.1～11.4 条，核心是两类问题：

1. **"标为完成"在全屏提醒页/通知栏对重复提醒的默认行为和列表页不一致**（高危，已修复）：
   会在用户没有明确选择的情况下，把一条重复提醒整体停掉。
2. **调度失败静默吞掉、无日志**（中危，已修复）：和第一轮修复的"触发阶段静默失败"是
   同一类问题，这次在"调度阶段"的 4 处遗留调用点也补上了。

## 复核后确认没有问题的部分（详见 README 第 11.6 条）

数据库迁移、月末 31 号裁剪逻辑、编辑页删除的仅本次/全部选择、分类管理校验、
AndroidManifest 权限与组件导出设置、DI 模块无循环依赖、无硬编码密钥、Entity/Model/Mapper
字段一致性。

## 发现但本轮未改动、需要用户决策的部分

`RepeatCalculator` 里"工作日"重复类型特意固定用北京时间（有专门单元测试覆盖，是有意设计），
但"周末"类型用的是设备系统时区，两者不一致；红米 K80 Pro 正常使用不会触发，只有手动改系统
时区才可能出现分歧。这是产品选择题，不是明确的 bug，留给用户决定要不要统一。

## 静态自检

- 全部改动文件括号配对自检通过。
- 全项目 `grep` 确认改动涉及的符号（`markDone` 调用点、`generateAlarmId`、
  `buildShowPendingIntent`）没有被现有单元测试引用，本轮改动不会导致现有测试编译失败。
- 仍然没有真实编译（环境限制见 README「已知限制」），本轮改动同样需要用户在自己的机器上跑
  一遍 `./gradlew testDebugUnitTest assembleRelease` 才能拿到 `BUILD SUCCESSFUL` 证据。

---

# 主集成更新 — 2026-07-14

- 在主集成机重新执行 `testDebugUnitTest assembleRelease`，已取得 `BUILD SUCCESSFUL`。
- 新增 `alarmId` 本地数据库去重：随机候选 ID 如已被已有提醒使用，会重新生成，最多重试 100 次后才失败；避免 PendingIntent requestCode 碰撞覆盖已有闹钟。
- 新增回归测试：`availableAlarmIdSkipsAnIdAlreadyUsedByAnotherReminder`。
- 真机验收未完成：主集成时 ADB 未发现已授权设备，锁屏、全屏、响铃、震动等系统级行为仍需按真机表格验收。
