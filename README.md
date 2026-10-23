# PassingBy

기차 창밖 풍경과 함께하는 집중 타이머. 앱 하나 안에 여행 경험을 추가할 수 있도록 모듈을 분리한다.

- [현재 모듈 구조·세션 수명·검증 방법](docs/architecture/modules.md)
- [제품 명세](PASSING_BY_SPEC.md)
- [출시 체크리스트](RELEASE_CHECKLIST.md)

```sh
rtk proxy ./gradlew checkModuleBoundaries testDebugUnitTest :journey:domain:test :app:assembleDebug
```

로컬 Android SDK 설정과 app/google-services.json은 저장소에 포함하지 않는다.
