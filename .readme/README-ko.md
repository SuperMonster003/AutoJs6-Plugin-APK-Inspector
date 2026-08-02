<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-apk-inspector-ic-launcher" border="0" width="128" />
  </p>

  <p>AutoJs6 Explorer에서 APK, 분할 패키지 컨테이너, Android App Bundle을 상세하게 읽기 전용으로 검사</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-APK-Inspector?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 언어 (Languages)

******

현재 README.md는 다음 언어를 지원합니다:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-en.md)
- [Francais [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-fr.md)
- [Espanol [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ja.md)
- 한국어 [ko] # 현재
- [Russkii [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ar.md)

******

### 소개

******

AutoJs6 APK Inspector 플러그인은 기본 Explorer에서 Android 패키지 파일을 검사하는 기본 동작을 제공합니다. 크기가 제한된 앱 전용 스냅샷을 분석하며 원본 패키지를 수정하거나 설치하지 않습니다.

******

### 기능

******

- APK, APKS, XAPK, APKM, APKZ, AAB 파일에 Explorer Action 프로토콜 v2 기본 동작을 등록합니다.
- 텍스트 및 바이너리 APK manifest, AAB protobuf manifest, bundletool toc.pb 메타데이터를 디코딩합니다.
- 패키지 식별자, 버전, SDK 범위, 요청 권한, 구성 요소, 기기에 맞는 분할 APK, OBB 자산, 구조 문제를 표시합니다.
- 암호학적 유효성을 단정하지 않고 APK V1, V2, V3 서명 방식의 존재를 감지합니다.
- 정리된 Android Manifest를 별도의 읽기 전용 뷰어에 표시합니다.
- 정확한 Android 패키지 MIME 형식을 위한 별도의 Android ACTION_VIEW 게이트웨이를 제공합니다.

******

### 지원 형식

******

Explorer 기본 동작은 다음 확장자와 정확히 일치합니다:

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

******

### 플러그인 인터페이스

******

AutoJs6는 다음 식별자로 플러그인을 찾고 실행합니다:

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

버전 1은 검사만 수행합니다. 설치 버튼, 설치 권한, 패키지 설치 프로그램, 소스 편집, 디렉터리 열거 기능이 없습니다. 호스트 설치 흐름은 독립적으로 유지됩니다. 플러그인을 사용할 수 없으면 AutoJs6는 호스트 대체 동작을 사용합니다.

플러그인은 JVM으로만 구현되며 네이티브 라이브러리가 없습니다. supportedAbis = emptyArray()를 선언하고 ABI 독립 단일 APK로 배포됩니다. AutoJs6 호스트 빌드 5269 이상이 필요합니다.

******

### 보안

******

보호된 Explorer 게이트웨이는 프로토콜 v2, 기본 파일 화면, 동작 ID, content URI 계층, 정확한 ClipData, 표시 이름, 확장자, MIME 형식, 크기, 읽기 전용 grant를 검증합니다. 공개 ACTION_VIEW 게이트웨이는 전용 패키지 MIME 형식만 허용합니다. 입력은 SHA-256을 계산하며 크기가 제한된 읽기 전용 비공개 스냅샷으로 한 번 복사됩니다. 프로토콜의 상위 URI는 열거하지 않습니다.

******

### 안전 제한

******

- 최대 입력 크기: 4 GiB.
- 동작당 대상 파일은 하나이며 캐시에 최소 128 MiB의 여유 공간이 필요합니다.
- 아카이브 항목 수, 항목 이름, 선언 크기, 전체 크기, 중첩 APK 검색, 메타데이터, protobuf, manifest 출력에 제한이 적용됩니다.
- 외부 ACTION_VIEW는 application/zip, application/octet-stream, 쓰기 grant, 영구 grant, prefix grant를 거부합니다.
- V1에서 V3 서명 방식은 존재 여부만 확인합니다. V4에는 별도 idsig 입력이 필요하며 이 프로토콜 범위 밖입니다.
- 플러그인은 패키지를 설치하지 않으며 저장소, 네트워크, 패키지 설치 권한을 요청하지 않습니다.

******

### 릴리스 기록

******

# v1.0.0

###### 2026/08/02

* `기능` 플러그인 ID `apk-inspector`, 동작 ID `inspect-android-package`, 엔진 `explorer-action`, 변형 `default`를 사용하는 APK Inspector 플러그인
* `기능` APK, APKS, XAPK, APKM, APKZ, AAB 파일을 위한 Explorer Action 프로토콜 v2 기본 읽기 전용 검사
* `기능` 텍스트 및 바이너리 APK manifest, AAB protobuf manifest, bundletool `toc.pb` 메타데이터의 읽기 전용 디코딩
* `기능` 패키지 세부 정보, 요청 권한, 구성 요소, 기기에 맞는 분할 APK, OBB 자산, 구조 결과, 정리된 manifest 보기, APK V1-V3 서명 방식 존재 감지
* `기능` 보호된 Explorer와 정확한 MIME Android `ACTION_VIEW`를 분리한 게이트웨이, 4 GiB 제한, SHA-256을 계산하는 제한된 비공개 읽기 전용 스냅샷
* `기능` 네이티브 라이브러리가 없는 순수 JVM 구현, `supportedAbis = emptyArray()`로 제한 없는 ABI 선언, ABI 독립 단일 APK, AutoJs6 호스트 빌드 5269 이상 요구
* `기능` 스페인어, 프랑스어, 러시아어, 아랍어, 일본어, 한국어, 영어, 중국어 간체, 홍콩 중국어 번체, 대만 중국어 번체로 현지화된 메타데이터, UI, 사용 안내, README, 변경 기록
* `의존성` Gson 버전 2.13.2 추가

##### 더 많은 릴리스

* [CHANGELOG-ko.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-ko.md)

******

### 빌드

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Release 빌드:

```powershell
.\gradlew.bat :app:assembleRelease
```

빌드 매개변수는 version.properties에서 가져옵니다. 현재 최소 SDK는 24이고 대상 SDK는 36입니다.

******

### 리소스 구성

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
```

strings.xml은 플러그인 메타데이터와 UI 텍스트를 현지화합니다. plugin_instruction.md는 호스트에 표시되는 안내를 제공합니다. .python/generate_markdown.py는 JSON 소스에서 현지화된 README와 변경 기록을 생성합니다.

******

### 링크

******

- AutoJs6 문서: https://docs.autojs6.com
- Android 보안 파일 공유: https://developer.android.com/training/secure-file-sharing
