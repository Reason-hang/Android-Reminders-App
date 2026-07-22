# 红米 K80 Pro 真机验收表 - v1.3 待执行

目标设备：红米 K80 Pro，HyperOS，Android 16 / API 36。

当前状态：**全部待验证**。2026-07-22 已获 ADB 验收授权并成功启动 daemon，但 `adb devices -l` 返回空列表，因此仍没有设备序列号、logcat、截图或录屏证据。不得把本表任何空项写成通过。

> 当前代码、APK 哈希和未完成项见 [HANDOFF_CURRENT.md](HANDOFF_CURRENT.md)。本表只记录 A 级真机证据；JVM/Lint/构建通过不改变任何“待验证”单元格。

## 验收信息

| 项目 | 记录 |
|---|---|
| APK 文件 | `ReminderApp-v1.3.apk`（待设备连接后安装） |
| APK SHA-256 | `960a6736368d14c415b0732dc2566e522910b67316c97015882e4123deef39c8` |
| 测试日期/时间 | 2026-07-22：仅 ADB 连接检查，未进入设备测试 |
| 手机型号 | 待 `adb shell getprop ro.product.model` |
| Android 版本 | 待 `adb shell getprop ro.build.version.release` |
| API | 待 `adb shell getprop ro.build.version.sdk` |
| HyperOS 版本 | 待 `adb shell getprop ro.build.display.id` |
| App 版本 | 待 `dumpsys package com.reminder.local` |
| 测试人 | 待填写 |

## 前置权限

| 检查项 | 预期 | 实测 |
|---|---|---|
| 通知 | 允许 | |
| 强提醒渠道 | 高重要性、横幅、锁屏显示内容 | |
| 全屏提醒 | 允许 | |
| 精确闹钟 | 允许 | |
| 锁屏显示 | 允许 | |
| 后台弹出界面 | 允许 | |
| 自启动 | 允许 | |
| 省电策略 | 无限制 | |
| 系统闹钟音量 | 大于 0 | |

## 安装与迁移

| ID | 场景 | 预期 | 结果/证据 |
|---|---|---|---|
| M1 | 全新安装 v1.3 | 安装成功，首次启动无崩溃，生成四个内置分类 | |
| M2 | v1.2 已有提醒上覆盖安装 v1.3 | 安装成功，旧提醒/分类/响铃/震动/重复/提前设置保留 | |
| M3 | 查看迁移后的提醒 | UI 无优先级；保存和编辑正常；无 Room schema 崩溃 | |
| M4 | 快速连续新建多条提醒 | 均保存成功，`alarmId` 不重复 | |

## CRUD 与 UI

| ID | 场景 | 预期 | 结果/证据 |
|---|---|---|---|
| U1 | 新增、编辑、删除 | 数据和闹钟同步更新，无“保存失败” | |
| U2 | 标记完成、撤销完成 | 状态正确，失败时页面给出提示 | |
| U3 | 顶部保存 | 打开编辑页一屏可见并可点，无需滚到底部 | |
| U4 | 时间滚轮 | 小时+分钟同时显示，分钟 1 格，快速滑动后中心值准确 | |
| U5 | 设置页 | 可滚动到最底部并打开自启动/权限入口 | |
| U6 | 连续点击不同提醒通知 | 每次进入对应提醒，不停留在上一条 | |

## 强提醒核心场景

每个场景至少测试两次：一次 App 最近使用过，一次划掉最近任务并等待触发。

| ID | 场景 | 预期 | 结果/证据 |
|---|---|---|---|
| A1 | 锁屏正式到点 | 亮屏、响铃、震动、全屏页；锁屏可预览标题和备注 | |
| A2 | App 后台正式到点 | 响铃、震动；锁屏时全屏；通知中心有业务通知 | |
| A3 | App 被划掉正式到点 | 与 A2 一致，不依赖 App UI 已打开 | |
| A4 | 手机已解锁正在使用 | 响铃、震动；系统允许时全屏，否则高优先级横幅并保留通知 | |
| A5 | 提前 5 分钟 | 提前时完整强提醒一次 | |
| A6 | A5 后正式到点 | 正式时间再次完整强提醒，不能被提前事件覆盖 | |
| A7 | 响铃关、震动开 | 只震动，不响铃 | |
| A8 | 响铃开、震动关 | 只响铃，不震动 | |
| A9 | 两项都关 | 仍有亮屏/全屏/通知，不响不震 | |
| A10 | 全屏页点关闭 | 当前响铃/震动停止；通知中心保留静音可预览记录 | |
| A11 | 横幅点关闭/划除 | 当前打断停止；通知中心保留静音记录 | |
| A12 | 点稍后 10 分钟 | 当前打断和通知结束；10 分钟后再次完整强提醒 | |
| A13 | 稍后提醒触发后查看重复下一次 | 不跳过正式下一周期 | |
| A14 | 全屏/通知点标为完成 | 非重复完成；重复只完成本次，下一周期仍存在 | |
| A15 | 系统闹钟音量为 0 | 日志记录 alarmVolume=0；调高后重新测试可响 | |

