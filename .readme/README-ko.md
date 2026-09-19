<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-apk-inspector-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>설치 패키지와 내용 검사</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-APK-Inspector?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=534BAE&label=License"/></a>
  </p>
</div>

### 언어 (Languages)

이 README는 다음 언어로 제공됩니다:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ja.md)
- 한국어 [ko] # 현재
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ar.md)

### 소개

APK Inspector는 AutoJs6 파일 관리자의 확장 플러그인입니다. 파일 관리자에서 APK, APKS, XAPK, APKM, APKZ, AAB 파일을 탭하면 검사 보고서가 바로 열립니다. 앱 이름, 버전, 요청 권한, 이 기기에 설치 가능한지 여부를 한 화면에서 확인할 수 있습니다. 패키지는 설치되지 않으며 원본 파일도 절대 수정되지 않습니다.

보고서는 네 부분으로 구성됩니다. "패키지 세부 정보"에는 앱 이름, 아이콘, 패키지 이름, 버전, SDK 범위, 서명 검증과 서명 인증서 교체 계보, 파일 크기, SHA-256 체크섬이 표시됩니다. "구성 요소"에는 패키지 안의 모든 분할 APK와 OBB 자산이 나열되고 이 기기와 일치하는 부분이 표시됩니다 (AAB는 모듈 목록). 또한 manifest에 선언된 activity/별칭, service, receiver, provider를 개수와 명시적 exported 상태별로 분류하고, 네이티브 .so 라이브러리를 ABI, 압축 해제 크기, 기기 호환 상태별로 집계하며, 표준 classes*.dex 파일과 압축 해제 크기를 나열합니다. "요청 권한"은 보호 수준별로 권한을 분류하고 런타임 위험 권한을 짧은 설명과 함께 먼저 강조합니다. "보안 및 호환성 결과"에는 구조적 문제와 기기 호환성 판정이 정리됩니다. "manifest 보기" 버튼으로 정형화된 AndroidManifest 전문도 볼 수 있습니다.

### 주요 기능

