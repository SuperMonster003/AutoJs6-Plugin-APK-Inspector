<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="APK Inspector" width="128" />
  </p>

  <h1>APK Inspector</h1>

  <p>AutoJs6 文件管理器插件: 无需安装, 点开即可看清 APK 与 AAB 安装包的版本、权限、签名与设备兼容性</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-APK-Inspector?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=534BAE&label=License"/></a>
  </p>
</div>

### 语言 (Languages)

README 提供以下语言版本:

- 简体中文 [zh-Hans] # 当前
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ar.md)

### 项目简介

APK 检查器 (APK Inspector) 是 AutoJs6 文件管理器的配套插件. 在文件管理器中点击 APK, APKS, XAPK, APKM, APKZ 或 AAB 文件, 即可直接打开一份安装包检查报告: 应用叫什么、是什么版本、要哪些权限、能否在当前设备安装, 一屏看清. 全程无需安装该软件包, 插件也绝不修改原文件.

检查报告分为四个部分: “软件包详情” 显示应用名称、图标、包名、版本、SDK 范围、签名验证与签名证书轮换链、文件大小及 SHA-256 校验值; “组件” 列出包内全部 APK 分包与 OBB 资源, 标出与当前设备匹配的部分 (AAB 则列出模块), 按 activity/别名、service、receiver 与 provider 分组统计清单声明及其显式 exported 状态, 按 ABI 汇总原生 .so 库的未压缩体积与设备匹配情况, 并列出标准 classes*.dex 文件及其未压缩体积; “请求的权限” 按保护级别分组列出应用申请的系统权限, 将运行时危险权限置顶并附简短说明; “安全与兼容性发现” 给出结构问题与设备兼容性结论. 报告页的 “查看清单” 按钮还可打开格式化后的 AndroidManifest 全文.

### 功能亮点

- 点击即查: 在 AutoJs6 文件管理器中点击安装包文件即可打开检查报告, 无需安装、解压或联网.
- 六种格式: 支持标准 APK、多分包集合格式 (APKS, XAPK, APKM, APKZ) 以及应用商店分发格式 AAB; APKS 同时兼容 bundletool 与 SAI 两种导出.
- 版本与兼容性: 显示包名、版本名与版本号、最低/目标/最高 SDK, 并与当前设备系统版本对照, 安装前即可判断兼容性.
- 权限透明: 按保护级别把请求权限分为运行时/危险、签名/受保护和普通三组, 高亮置顶运行时权限并附一句话说明; 无法解析的级别仍会显示并明确标注.
- 分包分析: 列出集合包内全部 APK 条目与 OBB 数据包, 标出为当前设备选中的分包 (基础包、语言、屏幕密度、架构等).
- 资源表身份回退: 当 Android 无法直接加载 AAB 或超限集合包时, 无需提取整个嵌套 APK, 即可按当前设备语言与密度从 AAB resources.pb 或 APK resources.arsc 解析应用名称和光栅图标.
- 组件暴露概览: 汇总已选 APK 分包或已扫描 AAB 模块中的 activity/别名、service、receiver 与 provider, 将显式 android:exported 分为已导出、未导出或未声明/无法解析.
- 原生库概览: 按 ABI 与未压缩体积汇总已选 APK 分包或 AAB 模块中的 .so 文件, 标出设备首选 ABI、支持的后备 ABI 与不支持的架构, 全程不提取库内容.
- DEX 概览: 按自然顺序列出已选 APK 分包或 AAB 模块中的标准 classes*.dex 文件, 显示单文件与未压缩总体积, 不提取、解码或反编译 DEX 内容.
- 报告复用: 长按软件包详情中的任一核心字段可复制其原始值; 也可通过 Android 系统分享面板发送与屏幕一致的 `text/plain` 报告, 文本只驻留内存, 不创建文件或申请存储权限.
- 签名与证书验证: 对 APK V2 / V3 / V3.1 签名执行真实密码学验证, 报告 V1 是否存在; 逐一展示当前签名证书, 并标注已验证轮换链中的新旧关系与 SHA-256 指纹.
- V4 / V4.1 同级签名验证: AutoJs6 只派生精确同级文件 `<APK 文件名>.idsig` 并授予有界只读描述符; 插件验证签名数据、证书与公钥、对应 V2 / V3 APK 摘要、fs-verity 根、内嵌 Merkle 树及 V3.1 轮换签名者.
- 清单可读化: 自动把二进制 APK 清单与 AAB protobuf 清单解码为可读 XML, 在独立只读页面随时查看.
- 完整性核对: 读取文件的同时计算 SHA-256, 方便与官方发布的校验值比对.
- 结构体检: 自动发现缺失基础包、分包重复或依赖缺失、版本与包名不一致等结构问题, 并区分阻断性 [!] 与提示性 [i].

