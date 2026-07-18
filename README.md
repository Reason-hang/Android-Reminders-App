# Android Reminders App

给红米 K80 Pro / HyperOS / Android 16（API 36）个人自用的离线提醒事项 App。

产品目标很明确：做一个简体中文、极简白色界面、离线可用、到点可靠提醒的个人工具。提醒到点时不能只安静地进通知中心，而要尽量像闹钟一样主动打断用户：亮屏、响铃、震动、全屏提醒页，并在锁屏上展示可预览的提醒内容。

本项目不申请网络权限，不做登录、不做云同步、不上传任何数据。所有提醒、分类和设置都保存在本机 Room/SQLite 数据库中，卸载 App 后数据随 App 删除。

## 产品定义

### 核心体验

- 界面语言：简体中文。
- 视觉风格：极简、白底、低干扰，不使用花哨装饰。
- 目标设备：红米 K80 Pro，Android 16 / API 36。
- 使用场景：个人离线提醒事项，不做多人协作和云端同步。
- 提醒心智：更接近“闹钟式强提醒”，而不是普通通知。

### 强提醒定义

到达提醒时间时，App 应同时尽力完成：

- 点亮屏幕。
- 响铃。
- 震动。
- 弹出全屏提醒页，需要用户手动关闭、稍后提醒或标为完成。
- 同时发布锁屏可见的通知，标题和备注可以预览。

提前提醒也属于强提醒。比如一条提醒设置为 20:59，提前 5 分钟提醒，则应触发两次强提醒：

- 20:54：提前提醒，亮屏、响铃、震动、全屏页、锁屏通知预览。
- 20:59：正式到点提醒，再次亮屏、响铃、震动、全屏页、锁屏通知预览。

重复提醒只在正式到点提醒时推进下一次周期，提前提醒不能推进重复周期，避免提前提醒把正式提醒或下次提醒链路打乱。

### 小米 / HyperOS 约束

Android 和 HyperOS 对后台启动、锁屏显示、通知预览、电池策略都有系统级限制。代码侧已经做了合理能力声明和实现，但用户仍需要在系统里确认：

- 通知权限已允许。
- 强提醒 / 全屏通知权限已允许。
- 锁屏显示已允许。
- 后台弹出界面已允许。
- 省电策略设为“无限制”。
- 自启动已允许。
- 系统锁屏通知样式允许显示通知内容。

代码不能绕过系统权限和厂商策略，只能声明权限、使用合规 API，并在设置页引导用户跳转到对应设置。

## 功能清单

| 模块 | 功能 | 状态 | 说明 |
|---|---|---|---|
| 提醒 CRUD | 新增提醒 | 已实现 | 标题不超过 50 字，备注不超过 200 字，可选分类、时间、重复、提前提醒、响铃、震动 |
| 提醒 CRUD | 编辑提醒 | 已实现 | 修改时间会取消旧闹钟并重新注册；已完成/过期提醒改到未来会重新激活 |
| 提醒 CRUD | 删除提醒 | 已实现 | 支持删除一次性提醒；重复提醒可选择仅本次或全部 |
| 提醒 CRUD | 完成提醒 | 已实现 | 支持列表勾选/滑动、通知操作、全屏页按钮 |
| 提醒 CRUD | 优先级 | 已移除 | “高 / 中 / 低”优先级字段已按需求移除，列表排序回归时间和状态 |
| 列表页 | 未完成/已完成分组 | 已实现 | 未完成和已完成分组展示 |
| 列表页 | 分类筛选 | 已实现 | 全部、各分类、未分类 |
| 列表页 | 空状态 | 已实现 | 无提醒时展示引导图标和文案 |
| 编辑页 | 一屏保存 | 已实现 | 保存入口固定在顶部右侧，无需滑动到表单底部 |
| 时间选择 | 双列上下滑动时分选择器 | 已实现 | 小时和分钟同时展示，分钟为 1 分钟一格，停止滑动后自动吸附到中心项 |
| 提前提醒 | 固定选项 | 已实现 | 无、5/10/15/30 分钟前、1/2/3 小时前、1/2 天前、1/2 周前、1 个月前 |
| 提前提醒 | 自定义提前提醒 | 已实现 | 数字 1-200，单位支持分钟、小时、天、周、个月 |
| 提前提醒 | 强提醒链路 | 已实现 | 提前提醒也会启动响铃、震动、亮屏和全屏提醒页 |
| 到点提醒 | 精确闹钟 | 已实现 | 使用 `AlarmManager.setAlarmClock()` |
| 到点提醒 | 全屏提醒页 | 已实现 | `AlarmActivity` 支持锁屏展示、点亮屏幕、手动关闭 |
| 到点提醒 | 前台服务兜底 | 已实现 | `AlarmAlertService` 负责响铃、震动、前台通知，避免 Activity 被系统拦截后完全无提醒 |
| 到点提醒 | 系统拒绝服务时降级 | 已实现 | 按单条响铃/震动设置选择备用渠道，并继续尝试全屏页、锁屏预览 |
| 到点提醒 | 锁屏通知预览 | 已实现 | 用户强提醒通知与前台服务通知分离，使用公开锁屏可见性和 BigText 预览 |
| 到点提醒 | 通知操作 | 已实现 | 关闭、稍后提醒 10 分钟、标为完成 |
| 重复提醒 | 基础重复 | 已实现 | 每小时、每天、每周、每月、每年 |
| 重复提醒 | 扩展重复 | 已实现 | 每隔 5 小时、周日、周末、工作日、每两周、每 3 个月、每 6 个月 |
| 重复提醒 | 结束重复 | 已实现 | 永不 / 自定义结束日期 |
| 重复提醒 | 月份边界 | 已实现 | 按最初设置的日期为目标，31 号遇到短月份裁剪，但后续长月份恢复到 31 号 |
| 分类管理 | 内置分类 | 已实现 | 工作、生活、学习、健康 |
| 分类管理 | 自定义分类 | 已实现 | 支持新增、编辑、删除；删除分类不会删除提醒 |
| 数据持久化 | 本地数据库 | 已实现 | Room / SQLite |
| 系统集成 | 通知权限 | 已实现 | Android 13+ 请求 `POST_NOTIFICATIONS` |
| 系统集成 | 精确闹钟权限 | 已实现 | 声明 `USE_EXACT_ALARM` 和 `SCHEDULE_EXACT_ALARM` |
| 系统集成 | 全屏通知权限 | 已实现 | 声明 `USE_FULL_SCREEN_INTENT` |
| 系统集成 | 震动/唤醒/前台服务 | 已实现 | 声明 `VIBRATE`、`WAKE_LOCK`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK` |
| 系统集成 | 开机恢复 | 已实现 | `BootReceiver` 在重启后重新注册未来提醒 |
| 设置页 | 默认响铃/震动 | 已实现 | 只影响新建提醒默认值 |
| 设置页 | 权限状态展示 | 已实现 | 展示通知、精确闹钟、全屏通知相关状态 |
| 设置页 | HyperOS 引导 | 已实现 | 引导省电无限制、自启动、锁屏显示、后台弹出界面 |

暂不包含：多端同步、云备份、桌面 Widget、自定义铃声选择、语音输入、账号体系。

## 架构设计

```text
UI (Jetpack Compose)
   ↓ StateFlow