- 호스트 대화상자 점진적 확장: 호환 AutoJs6 호스트에서는 기존 APK 정보 대화상자의 모든 기본 필드와 설치/manifest 작업을 그대로 유지하면서 SHA-256에 결합된 제한형 현지화 검사 요약을 추가합니다. 플러그인이 없거나 비활성화되거나 구버전이거나 호환되지 않거나 실패하면 기본 대화상자는 바뀌지 않습니다.
- 큰 글꼴 및 좁은 화면 레이아웃: 1.5x/2.0x 글꼴 배율에서 공간이 부족하면 보고서 헤더를 세로로 배치하고, 간결한 도구 모음 제목을 완전히 표시하며, 긴 SHA-256과 권한 이름을 생략 없이 줄바꿈하고, 가로 화면 매니페스트 검색에서 IME 전체 화면 추출을 방지합니다.
- 적응형 화면: 보고서와 매니페스트 뷰어가 시스템의 밝은/어두운 모드를 따릅니다. Android 12 이상에서는 Material You가 배경화면에서 색상표도 생성하며, 의미 기반 색상과 대비를 고려한 시스템 표시줄 아이콘으로 두 화면의 가독성을 유지합니다.
- 컨테이너 메타데이터: SAI APKS, XAPK, APKMirror APKM 메타데이터를 1 MiB 한도 안에서 읽고 패키징 도구/형식 버전, 메타데이터에 선언된 앱 버전, 실제 존재하는 아이콘 항목을 표시하며 APK manifest의 사실을 덮어쓰지 않습니다.
- 탭 한 번으로 검사: AutoJs6 파일 관리자에서 바로 보고서를 엽니다. 설치, 압축 해제, 네트워크 접근이 필요 없습니다.
- 여섯 가지 형식: 표준 APK, 다중 분할 번들 형식 (APKS, XAPK, APKM, APKZ), 스토어 배포 형식 AAB를 지원하며, APKS는 bundletool과 SAI 내보내기를 모두 처리합니다.
- 버전과 호환성: 패키지 이름, 버전 이름과 코드, 최소/대상/최대 SDK를 표시하고 기기의 Android 버전과 대조해 설치 전에 호환성을 판단할 수 있습니다.
- 권한 투명성: 요청 권한을 보호 수준(런타임/위험, 서명/보호, 일반)으로 분류하고 런타임 권한을 한 줄 설명과 함께 먼저 강조합니다. 확인할 수 없는 수준도 숨기지 않고 명확히 표시합니다.
- 분할 APK 분석: 번들 안의 모든 APK 항목과 OBB 자산을 나열하고 이 기기용으로 선택되는 분할 (베이스, 언어, 화면 밀도, ABI) 을 표시합니다.
- 기기 구성 시뮬레이션: 번들 보고서에서 언어, 화면 밀도, ABI를 바꾸면 동일한 비공개 스냅샷에 제한형 선택기를 기기에서 다시 실행하고 실제 기기 대비 추가되거나 제거되는 APK를 표시합니다.
- AAB 구성 및 전송: BundleConfig.pb 설정을 제한적으로 디코딩하고 base, feature, asset, ML, AI, SDK 모듈에 설치 시, 조건부, 주문형, fast-follow, 융합, 제거 가능 전송 메타데이터를 표시합니다. 손상되거나 한도를 넘는 구성 또는 모듈 manifest는 해당 표시만 저하시킵니다.
- 리소스 기반 앱 식별 대체 경로: Android가 AAB 또는 제한을 넘는 번들을 직접 불러오지 못하면 중첩 APK 전체를 추출하지 않고 AAB resources.pb 또는 APK resources.arsc에서 현재 언어와 밀도에 맞는 앱 이름과 래스터 아이콘을 해석합니다.
- manifest 구성 요소 공개 상태: 선택된 APK 분할 또는 스캔한 AAB 모듈의 activity/별칭, service, broadcast receiver, content provider를 집계하고 명시적 android:exported 값을 내보냄, 내보내지 않음, 미지정/미해결로 분류합니다.
- 네이티브 라이브러리 개요: 선택된 APK 분할 또는 AAB 모듈의 .so 파일을 ABI와 압축 해제 크기별로 집계하고 기기 우선 ABI, 지원되는 대체 ABI, 미지원 아키텍처를 표시하며 라이브러리 내용은 추출하지 않습니다.
- DEX 개요: 선택된 APK 분할 또는 AAB 모듈의 표준 classes*.dex 파일을 자연 순서로 나열하고 파일별 및 전체 압축 해제 크기를 표시하며 DEX 내용은 추출, 디코딩 또는 디컴파일하지 않습니다.
- 보고서 재사용: 패키지 세부 정보의 주요 행을 길게 누르면 값만 복사할 수 있고 Android 공유 시트로 화면과 동일한 `text/plain` 보고서를 공유할 수 있습니다. 텍스트는 메모리에만 유지되며 파일 생성이나 저장소 권한이 필요하지 않습니다.
- 서명 및 인증서 검증: APK V2 / V3 / V3.1 서명을 암호학적으로 검증하고 V1 존재 여부를 보고합니다. 현재 서명 인증서를 각각 표시하며, 검증된 교체 계보의 이전/현재 관계와 SHA-256 지문도 보여 줍니다.
- V4 / V4.1 사이드카 검증: AutoJs6는 정확한 `<APK 파일 이름>.idsig`만 파생해 제한된 읽기 전용 디스크립터를 부여합니다. 플러그인은 서명 데이터, 인증서와 공개 키, 대응하는 V2 / V3 APK 다이제스트, fs-verity 루트, 포함된 Merkle 트리 및 V3.1 교체 서명자를 검증합니다.
- manifest 가독화: 바이너리 APK manifest와 AAB protobuf manifest를 읽기 쉬운 XML로 디코딩해 줄 번호와 의미 기반 구문 강조를 갖춘 독립 읽기 전용 뷰어에 표시합니다. 대소문자를 구분하지 않는 제한 검색, 일치 항목 강조, 이전/다음 이동도 제공합니다.
- TalkBack 및 RTL 접근성: 보고서 구역에 제목 의미를 제공하고 모든 아이콘 작업에 음성 레이블을 지정하며 사용자 지정 상호작용 행은 48dp 터치 영역을 충족합니다. 동적 결과를 알리고 아랍어 UI는 언어 방향에 맞춰 미러링됩니다.
- 무결성 대조: 파일을 읽는 동시에 SHA-256을 계산해 공식 배포처의 체크섬과 바로 비교할 수 있습니다.
- 구조 점검: 베이스 APK 누락, 분할 중복이나 의존성 누락, 버전·패키지 불일치 등을 찾아내고, 차단 [!] 과 참고 [i] 를 구분해 표시합니다.

