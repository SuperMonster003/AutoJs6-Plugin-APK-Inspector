<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-apk-inspector-ic-launcher" border="0" width="128" />
  </p>

  <p>在 AutoJs6 檔案總管中深度唯讀檢查 APK, 分割套件容器及 Android App Bundle</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-APK-Inspector?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 語言

******

目前 README.md 支援以下語言:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-HK.md)
- 繁體中文 (台灣) [zh-Hant-TW] # 目前
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-en.md)
- [Francais [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-fr.md)
- [Espanol [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ko.md)
- [Russkii [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ar.md)

******

### 簡介

******

AutoJs6 APK Inspector 外掛程式為主檔案頁中的 Android 套件提供主要檢查動作. 外掛程式只分析有界的應用程式私有快照, 不修改或安裝來源檔案.

******

### 功能

******

- 為 APK, APKS, XAPK, APKM, APKZ 及 AAB 註冊 Explorer Action v2 主要動作.
- 解析 APK 文字或二進位 Manifest, AAB protobuf Manifest 及 bundletool toc.pb 中繼資料.
- 顯示套件識別, 版本, SDK 範圍, 要求權限, 元件, 裝置配對 split, OBB 資源及結構問題.
- 偵測 APK V1, V2 及 V3 簽章方案是否存在, 不宣稱完成密碼學有效性驗證.
- 在獨立唯讀頁面顯示格式化 Android Manifest.
- 為精確 Android 套件 MIME 類型提供獨立 ACTION_VIEW 閘道.

******

### 支援格式

******

Explorer 主要動作精確比對以下副檔名:

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

******

### 外掛程式介面

******

AutoJs6 使用以下識別探索並執行外掛程式:

```text
service action: org.autojs.plugin.EXPLORER_ACTION
execute action: org.autojs.plugin.EXPLORER_ACTION_EXECUTE
plugin id: apk-inspector
engine: explorer-action
variant: default
Explorer action id: inspect-android-package
MIME type: Explorer: extension-only; ACTION_VIEW: dedicated Android package MIME types
required host build: 5269
supported ABIs: unrestricted (supportedAbis = emptyArray())
```

版本 1 只執行檢查. 外掛程式不包含安裝按鈕, 安裝權限, 套件安裝程式, 來源檔案編輯器或目錄列舉. 宿主安裝流程保持獨立. 外掛程式不可用時, AutoJs6 使用宿主降級動作.

外掛程式完全使用 JVM 實作且不包含原生程式庫. 外掛程式宣告 supportedAbis = emptyArray(), 並發行為一個 ABI 無關 APK. 需要 AutoJs6 宿主組建 5269 或更高版本.

******

### 安全性

******

受保護 Explorer 閘道驗證協定 v2, 主檔案頁 surface, 動作 ID, content URI 階層, 精確 ClipData, 檔案名稱, 副檔名, MIME 類型, 大小及唯讀授權. 公開 ACTION_VIEW 閘道只接收專用套件 MIME 類型. 輸入只複製一次到有界的唯讀私有快照, 同時計算 SHA-256. 外掛程式不會列舉協定中的父 URI.

******

### 安全限制

******

- 最大輸入大小: 4 GiB.
- 每次動作只接收一個目標檔案, 並保留至少 128 MiB 快取空間.
- 封存項目數, 名稱, 宣告大小, 總大小, 巢狀 APK 掃描, 中繼資料, protobuf 及 Manifest 輸出均有上限.
- 外部 ACTION_VIEW 拒絕 application/zip, application/octet-stream, 寫入, 永久及前綴授權.
- V1-V3 只偵測簽章方案是否存在. V4 需要獨立 idsig 輸入, 不屬於目前協定.
- 外掛程式從不安裝套件, 且不要求儲存空間, 網路或套件安裝權限.

******

### 版本記錄

******

# v1.0.0

###### 2026/08/02

* `新增` APK 檢查器外掛, 外掛 ID 為 `apk-inspector`, 動作 ID 為 `inspect-android-package`, 引擎為 `explorer-action`, 變體為 `default`
* `新增` 透過 Explorer Action 通訊協定 v2 為 APK, APKS, XAPK, APKM, APKZ 和 AAB 檔案提供主要唯讀檢查動作
* `新增` 唯讀解碼文字和二進位 APK 資訊清單, AAB protobuf 資訊清單及 bundletool `toc.pb` 中繼資料
* `新增` 套件詳細資料, 要求權限, 元件, 裝置相符分割, OBB 資源, 結構發現, 格式化資訊清單檢視和 APK V1-V3 簽章配置存在性偵測
* `新增` 相互分離的受保護檔案瀏覽器入口和精確 MIME Android `ACTION_VIEW` 入口, 4 GiB 輸入上限及計算 SHA-256 的有界私人唯讀快照
* `新增` 純 JVM 實作且不包含原生程式庫, 透過 `supportedAbis = emptyArray()` 宣告 ABI 無限制, 發布單一 ABI 無關 APK, 要求 AutoJs6 主程式組建版本 5269
* `新增` 外掛中繼資料, 介面文字, 使用說明, README 和 CHANGELOG 的多語言資源: 西班牙文/法文/俄文/阿拉伯文/日文/韓文/英文/簡體中文/香港繁體/台灣繁體
* `依賴` 附加 Gson 版本 2.13.2

##### 查看更多版本

* [CHANGELOG-zh-Hant-TW.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hant-TW.md)

******

### 組建

******

```powershell
.\gradlew.bat :app:assembleDebug
```

發行組建:

```powershell
.\gradlew.bat :app:assembleRelease
```

組建參數來自 version.properties. 目前最低 SDK 為 24, 目標 SDK 為 36.

******

### 資源結構

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
```

strings.xml 本地化外掛程式中繼資料與介面文字. plugin_instruction.md 提供宿主顯示的使用說明. .python/generate_markdown.py 根據 JSON 來源產生多語言 README 及更新日誌.

******

### 連結

******

- AutoJs6 文件: https://docs.autojs6.com
- Android 安全檔案分享: https://developer.android.com/training/secure-file-sharing