ViewModel (MVVM, Hilt)
   ↓
UseCase
   ↓
Repository interface
   ↓
Repository implementation
   ↓
Room DAO
   ↓
SQLite
```

提醒触发链路：

```text
AlarmSchedulerImpl
   ↓ setAlarmClock()
AlarmManager
   ↓
AlarmReceiver
   ↓
AlarmAlertService
   ├─ 前台通知
   ├─ 响铃
   ├─ 震动
   ├─ WakeLock 点亮屏幕
   └─ FullScreenIntent / PendingIntent 启动 AlarmActivity
           ↓
       AlarmActivity 全屏提醒页
```

### 分层原则

- UI 只负责展示和用户交互。
- ViewModel 只管理页面状态，不直接写数据库。
- UseCase 承载新增、编辑、完成、删除、重排提醒等业务规则。
- Repository 隔离 Room，避免 UI 和业务层直接依赖数据库实现。
- Alarm / Notification / Receiver 相关逻辑单独隔离，避免散落在页面代码里。

### 关键设计决策

- 精确提醒使用 `AlarmManager.setAlarmClock()`，比普通延迟任务更符合“闹钟/提醒”场景。
- 强提醒使用前台服务播放铃声和震动，而不是依赖通知渠道声音，原因是 Android 8+ 通知渠道创建后声音/震动设置会被系统持久化，单条通知难以覆盖。
- 全屏提醒页、用户强提醒通知和前台服务通知三者分离：Activity 负责中间弹窗；公开可见的用户通知负责锁屏预览和系统记录；低重要性的服务通知只负责维持响铃进程。
- 用户点击“关闭”只结束本次响铃、震动和全屏打断；通知中心保留一条静音记录。点击“稍后提醒”或“标为完成”才移除当前通知。
- Android 14-16 的后台 Activity 启动使用与系统版本匹配的显式授权模式；Android 16 使用闹钟场景所需的 `ALLOW_ALWAYS` 模式。即使如此，最终是否展示全屏页仍受系统全屏通知权限和 HyperOS“后台弹出界面”开关控制。
- 强提醒通知渠道使用版本化 ID：`reminder_fullscreen_alert_v3`。原因是 Android 通知渠道创建后设置会持久化，代码更新不能可靠覆盖旧渠道的锁屏可见性、重要性等设置。
- 前台服务启动、前台提升或手动声音/震动播放失败时，备用通知按“响铃+震动 / 仅响铃 / 仅震动 / 静音”四种组合选择独立渠道，不能因为主链路部分失败就退化成安静记录，也不能违背单条提醒开关。
- 提前提醒和正式到点提醒共享强提醒链路，但重复周期只允许正式到点提醒推进。
- 重复提醒使用 `triggerTime` 作为模板时间、`nextTriggerTime` 作为下一次触发时间，避免每月重复在 28/29/30/31 号之间永久漂移。

## 技术栈

| 组件 | 版本 |
|---|---|
| Kotlin | 2.1.21 |
| Android Gradle Plugin | 8.13.2 |
| Gradle Wrapper | 8.13 |
| Jetpack Compose BOM | 2026.06.00 |
| Material 3 | Compose BOM 管理 |
| Room | 2.8.3 |
| Hilt | 2.58 |
| Navigation Compose | 2.9.8 |
| DataStore Preferences | 1.1.1 |
| minSdk / targetSdk / compileSdk | 31 / 36 / 36 |

当前源码规模：

- 主源码 Kotlin 文件：58 个。
- 单元测试 Kotlin 文件：13 个。

## 构建与安装

### 环境要求

- JDK 17。
- Android SDK 36。
- Android Build Tools 35/36。
- 可选：ADB，用于真机安装和日志排查。

### 构建 Release APK

```bash
./gradlew testDebugUnitTest assembleRelease
```

构建产物：

```text
app/build/outputs/apk/release/app-release.apk
```

本项目当前 release 包使用 debug signingConfig 签名，适合个人直装测试。如果后续要上架应用商店，应替换为正式 keystore。

### ADB 安装

```bash
adb install -r --no-streaming app/build/outputs/apk/release/app-release.apk
```

如果本机使用 Homebrew Android command line tools，ADB 可能在：

```bash
/opt/homebrew/share/android-commandlinetools/platform-tools/adb
```

## 测试与验证

已覆盖的单元测试：

- `RepeatCalculatorTest`：重复提醒日期计算，覆盖每月边界和无漂移。
- `AdvanceReminderCalculatorTest`：提前提醒触发时间计算。
- `AlarmAlertLaunchPolicyTest`：后台启动全屏提醒所需策略。
- `AlarmAlertInteractionPolicyTest`：关闭、稍后提醒和完成操作的通知保留策略。
- `AlarmAlertConcurrencyPolicyTest`：多提醒重叠及旧关闭操作不能误停新提醒。
- `AlarmAlertContentFormatterTest`：锁屏预览标题和提前提醒标题。
- `AlarmTriggerPolicyTest`：提前提醒/到点提醒强提醒策略，确保提前提醒不推进重复周期。
- `AlarmSchedulerRequestCodeTest`：提前/到点的调度和系统展示 PendingIntent 不互相覆盖。
- `AlarmNotificationPolicyTest`：服务通知、用户强提醒和四种备用渠道相互隔离。
- `AlarmDeliveryPolicyTest`：前台提升或手动播放部分失败时，仅对缺失的声音/震动启用兜底。
- `AddReminderUseCaseTest`：新增提醒业务规则。
- `WheelPickerSelectionTest`：双列滚轮中心项选择和部分可见项边界。

最近一次已执行验证（2026-07-18）：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew testDebugUnitTest assembleRelease
```

