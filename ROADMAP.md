# AutoJs6 APK Inspector Roadmap

本文档是 APK 检查器从 "基础安装包检查" 演进为 "全面离线安装包分析工具" 的执行清单. 每一项只有在代码/测试与可验证的验收条件同时完成后才可勾选.

## 状态与边界

- `[x]`: 已完成并在当前仓库验证.
- `[ ]`: 尚未完成; 括号中的 `插件`/`宿主`/`API`/`测试`/`发布` 表示主要落点.
- 插件的只读定位不可动摇: 任何条目都不得引入安装能力、源文件写入、目录枚举, 或存储/网络/安装权限申请.
- 插件代码位于本仓库; Explorer Action 协议与宿主文件管理器位于配套的 AutoJs6 仓库. 标注 `API`/`宿主` 的条目需要两侧代码与联合验证同时完成后才可勾选.
- 当前部署基线: Explorer Action 协议 v22, 宿主版本代码 5277, 单文件只读主动作和更多菜单动作 + 有界 `.idsig` 描述符 + 独立 ACTION_VIEW 网关.

## M0: 已交付基线 (v1.0.x)

- [x] (插件) 文件管理器主动作与 ACTION_VIEW 双入口, 逐字段校验协议版本、动作 ID、content URI 结构、文件名、大小与只读授权.
- [x] (插件) 六种格式解析: APK, APKS (bundletool/SAI), XAPK, APKM, APKZ, AAB; 集合包按设备规格选择分包并统计 OBB 资源.
- [x] (插件) 检查报告四分区: 软件包详情 / 组件 / 请求的权限 / 安全与兼容性发现, 附应用图标与标签加载.
- [x] (插件) 文本与二进制 APK 清单、AAB protobuf 清单及 bundletool toc.pb 解码, 独立只读页面展示格式化清单.
- [x] (插件) APK V1 / V2 / V3 签名方案存在性检测 (不含密码学验证).
- [x] (插件) 有界只读快照链路: 一次复制、SHA-256 计算、4 GiB 上限、128 MiB 缓存预留、24 小时过期清理.
- [x] (插件) v1.0.x 初版解析上限 (当前预算见 M7): 16384 归档条目 / 512 集合包 APK / 1024 字符条目名 / 8 GiB 声明总量 / 256 MiB 嵌套扫描 / 1 MiB 元数据.
- [x] (发布) 10 种语言的界面文本、使用说明与 README / CHANGELOG 生成管线 (.readme + .changelog + .python).

验收条件: 已随 v1.0.0 / v1.0.1 发布并在宿主 5269+ 实机验证.

## M1: 签名与证书深化

- [x] (插件) 在软件包详情中展示签名证书信息: 主体、颁发者、序列号、有效期与 SHA-256 指纹; 多签名者逐一列出.
- [x] (插件) 引入 apksig 库或等效实现, 对 V2 / V3 方案执行真实密码学验证, 报告 "存在 / 验证通过 / 验证失败 (原因)" 三态结论.
- [x] (插件) 展示 V3.1 与证书轮换 (SigningCertificateLineage) 信息, 标注新旧证书关系.
- [x] (插件) 检测同目录 `.idsig` 文件存在性并在报告中提示 V4 签名可能可用 (仅提示, 不读取协议外文件).
- [x] (API/宿主) 有界同级文件授权: 宿主按所选文件名生成 `.idsig` 候选并授予只读描述符, 插件据此完成 V4 完整校验 (参照 Archive-Manager v12 分卷授权模式).
- [x] (测试) 覆盖有效签名、破坏签名、多签名者、证书轮换与缺失 idsig 的样本矩阵.

验收条件: 证书字段与 `apksigner verify --print-certs` 在样本集上一致; 验证失败的样本能给出与 apksigner 同级别的失败原因.

## M2: 报告内容增强

- [x] (插件) 危险权限高亮: 按 protectionLevel 将请求权限分组 (运行时 / 签名 / 普通), 运行时权限置顶并附一句话说明.
- [x] (插件) 组件统计: 从清单提取 activity / service / receiver / provider 数量与导出 (exported) 状态, 在组件分区分组展示.
- [x] (插件) 原生库概览: 列出 lib/ 下各 ABI 目录与体积, 标注与当前设备 ABI 的匹配情况.
- [x] (插件) DEX 概览: 列出 classes*.dex 数量与体积 (有界读取, 不反编译).
- [x] (插件) 16 KB 页大小就绪检查: 对直接检查的 APK / AAB 只读取每个 64 位 (`arm64-v8a` / `x86_64` / `riscv64`) 原生库的 ELF 头与程序头表 (每个条目至多 64 KiB, 经 ZIP 解压流读取, 不落盘), 判定全部 `PT_LOAD` 对齐是否不低于 16 KB; 当清单声明 `extractNativeLibs="false"` 时再核对未压缩条目的 ZIP 数据偏移是否按 16 KB 对齐. 结论分为就绪 / 未就绪 / 未验证 / 无 64 位库 / 未评估 (容器内嵌套 APK 只索引中央目录, 不评估), 在原生库分区与宿主文件信息摘要中展示, 未就绪时进入发现分区. (2026-09-12)
- [x] (插件) 报告字段长按复制 (SHA-256、包名、版本等), 并支持通过系统分享面板导出纯文本报告 (不申请存储权限, 不落盘).
- [x] (插件) 为 AAB 与超限集合包提供有界的资源表回退: 按当前设备语言与密度从 AAB `resources.pb` 或 APK `resources.arsc` 解析应用名称和光栅图标, 替代清单引用/文件名回退, 且超限或损坏只影响回退结果.
- [x] (测试) 新增权限、清单组件、原生库、DEX、资源表回退与损坏嵌套代码目录的分区隔离矩阵: 生产上限或失败只使相关分区降级, 其余分区仍完整生成, 分享文本保留同一 partial 提示.

