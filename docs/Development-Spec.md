# EhBridge — Android EhViewer 画廊目录双向批量转换器

**产品与开发规格书 1.0**  
**适用对象：Codex / Android 开发工程师**  
**日期：2026-10-23**  
**交付目标：可编译、可运行的独立 Android App；一套确定的两格式转换规则。**

## 1. 产品目标与边界

EhBridge 是**离线、本地、批处理**的小工具。它以两个 SAF 文件夹作为输入根目录和输出根目录，将输入根目录下的**一级画廊子文件夹**逐一识别，再统一输出为用户选定的 EhViewer 目录组织形式。

用户的主要步骤：

1. 选择**输入目录**（可以同时包含散图画廊和 CBZ 画廊）。
2. 选择**输出目录**（与输入目录分离）。
3. 选择**目标格式**：「A｜文件夹＋散图」或「B｜文件夹＋CBZ」。
4. 点击「扫描」，预览每个子项将被转换、复制或跳过，以及跳过原因。
5. 点击「开始」，逐画廊执行并展示完成数量、失败原因及进度。

**固定原则**：

- 仅处理 §2 定义的 A 和 B 两种格式。输出始终保持**一画廊一文件夹**。
- **已是目标格式：直接复制整个画廊文件夹**，不重新压缩、不重建元数据。
- **是另一种格式：转换**，原始页图字节、页图文件名、外部 `ComicInfo.xml`、封面文件名及字节均保留。
- **不属于 A 或 B：不转换、不复制**；明确显示哪个文件或结构不符合要求。
- **缺页允许**，不联网补图、不依赖 EhViewer 的账号或数据库，不因预期页数与实际图片数不同而报错。
- **只读输入、输出不覆盖**；单个画廊出错不影响其他画廊。
- **手机与平板均可使用**，尤其适配大屏横屏、分屏和可变窗口；Material You 视觉风格。

软件默认不访问网络；不申请 Root、`MANAGE_EXTERNAL_STORAGE`，不直接修改 EhViewer 数据库。

## 2. 唯一支持的两种目录格式

### 2.1 A：文件夹＋散图

```text
选定根目录/
└── 1234567-画廊标题/
    ├── .ehviewer
    ├── 00000001.jpg
    ├── 00000002.png
    ├── 00000003.webp
    ├── thumb.webp
    └── ComicInfo.xml
```

**最低判定条件**：

- 一级文件夹名可解析为 `<正整数 GID>-<非空标题>`；名称原样使用，不重命名标题。
- 含普通文件 `.ehviewer` 和 `ComicInfo.xml`。
- 含且仅含一个封面文件：`thumb` 或 `thumb.<任意非空后缀>`。**不检查后缀是否合法，不检查 MIME、不对封面解码**；匹配文件基本名 `thumb` 时可忽略大小写，输出保留原始拼写。
- 至少有一份数字页图：文件名 `<至少8位十进制数字>.<非空后缀>`，页号大于 0。例如 `00000001.jpg`。页图后缀**不采用扩展名白名单**，不检查真实图片格式、不解码。
- 只要求必要文件能被打开读取，**不读取或验证 `.ehviewer` 的内容**，不要求 XML 能成功解析，也不要求图片文件非零字节。

**不阻断**：页号跳跃、从较大页号开始、只保留一张图、ComicInfo 记录的总页数多于实际文件数、两个不同文件名表达相同页码（如 `00000001.jpg` 和 `00000001.png`）。同页号在输出中按文件名稳定排序、都保留，不擅自去重或覆盖。

**可能导致跳过**：缺少以上必备组成项；出现另一个作为主要存档的 `<GID>.cbz`；存在无法归入页图、封面、XML、`.ehviewer` 或已知系统杂项的其他**有内容的文件**或子目录，因转换时无法保证这些未知数据有合适去处。需要报出具体路径，不能悄悄丢弃。

### 2.2 B：文件夹＋CBZ

```text
选定根目录/
└── 1234567-画廊标题/
    ├── 1234567.cbz
    ├── thumb.webp
    └── ComicInfo.xml

1234567.cbz（ZIP 格式；内部根目录）
├── 00000001.jpg
├── 00000002.png
├── 00000003.webp
└── ComicInfo.xml
```

**最低判定条件**：

