# EhBridge 1.1.0 验收记录

- 版本：1.1.0，versionCode 2。
- 包名／namespace：`com.hycnyl.ehbridge`。
- 最低 Android 8.0（API 26），目标 API 36。
- 单一签名 universal APK，包含 arm64-v8a、armeabi-v7a、x86、x86_64；无 ABI 拆包。
- 英文与简体中文应用文案完整，沿用系统语言与应用内语言切换。

## 备份

所有修改之前，已完整备份 1.0.0 工程（含私有签名配置）和原交付物，并校验备份内容。完整备份：`/workspace/backups/EhBridge-1.0.0-20261023-090751.tar.gz`。原 1.0.0 APK 和源码 ZIP 仍保留在 deliverables。新的 AGENTS.md 记录了用户的长期要求：以后每轮修改之前也先备份。

## CBZ 问题与修复

用户样本的外层 ZIP 解开后，是一个 `GID-标题` 文件夹，其内含 `GID.cbz`、外部 ComicInfo.xml 和 thumb.webp。CBZ 平铺 43 个八位数字命名的 webp 页面与一个 ComicInfo.xml；全部为 STORED，带 data-descriptor 标志，外部／内部 XML 相同。不存在路径穿越、嵌套页面、重复条目或异常压缩比。这是合法 B 结构。

旧实现仅用 lseek 判断是否能直接读取，然后将 SAF 描述符路径 `/proc/self/fd/N` 交给 ZipFile 重新打开。描述符授权不代表拥有重新打开路径的权限；重开失败又被扫描器统一标成 ARCHIVE_UNSAFE，掩盖了真实读写原因。

1.1.0 保留直接访问优化，但重开被拒绝时，通过原已授权描述符读取到应用私有缓存，再用 ZipFile 读取中央目录；操作结束／失败／取消会关闭资源并清理副本。扫描只将 ZIP 结构或 ZIP 解析错误报告为 ARCHIVE_UNSAFE，普通提供器读写错误报告为 READ_WRITE_FAILURE。路径、重复条目、加密、压缩方式、大小和 CRC 检查继续生效。

## 元数据与命名

外部 ComicInfo、缩略图和内部 ComicInfo 均非必备。缺失产生非阻断提示，输出 XML 按外部原文、内部原文、文件夹信息生成的顺序选择。不会修改源文件，不会为缺失缩略图生成假图，不会编造 token。同格式复制保留既有文件并补齐缺失外部 XML。

EhViewer 命名依据来自用户指定仓库的实际源码：`GID-选定标题`，标题按偏好取普通或日文，非法 FAT 字符替换为 `_`，去除末尾空格／点，超过 255 UTF-8 字节时从中间截断为 `...`，数据库保存的旧目录名优先复用。因此修复保留当前文件夹中 GID 后的标题，不猜测已被替换或截断的字符。

研究来源：UjuiUjuMandan/EhViewer 包装仓库指向 codeberg.org/EhViewer/EhViewer，固定提交 `257bfe843ee4345420b4f01cb26001f0ff3b0761`；SpiderDen.kt 的 downloadDirname、FileUtils.kt 的 sanitizeFilename、client/EhUtils.kt 的 getSuitableTitle。

## 最小验证范围

1. 原有五项快速核心回归：页面名称／字节／XML 保留，CBOR 与 token 回退，同格式完整复制，混合目录和错误分类，ZIP 安全与输出冲突／失败／取消隔离。缺 XML 的旧断言已更新为有效项与警告。
2. 上传真实样本：在两种扫描目标下分别识别为 COPY 和 UNPACK；B→A→B 往返全部 43 页字节／CRC／大小一致，XML 一致，源 CBZ 哈希不变。
3. 可选元数据：缺外部 XML／封面时从内部 XML 修复；内外 XML 与封面全部缺失时从目录生成正确转义的 XML，生成 null-token SpiderInfo，散图也可再次打包；同格式复制补侧车且 CBZ 字节不变。

上述共 7 个 JVM 测试，0 失败、0 错误，运行时间合计约 0.9 秒。结果见 `verification/v1.1.0/core-results.json`。

Android 描述符权限回归的最终结果见下节。未重复 1.0.0 的 200 画廊压力测试，未将旧版本压力指标冒充本版测试结果。此次不查看、不解码样本图片；仅解析目录、ZIP、XML、CBOR 以及流式复制／校验原始字节。

## 界面与发布校验

“全部／散图／CBZ／无效”采用可点击筛选，扫描默认全部；筛选仅控制显示，不改变批处理计划。快速滑动显示可拖动的 Material 3 滑块，普通慢速滚动不触发，空闲约 1.2 秒淡出。列表与滑块分离，滚动位置在布局阶段读取，避免列表内容逐像素重组。

右上角非阻断提示打开逐类型开关对话框，独立持久化，列表和详情均按设置显示，错误不隐藏，报告保留所有提示。新增 XML 缺失、封面缺失、元数据修复三个提示类型。

自适应图标含标准背景／前景及 Android 13+ monochrome 层；普通图标回退为真正的 mipmap vector。支持主题图标的桌面负责按系统动态颜色着色。本环境未进行 Android 13 桌面视觉验证，也未扩大为长列表性能压力测试。

最终 assembleRelease 和 lintRelease 成功，lint 0 错误；其余提示为 SDK 属性兼容提示、依赖版本更新建议和 KTX 写法建议等。APK 签名有效，证书与 1.0.0 相同；包名、版本、四 ABI、ZIP 16 KiB 对齐及所有 native ELF LOAD 段至少 16 KiB 对齐已核对。证据在 `verification/v1.1.0/`。

包名改变后，Android 将本版视为新应用，可与旧版并存；目录授权和偏好需要重新选择。签名备份为私有文件，公开源码包排除密钥、签名密码、用户原始元数据 JSON 和上传样本。

## Android 权限回归结果

API 30 上使用临时调试提供器，先打开样本 CBZ 的只读文件描述符，再撤去其文件路径读取权限，准确构造“描述符已授权且 lseek 可用，但路径无法重新打开”的条件。旧方式稳定触发 FileNotFoundException；新 SafStore 在 COPY／UNPACK 两种扫描目标下均接受样本，完成 43 页提取、CRC 与 CBOR SpiderInfo 校验，结束后无 ZIP 缓存残留。1 个原生测试通过，耗时 8.501 秒。证据见 android-descriptor-regression.txt。该项验证的是权限回退条件，没有将其描述为目标用户手机或所有第三方提供器实测。

本轮临时测试类、提供器权限模拟钩子、解压样本和设备测试数据在验证后清除；原有维护用回归测试保留。没有改变已签名发布包；临时提供器从未包含在 release APK 中。

签名 release 包已在 API 30 安装并冷启动成功；实际菜单可打开含 10 个独立开关的提示对话框，关闭其中一个仅持久化对应提示类型，其余 9 个维持开启，随后恢复默认设置。