验收条件: 任一新分区解析失败或超限只影响该分区并显示原因, 报告整体仍可用; 导出文本与屏幕内容一致.

## M3: 集合包与 AAB 深化

- [x] (插件) 摘要展示 APKS / XAPK / APKM 自带元数据 (`meta.sai_v1/v2.json` / `manifest.json` / `info.json`, 限 4 MiB): 打包工具与格式版本、元数据声明的应用版本及实际存在的图标条目; 损坏或超限仅使该摘要降级.
- [x] (插件) 设备配置模拟: 在报告内切换语言 / 屏幕密度 / ABI, 本地重算分包选择结果, 标注与真实设备的差异.
- [x] (插件) AAB 深化: 解析 BundleConfig.pb 与 dynamic feature 模块的分发条件 (onDemand / 条件安装), 在模块列表中标注.
- [x] (测试) 模拟选择结果与 bundletool build-apks + install-apks 在样本集上的选择一致.

验收条件: 同一 APKS 样本在三种模拟配置下的选择结果可复现且与 bundletool 一致; AAB 的 BundleConfig、模块分发类型与条件在损坏/超限样本中独立降级并明确标注.

## M4: 界面与无障碍

- [x] (插件) 深色主题与动态取色 (Material You) 跟随系统, 与宿主明暗状态一致; 清单查看页同步适配.
- [x] (插件) 清单查看页支持 XML 语法高亮、行号与文本查找.
- [x] (插件) TalkBack 审计: 分区标题语义、图标 contentDescription、至少 48 dp 触控目标; 阿拉伯语 RTL 页面镜像复核.
- [x] (插件) 1.5x / 2.0x 字体倍率与横屏、窄屏 (320 dp) 布局检查, 长 SHA-256 与权限名正确折行.
- [x] (测试) 建立屏幕尺寸 × 字体倍率 × 明暗主题 × RTL 的截图复核矩阵.

验收条件: 矩阵内所有组合无文本截断与不可达控件; TalkBack 可线性朗读完整报告.

## M5: 工程与质量

- [x] (测试) 建立去隐私化样本集: 六种格式 × (正常 / 结构损坏 / 超限 / 设备不兼容), 附生成方式与 SHA-256 清单, 可提交仓库.
- [x] (测试) 为 PackageRequestPolicy / PackageCacheStager / AndroidPackageArchive / ApkSignatureDetector 建立单元测试, 覆盖全部拒绝分支.
- [x] (测试) 两个入口 Activity 的仪器或 Robolectric 测试: 伪造 Intent、越权授权、超大声明与并发取消均被安全拒绝.
- [x] (发布) GitHub Actions: assembleDebug + 单元测试 + 文档一致性检查 (运行 generate_markdown.py 后工作区无 diff 才通过).
- [x] (发布) releases/ 产物附 SHA-256 校验文件, 命名与现有 `autojs6-plugin-apk-inspector-v*-<hash>.apk` 规则统一.

验收条件: CI 在干净克隆上绿灯; 手工改动生成的 Markdown 会被文档一致性检查拦截.

## M6: 宿主协同 (跨仓库, 可选)

- [x] (API/宿主/插件) 渐进增强宿主现有 APK 信息对话框: 严格保留全部基础信息与安装/清单交互; 插件可用时在同一对话框追加更完整的检查摘要, 插件缺失、停用、不兼容或检查失败时仍只显示原有基础信息.
- [x] (插件) 上述协议扩展保持向后兼容: 基础 Explorer Action v22 目录和 Activity 入口保持不变, 新能力通过独立能力位协商.

验收条件: 新旧宿主与新旧插件混布时按能力位自动降级; 无插件路径的对话框内容、按钮和失败处理保持不变, 且无崩溃与入口错位.


## M7: 大型安装包与文件管理器入口 (2026-09-10)