- 一级画廊文件夹名遵循 A 的规则。
- 外部有普通文件 `<GID>.cbz`（输入识别时 `.cbz` 扩展名大小写可兼容，但 GID 必须与文件夹 GID 相同）、`ComicInfo.xml`、唯一一份 `thumb`/`thumb.<任意后缀>`。
- CBZ 是可读取的标准 ZIP，**内部根级**包含 `ComicInfo.xml` 和至少一份数字页图。内部页图沿用 A 的宽容命名与缺页规则。
- 内外 `ComicInfo.xml` **允许内容不同**：这是非阻断提示。B→A 使用**外部 XML**作为输出 XML，内部 XML 只用于存在性及可能的元数据提取回退。
- ZIP 内不解码图片、不因实际页数少于元数据预期页数拒绝处理；根级空目录条目及常见无内容系统杂项允许忽略。未知的有内容条目、嵌套页图、重复的**同名** ZIP 条目等有数据丢失或覆盖歧义的情形，报告具体条目并跳过该画廊。

**EhBridge 创建 B 的强制规则**：

- 外层业务文件**恰好**为 `<GID>.cbz`、原样封面、`ComicInfo.xml`。
- CBZ 内部根级仅写入实际存在的散图页文件和一份 `ComicInfo.xml`。
- **内部 `ComicInfo.xml` 必须与外部 `ComicInfo.xml` 字节一致**；二者均使用输入 A 的外部 XML 原始字节，不生成或改写 XML。
- CBZ 中不写入 `.ehviewer`、封面、其他附加元数据或子文件夹。
- 缺页不补齐，页文件名与内容字节不变。

### 2.3 两种格式的共同识别规则

- 画廊文件夹名中的第一个 `-` 用于分离 GID 与标题；允许 Unicode、空格、其他 `-` 进入标题。不得为了“美化”名称改变原目录名。
- 页号按**数值升序**排列；同一数值的不同页文件名再按文件名稳定排序。除自然数越界导致无法合理分配页索引的情况外，不限制页码是否连续。
- `thumb`/`thumb.*` 可接受任何后缀，包括与实际图像编码不匹配的后缀；保留原字节，不做图片分析。
- 已知系统杂项（如 `.nomedia`、`.DS_Store`、`Thumbs.db`）可在扫描时忽略；同格式复制时完整保留原目录内容，异格式转换时不输出这些杂项。
- **不因未知元数据字段、XML 格式细节、页数差异、图片内容或图片后缀拒绝转换。** 只检查能否安全确定目录归属、必要文件是否存在，以及是否存在无法安全映射的数据。

## 3. 批处理规则

### 3.1 决策表

| 输入一级子项 | 目标 A（散图） | 目标 B（CBZ） |
|---|---|---|
| 合规 A | **复制**整个文件夹 | **转换**成 B |
| 合规 B | **转换**成 A | **复制**整个文件夹 |
| 无法识别为 A 或 B | **跳过并说明原因** | **跳过并说明原因** |
| 输出目录已有相同文件夹名 | **跳过；不覆盖、不合并** | **跳过；不覆盖、不合并** |

扫描输入根目录时仅处理一级子项；不将任何单独放在根目录的 CBZ 视为一份画廊。根目录中的非系统普通文件也应显示为被忽略/跳过项并说明原因。

### 3.2 明确区分「合法格式」和「元数据恢复能力」

合法 B 的外部 `ComicInfo.xml` 即使缺少链接或不能解析，**仍是合法 B**，B→B 应原样复制。B→A 允许生成最小 SpiderInfo（§5），元数据缺失只产生**恢复能力受限**的警告，不将文件层面的转换判为失败。

同理，A 中 `.ehviewer` 即使是其他版本生成的内容，只要该文件存在且可复制，A→B / A→A 不为此执行 CBOR 解析或校验。

### 3.3 残缺画廊示例

```text
输入/
├── 10001-甲/           A；只有 00000003.jpg 和 00000008.webp
├── 10002-乙/           B；已包含规定的 CBZ 与内外 XML
├── 10003-丙/           A 候选；缺 ComicInfo.xml
└── README.txt           非画廊根级对象
```

目标 B：甲**成功转换**（ZIP 只有第 3、8 页及 XML），乙**整目录复制**，丙**跳过并报缺外部 XML**，`README.txt` 报一级项目不是画廊。不得凭空生成第 1、2、4–7 页。

## 4. A→B：流式归档

