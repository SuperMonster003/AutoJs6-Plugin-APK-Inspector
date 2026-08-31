<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="APK Inspector" width="128" />
  </p>

  <h1>APK Inspector</h1>

  <p>AutoJs6 ファイルマネージャープラグイン: APKやAABファイルをタップするだけで, インストールせずにバージョン・権限・署名・端末互換性を確認</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-APK-Inspector?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=534BAE&label=License"/></a>
  </p>
</div>

### 言語 (Languages)

このREADMEは次の言語で利用できます:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-es.md)
- 日本語 [ja] # 現在
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ar.md)

### 概要

APK Inspectorは, AutoJs6ファイルマネージャーの拡張プラグインです. ファイルマネージャーでAPK, APKS, XAPK, APKM, APKZ, AABファイルをタップすると, 検査レポートがすぐに開きます. アプリの名前, バージョン, 要求する権限, この端末にインストールできるかどうかが1画面で分かります. パッケージがインストールされることはなく, 元のファイルも一切変更されません.

レポートは4つのセクションで構成されます. 「パッケージ詳細」にはアプリ名, アイコン, パッケージ名, バージョン, SDK範囲, 署名検証と署名証明書のローテーション系譜, ファイルサイズ, SHA-256チェックサムが表示されます. 「コンポーネント」にはパッケージ内の全分割APKとOBBアセットが一覧表示され, この端末に適合する部分が示されます (AABの場合はモジュール一覧). さらにmanifestで宣言されたactivity/alias, service, receiver, providerを件数と明示的なexported状態で分類し, ネイティブ.soライブラリをABI、非圧縮サイズ、端末対応状況で集計し, 標準のclasses*.dexファイルと非圧縮サイズを一覧表示します. 「要求権限」は保護レベル別に権限を分類し, 実行時の危険な権限を説明付きで先頭に強調表示します. 「セキュリティと互換性の結果」には構造上の問題と端末互換性の判定がまとまります. 「manifestを表示」ボタンで整形済みAndroidManifest全文も確認できます.

### 特長

- 適応型表示: レポートとマニフェストビューアーはシステムのライト/ダーク設定に追従し、Android 12以降ではMaterial Youが壁紙から配色を生成します。セマンティックカラーとコントラスト対応のシステムバーアイコンで両画面の読みやすさを維持します。
- コンテナメタデータ: SAI APKS、XAPK、APKMirror APKM のメタデータを 1 MiB 上限で読み取り、パッケージ作成ツール/形式バージョン、メタデータ宣言のアプリバージョン、実在するアイコンエントリを表示し、APK manifest の事実は上書きしません。
- タップするだけ: AutoJs6ファイルマネージャーから直接レポートを開けます. インストール, 展開, ネットワークアクセスは不要です.
- 6形式に対応: 標準APK, 複数分割のバンドル形式 (APKS, XAPK, APKM, APKZ), ストア配布形式AABに対応. APKSはbundletoolとSAIの両方の書き出しを扱えます.
- バージョンと互換性: パッケージ名, バージョン名とバージョンコード, 最小/ターゲット/最大SDKを表示し, 端末のAndroidバージョンと対比します.
- 権限の透明化: 要求権限を保護レベル (実行時/危険, 署名/保護, 通常) で分類し, 実行時権限を先頭に強調して1行の説明を表示します. 取得できないレベルも非表示にせず明記します.
- 分割APKの分析: バンドル内の全APKエントリとOBBアセットを一覧にし, この端末向けに選択される分割 (ベース, 言語, 画面密度, ABI) を示します.
- 端末構成シミュレーション: バンドルのレポート内で言語、画面密度、ABIを切り替えると, 同じ非公開スナップショットに対して上限付き選択処理を端末内で再実行し, 実際の端末から追加または削除されるAPKを表示します.
- AAB 構成と配信: BundleConfig.pb を上限付きでデコードし, base、feature、asset、ML、AI、SDK モジュールにインストール時、条件付き、オンデマンド、fast-follow、融合、削除可能の配信メタデータを付記します. 破損または上限超過の構成やモジュールmanifestは, 対応する注記だけを降格させます.
- リソースによるアプリ識別フォールバック: AndroidがAABまたは上限超過バンドルを直接読み込めない場合, ネストAPK全体を展開せずに, AABのresources.pbまたはAPKのresources.arscから現在の言語と密度に合うアプリ名とラスターアイコンを解決します.
- manifestコンポーネントの公開状態: 選択済みAPK分割または走査済みAABモジュールのactivity/alias, service, broadcast receiver, content providerを集計し, 明示的なandroid:exported値をエクスポート、非エクスポート、未指定/未解決に分類します.
- ネイティブライブラリ概要: 選択済みAPK分割またはAABモジュールの.soファイルをABIと非圧縮サイズで集計し, 端末の優先ABI、対応する代替ABI、非対応アーキテクチャを表示します. ライブラリ内容は展開しません.
- DEX概要: 選択済みAPK分割またはAABモジュールの標準classes*.dexファイルを自然順で一覧にし, ファイルごとと合計の非圧縮サイズを表示します. DEX内容の展開、デコード、逆コンパイルは行いません.
- レポートの再利用: パッケージ詳細の主要行を長押しすると値だけをコピーでき, Android共有シートから画面と同一の`text/plain`レポートを共有できます. テキストはメモリ内だけに保持され, ファイル作成やストレージ権限は不要です.
- 署名と証明書の検証: APKのV2 / V3 / V3.1署名を暗号学的に検証し, V1の有無を報告します. 現在の署名証明書を個別に表示し, 検証済みローテーション系譜の新旧関係とSHA-256指紋も示します.
- V4 / V4.1サイドカー検証: AutoJs6は完全一致する`<APKファイル名>.idsig`だけを派生し, 上限付き読み取り専用ディスクリプターを付与します. プラグインは署名データ, 証明書と公開鍵, 対応するV2 / V3 APKダイジェスト, fs-verityルート, 内蔵Merkleツリー, V3.1ローテーション署名者を検証します.
- manifestの可読化: バイナリAPK manifestとAAB protobuf manifestを読みやすいXMLへデコードし, 行番号とセマンティックな構文強調を備えた独立した読み取り専用ビューアーで表示します. 大文字/小文字を区別しない上限付き検索、該当箇所の強調、前/次への移動にも対応します.
- 完全性の確認: ファイル読み取りと同時にSHA-256を計算し, 公式に公開されたチェックサムとすぐ比較できます.
- 構造チェック: ベースAPKの欠落, 分割の重複や依存欠落, バージョンやパッケージ名の不一致などを検出し, 阻害要因 [!] と参考情報 [i] を区別して表示します.

