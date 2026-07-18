# 最终测试报告 - 2026-07-18

## 验证范围

- 强提醒响铃、全屏启动、关闭后通知保留。
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
| JVM 测试类 | 9 |
| JVM 测试用例 | 29 |
| 失败 | 0 |
| 错误 | 0 |
| 跳过 | 0 |
| Release 构建 | 通过 |
| Lint Vital | 通过 |

新增回归覆盖：

- Android 16 后台全屏启动策略。
- 关闭强提醒后保留通知记录。
- 多提醒重叠时旧操作不能误停新提醒。
- 时间滚轮选择最接近视口中心的项目。
- 10 分钟前、3 小时前、2 周前。
- 每隔 5 小时。

## APK 验证

- 路径：`../outputs/app-release.apk`
- 包名：`com.reminder.local`
- 版本：`1.1`（`versionCode 2`）
- minSdk / targetSdk：31 / 36
- 大小：48,270,426 字节
- SHA-256：`81cc3ebd672548441d827f6d3858ef1605ed95cc7eca5274679d844851def69d`
- `apksigner verify`：通过 APK Signature Scheme v2
- 签名：Android Debug，适合个人直装测试，不适合应用商店发布

## 安全与隐私

- Manifest 不包含 `android.permission.INTERNET`。
- 未发现硬编码 API Key、密码、私钥或客户端密钥。
- 未加入账号、网络传输或云同步能力。

## 独立代码审查

审查发现的多提醒 Service 状态覆盖、Activity 旧查询覆盖新提醒、加载期间关闭丢通知、铃声无降级、滚轮提交旧值共 5 项，均已修复并重新通过完整构建。

## 未完成的真机验收

执行 `adb devices -l` 时没有已授权设备，因此不能宣称以下项目已通过真机测试：

- 红米 K80 Pro 锁屏时亮屏、响铃、震动、全屏页和内容预览。
- 手机已解锁使用中时主动拉起全屏页。
- 点击“关闭”后通知中心记录继续保留。
- 两条提醒同一分钟触发时的前后提醒切换。
- HyperOS 自启动、后台弹出界面和重启恢复。

真机验收步骤见 `REAL_DEVICE_VERIFICATION_2026-07-10.md`。