结果：

- `BUILD SUCCESSFUL`，全量 JVM 单元测试通过。
- 13 个测试类、41 个测试用例，失败/错误/跳过均为 0。
- 完整 `lintDebug` 与 Release `lintVitalRelease` 均通过。
- APK 签名验证通过：APK Signature Scheme v2。
- 包名：`com.reminder.local`。
- 版本：`1.2`（`versionCode 3`）。
- 应用名：`提醒事项`。
- APK 大小：48,384,713 字节。
- 当前没有 ADB 真机连接，因此锁屏/亮屏使用中强提醒仍需按真机验收表补测。

完整自动化与 APK 验证证据见 `TEST_REPORT_2026-07-18.md`。

最近一次交付 APK：

```text
/Users/lizhihang/Documents/Codex/2026-06-30/10-0-1-app-apk-app/outputs/ReminderApp-v1.2.apk
```

SHA-256：

```text
d0f791d33a47ab638ed01c77b724bb6e24195f610802f25cbc20303b3be5587a
```

### 历史记录：2026-07-10 外部 AI 沙箱无法构建

本轮修复由 Claude 在一个隔离的云端沙箱容器里完成，如实记录构建尝试结果：

```bash
$ ./gradlew testDebugUnitTest assembleRelease
Fetching distribution.
Downloading https://services.gradle.org/distributions/gradle-8.13-bin.zip
Attempt 1/1 failed. Reason: Server returned HTTP response code: 403 for URL: ...
Exception in thread "main" java.io.IOException: Server returned HTTP response code: 403 ...
```

排查记录：

- 该沙箱没有安装 Android SDK（`ANDROID_HOME` 未设置，找不到 SDK 目录）。
- 该沙箱的出站网络是白名单制，`services.gradle.org`、`dl.google.com`、`repo.maven.apache.org`
  均返回 `403`（已逐一实测），也就是连 Gradle 发行包本身都下载不下来，
  更不用说下载 AndroidX / Compose / Room / Hilt 等依赖。
- 因此 `BUILD SUCCESSFUL` **在本轮没有拿到**，不是因为代码改动导致编译失败，
  而是这个沙箱环境本身不具备编译 Android 工程的条件。

**该结论只描述 2026-07-10 的外部沙箱，不代表当前主干。** 当前主干已在 Mac 本机 Android SDK 环境构建过；最新改动以 2026-07-18 的实际测试和构建记录为准。
本轮交付物是修复后的源码 + 文档，`./gradlew testDebugUnitTest assembleRelease` 需要用户在自己的
开发机（有 Android SDK、能访问 Google/Gradle 官方仓库）上执行，或者直接用 Android Studio 打开
`part-01-source` 里的工程后点 Run/Build。命令和上面"构建 Release APK"一节完全一致，不需要改动。

## 故障复盘与修复记录

这一节是项目记忆。后续迭代时必须先读这里，避免重复犯同类错误。

### 1. 保存失败

现象：

- 新建提醒填写完成后点击保存，页面提示“保存失败，请重试”。

排查：

- 用户连接真机并授权 USB 调试后，结合日志和代码检查排查。
- 当前阶段保存已恢复，用户已确认“新建保存成功了”。

修复原则：

- 新增/编辑必须保持“数据库写入 + 闹钟注册”一致性。
- 闹钟注册失败时，要明确错误来源，不能只给笼统失败。
- 以后涉及保存链路时，必须同时检查数据库迁移、字段映射、权限检查、闹钟注册和 UI 错误提示。

### 2. 提前提醒只有通知中心记录

现象：

- 设置了提前 5 分钟提醒。
- 提前提醒时间到了以后，只有通知中心有一条记录，没有响铃、震动、亮屏、全屏弹窗。
- 打开 App 后才可能看到中间弹窗。

根因：

- `AlarmReceiver` 中对 `KIND_ADVANCE` 提前提醒写成了普通通知分支，并且直接 `return`。
- 这导致提前提醒没有启动 `AlarmAlertService`，自然不会响铃、震动、亮屏或启动 `AlarmActivity`。

修复：

- 新增 `AlarmTriggerPolicy`。
- 提前提醒和到点提醒都调用 `AlarmAlertService.startIntent()` 进入强提醒链路。
- 只有正式到点提醒允许推进重复周期。
- 新增 `AlarmTriggerPolicyTest` 防止回归。

防复发规则：

- 提前提醒不是轻提醒，是强提醒。
- 所有提醒触发类型新增后，都必须明确是否：
  - 启动强提醒；
  - 允许响铃；
  - 允许震动；
  - 允许全屏；
  - 是否推进重复周期。

### 3. 锁屏亮屏但没有通知预览

现象：

- 锁屏后提醒到点，手机亮屏，有响铃和震动。
- 锁屏界面没有显示提醒通知，也没有内容预览。

排查：

- ADB 检查 App 权限：
  - `POST_NOTIFICATIONS` 已允许。
  - `USE_FULL_SCREEN_INTENT` 已允许。
  - `WAKE_LOCK` 已允许。
  - `VIBRATE` 已允许。
  - 前台服务已允许。
- AppOps 显示全屏通知相关能力允许。
- 手机上旧通知渠道 `reminder_fullscreen_alert` 已存在，通知渠道设置由系统持久化，代码更新不能保证覆盖旧渠道的锁屏可见性。

