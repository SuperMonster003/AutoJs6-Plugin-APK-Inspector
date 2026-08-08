<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="apk-inspector-ic-launcher" border="0" width="128" />
  </p>

  <p>文件管理器插件. 无需安装即可检查 APK, APKS, XAPK, APKM, APKZ 和 AAB 文件</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-APK-Inspector?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 语言

******

当前 README.md 支持以下语言:

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

******

### 简介

******

APK 检查器为文件管理器中的 Android 包文件提供主检查动作. 插件只分析有界的应用私有快照, 不修改或安装源文件.

******

### 功能

******

- 为 APK, APKS, XAPK, APKM, APKZ 与 AAB 注册 Explorer Action v2 主动作.
- 解析 APK 文本或二进制 Manifest, AAB protobuf Manifest 与 bundletool toc.pb 元数据.
- 显示包标识, 版本, SDK 范围, 请求权限, 组件, 设备匹配 split, OBB 资源及结构问题.
- 检测 APK V1, V2 与 V3 签名方案是否存在, 不宣称完成密码学有效性验证.
- 在独立只读页面显示格式化 Android Manifest.
- 为精确 Android 包 MIME 类型提供独立 ACTION_VIEW 网关.

******

### 支持格式

******

Explorer 主动作精确匹配以下扩展名:

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

******

### 插件接口

******

宿主通过以下标识发现并执行插件:

```text
service action: org.autojs.plugin.EXPLORER_ACTION
execute action: org.autojs.plugin.EXPLORER_ACTION_EXECUTE
plugin id: apk-inspector
engine: explorer-action
variant: default
Explorer action id: inspect-android-package
MIME type: Explorer: extension-only; ACTION_VIEW: dedicated Android package MIME types
required host build: 5269
```

版本 1 只执行检查. 插件不包含安装按钮, 安装权限, 包安装器, 源文件编辑器或目录枚举. 宿主安装流程保持独立. 插件不可用时, 宿主使用降级动作.

需要宿主构建版本 5269 或更高版本.

******

### 安全性

******

受保护 Explorer 网关验证协议 v2, 主文件页 surface, 动作 ID, content URI 层级, 精确 ClipData, 文件名, 扩展名, MIME 类型, 大小及只读授权. 公共 ACTION_VIEW 网关只接收专用包 MIME 类型. 输入仅复制一次到有界的只读私有快照, 同时计算 SHA-256. 插件不会枚举协议中的父 URI.

******

### 安全限制

******

- 最大输入大小: 4 GiB.
- 每次动作只接收一个目标文件, 并保留至少 128 MiB 缓存空间.
- 归档条目数, 名称, 声明大小, 总大小, 嵌套 APK 扫描, 元数据, protobuf 与 Manifest 输出均有上限.
- 外部 ACTION_VIEW 拒绝 application/zip, application/octet-stream, 写入, 持久化及前缀授权.
- V1-V3 只检测签名方案是否存在. V4 需要独立 idsig 输入, 不属于当前协议.
- 插件从不安装软件包, 且不申请存储, 网络或软件包安装权限.

******

### 版本历史

******

# v1.0.1

###### 2026/08/08

* `修复` 在插件中心启用时返回有效的 Explorer Action 服务绑定
* `优化` 精简插件名称和描述, 并使用户文档表述更自然

# v1.0.0

###### 2026/08/02

* `新增` APK 检查器插件, 插件 ID 为 `apk-inspector`, 动作 ID 为 `inspect-android-package`, 引擎为 `explorer-action`, 变体为 `default`
* `新增` 通过 Explorer Action 协议 v2 为 APK, APKS, XAPK, APKM, APKZ 和 AAB 文件提供主要只读检查动作
* `新增` 只读解码文本和二进制 APK 清单, AAB protobuf 清单及 bundletool `toc.pb` 元数据
* `新增` 软件包详情, 请求权限, 组件, 设备匹配拆分, OBB 资源, 结构发现, 格式化清单查看和 APK V1-V3 签名方案存在性检测
* `新增` 相互分离的受保护文件浏览器入口和精确 MIME Android `ACTION_VIEW` 入口, 4 GiB 输入上限及计算 SHA-256 的有界私有只读快照
* `新增` 插件元数据, 界面文本, 使用说明, README 和 CHANGELOG 的多语言资源: 西班牙语/法语/俄语/阿拉伯语/日语/韩语/英语/简体中文/香港繁体/台湾繁体
* `依赖` 附加 Gson 版本 2.13.2

##### 查看更多版本

* [CHANGELOG-zh-Hans.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hans.md)

******

### 构建

******

```powershell
.\gradlew.bat :app:assembleDebug
```

发布构建:

```powershell
.\gradlew.bat :app:assembleRelease
```

构建参数来自 version.properties. 当前最低 SDK 为 24, 目标 SDK 为 36.

******

### 资源结构

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
```

strings.xml 本地化插件元数据与界面文本. plugin_instruction.md 提供宿主显示的使用说明. .python/generate_markdown.py 根据 JSON 源生成多语言 README 与更新日志.

******

### 链接

******

- AutoJs6 文档: https://docs.autojs6.com
- Android 安全文件共享: https://developer.android.com/training/secure-file-sharing