### 使い方

1. APK Inspectorをダウンロードしてインストールし, AutoJs6のプラグインセンターで有効化します (AutoJs6バージョンコード 5277 以降が必要).
2. AutoJs6のファイルマネージャーを開き, 確認したいパッケージファイル (APK, APKS, XAPK, APKM, APKZ, AAB) を探します.
3. ファイルをタップするか, メニューから「Androidパッケージを検査」を選ぶと, まもなく検査レポートが表示されます.
4. アプリのアイコンと名前, パッケージ詳細, コンポーネント, 要求権限, セキュリティと互換性の結果を上から順に確認します.
5. APKS、XAPK、APKM、APKZでは, 言語、画面密度、ABIを選んでシミュレーションを適用し, 選択APKを実際の端末と比較できます.
6. 「パッケージ詳細」の行を長押しすると値をコピーできます. ツールバーの「レポートを共有」をタップすると, 画面と同じテキストをAndroid共有シートから送信できます.
7. 「manifestを表示」をタップするとAndroidManifest全文を読めます. 戻るボタンでファイルマネージャーに戻ります.

> 他のアプリからも, content URIと専用のAndroidパッケージMIMEタイプを使えば, システムの「他のアプリで開く」(ACTION_VIEW) 経由でAPK Inspectorに渡せます. プラグインは常に読み取り専用で, インストール手段は一切提供しません.

### 対応形式

ファイルマネージャーの主要アクションは次の拡張子と完全一致します (大文字小文字は区別しません):

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

APKS, XAPK, APKM, APKZは複数の分割APKをまとめたコンテナ形式です. AABはアプリストア提出用のApp Bundle形式で, ここで内容を確認できますが, インストールにはbundletoolなどでのAPK(S)変換が必要です.

### よくある質問

#### このプラグインでAPKをインストールできますか?

できません. これは意図的な設計です. プラグインはインストール権限を要求せず, 画面のどこにもインストールボタンがありません. 役割はインストール前にパッケージの中身を確認することです. インストールはシステムのインストーラーやホスト自身のフローで行ってください.

#### 検査できないファイルがあるのはなぜですか?

