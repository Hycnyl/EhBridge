# EhBridge

离线 Android 画廊批量转换工具，支持「文件夹＋散图」与「文件夹＋CBZ」双向转换。

An offline Android batch converter for loose-image and CBZ gallery folders.

**1.1.1** · Android 8.0+ · 简体中文 / English · Universal APK

[下载 / Downloads](https://github.com/Hycnyl/EhBridge/releases) · [GPL-3.0](LICENSE)

## 简体中文

- **转换与复制**：散图打包为 CBZ，CBZ 解包为散图，同格式画廊直接复制；保留原始页面，源目录只读，已有输出不覆盖。
- **可选元数据**：`.ehviewer`、ComicInfo XML 和封面均可缺失；输出 XML 可从 CBZ 恢复或根据文件夹信息生成，解包时生成 CBOR `.ehviewer`。
- **批量总览**：扫描预览、全部／散图／CBZ／无效筛选、快速滚动、处理进度与取消。
- **提示与报告**：按类型控制非阻断提示，查看画廊详情，导出完整文字报告。
- **原生界面**：Material 3、亮色／暗色／跟随系统、Android 12+ 动态配色、Android 13+ 主题图标和大屏双栏；「关于」提供软件信息、项目链接与使用指南。

## English

- **Convert and copy:** pack loose pages into CBZ, extract CBZ into loose files, or copy matching layouts. Original pages are preserved, sources are read-only and existing output is never overwritten.
- **Optional metadata:** `.ehviewer`, ComicInfo XML and covers may be absent. Output XML is recovered from CBZ or generated from folder information; extraction creates CBOR `.ehviewer` state.
- **Batch overview:** scan previews, All / Loose / CBZ / Invalid filters, fast scrolling, progress and cancellation.
- **Warnings and reports:** per-type warning controls, gallery details and complete text report export.
- **Native interface:** Material 3, light/dark/system themes, dynamic colors on Android 12+, themed icons on Android 13+ and a two-column tablet layout. About includes software information, project links and a usage guide.

## 界面预览 / Screenshots

| 亮色 / Light | 暗色 / Dark |
| --- | --- |
| <a href="docs/screenshots/phone-light.png"><img src="docs/screenshots/phone-light.png" width="300" alt="EhBridge 亮色界面 / Light theme"></a> | <a href="docs/screenshots/phone-dark.png"><img src="docs/screenshots/phone-dark.png" width="300" alt="EhBridge 暗色界面 / Dark theme"></a> |

**大屏双栏 / Tablet**

[![EhBridge 大屏双栏界面 / Tablet layout](docs/screenshots/tablet-light.png)](docs/screenshots/tablet-light.png)

## 引用与致谢 / References and acknowledgments

- [EhViewer](https://github.com/UjuiUjuMandan/EhViewer) · [EhViewer-NekoInverter](https://github.com/EhViewer-NekoInverter/EhViewer)：画廊命名与 SpiderInfo 格式参考 / Gallery naming and SpiderInfo format references.
- [AndroidX / Jetpack Compose](https://developer.android.com/jetpack/compose) · [Material 3](https://m3.material.io/)：界面组件 / UI components.
- [Kotlin](https://kotlinlang.org/) · [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) · [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)：语言、协程与 CBOR / Language, coroutines and CBOR.
- [Gradle](https://gradle.org/)：构建工具 / Build tooling.

作者 / Author：[Hycnyl](https://github.com/Hycnyl) · Copyright (C) 2026 Hycnyl · [GPL-3.0-only](LICENSE) · [第三方许可 / Third-party notices](THIRD_PARTY_NOTICES.md)
