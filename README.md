# Android Reminders App

> 状态：v1.12 当前发布基线（解锁强提醒悬浮层已获授权并通过真机验证）
> 更新时间：2026-08-12
> 包名：`com.reminder.local`
> 目标设备：红米 K80 Pro、HyperOS、Android 16

Android 本地离线强提醒应用。v1.12 针对 Android 在已解锁状态把 FullScreenIntent 降级为横幅的确定性平台行为，新增经用户显式授权的应用悬浮强提醒页；锁屏继续使用系统 FullScreenIntent。时间选择器的分钟滚轮改为循环排列，`58、59、00、01` 可连续选择。

## 目录

- [产品定位](#产品定位)
- [已实现功能](#已实现功能)
- [强提醒语义](#强提醒语义)
- [技术架构](#技术架构)
- [当前验证](#当前验证)
- [构建方式](#构建方式)
- [文档入口](#文档入口)

## 产品定位

- 简体中文、单用户、完全离线，数据保存在本机 Room/SQLite。
- 面向个人日常事项，重点保证准时、可重复、可提前、可稍后和锁屏可感知。
- 不包含账号、联网、广告、统计、云同步、远程推送、桌面 Widget、自定义铃声和节假日调休日历。

## 已实现功能

| 模块 | 功能 |
|---|---|
| 提醒事项 | 新增、编辑、删除、完成、按分类筛选、待办与历史状态展示 |
| 时间规则 | 单次、每小时、每 5 小时、每天、工作日、每周、周日、周末、每两周、每月、每 3 个月、每 6 个月、每年，可设置重复结束日期 |
| 提前提醒 | 固定提前量和分钟、小时、天、周、月自定义提前量 |
| 通知方式 | 每条提醒独立控制响铃与振动，设置页管理新提醒默认值 |
| 强提醒 | `ADVANCE`、`DUE`、`SNOOZE` 独立触发；锁屏使用系统 FullScreenIntent，解锁使用应用悬浮强提醒页 |
| 提醒操作 | 关闭、稍后 10 分钟、标为完成；重复提醒支持仅本次或全部 |
| 分类 | 新增、编辑、删除分类，颜色标识和未分类筛选 |
| 系统恢复 | 开机、应用更新、精确闹钟权限恢复和重新打开 App 后重建未来闹钟 |
| 权限引导 | 精确闹钟、锁屏全屏提醒、解锁悬浮强提醒页、通知渠道、自启动和省电策略入口 |
| 诊断与排障 | 实例级提醒黑匣子、24 小时增强诊断、应用侧结论解析、用户主动 ZIP 导出与清除 |

完整需求和验收口径见 [产品需求文档](docs/01-产品文档/01-产品需求文档.md)。

## 强提醒语义

- 每个事件使用 `reminderId + alarmId + kind + occurrenceTime` 标识；旧事件不得停止新事件。
- `ADVANCE`、`DUE`、`SNOOZE` 是独立事件，只有 `DUE` 推进重复周期。
- 同一提醒的提前事件未关闭时，到点事件仍重新响铃、振动并投递视觉提醒。
- 强提醒开始 10 分钟后自动停止声音和振动、结束前台状态、关闭匹配的全屏页并撤销 App 主动保亮；保留一条静音通知供用户回看。
- 锁屏或屏幕关闭时，只由系统对通知的 `FullScreenIntent` 投递 `AlarmActivity`；应用不从后台自行拉起 Activity，也不在通知前主动点亮屏幕。
- 已解锁且用户正在操作时，Android 会把 FullScreenIntent 降级为横幅。v1.12 在用户开启“显示在其他应用上层”后，由前台提醒服务挂载可交互的应用悬浮强提醒页；未授权时明确降级为系统横幅。
- 悬浮页和锁屏页共享关闭、稍后 10 分钟、标为完成及十分钟自动结束语义；悬浮页移除后不再由 App 保持屏幕常亮。
- 每条提醒的“响铃提醒”“震动提醒”是持久化的独立开关；两者关闭时只会有视觉提醒，编辑页会明确提示。
- “稍后提醒 10 分钟”也是独立 `SNOOZE` 事件。v1.12 真机已验证从 21:03:16 操作到 21:13:16 准时回调，并在解锁状态重新显示悬浮整页、响铃和震动。

## 技术架构

```text
Compose UI → ViewModel → UseCase → Repository interface → Room DAO

AlarmScheduler → AlarmManager → AlarmReceiver → AlarmAlertService
                                      ├─ 声音、振动与十分钟会话计时
                                      ├─ 锁屏 → 高优先级通知 → SystemUI FullScreenIntent → AlarmActivity
                                      └─ 解锁且已授权 → TYPE_APPLICATION_OVERLAY → 应用强提醒页
```

Room 当前为 v4。应用未声明 `INTERNET`，业务组件默认不导出，PendingIntent 使用 immutable；诊断日志保存在应用私有目录，不记录提醒标题和备注正文。详见 [强提醒诊断与日志体系](docs/04-开发文档/03-强提醒诊断与日志体系.md)。

## 当前验证

2026-08-12 的诊断包已确认：DUE、ADVANCE 和 SNOOZE 都能到达 Receiver、前台服务和通知链路；旧版解锁态失败点是 SystemUI 不启动 Activity。v1.12 已获用户授权并在红米 K80 Pro 上确认 `TYPE_APPLICATION_OVERLAY` 整页可见；真实 ADVANCE、DUE 和 SNOOZE 均完成解锁态整页验证，启用的声音和震动也实际启动。

| 证据层 | 结果 |
|---|---|
| JVM 测试 | 100 个测试通过，失败、错误、跳过均为 0 |
| 仪器测试 | `compileDebugAndroidTestKotlin` 与测试 APK 构建通过；悬浮层真机用例 1/1 通过 |
| Lint | 0 error、63 warnings、3 hints |
| Release 构建 | `assembleRelease` 通过 |
| APK | v1.12 (`1.12 (13)`) Release 已构建并验签；版本化文件与校验值见 APK 交付文档 |
| 目标真机 | v1.12 Release 已覆盖安装且读回 `1.12 (13)`；悬浮权限为 `allow`，专用用例和真实 ADVANCE、DUE、SNOOZE 整页通过。锁屏、十分钟自动结束等未覆盖场景仍按真机清单复验 |

## 构建方式

需要 JDK 17 和 Android SDK 36：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleRelease --console=plain
```

本次构建产物为 `app/build/outputs/apk/release/app-release.apk`；对外交付文件为 `outputs/ReminderApp-v1.12.apk`。APK、keystore、密码、token 和 `local.properties` 不提交仓库。

## 文档入口

完整阅读顺序、当前文档清单和维护规则见 [文档总索引](docs/00-文档总索引.md)。接手项目先读 [当前项目状态与交接](docs/04-开发文档/02-当前项目状态与交接.md)，关键方案见 [AI 自主决策记录](docs/04-开发文档/01-AI自主决策记录.md)。
