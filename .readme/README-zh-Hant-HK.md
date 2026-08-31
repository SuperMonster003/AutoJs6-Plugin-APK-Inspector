<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="APK Inspector" width="128" />
  </p>

  <h1>APK Inspector</h1>

  <p>AutoJs6 檔案管理器插件: 毋須安裝, 點開即可看清 APK 與 AAB 安裝包的版本、權限、簽署與裝置相容性</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-APK-Inspector?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=534BAE&label=License"/></a>
  </p>
</div>

### 語言 (Languages)

README 提供以下語言版本:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hans.md)
- 繁體中文 (香港) [zh-Hant-HK] # 目前
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ar.md)

### 項目簡介

APK 檢查器 (APK Inspector) 是 AutoJs6 檔案管理器的配套插件. 在檔案管理器中點擊 APK, APKS, XAPK, APKM, APKZ 或 AAB 檔案, 即可直接開啟一份安裝包檢查報告: 應用叫甚麼、是甚麼版本、要哪些權限、能否在目前裝置安裝, 一屏看清. 全程毋須安裝該套件, 插件亦絕不修改原始檔案.

檢查報告分為四個部分: 「套件詳情」顯示應用名稱、圖示、套件名稱、版本、SDK 範圍、簽署驗證與簽署證書輪換鏈、檔案大小及 SHA-256 校驗值; 「元件」列出包內全部 APK 分包與 OBB 資源, 標出與目前裝置匹配的部分 (AAB 則列出模組), 按 activity/別名、service、receiver 與 provider 分組統計資訊清單聲明及其明確 exported 狀態, 按 ABI 匯總原生 .so 程式庫的未壓縮大小及裝置匹配情況, 並列出標準 classes*.dex 檔案及其未壓縮大小; 「要求的權限」按保護等級分組列出應用申請的系統權限, 將執行階段危險權限置頂並附簡短說明; 「安全和相容性發現」給出結構問題與裝置相容性結論. 報告頁的「檢視資訊清單」按鈕還可開啟格式化後的 AndroidManifest 全文.

### 功能亮點

