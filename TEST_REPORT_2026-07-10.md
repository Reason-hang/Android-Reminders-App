# 测试报告 — 2026-07-10

> 主集成更新（2026-07-14）：下方内容是外部交付方在受限沙箱中的历史记录，不代表当前主干状态。主集成已在本机 Android SDK 环境执行 `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew testDebugUnitTest assembleRelease`，结果为 `BUILD SUCCESSFUL`。新增 alarmId 碰撞回归测试已通过。真机 ADB 在本次验收时未发现已授权设备，因此真机强提醒场景仍是待验收项。

## 1. 单元测试

**未在本环境执行**（无 Android SDK、无法联网下载 Gradle 发行版和依赖，见 README「已知限制」）。

静态复核：

- 逐个 `grep` 确认 6 个现有测试文件（`RepeatCalculatorTest`、`AdvanceReminderCalculatorTest`、
  `AlarmAlertLaunchPolicyTest`、`AlarmAlertContentFormatterTest`、`AlarmTriggerPolicyTest`、
  `AddReminderUseCaseTest`）均不引用本轮删除或修改的符号
  （`NotificationHelper` 死方法/渠道、`AlarmReceiver.notificationHelper` 字段、
  `AlarmAlertService.setSilent`），本轮改动不会导致这些测试编译失败。
- 本轮没有新增单元测试用例（改动集中在前台服务、通知构建、权限跳转 Intent、
  Compose 页面按钮布局，这些行为强依赖 Android Framework/真机运行时状态，
  超出纯 JVM 单元测试的覆盖范围）。

结论：**需要用户在有 SDK 的机器上跑一遍 `./gradlew testDebugUnitTest` 才能拿到真正的
`BUILD SUCCESSFUL` 证据**，本报告不能替代。

## 2. Gradle 构建（`assembleRelease`）

**尝试执行，失败，原因是环境限制，非代码问题。** 原始命令与终端输出：

```text
$ ./gradlew testDebugUnitTest assembleRelease
Fetching distribution.
Downloading https://services.gradle.org/distributions/gradle-8.13-bin.zip
Attempt 1/1 failed. Reason: Server returned HTTP response code: 403 for URL:
https://services.gradle.org/distributions/gradle-8.13-bin.zip
Exception in thread "main" java.io.IOException: Server returned HTTP response code: 403
	at org.gradle.wrapper.Download.download(SourceFile:1)
	at org.gradle.wrapper.Install.forceFetch(SourceFile)
	at org.gradle.wrapper.Install.lambda$createDist$0(SourceFile:9)
	at org.gradle.wrapper.Install.createDist(SourceFile:24)
	at org.gradle.wrapper.GradleWrapperMain.lambda$prepareWrapper$0(SourceFile:2)
	at org.gradle.wrapper.GradleWrapperMain.main(SourceFile:2)
```

补充实测（逐一验证沙箱网络白名单确实不包含 Android/Gradle 官方仓库域名）：

```text
curl https://dl.google.com               -> HTTP 403
curl https://services.gradle.org/...     -> HTTP 403
curl https://repo.maven.apache.org/...   -> HTTP 403
```

环境检查：

```text
$ java -version
openjdk version "21.0.10" 2026-01-20   # JDK 本身是有的

$ echo $ANDROID_HOME
(空，未设置，也没有找到 SDK 目录)
```

结论：连 Gradle 发行版本身都下载不到，Android SDK 也不存在，**在当前沙箱环境里，
不管代码写得对不对，`assembleRelease` 都无法执行成功**。这不是本轮代码改动引入的问题。

## 3. APK 签名验证

**未执行**——上一步没有产出新 APK，没有可验证的对象。

## 4. 真机安装结果

**未执行**——Claude 运行的沙箱容器和用户的电脑、手机之间没有连接，无法执行
`adb install`。用户消息里提到的"已经连接了手机和电脑"，指的是用户自己的电脑与手机之间的连接，
不等于 Claude 的沙箱环境能访问到。

## 5. 真机关键场景验证结果

**未执行**，原因同上。已在 README 新增「给下一轮排查的诊断信息」一节，
列出用户可以自行验证、以及如需进一步排查该如何抓 logcat 并同步回来的具体步骤。

## 6. 交付前静态自检（本环境能做到的部分）

替代真实编译的最后一道防线，本轮对修改到的 6 个 Kotlin 文件做了：

- 逐文件人工走查每一处改动的上下文（import、括号闭合、被引用的符号是否还存在）。
- 括号/圆括号配对统计脚本自检（弱校验，只能发现明显的语法错误，不能替代编译器）：
  全部 6 个文件 `{}` 和 `()` 计数配对，未发现明显不匹配。
- 全项目 `grep` 确认被删除的符号（`CHANNEL_SOUND_VIBRATE` 等 5 个渠道常量、
  `showAdvanceReminderNotification` 等 3 个方法、`AlarmReceiver.notificationHelper` 字段）
  在其他任何文件里都没有残留引用。

这些自检不能替代真实的 Kotlin 编译器（本环境没有 `kotlinc`，也没有 Android SDK），
**仍然需要一次真实的 `./gradlew` 构建来最终确认没有编译错误**。

## 7. 第二轮复查（对照完整 README 全项目排查）的静态自检

- 新增/修改的 6 个文件（`AlarmActivity.kt`、`NotificationActionReceiver.kt`、
  `CompleteReminderUseCase.kt`、`DeleteReminderUseCase.kt`、`RescheduleAllAlarmsUseCase.kt`、
  `AlarmSchedulerImpl.kt`）括号配对自检通过。
- `grep` 确认 `markDone` 的调用点、`generateAlarmId()`、`buildShowPendingIntent()`
  均未被现有单元测试引用，本轮改动不会导致现有测试编译失败。
- `buildShowPendingIntent` 新增了 `kind` 参数，`grep` 确认全项目只有 3 处调用
  （`scheduleOne` 两次、`scheduleSnooze` 一次），均已同步更新签名，没有遗漏调用点。
- 同样没有真实编译（原因见「已知限制」），需要用户在自己的机器上跑一遍
  `./gradlew testDebugUnitTest assembleRelease`。

## 总体结论

| 验收项 | 状态 |
|---|---|
| 单元测试跑通 | 未执行（需用户机器） |
| `BUILD SUCCESSFUL` | 未拿到（环境限制，见上） |
| APK 可安装 | 未验证（没有新 APK） |
| 真机强提醒验证（响铃/震动/亮屏/全屏页/锁屏预览） | 未验证（无真机连接） |
| 代码走查修复 3 个反馈问题的已知根因 | 已完成，详见 CHANGELOG 和 README 故障复盘第 9/10 条 |
| 静态自检（语法/死引用） | 已完成，见上 |
