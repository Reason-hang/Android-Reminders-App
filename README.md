# 提醒事项 App

一个给 Android 手机个人自用的离线提醒事项 App。界面为简体中文、极简白色风格，数据全部保存在手机本地 Room 数据库中，不联网、不上传任何数据。

## 功能

- 新增、编辑、删除提醒事项
- 标题、备注、日期时间、分类、优先级、重复规则
- 响铃、震动开关
- 未完成 / 已完成分组
- 分类筛选
- 左滑删除、右滑或勾选完成
- 到点系统通知
- 通知栏快捷操作：标为完成、稍后提醒 10 分钟
- 重复提醒：每天、每周、每月
- 分类管理：内置工作、生活、学习、健康，也可新增自定义分类
- 开机后自动重新注册未来提醒
- 设置页展示精确闹钟权限和小米/红米省电策略提示

## 技术栈

- Kotlin 2.1.21
- Jetpack Compose + Material 3
- Android Gradle Plugin 8.13.2
- Gradle Wrapper 8.13
- Room 2.8.3
- Hilt 2.58
- Navigation Compose 2.8.4
- DataStore Preferences 1.1.1
- minSdk 31
- compileSdk / targetSdk 36

## 构建

需要本机安装 JDK 17 和 Android SDK 36。

```bash
./gradlew assembleRelease
```

构建成功后 APK 位于：

```text
app/build/outputs/apk/release/app-release.apk
```

当前项目的 release 构建已验证通过，生成的 APK 使用 debug signingConfig 签名，适合个人直装测试使用。

## 安装注意事项

- 首次启动请允许通知权限，否则到点提醒不会弹出通知。
- 如果设置页显示精确闹钟权限未开启，请点击入口到系统设置中开启。
- 小米/红米/HyperOS 建议手动把本 App 的省电策略设为“无限制”，并允许自启动。

## 隐私

本 App 没有申请网络权限，不包含任何网络请求代码。所有提醒、分类和设置都只存储在本机。
