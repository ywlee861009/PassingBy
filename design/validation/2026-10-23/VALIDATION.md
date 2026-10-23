# 환경음·결제·계절 개선 검증 기록

2026-10-23 · 기준: `6be44c8` 이후 구현. 원래 범위는 [핸드오프](../../../IMPROVEMENT_HANDOFF.md)에 있다.

## 실행 환경과 결과

- macOS arm64, OpenJDK 17.0.18, Gradle 8.13, 저장소에 고정된 의존성, 로컬 Android SDK. 기기: Android Emulator 36.4.9, `Pixel_8_API_35`, Android 15/API 35, arm64, SwiftShader, 오디오 출력 없는 실행.
- 디버그 APK·테스트 APK 빌드 성공. JVM 테스트 **12개**, 에뮬레이터 계측 테스트 **5개** 통과. 최종 렌더 캡처 생성 여부·타일 캐시 검증도 별도로 재실행해 통과했다.
- R8 및 리소스 축소를 적용한 release APK 빌드 성공. Crashlytics 매핑 업로드를 제외하고 로컬에서만 빌드했다. Play 배포는 실행하지 않았다.
- `lintDebug`, `lintRelease`: 오류 0개. 권장 KTX 사용·기존 리소스/의존성 관련 경고 등은 남아 있다(각 37개/34개).
- 기존 버전 계산 Python 테스트 6개 통과. 번역 대상 리소스 키의 한국어·영어 일치 확인. 출시 노트는 직전 성공 배포 `android-internal-10` 이후 변경사항에 맞춰 두 언어 모두 갱신했다(한국어 177자, 영어 365자).

## 환경음

`SlidingPeak`는 고정 배열의 단조 큐와 Long 인덱스를 사용한다. 무음·단일 피크·같은 피크 반복·증가/감소·고정 시드 난수·여러 링 회전·Int 범위를 넘는 인덱스에서 원래 128개 스캔과 최대값 및 **모든 PCM 샘플이 일치**했다. 링 버퍼 쓰기/포인터 이동/출력 순서와 gain·음량·합성식은 유지했다.

재생은 `finally`에서 stop과 release를 각각 시도한다. 빠른 끄기·켜기 8회와 재시작에서 스레드가 하나만 생기고 페이드 종료 뒤 사라짐을 계측 테스트로 확인했다. 실제 앱에서도 `ps -A -T -o CMD`로 전경 1개 → 홈 이동/페이드 종료 뒤 0개 → 복귀 뒤 1개를 확인했다.

리미터 단독 참고 측정([원자료](limiter-cpu.csv)): 22,050Hz의 4초 입력 88,200개, Random(11), ±2 범위의 동일 입력. 동일 debug 계측 APK에서 두 구현을 4회 워밍업하고, 순서를 번갈아 7회 측정했다. `Debug.threadCpuTimeNanos()` 중앙값은 전체 스캔 **215.236ms**, 큐 **2.710ms**이고, 모든 checksum이 같았다.

이 비교는 두 알고리즘을 같은 런타임에서 실행한 결과다. **환경음 스레드 전체 CPU·실기기 release 성능·배터리 개선율의 측정값이 아니다.** 스피커/이어폰 청취와 30분 배터리·발열 비교는 실기기 부재로 미실행이다.

## 구매 복원·결제

- 테스트한 상태: 초기 연결/자동 복귀/수동 복원 중첩, 연결 실패·시간 초과 뒤 재시도, 오래된 응답 배제, 구매 있음/없음/대기, 일반 자동 조회의 복원 안내 생략, 종료 후 콜백 배제.
- 거래 분류 시험: PENDING·미확인·알 수 없는 상품은 해제/ack하지 않는다. PURCHASED만 권한에 합치며, 이미 ack한 구매는 중복 ack하지 않는다. 비어 있거나 일부인 결과도 기존 로컬 권한을 지우지 않는다.
- 실제 설정 UI에서 한국어·영어 문구를 확인했다. Play 연결이 되지 않은 에뮬레이터에서 수동 복원을 누르면 확인 중 문구와 버튼 비활성화가 나타나고, 응답 제한 시간 뒤 다시 누를 수 있었다.
- `onResume()` 조회는 초기 조회와 합친다. 상품 조회에도 시간 제한이 있고, ack 실패는 1/2/4초 후 최대 3회 재시도하며 다음 구매 조회에서도 미확인 거래를 다시 처리한다. 구매 토큰·영수증·스토어 debugMessage는 분석/오류 이벤트로 전송하지 않는다.
- **Play 내부 테스트의 실제 구매·취소/거절·지연 승인·재설치 복원·ack 실패 재시도·R8 앱 동작은 미검증**이다. 연결된 실기기와 라이선스 테스트 계정이 필요하다. JVM 상태 전이 통과를 실제 결제 통과로 기록하지 않았다.

