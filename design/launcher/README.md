# Passing By 앱 아이콘

사용자가 승인한 `drafts/train-travel-v1.png`의 그림책 느낌을 채택했다. 주황색·크림색 여행 열차가 초록 산 사이의 아치 다리를 지나는 장면이다. 열차·다리·앞쪽 풀은 투명 전경으로, 하늘·해·산은 불투명 배경으로 분리해 런처의 모양과 움직임에 대응한다.

## 원본과 앱 리소스

- `train-foreground.png`: 승인된 그림에서 ImageGen으로 분리한 1254×1254 RGBA 전경. 하늘과 산, 다리 아치 안쪽은 실제 투명 픽셀이다.
- `train-background.png`: 같은 질감과 색감으로 만든 1254×1254 불투명 풍경 배경.
- `generation-prompts.txt`: 내장 ImageGen 도구에 사용한 최종 전경·배경 프롬프트.
- `app/src/main/res/drawable/ic_launcher_background.xml`: 풍경 비트맵을 전체 배경으로 표시한다.
- `app/src/main/res/drawable/ic_launcher_foreground.xml`: 열차 앞부분을 보호하는 상대 여백 5.55556%와 전경 비트맵을 정의한다. dp 고정 여백을 쓰지 않아 런처 크기가 바뀌어도 비율이 같다.
- `app/src/main/res/drawable-nodpi/ic_launcher_*_art.webp`: 앱에 포함하는 432×432 무손실 WebP 두 장. 전경의 투명도를 보존한다.
- `app/src/main/res/drawable/ic_launcher_monochrome.xml`: 같은 옆모습 열차와 아치 다리를 단색 벡터로 단순화했다. 표시는 중앙 66dp 안전 영역 안에 있다.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`: Android 8 이상용 적응형 아이콘. Android 13 이상은 단색 레이어로 테마 아이콘을 표시한다.

레이어는 108×108dp 기준으로 구성하며, 기본 보이는 영역은 중앙 72×72dp다. 열차 앞부분은 중앙 안전 영역에 두고, 뒤쪽 객차·다리·풍경은 가장자리까지 이어지는 그림으로 취급한다. 외곽 모양은 런처가 적용한다.

## 내보내기

아트워크 수정은 원본 PNG에서 진행한다. 다음 명령은 이미지 내용을 다시 그리지 않고 Android 비트맵, 스토어 파일과 확인용 이미지만 내보낸다. Python 3와 Pillow가 필요하다.

```sh
rtk proxy python3 design/launcher/export_icons.py
```

| 파일 | 용도 |
| --- | --- |
| `play-store-icon.png` | Play Console용 512×512 RGBA PNG. 완전 불투명하며 외곽 라운딩과 그림자가 없다. |
| `icon-preview.png` | 둥근 사각형 미리보기. 스토어 업로드용은 아니다. |
| `adaptive-preview.png` | 원형·둥근 사각형·스쿼클, 밝고 어두운 테마, 48/64/80px 크기 비교. 실제 앱에 들어가는 WebP를 사용한다. |
| `parallax-preview.png` | 배경 위에서 전경을 좌우로 움직였을 때의 모양 비교. |

테마 미리보기의 색상은 예시이며 실제 색상은 사용자의 시스템 테마를 따른다. 미리보기 모양과 움직임은 대표적인 조건을 나타내며 제조사별 런처 화면을 캡처한 것은 아니다.

참고: [Android 적응형 아이콘](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive), [Google Play 아이콘 규격](https://developer.android.com/distribute/google-play/resources/icon-design-specifications).
