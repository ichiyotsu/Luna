# Luna

**M3 Browser** 是一款 Material 3 Expressive 风格的安卓浏览器（包名 `com.m3browser`）。本仓库包含它的源码，以及自动构建并发布安装包的 GitHub Actions 工作流。

## 功能

- 多标签页浏览，可预览、切换和关闭标签
- 地址栏输入网址，或用必应、Google、百度搜索
- 书签与浏览历史
- 文件下载到系统「下载」目录
- 浅色、深色、跟随系统三种主题；Android 12 及以上可使用 Material You 壁纸取色
- 可作为浏览器打开 http/https 链接，并接收系统的网页搜索请求

最低支持 Android 8.0（API 26）。当前版本 `1.0.1`（versionCode 2）。

## 在手机上安装

1. 打开本仓库的 [Releases](https://github.com/ichiyotsu/Luna/releases) 页面。
2. 下载最新 Release 里的 `M3Browser-v1.0.1.apk`（标签名为 `v1.0.1`；版本号随 `app/build.gradle.kts` 里的 `versionName` 变化，文件名始终是 `M3Browser-v<versionName>.apk`）。
3. 在手机上允许安装未知来源应用，然后打开该 APK 完成安装。

推送到 `main`、手动运行 **Build and release APK** 工作流，或推送 `v*` 标签时，Actions 会构建 APK，并创建或更新标签为 `v<versionName>` 的 Release，把安装包挂为附件。签名使用运行器上自动生成的 debug 密钥，不需要在仓库里配置任何 secret，下载后可以直接安装。

## 本地构建

需要 JDK 17，以及 Android SDK platform 37（`platforms;android-37.0`）和 build-tools `37.0.0`。

```bash
./gradlew assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/`。
