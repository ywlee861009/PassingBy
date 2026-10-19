# 여행 시작 화면

기존 주황색·크림색 열차 아이콘의 그림책 질감을 이어 받은 전체화면 여행 그림이다.
내장 ImageGen 도구로 만들었고, 최종 프롬프트는 `generation-prompts.txt`에 있다.

- `journey-square.png`: 모든 화면에 쓰는 1:1 원본 한 장. 크림색 하늘, 초록 산, 아치 다리를 지나는 열차와 흰 들꽃.
- `app/src/main/res/drawable-nodpi/launch_journey.webp`: 앱에 포함하는 공통 WebP 리소스 한 장.
- `app/src/main/res/drawable/ic_splash_train.xml`: 시스템 시작 화면에 쓰는 배경 없는 열차·다리 그림.

Android 12 이상에서 앱의 첫 프레임 전에는 시스템 시작 화면이 나온다. 전체 이미지는 허용되지 않아
크림색 단색 배경과 작은 열차 그림으로 설정한다. 첫 프레임이 준비되면 시스템 화면을 즉시 제거한다.
이어지는 Compose 화면에서 여행 그림을 900ms 보여 준 뒤 400ms 동안 여행 화면으로 페이드한다.
여행 화면은 처음부터 뒤에서 준비되며, 탭이나 뒤로 가기로 그림을 일찍 닫을 수 있다.
프로세스의 첫 Activity에서만 보여 주므로 앱 복귀·화면 회전·언어 변경에서는 반복하지 않는다.

그림은 방향별 리소스 없이 `ContentScale.Crop`과 `Alignment.Center`로 화면을 빈틈없이 채운다.
비율을 유지해 확대하고 중앙 기준으로 넘치는 부분을 자른다. 세로 화면은 좌우, 가로 화면은 위아래가
잘리므로 열차 앞부분은 원본 중앙에 둔다. 앱 이름은 이미지에
굽지 않고 고운바탕으로 별도 표시한다. 스크린리더는 그림 아래의 승차권을 전환이 끝난 뒤 탐색한다.

내보내기에는 Python 3와 Pillow가 필요하다. 이미지 내용을 다시 그리지 않고 WebP로 변환한다.

```sh
rtk proxy python3 design/startup/export_artwork.py
```

플랫폼 제약: [Android 시작 화면](https://developer.android.com/develop/ui/views/launch/splash-screen).