主な原因: ファイルが 4 GiB の上限を超えている, 端末のキャッシュ空き容量が不足している (最低 128 MiB が必要), バンドルのエントリ数やサイズが解析上限を超えている, 読み取り中に他のアプリがファイルを変更した, ファイル自体の構造が壊れている, など. エラーメッセージに具体的な理由が表示されます.

#### 署名検出の結果はパッケージの安全性を証明しますか?

いいえ. プラグインはV2 / V3 / V3.1 / V4 / V4.1のパッケージ整合性と署名者証明を暗号学的に検証し, 証明書指紋とローテーション系譜を表示します. ただし有効な署名が証明するのは署名後にパッケージが変更されていないことだけで, 署名者やアプリ自体の信頼性ではありません. 指紋とSHA-256を公式配布元と照合してください.

#### AABファイルに「インストール前に変換が必要」と表示されるのはなぜですか?

AABはアプリストア向けの配布形式で, Android端末に直接インストールできません. プラグインはprotobuf manifestとモジュール構造をデコードし, resources.pbからローカライズ済みアプリ名と密度に合うラスターアイコンを解決して表示できますが, インストールには引き続きbundletoolなどでAPK(S)へ変換する必要があります.

### 権限とセキュリティ

プラグインはストレージ, ネットワーク, パッケージインストール権限を要求しません. 選択したパッケージにはホストが付与した一時的な読み取り専用content URIだけでアクセスし, 任意の`.idsig`にはホストが完全一致する`<APKファイル名>.idsig`用に生成した上限付き読み取り専用ディスクリプターだけでアクセスします. ディレクトリ列挙や任意の同一フォルダーパスは提供されません. 検査前に両入力をアプリ専用キャッシュの読み取り専用スナップショットへコピーし (パッケージのコピー中にSHA-256を計算), 解析はすべてスナップショット上で行います. `.idsig`は40 MiBまでで, 期限切れスナップショットは24時間以内に削除されます. ファイルマネージャーのリクエストはプロトコル, リクエストIDとアクションID, 対象メタデータ, ホストバージョン, URI形式, 名前, サイズ, 読み取り専用grant, セッションBinderを項目ごとに検証します. 他アプリからの「他のアプリで開く」は専用MIMEタイプだけを受け付け, application/zip, application/octet-stream, 書き込み・永続化・プレフィックスgrantを拒否します.

細工されたファイルによる端末リソースの枯渇を防ぐため, 解析には次の上限があり, 超過したファイルは理由の提示とともに拒否されます:

- 1ファイルの上限は 4 GiB で, コピー時にはキャッシュに 128 MiB 以上の空きが必要です. 1回のアクションで処理する対象ファイルは1つだけです.
- アーカイブのエントリは最大 16384 件, バンドルあたりのAPKエントリ走査は最大 512 件, エントリ名は最長 1024 文字です.
- 宣言されたエントリサイズは 4 GiB まで, 宣言された合計サイズは 8 GiB までです.
- ネストしたAPKのmanifest走査は 256 MiB まで, バンドルメタデータは 1 MiB まで, アイコンとラベルの読み込みに使う一時APKは 512 MiB までです.
- AAB BundleConfig.pb は 1 MiB までです. 配信注記は 128 manifest / 16 MiB の AAB 走査を共有し, モジュールごとに最大 128 件の条件値を保持します. 上限超過や破損メタデータは分離して明記します.
- リソースフォールバックは各テーブルを最大 32 MiB, 各アイコンを最大 4 MiB まで読み取り, ネストAPK内のテーブルとアイコンの探索には共有 512 MiB 予算を使います. 上限超過、不正リソース、未解決参照は該当フォールバックだけを無効にし, 明記します.
- 権限分類は最大 2048 件を走査し, 安全な一意名を最大 512 件表示します. 読み込む説明は1件 240 文字までで, 省略や取得不能な保護レベルを明記します.
- コンポーネント統計はmanifestごとに最大 4096 件, AABでは共有入力予算 16 MiB の範囲で最大 128 個のモジュールmanifestを走査します. 省略, 未解決のexported値, manifest単位の失敗を明記します.
- ネイティブ統計は最大 4096 個の.soエントリを保持し, 64 個のABIディレクトリを表示します. 選択済みネストAPKは最大 512 個を共有入力予算 256 MiB で読み, APKごとの中央ディレクトリ保持量を 8 MiB に制限します. 上限超過や失敗は一部結果として明記します.
- DEX統計は走査した標準エントリをすべて集計しますが, 自然順のパス表示は最大 128 件です. ネイティブライブラリ概要と同じ上限付き中央ディレクトリ走査を共有し, DEX内容の展開、デコード、逆コンパイルは行いません.

