<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-apk-inspector-ic-launcher" border="0" width="128" />
  </p>

  <p>AutoJs6 ExplorerでAPK, 分割パッケージコンテナ, Android App Bundleを詳細かつ読み取り専用で検査</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-APK-Inspector?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 言語 (Languages)

******

現在のREADME.mdは次の言語に対応しています:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-en.md)
- [Francais [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-fr.md)
- [Espanol [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-es.md)
- 日本語 [ja] # 現在
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ko.md)
- [Russkii [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ar.md)

******

### 概要

******

AutoJs6 APK Inspectorプラグインは, メインExplorerでAndroidパッケージファイルを検査する主要アクションを提供します. サイズ制限されたアプリ専用のスナップショットを解析し, 元のパッケージを変更またはインストールしません.

******

### 機能

******

- APK, APKS, XAPK, APKM, APKZ, AABファイル向けにExplorer Actionプロトコルv2の主要アクションを登録します.
- テキスト形式とバイナリ形式のAPK manifest, AAB protobuf manifest, bundletool toc.pbメタデータをデコードします.
- パッケージ識別子, バージョン, SDK範囲, 要求権限, コンポーネント, 端末に適合する分割APK, OBBアセット, 構造上の問題を表示します.
- 暗号学的な有効性を断定せずにAPK V1, V2, V3署名方式の存在を検出します.
- 整形したAndroid Manifestを独立した読み取り専用ビューアーで表示します.
- 正確なAndroidパッケージMIMEタイプ向けに独立したAndroid ACTION_VIEWゲートウェイを提供します.

******

### 対応形式

******

Explorerの主要アクションは次の拡張子と完全一致します:

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

******

### プラグインインターフェース

******

AutoJs6は次の識別子でプラグインを検出して実行します:

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

バージョン1は検査だけを行います. インストールボタン, インストール権限, パッケージインストーラー, ソース編集, ディレクトリ列挙はありません. ホストのインストール処理は独立したままです. プラグインが利用できない場合, AutoJs6はホスト側のフォールバックを使用します.

プラグインは完全にJVMで実装され, ネイティブライブラリを含みません. supportedAbis = emptyArray()を宣言し, ABIに依存しない単一APKとして公開されます. AutoJs6ホストのビルド5269以降が必要です.

******

### セキュリティ

******

保護されたExplorerゲートウェイは, プロトコルv2, メインファイル画面, アクションID, content URIの親子関係, 正確なClipData, 表示名, 拡張子, MIMEタイプ, サイズ, 読み取り専用grantを検証します. 公開ACTION_VIEWゲートウェイは専用パッケージMIMEタイプだけを受け付けます. 入力はSHA-256を計算しながら, サイズ制限された読み取り専用のプライベートスナップショットへ1回だけコピーされます. プロトコルの親URIは列挙しません.

******

### 安全制限

******

- 最大入力サイズ: 4 GiB.
- 1回のアクションにつき対象ファイルは1つで, キャッシュに128 MiB以上の空きが必要です.
- アーカイブエントリ数, エントリ名, 宣言サイズ, 合計サイズ, ネストしたAPK走査, メタデータ, protobuf, manifest出力には上限があります.
- 外部ACTION_VIEWはapplication/zip, application/octet-stream, 書き込みgrant, 永続grant, prefix grantを拒否します.
- V1からV3の署名方式は存在確認だけです. V4には別のidsig入力が必要で, このプロトコルの対象外です.
- プラグインはパッケージをインストールせず, ストレージ, ネットワーク, パッケージインストール権限を要求しません.

******

### リリース履歴

******

# v1.0.0

###### 2026/08/02

* `機能` プラグインID `apk-inspector`, アクションID `inspect-android-package`, エンジン `explorer-action`, バリアント `default` のAPK Inspectorプラグイン
* `機能` APK, APKS, XAPK, APKM, APKZ, AABファイル向けExplorer Actionプロトコルv2の主要な読み取り専用検査
* `機能` テキスト形式とバイナリ形式のAPK manifest, AAB protobuf manifest, bundletool `toc.pb`メタデータの読み取り専用デコード
* `機能` パッケージ詳細, 要求権限, コンポーネント, 端末に適合する分割APK, OBBアセット, 構造上の発見, 整形manifest表示, APK V1-V3署名方式の存在検出
* `機能` 保護されたExplorerと正確なMIMEのAndroid `ACTION_VIEW`を分離したゲートウェイ, 4 GiB上限, SHA-256を計算する制限付きプライベート読み取り専用スナップショット
* `機能` ネイティブライブラリを含まない純粋なJVM実装, `supportedAbis = emptyArray()`によるABI無制限, ABI非依存の単一APK, AutoJs6ホストビルド5269以降
* `機能` スペイン語, フランス語, ロシア語, アラビア語, 日本語, 韓国語, 英語, 簡体字中国語, 香港繁体字中国語, 台湾繁体字中国語のメタデータ, UI, 使用説明, README, 変更履歴
* `依存関係` Gsonバージョン2.13.2を追加

##### その他のリリース

* [CHANGELOG-ja.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-ja.md)

******

### ビルド

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Releaseビルド:

```powershell
.\gradlew.bat :app:assembleRelease
```

ビルドパラメーターはversion.propertiesから取得します. 現在の最小SDKは24, ターゲットSDKは36です.

******

### リソース構成

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
```

strings.xmlはプラグインのメタデータとUIテキストをローカライズします. plugin_instruction.mdはホストが表示する説明を提供します. .python/generate_markdown.pyはJSONソースからローカライズされたREADMEと変更履歴を生成します.

******

### リンク

******

- AutoJs6ドキュメント: https://docs.autojs6.com
- Androidの安全なファイル共有: https://developer.android.com/training/secure-file-sharing
