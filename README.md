# Android Reminders App

> 当前版本：v1.15（versionCode 16）
> 更新时间：2026-08-16
> 包名：`com.reminder.local`；目标设备：红米 K80 Pro、HyperOS、Android 16

单用户、完全离线的 Android 强提醒应用。核心是可靠调度和可恢复的数据闭环：提醒可新增、编辑、完成、移入回收站、恢复、永久删除和手动整理；不包含任何录音、音轨、语音转写、账号或联网功能。

## 已实现能力

| 模块 | 当前能力 |
|---|---|
| 提醒 | 新增、编辑、完成、单次/重复、提前提醒、稍后 10 分钟、分类筛选、响铃与振动独立开关 |
| 强提醒 | `ADVANCE`、`DUE`、`SNOOZE` 独立实例；锁屏走 FullScreenIntent，解锁且获悬浮权限时显示应用整页悬浮强提醒；10 分钟自动结束 |
| 数据闭环 | 删除先进入“已删除”回收站；支持多选恢复与永久删除；已删除记录不会出现在列表或重新被调度 |
| 首页治理 | 标题最多 500 字符；首页最多 4 行、省略号截断；紧凑卡片布局；“整理”支持多选置顶、置底、删除和长按把手精细拖拽 |
| 编辑体验 | 新增/编辑页支持本次会话内撤销、重做；顶部使用撤销/重做/对勾保存图标，删除收纳到更多菜单 |
| 恢复与诊断 | 开机/更新/权限恢复后重建有效闹钟；应用私有 Reminder Black Box 支持本地诊断与主动 ZIP 导出 |

## 关键规则

- `triggerTime` 是重复模板，`nextTriggerTime` 是下一次真实提醒时间；排序从不改变两者或 AlarmManager 调度。
- 回收站使用 `DELETED + deletedAt + statusBeforeDelete`：移入后立即取消闹钟；恢复后按删除前状态恢复，未来待办会重新注册闹钟。
- 只有 `DUE` 推进重复周期；`ADVANCE` 和 `SNOOZE` 不吞掉正式到点提醒。
- Android 在设备解锁且用户正在使用时可能把 FullScreenIntent 降级为横幅；本 App 仅在用户显式授予悬浮权限后使用 `TYPE_APPLICATION_OVERLAY` 提供整页强提醒。

## 架构

```text
Compose UI → ViewModel → UseCase → Repository → Room/SQLite

AlarmScheduler → AlarmManager → AlarmReceiver → AlarmAlertService
                                      ├─ 声音、振动、十分钟会话
                                      ├─ 锁屏：SystemUI FullScreenIntent → AlarmActivity
                                      └─ 解锁且已授权：TYPE_APPLICATION_OVERLAY
```

Room 当前为 v6。v4→v5 增加手动排序和回收站字段；v5→v6 修复可空 `statusBeforeDelete` 的 Converter 契约，并清洗异常历史枚举值；无 destructive migration。应用不声明 `INTERNET`，业务组件默认不导出，诊断不记录标题和备注正文。

## 当前验证状态

| 证据层 | v1.15 结果 |
|---|---|
| JVM | 112 个测试通过，失败 0、错误 0；新增可空 `statusBeforeDelete` Converter 回归 |
| 数据库 | Room v6 schema 已生成；新增 v5→v6 清洗迁移和真实 DAO 映射仪器用例，待设备/模拟器实际执行 |
| 编译、Lint、Release | Android 仪器源码编译、Lint（0 error、67 warnings、4 hints）与 Release 已通过；详细输出见 [自动化验证](docs/05-测试与验收/01-自动化验证.md) |
| 红米真机 | v1.15 覆盖安装和“v1.14 空 `statusBeforeDelete` 冷启动”待验收；不能把旧版证据当作本版通过证据 |

## 构建

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
./gradlew testDebugUnitTest :app:compileDebugAndroidTestKotlin lintDebug assembleRelease --console=plain
```

构建产物为 `app/build/outputs/apk/release/app-release.apk`；对外交付文件必须命名为 `outputs/ReminderApp-v1.15.apk`，不提交 APK、keystore、密码或本机配置。

## 文档

从 [文档总索引](docs/00-文档总索引.md) 开始。接手时先读 [当前项目状态与交接](docs/04-开发文档/02-当前项目状态与交接.md)，再执行 `git status -sb && git log -1 --oneline`；关键取舍见 [AI 自主决策记录](docs/04-开发文档/01-AI自主决策记录.md)。