代码层确认的缺口：

- 旧通知渠道可能已经被系统或用户设置固化，代码修改同一个 channel ID 不一定改变真机行为。
- 用户可见的锁屏提醒此前复用前台服务 `ongoing` 通知，HyperOS 可能只把它当作服务运行记录，
  而不是正常展示标题和备注的用户提醒。

修复：

- 强提醒渠道改为新 ID：`reminder_fullscreen_alert_v3`。
- 新渠道显式设置 `lockscreenVisibility = VISIBILITY_PUBLIC`。
- 用户强提醒通知与低重要性的前台服务通知分离；前者公开显示标题/备注，后者仅负责维持响铃进程。
- 强提醒通知显式设置：
  - `CATEGORY_ALARM`
  - `PRIORITY_HIGH`
  - `VISIBILITY_PUBLIC`
  - `BigTextStyle`
  - `setFullScreenIntent(..., true)`
  - `setShowWhen(true)`

防复发规则：

- Android 通知渠道一旦创建，其重要性、声音、震动、锁屏可见性等会被系统持久化。
- 修改通知渠道行为时，不能只改原 channel 配置；必要时必须升级 channel ID。

### 4. 全屏弹窗和后台启动

现象：

- 用户希望提醒到点时不要只出现顶部横幅，而是锁屏也能显示全屏中间提醒页。

排查：

- Android 14+ 对后台 Activity 启动和全屏通知限制更严格。
- 全屏页能否启动取决于系统是否执行 `FullScreenIntent` / PendingIntent，以及小米后台弹出界面权限。

修复：

- 新增 `AlarmActivity` 作为强提醒全屏页。
- `AlarmActivity` 设置 `setShowWhenLocked(true)` 和 `setTurnScreenOn(true)`。
- Manifest 中为 `AlarmActivity` 配置 `showWhenLocked` 和 `turnScreenOn`。
- `AlarmAlertService` 使用前台服务兜底，负责响铃和震动。
- PendingIntent 创建和发送时加入后台启动 Activity 相关 ActivityOptions。

防复发规则：

- 不能把“服务响铃成功”误判为“全屏页启动成功”。
- 响铃/震动链路和全屏 Activity 链路要分别验证。
- 真机排查必须抓 logcat，看是否存在 `background activity launch denied`、`full screen intent denied`、`ActivityTaskManager` 拦截等日志。

### 5. 保存按钮太深

现象：

- 每次新增或编辑提醒后，需要滑到页面底部才能点击保存。

修复：

- `EditReminderScreen` 顶部右侧增加“保存”按钮。
- 底部保存按钮保留，兼容原使用习惯。

防复发规则：

- 高频主操作不能只放在长表单底部。
- 长表单至少应提供顶部操作或固定底部操作。

### 6. 优先级字段不符合当前产品定位

现象：

- 用户明确要求去掉“优先级”高/中/低。

修复：

- 移除优先级 UI 和相关交互。
- 列表排序不再依赖优先级。

防复发规则：

- 不要自行加入用户未要求、且会增加操作负担的字段。
- 个人极简工具优先减少决策成本。

### 7. 构建工具链与依赖兼容

问题：

- 原始项目需要 Android 16 / API 36。
- AGP 版本必须能支持 compileSdk 36。
- Kotlin、Room、Hilt 版本之间存在 metadata 兼容关系。

修复：

- 使用 AGP 8.13.2 + Gradle 8.13。
- Kotlin 使用 2.1.21。
- Room 使用 2.8.3。
- Hilt 使用 2.58。
- 补齐 Gradle Wrapper。

防复发规则：

- 升级 compileSdk / Kotlin / AGP / Hilt / Room 时必须整体看兼容矩阵。
- 不要只升级一个库版本后假设能编译。

### 8. Compose 和资源问题

已修复问题：

- `ic_empty_state.xml` 引用不存在的属性。
- `CategoryFilterRow.kt` 缺少 `Modifier.padding` import。
- `EditReminderScreen.kt` 滚动相关 import 错误。
- Material 3 下拉菜单 API 使用方式不匹配。

防复发规则：

- UI API 修改后必须跑编译，不凭记忆判断 Compose API。
- 资源文件引用主题属性时必须确认当前主题真的定义了该属性。

### 9. 2026-07-10：问题 2 / 3 / 5 在红米 K80 Pro 真机上复现

用户现象（原话摘要）：

- 提前 5 分钟提醒到点时，只有通知中心一条记录，没有震动、响铃、弹窗，只有手动打开 App 才会看到中间弹窗。
- 锁屏后到点提醒，有亮屏、震动、响铃，但锁屏界面没有任何通知预览内容。
- 保存提醒仍然需要滑动到表单底部才能点到保存按钮。

也就是说，第 2、3、5 条在本文档里都已经写了“已修复”，但用户这次是在真机上重新遇到了几乎一样的症状。
这次复查没有物理接触到用户的真机、也没有拿到 logcat（环境限制见下方“本轮的信息来源与局限”），
下面是**基于代码走查**做出的根因排查，其中一部分已直接修复，另一部分是系统/厂商权限问题，
需要用户在真机上确认后才能最终验证。

#### 2.1 之前的"已修复"哪里是真的，哪里没做完

- 路由层面的根因（`KIND_ADVANCE` 走普通通知分支、不启动 `AlarmAlertService`）确实修复了：
  现在 `AlarmTriggerPolicy.shouldStartStrongAlert()` 对 `KIND_DUE` 和 `KIND_ADVANCE` 都返回 `true`，
  两者都会启动 `AlarmAlertService` 强提醒链路，代码本身没有问题。
- 但是，`NotificationHelper.kt` 里当时那个"普通通知分支"的代码
  （`showAdvanceReminderNotification()`、`CHANNEL_ADVANCE` 渠道，`IMPORTANCE_DEFAULT`、无声音无震动）
  **并没有被删除**，只是不再被调用——变成了死代码。
  风险在于：`CHANNEL_ADVANCE` 在系统设置里显示名就是"提前提醒"，
  排查问题时非常容易被误认成"控制提前提醒的渠道"，浪费排查时间。
  本轮已删除这部分死代码（连同其余 4 个从未使用过的渠道），只保留真正在用的
  `CHANNEL_FULLSCREEN_ALERT`。
