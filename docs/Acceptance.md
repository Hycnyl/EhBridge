# EhBridge 1.0.0 验收记录

日期：2026-10-23（UTC）。交付为独立、签名的 universal APK，支持简体中文与 English，minSdk 26 / targetSdk 36。

## 交付范围

已实现文档两种格式的识别、混合一级目录扫描、同格式递归原字节复制、A→B 流式 ZIP、B→A 最小真实 CBOR SpiderInfo、缺页宽容处理、逐项错误与警告、取消、暂存提交与清理、输出冲突跳过、SAF 持久授权、Material 3 自适应界面、前台进度与熄屏处理、语言/主题切换及报告导出。没有第三种输出格式、网络访问权限、Root、全盘存储权限或 EhViewer 数据库操作。

为保证输入输出互不包含，两个目录须从同一 SAF 提供程序选择。不同 provider 可能别名指向相同本机位置，因此不能证明关系时拒绝执行；本机 Documents/home 别名会归一化检查。输出文件创建依赖 SAF 的新文档语义并核对返回名称，不接受提供程序偷偷追加后缀或改名。创建私有暂存子项时不逐页重新枚举增长中的目录。

## 构建与六组验收

| 分组 | 结果 | 实际验证 |
|---|---|---|
| T1 缺页 A→B | 通过 | 第 3/8 页、同页号不同文件名、零字节页、任意后缀（包含非路径用途的冒号）、任意封面后缀；只写现有页与 XML，内外 XML 原字节相同，源不变 |
| T2 B→A SpiderInfo | 通过 | 有 token、无 token、非法 XML、内置 XML 回退、冲突外部优先、错误 URL GID、DTD/外部实体禁用、PageCount 与最大页号取大、空 Int 键 Map、真实 CBOR 编解码、安全页索引上限 |
| T3 同格式复制 | 通过 | A→A/B→B 的原始字节、CBZ、opaque .ehviewer、无效 XML、系统杂项、额外文件、嵌套与空目录均保留 |
| T4 混合和非法项 | 通过 | A/B 混合自动选择操作、缺 XML、未知有内容文件、双主结构、多封面、GID 不符、极端页号、错误目录名、根级文件、已有输出；同名输入的结果按文档身份单独记录，后一项跳过 |
| T5 安全和隔离 | 通过 | ZIP 穿越/嵌套/Windows 驱动路径、同名 ZIP 条目、加密 ZIP、输出冲突、写入失败后继续其他画廊、取消与暂存清理、根目录包含关系、无 rename provider 的标识式提交 |
| T6 Android 界面与批处理 | 通过 | 420dp 手机、740dp 中宽、1280dp 宽窗口双栏（无障碍节点位置验证）、中英切换、200% 字体可滚动到操作、运行中 Activity 重建/窗口变化保持状态且只启动一个任务、帮助操作可用、熄屏处理、200 画廊/20000 页、SAF 回转与持久化 CBOR |

T1–T5 为 JUnit JVM 合成文件测试，证据 `verification/core-tests.xml`。T6 使用 Android 平台 ActivityScenario/UiAutomation 与 debug-only DocumentsProvider，实际通过 ContentResolver 和 java.util.zip 读写；没有替换生产 Compose 调度器。完整测试结果为 `OK (1 test)`，见 `verification/android-T6.txt`。最后的存储修订另运行同一 T6 的 focused 路径，检查目录别名/交叉 provider 拒绝、特殊后缀、Android SAX URL/页数提取、真实 CBOR、递归复制与已有输出不覆盖，见 `verification/android-storage-final.txt`。

发布构建启用 R8 和资源压缩。最终 `testDebugUnitTest`、`lintRelease`、`assembleRelease` 通过；lint 没有错误，剩余提示为兼容属性、固定版本更新提醒及 Kotlin 风格建议，未用 baseline 隐藏错误。

## 实测批处理结果

环境：Android 11 / API 30，x86_64 软件 TCG 模拟器，无 KVM，2 vCPU、2048 MB guest RAM。所有输入是假图片字节，不进行图片解码。