1. 从 A 枚举页图文件名与 URI；不解析图像，不检查连续性或与 `.ehviewer` 总页数是否相符。
2. 为该画廊在输出根目录创建独立的**临时任务文件夹**，不覆盖已有最终目录。
3. 将封面与外部 `ComicInfo.xml` 原样流式复制到临时文件夹。
4. 创建 `<GID>.cbz`，使用标准 `ZipOutputStream`，先写入根级 `ComicInfo.xml`，其内容**直接来自复制前的 XML 源文件字节**；再按页号数值顺序写入现有页图，ZipEntry 名为原始页文件名。即使一页缺失、重复页号使用不同文件名，也照实保留。
5. 使用固定大小缓冲区逐文件流式读写；建议 `Deflater.NO_COMPRESSION`（ZIP method 仍为 `DEFLATED`，不用先全量计算 CRC 与长度）；禁止图片转码和全图库二次哈希。
6. 完成输出流并检查无 I/O 异常；验证 CBZ 容器能被重新打开且包含预期的页文件名集合和根级 XML（**不再次完整读取所有图片进行 SHA-256 比对**）。
7. 提交该画廊输出文件夹；提交失败则记录并清理未完成文件夹，不删除输入。

A 中的 `.ehviewer` **不进入 B**。B 不能表达全部原生下载状态，属于所定义的格式转换语义。

## 5. B→A：以最小 SpiderInfo 宽容重建 `.ehviewer`

本节定义**必须实现的正式功能**，不是实验性选项。B→A 的目标是在没有网络、缺页、缺少 pTokenMap 时，仍最大限度保留解压的页图，并产出结构正确的 `.ehviewer`。

### 5.1 已掌握的数据结构

此方案制定时，用户提供了一份 **`.ehviewer` 反向解析成 JSON 的 SpiderInfo 样本**。它直接证实了下列实际字段与关系：

| 字段 | 样本情况 | 实现含义 |
|---|---|---|
| `gid` | 有，正整数 | 画廊 ID |
| `token` | 有，10 位字符 | 画廊级访问 token；与单页 token 不同 |
| `pages` | 有，36 | 总页数，可大于离线已下载的图片数 |
| `pTokenMap` | 有，键 `"0"`～`"35"` | 页索引**从 0 开始**；JSON 对象的键是字符串表示的索引 |
| `previewPerPage` | 有，20 | 预览分页参数；**不是所有画廊都必须为 20** |

**重要**：提供的是 JSON **解码结果**，不是 `.ehviewer` 的原始二进制字节；不能把 JSON 直接改名为 `.ehviewer`。用户 JSON 里没有出现的 `startPage`、`previewPages`、`upgradeFrom` 不能据此推论字段不存在。

在可读取的相近 EhViewer 分支源码中，SpiderInfo 的序列化模型为：

```kotlin
@Serializable
class SpiderInfo(
    val gid: Long,
    var token: String? = null,
    val pages: Int,
    val pTokenMap: MutableMap<Int, String> = hashMapOf(),
    var startPage: Int = 0,
    var previewPages: Int = -1,
    var previewPerPage: Int = -1,
    var upgradeFrom: Long? = null,
)
```

相近实现使用 `kotlinx.serialization.cbor.Cbor` 编码 `.ehviewer`；读取时支持此 CBOR 模型及一种历史文本表示。详见附录源码链接。**目标分支的精确编解码兼容性仍需用真实 `.ehviewer` 文件完成一次端到端核对**，但这项核对不应该成为大量离线转换任务的常规预检。

### 5.2 重建字段的唯一规则

| 字段 | B→A 的生成规则 | 缺失时行为 |
|---|---|---|
| `gid: Long` | 取画廊文件夹名前缀，并要求与 `<GID>.cbz` 名匹配 | 必备结构字段；不一致则跳过 |
| `token: String?` | **优先**从外部 ComicInfo 的画廊 URL 提取；必要时从内置 XML 的 URL 尝试补充；只有当 URL 的 GID 与文件夹 GID 相同时才接受 | 写入 **`null`**，不捏造；转换**继续成功**，附恢复能力受限警告 |
| `pages: Int` | `max(最大现存页号, XML 中可信的正整数 PageCount)`；缺失/异常的 XML 计数不使用 | 至少有一张页图时可由最大页号推出；**不使用实际文件数代替最大页号** |
| `pTokenMap` | 写入**空 Map**。B 不具备可靠的逐页 token，不能根据页号推导 | 完全允许为空；已有的本地页图不依赖每页 token 才能作为文件保存 |
| `startPage` | `0` | 不恢复原阅读位置 |
| `previewPages` | `-1` | 使用兼容默认值 |
| `previewPerPage` | `-1` | 使用兼容默认值；**不能从样本的 20 推定通用常量** |
| `upgradeFrom` | `null` | 不推测历史升级关联 |