### 사용 방법

1. APK Inspector를 다운로드해 설치한 뒤 AutoJs6 플러그인 센터에서 활성화합니다 (AutoJs6 버전 코드 5277 이상 필요).
2. AutoJs6 파일 관리자를 열고 확인할 패키지 파일 (APK, APKS, XAPK, APKM, APKZ, AAB) 을 찾습니다.
3. 파일을 탭하거나 파일 메뉴에서 "Android 패키지 검사"를 선택하면 잠시 후 검사 보고서가 나타납니다.
4. 앱 아이콘과 이름, 패키지 세부 정보, 구성 요소, 요청 권한, 보안 및 호환성 결과를 위에서부터 차례로 확인합니다.
5. APKS, XAPK, APKM 또는 APKZ에서는 언어, 화면 밀도, ABI를 선택하고 시뮬레이션을 적용해 선택된 APK를 실제 기기와 비교합니다.
6. "패키지 세부 정보"의 행을 길게 눌러 값을 복사하거나 도구 모음의 "보고서 공유"를 탭해 화면과 동일한 텍스트를 Android 공유 시트로 전송합니다.
7. "manifest 보기"를 탭하면 AndroidManifest 전문을 읽을 수 있으며, 뒤로 가기를 누르면 파일 관리자로 돌아갑니다.

> 다른 앱도 content URI와 전용 Android 패키지 MIME 유형을 사용하면 시스템 "연결 프로그램" (ACTION_VIEW) 을 통해 APK Inspector로 패키지를 전달할 수 있습니다. 플러그인은 항상 읽기 전용이며 설치 수단을 전혀 제공하지 않습니다.

### 지원 형식

파일 관리자 기본 동작은 다음 확장자와 정확히 일치할 때 실행됩니다 (대소문자 구분 없음):

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

APKS, XAPK, APKM, APKZ는 여러 분할 APK를 묶은 컨테이너 형식입니다. AAB는 앱 스토어 제출용 App Bundle 형식으로, 여기서 내용을 확인할 수 있지만 설치하려면 bundletool 등으로 APK(S)로 변환해야 합니다.

### 자주 묻는 질문

#### 이 플러그인으로 APK를 설치할 수 있나요?

아니요, 의도된 설계입니다. 플러그인은 설치 권한을 요청하지 않으며 화면 어디에도 설치 버튼이 없습니다. 설치 전에 패키지 내용을 확인하는 것이 역할입니다. 설치는 시스템 설치 관리자나 호스트 자체 흐름을 이용하세요.

#### 일부 파일은 왜 검사할 수 없나요?

흔한 원인: 파일이 8 GiB 한도를 초과함, 기기 캐시 공간 부족 (최소 128 MiB 여유 필요), 번들의 항목 수나 크기가 해석 한도를 초과함, 읽는 도중 다른 앱이 파일을 변경함, 파일 자체의 구조 손상 등입니다. 오류 메시지에 구체적인 이유가 표시됩니다.

#### 서명 감지 결과가 패키지의 안전을 증명하나요?

아니요. 플러그인은 V2 / V3 / V3.1 / V4 / V4.1의 패키지 무결성과 서명자 증명을 암호학적으로 검증하고 인증서 지문과 교체 계보를 표시합니다. 하지만 유효한 서명이 증명하는 것은 해당 서명 이후 패키지가 바뀌지 않았다는 사실뿐이며 서명자나 앱 자체의 신뢰성은 아닙니다. 지문과 SHA-256을 공식 배포처와 대조하세요.

#### AAB 파일에 "설치하려면 변환이 필요합니다"라고 표시되는 이유는 무엇인가요?

AAB는 앱 스토어용 배포 형식이라 Android 기기에 직접 설치할 수 없습니다. 플러그인은 protobuf manifest와 모듈 구조를 디코딩하고 resources.pb에서 현지화된 앱 이름과 밀도에 맞는 래스터 아이콘을 해석해 보여줄 수 있지만, 설치하려면 여전히 bundletool 등으로 APK(S)로 변환해야 합니다.

### 권한과 보안