- 大字體與緊湊螢幕配置: 在 1.5x / 2.0x 字體倍率下, 空間不足時報告頁首會自動縱向排列, 緊湊工具列標題保持完整可見, 長 SHA-256 與權限名稱不省略並正確換行, 橫向資訊清單搜尋不會進入輸入法全螢幕擷取介面.
- 自適應外觀: 報告及清單檢視頁跟隨系統淺色/深色設定; Android 12 或以上亦會由 Material You 按桌布產生配色, 並以語義色彩及對比度感知的系統列圖示保持兩頁清晰易讀.
- 容器元數據: 在 1 MiB 上限內讀取 SAI APKS、XAPK 與 APKMirror APKM 元數據, 顯示打包工具/格式版本、元數據聲明的應用程式版本和實際存在的圖示條目, 且不會覆蓋 APK 資訊清單中的事實.
- 點擊即查: 在 AutoJs6 檔案管理器中點擊安裝包檔案即可開啟檢查報告, 毋須安裝、解壓或連網.
- 六種格式: 支援標準 APK、多分包集合格式 (APKS, XAPK, APKM, APKZ) 以及應用商店發佈格式 AAB; APKS 同時兼容 bundletool 與 SAI 兩種匯出.
- 版本與相容性: 顯示套件名稱、版本名與版本號、最低/目標/最高 SDK, 並與目前裝置系統版本對照, 安裝前即可判斷相容性.
- 權限透明: 按保護等級把要求的權限分為執行階段/危險、簽名/受保護和一般三組, 高亮置頂執行階段權限並附一句說明; 無法解析的等級仍會顯示並清楚標示.
- 分包分析: 列出集合包內全部 APK 條目與 OBB 資料包, 標出為目前裝置選中的分包 (基礎包、語言、螢幕密度、架構等).
- 裝置配置模擬: 在集合包報告內切換語言、螢幕密度與 ABI, 插件會針對同一私有快照在本機重新運行有界選擇器, 並列出相對實際裝置新增或移除的 APK 分包.
- AAB 配置與發佈: 有界解析 BundleConfig.pb 設定, 並為 base、feature、asset、ML、AI 及 SDK 模組標示安裝時、有條件、按需要、快速跟進、舊裝置融合和可移除發佈中繼資料; 設定或模組清單損壞/超限只會令相應標示降級.
- 資源表身分回退: 當 Android 無法直接載入 AAB 或超限集合包時, 毋須提取整個巢狀 APK, 即可按目前裝置語言及密度從 AAB resources.pb 或 APK resources.arsc 解析應用程式名稱和點陣圖示.
- 元件暴露概覽: 匯總已選 APK 分包或已掃描 AAB 模組中的 activity/別名、service、receiver 與 provider, 將明確 android:exported 分為已匯出、未匯出或未聲明/無法解析.
- 原生程式庫概覽: 按 ABI 及未壓縮大小匯總已選 APK 分包或 AAB 模組中的 .so 檔案, 標示裝置首選 ABI、支援的後備 ABI 和不支援的架構, 全程不提取程式庫內容.
- DEX 概覽: 按自然順序列出已選 APK 分包或 AAB 模組中的標準 classes*.dex 檔案, 顯示每個檔案及未壓縮總大小, 不提取、解碼或反編譯 DEX 內容.
- 報告重用: 長按套件詳情中的任何核心欄位可複製原始值; 亦可透過 Android 系統分享面板傳送與畫面一致的 `text/plain` 報告, 文字只保留在記憶體, 不建立檔案或申請儲存權限.
- 簽署與證書驗證: 對 APK V2 / V3 / V3.1 簽署執行真正密碼學驗證, 報告 V1 是否存在; 逐一顯示目前簽署證書, 並標註已驗證輪換鏈中的新舊關係與 SHA-256 指紋.
- V4 / V4.1 同級簽署驗證: AutoJs6 只派生精確同級檔案 `<APK 檔案名稱>.idsig` 並授予有界唯讀描述符; 插件驗證簽署資料、證書與公鑰、對應 V2 / V3 APK 摘要、fs-verity 根、內嵌 Merkle 樹及 V3.1 輪換簽署者.
- 資訊清單可讀化: 自動把二進制 APK 清單與 AAB protobuf 清單解碼為可讀 XML, 在獨立唯讀頁面顯示行號及語義語法高亮, 並提供有界、不分大小寫的文字尋找、相符項目高亮及上一個/下一個跳轉.
- TalkBack 及 RTL 無障礙: 報告分區提供標題語義, 所有圖示操作均有朗讀標籤, 自訂互動列符合 48 dp 觸控目標, 動態結果會被讀出, 阿拉伯語介面按語言方向鏡像.
- 完整性核對: 讀取檔案的同時計算 SHA-256, 方便與官方發佈的校驗值比對.
- 結構體檢: 自動發現缺失基礎包、分包重複或依賴缺失、版本與套件名稱不一致等結構問題, 並區分阻斷性 [!] 與提示性 [i].

### 使用方法

1. 從 Releases 下載並安裝 APK 檢查器, 然後在 AutoJs6 的插件中心啟用它 (需要 AutoJs6 版本代碼 5277 或更高).
2. 開啟 AutoJs6 的檔案管理器, 找到想檢視的安裝包檔案 (APK, APKS, XAPK, APKM, APKZ 或 AAB).
3. 點擊該檔案, 或在檔案選單中選擇「檢查 Android 套件」, 稍候片刻即可看到檢查報告.
4. 自上而下檢視應用圖示與名稱、套件詳情、元件、要求的權限, 以及安全和相容性發現.
5. 檢視 APKS、XAPK、APKM 或 APKZ 時, 選擇語言、螢幕密度與 ABI 並套用模擬, 即可把所選 APK 與實際裝置結果對照.
6. 長按「套件詳情」中的任何欄位可複製其值; 點按工具列的「分享報告」可透過 Android 系統分享面板傳送與畫面一致的文字.
7. 點按「檢視資訊清單」可閱讀完整的 AndroidManifest; 看完後按返回鍵即可回到檔案管理器.

