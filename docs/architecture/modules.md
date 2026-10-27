# 앱 하나의 여행 경험 모듈화

13개 Gradle 모듈 분리와 공통 세션 런타임·경험 제공자 연결을 완료했다.
기차·비행기·배·우주선을 각각 경험 제공자로 등록한다. 비기차 모드의 공통 항해 수명과 창틀은 voyage가 담당한다.

## 모듈과 의존성

| 모듈 | 소유하는 코드 | 직접 프로젝트 의존성 |
| --- | --- | --- |
| `:app` | Android 진입점, AppGraph, 앱 수명 세션·스케줄러·알림/통계 연결, 앱 빌드 설정 | 구현 모듈을 조립 |
| `:journey:domain` | Android 없는 시계·상태 머신·SessionRuntime·JourneyProgram·저장 계약 | 없음 |
| `:journey:data` | SharedPreferences 설정과 원자적 세션 체크포인트 | domain |
| `:experience:api` | 경험 제공자·레지스트리·코스/역 메타데이터·읽기 전용 장면·렌더링/환경 설정 계약 | domain |
| `:experience:train` | TrainExperienceProvider, 코스·운행 계획·풍경·날씨·렌더러·환경음과 기차 리소스 | api, domain, telemetry |
| `:experience:voyage` | 비기차 공통 항해 진행·복원·창틀·환경음 | api |
| `:experience:airplane` | 비행기 코스·구름·날개·리소스 | api, voyage |
| `:experience:ship` | 배 코스·바다·섬·파도·리소스 | api, voyage |
| `:experience:spaceship` | 태양계 코스·천체·별·행성 표면·리소스 | api, voyage |
| `:feature:journey` | Compose 화면·표시 상태 ViewModel·설정·수첩·폰트·언어·구매 UI | domain, api, billing, telemetry |
| `:platform:notifications` | 알림·AlarmManager·채널 리소스 | telemetry |
| `:platform:billing` | Play Billing SDK와 구매 조회/승인/복원 | telemetry |
| `:platform:telemetry` | Firebase SDK와 통계/오류 보고 | 없음 |

feature → train 의존성은 제거했다. 화면은 JourneyCourse·ExperienceScene·ExperienceProgram을 사용하며,
기차의 CoursePlan·SceneState·SceneRenderer·AmbientSound를 생성하거나 가져오지 않는다.
공통 시간대·계절·날씨 선택과 문구는 api가, 지형과 계절색 합성은 train이 소유한다.

## 세션 실행과 수명

AppGraph가 ExperienceRegistry에 구현을 등록하고 저장된 코스의 제공자로 JourneyProgram을 만든다.
SessionRuntime은 앱 프로세스 수명으로 유지되며, ViewModel은 관찰을 해제해도 런타임을 종료하지 않는다.
UI 리소스는 releasePresentation으로 정리하고 알람 처리에는 렌더러·환경음을 생성하지 않는다.
다른 제공자의 코스를 고르면 새 프로그램으로 교체하고 이전 표현 자원을 해제한다.

상태 머신은 단조 시계로 출발·감속 정지·재개·중단·도착·휴식 전이를 판정한다.
주입된 단일 실행 흐름 스케줄러가 기한과 역 통과를 확인한다. 프레임 누적은 사용하지 않는다.
`onFrame`은 현재 시각으로 풍경·소리·표면색을 갱신하며 세션 전이·저장·알림을 실행하지 않는다.
도착·휴식 종료·역 도장·통계·알림 소비는 ViewModel 밖에서 처리한다.
진행 중인 세션 시간과 다음 승차권 선택 시간을 구분하고, 복원 시 완료 전이를 재발행하지 않는다.

기차의 누적 거리·방향 반전·코스 계획은 TrainProgram이 소유한다. 도착역에서 휴식 후 돌아갈 때도
이전 계획의 종착 위치를 기준으로 새 계획을 연결한다. 개발용 탐색도 런타임의 같은 전이를 사용한다.

## 저장과 백그라운드 복원

진행 상태·코스·방향·역 도장·일일/누적 완료 기록은 SharedPreferences의 한 편집으로 저장한다.
세션 식별자로 같은 완료 체크포인트의 재시도가 기록을 늘리지 않게 한다. 저장소 계약에는
개별 완료 기록·활성 상태만 쓰는 우회 메서드를 두지 않는다.
기존 저장 키와 숫자 배열을 유지하고, 식별자가 없는 기존 세션은 안정적인 식별자로 복원한다.
단조 시계가 무효가 되는 재부팅은 기존 boot count 검사로 걸러낸다.
완료 알림의 전달 의도도 체크포인트에 함께 기록하고 전달 후 확인한다. 저장 직후 종료되면
재시작 시 대기 목록을 처리하며, 이미 표시된 같은 알림은 같은 ID로 갱신해 재알림을 줄인다.

