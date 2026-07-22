# 安全审计报告 - 2026-07-22

> 当前版本、APK 和验收边界以 [HANDOFF_CURRENT.md](HANDOFF_CURRENT.md) 为准；本报告仅覆盖 2026-07-22 所列审计范围。

## 范围与结论

审计范围为当前 v1.3 工作区的 Kotlin 源码、Manifest、Gradle 配置、Room Schema、`.gitignore` 与已生成 APK 元数据。该 App 是纯离线单机工具，无登录、网络接口、WebView、动态代码执行或外部文件导入链路。

| 级别 | 数量 | 结论 |
|---|---:|---|
| Critical | 0 | 未发现可远程利用的执行或数据泄露路径 |
| High | 0 | 未发现硬编码凭据、可导出业务组件或网络数据传输路径 |
| Medium | 1 | Release 构建使用 Android Debug 证书，只适合个人直装 |
| Low | 1 | 锁屏公开显示标题/备注是产品要求，存在旁观泄露风险 |
| Info | 2 | 依赖 CVE 数据库未联网核验；Lint 有维护性警告 |

## 已验证项

- `aapt dump badging` 未显示 `android.permission.INTERNET`；Manifest 仅请求闹钟、通知、全屏、振动、WakeLock、前台媒体服务和开机恢复所需权限。
- 主业务 Activity、Receiver、Service 均 `exported=false`；仅启动器 Activity 对系统桌面导出，不接收外部 reminderId。
- 业务 PendingIntent 使用 immutable 标志；扫描未发现 `FLAG_MUTABLE`。
- Room 未使用 `fallbackToDestructiveMigration`；备份和设备迁移均关闭。
- 已扫描跟踪文件和配置文件：未发现 API key、token、密码、私钥、证书、带凭据的数据库连接串，且 `local.properties`、`secrets.properties`、`*.jks`、`*.keystore` 已被忽略。
- 无 `WebView`、`Runtime.exec`、`ProcessBuilder`、原始 SQL 拼接、反序列化或自定义加密实现。
- 应用日志只记录内部 reminderId、alarmId、触发种类和状态；未记录标题、备注正文。

## 发现与处置

### Medium: Debug 签名的 Release APK

位置：[app/build.gradle.kts](app/build.gradle.kts) 的 `release.signingConfig = debug`。

风险：该 APK 可用于个人直装和覆盖升级当前同一调试签名的安装，但不具备商店发布或长期生产签名链的安全性。

处置：本次保持调试签名，避免强制更换签名导致用户已安装版本无法覆盖升级。后续正式发布须由用户安全保管自己的 keystore 和密码，并通过本机或受控 CI 注入；严禁提交到仓库。

### Low: 锁屏内容可见

锁屏显示标题和备注是产品核心体验。风险是附近人员可读到这些内容。

处置：README 明确提示不要填写密码、验证码、身份证号等；用户可在系统通知设置中隐藏敏感内容。

## 证据边界

- 依赖版本从 Gradle 配置读取；本轮没有联网 CVE 数据库，不能声称“依赖零漏洞”。
- `lintDebug` 成功但保留 55 个 warnings、3 个 hints，主要是可升级依赖、KAPT、弃用 API、未使用资源；它们不是已确认的可利用漏洞。
- 未连接真机，无法从实际设备侧复核厂商系统权限页、通知渠道持久化和锁屏展示。