## 계절·화면·캐시

- `default`/`autumn` ID로 저장한다. 키가 없거나 알 수 없으면 기본 풍경이다. 새 TripStore 인스턴스의 복원 시험, 실제 UI의 기본↔가을 전환, 프로세스 재시작, 회전 뒤 가을 선택 유지, 일본 코스 변경 뒤 저장값 유지(`course=japan`, `season=autumn`)를 확인했다.
- 매 프레임 원본 시간대에서 계절색을 계산하고 비·눈 안개, 적설, 식생 명암과 윗면 눈 순서로 합성한다. 소나무·대나무·사이프러스와 집/하늘/역 이름판의 원색을 계절색으로 바꾸지 않는다. 단풍색에는 원래 명도와 밤의 청색을 반영한다.
- 8개 코스 기본·가을, 한국/일본의 낮·밤×맑음·소나기·함박눈×세로·가로, 8개 코스 DIRECT/BAKED/TILES 가을을 합쳐 **84장**을 생성했다. 아래 비교판은 실제 Canvas 렌더 캡처를 축소 배치한 것이다. 원본은 로컬 `app/build/handoff-validation-final/`에 있다.
- 빠른 계절 전환 뒤 TILES의 최종 캐시 키가 현재 선택과 맞는지 실 렌더링으로 확인했다. 화면 크기·테마/모드 세대·계절·시간대·날씨 키가 맞는 결과만 UI 스레드에서 적용한다. 전환 중인 중간색이 영구 캐시되지 않게 목표 시간대에서 굽는다.
- BAKED 고정 위치에서 100회 기본/가을 전환 전후 `bitmapBytes`가 8개 코스 모두 같았다. 지형 알파 마스크와 스프라이트를 계절 변경만으로 다시 생성하지 않는다. 식생 톤 배열은 재사용하며 계절색 계산에서 프레임당 새 배열/컬렉션을 만들지 않는다.

[8개 코스 비교](courses.webp) · [세로 날씨 비교](weather-portrait.webp) · [가로 날씨 비교](weather-landscape.webp) · [영어 설정](settings-en.webp) · [한국어 설정](settings-ko.webp)

렌더링 참고 측정([원자료](render-cpu.csv)): 창틀을 뺀 720×1280 소프트웨어 Canvas, 각 코스 첫 구간 거리의 45%, 고정 낮/맑음, BAKED, 위치 고정. 40프레임/캐시 워밍업 후 각 계절 20프레임 추가 워밍업, 100프레임 CPU 평균을 두 배치에서 확인했다. 표는 배치 평균(ms/프레임), 메모리는 해당 렌더러의 스프라이트·캐시 비트맵 합계(MiB)다.

| 코스 | 기본 CPU ms | 가을 CPU ms | 비트맵 MiB |
| --- | ---: | ---: | ---: |
| korea | 9.46 | 9.36 | 7.62 |
| japan | 11.06 | 11.20 | 7.43 |
| korea-jeolla | 10.33 | 9.74 | 7.63 |
| korea-gangneung | 11.29 | 11.45 | 8.70 |
| china-north | 7.74 | 7.70 | 5.98 |
| china-south | 11.52 | 11.20 | 7.79 |
| portugal | 10.12 | 9.77 | 6.95 |
| france | 8.90 | 9.41 | 6.77 |

일부 코스는 배치에 따라 빠르거나 느리게 나타났다. 이 소프트웨어 Canvas 참고 측정만으로 지속적인 성능 차이를 확정할 수 없다. GPU 프레임 간격·프레임당 전체 할당·장시간 메모리 추이와 실기기 발열은 별도 검증이 필요하다.

## 다시 실행하기

```sh
rtk proxy ./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
rtk proxy ./gradlew :app:connectedDebugAndroidTest
rtk proxy ./gradlew :app:assembleRelease :app:lintRelease -x :app:uploadCrashlyticsMappingFileRelease
```

캡처를 남길 때는 debug 앱과 androidTest APK를 에뮬레이터에 설치한 뒤 아래 계측 명령을 쓴다. 일반 Gradle 연결 테스트는 종료 시 APK/앱 저장소를 정리할 수 있다.

```sh
rtk proxy adb shell am instrument -w -e additionalTestOutputDir /sdcard/Android/media/com.kero.passingby/handoff-validation com.kero.passingby.test/androidx.test.runner.AndroidJUnitRunner
rtk proxy adb pull /sdcard/Android/media/com.kero.passingby/handoff-validation app/build/handoff-validation
```