**页号与页索引不同**：名为 `00000003.webp` 的第 3 页，对应 SpiderInfo 的**零基索引 2**。但本工具不掌握该图片真正的 pToken，因此不得把文件名填进 `pTokenMap`；它应为空。

为避免极端异常页号使 EhViewer 在创建 `IntArray(pages)` 时耗尽内存，`pages` 必须是 `1..100000` 范围内整数；超出上限时仅对该画廊报 `UNSAFE_PAGE_INDEX`。该阈值是资源安全界限，不检查下载是否完整；普通残缺下载完全不受影响。

### 5.3 token 的宽容提取策略

1. **首选外部** `ComicInfo.xml` 的 `<Web>`；也可以查看其中其他文本字段/属性是否出现 EhViewer 画廊链接。要求 URL 或等价可解析路径具备 `GID + 十位十六进制画廊 token`，GID 与文件夹匹配。可接受 `e-hentai.org`、`exhentai.org`，允许 http/https、尾部斜杠、转义后的 XML 文本及大小写不同的十六进制字母。
2. 外部 XML 没有取得 token 时，再从 CBZ **内部** `ComicInfo.xml` 按相同规则寻找；**不因此修改输出外部 XML 的内容**。
3. 当外部和内部 XML 给出的有效 token 不一致时，选择外部并发出 `TOKEN_SOURCE_CONFLICT` 非阻断警告。
4. XML 不可解析、没有 URL、网站已经无法访问或画廊被删除时，**不联网、不阻断解压**；将 `token` 写为 `null`，告知用户「离线散图已成功恢复；缺少画廊访问 token，EhViewer 可能无法恢复下载列表/联网续传」。
5. 不从 GID、作品标题、封面、图片哈希、没有明确来源的任意十位字符串**猜测**访问 token，不制造伪 token。

XML 解析使用 Android 标准解析器即可；必须关闭外部实体与 DTD 等危险功能（防 XXE）。读取 XML 失败只影响**元数据增强**，不影响必要文件的拷贝；如果 XML 内容不可解析，保留其原始字节，并使用最小 SpiderInfo 继续。

### 5.4 编码与可用性定义

- 实现一个小型 `SpiderInfoCodec.kt`：使用 `kotlinx.serialization` 和 CBOR，数据类**字段名、数据类型与默认值**应与上列结构严格保持一致；`pTokenMap` 的 CBOR 键使用**整数类型**（即 Kotlin `Int`），不能把 JSON 的 `"0"` 键当作 CBOR 字符串键。
- 对写出的 `.ehviewer` **立刻用本 App 同一 codec 反序列化一次**，检查 `gid`、`pages`、`token`、`pTokenMap` 可读。这是单文件的轻量元数据校验，不扫描图片内容。
- 样本 JSON 只验证了**字段语义**。开发验收时拿一份目标 EhViewer 实际生成的**二进制 `.ehviewer`**，做**一次** `decode→encode→decode` 对照及目标 App 实机识别测试。确认 codec 后，批量处理时不再进行额外版本探测或每次与 APK 交互。
- 有 token 时生成 `SpiderInfo(gid, token, pages, emptyMap(), 0, -1, -1, null)`；没有 token 时生成相同对象，但 `token=null`，**都生成真实 CBOR `.ehviewer`**，绝不写空文件、JSON 或假 token。
- **“转换成功”**只表示输出文件夹符合 A 的结构、已解压的页图/封面/XML 可读取、生成的 SpiderInfo 能按协议解码。**“EhViewer 下载列表恢复成功”**是另一层能力：没有有效 token 时可能不可用，且原阅读位置、pTokenMap 无法恢复。EhBridge 不声称、也不尝试直接操纵 EhViewer 私有数据库。

### 5.5 B→A 执行步骤

1. 根据 B 的必要结构确定 `<GID>.cbz`，确保 ZIP 条目能安全写出。
2. 创建临时目标画廊文件夹；将**外部** `ComicInfo.xml` 和原样封面复制到其中。
3. 从 ZIP 根级提取实际存在的数字页图，保持原名及字节；ZIP 内的 XML 不复制为另一份外部文件。
4. 尝试按 §5.3 获取 token；按 §5.2 计算 pages，建立最小 SpiderInfo；通过 `SpiderInfoCodec` 写 `.ehviewer` 并确认可解码。
5. 已拷贝文件、ZIP 关闭和元数据写入均未报错后提交最终目录；报表中将 token 缺失作为**警告**，不记为转换失败。