### 使用方法

1. 从 Releases 下载并安装 APK 检查器, 然后在 AutoJs6 的插件中心启用它 (需要 AutoJs6 版本代码 5277 或更高).
2. 打开 AutoJs6 的文件管理器, 找到想查看的安装包文件 (APK, APKS, XAPK, APKM, APKZ 或 AAB).
3. 点击该文件, 或在文件菜单中选择 “检查 Android 软件包”, 稍候片刻即可看到检查报告.
4. 自上而下查看应用图标与名称、软件包详情、组件、请求的权限, 以及安全与兼容性发现.
5. 长按“软件包详情”中的任一字段可复制其值; 点按工具栏中的“分享报告”可通过 Android 系统分享面板发送与屏幕一致的文本.
6. 点按 “查看清单” 可阅读完整的 AndroidManifest; 看完后按返回键即可回到文件管理器.

> 其他应用也可以通过系统 “打开方式” (ACTION_VIEW) 调起 APK 检查器, 前提是以 content 地址与专用安装包 MIME 类型发起请求. 插件始终只读, 不提供任何安装入口.

### 支持格式

文件管理器主动作精确匹配以下扩展名 (不区分大小写):

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

其中 APKS, XAPK, APKM 与 APKZ 是包含多个分包 APK 的集合格式; AAB 是提交应用商店的 App Bundle 格式, 只能查看内容, 需经 bundletool 等工具转换后才能安装.

### 常见问题

#### 这个插件能安装 APK 吗?

不能, 这是刻意的设计. 插件不申请安装权限, 界面中没有任何安装入口, 它的定位是在安装前帮你看清安装包内容. 安装请继续使用系统安装器或宿主自身的流程.

#### 为什么某些文件无法检查?

常见原因包括: 文件超过 4 GiB 上限; 设备缓存空间不足 (需预留至少 128 MiB); 集合包条目数量或体积超出解析上限; 文件在读取过程中被其他应用改动; 或文件本身结构损坏. 错误提示会说明具体原因.

#### 签名检测结果能证明安装包安全吗?

不能. 插件会验证 V2 / V3 / V3.1 / V4 / V4.1 的软件包完整性与签名者证明, 并显示证书指纹和轮换链; 但有效签名只能证明文件自该签名者签署后未被修改, 不能证明签名者或应用本身可信. 请再与官方下载渠道公布的证书指纹和 SHA-256 对照.

#### 为什么 AAB 文件会提示 “需要转换后才能安装”?

AAB 是面向应用商店的分发格式, Android 设备无法直接安装. 插件可以解析它的 protobuf 清单与模块结构, 并通过 resources.pb 解析本地化应用名称和密度匹配的光栅图标供查看; 安装仍需先用 bundletool 等工具转换为 APK 或 APKS.

### 权限与安全

插件不申请存储、网络或安装软件包权限. 目标安装包只能通过宿主授予的临时只读 content 地址访问; 可选 `.idsig` 只能通过宿主为精确 `<APK 文件名>.idsig` 派生的有界只读描述符访问, 不提供目录列表或任意同级路径. 检查开始前, 两份输入都会被复制为应用私有缓存中的只读快照 (复制安装包的同时计算 SHA-256), 全部解析只发生在快照上; `.idsig` 上限为 40 MiB, 过期快照最迟 24 小时后自动清理. 来自文件管理器的请求会逐项校验协议版本、请求与动作标识、目标元数据、宿主版本、URI 结构、文件名、大小、只读授权及宿主会话 Binder, 任一不符即拒绝; 来自其他应用的 “打开方式” 请求只接受专用安装包 MIME 类型, 拒绝 application/zip、application/octet-stream 以及任何写入、持久化或前缀授权.