> 其他應用亦可以透過系統「開啟方式」(ACTION_VIEW) 調起 APK 檢查器, 前提是以 content 位址與專用安裝包 MIME 類型發起請求. 插件始終唯讀, 不提供任何安裝入口.

### 支援格式

檔案管理器主要動作精確匹配以下副檔名 (不區分大小寫):

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

其中 APKS, XAPK, APKM 與 APKZ 是包含多個分包 APK 的集合格式; AAB 是提交應用商店的 App Bundle 格式, 只能檢視內容, 需經 bundletool 等工具轉換後才能安裝.

### 常見問題

#### 這個插件能安裝 APK 嗎?

不能, 這是刻意的設計. 插件不申請安裝權限, 介面中沒有任何安裝入口, 它的定位是在安裝前幫你看清安裝包內容. 安裝請繼續使用系統安裝器或宿主自身的流程.

#### 為甚麼某些檔案無法檢查?

常見原因包括: 檔案超過 4 GiB 上限; 裝置快取空間不足 (需預留至少 128 MiB); 集合包條目數量或體積超出解析上限; 檔案在讀取過程中被其他應用改動; 或檔案本身結構損壞. 錯誤提示會說明具體原因.

#### 簽署檢測結果能證明安裝包安全嗎?

不能. 插件會驗證 V2 / V3 / V3.1 / V4 / V4.1 的套件完整性與簽署者證明, 並顯示證書指紋和輪換鏈; 但有效簽署只能證明檔案自該簽署者簽署後未被修改, 不能證明簽署者或應用本身可信. 請再與官方下載渠道公布的證書指紋和 SHA-256 對照.

#### 為甚麼 AAB 檔案會提示「需要轉換後才能安裝」?

AAB 是面向應用商店的發佈格式, Android 裝置無法直接安裝. 插件可以解析它的 protobuf 清單與模組結構, 並透過 resources.pb 解析本地化應用程式名稱及密度配對的點陣圖示供檢視; 安裝仍需先用 bundletool 等工具轉換為 APK 或 APKS.

### 權限與安全

插件不申請儲存、網絡或安裝套件權限. 目標套件只能透過宿主授予的臨時唯讀 content 位址存取; 可選 `.idsig` 只能透過宿主為精確 `<APK 檔案名稱>.idsig` 派生的有界唯讀描述符存取, 不提供目錄清單或任意同級路徑. 檢查開始前, 兩份輸入都會被複製為應用私人快取中的唯讀快照 (複製套件的同時計算 SHA-256), 全部解析只發生在快照上; `.idsig` 上限為 40 MiB, 過期快照最遲 24 小時後自動清理. 來自檔案管理器的請求會逐項校驗協議版本、請求與動作標識、目標中繼資料、宿主版本、URI 結構、檔案名稱、大小、唯讀授權及宿主會話 Binder, 任一不符即拒絕; 來自其他應用的「開啟方式」請求只接受專用套件 MIME 類型, 拒絕 application/zip、application/octet-stream 以及任何寫入、持久化或前綴授權.

為防止惡意構造的檔案耗盡裝置資源, 解析設有以下上限, 超限檔案會被拒絕並給出提示:

- 單個檔案最大 4 GiB, 複製時快取至少保留 128 MiB 可用空間; 每次動作只處理一個目標檔案.
- 封存最多解析 16384 個條目, 集合包最多掃描 512 個 APK 分包, 條目名稱最長 1024 字符.
- 聲明的單條目大小不超過 4 GiB, 聲明總大小不超過 8 GiB.
- 巢狀 APK 清單掃描不超過 256 MiB, 集合包中繼資料不超過 1 MiB, 用於載入圖示與標籤的臨時 APK 不超過 512 MiB.
- AAB BundleConfig.pb 不超過 1 MiB. 發佈標示與 AAB 組件統計共用 128 份清單 / 16 MiB 掃描預算, 每個模組最多保留 128 個條件值; 超限或損壞中繼資料會互相隔離並清楚標示.
- 資源表回退中每份資源表最多讀取 32 MiB, 每個圖示最多讀取 4 MiB; 在巢狀 APK 中定位資源表及圖示共用 512 MiB 掃描預算. 超限、資源損壞或引用無法解析時只會停用相應回退並清楚標示.
- 權限分類最多掃描 2048 項要求、顯示 512 個安全且去重的名稱, 每條系統說明最多 240 個字元; 省略項目和無法取得的保護等級都會清楚標示.
- 元件統計每份資訊清單最多掃描 4096 項聲明; AAB 最多掃描 128 份模組資訊清單, 共用 16 MiB 輸入預算. 省略項目、無法解析的 exported 值及單份資訊清單失敗都會清楚標示.
- 原生程式庫統計最多保留 4096 個 .so 條目並顯示 64 個 ABI 目錄; 最多讀取 512 個已選巢狀 APK, 共用 256 MiB 輸入預算, 每個 APK 最多保留 8 MiB 中央目錄資料. 超限和失敗只會產生清楚標示的不完整結果.
- DEX 統計會計入已掃描的全部標準條目, 但最多顯示 128 條自然排序路徑; 與原生程式庫概覽共用同一次有界中央目錄掃描, 不提取、解碼或反編譯 DEX 內容.

### 插件介面

宿主 (AutoJs6) 透過以下標識發現並調用插件, 供插件或宿主開發者參考:

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

目前版本只執行唯讀檢查: 沒有安裝按鈕、安裝權限或套件安裝器, 不修改來源檔案, 亦不枚舉目錄. V4 只使用宿主精確派生的 `.idsig` 候選, 將其有界唯讀描述符複製為私人快照, 並在暫存後立即關閉宿主會話. 插件未安裝或被停用時, 宿主自動回退到預設動作, 互不影響.

### Roadmap

已實現能力以上文與 Roadmap 勾選條目為準; 集合包與 AAB 深化等後續計劃集中維護在 Roadmap 中, 未勾選條目不代表目前版本已支援.

- [ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/ROADMAP.md)

### 版本記錄

#### v1.1.0

_2026/08/30_