### プラグインインターフェース

ホスト (AutoJs6) は次の識別子でプラグインを検出して呼び出します. プラグインやホストの開発者向けの参考情報です:

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

現在のバージョンは読み取り専用の検査だけを行います. インストールボタン, インストール権限, パッケージインストーラーはなく, 元ファイルの変更もディレクトリの列挙も行いません. V4ではホストが正確に派生した`.idsig`候補だけを使い, 上限付き読み取り専用ディスクリプターを専用スナップショットへコピーした直後にホストセッションを閉じます. プラグインが未導入または無効の場合, ホストは既定のアクションへ自動的にフォールバックします.

### Roadmap

実装済みの機能は上記とRoadmapのチェック済み項目のとおりです. バンドルとAAB解析の深化などの計画はRoadmapで管理しており, 未チェック項目は現在の機能ではありません.

- [ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/ROADMAP.md)

### リリース履歴

#### v1.1.0

_2026/08/30_

- `ヒント` Explorer Actionプロトコルv22と上限付きV4サイドカーアクセスにはAutoJs6バージョンコード5277以降が必要です
- `機能` 読み取り専用manifestビューアーに行番号、Materialテーマ対応のXML構文強調、上限付き大文字/小文字非区別検索を追加し、該当箇所の強調、前/次の循環移動、状態復元に対応
- `機能` レポートとマニフェストビューアーにシステム連動のMaterial 3ライト/ダークテーマ、Android 12以降のMaterial You動的配色、背景コントラストに応じるシステムバーアイコンを追加
- `機能` SAI APKS の `meta.sai_v1/v2.json`、XAPK の `manifest.json`、APKMirror APKM の `info.json` に対する上限付きコンテナメタデータ概要を追加し、パッケージ作成ツール/形式バージョン、メタデータ宣言のアプリバージョン、実在するアイコンエントリを表示します。破損または 1 MiB 超のメタデータはこの概要だけを降格させ、明示します
- `機能` APKS、XAPK、APKM、APKZのレポート内に端末構成シミュレーションを追加しました。言語、画面密度、ABIを切り替えると、同じ非公開スナップショットに対して上限付き分割選択を端末内で再実行し、互換/無効状態と実際の端末から追加または削除されるAPKを表示します。メインレポートは実際の端末に基づいたままです
- `機能` 上限付きAAB構成および配信メタデータを追加しました。BundleConfig.pbのbundletool/type/split/compression/optimization設定をデコードし、base、feature、asset、ML、AI、SDKモジュールにインストール時、条件付き、オンデマンド、fast-follow、融合、削除可能の配信を注記します。破損、上限超過、省略されたメタデータは分離して明記します
- `機能` AABのresources.pbとAPKのresources.arscから現在の言語と密度に合うアプリ名とラスターアイコンを解決する上限付きフォールバックを追加しました. 上限超過のネストAPK全体は展開せず, テーブル、アイコン、走査上限の失敗は互いに分離して明記します
- `機能` パッケージ詳細の主要値を長押しでコピーし, 画面と同一のプレーンテキストレポートをAndroid共有シートから送信できるようにしました. 内容はメモリ内だけに保持され, ストレージ権限もファイル作成も不要です
- `機能` 選択済みAPK分割とAABモジュールの標準classes*.dexファイルを自然順で一覧表示し、ファイルごとと合計の非圧縮サイズを示す上限付きDEX概要を追加。ネイティブライブラリの中央ディレクトリ走査を共有し、DEX内容の展開、デコード、逆コンパイルは行いません
- `機能` 選択済みAPK分割とAABモジュールの.soファイルをABIと非圧縮サイズで集計し, 端末の優先、代替対応、非対応ABIを表示する上限付きネイティブライブラリ概要を追加; ライブラリ内容は展開しません
- `機能` 選択済みAPK分割と走査済みAABモジュールのactivity/alias, service, broadcast receiver, content providerを明示的なandroid:exported状態別に集計し, 不完全な結果を明記する上限付きmanifestコンポーネント統計を追加
- `機能` APK V2 / V3 / V3.1 / V4 / V4.1の端末上暗号学的検証を追加し, コンテンツダイジェスト, 署名者証明, fs-verityルート, 内蔵Merkleツリー, 補完署名方式との照合に対応
- `機能` 詳細な署名証明書情報と検証済みローテーション系譜を追加し, 旧/現在の役割, 能力フラグ, SHA-256指紋を表示
- `機能` ホストが正確に派生した読み取り専用ディスクリプターから`.idsig`を上限付きで一時保存; ディレクトリ列挙や任意の同一フォルダーファイルへのアクセスは提供しません
- `機能` 要求権限を`protectionLevel`で実行時/危険・署名/保護・通常に分類; 実行時権限を上限付き1行説明とともに先頭で強調し, 取得不能なレベルも表示して明記
- `改善` Explorer Action v22リクエスト検証と不変の専用スナップショットを強化し, パッケージ4 GiB, idsig 40 MiBの上限, ファイル識別確認, 迅速なホストセッション終了を追加
- `改善` Build Tools 37 `apksigner`による有効, 改変, 複数署名者, V3.1/V4.1ローテーション, 欠落, 不正形式の公式サンプル行列を追加
- `改善` 実パッケージを使うセクション分離マトリクスを追加し, 権限、manifestコンポーネント、ネイティブライブラリ、DEXの本番上限と, リソーステーブル超過、ネスト中央ディレクトリ破損を網羅しました. 各サンプルで影響外セクションの完全性とプレーンテキスト共有でのpartial通知保持を検証します
- `改善` 再現可能な bundletool 1.18.2 選択ゴールデンマトリクスを追加しました. 匿名化した最小 AAB に `build-apks` を実行し, `install-apks` と共通の `ExtractApksCommand` で言語/密度/ABI が異なる3つの端末仕様のインストール APK を記録します. 単体テストでは各シミュレーション集合の一致、重複なし、反復実行時の安定性を検証します