**示例**：ZIP 中只有 `00000003.jpg`、`00000008.webp`，外部 XML 写 `PageCount=36`，但无法获取 token：仍输出两张原图、外部封面/XML 和编码正确的 `.ehviewer`，其 `pages=36`、`pTokenMap={}`、`token=null`。报告显示「B→A 已完成；仅离线文件可保证，EhViewer 元数据恢复能力受限」。

## 6. 同格式复制

- A→A、B→B：**递归复制输入画廊文件夹的全部文件与原有目录层级**；即使目录里有已识别的无业务系统杂项，也原样保留。
- 不打开/重压缩 CBZ，不重新生成 `.ehviewer`，不修改 XML，不改变 `thumb.*` 文件名或字节。
- `ContentResolver` 流式复制，捕捉文件读取、写入与空间不足异常；默认不计算文件哈希。SAF provider 不能保证修改时间戳保持一致，报告不宣称属性完全复制。
- 输出中目标同名文件夹已存在时跳过，不做合并或覆盖。

## 7. 最小校验与故障处理

### 7.1 只保留以下必要校验

1. **形状**：一级画廊文件夹名可识别；A/B 必备文件存在且类型正确；至少存在一份实际页文件；必要项没有无法确定的歧义。
2. **数据不丢弃**：异格式转换时，未知有内容文件或目录不能安全映射时拒绝该项并指出名字；同格式复制保证所有条目保留。
3. **ZIP 可处理性**：能读出 ZIP 条目；对 B→A 必须防路径穿越、绝对路径、非法路径分隔、同名覆盖、加密/不支持方法以及明显解压膨胀风险；错误发生在该画廊而不影响整批。
4. **写入成功**：源数据流读完、输出流关闭成功、暂存目标完整；创建 B 时 CBZ 内有目标页名和内嵌 XML；创建 A 时 `.ehviewer` 自检可解析。
5. **冲突/权限**：输入输出树互不包含、不可相同；目标路径未被占用；SAF 权限足够。

### 7.2 不进行的校验

- 不遍历每张图片解码、不比对 MIME/后缀/魔数、不要求封面一定是 WebP。
- 不检查图像页号连续、不要求文件数量等于 `PageCount` 或 `SpiderInfo.pages`，不要求从第一页开始。
- 不为 20,000 张图片预先计算 SHA-256、不二次逐图片哈希校验、不重新下载缺失图片。
- 不要求原 `.ehviewer` 内的 `pTokenMap` 覆盖所有页图；A→B 无需读取它。
- 不在分类阶段解析 XML 的完整业务语义；B→A 提取 token/总页数是**尽力而为**，不是结构完整性门槛。

ZIP 库自然产生/核验的 CRC、复制时的 I/O 异常以及必要的路径安全处理仍然保留；这不是额外的耗时媒体校验。

### 7.3 临时目录与批次隔离

- 每画廊一个临时输出文件夹，完成后才提交为目标目录名；遇错误尽量清理临时对象，若 provider 不支持原子重命名，应使用独立状态标识/确定的提交步骤，避免把半成品计入成功。
- 不在同一批次重复处理同名 GID/文件夹；两个输入项会写入同一路径则按冲突跳过后一项。
- 默认串行处理，支持**取消（在文件边界或复制循环内响应）**，失败继续下一画廊；不需要对每一个图片做断点续传。取消后允许重新扫描/重试未完成画廊。
- 结果按「转换成功 / 复制成功 / 无效跳过 / 执行失败」分类；另列非阻断警告。
- 发生异常时记录相对路径与原因；**不在日志输出真实 gallery token、XML 全文或用户隐私路径中的敏感信息**。

## 8. Android 技术实现（单工程，少量清晰职责）

### 8.1 选型

- **Kotlin、Jetpack Compose、Material 3 / Material You**，采用动态色、深浅色适配、字体缩放、边到边布局。
- 单 Gradle `:app` 模块；Android Gradle Plugin、Kotlin、Compose BOM 选稳定兼容版本，使用 Gradle Kotlin DSL。
- `minSdk 26`，`targetSdk` 按实施时稳定系统要求设置。
- **SAF**：`ACTION_OPEN_DOCUMENT_TREE`、`ContentResolver`、`DocumentsContract`/`DocumentFile`；持久化授予的目录 URI 权限。不得用 `File(uri.path)` 访问 SAF 文档。
- `kotlinx.coroutines` / Flow + 简单 ViewModel；`java.util.zip` 流式处理；`kotlinx.serialization-cbor` 编解码 SpiderInfo。
- 批量处理与主线程分离。持续任务使用**可见进度与取消**；如要求熄屏继续，采用当前 Android 允许的长时运行任务/前台服务方式，按官方后台规则配置类型与通知。无需多协程并发压缩和复杂持久化队列。