为防止恶意构造的文件耗尽设备资源, 解析设有以下上限, 超限文件会被拒绝并给出提示:

- 单个文件最大 4 GiB, 复制时缓存至少保留 128 MiB 可用空间; 每次动作只处理一个目标文件.
- 归档最多解析 16384 个条目, 集合包最多扫描 512 个 APK 分包, 条目名最长 1024 字符.
- 声明的单条目大小不超过 4 GiB, 声明总大小不超过 8 GiB.
- 嵌套 APK 清单扫描不超过 256 MiB, 集合包元数据不超过 1 MiB, 用于加载图标与标签的临时 APK 不超过 512 MiB.
- 资源表回退中每份资源表最多读取 32 MiB, 每个图标最多读取 4 MiB; 在嵌套 APK 中定位资源表与图标共用 512 MiB 扫描预算. 超限、资源损坏或引用无法解析时只关闭对应回退并明确标注.
- 权限分类最多扫描 2048 项请求、显示 512 个安全且去重的名称, 每条系统说明最多 240 个字符; 省略项和无法获取的保护级别都会明确标注.
- 组件统计每份清单最多扫描 4096 项声明; AAB 最多扫描 128 份模块清单, 共享 16 MiB 输入预算. 省略项、无法解析的 exported 值与单份清单失败都会明确标注.
- 原生库统计最多保留 4096 个 .so 条目并显示 64 个 ABI 目录; 最多读取 512 个已选嵌套 APK, 共享 256 MiB 输入预算, 每个 APK 最多保留 8 MiB 中央目录数据. 超限与失败只产生明确标注的不完整结果.
- DEX 统计会计入已扫描的全部标准条目, 但最多显示 128 条自然排序路径; 与原生库概览共用同一次有界中央目录扫描, 不提取、解码或反编译 DEX 内容.

### 插件接口

宿主 (AutoJs6) 通过以下标识发现并调用插件, 供插件或宿主开发者参考:

```text
service action: org.autojs.plugin.EXPLORER_ACTION
execute action: org.autojs.plugin.EXPLORER_ACTION_EXECUTE
plugin id: apk-inspector
engine: explorer-action
variant: default
Explorer action id: inspect-android-package
MIME type: Explorer: extension-only; ACTION_VIEW: dedicated Android package MIME types
required host build: 5277
```

当前版本只执行只读检查: 没有安装按钮、安装权限或软件包安装器, 不修改源文件, 也不枚举目录. V4 只使用宿主精确派生的 `.idsig` 候选, 将其有界只读描述符复制为私有快照, 并在暂存后立即关闭宿主会话. 插件未安装或被停用时, 宿主自动回退到默认动作, 互不影响.

### Roadmap

已实现能力以上文与 Roadmap 勾选条目为准; 集合包深化、设备配置模拟等后续计划集中维护在 Roadmap 中, 未勾选条目不代表当前版本已支持.

- [ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/ROADMAP.md)

### 版本记录

#### v1.1.0

_2026/08/30_

