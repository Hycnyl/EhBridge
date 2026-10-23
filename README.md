# EhBridge

离线 Android 画廊批量转换工具，在「文件夹＋散图」与「文件夹＋CBZ」之间转换，保留原始页面，源目录只读。

An offline Android batch converter for loose-image and CBZ gallery folders. Original pages are preserved and source folders remain read-only.

**1.1.1** · Android 8.0+ · 简体中文 / English · Universal APK

[下载 / Downloads](https://github.com/Hycnyl/EhBridge/releases) · [简体中文](#简体中文) · [English](#english) · [GPL-3.0](LICENSE)

## 界面预览 / Screenshots

| 常规设备 · 亮色 / Phone · Light | 常规设备 · 暗色 / Phone · Dark |
| --- | --- |
| <a href="docs/screenshots/phone-light.png"><img src="docs/screenshots/phone-light.png" width="300" alt="EhBridge 亮色界面 / Light theme"></a> | <a href="docs/screenshots/phone-dark.png"><img src="docs/screenshots/phone-dark.png" width="300" alt="EhBridge 暗色界面 / Dark theme"></a> |

**大屏双栏 / Tablet, two-column layout**

[![EhBridge 大屏双栏界面 / Tablet layout](docs/screenshots/tablet-light.png)](docs/screenshots/tablet-light.png)

点击图片可查看原图。 / Click an image for full size.

## 简体中文

### 主要功能

- **双向转换**：散图打包为 CBZ，CBZ 解包为散图；同格式任务复制画廊。页面不解码、不转码。
- **可选元数据**：散图的 `.ehviewer`、外部 `ComicInfo.xml`、封面和 CBZ 内部 XML 均可缺失。缺少状态文件或外部侧车文件时显示非阻断提示，输出 XML 自动恢复或生成。
- **批量管理**：扫描预览、「全部／散图／CBZ／无效」筛选、可拖动的快速滚动滑块、进度与取消。
- **提示与报告**：按类型开关非阻断提示，点击画廊查看详情，导出完整文字报告。
- **原生界面**：Material 3、亮色／暗色／跟随系统、Android 12+ 动态配色、大屏双栏，以及受支持桌面上的 Android 13+ 主题图标。
- **独立输出**：不修改源目录，不覆盖已有输出；支持前台通知显示后台处理进度。

### 下载与使用

从 [Releases](https://github.com/Hycnyl/EhBridge/releases) 下载 `EhBridge-1.1.1-universal.apk`。最低 Android 8.0，一个 APK 覆盖 arm64-v8a、armeabi-v7a、x86、x86_64，无需选择架构。应用 ID：`com.hycnyl.ehbridge`。

1. 通过系统文件选择器，从同一存储提供程序选择两个独立的输入、输出根目录。
2. 选择目标格式「散图」或「CBZ」，点击「扫描」。
3. 查看处理计划和提示，点击「开始处理」。筛选只影响列表显示，批处理使用完整扫描计划。

已有同名输出会跳过。取消后保留已完成的画廊，重新扫描即可继续整理。

右上角菜单可切换语言和外观、设置提示、导出报告；「关于」包含软件信息、作者与项目链接，以及分节使用指南。

### 目录规则

每个画廊是输入根目录下的一个直属文件夹，命名为 `正整数GID-标题`。两种格式示例：

```text
A：散图 / Loose                 B：CBZ
10001-title/                   10001-title/
├── 00000001.jpg               ├── 10001.cbz
├── 00000003.png               ├── ComicInfo.xml  [可选 / optional]
├── .ehviewer      [可选]       └── thumb.webp     [可选 / optional]
├── ComicInfo.xml  [可选]
└── thumb.webp     [可选]

10001.cbz：
├── 00000001.jpg
├── 00000003.png
└── ComicInfo.xml  [可选 / optional]
```

页面文件名由至少 8 位数字和非空扩展名组成，页号大于 0，允许缺页和不同扩展名。CBZ 名称与 GID 一致，页面平铺在 ZIP 根目录；封面可命名为 `thumb` 或 `thumb.*`。

输出优先保留已有 XML；缺少外部 XML 时，从 CBZ 内恢复，或根据文件夹标题、GID 和页号生成。CBZ 解包为散图时生成 CBOR `.ehviewer`；散图同格式复制保留原有状态，不补造缺失的状态文件。

### 从源码构建

准备 JDK 21 和 Android SDK 36，通过 `ANDROID_HOME` 或 `local.properties` 设置 SDK 路径。项目自带 Gradle Wrapper。

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

Windows 使用 `gradlew.bat`。本地安装测试可使用 debug 包；release 签名通过本机 `signing.properties` 配置。

## English

### Features

- **Bidirectional conversion:** pack loose pages into CBZ or extract CBZ into loose files. Matching layouts are copied. Pages are neither decoded nor transcoded.
- **Optional metadata:** loose-gallery `.ehviewer`, external `ComicInfo.xml`, covers and inner CBZ XML are optional. Missing state or external sidecar files produce nonblocking warnings; output XML is recovered or generated automatically.
- **Batch management:** scan previews, All / Loose / CBZ / Invalid filters, a draggable fast-scroll thumb, progress and cancellation.
- **Warnings and reports:** per-type warning switches, gallery details and complete text report export.
- **Native interface:** Material 3, light/dark/system themes, dynamic colors on Android 12+, two columns on wide screens and themed icons on supported Android 13+ launchers.
- **Separate output:** sources remain read-only and existing output is never overwritten. Foreground notifications show background progress.

### Download and use

Download `EhBridge-1.1.1-universal.apk` from [Releases](https://github.com/Hycnyl/EhBridge/releases). Android 8.0 or later is required. One APK covers arm64-v8a, armeabi-v7a, x86 and x86_64, with no architecture selection. Application ID: `com.hycnyl.ehbridge`.

1. Choose separate input and output roots through the system picker, using the same storage provider.
2. Select Loose or CBZ as the target layout, then scan.
3. Review the plan and warnings, then start. Filters only affect the list; processing uses the full scan plan.

Existing output names are skipped. Completed galleries remain after cancellation; rescan to continue organizing.

The overflow menu provides language, appearance, warning settings and report export. About contains software information, author and project links, and a structured usage guide.

### Gallery formats

Each gallery is a direct child of the input root, named `positive-GID-title`. The examples above show both layouts; entries marked `[可选]` are optional. Page filenames use at least eight digits followed by a nonempty extension, with a positive page number. Gaps and different extensions are allowed. CBZ archives are named after the GID and contain flat page entries at the ZIP root. Covers may be named `thumb` or `thumb.*`.

Existing XML is preserved. Missing external XML is restored from the CBZ or generated from the folder title, GID and page indices. Extracting CBZ creates CBOR `.ehviewer` state; same-layout loose copies preserve existing state without fabricating a missing file.

### Build

Use JDK 21 and Android SDK 36. Configure `ANDROID_HOME` or `local.properties`, then run the Gradle Wrapper commands above. On Windows, use `gradlew.bat`. Debug builds can be installed for local testing; configure release signing through your local `signing.properties`.

## 作者与协议 / Author and license

[Hycnyl](https://github.com/Hycnyl) · [Hycnyl/EhBridge](https://github.com/Hycnyl/EhBridge)

Copyright (C) 2026 Hycnyl. **GNU GPL v3.0 only（GPL-3.0-only）** — [LICENSE](LICENSE)。第三方许可 / Third-party notices: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
