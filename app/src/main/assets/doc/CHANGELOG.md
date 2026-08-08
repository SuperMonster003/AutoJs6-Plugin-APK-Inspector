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
