# Passing By — 출시 전 체크리스트

> 2026-10-17 작성 · 기준 커밋 `c6c468c` · 출시 방식: **처음부터 결제 포함** (1·2코스 무료, 3~8코스 코스별 일회성 구매)
>
> 코드 위치는 줄 번호 대신 파일과 이름으로 적는다. 관련 명세는 [PASSING_BY_SPEC.md](PASSING_BY_SPEC.md).

---

## 1. 코드 — 출시 전에 반드시

- [x] **개발 도구 끄기** — `app/build.gradle.kts`의 `DEV_TOOLS`
  - debug에서만 `true`, release에서는 `false`다. 켜져 있으면 "모든 코스 열기" 스위치, 길게 눌러 여는 개발 패널, adb 실행 옵션(`MainActivity.applyDebugExtras`)이 모두 사용자에게 열린다.
  - 그래서 release 빌드로 성능을 잴 때는 HUD와 개발 패널이 없다. 필요해지면 release를 복사한 별도 빌드 타입(예: `perf`)을 만들어 거기서만 켠다.
- [x] **출시 서명** — `app/build.gradle.kts`의 release `signingConfig`
  - brayne과 같은 업로드 키(`kero-studio.jks`, alias `kero`)를 쓴다. 키 파일은 ethan-of-brain에서 복사해 이 프로젝트의 `keystore/`에 두었고(`.gitignore`의 `*.jks`로 제외), 경로와 비밀번호는 커밋하지 않는 `local.properties`의 `kero.upload.*`에 있다. 다른 기기에서 빌드하려면 둘 다 따로 옮겨야 한다.
  - 인증서 SHA-256 지문은 `C8:51:47:22:…:8E:0E:39:FA`. 업로드한 번들의 지문은 `keytool -J-Duser.language=en -printcert -jarfile app-release.aab`로 확인한다(한국어 로케일에서는 JDK 24 keytool이 오류를 낸다).
  - `local.properties`에 이 값이 없는 기기에서는 debug 키로 서명된다. 그 번들은 Play에 올릴 수 없다.
- [x] **앱 아이콘**
  - 사용자가 승인한 그림책풍 여행 열차·아치 다리의 적응형 아이콘을 `AndroidManifest.xml`에 연결했다. Android 13 이상 테마 아이콘용 단색 레이어도 포함한다.
  - 스토어용 512×512 PNG와 모양별 미리보기는 `design/launcher/`에 있다. 소스 위치와 파일 용도는 그 폴더의 `README.md` 참고.
- [x] **버전** — `version.properties`
  - 손으로 올린 첫 번들만 `versionCode 1`(`0.1.0-prototype`)이다. 그 뒤는 자동 배포가 `versionCode`를 1씩, 표시 버전을 `1.0.0001`, `1.0.0002`…로 올린다.
  - 표시 버전의 앞자리를 바꾸려면 `version_major`·`version_minor`를 고친다. patch는 0001부터 다시 시작하고 `versionCode`는 계속 올라간다.
  - 자동 배포를 켠 뒤에는 손으로 업로드하지 않는다. 로컬 빌드도 `versionCode 2`라서 다음 자동 배포와 번호가 겹친다.