- 问题 3 的根因（旧 channel ID 被系统固化）在概念上是对的，修复方式（升级 channel ID + 显式
  `VISIBILITY_PUBLIC`）也是 Android 上处理这类问题的正确做法。但 `VISIBILITY_PUBLIC`
  只是"App 允许锁屏显示"，最终能不能显示还要看：（a）系统"通知设置"里"锁屏显示通知"总开关，
  （b）MIUI/HyperOS 額外的"锁屏显示"权限。这两个系统级开关在当时的修复记录里都没有提到，
  设置页也没有引导用户去检查——本轮已经补上（见下方“本轮代码修复”第 3 条和第 4 条）。
- 问题 5 当时"顶部按钮 + 底部按钮都保留，兼容原使用习惯"——这正是用户现在仍然要滑动到底部的
  直接原因：两个按钮都能保存，用户大概率还是在用回原来的底部按钮，顶部按钮的存在并没有真正
  改变操作路径。本轮已把底部按钮删除，保存只保留顶部栏一个入口。

#### 2.2 本轮代码修复（已落地到本次交付的源码里）

1. **`AlarmAlertService.onStartCommand()` 四个关键步骤（发通知 / 亮屏 / 响铃震动 / 拉起全屏页）
   改成各自独立 `try/catch` + `Log`。**
   之前这四步是裸调用，前一步抛异常会导致后面所有步骤连带跳过，且没有任何日志，
   这正好能解释"通知栏有记录但没有任何其他反应"这种"部分生效"的症状——
   `startForeground()` 先执行、已经把通知发出去了，但后面 `wakeScreen()` /
   `startAlert()` / `launchAlarmActivity()` 只要有一个抛异常，用户就会看到和本次反馈完全一致的现象。
   现在任何一步失败都不会拖累其他步骤，并且 logcat 里会准确打印是哪一步、抛出的是什么异常。
2. **`AlarmReceiver` 里 `startForegroundService()` 增加 `try/catch`，失败时降级发送一条兜底通知。**
   Android 12+ 在少数系统状态下会拒绝后台启动前台服务（`ForegroundServiceStartNotAllowedException`），
   之前这里完全没有保护，一旦被拒绝，协程内异常被吞掉，用户什么反馈都收不到。
   现在会捕获异常、打日志，并且退化成一条锁屏可见、带全屏 Intent 的普通通知，
   保证"至少能看到内容"而不是"什么都没有"。
3. **去掉强提醒通知上的 `setSilent(true)`。**
   这条通知本来就配置了 `PRIORITY_HIGH` + `CATEGORY_ALARM` + 渠道 `IMPORTANCE_HIGH`，
   但同时又标记成"静音通知"。不同厂商 ROM 对"静音通知"的横幅/全屏优先级处理不完全一致，
   这是一个不必要的风险点。渠道本身已经设置了无声音/无震动（由 `AlarmAlertService`
   自己播放循环铃声、避免和渠道声音打架），去掉 `setSilent(true)` 不会带来重复响铃。
4. **设置页补充"锁屏通知"和"小米权限管理（锁屏显示/悬浮窗）"两个入口和说明文案。**
   之前设置页只引导用户开"精确闹钟""全屏提醒""省电策略无限制""自启动"，
   唯独漏了"锁屏显示通知"（标准 Android `ACTION_APP_NOTIFICATION_SETTINGS`）和 MIUI 专属的
   "锁屏显示""悬浮窗/后台弹出界面"权限——这两项和电池策略、自启动是完全独立的开关，
   任何一项缺失都可能复现用户本次反馈的症状。
5. **`EditReminderScreen`：删除表单底部的重复保存按钮，顶部栏保存按钮改成带图标的实心按钮，
   保存失败提示从页面底部文字改成 `Snackbar`。**
   彻底解决"必须滑动到底部才能保存"，并且保存失败时无论滚动到哪里都能看到提示。

#### 2.3 本轮的信息来源与局限（必须如实说明）

这一轮排查完全基于**代码走查 + Android/MIUI 已知行为**，不是基于用户手机的实时 logcat——
运行 Claude 的沙箱环境和用户电脑、手机之间没有连接，无法执行 `adb logcat`、无法安装 APK、
无法在真机上复现问题。上面第 2.1 / 2.2 节的根因判断，**在获得真机 logcat 或复测结果之前，
只能算"高置信度假设 + 已完成的防御性修复"，不能算"已验证的根因"**。

如果本轮修复后问题依然复现，请按下方"给下一轮排查的诊断信息"里的步骤抓取 logcat 并同步，
优先看这几类日志：`AlarmReceiver`、`AlarmAlertService`（本轮新增的日志 tag）、
`ActivityTaskManager`、`NotificationManager`、以及是否出现
`ForegroundServiceStartNotAllowedException` / `background activity launch denied` /
`full screen intent denied` 字样。

### 10. 通用教训：所有静默失败类问题的共性修复原则

这一条是从本轮排查中抽象出来的通用规则，不针对某一个具体 bug，后续新增任何
"系统触发、非用户交互"的代码路径（`BroadcastReceiver`、`Service`、`WorkManager` 任务等）时都要遵守：

1. **系统触发的代码路径必须有日志，不能假设"没报错就是成功"。**
   `BroadcastReceiver.onReceive()`、`Service.onStartCommand()` 这类入口无法用断点交互式调试，
   出问题时唯一的排查手段就是日志。本轮之前整个项目里没有一行 `Log`，
   这是这次问题这么难排查的关键原因之一。
2. **一个函数里如果有多个"锦上添花"但彼此独立的副作用（响铃、震动、亮屏、弹窗各算一个），
   要各自 try/catch，不能因为一个失败拖累其余全部不执行。**
3. **重构/修复一个 bug 时，如果新写法替代了旧写法，必须删除旧代码，而不是"不再调用但留着"。**
   死代码不仅是代码质量问题，还会在排查阶段误导人——尤其是死代码里如果还保留着和真实功能
   同名/近似的命名（比如本例的 `CHANNEL_ADVANCE`），非常容易被当成"正在生效的东西"来排查，
   浪费大量时间。