| 项目 | 观测值 |
|---|---:|
| 输入画廊 | 200 |
| 实际数字页文件 | 20000 |
| 另一个无效根级文件 | 1 |
| 扫描 | 146.042 秒 |
| 批处理（含执行中界面操作） | 640.598 秒 |
| 转换成功 / 复制成功 | 200 / 0 |
| 无效跳过 / 执行失败 | 1 / 0 |
| 每个 CBZ 条目 | 100 页 + ComicInfo.xml |
| 剩余临时目录/未完成标识 | 0 |
| 任务启动次数 | 1 |
| 任务前 PSS | 184921 KiB，约 180.6 MiB |
| 运行中 PSS 样本 | 193111 KiB，约 188.6 MiB |
| 完成后 PSS | 192378 KiB，约 187.9 MiB |

原始记录在 `verification/test-evidence/performance.json`。PSS 是三个时间点的采样，不是峰值。执行后检查 200 份 CBZ 目录与内外 XML 一致性、源页文件数量、残留目录，并通过 SAF 解包一个 100 页画廊，检查 .ehviewer 与恢复页字节。整个 T6 用时 979.294 秒，包含界面、配置、扫描及回转检查。

该结果证明此模拟器上的稳定性和功能行为，不代表实体手机、SD 卡或真实大图的吞吐速度，不设置没有设备依据的速度门槛。测试开发中曾调整 AVD 像素宽度/密度限制、系统 UI 无响应和测试框架时钟/重建竞争；上述记录取最终通过的实际运行。

## APK 检查

- 一个 APK 包含 arm64-v8a、armeabi-v7a、x86、x86_64；没有 ABI 拆分。
- 已卸载 debug 包并安装实际交付的 release 签名 APK，在 API 30 模拟器冷启动成功（约 5 秒，软件 TCG）；并通过实际界面从 English 切换到简体中文；见 `verification/release-install.txt`、`release-ui-zh.xml` 与 release 启动截图。
- APK Signature Scheme v2 验证通过，使用独立 RSA 3072 位 release 密钥；适用于 minSdk 26。
- ZIP 原生库 16 KiB 对齐通过，arm64-v8a/x86_64 ELF LOAD 段对齐为 0x4000。
- Manifest 无 INTERNET、MANAGE_EXTERNAL_STORAGE、debuggable 或 FixtureDocumentsProvider；前台服务、通知及唤醒锁权限按功能声明。
- 校验记录为 `verification/apk-signature.txt`、`apk-alignment.txt`、`apk-manifest.txt`、`apk-badging.txt`、`elf-*-alignment.txt`。交付 SHA-256 位于包旁的 `SHA256SUMS.txt`。

## 可执行命令

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class io.github.ehbridge.T6AdaptiveAndBatchTest \
  io.github.ehbridge.test/androidx.test.runner.AndroidJUnitRunner
# 同一 T6 的最终存储小样本复核：先有完整测试的 fixtures/input，再运行
adb shell am instrument -w -r -e verifyOnly true \
  -e class io.github.ehbridge.T6AdaptiveAndBatchTest \
  io.github.ehbridge.test/androidx.test.runner.AndroidJUnitRunner
adb exec-out run-as io.github.ehbridge tar -C files -cf - test-evidence > test-evidence.tar
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
zipalign -c -P 16 -v 4 app/build/outputs/apk/release/app-release.apk
```

每个阶段都有实际代码与可执行命令：阶段 I 对应扫描、双目录与窗口检查；阶段 II 对应双向转换、复制及 T1–T5/T6 SAF 回转；阶段 III 对应上述 200 画廊实测及交付构建。

## 尚未验证的边界

用户只提供了解码 JSON，没有目标 EhViewer 实际生成的二进制 `.ehviewer` 和实体设备。因此目标分支的金样本 decode→encode→decode、在目标 EhViewer 中恢复下载列表/续传，以及实体手机和平板上的确认仍未完成。已完成本工具的 schema/真实 CBOR、自身解码、Android SAX/SAF 和文件转换验证，不能把这些等同于目标 App 下载项恢复成功。

缺 token 时输出 null 和有效 CBOR，离线页文件仍正常恢复；原阅读位置和逐页 token 不恢复。四种 ABI 均被打包，本次运行验证的是 x86_64 Android 模拟器，并未声称分别在四种 CPU 实机上运行过。