- `提示` Explorer Action 協議 v22 與有界 V4 同級檔案存取要求 AutoJs6 版本代碼不低於 5277
- `新增` 新增適配 1.5x/2.0x 字體、橫向和 320 dp 窄螢幕的響應式配置: 空間不足時報告頁首自動縱向排列, 緊湊工具列標題保持完整, 長 SHA-256 與權限名稱不省略並正確換行, 資訊清單搜尋避免輸入法全螢幕擷取介面
- `新增` 新增報告及資訊清單檢視頁的 TalkBack 和 RTL 無障礙支援: 兼容舊版系統的標題語義、圖示操作朗讀標籤、48 dp 自訂觸控目標、動態結果讀出、跟隨語言的版面方向及阿拉伯語返回箭嘴鏡像
- `新增` 為唯讀資訊清單檢視頁新增行號、配合 Material 主題的 XML 語法高亮及有界、不分大小寫的文字尋找; 支援相符項目高亮、上一個/下一個循環跳轉和狀態恢復
- `新增` 新增跟隨系統的 Material 3 淺色/深色主題, 報告及清單檢視頁同步適配; Android 12 或以上啟用 Material You 動態取色, 系統列圖示會按背景對比度自動調整
- `新增` 新增有界容器元數據摘要: 解析 SAI APKS 的 `meta.sai_v1/v2.json`、XAPK 的 `manifest.json` 與 APKMirror APKM 的 `info.json`, 顯示打包工具/格式版本、元數據聲明的應用程式版本及實際存在的圖示條目; 損壞或超過 1 MiB 的元數據只影響該摘要並顯示提示
- `新增` 新增 APKS、XAPK、APKM 與 APKZ 報告內裝置配置模擬: 切換語言、螢幕密度和 ABI 後, 針對同一私有快照在本機重新執行有界分包選擇, 顯示相容/無效狀態及相對實際裝置新增或移除的 APK; 主要報告仍以實際裝置為準
- `新增` 新增有界 AAB 配置與發佈中繼資料: 解析 BundleConfig.pb 的 bundletool/type/split/compression/optimization 設定, 並為 base、feature、asset、ML、AI 及 SDK 模組標示安裝時、有條件、按需要、快速跟進、舊裝置融合和可移除發佈; 損壞、超限或略過的中繼資料會互相隔離並清楚標示
- `新增` 新增有界資源表應用程式名稱及點陣圖示回退: 按目前語言及密度解析 AAB resources.pb 和 APK resources.arsc, 毋須提取超限巢狀 APK; 資源表、圖示及掃描超限失敗相互隔離並清楚標示
- `新增` 新增長按複製套件詳情核心欄位的原始值, 並透過 Android 系統分享面板傳送與畫面一致的純文字報告; 分享內容只保留在記憶體, 不申請儲存權限或建立檔案
- `新增` 新增有界 DEX 概覽: 按自然順序列出已選 APK 分包和 AAB 模組中的標準 classes*.dex 檔案及其單項與未壓縮總大小, 與原生程式庫共用中央目錄掃描, 不提取、解碼或反編譯 DEX 內容
- `新增` 新增有界原生程式庫概覽: 按 ABI 及未壓縮大小匯總已選 APK 分包和 AAB 模組中的 .so 檔案, 標示裝置首選、後備相容及不支援的 ABI, 全程不提取程式庫內容
- `新增` 新增有界資訊清單元件統計: 匯總已選 APK 分包和已掃描 AAB 模組中的 activity/別名、service、receiver 與 provider, 按明確 android:exported 狀態分組並標示不完整結果
- `新增` 新增 APK V2、V3、V3.1、V4 與 V4.1 的裝置端密碼學驗證, 覆蓋內容摘要、簽署者證明、fs-verity 根、內嵌 Merkle 樹及互補簽署方案配對
- `新增` 新增詳細簽署證書欄位與已驗證證書輪換鏈, 顯示舊/目前角色、能力標誌和 SHA-256 指紋
- `新增` 新增透過宿主精確派生唯讀描述符暫存 `.idsig` 的有界鏈路; 仍不提供目錄枚舉或任意同級檔案存取
- `新增` 新增按 `protectionLevel` 將要求的權限分為執行階段/危險、簽名/受保護和一般三組; 執行階段權限高亮置頂並附有界的一句說明, 無法取得的等級仍會顯示並清楚標示
- `修復` 修正切換語言、主題或其他 Activity 重建後「檢視資訊清單」操作消失的問題; 現在會安全替換先前的唯讀私人資訊清單快照
- `修復` 修正緊湊屏幕或大字體下軟鍵盤壓縮資訊清單搜尋面板並導致其無法到達的問題; 現在會待用戶點擊已聚焦的搜尋欄後才顯示輸入法
- `優化` 強化 Explorer Action v22 請求校驗與不可變私人快照, 設定 4 GiB 套件上限、40 MiB idsig 上限、檔案身分複核並及時關閉宿主會話
- `優化` 新增由 Build Tools 37 `apksigner` 產生的有效、破壞、多簽署者、V3.1 輪換、V4.1 輪換、缺失及格式損壞樣本矩陣
- `優化` 新增真實安裝套件分區隔離矩陣, 覆蓋權限、資訊清單元件、原生程式庫及 DEX 的生產上限, 以及資源表超限和巢狀中央目錄損壞; 每個樣本均斷言未受影響分區保持完整, partial 提示在純文字分享中原樣保留
- `優化` 新增可重現的 bundletool 1.18.2 選擇金標矩陣: 從去私隱化最小 AAB 執行 `build-apks`, 並以 `install-apks` 共用的 `ExtractApksCommand` 為三組語言/密度/ABI 裝置規格記錄安裝分包; 單元測試逐項驗證模擬集合無重複且重複執行結果穩定
- `優化` 新增可重複執行的 36 組合介面截圖覆核矩陣, 涵蓋 411/320 dp 直屏、緊湊橫屏、1.0x/1.5x/2.0x 字體、明暗主題及 LTR/RTL; 保留裝置狀態的 ADB 執行器會審核控制項可達性、無障礙朗讀次序與 48 dp 觸控目標, 並產生 PNG/XML/縮圖索引證據
- `優化` 新增可重現的 24 項去私隱化樣本矩陣, 涵蓋 APK/APKS/XAPK/APKM/APKZ/AAB 的正常、結構損壞、超限及裝置不相容輸入; 純標準庫產生器、SHA-256 清單及 JVM 契約測試逐項驗證位元組重現、解析結果、儲存庫大小及不含程式碼、簽署材料和用戶資料
- `優化` 新增完整失敗即拒絕單元測試矩陣, 涵蓋軟件包請求驗證、私人快取暫存、Android 封存檔防護及 APK 簽署區塊解析; 逐項觸發每個列舉拒絕原因, 包括格式錯誤的元數據、不安全路徑、資源上限、截斷結構及取消
- `優化` 新增兩個已匯出入口 Activity 的 Robolectric 安全迴歸測試, 涵蓋偽造動作、越權 URI 授權、超過 4 GiB 的聲明及並行生命週期取消; 被拒請求不會啟動檢查頁或開啟超大內容, Explorer 宿主工作階段只會關閉一次

