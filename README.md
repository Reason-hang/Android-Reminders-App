# 提醒事项 App —— 功能清单与复查说明

给红米 K80 Pro（Android 16 / API 36）个人自用的离线提醒事项 App。界面为简体中文、极简白色风格，数据全部保存在手机本地 Room 数据库中，不联网、不上传任何数据。

本 README 汇总项目功能清单、架构、技术选型、构建方式，以及本轮复查和打包过程中发现并修复的问题。

## 一、功能清单

| 模块 | 功能点 | 状态 | 说明 |
|---|---|---|---|
| **提醒 CRUD** | 新增提醒 | 已实现 | 标题（≤50字）、备注（≤200字，可选）、时间、分类、优先级、重复规则、响铃/震动开关 |
| | 编辑提醒 | 已实现 | 修改时间会取消旧闹钟、校验新时间不能是过去、重新注册闹钟；对已完成/已过期提醒改到未来时间会重新激活 |
| | 删除提醒 | 已实现 | 列表左滑 + 二次确认弹窗；重复提醒可选“仅本次”（跳到下一次）或“全部”（彻底删除） |
| | 完成/撤销完成 | 已实现 | 列表右滑、勾选框、通知栏按钮三种入口；非重复提醒完成后支持 Snackbar 撤销 |
| **列表页** | 未完成/已完成分组 | 已实现 | 未完成按“优先级 → 时间”排序，已完成按完成时间倒序 |
| | 分类筛选 | 已实现 | 顶部横向 Chip：全部 / 各分类 / 未分类 |
| | 空状态 | 已实现 | 无任何提醒时显示引导图标和文案 |
| **到点提醒** | 系统通知 | 已实现 | 使用 `AlarmManager.setAlarmClock()` 注册精确闹钟 |
| | 响铃/震动 | 已实现 | 每条提醒可单独设置；用 4 个通知渠道区分响铃/震动组合 |
| | 通知栏快捷操作 | 已实现 | “标为完成”、“稍后提醒（10分钟）”，App 被系统杀掉也能响应 |
| | 点击通知跳转 | 已实现 | 跳转并定位到对应提醒的编辑页 |
| **重复提醒** | 每天 / 每周 / 每月 | 已实现 | 月份边界按最初设置日期为目标，超出当月天数才裁剪，31 号提醒不会永久漂移到 28/29 号 |
| | 重复截止日期 | 已实现 | 可选，超过截止日期后自动停止并标记为已完成 |
| **分类管理** | 内置分类 | 已实现 | 首次建库自动插入“工作 / 生活 / 学习 / 健康”四个分类，不可删除 |
| | 自定义分类 | 已实现 | 增/改/删，最多 20 个，8 种预设颜色；删除分类会把其下提醒改为“未分类”，不会删除提醒本身 |
| **数据持久化** | 本地数据库 | 已实现 | Room（SQLite），关闭 App 重开数据仍保留，卸载即清空 |
| **系统集成** | 精确闹钟权限 | 已实现 | 同时声明 `USE_EXACT_ALARM` 和 `SCHEDULE_EXACT_ALARM`，设置页提供权限入口 |
| | 通知权限 | 已实现 | 首次启动请求 `POST_NOTIFICATIONS` |
| | 开机重启后恢复 | 已实现 | `BootReceiver` 重新注册所有未来闹钟，并把关机期间错过的重复提醒追到未来的下一个时间点 |
| | 简体中文 / 白色界面 | 已实现 | 固定浅色白底，不跟随系统深色模式，不启用动态取色 |
| **设置页** | 默认响铃/震动 | 已实现 | 只影响新建提醒的默认值 |
| | 权限状态展示 | 已实现 | 精确闹钟未开启时给出引导入口 |
| | MIUI 省电提示 | 已实现 | 引导用户手动关闭省电限制、允许自启动 |

不在本版范围内：多端同步、云备份、语音输入、桌面 Widget、自定义提醒铃声、单元测试/UI 测试代码。

## 二、架构与技术栈

```text
UI (Jetpack Compose)
   ↓ StateFlow
ViewModel (MVVM, Hilt 注入)
   ↓
UseCase（Add / Edit / Complete / Delete / Reschedule）
   ↓
Repository（接口 + 实现，隔离 Room）
   ↓
Room DAO → SQLite
```

