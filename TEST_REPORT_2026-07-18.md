# 最终测试报告 - 2026-07-18

## 验证范围

- 提前/到点两次强提醒调度、响铃/震动降级、全屏启动、关闭后通知保留。
- 锁屏用户通知与前台服务通知分离、公开内容预览和通知渠道版本化。
- 多提醒重叠和 Activity 异步加载竞态。
- 双列小时/分钟滚轮与中心项吸附。
- 新增提前提醒选项和“每隔 5 小时”重复。
- Release APK 构建、版本、签名、最小权限和敏感信息静态检查。

## 自动化结果

执行命令：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew testDebugUnitTest assembleRelease
```

结果：`BUILD SUCCESSFUL`。

| 指标 | 结果 |
|---|---|
| JVM 测试类 | 13 |
| JVM 测试用例 | 41 |
| 失败 | 0 |
| 错误 | 0 |
| 跳过 | 0 |
| Release 构建 | 通过 |
| Lint Vital | 通过 |
| 完整 Debug Lint | 通过，错误 0 |

新增回归覆盖：

- Android 16 后台全屏启动策略。
- 关闭强提醒后保留通知记录。
- 多提醒重叠时旧操作不能误停新提醒。
- 时间滚轮选择最接近视口中心的项目。
- 10 分钟前、3 小时前、2 周前。
- 每隔 5 小时。
- 提前与到点的 AlarmManager 操作和系统展示 PendingIntent 均不互相覆盖。
- 前台提升失败、声音/震动部分失败时按缺失媒介选择备用渠道。
- 提前提醒未关闭时，到点提醒重新开始本轮播放。
- 服务保活通知、用户强提醒通知和四种备用渠道使用不同渠道 ID。

## APK 验证

- 路径：`../outputs/ReminderApp-v1.2.apk`
- 包名：`com.reminder.local`
- 版本：`1.2`（`versionCode 3`）
- minSdk / targetSdk：31 / 36
- 大小：48,384,713 字节
- SHA-256：`d0f791d33a47ab638ed01c77b724bb6e24195f610802f25cbc20303b3be5587a`
- `apksigner verify`：通过 APK Signature Scheme v2
- 签名：Android Debug，适合个人直装测试，不适合应用商店发布

## 安全与隐私

- Manifest 不包含 `android.permission.INTERNET`。
- 未发现硬编码 API Key、密码、私钥或客户端密钥。
- 未加入账号、网络传输或云同步能力。

## 独立代码审查

本轮审查额外发现：服务启动成功后，`startForeground()` 或手动播放仍可能失败；提前/到点的系统展示 PendingIntent 仍可能覆盖。两项均已补充回归测试并修复，随后重新通过干净的完整构建。

## 未完成的真机验收

执行 `adb devices -l` 时没有已授权设备，因此不能宣称以下项目已通过真机测试：

- 红米 K80 Pro 锁屏时亮屏、响铃、震动、全屏页和内容预览。
- 手机已解锁使用中时主动拉起全屏页。
- 点击“关闭”后通知中心记录继续保留。
- 两条提醒同一分钟触发时的前后提醒切换。
- HyperOS 自启动、后台弹出界面和重启恢复。

真机验收步骤见 `REAL_DEVICE_VERIFICATION_2026-07-10.md`。