### 8.2 目录建议

```text
app/src/main/java/<package>/
├── ui/          MainActivity.kt, MainScreen.kt, AdaptiveContent.kt, ResultList.kt
├── model/       GalleryItem.kt, ConversionPlan.kt, ConversionResult.kt
├── storage/     SafTree.kt, SafeCopy.kt, StagedOutput.kt
├── scanner/     GalleryScanner.kt, ZipInspector.kt
├── converter/   BatchConverter.kt, LooseToCbz.kt, CbzToLoose.kt
└── metadata/    SpiderInfoCodec.kt, ComicInfoMetadata.kt
```

类/文件数量可进一步合并；**不要**为该工具引入多个 Gradle 业务模块、依赖注入容器、大型业务数据库、云端 SDK 或网络下载引擎。

### 8.3 建议数据模型

```kotlin
enum class Layout { LOOSE, CBZ, INVALID }
enum class TargetLayout { LOOSE, CBZ }
enum class Operation { COPY, PACK, UNPACK, SKIP }
enum class ItemOutcome { COPIED, CONVERTED, SKIPPED, FAILED }

data class GalleryItem(
    val name: String,
    val gid: Long?,
    val folderUri: Uri,
    val layout: Layout,
    val issues: List<ScanIssue>,
)
```

扫描结果保存必要的 URI 与文件名索引；**不保存图片字节**。实际转换前重新核对目标是否被占用，避免扫描期间目录变化造成覆盖。

## 9. Material You 与平板横屏适配

**依据当前窗口可用宽度而非设备名称选择布局**，不得将手机单栏简单无限拉宽。

| 窗口宽度 | 需求 |
|---|---|
| `<600dp` | 手机单列：两个文件夹选择控件、目标格式、扫描与开始；扫描结果纵向列表；详情用弹层或单独页面 |
| `600–839dp` | 中宽布局：选择器按空间并列或换行，列表宽度自适应；无水平溢出 |
| `≥840dp` | **平板横屏双栏**：左边输入/输出/目标格式/任务总览；右边画廊列表、预期操作、错误与详情 |
| `≥1200dp` | 控制区域使用合理最大宽度，右侧留给列表与详情；允许额外详情区，但不要堆砌控件 |

```text
┌──────────────────────────────────────────────────────────────────────────┐
│ EhBridge                                          [主题]    [帮助]       │
├──────────────────────────┬───────────────────────────────────────────────┤
│ 输入目录   [选择]         │ 扫描：散图 120 / CBZ 72 / 非法 8             │
│ 输出目录   [选择]         │ ┌─────────────────────────────────────────┐   │
│                          │ │ 画廊名        类型    操作     结果         │   │
│ 目标格式                 │ │ 10001-甲      A      转CBZ     待处理       │   │
│ ( ) 散图 A               │ │ 10002-乙      B      复制      待处理       │   │
│ (●) CBZ B                │ │ 10003-丙      无效   跳过      缺元数据     │   │
│                          │ └─────────────────────────────────────────┘   │
│ [扫描] [开始] [取消]     │ 选中画廊的原因说明 / 提示 / 任务进度         │
└──────────────────────────┴───────────────────────────────────────────────┘
```

- 切换横竖屏、分屏、窗口缩放时，已选 URI、目标格式、当前扫描结果与正在执行的任务不得重置或重复启动。
- 一眼看清「转换 / 复制 / 跳过」；非法项显示具体文件名及中文解释，非阻断提示与错误分开展示。
- 超长标题省略但详情显示全名；适配鼠标与键盘焦点、TalkBack、200% 字体放大。
- 处理长任务显示整体画廊进度、当前画廊和已处理页数；始终可取消。界面不要为小工具增加繁琐配置页。

## 10. 错误与提示

错误必须包含**受影响的一级画廊相对路径、错误原因、涉及文件、处理结果**；内部状态码可供程序和报告使用。

