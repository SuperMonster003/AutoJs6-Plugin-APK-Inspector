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

レポートは4つのセクションで構成されます. 「パッケージ詳細」にはアプリ名, アイコン, パッケージ名, バージョン, SDK範囲, 署名検証と署名証明書のローテーション系譜, ファイルサイズ, SHA-256チェックサムが表示されます. 「コンポーネント」にはパッケージ内の全分割APKとOBBアセットが一覧表示され, この端末に適合する部分が示されます (AABの場合はモジュール一覧). 「要求権限」は保護レベル別に権限を分類し, 実行時の危険な権限を説明付きで先頭に強調表示します. 「セキュリティと互換性の結果」には構造上の問題と端末互換性の判定がまとまります. 「manifestを表示」ボタンで整形済みAndroidManifest全文も確認できます.

### 特長

- タップするだけ: AutoJs6ファイルマネージャーから直接レポートを開けます. インストール, 展開, ネットワークアクセスは不要です.
- 6形式に対応: 標準APK, 複数分割のバンドル形式 (APKS, XAPK, APKM, APKZ), ストア配布形式AABに対応. APKSはbundletoolとSAIの両方の書き出しを扱えます.
- バージョンと互換性: パッケージ名, バージョン名とバージョンコード, 最小/ターゲット/最大SDKを表示し, 端末のAndroidバージョンと対比します.
- 権限の透明化: 要求権限を保護レベル (実行時/危険, 署名/保護, 通常) で分類し, 実行時権限を先頭に強調して1行の説明を表示します. 取得できないレベルも非表示にせず明記します.
- 分割APKの分析: バンドル内の全APKエントリとOBBアセットを一覧にし, この端末向けに選択される分割 (ベース, 言語, 画面密度, ABI) を示します.
- 署名と証明書の検証: APKのV2 / V3 / V3.1署名を暗号学的に検証し, V1の有無を報告します. 現在の署名証明書を個別に表示し, 検証済みローテーション系譜の新旧関係とSHA-256指紋も示します.
- V4 / V4.1サイドカー検証: AutoJs6は完全一致する`<APKファイル名>.idsig`だけを派生し, 上限付き読み取り専用ディスクリプターを付与します. プラグインは署名データ, 証明書と公開鍵, 対応するV2 / V3 APKダイジェスト, fs-verityルート, 内蔵Merkleツリー, V3.1ローテーション署名者を検証します.
- manifestの可読化: バイナリAPK manifestとAAB protobuf manifestを読みやすいXMLへデコードし, 独立した読み取り専用ビューアーで表示します.
- 完全性の確認: ファイル読み取りと同時にSHA-256を計算し, 公式に公開されたチェックサムとすぐ比較できます.
- 構造チェック: ベースAPKの欠落, 分割の重複や依存欠落, バージョンやパッケージ名の不一致などを検出し, 阻害要因 [!] と参考情報 [i] を区別して表示します.

### 使い方

1. APK Inspectorをダウンロードしてインストールし, AutoJs6のプラグインセンターで有効化します (AutoJs6バージョンコード 5277 以降が必要).
2. AutoJs6のファイルマネージャーを開き, 確認したいパッケージファイル (APK, APKS, XAPK, APKM, APKZ, AAB) を探します.
3. ファイルをタップするか, メニューから「Androidパッケージを検査」を選ぶと, まもなく検査レポートが表示されます.
4. アプリのアイコンと名前, パッケージ詳細, コンポーネント, 要求権限, セキュリティと互換性の結果を上から順に確認します.
5. 「manifestを表示」をタップするとAndroidManifest全文を読めます. 戻るボタンでファイルマネージャーに戻ります.

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

AABはアプリストア向けの配布形式で, Android端末に直接インストールできません. プラグインはそのprotobuf manifestとモジュール構造をデコードして表示できますが, インストールにはbundletoolなどでAPK(S)へ変換する必要があります.

### 権限とセキュリティ

プラグインはストレージ, ネットワーク, パッケージインストール権限を要求しません. 選択したパッケージにはホストが付与した一時的な読み取り専用content URIだけでアクセスし, 任意の`.idsig`にはホストが完全一致する`<APKファイル名>.idsig`用に生成した上限付き読み取り専用ディスクリプターだけでアクセスします. ディレクトリ列挙や任意の同一フォルダーパスは提供されません. 検査前に両入力をアプリ専用キャッシュの読み取り専用スナップショットへコピーし (パッケージのコピー中にSHA-256を計算), 解析はすべてスナップショット上で行います. `.idsig`は40 MiBまでで, 期限切れスナップショットは24時間以内に削除されます. ファイルマネージャーのリクエストはプロトコル, リクエストIDとアクションID, 対象メタデータ, ホストバージョン, URI形式, 名前, サイズ, 読み取り専用grant, セッションBinderを項目ごとに検証します. 他アプリからの「他のアプリで開く」は専用MIMEタイプだけを受け付け, application/zip, application/octet-stream, 書き込み・永続化・プレフィックスgrantを拒否します.

細工されたファイルによる端末リソースの枯渇を防ぐため, 解析には次の上限があり, 超過したファイルは理由の提示とともに拒否されます:

- 1ファイルの上限は 4 GiB で, コピー時にはキャッシュに 128 MiB 以上の空きが必要です. 1回のアクションで処理する対象ファイルは1つだけです.
- アーカイブのエントリは最大 16384 件, バンドルあたりのAPKエントリ走査は最大 512 件, エントリ名は最長 1024 文字です.
- 宣言されたエントリサイズは 4 GiB まで, 宣言された合計サイズは 8 GiB までです.
- ネストしたAPKのmanifest走査は 256 MiB まで, バンドルメタデータは 1 MiB まで, アイコンとラベルの読み込みに使う一時APKは 512 MiB までです.
- 権限分類は最大 2048 件を走査し, 安全な一意名を最大 512 件表示します. 読み込む説明は1件 240 文字までで, 省略や取得不能な保護レベルを明記します.

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

実装済みの機能は上記とRoadmapのチェック済み項目のとおりです. レポートのエクスポート, リソースやネイティブライブラリの分析などの計画はRoadmapで管理しており, 未チェック項目は現在の機能ではありません.

- [ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/ROADMAP.md)

### リリース履歴

#### v1.1.0

_2026/08/30_

- `ヒント` Explorer Actionプロトコルv22と上限付きV4サイドカーアクセスにはAutoJs6バージョンコード5277以降が必要です
- `機能` APK V2 / V3 / V3.1 / V4 / V4.1の端末上暗号学的検証を追加し, コンテンツダイジェスト, 署名者証明, fs-verityルート, 内蔵Merkleツリー, 補完署名方式との照合に対応
- `機能` 詳細な署名証明書情報と検証済みローテーション系譜を追加し, 旧/現在の役割, 能力フラグ, SHA-256指紋を表示
- `機能` ホストが正確に派生した読み取り専用ディスクリプターから`.idsig`を上限付きで一時保存; ディレクトリ列挙や任意の同一フォルダーファイルへのアクセスは提供しません
- `機能` 要求権限を`protectionLevel`で実行時/危険・署名/保護・通常に分類; 実行時権限を上限付き1行説明とともに先頭で強調し, 取得不能なレベルも表示して明記
- `改善` Explorer Action v22リクエスト検証と不変の専用スナップショットを強化し, パッケージ4 GiB, idsig 40 MiBの上限, ファイル識別確認, 迅速なホストセッション終了を追加
- `改善` Build Tools 37 `apksigner`による有効, 改変, 複数署名者, V3.1/V4.1ローテーション, 欠落, 不正形式の公式サンプル行列を追加

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