## 并发与旧操作

| ID | 场景 | 预期 | 结果/证据 |
|---|---|---|---|
| C1 | 两条不同提醒同一分钟 | 后一条正常强提醒；前一条保留通知记录 | |
| C2 | C1 后点击前一条旧“关闭” | 不停止后一条的响铃/震动/全屏 | |
| C3 | 同一重复提醒 ADVANCE 未关时 DUE 到达 | DUE 重新响铃/震动并成为当前事件 | |
| C4 | DUE 后点击旧 ADVANCE 操作 | 不停止或取消当前 DUE 通知 | |
| C5 | 同一个 PendingIntent 重复投递 | occurrence 只推进一次，不跳两个周期 | |

## 重复与提前

| ID | 场景 | 预期 | 结果/证据 |
|---|---|---|---|
| R1 | 每小时/每隔5小时/每天/每周/每两周 | 下一次准确 | |
| R2 | 工作日 | 北京时间周一到周五，周五后为周一 | |
| R3 | 周末/周日 | 周六、周日或下一周日准确 | |
| R4 | 每月 31 日跨 2 月再到 3 月 | 2 月 28/29，3 月恢复 31 | |
| R5 | 每3月/每6月/每年 | 月末和闰年裁剪准确 | |
| R6 | 指定结束日期 | 截止后无新闹钟并变为完成 | |
| R7 | 提前量等于/大于重复周期 | 保存被明确拒绝 | |
| R8 | 自定义提前 1 和 200，各单位 | 计算、显示和触发一致 | |

## 系统恢复

| ID | 场景 | 预期 | 结果/证据 |
|---|---|---|---|
| B1 | 重启手机 | 解锁后未来提醒重新注册并能触发 | |
| B2 | 关机期间错过重复提醒 | 启动后追赶到未来最近一次，不立即连响多次 | |
| B3 | 覆盖安装 v1.3 | 待处理提醒自动重建 | |
| B4 | 精确闹钟权限恢复 | 待处理提醒自动重建 | |
| B5 | 一条异常提醒重建失败 | 日志记录失败，其他提醒继续恢复 | |

## ADB 日志采集

先确认连接：

```bash
/opt/homebrew/share/android-commandlinetools/platform-tools/adb devices -l
```

记录设备信息：

```bash
ADB=/opt/homebrew/share/android-commandlinetools/platform-tools/adb
$ADB shell getprop ro.product.model
$ADB shell getprop ro.build.version.release
$ADB shell getprop ro.build.version.sdk
$ADB shell getprop ro.build.display.id
$ADB shell dumpsys package com.reminder.local | grep -E 'versionName|versionCode'
```

触发前清空并抓完整日志：

```bash
$ADB logcat -c
$ADB logcat -v threadtime > reminder-v1.3-full.log
```

另开终端抓关键链路：

```bash
$ADB logcat -v threadtime \
  AlarmScheduler:I AlarmReceiver:I AlarmAlertService:I \
  NotificationActionReceiver:I ActivityTaskManager:I NotificationService:I '*:S' \
  > reminder-v1.3-filtered.log
```

检查系统调度和通知：

```bash
$ADB shell dumpsys alarm | grep -A 12 -B 4 com.reminder.local
$ADB shell dumpsys notification --noredact | grep -A 30 -B 5 com.reminder.local
$ADB shell dumpsys activity services com.reminder.local
```

## 通过门槛

- 所有 A/U/M/R/B 项有实际结果；核心 A1-A14、C1-C5 不得空白。
- 失败项附日志时间、提醒标题代号、触发 kind 和截图/录屏。
- 只有本表全部通过并保存证据，才能写“红米 K80 Pro / HyperOS 完整产品验收通过”。