- [x] (插件/宿主) 用统一 `PackageInspectionLimits` 管理 APK, AAB, APKS 与嵌套清单的预算, 避免同一文件在不同解析路径碰到不一致的旧上限.
- [x] (插件) 嵌套包原生库与 DEX 分析支持 ZIP64 EOCD, locator 与 64 位条目大小, 大量资源不再造成 16384 条目或 ZIP32 目录限制导致的分区缺失.
- [x] (插件) Explorer Action v22 目录显式声明单文件基数, Activity 呈现与无输出模式; 主按钮和更多菜单采用独立动作 ID, 共享同一只读入口与请求验证.
- [x] (测试) 70000 个资源条目且清单位于末尾的 APK 与嵌套 XAPK, 超过 18184 个条目的外层容器/AAB/APKS, 超过 4 MiB 的清单, 损坏 ZIP64 locator/偏移与缩小预算的降级测试.
- [x] (宿主/实机) QV710AF65F 验证启用/停用后的六类扩展名菜单, 并从更多菜单打开 `WeChat-8.0.48(2589)-GP.apk` 检查报告.

| 检查项目 | 原预算 | 当前预算 |
| --- | --- | --- |
| 归档与嵌套 ZIP 条目 | 16384, AAB 清单扫描 10000 | 262144 |
| 非 bundletool 集合包 APK 数量 | 512 | 4096 |
| toc.pb 中 APK 数量 | 16384 | 262144 |
| 条目名称长度 | 1024 字符 | 4096 字符 |
| 单包暂存与单条目声明大小 | 4 GiB | 8 GiB |
| 所有条目声明大小之和 | 8 GiB | 64 GiB |
| 嵌套清单/原生库/资源扫描总量 | 256 MiB / 256 MiB / 512 MiB | 16 GiB |
| 单个嵌套 APK 的清单扫描 | 64 MiB | 8 GiB |
| APK/AAB 清单输入与输出字符预算 | 4 MiB / 4M 字符 | 16 MiB / 16M 字符 |
| toc.pb 输入 | 2 MiB | 16 MiB |
| 容器元数据与 BundleConfig.pb | 1 MiB | 4 MiB |
| 资源表 / 图标 | 32 MiB / 4 MiB | 64 MiB / 8 MiB |
| 原生库目录项 / 被扫描 APK 数量 | 4096 / 512 | 32768 / 4096 |
| 嵌套 APK 中央目录保留窗口 | 8 MiB | 32 MiB |
| 提取用于系统资源显示的 base.apk | 512 MiB | 8 GiB, 同时检查缓存可用空间 |
| AAB 模块清单数量 / 总输入 | 128 / 16 MiB | 512 / 64 MiB |

流式字节预算不预分配同等大小的内存. 路径合法性, 重复名称, ZIP/protobuf 边界, 整数溢出, 缓存预留与只读授权继续校验. 权限/DEX 的界面展示条数和签名格式自身的结构约束维持各自语义.

验收证据: `LargePackageInspectionTest`, `ExplorerActionCatalogTest`, `NativeLibrarySummaryTest`, `PrivacyNeutralFixtureMatrixTest` 和宿主 `ApkInspectorIntegrationTest`. 本地构建及实机日志存放在两侧 `app/build/inspection-fixes/`.

实机结果: 菜单入口可见, 六类扩展名和启用状态刷新测试通过; 微信 APK 报告显示 `com.tencent.mm`, `8.0.48 (2589)`, V2/V3 签名验证通过. 对应截图与联合测试日志位于宿主 `app/build/inspection-fixes/`.

## 共享解析组件迁移 (3-Setup Installer 原 P8 条目, 2026-10-02)

- [x] 消费宿主 `package-archive-parser` 的固定 Release AAR, SHA-256 `1441bbcee8468362b0ee41f7b3d5ab47eb87b4df78a1388bb1134223055f46e7`; 公共与 Explorer 契约 AAR 原字节保留, 三份二进制均在配置阶段按锁核验.
- [x] 删除重复 APK/AAB 清单二进制解码, bundletool TOC 与预算定义, 原 Archive 选包实现改为共享解析结果上的只读报告适配. 同格式/设备输入使用同一选包结果; 保留 Inspector 专有组件, 保护级别, 图标, AAB 分发, 签名密码学验证, 原生库, DEX 和 16 KiB 分析.
- [x] 保留严格签名块异常校验及明确授权的 `.idsig` 边界; V1 存在性检测复用共享实现, 不调用隐式检查旁边 `.idsig` 的共享便捷方法. 保留原显示快照空间/字节限制, 不对外暴露安装准备或暂存功能.
- [x] 原有消费方测试迁移到共享类型, 新增六格式结果一致性/只读来源, AAB XML 命名空间与树预算, sidecar 授权回归. 本地完整 JVM 231 项通过, QV770340J7/API 33 的契约两项与六格式 Android 解析一项共 3/3 通过, 未安装测试夹具或改用户默认项.

对应版本为 1.2.2, 本地 build 42; Explorer Action v22, host file-information v1 和最低宿主 5277 保持. 详细差异, 签名 Release 与设备收尾见 [共享解析迁移证据](docs/development/shared-parser-migration-evidence.md). 此项属于既有安装器 P8 的跨仓库迁移, 不引入安装能力, 不表示已公开发布.