- [ ] **AAB로 빌드** — 새 앱은 APK가 아니라 `./gradlew :app:bundleRelease`로 만든 `.aab`를 올린다.
- [x] **사용 통계·오류 보고(Firebase)** — `telemetry/Telemetry.kt`에서만 Firebase를 부른다.
  - Firebase 프로젝트 `passingby-c112d`. 설정 파일 `app/google-services.json`은 공개 저장소라 커밋하지 않는다(`.gitignore`). 다른 기기에서 빌드하려면 이 파일을 따로 옮기고, CI는 `GOOGLE_SERVICES_JSON` Secret으로 받는다.
  - release만 수집하고 debug는 끈다. debug에서 확인하려면 `./gradlew :app:installDebug -Ppassingby.telemetry=true`로 빌드하고, 개발 패널의 "테스트 크래시"로 Crashlytics 연결을 확인한다.
  - 광고 ID·기기 ID(SSAID)·광고 측정 권한은 매니페스트에서 뺐다. 처리방침의 "광고 ID 미사용"과 Play의 광고 ID 신고("사용 안 함")가 이것에 기대므로 라이브러리를 올릴 때 병합된 매니페스트에 `AD_ID`가 다시 들어오지 않았는지 본다.
  - 사용자는 설정 → 정보 보내기에서 끌 수 있다(기본 켜짐). 끄면 아직 보내지 않은 오류 보고도 지운다.
  - Firebase 콘솔: Analytics 데이터 보관 기간 2개월(기본값, 처리방침 3항과 같아야 함), Google 신호·Google Ads 연결은 켜지 않는다(처리방침 7항). Play Console과 Firebase를 연결하면 인앱 구매가 `in_app_purchase` 이벤트로 자동 기록된다.
  - 이벤트: `trip_start`·`trip_pause`·`trip_resume`·`trip_end_early`·`trip_arrive`·`rest_start`·`rest_end`·`trip_get_off`·`course_select`·`purchase_tap`·`purchase_result`·`restore_tap`·`course_unlock`·`billing_error`(stage·code)·`billing_products_missing`·`notification_permission`. 비치명 오류: 남은 시간 알림 실패, 도착 알림 실패, 결제 DEVELOPER_ERROR·ERROR.

## 2. 코드 — 결제 품질 (권장)

- [x] **구매 실패 문구** — `purchase_unavailable`의 한국어·영어를 실제 연결 실패에 맞는 재시도 안내로 변경했다(2026-10-23).
- [x] **구매 복원 결과 처리** — 실제 응답 후 완료·구매 없음·실패·대기를 구분하고, 확인 중 버튼 비활성화·15초 제한·재시도·중복 요청 병합을 구현했다. JVM 상태 전이 시험 통과(2026-10-23). Play 라이선스 계정 실결제 검증은 아래 실기기 시험에 남긴다.
- [x] **결제 대기 처리 구현** — PENDING 안내, 승인 전 잠금 유지·ack 제외, `onResume()` 재조회와 완료 구매 ack 재시도를 구현했다. 전체 조회·부분 이벤트를 구분하며 기존 권한을 합치는 정책을 유지한다. JVM 분류 시험 통과(2026-10-23).
- [ ] **Play 결제 회귀 확인** — 내부 테스트에서 설치한 R8 빌드로 지연 승인·앱 복귀·재설치 복원·오프라인·ack 실패 후 재시도·한국어/영어 안내를 확인한다. 연결된 실기기와 라이선스 계정이 없어 2026-10-23에는 실행하지 못했다.
- [ ] **환불해도 다시 잠기지 않음** — `JourneyViewModel`의 `onOwned` 처리
  - 열린 코스 목록(`TripStore.unlocked`)은 추가되기만 해서, 환불받아도 코스가 계속 열려 있다.
  - `allowBackup="true"`라서 이 목록이 백업을 통해 새 기기로도 넘어간다.
  - 그냥 둘지 정한다. 고친다면 복원에 성공했을 때 Play가 알려 주는 구매 목록으로 덮어쓴다.
- [ ] **서버 검증 없음** — 루팅 기기에서는 결제가 우회될 수 있다. 개인 개발 소액 상품에서는 흔히 감수하는 수준이지만, 알고 넘어간다.
- [ ] **오픈소스 고지** — 라이선스 화면에 서체 두 개(Pretendard, 고운바탕)와 행성·지상 이미지(NASA, Solar System Scope CC BY 4.0)의 출처가 있다. AndroidX와 Kotlin(Apache 2.0)도 배포할 때 라이선스 고지가 필요하다.

## 3. 실기기 시험

