// 顶层 build 文件：只声明插件版本，不在这里 apply
//
// 版本选择说明（2026-07 复查修正）：
// AGP 8.7.x 官方文档明确写明"最高只支持 API 35"，而本项目 compileSdk/targetSdk = 36（Android 16），
// 用 8.7.2 会导致 Gradle Sync 直接失败。这里改用 8.13.2 —— 这是 Google 官方兼容表里
// "compileSdk 36.0" 所要求的已验证稳定版本（其对应的最低 Gradle 版本是 8.13，
// 已经同步更新到 gradle/wrapper/gradle-wrapper.properties）。
// Kotlin 使用 2.1.21，避开 Room/Hilt 注解处理器读取 Kotlin 2.2 metadata 的兼容问题。
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("org.jetbrains.kotlin.kapt") version "2.1.21" apply false
    id("com.google.dagger.hilt.android") version "2.58" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
