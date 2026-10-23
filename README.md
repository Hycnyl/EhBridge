# EhBridge 1.0.0

[简体中文](#简体中文) · [English](#english)

> **历史存档：1.0.0 存在严重的 CBZ 识别缺陷，不能作为正常可用版本使用。** 本提交保留最初版本的源码与缺陷，供版本追溯；请在后续修复版本发布后使用修复版本。
>
> **Historical archive: version 1.0.0 has a critical CBZ detection bug and is unsuitable for normal use.** This snapshot preserves the original implementation, including its defects. Use a later fixed release when available.

## 简体中文

### 项目简介

EhBridge 是 Android 上离线运行的 EhViewer 画廊文件夹批量转换工具，设计用于在「文件夹＋散图」和「文件夹＋CBZ」之间转换。同格式任务按原样复制。应用支持简体中文和 English、Material 3、Android 12 及以上动态取色、深浅主题与宽屏双栏布局。

- 作者：[Hycnyl](https://github.com/Hycnyl)
- 项目：[Hycnyl/EhBridge](https://github.com/Hycnyl/EhBridge)
- 本快照：`1.0.0`，`versionCode = 1`，原始包名 `io.github.ehbridge`
- 系统要求：Android 8.0 / API 26 及以上；发布 APK 为 universal，不按 CPU 架构拆分。
- 安装包由维护者手动发布至 [Releases](https://github.com/Hycnyl/EhBridge/releases)。本仓库提交的是源码，不包含 APK 或签名材料。

### 1.0.0 的严重缺陷

真实设备上的合法 CBZ 画廊可能在扫描时被错误判定为 `ARCHIVE_UNSAFE`，影响涉及 CBZ 的识别和处理。原实现把 SAF 文件描述符可定位误当成可以通过 `/proc/self/fd/…` 重新打开；部分 Android 存储提供程序允许读取描述符，却不允许按该路径重新打开。读取失败随后被归为「ZIP 不可读取或不安全」。

因此，出现该提示并不能证明原画廊或 CBZ 已损坏。本历史快照没有修复这个问题。保留的合成测试结果也不能证明实机兼容性或实际可用性。

### 原始版本的格式要求

以下是 **1.0.0 实现采用的规则**，后续版本可能调整。每个画廊是输入根目录下的一个直属文件夹，名称为 `正整数GID-标题`。

```text
A：文件夹＋散图                 B：文件夹＋CBZ
10001-title/                   10001-title/
├── .ehviewer                  ├── 10001.cbz
├── ComicInfo.xml              ├── ComicInfo.xml
├── thumb.webp                 └── thumb.webp
├── 00000001.jpg
└── 00000003.png

10001.cbz 的 ZIP 根目录：
├── ComicInfo.xml
├── 00000001.jpg
└── 00000003.png
```

1.0.0 要求外部 `ComicInfo.xml` 和封面存在；封面名称为 `thumb` 或 `thumb.*`，不限定 WebP。散图还要求 `.ehviewer`；CBZ 还要求内部根级 `ComicInfo.xml`。页文件名由至少 8 位数字和非空扩展名组成，页号大于 0；允许缺页，不解码或转码图片。

设计行为：A→B 打包页面和 XML，丢弃原 `.ehviewer`；B→A 解包页面并生成 CBOR 格式的 SpiderInfo；A→A / B→B 递归复制。生成 `.ehviewer` 不保证能够恢复目标 EhViewer 的下载列表、页 token 或阅读进度。

### 使用流程与数据边界

原始操作流程为：选择输入目录 → 选择独立输出目录 → 选择目标格式 → 扫描并查看计划 → 开始处理。右上角菜单提供语言、外观、非阻断提示、报告导出及帮助选项。鉴于上述缺陷，不建议用 1.0.0 执行实际转换。

应用通过 Android 存储访问框架（SAF）取得用户授权。输入输出目录须在同一存储提供程序下，且不能相同或互相包含。输入仅供读取；已有同名输出跳过，不覆盖或合并。批处理支持进度、取消和前台通知；中断后需重新扫描。应用本身不申请网络、Root 或全盘文件管理权限，构建时仍需联网下载依赖。

仓库不包含真实画廊、用户提供的 `.ehviewer` 数据、签名私钥或签名密码。`docs/SpiderInfo-structure-example.json` 是合成的字段示例，不是可直接使用的二进制 `.ehviewer`。强制停止或存储断开后可能残留临时输出目录，SAF 不提供跨提供程序的事务保证。

### 从源码构建

准备 JDK 21、Android SDK 36、Build Tools 35.0.0 和 Platform Tools。项目使用 Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21、Compose BOM 2025.10.01，JVM 字节码目标为 17。通过 `ANDROID_HOME` 或本机 `local.properties` 指定 SDK 路径。

```sh
sdkmanager 'platforms;android-36' 'build-tools;35.0.0' 'platform-tools'
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Windows 使用 `gradlew.bat`。如需 release 构建，运行 `./gradlew :app:assembleRelease`；未配置私有签名时会生成未签名 APK。自行签名可在被 Git 忽略的 `signing.properties` 中配置 `storeFile`、`storePassword`、`keyAlias` 和 `keyPassword`，密钥文件路径相对于项目根目录。请使用自己的密钥，不要提交签名材料。

原有仪器测试 `T6AdaptiveAndBatchTest` 会修改显示配置并生成约 200 个合成画廊、20,000 个假页文件，仅适合专用测试设备或模拟器。本次历史发布没有重新运行 Android 构建或仪器测试。

原始需求保留于 [Development-Spec.md](docs/Development-Spec.md)，当时的测试记录保留于 [Acceptance.md](docs/Acceptance.md) 和 `docs/verification/`。它们是历史资料，不能替代上述已知缺陷说明。

### 开源协议与致谢

Copyright (C) 2026 Hycnyl。EhBridge 原创代码按 **GNU General Public License v3.0 only（SPDX：GPL-3.0-only）** 发布，完整协议见 [LICENSE](LICENSE)。软件按协议不提供担保。随附的第三方组件保留各自许可，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

感谢 [EhViewer](https://github.com/UjuiUjuMandan/EhViewer) 及相关分支提供的格式参考，以及 AndroidX、Jetpack Compose、Kotlin、kotlinx 和 Gradle。本项目是独立工具，不代表 EhViewer 官方。

## English

### Overview

EhBridge is an offline Android batch converter for EhViewer gallery folders. It is designed to convert between loose-image folders and folders containing a CBZ archive. Matching layouts are copied without conversion. It supports Simplified Chinese and English, Material 3, dynamic colors on Android 12+, light/dark themes and a two-column layout on wide screens.

- Author: [Hycnyl](https://github.com/Hycnyl)
- Project: [Hycnyl/EhBridge](https://github.com/Hycnyl/EhBridge)
- This snapshot: `1.0.0`, `versionCode = 1`, original application ID `io.github.ehbridge`
- Requirements: Android 8.0 / API 26 or later. Release APKs are universal, with no ABI splits.
- The maintainer uploads APKs manually to [Releases](https://github.com/Hycnyl/EhBridge/releases). This source repository contains no APKs or signing material.

### Critical defect in 1.0.0

Valid CBZ galleries on real Android devices may be incorrectly classified as `ARCHIVE_UNSAFE`, preventing CBZ detection and processing. The original implementation assumes a seekable SAF file descriptor can also be reopened through `/proc/self/fd/…`. Some Android storage providers permit descriptor access but deny reopening that path. The resulting read failure is then reported as an unreadable or unsafe ZIP.

That message does not establish that the gallery or CBZ is corrupt. This historical snapshot deliberately retains the defect. Its archived synthetic test results do not establish real-device compatibility or usability.

### Original format requirements

The directory example in the Chinese section shows the rules **implemented in 1.0.0**; later versions may change them. Each gallery must be a direct child of the input root, named `positive-GID-title`.

Both layouts require an external `ComicInfo.xml` and one cover named `thumb` or `thumb.*`; WebP is not mandatory. Loose-image layout A also requires `.ehviewer`. Layout B contains `GID.cbz`, whose ZIP root must contain `ComicInfo.xml` and numbered page files. Page filenames use at least eight digits followed by a nonempty extension, with a positive page number. Missing pages are allowed; images are neither decoded nor transcoded.

Intended behavior: A→B packs pages and XML while discarding the original `.ehviewer`; B→A extracts pages and generates CBOR SpiderInfo; A→A and B→B copy recursively. A generated `.ehviewer` does not guarantee restoration of download lists, page tokens or reading progress in the target EhViewer application.

### Workflow and data handling

The original workflow is: select input → select a separate output → choose the target layout → scan and review → start. The overflow menu provides language, appearance, nonblocking warnings, report export and help. Because of the critical defect, 1.0.0 is not recommended for actual conversion.

The app uses Android's Storage Access Framework (SAF). Input and output must belong to the same provider and must neither coincide nor contain one another. Input is read-only; existing output names are skipped without overwriting or merging. Batch operations support progress, cancellation and foreground notifications. Interrupted work must be scanned again. The app requests no network, Root or all-files access; building still requires downloading dependencies.

No real galleries, user-supplied `.ehviewer` data, signing keys or signing passwords are included. `docs/SpiderInfo-structure-example.json` is a synthetic schema example, not a usable binary `.ehviewer`. Forced termination or disconnected storage can leave temporary output directories; SAF does not provide cross-provider transactions.

### Building and checking

Use JDK 21, Android SDK 36, Build Tools 35.0.0 and Platform Tools. Versions are pinned to Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21 and Compose BOM 2025.10.01; the JVM bytecode target is 17. Set `ANDROID_HOME` or configure your SDK path in a local `local.properties`.

```sh
sdkmanager 'platforms;android-36' 'build-tools;35.0.0' 'platform-tools'
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Use `gradlew.bat` on Windows. `./gradlew :app:assembleRelease` creates an unsigned APK unless private signing is configured. To sign your own build, provide `storeFile`, `storePassword`, `keyAlias` and `keyPassword` in the Git-ignored `signing.properties`; the key path is relative to the project root. Use your own key and keep all signing material private.

The original `T6AdaptiveAndBatchTest` instrumentation test changes display settings and creates approximately 200 synthetic galleries and 20,000 fake page files. Run it only on a dedicated test device or emulator. Android builds and instrumentation tests were not rerun for this historical publication.

The original specification is in [Development-Spec.md](docs/Development-Spec.md). Original test records remain in [Acceptance.md](docs/Acceptance.md) and `docs/verification/`. These are historical records and do not override the known-defect notice above.

### License and acknowledgments

Copyright (C) 2026 Hycnyl. Original EhBridge code is licensed under the **GNU General Public License v3.0 only (SPDX: GPL-3.0-only)**; see [LICENSE](LICENSE). The software comes without warranty under that license. Bundled third-party components retain their own licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Thanks to [EhViewer](https://github.com/UjuiUjuMandan/EhViewer) and related forks for format references, and to AndroidX, Jetpack Compose, Kotlin, kotlinx and Gradle. EhBridge is an independent tool and is not an official EhViewer project.