release 빌드를 테스트 트랙에 올리고 Play에서 설치해서 시험한다. 직접 설치(sideload)한 빌드보다 결제 시험에 확실하다.

- [ ] 구매 성공 시 코스가 열리는지, 구매 취소, 거절되는 테스트 카드
- [ ] 늦게 승인되는 테스트 카드 (결제 대기 → 몇 분 뒤 승인)
- [ ] 앱을 지우고 다시 설치한 뒤 구매 복원
- [ ] 비행기 모드에서 구매·복원을 눌렀을 때 나오는 문구
- [ ] 잠긴 코스 미리 보기 → 가격 표시 → 구매까지 이어지는지
- [ ] 위 항목 전부와 남은 시간 알림·도착 알림을 R8이 적용된 빌드에서 확인
- [ ] **앱 프로세스가 종료된 뒤의 여행** — 포그라운드 서비스가 없어서(명세 10.5) 백그라운드에서 프로세스가 자주 종료된다.
  - 달리는 중·멈춘 동안·쉬는 중 각각, 홈으로 나간 뒤 `adb shell am kill com.kero.passingby`(백그라운드 프로세스만 종료)로 끄거나 최근 앱 목록에서 지운다.
  - 남은 시간 알림이 계속 줄어드는지, 끝나는 시각에 그 알림이 사라지는지, 그 뒤 도착 안내 알림이 오는지, 앱을 다시 열면 여행이 이어지는지(도착 뒤에 열면 도착 화면이고 소리가 다시 울리지 않는지) 본다.
  - 도착 안내는 늦게 와도 된다(명세 10.5). 다만 오지 않으면 안 되므로, 절전이 강한 제조사 기기(삼성·샤오미 등)에서 화면을 끈 채 25분 여행을 하고 도착 알림이 결국 오는지, 얼마나 늦었는지 적어 둔다.
- [ ] **언어 전환** — 설정 → 언어에서 기기 설정·한국어·영어를 바꿔 가며 화면과 알림 문구가 따라가는지
  - Android 12 이하 기기에서 꼭 확인한다. 13 이상은 시스템 앱별 언어를 쓰고, 12 이하는 앱이 직접 저장해 화면에 입히는 다른 경로라서(`AppLanguage`) 에뮬레이터 API 35에서만 확인했다.
  - Play에서 설치한 빌드로도 확인한다. 번들의 언어별 분할을 꺼 두었는데(`bundle.language.enableSplit = false`), 켜져 있으면 기기 언어가 아닌 쪽 문구가 설치되지 않는다.

## 4. Play Console — 진행 순서대로

1. [ ] **계정 유형 확인 (가장 오래 걸린다)**
   - 2023년 11월 13일 이후에 만든 개인 계정이면, 테스터 12명 이상이 14일 연속 참여하는 비공개 테스트를 거쳐야 프로덕션 출시를 신청할 수 있다.
   - 중간에 테스터가 12명 아래로 떨어지면 14일이 처음부터 다시 시작된다는 얘기도 있다. 조직 계정은 이 요건이 없다.
   - 테스터 모집부터 시작한다.
2. [ ] **결제 프로필(판매자 계정)**
   - 정산 계좌와 세금 정보를 등록한다.
   - 판매를 하면 이름, 주소, 연락처가 스토어에 공개될 수 있으니 공개할 주소를 미리 정해 둔다.
3. [ ] **앱 만들기**
   - 기본 언어는 한국어, **무료 앱**으로 설정한다. 인앱결제 모델이라 무료가 맞고, 한 번 무료로 낸 앱은 유료 앱으로 바꿀 수 없다.
   - 패키지명 `com.kero.passingby`는 첫 업로드 뒤에는 바꿀 수 없다.