4. **"用户反馈某功能不好用" + "加一个新方案" 不等于"问题被解决"，如果没有同时去掉旧方案，
   用户很可能还是在用回旧路径。** 本次“保存按钮太深”就是典型案例：加了顶部按钮但保留底部按钮，
   等于什么也没变。
5. **凡是涉及"系统权限允许 App 做某事"（精确闹钟、全屏通知、通知锁屏可见、后台弹窗、自启动）
   的功能，App 代码能做的只是"引导用户去设置页"，不能保证用户已经真的开启。**
   排查此类问题时，先假设是权限/系统限制，再假设是代码 bug，两者都要在复盘记录里分别说明，
   不要笼统写"已修复"却没有注明"这是代码层面的修复，权限仍需用户自行确认"。
6. **在无法接触真机、无法拿到实时日志的情况下做出的根因判断，必须明确标注"未经真机验证"，
   不能和"已验证修复"混为一谈。**

### 11. 2026-07-10 第二轮：对照完整 README / 验收标准做的全项目复查

用户要求"对照整份 README 文档，全面检查是否有 bug、故障或风险"，不局限于前面反馈的 3 个问题。
这一轮把 58 个 Kotlin 源文件基本都过了一遍（数据库/迁移、Repository、DAO、四个核心 UseCase、
DI 模块、AndroidManifest、全部 UI ViewModel/Screen、RepeatCalculator 等），逐项对照本文档开头
"核心功能验收""强提醒验收""提前提醒验收""重复提醒验收""权限验收""安全与隐私验收""代码质量验收"
逐条检查。以下是发现的问题，按严重程度排列。

#### 11.1 高危：全屏提醒页/通知栏"标为完成"会误杀整条重复提醒（已修复）

**现象**：对着一条"每天/每周……"这种重复提醒，在到点后弹出的全屏页上点"标为完成"，
或者在通知栏点"标为完成"按钮，会把这条重复提醒**永久停止**，之后不会再提醒——
和用户的直觉（"完成了今天这一次，明天还提醒我"）不一致。

**代码证据**：`CompleteReminderUseCase.markDone()` 的 `scope` 参数默认值是
`RepeatActionScope.ALL`（彻底停止所有重复）。`AlarmActivity.markDoneAndClose()` 和
`NotificationActionReceiver` 的 `ACTION_MARK_DONE` 分支调用这个方法时都**没有传 scope**，
等于都在用最激进的默认值。对比列表页 `ReminderListViewModel.onToggleComplete()`：
对重复提醒会先弹 `RepeatScopeDialog` 让用户选"仅本次"还是"停止所有重复"，
两条路径的行为完全不一致。

**根因**：`markDone(reminder, scope = RepeatActionScope.ALL)` 这个默认值本身是为
"非重复提醒"场景设计的（此时 scope 传什么都无所谓），但被另外两处调用方
在"重复提醒"场景下意外沿用了这个默认值，没有专门处理。

**修复**：`AlarmActivity.markDoneAndClose()` 和 `NotificationActionReceiver.ACTION_MARK_DONE`
都改成显式传 `RepeatActionScope.ONCE`（仅完成本次、正常推进下一次），和"从通知栏点标为完成"
的直觉预期一致；全屏页是抢时间关响铃的场景，不适合再弹二次确认框打断，所以选择默认 ONCE
而不是弹窗——真要彻底停止一条重复提醒，请到列表页操作（那里会弹窗确认，行为不变）。

**验证结果**：代码走查确认修复后行为和列表页一致；未做真机验证，请在
`REAL_DEVICE_VERIFICATION_2026-07-10.md`「强提醒」表格里补测"全屏页标为完成"和
"通知栏标为完成"这两行，分别用重复提醒和非重复提醒各测一次。

**防复发规则**：任何"完成/删除"类操作，只要业务上有重复提醒的"仅本次 / 全部"区分，
所有调用入口（列表页、全屏提醒页、通知栏 Action、以后如果加桌面小组件/语音助手）
都要显式传 scope，不能依赖默认值——默认值只应该覆盖"这个参数根本不重要"的场景。

#### 11.2 中危：多处调度失败被完全静默吞掉，无日志可查（已修复）

**现象**：`CompleteReminderUseCase.markDone()`"仅本次"分支、`markPending()`、
`DeleteReminderUseCase`"仅删除本次"分支、`RescheduleAllAlarmsUseCase`（开机/冷启动重建闹钟）
里，"重新调度下一次闹钟"这一步都是 `runCatching { alarmScheduler.scheduleExact(...) }
.onSuccess { repository.update(...) }`，**没有 `onFailure`**。一旦调度失败（比如精确闹钟权限
被临时收回），数据库不会更新，但前面几行往往已经把旧闹钟取消了——这条重复提醒就会变成
"列表里看着还是 PENDING，其实没有任何系统闹钟在支撑它"的僵尸记录，且没有任何日志能发现。

这和上一轮（问题 1）里 `AlarmAlertService` 的"静默失败"是同一类根因，只是发生在
"调度"阶段而不是"触发/响铃"阶段，属于同一条防复发规则应该覆盖、但当时遗漏的地方。

**修复**：以上 4 处全部补上 `.onFailure { Log.e(...) }`，`RescheduleAllAlarmsUseCase`
额外加了整体进度日志（开始处理几条、精确闹钟权限缺失时是哪条被跳过）。

**验证结果**：未做真机验证。如果之后出现"重复提醒莫名其妙不再提醒了"的情况，
现在 logcat 里能直接看到是这几个 UseCase 里的哪一步失败、异常是什么。

#### 11.3 低危：新建 alarmId 存在极小概率的碰撞风险（已修复）