- `提示` Explorer Action 协议 v22 与有界 V4 同级文件访问要求 AutoJs6 版本代码不低于 5277
- `新增` 新增有界资源表应用名称与光栅图标回退: 按当前语言与密度解析 AAB resources.pb 和 APK resources.arsc, 无需提取超限嵌套 APK; 资源表、图标及扫描超限失败相互隔离并明确标注
- `新增` 新增长按复制软件包详情核心字段原始值, 并通过 Android 系统分享面板发送与屏幕一致的纯文本报告; 分享内容只驻留内存, 不申请存储权限或创建文件
- `新增` 新增有界 DEX 概览: 按自然顺序列出已选 APK 分包和 AAB 模块中的标准 classes*.dex 文件及其单项与未压缩总体积, 与原生库共用中央目录扫描, 不提取、解码或反编译 DEX 内容
- `新增` 新增有界原生库概览: 按 ABI 与未压缩体积汇总已选 APK 分包和 AAB 模块中的 .so 文件, 标出设备首选、后备兼容与不支持的 ABI, 全程不提取库内容
- `新增` 新增有界清单组件统计: 汇总已选 APK 分包和已扫描 AAB 模块中的 activity/别名、service、receiver 与 provider, 按显式 android:exported 状态分组并标注不完整结果
- `新增` 新增 APK V2、V3、V3.1、V4 与 V4.1 的设备端密码学验证, 覆盖内容摘要、签名者证明、fs-verity 根、内嵌 Merkle 树及互补签名方案匹配
- `新增` 新增详细签名证书字段与已验证证书轮换链, 展示旧/当前角色、能力标志和 SHA-256 指纹
- `新增` 新增通过宿主精确派生只读描述符暂存 `.idsig` 的有界链路; 仍不提供目录枚举或任意同级文件访问
- `新增` 新增按 `protectionLevel` 将请求权限分为运行时/危险、签名/受保护和普通三组; 运行时权限高亮置顶并附有界的一句话说明, 无法获取的级别仍会显示并明确标注
- `优化` 强化 Explorer Action v22 请求校验与不可变私有快照, 设置 4 GiB 安装包上限、40 MiB idsig 上限、文件身份复核并及时关闭宿主会话
- `优化` 新增由 Build Tools 37 `apksigner` 生成的有效、破坏、多签名者、V3.1 轮换、V4.1 轮换、缺失及格式损坏样本矩阵

#### v1.0.1

_2026/08/08_

- `修复` 修复插件在插件中心启用后宿主无法绑定服务的问题; 现在启用后 “检查 Android 软件包” 动作立即可用
- `优化` 精简插件名称与描述, 使用户文档表述更自然易读

#### v1.0.0

_2026/08/02_

- `提示` 首个公开版本, 需要 AutoJs6 版本代码 5269 或更高
- `新增` 在 AutoJs6 文件管理器中点击 APK, APKS, XAPK, APKM, APKZ 或 AAB 文件, 即可打开只读检查报告 (插件 ID `apk-inspector`, 动作 ID `inspect-android-package`)
- `新增` 检查报告展示应用名称与图标、包名、版本、SDK 范围、请求的权限、分包与 OBB 资源、结构问题, 以及 V1-V3 签名方案存在性
- `新增` 自动解码文本与二进制 APK 清单、AAB protobuf 清单及 bundletool `toc.pb` 元数据, 并提供格式化清单的独立查看页面
- `新增` 支持其他应用通过系统 “打开方式” (ACTION_VIEW) 以专用安装包 MIME 类型调起检查
- `新增` 检查前先把文件复制为带 SHA-256 校验的只读私有快照 (上限 4 GiB); 插件不申请存储、网络或安装软件包权限
- `新增` 内置 10 种语言的界面文本、使用说明、README 与 CHANGELOG: 简体中文、香港繁体、台湾繁体、英语、法语、西班牙语、日语、韩语、俄语、阿拉伯语
- `依赖` 引入 Gson 2.13.2

##### 完整记录

- [CHANGELOG-zh-Hans.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hans.md)

### 构建

```powershell
.\gradlew.bat :app:assembleDebug
```

Release 构建:

```powershell
.\gradlew.bat :app:assembleRelease
```

构建与签名参数由 version.properties 与 sign.properties 控制; 当前最低支持 Android 7.0 (SDK 24), 目标 SDK 36.

README 与 CHANGELOG 均由 .python/generate_markdown.py 依据 .readme/ 与 .changelog/ 下的 JSON 语言资源和模板生成 (共 10 种语言). 修改文档请编辑对应 JSON 后重新运行脚本, 不要直接改动生成的 Markdown.

### 相关链接

- AutoJs6 文档: https://docs.autojs6.com
- Android 安全文件共享: https://developer.android.com/training/secure-file-sharing