4. [ ] **Play 앱 서명 설정과 업로드 키 등록**
5. [x] **내부 테스트 트랙에 AAB 업로드** — 보통 결제 라이브러리(BILLING 권한)가 들어간 번들을 한 번 올려야 인앱 상품을 만들 수 있다.
   - 2026-10-17: 첫 번들(`versionCode 1`, 상품 4개만 포함)을 손으로 올려 내부 테스트로 출시했고, GitHub Secrets 5개를 등록했다. 자동 배포는 아직 한 번도 돌리지 않았다.
   - 첫 번들(`versionCode 1`)은 Play Console에서 손으로 올리고 내부 테스트로 **출시**까지 한다. API로는 새 앱의 첫 업로드를 할 수 없고, 출시한 적 없는 앱에는 자동 배포가 초안만 만들 수 있다.
   - 그 뒤는 `git push origin main:release`로 자동 배포한다(`.github/workflows/android-internal-release.yml`). 빌드 → 업로드 키 서명 확인 → 내부 테스트 업로드 → `android-internal-<versionCode>` 태그 순서로 진행한다.
   - 자동 배포 준비: Google Cloud에서 Google Play Android Developer API 사용 설정, 서비스 계정을 Play Console "사용자 및 권한"에 이 앱 권한(테스트 트랙 출시)으로 초대, GitHub 저장소 Secrets에 `PLAY_SERVICE_ACCOUNT_JSON`·`UPLOAD_KEYSTORE_BASE64`·`UPLOAD_STORE_PASSWORD`·`UPLOAD_KEY_ALIAS`·`UPLOAD_KEY_PASSWORD` 등록. brayne에 쓰는 서비스 계정을 그대로 초대해도 된다.
   - What's new는 항상 한국어(`release-notes/whatsnew-ko-KR`)와 영어(`release-notes/whatsnew-en-US`) 두 버전으로 작성한다. 각 언어는 비어 있지 않은 500자 이내이며 같은 변경사항을 담는다. 앱 코드가 바뀌면 직전 성공 배포 이후 변경사항에 맞춰 두 파일을 모두 갱신한다. CI가 두 언어와 글자 수를 검증하고 함께 업로드하며, 한국어 커밋 제목만으로 대체하지 않는다.
6. [x] **인앱 상품 6개 등록 (일회성 상품)**
   - 2026-10-17: 6개 모두 만들어 활성화했다. 상품마다 구매 옵션 `buy`(구입, 이전 버전과의 호환성) 하나뿐이고 혜택(오퍼)은 없다. 앱이 호환 옵션의 가격만 읽으므로 혜택을 추가하기 전에 결제 코드를 먼저 고친다.
   - ID는 코드(`Course.productId`, 나라별 `content/*Courses.kt`)와 정확히 같아야 한다: `course_korea_jeolla`, `course_korea_gangneung`, `course_china_north`, `course_china_south`, `course_portugal`, `course_france`
   - 한 번 만든 ID는 지워도 다시 쓸 수 없으니 확정한 뒤에 만든다.
   - 이름, 설명, 가격을 넣고 **활성화**해야 앱에서 보인다.