`AlarmSchedulerImpl.generateAlarmId()` 原来是 `System.currentTimeMillis() % Int.MAX_VALUE`，
如果两条提醒在同一毫秒内创建，会拿到相同的 alarmId，导致 PendingIntent 互相覆盖、
其中一条提醒的系统闹钟丢失。正常手动点"新增"几乎不可能踩到，但一旦以后做批量导入/
快速连续创建，风险会变得不可忽略。已改成 `Random.nextInt(1, Int.MAX_VALUE)`，
只影响新创建的提醒，数据库里已有的 alarmId 不受影响，不需要迁移。

#### 11.4 低危：状态栏"下一个闹钟"预览意图的 kind 写死为到点提醒（已修复）

`AlarmSchedulerImpl.buildShowPendingIntent()` 之前无论是到点提醒、提前提醒还是稍后提醒，
统一写死传 `AlarmAlertKind.DUE`。这个意图只在用户手动点系统状态栏那个小闹钟图标预览时才会用到，
不影响到点后的真实强提醒流程，影响面很小，但既然顺手能修就一并改成按实际 kind 传值。

#### 11.5 观察到但未改动：「工作日」和「周末」重复类型使用的时区不一致

`RepeatCalculator.computeNextWorkday()`（工作日）**特意**用硬编码的 `Asia/Shanghai`
时区判断"下一个工作日"，这是有专门单元测试（`workdaysUseBeijingMondayToFriday`）覆盖的
**有意为之**的设计，不是疏漏；但 `computeNextWeekend()`（周末）用的是设备系统默认时区，
两者不一致。红米 K80 Pro 正常使用场景下系统时区就是中国时区，实际不会触发；
但如果手动把系统时区改成别的地区（出差/测试），"工作日"和"周末"这两个重复类型可能会对
"今天算不算工作日"给出不一样的答案。

这是一个产品选择题（要不要让"周末"也固定用北京时间，和"工作日"保持一致），
不是一个能直接判断对错的 bug，所以这一轮**没有改代码**，留给你决定；如果需要统一，
告诉我要哪个方向，我可以直接改。

#### 11.6 复核确认：以下方面走查后没有发现问题

- 数据库迁移（1→2、2→3）都是安全的 `ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT`，
  且在 `DatabaseModule` 里正确用 `.addMigrations()` 接入，**没有** `fallbackToDestructiveMigration()`
  这种会导致老用户数据被清空的写法。
- `RepeatCalculator` 的月末 31 号裁剪逻辑（`computeNextMonth`）正确使用"原始触发日"而不是
  "上一次实际触发日"，2 月裁剪到 28/29 之后，3 月能正确恢复到 31 号，不会永久漂移——
  有专门单元测试覆盖，逻辑走查也确认正确。
- `EditReminderScreen` 的"删除"操作对重复提醒有正确的"仅本次/全部"选择弹窗（和 11.1 的
  "标为完成"问题不是同一处）。
- `CategoryViewModel`：分类名称去重、空名校验、最多 20 个分类、内置分类禁止删除、
  删除分类前把归属提醒改成"未分类"，逻辑都正确。
- `AndroidManifest.xml`：没有申请 `INTERNET` 权限，没有网络相关代码；四个系统触发组件
  （`AlarmReceiver`、`NotificationActionReceiver`、`AlarmActivity`、`AlarmAlertService`）
  都正确设置 `exported="false"`，只有必须由系统外部广播触发的 `BootReceiver`
  设了 `exported="true"`，且用 intent-filter 限定了具体 action，没有过度暴露。
- DI 模块（`AlarmModule`、`RepositoryModule`、`DatabaseModule`）之间是单向依赖，
  没有循环依赖。
- 全项目搜索未发现硬编码密码/token/私钥；`build.gradle.kts` 没有内嵌签名密钥信息。
- Entity/Model/Mapper 字段一一对应，`Mappers.kt` 里 `toDomain()`/`toEntity()`
  没有遗漏字段。

### 12. 通用教训（本轮新增）

7. **"完成/删除"这类对重复数据有"仅本次/全部"语义的操作，任何新增调用入口都必须显式传
   scope，不能依赖函数默认值**——默认值只应该覆盖对该场景真正无意义的参数。上线前建议
   全项目搜索一遍该方法的所有调用点，确认没有遗漏。
8. **"调度失败后打日志"这条规则，要应用到所有"调度"相关代码路径，而不只是"触发/响铃"那一条**——
   本轮在触发阶段（`AlarmAlertService`）已经加过日志，但调度阶段（完成/删除/开机重建）
   当时被漏掉了，说明防复发规则需要在全项目搜索同类模式（这里是
   `runCatching { alarmScheduler.schedule... }.onSuccess { ... }` 且没有 `onFailure`）
   来验证覆盖完整，而不是只改被明确反馈到的那一处。

## 后续迭代准则

- 先确认产品语义，再写代码。比如“提前提醒”到底是轻通知还是强提醒，必须先定清楚。
- 所有提醒触发链路都要区分：
  - 调度是否成功；
  - Receiver 是否触发；
  - Service 是否启动；
  - 声音是否播放；
  - 震动是否触发；
  - 屏幕是否点亮；
  - Activity 是否被系统允许拉起；
  - Notification 是否在锁屏可见。
- 每次改提醒链路，至少新增或更新一个单元测试。
- 每次改通知渠道，考虑是否需要新 channel ID。
- 每次改数据库字段，必须检查：
  - Entity；
  - Domain model；
  - Mapper；
  - Migration；
  - ViewModel 初始状态；
  - Add/Edit UseCase；
  - UI 表单；
  - 测试数据。
- 不要把系统权限问题和代码逻辑问题混在一起。先看代码路径，再看 AppOps / 权限 / logcat。
- 不能只凭“能编译”判断体验完成，提醒类 App 必须真机锁屏验证。

## 历史环境限制（2026-07-10，已被当前主干验证替代）

- **以下两条只描述 2026-07-10 的外部 AI 交付环境。** 当时 AI 助手在无 Android SDK、无法访问 Google/Gradle 官方仓库、
  未连接任何物理设备的沙箱环境里完成，只能做到代码走查 + 静态修复，无法执行
  `./gradlew testDebugUnitTest assembleRelease`、无法安装运行、无法抓取真机 logcat。
  详见下方"构建与安装"里的"2026-07-10 构建结果"和"给下一轮排查的诊断信息"。
