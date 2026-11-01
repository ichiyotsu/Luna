# Luna

**Luna** 是一款 Material 3 Expressive 风格的安卓浏览器（包名 `com.m3browser`）。本仓库包含它的源码，以及自动构建并发布安装包的 GitHub Actions 工作流。

## 功能

- 多标签页浏览，可预览、切换和关闭标签
- 地址栏输入网址，或用必应、Google、百度搜索
- 书签与浏览历史
- 文件下载到系统「下载」目录
- 浅色、深色、跟随系统三种主题；可开关莫奈取色，或选择预设种子色
- 地址栏与工具栏合并，可放在顶部或底部，滚动时收成网址胶囊
- 历史记录、无痕模式、网页翻译、书签导入导出，以及可选的 GitHub Gist 同步
- 可作为浏览器打开 http/https 链接，并接收系统的网页搜索请求

最低支持 Android 8.0（API 26）。当前版本 `1.0.3`（versionCode 4）。

## 在手机上安装

1. 打开本仓库的 [Releases](https://github.com/ichiyotsu/Luna/releases) 页面。
2. 下载最新 Release 里的 `Luna-v1.0.3.apk`（标签名为 `v1.0.3`；文件名是 `Luna-v<versionName>.apk`）。
3. 在手机上允许安装未知来源应用，然后打开该 APK。1.0.3 与 1.0.2 使用同一签名，可以直接覆盖安装。已安装 `1.0.1` 的仍需先卸载，因为那个版本的签名密钥是随机的。

推送到 `main`、手动运行 **Build and release APK** 工作流，或推送 `v*` 标签时，Actions 会构建 APK，并创建或更新标签为 `v<versionName>` 的 Release，把安装包挂为附件。

## 本地构建

需要 JDK 17，以及 Android SDK platform 37（`platforms;android-37.0`）和 build-tools `37.0.0`。

```bash
./gradlew assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/`。
