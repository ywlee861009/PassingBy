# 앱 하나의 여행 경험 모듈화

이 변경은 동작을 보존하는 1단계 모듈 분리다. 항공·항해 기능, 새 세션 상태 머신,
저장 스키마 변경, 신규 마이그레이션 코드는 포함하지 않는다.

## 실제 모듈과 의존성

| 모듈 | 소유하는 코드 | 직접 프로젝트 의존성 |
| --- | --- | --- |
| `:app` | Application, MainActivity, Receiver, AppGraph, 앱 테마·런처·서명·Firebase 빌드 설정 | 아래 구현 모듈을 조립 |
| `:journey:domain` | Android 없는 JourneyClock, JourneyStore 저장 계약 | 없음 |
| `:journey:data` | SharedPreferences TripStore 구현 | domain |
| `:experience:api` | Canvas ExperienceRenderer 계약, 프레임 통계 | 없음 |
| `:experience:train` | 코스·운행 계획·계절/날씨·SceneState·렌더러·기차 환경음, 해당 리소스 | api, telemetry |
| `:feature:journey` | 여행 화면·ViewModel·설정·수첩·시작 그림·폰트·언어 선택 | domain, api, train, billing, notifications, telemetry |
| `:platform:notifications` | 알림·AlarmManager 어댑터, 채널 리소스 | telemetry |
| `:platform:billing` | Billing SDK와 구매 조회/승인/복원 | telemetry |
| `:platform:telemetry` | Firebase SDK와 통계/오류 보고 | 없음 |

JVM 도메인은 Android 클래스, Compose, 앱 R/BuildConfig를 참조하지 않는다.
저장 구현은 계절·시간대의 문자열 ID만 저장하고, 경험 타입으로 변환하는 것은 소비자가 담당한다.
코스의 문자열과 그래픽 데이터는 train 모듈에, 공통 UI 문자열·폰트는 feature 모듈에 있다.
기존 Kotlin 패키지는 이동에 따른 변경량을 줄이기 위해 유지하며 Gradle namespace/R은 모듈별로 분리했다.

## 조립과 수명

AppGraph가 저장소와 JourneyViewModel.Factory를 조립하고, 라이브러리에 개발 정책을 전달한다.
알림 어댑터에는 앱 열기 PendingIntent와 Receiver Intent의 factory를 전달한다.
따라서 라이브러리가 MainActivity나 앱 BuildConfig를 역참조하지 않는다.
Telemetry의 빌드별 수집 허용 여부도 Application에서 전달한다.

기존 ViewModel 수명, 프레임 루프, 감속/가속 시계, 알람 종류, 저장 형식은 유지한다.
Billing의 Play SDK listener는 facade 내부로 숨겨 feature의 컴파일 API로 노출하지 않는다.
내부 렌더링 구현을 검증하는 기기 테스트는 train으로 옮기고, 실제 앱 그래프를 사용하는
저장·세션 복원 기기 테스트는 app에 유지했다.

## 남아 있는 2단계 작업

feature의 JourneyViewModel은 아직 train의 Course·SceneState·CoursePlan을 직접 사용한다.
Billing/Telemetry facade도 현재 호출 경로를 유지한다. 이번 단계가 최종 플러그인 구조는 아니다.
다음 단계에서 SessionCoordinator/세션 명령과 JourneyProgram을 추출하고,
ExperienceProvider registry로 기차를 연결한 뒤 feature → train 의존성을 제거한다.
항공·항해 Gradle 모듈은 실제 구현 때 추가하며 빈 모듈을 미리 만들지 않는다.

## 검증 명령

```sh
rtk proxy ./gradlew checkModuleBoundaries testDebugUnitTest :journey:domain:test :app:assembleDebug :app:assembleDebugAndroidTest :experience:train:assembleDebugAndroidTest lintDebug
rtk proxy ./gradlew :app:assembleRelease -x :app:uploadCrashlyticsMappingFileRelease
rtk proxy ./gradlew :app:connectedDebugAndroidTest :experience:train:connectedDebugAndroidTest
```

`checkModuleBoundaries`는 허용한 의존 그래프, 순환 참조, 앱 R/BuildConfig 역참조,
JVM 도메인과 SDK 격리를 검사한다. 현재 단계의 임시 feature → train 의존성은 명시적으로 허용한다.
테스트와 빌드에는 기존과 같이 app/google-services.json이 필요하다. 실 Play 구매와 실기기 음질·배터리는
기기 테스트 결과와 구분한다.

CI도 같은 의존성 검사·단위 테스트·전체 Android 모듈 Lint를 실행한다.
출시 노트 변경 검사는 app뿐 아니라 journey/experience/feature/platform과 공통 Gradle 변경을 포함한다.
로컬 릴리스 검증 명령은 Crashlytics 매핑 업로드를 제외하며, 실제 배포 CI는 기존 업로드를 유지한다.

## 이번 분리의 검증 결과 (2026-10-23)

- 의존성 경계 검사 통과. 9개 모듈 사이 순환·앱 역참조 없음.
- JVM/Android 단위 테스트 24개 통과(기존 19개 + JourneyClock 5개).
- Android 35 에뮬레이터 기기 테스트 8개 통과(app 3개, train 5개).
- 전체 Android 모듈 Lint 오류 0개. 디버그 APK와 R8/리소스 축소를 적용한 릴리스 APK 빌드 성공.
- 8개 코스와 사계절·주야·날씨·화면 방향·렌더 모드 조합의 PNG 144장 캡처.
  분리 전 원본에서 확보한 비교 대상 68장은 분리 후 이미지와 픽셀 단위로 동일했다.
- 문자열·테마 등 리소스 값 329개와 이미지·폰트·라이선스 바이트가 원본과 동일함을 확인.
  병합된 앱 권한 8개도 변경 없음.
- 실제 MainActivity 시작, 세로↔가로 재생성, 프로세스 종료 후 같은 세션 복원을 확인.
  화면과 프로세스가 없는 상태에서 AlarmManager가 새 프로세스를 시작해 도착 알림을 게시하는 경로도 통과.
- CI YAML과 셸 구문, 기존 릴리스 버전 스크립트 테스트 6개 통과.

위 결과는 자동 테스트와 에뮬레이터 검증 범위다. 실제 Play 구매·구매 복원,
실기기 오디오 청취·장시간 배터리 검증은 수행하지 않았다.
