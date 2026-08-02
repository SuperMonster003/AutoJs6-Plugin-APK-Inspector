******

### 版本記錄

******

# v1.0.0

###### 2026/08/02

* `新增` APK 檢查器外掛程式, 外掛程式 ID 為 `apk-inspector`, 動作 ID 為 `inspect-android-package`, 引擎為 `explorer-action`, 變體為 `default`
* `新增` 透過 Explorer Action 通訊協定 v2 為 APK, APKS, XAPK, APKM, APKZ 和 AAB 檔案提供主要唯讀檢查動作
* `新增` 唯讀解碼文字和二進制 APK 資訊清單, AAB protobuf 資訊清單及 bundletool `toc.pb` 中繼資料
* `新增` 套件詳情, 要求權限, 元件, 裝置配對分割, OBB 資源, 結構發現, 格式化資訊清單檢視和 APK V1-V3 簽章配置存在性偵測
* `新增` 互相分離的受保護檔案瀏覽器入口和精確 MIME Android `ACTION_VIEW` 入口, 4 GiB 輸入上限及計算 SHA-256 的有界私人唯讀快照
* `新增` 純 JVM 實作且不包含原生程式庫, 透過 `supportedAbis = emptyArray()` 宣告 ABI 無限制, 發佈單一 ABI 無關 APK, 要求 AutoJs6 主程式組建版本 5269
* `新增` 外掛程式中繼資料, 介面文字, 使用說明, README 和 CHANGELOG 的多語言資源: 西班牙文/法文/俄文/阿拉伯文/日文/韓文/英文/簡體中文/香港繁體/台灣繁體
* `依賴` 附加 Gson 版本 2.13.2