| 代码 | 语义 | 是否跳过 |
|---|---|---|
| `INVALID_GALLERY_ITEM` | 一级项不是 `<GID>-<标题>` 文件夹 | 是 |
| `MISSING_REQUIRED_FILE` | 缺 `.ehviewer` / `ComicInfo.xml` / 封面 / CBZ | 是；说清缺哪一项 |
| `NO_PAGE_FILES` | A 或 CBZ 内完全没有可识别页图 | 是；**缺部分页不属于此类** |
| `LAYOUT_AMBIGUOUS` | A 散图与 B 主存档同时出现，或存在重复封面等歧义 | 是 |
| `UNMAPPED_EXTRA_DATA` | 含转换无法无损放置的未知文件/子目录 | 是；列出名字 |
| `GID_MISMATCH` | CBZ 文件名 GID 与画廊文件夹 GID 不一致 | 是 |
| `ARCHIVE_UNSAFE` | ZIP 路径穿越、同名条目覆盖、加密/无法安全解包 | 是 |
| `UNSAFE_PAGE_INDEX` | 极端异常页号超出安全范围 | 是 |
| `OUTPUT_EXISTS` | 输出已有同名画廊 | 是；不覆盖 |
| `READ_WRITE_FAILURE` | SAF 权限、空间不足、读写中断等 | 本项失败，其他项继续 |
| `SPIDER_CODEC_ERROR` | B→A `.ehviewer` CBOR 生成或本地反序列化失败 | 本项失败；输入保留 |

**非阻断提示（可在设置菜单中通过高级设置选择性关闭）**：

- `TOKEN_UNAVAILABLE`：无法恢复真实画廊 token；已完成文件转换，但 EhViewer 下载项恢复/续传可能受限。
- `TOKEN_SOURCE_CONFLICT`：内外 XML 的画廊 token 不一致；按外部优先。
- `COMICINFO_DIFFERENT`：B 的内外 XML 不一致；输出外部 XML 保持原样。
- `GALLERY_INCOMPLETE`：如果无需额外读取便能发现页码断档/实际图片少于 XML 总数，可提示，但**不能阻断**，且不必为了提示执行耗时检查。
- `SOURCE_STATE_NOT_TRANSFERRED`：A→B 的 `.ehviewer` 内部状态不在 B 格式中表达。

## 11. 最少而充分的测试与验收

自动化测试只保留**六组**，尽量用几 KB 的合成文件夹和假图片字节运行，不解码图片：

| 测试 | 需要验证的事实 |
|---|---|
| **T1：缺页 A→B** | 只有第 3、8 页、`thumb.foo`、XML 写更多页仍能打包；CBZ 含两页＋内部 XML；内外 XML 原字节相等 |
| **T2：B→A 最小 SpiderInfo** | 有有效 URL 时提取 token；无 token / XML 无法解析时仍写出 `token=null` 的可解码 CBOR；`pages` 取可信 PageCount 与最大页号的较大值，`pTokenMap` 为空 |
| **T3：同格式复制** | A→A、B→B 保留画廊目录内各文件原始字节，不修改 CBZ 或 `.ehviewer` |
| **T4：混合目录与非法项** | 合规 A/B 自动转换或复制；缺必备文件、混合主结构、未知数据、目录名错误等被单项跳过并说明 |
| **T5：安全与失败隔离** | ZIP 路径穿越/同名覆盖、输出已存在、写入失败、用户取消；绝不覆盖源、其他画廊仍可处理 |
| **T6：界面窗口变化** | 手机窄屏单栏、平板宽屏横屏双栏、运行中旋转/分屏无状态重置和重复任务 |

**唯一必要的实机兼容性核对**：生成虚拟画廊目录和文件（A组织形式），模拟运行约 200 个画廊 / 约20,000 个文件；不发生明显内存失控、主线程卡顿或整批因单项错误中断。不设置没有设备测量依据的硬性速度指标。

## 12. 实施顺序与交付要求

**阶段 I｜工程与扫描**

创建可编译 Kotlin/Compose 工程；实现 SAF 双目录选择、一级画廊扫描、A/B 自动分类、混合目录决策、手机与平板自适应界面、明确错误结果。完成 T4/T6。

**阶段 II｜三类文件操作**

完成 A→B、A→A/B→B、B→A，含 `SpiderInfoCodec`、ComicInfo URL 尽力提取、ZIP 流式 I/O、暂存提交、取消与逐画廊隔离。完成 T1/T2/T3/T5；至少编译运行于 Android 模拟器。

**阶段 III｜简单实机验收**

用目标 EhViewer 原始二进制 `.ehviewer` 做一次兼容性核对；在手机和平板横屏上进行小样本操作；实际执行约 200 画廊的批处理并记录可观察到的结果。修复发现的问题，输出 README 使用说明和 APK 构建命令。

每阶段必须提供：实际代码、可执行的编译/测试命令、已通过/未通过结果、需要用户提供真实样本才能确认的边界。禁止仅交付占位类或泛泛的架构描述。

