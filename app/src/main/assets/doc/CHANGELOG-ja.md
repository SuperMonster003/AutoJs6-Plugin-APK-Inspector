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