#### v1.0.1

_2026/08/08_

- `修復` 修復插件在插件中心啟用後宿主無法綁定服務的問題; 現在啟用後「檢查 Android 套件」動作立即可用
- `優化` 精簡插件名稱與描述, 使用戶文檔表述更自然易讀

#### v1.0.0

_2026/08/02_

- `提示` 首個公開版本, 需要 AutoJs6 版本代碼 5269 或更高
- `新增` 在 AutoJs6 檔案管理器中點擊 APK, APKS, XAPK, APKM, APKZ 或 AAB 檔案, 即可開啟唯讀檢查報告 (插件 ID `apk-inspector`, 動作 ID `inspect-android-package`)
- `新增` 檢查報告展示應用名稱與圖示、套件名稱、版本、SDK 範圍、要求的權限、分包與 OBB 資源、結構問題, 以及 V1-V3 簽署方案存在性
- `新增` 自動解碼文字與二進制 APK 清單、AAB protobuf 清單及 bundletool `toc.pb` 中繼資料, 並提供格式化清單的獨立檢視頁面
- `新增` 支援其他應用透過系統「開啟方式」(ACTION_VIEW) 以專用安裝包 MIME 類型調起檢查
- `新增` 檢查前先把檔案複製為帶 SHA-256 校驗的唯讀私人快照 (上限 4 GiB); 插件不申請儲存、網絡或安裝套件權限
- `新增` 內置 10 種語言的介面文字、使用說明、README 與 CHANGELOG: 簡體中文、香港繁體、台灣繁體、英語、法語、西班牙語、日語、韓語、俄語、阿拉伯語
- `依賴` 引入 Gson 2.13.2

##### 完整記錄

- [CHANGELOG-zh-Hant-HK.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hant-HK.md)

### 構建

```powershell
.\gradlew.bat :app:assembleDebug
```

Release 構建:

```powershell
.\gradlew.bat :app:assembleRelease
```

構建與簽署參數由 version.properties 與 sign.properties 控制; 目前最低支援 Android 7.0 (SDK 24), 目標 SDK 36.

README 與 CHANGELOG 均由 .python/generate_markdown.py 依據 .readme/ 與 .changelog/ 下的 JSON 語言資源和模板生成 (共 10 種語言). 修改文檔請編輯對應 JSON 後重新運行腳本, 不要直接改動生成的 Markdown.

### 相關連結

- AutoJs6 文檔: https://docs.autojs6.com
- Android 安全檔案分享: https://developer.android.com/training/secure-file-sharing
