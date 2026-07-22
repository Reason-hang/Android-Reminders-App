# 变更记录 - 2026-07-18（v1.3）

## v1.3 专业审计追加（2026-07-18 历史记录）

### 2026-07-22 验证与交付更新

- 修复 `AlarmActivity.onNewIntent()` 所需 `android.content.Intent` 导入缺失的编译回归，并以 `AlarmActivitySourceContractTest` 固化该契约。
- 独立代码复核发现并修复：旧的一次性提醒通知在用户改期后，不能再完成或取消新提醒；新增回归测试。
- 当前测试为 20 个测试类、63 个 JVM 用例；实际结果为 63/63 通过，失败/错误/跳过均为 0。
- 已实际通过 `lintDebug` 和 `assembleRelease`；Lint 留有 55 个 warnings、3 个 hints，未阻断构建。
- APK 已复核为 `com.reminder.local`、`1.3 (4)`、48,418,233 bytes，SHA-256 为 `960a6736368d14c415b0732dc2566e522910b67316c97015882e4123deef39c8`。
- APK v2 签名有效，但签名证书为 Android Debug，交付定位为个人直装测试；生产发布须由用户保管正式 keystore。
- ADB daemon 已能启动，但本次设备列表为空，红米真机验收保持未通过状态。

- 版本升级为 `1.3 (4)`；最终 APK 必须命名为 `ReminderApp-v1.3.apk`。
- Snooze 改为独立 `KIND_SNOOZE`，不再推进重复周期。
- ADVANCE、DUE、SNOOZE 统一携带 occurrence；Receiver 只处理当前 occurrence，避免重复广播和旧闹钟推进。
- 重复提醒到点后再点“完成本次”不会二次推进。
- 旧通知操作使用 `alarmId + kind + occurrenceTime` 定位，不能无条件停止新提醒。
- 新增/编辑/完成/删除/重建闹钟补偿路径，调度或数据库失败不再静默留下僵尸提醒。
- 调度副作用分段隔离，一个步骤失败不再阻断声音、震动、通知和 Activity 的其他步骤。
- 完整移除 priority；Room 升级 v4，保留数据迁移并增加 alarmId 唯一索引。
- 新增应用更新和精确闹钟权限变化后的重建入口，开机恢复按单条隔离失败。
- 删除分类与提醒改为未分类放进同一 Room 事务。
- 外部 Launcher 不再接受 reminderId；通知详情使用不导出的内部 Activity。
- 关闭云备份和设备迁移；扩展敏感文件 `.gitignore`。
- 设置页支持垂直滚动，通知入口支持 `onNewIntent` 打开最新提醒。
- 以下条目均为当时的历史记录：测试扩展到 19 个类、61 个用例；最终全量执行受 Codex 平台提权用量限制阻塞，不能宣称通过。
- 新增 `AUDIT_REPORT_2026-07-18.md`、`BUILD_APK_REPORT_2026-07-18.md`、`PROJECT_MEMORY_2026-07-18.md`。

## 用户反馈

1. 提前 5 分钟时只有通知中心记录，没有亮屏、响铃、震动和全屏页；期望提前时和正式到点各触发一次完整强提醒。
2. 锁屏到点后虽然亮屏、响铃、震动，但锁屏没有标题和备注预览。
3. 后续交付 APK 文件名必须带版本号。

## 排查结论

- 当前主干中 `ADVANCE` 和 `DUE` 都进入 `AlarmAlertService`，且使用不同 `PendingIntent` requestCode；提前提醒不会消费或覆盖正式到点闹钟。
- “只有一条安静通知”的现象与旧降级路径一致：`startForegroundService()` 失败时只发布了使用静音渠道的普通通知，无法继续响铃或震动。
- 锁屏预览此前复用了前台服务的 `ongoing` 通知。厂商系统可能把它归为服务运行记录，不按普通用户提醒展示在锁屏；旧 `v2` 渠道的锁屏行为也可能已被系统持久化。
- 本次连接检查中，`adb devices -l` 为空，macOS USB 设备树也没有识别到手机，因此没有取得用户历史触发时的真机 logcat。以上是代码层已确认缺口，不冒充真机堆栈结论。

## 修复

1. 用户强提醒通知与前台服务保活通知拆分：服务通知走低重要性私密渠道，提醒通知走高重要性公开锁屏渠道。
2. 强提醒渠道升级为 `reminder_fullscreen_alert_v3`，避免旧渠道行为继续被系统固化。
3. 前台服务无法启动、`startForeground()` 失败或手动声音/震动部分失败时，按单条提醒配置选择四种备用渠道：响铃+震动、仅响铃、仅震动、静音；只补偿失败的媒介，避免重复响铃。
4. 备用通知继续携带公开锁屏预览和 FullScreenIntent，并使用 Android 14-16 对应的后台 Activity 启动授权主动拉起全屏页。
5. 强提醒通知被滑除时按“关闭”处理：停止当前打断，并重新保留一条静音通知记录。
6. 调度器新增日志，明确记录正式和提前闹钟各自的 `kind`、requestCode、triggerAt；Receiver 增加整体异常日志和备用强提醒日志。
7. 设置页增加直达强提醒渠道的入口，用于检查锁屏内容和横幅设置。
8. 版本升级为 `1.2 (3)`；交付 APK 命名为 `ReminderApp-v1.2.apk`。
9. 提前提醒和到点提醒的系统展示 PendingIntent 也使用不同 requestCode，避免后注册的提前提醒覆盖到点预览参数。
10. 若提前提醒尚未关闭，到点事件会停止上一轮播放并重新启动本轮响铃/震动，确保它仍是第二次独立强提醒。
11. 新增 `AlarmDeliveryPolicy`，把前台提升、声音、震动的实际结果收敛为可测试的降级决策。
12. 干净执行 `clean testDebugUnitTest assembleRelease`：13 个测试类、41 个用例全部通过，Release 构建和 Lint Vital 通过。
13. 完整 Debug Lint 暴露 Navigation 2.8.x 的官方已知检测器崩溃；升级稳定版 Navigation Compose 2.9.8，保留完整 Lint 检查而不是禁用规则。
14. 为 AlarmManager、全屏页和通知操作的 PendingIntent 增加包含 `reminderId + kind/action` 的唯一 URI，消除不同提醒之间的 requestCode 碰撞覆盖风险。
15. 手动响铃和震动分别捕获结果，某一媒介失败时只补偿该媒介，避免手动铃声与备用渠道再次同时响铃。

## 防复发规则

- 每种触发类型必须验证“调度、Receiver、Service、声音、震动、亮屏、Activity、锁屏通知”八段链路，不能用通知出现代替整条链路通过。
- 强提醒主链路和降级链路必须具有相同产品语义；降级可以降低持续性，但不能静默丢掉用户已开启的声音和震动。
- 前台服务保活通知与用户业务通知职责分离，不能用一条 `ongoing` 通知同时承担锁屏预览。
- 提前提醒必须有独立 requestCode，并通过测试证明不会覆盖正式到点提醒。
- 通知渠道行为改变时升级渠道 ID，并在设置页提供直达渠道设置的入口。
- 没有真机日志时必须明确写“代码根因/高置信度判断”，不能宣称已完成真机根因确认。
- APK 交付文件名必须使用 `ReminderApp-v<version>.apk`，禁止再交付无法区分版本的通用文件名。
