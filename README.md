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
| 编辑页 | 一屏保存 | 已实现 | 顶部右侧有“保存”按钮，底部保存按钮保留 |
| 时间选择 | 上下滑动时分选择器 | 已实现 | 分钟为 1 分钟一格，不使用轮盘 TimePicker |
| 提前提醒 | 固定选项 | 已实现 | 无、5 分钟前、15 分钟前、30 分钟前、1 小时前、2 小时前、1 天前、2 天前、1 周前、1 个月前 |
| 提前提醒 | 自定义提前提醒 | 已实现 | 数字 1-200，单位支持分钟、小时、天、周、个月 |
| 提前提醒 | 强提醒链路 | 已实现 | 提前提醒也会启动响铃、震动、亮屏和全屏提醒页 |
| 到点提醒 | 精确闹钟 | 已实现 | 使用 `AlarmManager.setAlarmClock()` |
| 到点提醒 | 全屏提醒页 | 已实现 | `AlarmActivity` 支持锁屏展示、点亮屏幕、手动关闭 |
| 到点提醒 | 前台服务兜底 | 已实现 | `AlarmAlertService` 负责响铃、震动、前台通知，避免 Activity 被系统拦截后完全无提醒 |
| 到点提醒 | 锁屏通知预览 | 已实现 | 强提醒通知渠道使用公开锁屏可见性和 BigText 预览 |
| 到点提醒 | 通知操作 | 已实现 | 关闭、稍后提醒 10 分钟、标为完成 |
| 重复提醒 | 基础重复 | 已实现 | 每小时、每天、每周、每月、每年 |
| 重复提醒 | 扩展重复 | 已实现 | 周日、周末、工作日、每两周、每 3 个月、每 6 个月 |
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
- 全屏提醒页和前台通知同时存在：Activity 负责中间弹窗体验，Notification 负责锁屏预览、系统记录和被系统拦截时的兜底。
- 强提醒通知渠道使用版本化 ID：`reminder_fullscreen_alert_v2`。原因是 Android 通知渠道创建后设置会持久化，代码更新不能可靠覆盖旧渠道的锁屏可见性、重要性等设置。
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
| Navigation Compose | 2.8.4 |
| DataStore Preferences | 1.1.1 |
| minSdk / targetSdk / compileSdk | 31 / 36 / 36 |

当前源码规模：

- 主源码 Kotlin 文件：58 个。
- 单元测试 Kotlin 文件：6 个。

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
- `AlarmAlertContentFormatterTest`：锁屏预览标题和提前提醒标题。
- `AlarmTriggerPolicyTest`：提前提醒/到点提醒强提醒策略，确保提前提醒不推进重复周期。
- `AddReminderUseCaseTest`：新增提醒业务规则。

最近一次已执行验证：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew testDebugUnitTest assembleRelease
```

结果：

- `BUILD SUCCESSFUL`
- APK 签名验证通过：APK Signature Scheme v2
- 包名：`com.reminder.local`
- 应用名：`提醒事项`

最近一次交付 APK：

```text
/Users/lizhihang/Documents/Codex/2026-06-30/10-0-1-app-apk-app/outputs/app-release.apk
```

SHA-256：

```text
1a8d2c07e26ebd31f8ed9e382f7f11980c5c31d353774b03966382a2ea1bc1dd
```

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

根因：

- 旧通知渠道可能已经被系统或用户设置固化，锁屏可见性不是明确 PUBLIC。
- 代码修改同一个 channel ID 不一定能改变手机上已创建渠道的行为。

修复：

- 强提醒渠道改为新 ID：`reminder_fullscreen_alert_v2`。
- 新渠道显式设置 `lockscreenVisibility = VISIBILITY_PUBLIC`。
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
