# Android Reminders App

面向红米 K80 Pro / HyperOS / Android 16（API 36）的个人离线强提醒 App。当前源码版本为 **v1.3（versionCode 4）**，包名 `com.reminder.local`。

> 当前状态（2026-07-22）：v1.3 的 63/63 JVM 测试、`lintDebug` 和 `assembleRelease` 已实际通过，APK 已复核包名、版本、SHA-256 和 v2 签名。ADB 当前未识别设备，红米 K80 Pro 的锁屏/后台强提醒仍未实机验收；历史 v1.2 结果不替代本结论。详见 [测试报告](TEST_REPORT_2026-07-18.md)、[构建报告](BUILD_APK_REPORT_2026-07-18.md) 和 [真机验收表](REAL_DEVICE_VERIFICATION_2026-07-10.md)。

## 产品定义

这是一个简体中文、极简白色界面、完全离线的个人提醒工具。它不申请网络权限，不包含账号、广告、统计、云同步或远程服务；提醒、分类和设置只保存在本机 Room/SQLite 中。

核心体验不是“安静地写入通知中心”，而是尽量像闹钟一样主动提醒：

- 精确时间触发；
- 响铃和震动按每条提醒的开关执行；
- 锁屏时请求亮屏并显示全屏提醒页；
- 同时发布公开可见的通知，允许在锁屏预览标题和备注；
- 用户可选择关闭本次打断、稍后 10 分钟或标为完成；
- “关闭”只停止当前响铃/震动/全屏打断，通知中心保留静音记录；
- “稍后提醒”和“标为完成”结束当前打断并移除当前通知。

### 提前提醒语义

提前提醒和正式到点是两个独立强提醒事件。例如提醒时间为 20:59、提前 5 分钟：

1. 20:54 触发提前强提醒；
2. 20:59 再触发正式到点强提醒。

提前提醒不得推进重复周期；正式到点才计算并注册下一次重复。稍后提醒也是独立事件，不修改用户设置的正式时间和重复模板。

### Android / HyperOS 边界

App 会声明并使用系统允许的闹钟、全屏通知、前台服务和锁屏 API，但不能绕过 Android 或 HyperOS 的策略：

- 锁屏时：目标是亮屏、全屏页、响铃、震动和通知预览同时出现；仍受通知、全屏提醒、锁屏显示、后台弹出界面等权限影响。
- 手机已解锁且正在使用时：Android 可能只展示高优先级横幅而不自动打开全屏 Activity，这是平台允许的行为；App 仍会继续响铃、震动并保留通知。
- 小米自启动无法由普通 App 强制开启。App 能接收开机广播重建闹钟，并在设置页引导用户打开自启动、省电无限制、锁屏显示和后台弹出界面。
- 系统闹钟音量为 0、勿扰模式或用户修改通知渠道设置时，App 不能擅自修改系统音量或越过用户设置。

## 功能范围

| 模块 | 已实现内容 |
|---|---|
| 提醒 CRUD | 新增、编辑、删除、完成、撤销完成；标题最多 50 字、备注最多 200 字 |
| 编辑体验 | 顶部右侧固定保存按钮；删除入口同处，无需滚动到底部 |
| 时间选择 | 小时 + 分钟双列上下滑动，分钟 1 分钟一格，中心吸附 |
| 提前提醒 | 无；5/10/15/30 分钟；1/2/3 小时；1/2 天；1/2 周；1 个月；自定义 1-200 分钟/小时/天/周/个月 |
| 重复提醒 | 每小时、每隔 5 小时、每天、工作日、每周、每周日、周末、每两周、每月、每 3 个月、每 6 个月、每年 |
| 结束重复 | 永不或指定结束日期 |
| 重复操作 | 完成/删除可选“仅本次”或“全部”；通知和全屏页默认只处理本次 |
| 强提醒 | `setAlarmClock()`、前台服务、闹钟音频、震动、WakeLock、全屏 Intent、锁屏通知预览 |
| 通知操作 | 关闭、稍后 10 分钟、标为完成 |
| 分类 | 工作/生活/学习/健康内置分类；自定义分类；删除分类不删除提醒 |
| 数据 | Room/SQLite 本地持久化；无网络权限；关闭备份和设备迁移 |
| 系统恢复 | 开机、应用更新、精确闹钟权限变化和用户重新打开 App 后重建待处理闹钟；单条失败不阻塞其他提醒 |
| 设置 | 默认响铃/震动、权限状态、通知渠道、小米权限、自启动和省电设置跳转 |

“优先级（高/中/低）”已经从 UI、领域模型、Room Entity、Mapper 和 v4 数据库结构中删除。v3 升级到 v4 时保留已有提醒数据，仅移除该字段并修复异常的重复 `alarmId`。

暂不包含：账号、联网、多端同步、云备份、桌面 Widget、自定义铃声、语音输入和节假日调休日历。

## 架构

```text
Jetpack Compose UI
        ↓ StateFlow
ViewModel (Hilt/MVVM)
        ↓
UseCase（业务规则与补偿）
        ↓
Repository interface
        ↓
Room DAO / SQLite
```

提醒投递链路：

```text
AlarmSchedulerImpl
  ├─ ADVANCE PendingIntent
  ├─ DUE PendingIntent
  └─ SNOOZE PendingIntent
          ↓ setAlarmClock()
AlarmManager → AlarmReceiver → AlarmAlertService
                               ├─ 前台保活通知
                               ├─ 循环闹钟铃声 / 震动
                               ├─ WakeLock 请求亮屏
                               ├─ 公开锁屏通知
                               └─ FullScreenIntent → AlarmActivity
```

### 关键不变量

