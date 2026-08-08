******

### 릴리스 기록

******

# v1.0.1

###### 2026/08/08

* `수정` 플러그인 센터에서 활성화할 때 유효한 Explorer Action 서비스 바인딩 반환
* `개선` 플러그인 이름과 설명을 간결하게 하고 사용자 문서를 더 자연스럽게 정리

# v1.0.0

###### 2026/08/02

* `기능` 플러그인 ID `apk-inspector`, 동작 ID `inspect-android-package`, 엔진 `explorer-action`, 변형 `default`를 사용하는 APK Inspector 플러그인
* `기능` APK, APKS, XAPK, APKM, APKZ, AAB 파일을 위한 Explorer Action 프로토콜 v2 기본 읽기 전용 검사
* `기능` 텍스트 및 바이너리 APK manifest, AAB protobuf manifest, bundletool `toc.pb` 메타데이터의 읽기 전용 디코딩
* `기능` 패키지 세부 정보, 요청 권한, 구성 요소, 기기에 맞는 분할 APK, OBB 자산, 구조 결과, 정리된 manifest 보기, APK V1-V3 서명 방식 존재 감지
* `기능` 보호된 Explorer와 정확한 MIME Android `ACTION_VIEW`를 분리한 게이트웨이, 4 GiB 제한, SHA-256을 계산하는 제한된 비공개 읽기 전용 스냅샷
* `기능` 스페인어, 프랑스어, 러시아어, 아랍어, 일본어, 한국어, 영어, 중국어 간체, 홍콩 중국어 번체, 대만 중국어 번체로 현지화된 메타데이터, UI, 사용 안내, README, 변경 기록
* `의존성` Gson 버전 2.13.2 추가