## 13. 给 Codex 的执行指令（直接使用）

> 你是负责交付 Android App 的 Kotlin 工程师。请以用户提供的开发设计文档当作**唯一产品规格**，直接开发 **EhBridge 1.0**。
>
> 软件是 Android 离线批处理工具：用户选择输入文件夹、输出文件夹及目标格式 A「散图目录」或 B「CBZ 目录」，扫描输入根目录的一级画廊文件夹；符合目标格式的**整个文件夹直接复制**，另一种格式的画廊进行**转换**，非法项逐一跳过并给出具体中文报错。不要增加第三种输出形式。源文件不修改、不删除，输出目录已有同名项则跳过。
>
> A 格式包含 `.ehviewer`、散图页文件、任意后缀的 `thumb.*` 与外部 `ComicInfo.xml`；B 格式包含 `<GID>.cbz`、原样封面及外部 `ComicInfo.xml`，CBZ 根目录必须有实际页图和另一份同字节 XML（本工具创建时必须一致）。**不校验图片后缀、真实图片编码、页数连续性、下载完整性**；只要有部分合法命名的页文件，也照常归档或解压。
>
> **B→A 必须真的生成 `.ehviewer`：**使用 `kotlinx.serialization.cbor` 按 §5 的 SpiderInfo schema 编码。gid 来自文件夹，pages 来自 max(外部 XML 可信 PageCount, 最大现存页号)，pTokenMap 为空、startPage=0、previewPages=-1、previewPerPage=-1、upgradeFrom=null。优先从内外 XML 的画廊 URL 提取有效 gid/token；token 缺失时写 `null`，**仍完成转换**，通过警告说明 EhViewer 下载项恢复可能受限。不要伪造 pTokenMap 或网络请求。用户提供的 SpiderInfo JSON 是字段结构参照，**不是可直接写成 `.ehviewer` 的数据流**；目标版本的二进制兼容性做一次实机核对。
>
> Kotlin + Jetpack Compose + Material 3 / Material You；单 `:app` 模块，小型职责包；SAF + 流式 ZIP，不引入云服务、复杂数据库或过度解耦。**平板横屏宽窗口必须双栏**，支持旋转与分屏。校验只做结构、ZIP 路径安全、I/O、输出冲突与生成的 SpiderInfo 可解析；不要逐图哈希或解码。请按 §12 的三个阶段交付代码，每阶段构建并运行相关的最少测试，报告真实结果。优先写出可运行的完整工程，不要停留在方案讨论。

## 14. 参考依据与解释边界

- 目标仓库：<https://github.com/UjuiUjuMandan/EhViewer>。其主仓库通过 `.gitmodules` 指向 Codeberg 上的应用子模块；应用源文件未在指定 GitHub 仓库根目录直接展开。当前核查的子模块引用提交为 `257bfe843ee4345420b4f01cb26001f0ff3b0761`。
- 相近分支用于核对编码模型和行为的源码：
  - <https://github.com/EhViewer-NekoInverter/EhViewer/blob/master/app/src/main/java/com/hippo/ehviewer/spider/SpiderInfo.kt>
  - <https://github.com/EhViewer-NekoInverter/EhViewer/blob/master/app/src/main/java/com/hippo/ehviewer/spider/SpiderDen.kt>
  - <https://github.com/EhViewer-NekoInverter/EhViewer/blob/master/app/src/main/java/com/hippo/ehviewer/spider/SpiderQueen.kt>
  - <https://github.com/EhViewer-NekoInverter/EhViewer/blob/master/app/src/main/java/com/hippo/ehviewer/preference/RestoreDownloadPreference.kt>
  - <https://github.com/EhViewer-NekoInverter/EhViewer/blob/master/app/src/main/java/com/hippo/ehviewer/client/parser/GalleryDetailUrlParser.kt>
- 用户提供的 **`ehviewer_decoded.json`** 是 SpiderInfo **字段与样本值**的依据；其中 `pTokenMap` 索引形态、存在的字段可直接确认，二进制序列化协议仍依据可读源码与目标二进制金样本验证。
- Android 自适应窗口：<https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes>。

**产品交付判定**：使用一个简单工作流，可靠完成两种 EhViewer 画廊目录结构间的批量转换与复制；对残缺图库尽量宽容，对不合法/不可安全映射的数据给出明确信息；B→A 尽力构建最小有效 SpiderInfo，原始文件始终安全；手机与平板横屏均好用。