#### v1.0.1

_2026/08/08_

- `修正` プラグインセンターで有効化した後にホストがサービスへバインドできない問題を修正. 有効化後すぐに「Androidパッケージを検査」アクションが使えるようになりました
- `改善` プラグイン名と説明を簡潔にし, ユーザードキュメントをより自然な表現に改善

#### v1.0.0

_2026/08/02_

- `ヒント` 初回公開バージョン. AutoJs6バージョンコード5269以降が必要です
- `機能` AutoJs6ファイルマネージャーでAPK, APKS, XAPK, APKM, APKZ, AABファイルをタップすると読み取り専用の検査レポートを開けます (プラグインID `apk-inspector`, アクションID `inspect-android-package`)
- `機能` レポートにはアプリ名とアイコン, パッケージ名, バージョン, SDK範囲, 要求権限, 分割APKとOBBアセット, 構造上の問題, V1-V3署名方式の有無が表示されます
- `機能` テキスト/バイナリAPK manifest, AAB protobuf manifest, bundletool `toc.pb` メタデータを自動デコードし, 整形済みmanifestの専用ビューアーを提供
- `機能` 他のアプリからも専用のAndroidパッケージMIMEタイプを使い, システムの「他のアプリで開く」(ACTION_VIEW) 経由で検査を起動できます
- `機能` 検査前にファイルをSHA-256計算付きの読み取り専用プライベートスナップショットへコピー (上限4 GiB). プラグインはストレージ, ネットワーク, インストールのいずれの権限も要求しません
- `機能` UIテキスト, 使用説明, README, CHANGELOGを10言語で同梱: 簡体字中国語, 繁体字中国語 (香港/台湾), 英語, フランス語, スペイン語, 日本語, 韓国語, ロシア語, アラビア語
- `依存関係` Gson 2.13.2 を追加

##### 全履歴

- [CHANGELOG-ja.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-ja.md)

### ビルド

```powershell
.\gradlew.bat :app:assembleDebug
```

Releaseビルド:

```powershell
.\gradlew.bat :app:assembleRelease
```

ビルドと署名のパラメーターはversion.propertiesとsign.propertiesで管理されます. 現在の最小要件はAndroid 7.0 (SDK 24), ターゲットSDKは36です.

READMEとCHANGELOGは, .readme/ と .changelog/ のJSON言語ソースとテンプレートから .python/generate_markdown.py が生成します (10言語). ドキュメントを変更する場合は, 生成済みMarkdownを直接編集せず, JSONソースを編集してスクリプトを再実行してください.

### 関連リンク

- AutoJs6ドキュメント: https://docs.autojs6.com
- Androidの安全なファイル共有: https://developer.android.com/training/secure-file-sharing