| 组件 | 版本 |
|---|---|
| Kotlin | 2.1.21 |
| Jetpack Compose (Material 3) | Compose BOM 2026.06.00 |
| Android Gradle Plugin | 8.13.2 |
| Gradle Wrapper | 8.13 |
| Room | 2.8.3 |
| Hilt | 2.58 |
| Navigation Compose | 2.8.4 |
| DataStore Preferences | 1.1.1 |
| minSdk / targetSdk / compileSdk | 31 / 36 / 36 |

关键设计决策：

- 精确闹钟使用 `setAlarmClock()`，系统会按闹钟处理，Doze 模式下更稳定。
- 重复提醒单独维护 `nextTriggerTime`，和最初设置的 `triggerTime` 分离，避免每月重复时日期漂移。
- 新增/编辑遵循“数据库写入 + 闹钟注册”的一致性约束：闹钟注册失败会回滚数据库写入或恢复旧数据。
- 为符合个人工具 App 的使用预期，界面固定为白色浅色，不跟随深色模式和动态取色。

## 三、本轮全面复查与修复

### 1. 源码与功能清单核对

复核提醒 CRUD、分类筛选、重复提醒、通知栏操作、开机恢复、权限提示、MIUI/HyperOS 省电提示等功能链路，确认源码中均有对应实现。

### 2. Hilt 注入问题

发现 `SettingsDataStore` 注入 `Context` 时需要 `@ApplicationContext` 限定符，否则 Hilt 在编译期可能报缺失绑定。已修复。

### 3. 构建工具链问题

原始压缩包缺少 `gradle/wrapper/gradle-wrapper.jar`，`gradlew` 脚本也存在 JVM 参数拆分问题，导致无法直接构建。已补齐标准 Gradle Wrapper，并替换为可正常运行的脚本。

### 4. 版本兼容问题

实际构建过程中发现：

- Room 2.6.1 无法读取 Kotlin 2.2 metadata。
- Hilt 2.52 也无法兼容 Kotlin 2.2 metadata。
- Hilt 2.59+ 要求 AGP 9，不适合当前 AGP 8.13.2。

最终采用稳定可构建组合：

- Kotlin 2.1.21
- AGP 8.13.2
- Gradle 8.13
- Room 2.8.3
- Hilt 2.58

### 5. 资源与 Compose 编译问题

已修复：

- `ic_empty_state.xml` 引用不存在的 `colorControlNormal` 属性导致资源链接失败。
- `CategoryFilterRow.kt` 缺少 `Modifier.padding` import。
- `EditReminderScreen.kt` 的 `verticalScroll` import 错误。
- `ExposedDropdownMenu` 调用方式与当前 Material 3 版本不兼容。

### 6. 构建验证

已在本机完成 release 构建：

```bash
./gradlew assembleRelease
```

结果：

- `BUILD SUCCESSFUL`
- 生成 APK：`app/build/outputs/apk/release/app-release.apk`
- APK 签名验证通过：v2 signing scheme
- 包名：`com.reminder.local`
- 应用名：`提醒事项`
- minSdk：31
- targetSdk：36

## 四、构建与安装

### 环境要求

- JDK 17
- Android SDK 36
- Android Build Tools 35/36

### 构建 APK

```bash
./gradlew assembleRelease
```

构建产物：

```text
app/build/outputs/apk/release/app-release.apk
```

当前 release 包使用 debug signingConfig 签名，适合个人直装和真机测试使用。如果后续要正式发布到应用商店，应改用正式 keystore。

### 首次安装后建议检查

- 允许通知权限。
- 如果设置页显示精确闹钟权限未开启，进入系统设置手动开启。
- 在小米/红米/HyperOS 上，将本 App 省电策略设为“无限制”，并允许自启动。
- 新建一条 1 分钟后的提醒，锁屏等待，确认通知、响铃、震动符合设置。
- 测试通知栏“标为完成”和“稍后提醒”。
- 测试手机重启后，未来提醒是否仍能触发。

## 五、仓库说明

仓库只提交源码和项目配置，不提交本机或构建产物：

- 不提交 `build/`
- 不提交 `.gradle/`
- 不提交 `local.properties`
- 不提交 APK 文件
- 不提交 `.DS_Store`

## 六、隐私说明

本 App 没有申请网络权限，不包含任何网络请求代码。所有提醒、分类和设置只保存在本机数据库中，卸载 App 后数据随之删除。