플러그인은 저장소, 네트워크, 패키지 설치 권한을 요청하지 않습니다. 선택한 패키지는 호스트가 부여한 임시 읽기 전용 content URI로만 접근하고, 선택적 `.idsig`는 호스트가 정확한 `<APK 파일 이름>.idsig`용으로 파생한 제한된 읽기 전용 디스크립터로만 접근합니다. 디렉터리 열거나 임의의 같은 폴더 경로를 제공하지 않습니다. 검사 전에 두 입력을 앱 전용 캐시의 읽기 전용 스냅샷으로 복사하고 (패키지 복사 중 SHA-256 계산), 모든 해석은 스냅샷에서만 수행합니다. `.idsig`는 40 MiB로 제한되며 만료된 스냅샷은 24시간 안에 정리됩니다. 파일 관리자 요청은 프로토콜 버전, 요청 및 동작 ID, 대상 메타데이터, 호스트 버전, URI 형식, 이름, 크기, 읽기 전용 권한, 세션 Binder를 항목별로 검증합니다. 다른 앱의 "연결 프로그램" 요청은 전용 패키지 MIME 유형만 허용하고 application/zip, application/octet-stream 및 쓰기·영구·접두사 권한을 거부합니다.

조작된 파일이 기기 자원을 소진하지 못하도록 해석에는 다음 상한이 있으며, 상한을 넘는 파일은 사유와 함께 거부됩니다:

- 파일 하나의 최대 크기는 8 GiB 이며, 복사 시 캐시에 128 MiB 이상의 여유가 있어야 하고, 한 번의 동작은 대상 파일 하나만 처리합니다.
- 아카이브 항목은 최대 262144 개까지 해석하고, 번들당 APK 항목은 최대 4096 개까지 살피며, 항목 이름은 최대 4096 자입니다.
- 선언된 단일 항목 크기는 8 GiB, 선언된 총 크기는 64 GiB 를 넘을 수 없습니다.
- 중첩 APK manifest 스캔은 16 GiB, 번들 메타데이터는 4 MiB, 아이콘·라벨 로딩용 임시 APK는 8 GiB 까지로 제한됩니다.
- AAB BundleConfig.pb는 4 MiB로 제한됩니다. 전송 표시는 512개 manifest / 64 MiB AAB 스캔을 공유하고 모듈당 최대 128개 조건 값을 유지합니다. 한도 초과와 손상된 메타데이터는 격리해 표시합니다.
- 리소스 대체 경로는 테이블당 최대 64 MiB, 아이콘당 최대 8 MiB를 읽고, 중첩 APK 안에서 테이블과 아이콘을 찾을 때 공유 16 GiB 예산을 사용합니다. 한도 초과, 손상된 리소스, 미해결 참조는 해당 대체 경로만 비활성화하며 명확히 표시합니다.
- 권한 분류는 최대 2048개 요청을 스캔하고 안전한 고유 이름을 최대 512개 표시하며, 불러온 설명은 각각 240자로 제한합니다. 생략 항목과 확인할 수 없는 보호 수준은 명확히 표시됩니다.
- 구성 요소 통계는 manifest마다 최대 4096개 선언을 스캔하고, AAB에서는 공유 입력 예산 64 MiB 안에서 최대 512개 모듈 manifest를 스캔합니다. 생략, 미해결 exported 값, manifest별 실패를 명확히 표시합니다.
- 네이티브 통계는 최대 32768개의 .so 항목을 유지하고 64개의 ABI 디렉터리를 표시합니다. 선택된 중첩 APK는 최대 4096개를 공유 입력 예산 16 GiB 안에서 읽고 APK별 중앙 디렉터리는 32 MiB까지만 유지합니다. 한도와 실패는 일부 결과로 명확히 표시합니다.
- DEX 통계는 스캔한 모든 표준 항목을 집계하지만 자연 정렬된 경로는 최대 128개만 표시합니다. 네이티브 라이브러리 개요와 동일한 제한된 중앙 디렉터리 순회를 공유하므로 DEX 내용은 추출, 디코딩 또는 디컴파일하지 않습니다.

### 플러그인 인터페이스

호스트 (AutoJs6) 는 다음 식별자로 플러그인을 발견하고 호출합니다. 플러그인·호스트 개발자를 위한 참고 정보입니다:

```text
service action: org.autojs.plugin.EXPLORER_ACTION
execute action: org.autojs.plugin.EXPLORER_ACTION_EXECUTE
plugin id: apk-inspector
engine: explorer-action
variant: default
Explorer action id: inspect-android-package
host file information capability: v1
MIME type: Explorer: extension-only; ACTION_VIEW: dedicated Android package MIME types
required host build: 5277
```