TripAlarmReceiver는 저장된 세션을 읽고 런타임을 진행시킨다. 화면이 없는 새 프로세스에서도
도착 기록과 휴식 종료를 확정한다. 알림에는 감속·가속까지 반영한 실제 기한을 전달한다.
정확한 알람 권한이나 포그라운드 서비스는 추가하지 않는다. 시스템의 기존 부정확 알람 정책을 따른다.
알림 권한 허용 직후·앱 복귀·홈 전환 시에는 체크포인트 변경이 없어도 현재 세션의 알림을 다시 동기화한다.
첫 알림 게시가 권한 부족으로 실패하거나 알림이 사라져도 다음 경유지까지 기다리지 않고 카운트다운을 복구한다.

## 경계 검사와 CI

`checkModuleBoundaries`는 허용한 의존 그래프·순환·앱 R/BuildConfig 역참조·JVM/SDK 격리와
화면의 기차 구현 참조 및 세션 직접 기록을 검사한다. 임시 feature → train 예외는 없다.
main/PR에서도 경계 검사와 JVM 도메인 테스트를 실행한다. Firebase 설정 Secret을 사용할 수 있는
실행에서는 Android 단위 테스트와 전체 Lint도 실행하고, 배포 CI는 전체 검증을 필수로 유지한다.

## 검증 명령

```sh
rtk proxy ./gradlew checkModuleBoundaries testDebugUnitTest :journey:domain:test :app:assembleDebug :app:assembleDebugAndroidTest :experience:train:assembleDebugAndroidTest lintDebug
rtk proxy ./gradlew :app:assembleRelease -x :app:uploadCrashlyticsMappingFileRelease
rtk proxy ./gradlew :app:connectedDebugAndroidTest :experience:train:connectedDebugAndroidTest
```

`checkModuleBoundaries`는 허용한 의존 그래프, 순환 참조, 앱 R/BuildConfig 역참조,
JVM 도메인과 SDK 격리를 검사한다. feature → train 의존성과 화면의 세션 직접 기록도 금지한다.
앱을 포함한 Android 전체 테스트와 빌드에는 app/google-services.json이 필요하다. 실 Play 구매와 실기기 음질·배터리는
기기 테스트 결과와 구분한다.

CI도 같은 의존성 검사·단위 테스트·전체 Android 모듈 Lint를 실행한다.
출시 노트 변경 검사는 app뿐 아니라 journey/experience/feature/platform과 공통 Gradle 변경을 포함한다.
로컬 릴리스 검증 명령은 Crashlytics 매핑 업로드를 제외하며, 실제 배포 CI는 기존 업로드를 유지한다.

## 1단계 모듈 분리 검증 결과 (2026-10-23)

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

## 2단계 첫 작업 검증 결과 (2026-10-23)

- 전체 단위 테스트 43개 통과. 도메인 24개 중 SessionCoordinator 가상 시간 테스트는 19개다.
- 앱 기기 테스트 7개 통과(기존 복원 테스트 3개 + 세션 통합 테스트 4개).
  일시정지·중단 중복 호출, 완료 상태 복원 시 중복 기록 방지, 휴식 종료와 돌아오는 여정의 방향·풍경 연속성을 확인했다.
- 모듈 경계 검사와 전체 Android 모듈 Lint 오류 0개, 디버그·난독화 릴리스 빌드 성공.
- 실제 MainActivity의 세로/가로 회전, 프로세스 종료 후 같은 세션 복원,
  프로세스가 없는 상태의 도착 알림을 재검증했으며 앱 크래시가 없었다.

이전 첫 작업 이후 세션 스케줄러·이벤트 소비·저장 원자성·ExperienceProvider 연결을 완료했다.

## 2단계 완료 검증 결과 (2026-10-23)

- JVM/Android 단위 테스트 59개 통과. 가상 시간 런타임·완료 전달 복구·정확한 기한·제공자 선택을 포함한다.
- Android 35 에뮬레이터 앱 통합 테스트 11개, 기차 렌더링·오디오 테스트 5개 통과.
  화면 없는 도착·휴식 종료, 저장 재시도, 알림 대기 목록 확인, 기존 세션 복원과 왕복 풍경 연속성을 확인했다.
- 8개 Android 모듈 Lint 오류 0개, 모듈 경계 검사 통과. 디버그와 R8/리소스 축소 릴리스 APK 빌드 성공.
- 실제 MainActivity의 세로·가로 화면과 진행 제어 표시를 확인했다.
- 진행 중 앱을 백그라운드로 보낸 뒤 프로세스를 SIGKILL로 종료했다. AlarmManager가 새 프로세스를
  시작해 같은 세션을 ARRIVED로 저장하고 완료 기록을 한 번 늘렸으며, 도착 알림 게시와 대기 목록 확인을 완료했다.
  완료 상태에서 다시 프로세스를 종료·재실행해도 기록이 늘지 않았고 앱 크래시 로그가 없었다.
- 경계 검사 회귀 테스트 8개와 기존 배포 버전 테스트 6개 통과. 두 워크플로 YAML 구문을 확인했다.
- local.properties와 google-services.json이 없는 임시 프로젝트에서도 JVM 도메인 테스트가 통과했다.
- 한국어·영어 출시 노트를 함께 갱신했고 각 파일의 500자 제한을 확인했다.

