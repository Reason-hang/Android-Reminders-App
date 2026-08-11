# Android Reminders App

> 状态：v1.10 当前开发基线（已安装到目标机；部分定时真机回归已完成）
> 更新时间：2026-08-11
> 包名：`com.reminder.local`
> 目标设备：红米 K80 Pro、HyperOS、Android 16

Android 本地离线强提醒应用。v1.9 已根据红米真机日志修复 Android 16 的后台 Activity Launch（BAL）拦截路径；v1.10 补齐并发提醒接管和停止来源诊断，已安装到目标机。锁屏、熄屏和已解锁场景仍需用新的单提醒定时触发完成验收。

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
| 强提醒 | `ADVANCE`、`DUE`、`SNOOZE` 独立触发；通知、声音、振动、十分钟会话和系统 FullScreenIntent 协同 |
| 提醒操作 | 关闭、稍后 10 分钟、标为完成；重复提醒支持仅本次或全部 |
| 分类 | 新增、编辑、删除分类，颜色标识和未分类筛选 |
| 系统恢复 | 开机、应用更新、精确闹钟权限恢复和重新打开 App 后重建未来闹钟 |
| 权限引导 | 精确闹钟、全屏提醒、通知渠道、小米后台弹窗、自启动和省电策略入口 |
| 诊断与排障 | 实例级提醒黑匣子、24 小时增强诊断、应用侧结论解析、用户主动 ZIP 导出与清除 |

完整需求和验收口径见 [产品需求文档](docs/01-产品文档/01-产品需求文档.md)。

## 强提醒语义

- 每个事件使用 `reminderId + alarmId + kind + occurrenceTime` 标识；旧事件不得停止新事件。
- `ADVANCE`、`DUE`、`SNOOZE` 是独立事件，只有 `DUE` 推进重复周期。
- 同一提醒的提前事件未关闭时，到点事件仍重新响铃、振动并投递视觉提醒。
- 强提醒开始 10 分钟后自动停止声音和振动、结束前台状态、关闭匹配的全屏页并撤销 App 主动保亮；保留一条静音通知供用户回看。
- 全屏页只由系统对通知的 `FullScreenIntent` 投递；应用不再从后台自行 `PendingIntent.send()` 拉起 Activity，也不在通知前主动点亮屏幕。
- Android 在设备已解锁且用户正在操作时可能只展示高优先级横幅；普通第三方 App 不能强制改变该系统决策。
- 每条提醒的“响铃提醒”“震动提醒”是持久化的独立开关；两者关闭时只会有视觉提醒，编辑页会明确提示。

## 技术架构

```text
Compose UI → ViewModel → UseCase → Repository interface → Room DAO

AlarmScheduler → AlarmManager → AlarmReceiver → AlarmAlertService
                                      ├─ 声音、振动与十分钟会话计时
                                      └─ 高优先级通知 → SystemUI FullScreenIntent → AlarmActivity
```

Room 当前为 v4。应用未声明 `INTERNET`，业务组件默认不导出，PendingIntent 使用 immutable；诊断日志保存在应用私有目录，不记录提醒标题和备注正文。详见 [强提醒诊断与日志体系](docs/04-开发文档/03-强提醒诊断与日志体系.md)。

## 当前验证

2026-08-11 已对 v1.9 在红米 K80 Pro 执行真实定时取证，并在其基础上构建、覆盖安装 v1.10。v1.10 尚未经历新的定时触发；真机结论按下表区分版本与证据层。

| 证据层 | 结果 |
|---|---|
| JVM 测试 | 93 个测试通过，失败、错误、跳过均为 0 |
| 仪器测试源码 | `compileDebugAndroidTestKotlin` 通过；未在设备或模拟器运行 |
| Lint | 0 error、63 warnings、3 hints |
| Release 构建 | `assembleRelease` 通过 |
| APK | v1.10 (`1.10 (11)`) Release 已构建，SHA-256：`10b3dee42868eb9c8a7ae13eb87b004934c3566eb51924f70dba4958625558ee` |
| 目标真机 | v1.9 锁屏 ADVANCE 已确认 SystemUI 启动全屏页；DUE 已确认 Receiver、前台服务和携带 FullScreenIntent 的通知投递，因测试中切换/并发提醒而未确认 DUE 页面与十分钟结束；v1.10 已覆盖安装待复验 |

## 构建方式

需要 JDK 17 和 Android SDK 36：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleRelease --console=plain
```

本次构建产物为 `app/build/outputs/apk/release/app-release.apk`；对外交付前需复制为版本化名称。APK、keystore、密码、token 和 `local.properties` 不提交仓库。

## 文档入口

完整阅读顺序、当前文档清单和维护规则见 [文档总索引](docs/00-文档总索引.md)。接手项目先读 [当前项目状态与交接](docs/04-开发文档/02-当前项目状态与交接.md)，关键方案见 [AI 自主决策记录](docs/04-开发文档/01-AI自主决策记录.md)。