7. [ ] **라이선스 테스트** — 설정 → 라이선스 테스트에 테스트용 Gmail 계정을 등록한다. 이 계정으로 한 결제는 실제로 청구되지 않는다.
8. [ ] **앱 콘텐츠**
   - [ ] 개인정보처리방침 URL — 데이터를 수집하지 않는 앱도 필수
   - [ ] 데이터 보안 양식 — 내부 테스트에만 있는 동안은 면제이고, 비공개·공개 테스트나 프로덕션에 올리는 순간부터 필수다. 결제 관련 항목을 신고해야 하는지는 도움말을 확인한다.
     - Firebase를 넣었으므로 "수집함"으로 신고한다: 앱 활동(앱 상호작용), 앱 정보 및 성능(비정상 종료 로그, 진단), 기기 또는 기타 ID(Firebase 설치 ID·앱 인스턴스 ID). 목적은 분석·앱 기능, 전송 중 암호화됨. Google은 서비스 제공자라 "공유"에는 넣지 않는다.
     - 광고 ID: "사용하지 않음".
   - [ ] 광고 없음 신고, 콘텐츠 등급 설문
   - [ ] 타깃 연령 — 13세 미만을 넣으면 가족 정책 요건이 추가된다.
   - [ ] **포그라운드 서비스 권한 신고 — 해당 없게 만든다** — 2026-10-18에 포그라운드 서비스를 뺐다(명세 10.5).
     - 자동 배포로 올린 versionCode 2~4 번들(태그 `android-internal-2`~`4`)에는 `FOREGROUND_SERVICE_SPECIAL_USE`가 들어 있어서 앱 콘텐츠에 이 신고 항목이 뜬다.
     - 권한을 뺀 빌드를 올려 권한이 든 번들이 활성 상태로 남은 트랙이 없게 한 뒤, 이 항목이 사라지는지 확인한다. 그래도 신고를 요구하면 Play Console 도움말에서 처리 방법을 확인한다.
     - 라이브러리를 올릴 때 병합된 매니페스트에 `FOREGROUND_SERVICE` 권한이나 `foregroundServiceType`이 있는 서비스가 들어오지 않았는지 본다.
9. [ ] **스토어 등록정보**
   - 앱 이름 — 명세 15장에 이름 확정과 상표 검색이 미결로 남아 있다.
   - 도착 알림은 늦을 수 있는 안내라서(명세 10.5) "정확한 시각에 울리는 알람"처럼 약속하는 설명을 쓰지 않는다.
   - 짧은 설명(80자), 자세한 설명, 아이콘 512×512, 그래픽 이미지 1024×500, 휴대전화 스크린샷 2장 이상
   - 앱이 영어도 지원하므로 영어 등록정보(번역)와 영어 화면 스크린샷도 함께 넣는다.
   - 폴더블·태블릿 레이아웃이 있으니 태블릿 스크린샷도 넣으면 좋다.
10. [ ] **배포 국가** — 앱 문구는 한국어·영어를 지원한다(그 밖의 기기 언어는 영어로 보인다). 해외로 넓힐 수는 있지만, EU에 배포하면 판매자 연락처 공개 같은 추가 신고가 필요해지니 처음에는 한국과 영어권 몇 나라부터 시작하는 편이 안전하다.
11. [ ] **비공개 테스트 14일 → 프로덕션 출시 신청 → 검토**
12. [ ] **공유 링크 확인** — `친구에게 알려주기`는 Play 스토어 주소를 보낸다. 내부·비공개 테스트 동안에는 테스터가 아닌 사람이 이 주소를 열면 앱을 찾을 수 없다고 나온다. 프로덕션 공개 뒤 카카오톡으로 한 번 보내서 미리보기(아이콘·이름)가 뜨고 스토어로 이어지는지 확인한다.

## 5. 확인하지 못한 것

- [ ] 개인 개발자가 인앱결제로 판매할 때 **사업자 등록이나 통신판매업 신고**가 필요한지는 공식 근거를 찾지 못했다. 정산을 받기 전에 관할 구청이나 세무사에게 확인한다.

## 6. 정해야 할 것

- [ ] 코스 가격 (명세 12장 "가격 미정")
- [ ] 환불받은 코스를 다시 잠글지 (2장)
- [ ] 앱 이름 (명세 15장)

---

### 참고 자료

Play 정책은 바뀔 수 있으니 진행할 때 Play Console 도움말 원문으로 다시 확인한다.

- [Google Play 12-tester rule (Choicely)](https://www.choicely.com/blog/google-play-12-tester-rule)
- [Google Play closed testing requirements 2026 (Testers Community)](https://www.testerscommunity.com/blog/google-play-closed-testing-requirements-2026)
- [데이터 보안 섹션 안내 (Play Console 도움말)](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Google Play data safety section (iubenda)](https://www.iubenda.com/en/help/38933/-google-play-new-safety-section)
- [1인 앱 개발자 주소 노출 경험담 (다모앙)](https://damoang.net/development/1222)
