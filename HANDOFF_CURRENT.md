# 当前交接入口 - Android Reminders App

> **给新 AI / 新对话窗口：先读本文件，再读 README。** 本文件是当前事实入口；未被本文件明确列为当前证据的历史文档，只能作为背景，不能据此宣称已完成。

## 一分钟状态

| 项目 | 当前事实 |
|---|---|
| 项目 | Android Reminders App，简体中文、完全离线的 Android 强提醒工具 |
| 源码路径 | `/Users/lizhihang/Documents/Codex/2026-06-30/10-0-1-app-apk-app/work/ReminderApp` |
| Git 仓库 | `git@github.com:Reason-hang/Android-Reminders-App.git` |
| 分支 / 代码基线 | `main` / `445876efcb510429db13f353d6c126b300a0baa2` (`fix: harden v1.3 reminder alerts`)；新窗口必须再运行 `git status -sb && git log -1 --oneline` 获取文档或后续改动的最新提交 |
| App | `com.reminder.local`，v1.3，versionCode 4，Room v4 |
| APK | `/Users/lizhihang/Documents/Codex/2026-06-30/10-0-1-app-apk-app/outputs/ReminderApp-v1.3.apk` |
| APK SHA-256 | `960a6736368d14c415b0732dc2566e522910b67316c97015882e4123deef39c8` |
| APK 大小 / 签名 | 48,418,233 bytes；v2 有效；Android Debug 证书，仅适合个人直装测试 |
| 自动化验证 | 2026-07-22：20 个测试类，63/63 JVM 测试通过；`lintDebug`、`assembleRelease` 通过 |
| 真机验收 | **未通过也未失败：ADB 当前无设备，未取得红米 K80 Pro 的安装、logcat、截图或录屏证据** |

## 产品与架构约束

- 无账号、无网络、无云同步；禁止新增 `INTERNET` 或远程数据链路，除非用户明确批准。
- 强提醒目标链路：`AlarmManager -> AlarmReceiver -> AlarmAlertService -> 声音/震动/WakeLock/全屏页/公开锁屏通知`。通知出现不等于整条链路成功。
- `ADVANCE`、`DUE`、`SNOOZE` 必须是独立事件；只有 `DUE` 可推进重复周期。
- 所有通知和全屏操作都必须携带 `reminderId + alarmId + kind + occurrenceTime`；旧 occurrence 只能结束自身打断，不能改动新提醒或停止新提醒。
- `triggerTime` 是重复模板，`nextTriggerTime` 是实际下一次时间；月末重复不得永久漂移。
- Room 结构变更必须同步 Model、Entity、DAO、Mapper、Migration、Schema、测试；禁止 destructive migration。
- 数据库与 AlarmManager 不是跨系统事务；涉及新增、编辑、完成、删除、重建调度时必须保留失败补偿和诊断日志。

## 已修复且有代码/测试证据

1. 提前、正式、稍后提醒的事件隔离；提前和稍后不再推进重复周期。
2. 旧通知/旧全屏操作以 occurrence 隔离，不会误停正在响的新提醒。
3. 旧的一次性提醒通知在用户改期后，不会完成或取消改期后的新提醒。
4. 新增、编辑、完成、删除、批量重建的调度/数据库失败补偿；调度失败日志不再静默吞掉。
5. 优先级字段已从 UI、领域、Room 与 v3->v4 迁移中移除；`alarmId` 具备数据库唯一约束。
6. 前台服务保活通知与用户强提醒通知分离；后者为公开锁屏预览并含 FullScreenIntent。
7. 编辑页顶部保存、小时+分钟双列滚轮、提前提醒与重复规则扩展、开机/更新/权限变化恢复入口均已实现。

## 仍未验收或需继续改进

| 优先级 | 项目 | 验收标准 |
|---|---|---|
| P0 | 红米 K80 Pro 真机强提醒 | ADB 识别设备；安装 v1.3；锁屏/后台/划掉最近任务下，提前和正式到点各有完整提醒；保存 logcat、截图或录屏 |
| P1 | v3->v4 数据迁移 | 用 `MigrationTestHelper` 验证 priority 移除、重复/零值 `alarmId` 修复及旧数据可读；覆盖安装后真机复核 |
| P1 | 取消闹钟失败补偿 | 完成/删除全部后，若 `AlarmManager.cancel()` 异常，提供可持久化、可重试、可观测的清理策略 |
| P2 | 生产签名 | 用户安全保管正式 keystore；不提交密钥、密码或 `local.properties`；确认覆盖升级策略 |
| P2 | Lint 清理 | 当前 55 warnings、3 hints 未阻断构建；按风险处理弃用 API、依赖升级、KAPT 和未使用资源 |

## 必读顺序

1. [README.md](README.md)：产品定义、架构、约束、当前验证摘要。
2. [AUDIT_REPORT_2026-07-18.md](AUDIT_REPORT_2026-07-18.md)：修复问题、根因、残余风险与证据等级。
3. [TEST_REPORT_2026-07-18.md](TEST_REPORT_2026-07-18.md)、[BUILD_APK_REPORT_2026-07-18.md](BUILD_APK_REPORT_2026-07-18.md)：自动化与 APK 证据。
4. [REAL_DEVICE_VERIFICATION_2026-07-10.md](REAL_DEVICE_VERIFICATION_2026-07-10.md)：真机验收表和 ADB 采集命令。
5. [PROJECT_MEMORY_2026-07-18.md](PROJECT_MEMORY_2026-07-18.md)、[SECURITY_AUDIT_2026-07-22.md](SECURITY_AUDIT_2026-07-22.md)：防复发与安全边界。

`CHANGELOG_2026-07-10.md`、`CHANGELOG_2026-07-17.md`、`TEST_REPORT_2026-07-10.md`、`INTEGRATION_REPORT_2026-07-14.md` 是历史记录，不可作为当前验收依据。

## 新窗口首条提示词

```text
请接手 Android Reminders App。先阅读 HANDOFF_CURRENT.md、README.md，然后运行：
git status -sb && git log -1 --oneline

以 HANDOFF_CURRENT.md、Git 当前提交和本次实际命令输出为事实来源。历史报告只作背景，不能替代当前验证。
先用自己的话复述：当前版本、已验证证据、未验证真机链路、残余风险和本次待办；若任务涉及代码或配置，再给出分步计划与验收标准，等待我授权后执行。
不得把 JVM/Lint/APK 通过写成红米真机强提醒通过；不得提交 APK、keystore、密码、token、local.properties 或其他隐私文件。
```

## 每次交接前更新规则

1. 先刷新 `git status -sb`、`git log -1`、版本化 APK 哈希、测试/构建结果和 ADB 状态。
2. 仅将当次真实执行结果写入“当前事实”；历史记录必须保留日期并明确标为历史。
3. 修改 Alarm、通知、重复、Room 或 APK 交付逻辑时，同步更新 README、审计/测试/构建报告、真机验收表和本文件。
4. 交付前至少运行相关测试、`git diff --check`、Manifest/Schema 校验；需要宣称真机通过时，必须附设备证据。