현재 버전은 읽기 전용 검사만 수행합니다. 설치 버튼, 설치 권한, 패키지 설치 관리자가 없고, 원본 파일을 수정하지 않으며, 디렉터리를 열거하지도 않습니다. V4는 호스트가 정확히 파생한 `.idsig` 후보만 사용하고 제한된 읽기 전용 디스크립터를 전용 스냅샷으로 복사한 직후 호스트 세션을 닫습니다. 호환 호스트에서는 호스트 파일 정보 capability v1이 분석한 원본 SHA-256에 결합된 제한형 현지화 요약을 기존 APK 정보 대화상자에 추가할 수 있고, 전체 Activity 보고서도 계속 사용할 수 있습니다. 플러그인이 없거나 비활성화되거나 구버전이거나 호환되지 않거나 실패하면 기본 대화상자는 바뀌지 않으며 호스트는 기존 폴백을 조용히 사용합니다.

### Roadmap

구현된 기능은 위 내용과 Roadmap의 체크된 항목이 기준입니다. 심화 번들 및 AAB 분석 등의 계획은 Roadmap에서 관리하며, 체크되지 않은 항목은 현재 기능이 아닙니다.

- [ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/ROADMAP.md)

### 릴리스 기록

#### v1.2.1

_2026/09/19_

- `수정` 공유 빌드 플러그인 1.8.3을 통해 AGP 9.1의 SDK XML v4 파싱 경고 및 JVM 단위 테스트 조립 작업에서 APK 네이티브 라이브러리 정렬 검사가 잘못 실행되는 문제 해결
- `개선` compileSdk 와 targetSdk 를 37 (Android 17) 로 올리며, 플러그인 동작은 새 대상 버전의 영향을 받지 않음

#### v1.2.0

_2026/09/13_

- `기능` 화면에서 현지화된 로컬 릴리스 기록을 표시하고 영어 대체 제공
- `개선` 릴리스 서명 설정, 예상 APK 구성 및 문서 재생성 결과 검증

#### v1.1.1

_2026/09/12_

- `기능` 직접 검사하는 APK / AAB 파일에 16 KB 페이지 크기 준비 상태 검사를 추가: 각 64비트 네이티브 라이브러리 (`arm64-v8a` / `x86_64` / `riscv64`, 항목당 최대 64 KiB) 의 ELF 헤더와 프로그램 헤더 테이블만 읽어 모든 `PT_LOAD` 세그먼트가 16 KB 이상으로 정렬되었는지 확인하고, 매니페스트가 `extractNativeLibs="false"` 를 선언하면 비압축 라이브러리의 ZIP 데이터 오프셋도 확인합니다. 결론 (준비됨 / 준비 안 됨 / 미검증 / 64비트 라이브러리 없음 / 컨테이너에 중첩된 APK 는 미평가) 은 네이티브 라이브러리 구역과 호스트 파일 정보 요약에 표시되며, 준비되지 않은 경우 발견 구역에도 나열됩니다

##### 전체 기록

- [CHANGELOG-ko.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-ko.md)

### 빌드

```powershell
.\gradlew.bat :app:assembleDebug
```

Release 빌드:

```powershell
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:prepareReleaseArtifacts
.\gradlew.bat :app:verifyReleaseArtifacts
```

빌드와 서명 매개변수는 version.properties와 sign.properties가 관리합니다. 현재 최소 지원은 Android 7.0 (SDK 24), 대상 SDK는 37입니다.

`prepareReleaseArtifacts`는 Release APK를 빌드해 releases/로 복사하고 `autojs6-plugin-apk-inspector-v<version>-<CRC32>.apk` 명명 규칙을 유지한 뒤 APK별 `.sha256` 파일과 정렬된 `SHA256SUMS`를 생성합니다. `verifyReleaseArtifacts`는 파일 이름의 CRC32, 실제 SHA-256, 사이드카 파일, 매니페스트를 각각 검증합니다.

README와 CHANGELOG는 .readme/ 와 .changelog/ 의 JSON 언어 소스와 템플릿을 바탕으로 .python/generate_markdown.py 가 생성합니다 (10개 언어). 문서를 수정할 때는 생성된 Markdown을 직접 고치지 말고 JSON 소스를 수정한 뒤 스크립트를 다시 실행하세요.

### 관련 링크

- AutoJs6 문서: https://docs.autojs6.com
- Android 안전한 파일 공유: https://developer.android.com/training/secure-file-sharing


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/docs/16kb.md)