- `triggerTime` 是不可漂移的重复模板；`nextTriggerTime` 是下一次实际触发时间。
- ADVANCE 只提醒，不推进重复；DUE 才推进；SNOOZE 不推进。
- 每个系统操作都携带 `reminderId + alarmId + kind + occurrenceTime`，旧操作不能停止新的活跃提醒。
- 数据库更新和 AlarmManager 调度无法形成真正跨系统事务，因此 UseCase 必须实施失败补偿：注册失败不留下假成功记录，数据库失败尽力恢复旧闹钟。
- 重复提醒的“仅本次”不会停止整个重复链；Receiver 已推进过的 occurrence 不能再次推进。
- 同时到达多条提醒采用 latest-wins：当前响铃切换到后一条，前一条保留为通知记录。
- 31 日月度重复以原始日期为模板，2 月裁剪到 28/29 日，3 月恢复 31 日。

## 权限与隐私

Manifest 声明：

- `USE_EXACT_ALARM`；API 31-32 兜底 `SCHEDULE_EXACT_ALARM`；
- `POST_NOTIFICATIONS`、`USE_FULL_SCREEN_INTENT`；
- `RECEIVE_BOOT_COMPLETED`、`VIBRATE`、`WAKE_LOCK`；
- `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK`。

未声明 `INTERNET`。`AlarmActivity`、通知入口 Activity、闹钟 Receiver、通知 Action Receiver 和前台 Service 均不向第三方 App 导出；Launcher 只负责打开列表，不接受外部 reminderId。`PendingIntent` 全部使用 `FLAG_IMMUTABLE`。

锁屏显示标题和备注是本产品的明确需求，也意味着旁人可能在锁屏看到内容。用户应避免在标题/备注中填写密码、验证码、身份证号等敏感信息，并在系统通知设置中按个人需要选择是否隐藏锁屏敏感内容。

Release 当前使用 debug signingConfig，只适合个人直装和测试，不适合商店发布或建立长期生产签名信任链。

## 技术栈

| 组件 | 版本 |
|---|---|
| Kotlin | 2.1.21 |
| Android Gradle Plugin | 8.13.2 |
| Gradle Wrapper | 8.13 |
| Compose BOM | 2026.06.00 |
| Room | 2.8.3（数据库 v4，导出 Schema） |
| Hilt | 2.58 |
| Navigation Compose | 2.9.8 |
| minSdk / targetSdk / compileSdk | 31 / 36 / 36 |

当前规模：66 个主源码 Kotlin 文件、20 个测试类、63 个 `@Test` 用例。2026-07-22 从 Gradle XML 实际汇总为 `63 passed / 0 failed / 0 error / 0 skipped`。

## 构建

要求 JDK 17、Android SDK 36：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew clean testDebugUnitTest lintDebug assembleRelease --console=plain
```

验签与 APK 信息检查：

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
$ANDROID_HOME/build-tools/36.0.0/aapt dump badging \
  app/build/outputs/apk/release/app-release.apk
shasum -a 256 app/build/outputs/apk/release/app-release.apk
```

最终交付必须复制为带版本号的名称：

```text
ReminderApp-v1.3.apk
```

禁止把通用名 `app-release.apk` 当作对外交付文件名。

## 验证状态

| 证据 | 状态 |
|---|---|
| v1.2 修改前基线：41 个 JVM 用例、Lint、Release 构建 | 已实际通过，仅证明基线 |
| v1.3 TDD 红灯：新增测试暴露 8 个历史缺陷 | 已实际复现 |
| v1.3 中间回归：剩余 2 个 JVM `android.util.Log` mock 问题 | 已实际复现并修改 |
| v1.3 当前 63 个用例 | 已实际通过：63/63，失败 0、错误 0、跳过 0（2026-07-22） |
| v1.3 `lintDebug` / `assembleRelease` | 已实际通过（2026-07-22）；Lint 报告含 55 warnings、3 hints，均未阻断构建 |
| v1.3 APK、签名、SHA-256 | 已生成并复核：48,418,233 bytes，v2 签名，SHA-256 `960a6736368d14c415b0732dc2566e522910b67316c97015882e4123deef39c8` |
| 红米 K80 Pro 真机 | 未执行：本次 `adb devices -l` 无设备；不得将本行替换为“通过” |

## 文档

- [专业审计报告](AUDIT_REPORT_2026-07-18.md)
- [自动化测试报告](TEST_REPORT_2026-07-18.md)
- [构建与 APK 验证报告](BUILD_APK_REPORT_2026-07-18.md)
- [安全审计报告](SECURITY_AUDIT_2026-07-22.md)
- [红米 K80 Pro 真机验收表](REAL_DEVICE_VERIFICATION_2026-07-10.md)
- [项目故障记忆与防复发规则](PROJECT_MEMORY_2026-07-18.md)
- [v1.3 变更记录](CHANGELOG_2026-07-18.md)

## 防复发摘要

1. 通知出现不等于强提醒成功，必须分别验证 Alarm、Receiver、Service、声音、震动、亮屏、Activity 和锁屏通知。
2. 提前、到点、稍后必须是三个独立 kind；只有到点可推进重复周期。
3. 任何通知/全屏操作必须带 occurrence 实例标识；禁止无条件 `stopService()`。
4. 数据库和系统闹钟的任一侧失败，都必须记录日志并执行补偿，不能留下“列表正常、实际没闹钟”的僵尸提醒。
5. Room 字段变化必须同步 Model、Entity、Converter、Mapper、Migration、Schema 和测试。
6. Android 通知渠道设置会持久化，改变重要性/声音/锁屏行为时要升级渠道 ID。
7. 构建通过不代表真机强提醒通过；没有目标机日志、截图或录屏不得写“完整验收通过”。
8. 每次 APK 交付都使用 `ReminderApp-v<version>.apk`，并记录大小、签名和 SHA-256。
