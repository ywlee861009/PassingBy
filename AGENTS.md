@/Users/iyeong-u/.codex/RTK.md

## Git 커밋
- Codex가 새로 만들어 푸시할 모든 Git 커밋 메시지 끝에 `Co-authored-by: ywlee861009 <ywlee861009@gmail.com>` 트레일러를 넣고 커밋 전에 확인한다.

## 배포 출시 노트
- Google Play 배포의 What's new는 항상 한국어(`ko-KR`)와 영어(`en-US`) 두 버전으로 작성한다.
- `release-notes/whatsnew-ko-KR`와 `release-notes/whatsnew-en-US`에 같은 변경사항을 각 언어로 자연스럽게 작성하고, 각 파일은 비어 있지 않은 500자 이내로 유지한다.
- 앱 코드가 바뀐 배포는 직전 성공 배포 이후 변경사항에 맞춰 두 파일을 모두 갱신한다. 한국어만 커밋 제목으로 자동 생성하거나, 영어 파일에 한국어를 그대로 복사하지 않는다.