실제 Play 구매·기기 청취·장시간 배터리는 기존 별도 검증 범위로 유지한다.


## 비밀 승차권과 여행 모드 (2026-10-26)

- `experience:airplane`, `experience:ship`, `experience:spaceship`은 각각 코스·렌더러·리소스를 소유한다.
- `experience:voyage`는 api에만 의존하며, 무정차 항해 진행·체크포인트·반환 여정·공통 창틀·환경음 루프를 제공한다.
- feature는 각 구현 모듈을 직접 참조하지 않고 JourneyMode와 ExperienceRegistry로 코스를 선택한다.
- 기본 공개 모드는 기차뿐이다. 잠긴 사용자는 다른 모드의 메뉴·코스·수첩 항목에 접근할 수 없다.
- 설정 맨 아래 Passing By를 7회 연속 탭하면 비밀 승차권 입력창이 열린다(탭 간격 1.2초 이내).
- 올바른 코드를 입력하면 설치별로 해제되며, 설정에 여행 모드 메뉴가 나타난다. 설정에서 다시 잠글 수 있다.
- 입력 코드·원문 비밀값은 저장/통계에 남기지 않는다. 무설정 빌드는 입력창도 열리지 않는다.
- 검증값은 무시되는 local.properties의 `passingby.preview.code.sha256`, Gradle 속성 `passingby.previewCodeSha256`,
  또는 CI의 `PASSINGBY_PREVIEW_CODE_SHA256`으로 주입한다. 코드의 UTF-8 SHA-256 소문자 64자리만 넣는다.
- 잠금 해제 표식은 noBackupFilesDir에 저장해 다른 설치로 복원하지 않는다. 검증값 변경 시 기존 해제는 무효다.
- 이것은 로컬 미리보기 잠금이다. 서버에서 사용자 신원을 확인하는 권한 시스템은 아니다.
- 정식 공개는 `-Ppassingby.publicModes=train,spaceship` 등으로 제어한다. 비밀 입력 없이 같은 메뉴·선택 화면으로 진입한다.
- 모드 전환과 다시 잠그기는 DOCKED에서만 가능하다. 진행/일시정지/도착/휴식 중인 세션은 교체하지 않는다.
- 비공개/삭제 모드가 저장돼 있으면 기차로 돌아가며 이전 모드의 시계를 기차에 잘못 복원하지 않는다.
- 우주선은 `태양계 탈출` 단일 코스다. 지구 → 달 → 목성 → 토성 → 명왕성 → 태양계 밖으로 이어진다.
  중간 정착지는 달·목성·토성·명왕성 4곳이며 화면에는 `다음 정착지`, `… 곁에 머무는 중…`으로 안내한다.
  명왕성은 도착지가 아닌 중간 경유지이며 마지막 구간에서는 모든 행성을 뒤로 남긴다.
  기존 11개 코스 ID는 새 코스의 별칭으로 해석해 모드·진행 중 타이머·미처리 완료 알림을 복원한다.
  천체의 색·지형은 기존 Canvas 일러스트를 사용하며 거리·크기·비행 시간·경로는 집중 타이머에 맞춘 연출이다.
- 출발 상승 3초·궤도 회전 1초·달 경유 12초 이후의 시간표를 사용한다. 짧은 세션은 비례 축약한다.
  행성 접근·이탈은 각각 최대 8초이며 중간 구간은 순항한다. 별은 진행률 대신 세션 초 단위로 흘러
  긴 집중 시간에도 비행 속도감이 유지된다. 왕복·진행 바·기록·캡션·위치 탐색도 같은 시간표를 공유한다.
  순항 중에는 작은 화성·위성·천왕성·해왕성이 배경으로 흐르고, 약 6.5초마다 짧은 별똥별이 스친다.
  작은 천체는 정착지·남은 시간에 추가되지 않으며, 두 연출 모두 세션 시계를 따라 일시정지·탐색·왕복에 반응한다.
  `VoyageAudio`를 통해 우주선의 연속 PCM 엔진/선내/기동음을 주입하며 다른 모드의 음향과 구분한다.
- 비행기는 인천→제주, 배는 부산→제주의 기본 코스를 제공한다.
- 제품 계획: 새 코스는 추후 코스별 약 1,500원 일회성 구매 아이템으로 연결한다.
  현재는 비밀 미리보기에서 무료 테스트한다. 기존 기차의 결제 상품/구매 복원은 유지한다.

검증: `checkModuleBoundaries testDebugUnitTest :journey:domain:test :app:assembleDebug lintDebug`.
기기 통합 테스트는 `:app:connectedDebugAndroidTest`로 실행하며, 비밀 진입 검증 시
`-Pandroid.testInstrumentationRunnerArguments.previewCode=<로컬 코드>`를 추가한다.

관측 논문·선정 근거·정확한 방문 순서·연출 범위는 [우주 코스 관측 근거](space-research.md)를 참고한다.