- **锁屏显示 / 悬浮窗类问题最终是否解决，取决于用户是否手动开启对应系统权限。**
  代码层面能做的是补全设置页引导和防御性修复，但 Android/MIUI 没有公开 API 能让 App
  自己查询或直接开启"锁屏显示""悬浮窗"这两个 MIUI 专属权限，无法在代码里"自动修复"。
- **提前提醒 / 到点提醒能否 100% 准时触发，仍然受 MIUI/HyperOS 系统策略影响。**
  即使用户已经设置"省电策略：无限制"，如果"自启动""锁屏显示""悬浮窗"任何一项没开，
  仍可能出现响铃/震动/弹窗不完整的情况。
- **当时没有新增/修改单元测试。** 那一轮修复集中在系统交互相关的代码（前台服务、通知、
  权限跳转、Compose 页面按钮布局），这些行为强依赖 Android 运行时和真机权限状态，
  超出纯 JVM 单元测试能覆盖的范围；该历史缺口已在 2026-07-18 补齐为 13 个测试类、41 个用例。

## 给下一轮排查的诊断信息

如果本轮修复之后，问题 1（提前提醒无响铃/震动/弹窗）或问题 2（锁屏无预览）仍然复现，
请按下面的步骤操作，把结果发回来，可以直接定位到具体是哪一步失败：

1. **先在 App 内自查一次，不需要连电脑：** 打开 App →设置 → 看"精确闹钟""全屏提醒"两行是否都显示
   "已开启"；再看新增的"锁屏通知与后台弹窗"这一块，按提示分别打开"通知设置"和
   "小米权限管理"，确认"允许在锁屏上显示通知"“锁屏显示”“悬浮窗”都已经是开启状态。
   这一步经常能直接定位问题，不需要日志。
2. **如果第 1 步都已经开启但问题依旧，用电脑抓 logcat：**
   ```bash
   adb logcat -c
   # 抓完之后设置一个 1~2 分钟后触发的提醒，等它触发完
   adb logcat -v time "AlarmReceiver:*" "AlarmAlertService:*" "ActivityTaskManager:*" "NotificationManager:*" "*:E" > reminder_debug.log
   ```
   本轮已经在 `AlarmReceiver` 和 `AlarmAlertService` 里加了完整日志（tag 分别是
   `AlarmReceiver` / `AlarmAlertService`），正常触发时应该能看到类似：
   ```text
   D AlarmReceiver: onReceive reminderId=... kind=advance
   D AlarmReceiver: reminder=... status=PENDING kind=advance
   D AlarmAlertService: onStartCommand action=...ALARM_ALERT_START
   I AlarmAlertService: 强提醒投递完毕 alarmId=... kind=ADVANCE foreground=true ... fallback=false
   ```
   如果日志在某一行之后就断了，或者出现了 `E AlarmReceiver` / `E AlarmAlertService` 开头的行，
   把完整的 `reminder_debug.log` 发回来即可，不需要自己分析。
3. 把 `reminder_debug.log` 和当时的手机截图（通知栏 + 锁屏）一起发回来，
   比只描述文字现象能更快定位问题。

## 项目结构

```text
app/src/main/java/com/reminder/local
├── data          # Room、DataStore、Repository 实现
├── di            # Hilt Module
├── domain        # Model、UseCase、AlarmScheduler 接口
├── notification  # 通知渠道和通知构建
├── receiver      # AlarmReceiver、BootReceiver、通知 Action Receiver
├── service       # 强提醒前台服务、铃声震动、全屏启动策略
├── ui            # Compose 页面、组件、主题、导航
├── util          # 权限和时间工具
├── AlarmActivity.kt
├── App.kt
└── MainActivity.kt
```

## 仓库提交规则

提交源码和项目配置：

- `app/src/**`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle/wrapper/**`
- `gradle.properties`
- `README.md`

不提交本机产物：

- `build/`
- `.gradle/`
- `local.properties`
- `.idea/`
- APK 构建产物
- `.DS_Store`

## 隐私与安全

- 不申请 `INTERNET` 权限。
- 不上传提醒内容。
- 不读取联系人、短信、相册、定位等敏感数据。
- 使用最小权限原则，只声明提醒功能必需权限。
- 数据仅存储在本机 App 私有目录。

## 主集成验收记录（2026-07-14）

本次由主集成方将外部 AI 交付的源码导入当前 `main` 基线。以下是本机重新取得的证据，优先级高于本文中外部沙箱的历史“未编译”记录：

完整表格见 `INTEGRATION_REPORT_2026-07-14.md`。

- 已在本机 JDK 17、Android SDK 环境运行 `./gradlew testDebugUnitTest assembleRelease`，结果为 `BUILD SUCCESSFUL`。
- 已通过全部现有 JVM 单元测试，并额外加入 `availableAlarmIdSkipsAnIdAlreadyUsedByAnotherReminder` 回归用例。
- 外部交付将闹钟 ID 从时间戳改为随机数；主集成补齐了本地数据库占用检查与最多 100 次重试，避免随机数碰撞覆盖另一条提醒的 PendingIntent。
- 已复核：全屏页和通知栏的“标为完成”对重复提醒均显式使用 `RepeatActionScope.ONCE`，不会再默认停止整条重复提醒。
- 已复核：提前提醒与到点提醒都会进入 `AlarmAlertService` 强提醒链路；只有到点提醒会推进重复周期。

### 尚待真机验收

当前 ADB 没有检测到已授权设备，因此以下依赖红米 K80 Pro / HyperOS 系统策略的项目不能宣称已通过：锁屏通知内容预览、全屏页后台拉起、亮屏、响铃、震动、提前提醒与到点提醒两次触发、开机恢复。请以 `REAL_DEVICE_VERIFICATION_2026-07-10.md` 的表格逐项实测，并保留 logcat 证据。

### 明确的产品决策待确认

“工作日”已按用户要求固定为北京时间周一至周五；“周末”目前沿用设备系统时区。这是保留的产品选择，不是本次无授权改动的范围。若要让“周末”也固定为北京时间，应同时补充跨时区单元测试后再改。
