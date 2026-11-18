# 一刻专注 · Yike Focus

一款为 Android 平板设计的液态玻璃番茄钟。上下滚动时，壁纸的颜色透过任务卡片和统计面板。手指划过悬浮导航，选中胶囊的圆角边缘会折射下方的内容，文字和时钟依然清晰。

**[下载安卓版 2.4.0](https://github.com/PinkPurpleYellow/yike-focus/releases/download/v2.4.0/yike-focus-2.4.0.apk)**　·　[更新记录](app/src/main/assets/changelog.txt)　·　[隐私政策全文](app/src/main/assets/privacy_policy.txt)

![液态玻璃模式下的平板专注页，壁纸透过计时卡和悬浮导航](docs/liquid-glass.png)

*2.4.0 应用界面，使用项目绘制的渐变壁纸进行主机渲染。*

在「设置 → 外观与主题」选择「液态玻璃」，可以调整透明度和模糊强度。Android 13 及以上支持动态着色器折射，Android 12/12L 支持实时模糊，Android 8–11 使用兼容磨砂效果。喜欢安静的界面时，可以关闭装饰动效，或切回传统卡片。

专注计时、任务清单和七天统计都能离线使用。开始一轮专注，打开全屏时钟，或为自己选一段雨声。

## 安装与升级

支持 Android 8.0 及以上。用平板下载安装包，打开后按系统提示安装。首次使用时可以先阅读用户协议和隐私政策。已有旧版的用户请先完成当前专注，按需在设置里导出备份，再打开新版 APK 选择更新。

## 本版亮点

- 卡片与悬浮导航使用液态玻璃材质，文字和统计图保持清晰。
- 首次使用时可离线阅读协议；日后也能在「设置 → 关于」查看。
- 历史更新按版本整理。详见[更新日志](app/src/main/assets/changelog.txt)。

## 从源码构建

安装 JDK 17、Android SDK Platform 35、Build Tools 35.0.0，配置 Android SDK 路径。用 Android Studio 打开仓库，或在根目录运行 Gradle Wrapper。Windows 用 `gradlew.bat :app:assembleDebug`，macOS 或 Linux 用 `bash gradlew :app:assembleDebug`。构建完成后，调试包位于 `app/build/outputs/apk/debug/`。

## 版权与联系

开发者 **PinkPurpleYellow** · ff1921570609@outlook.com。自有代码的权利声明及第三方许可见[版权说明](COPYRIGHT.md)。
