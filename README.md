# CommentEngine

정형 수치를 받아 AI 코멘트를 만들어 주는 범용 엔진입니다. "본체(엔진) + 칩" 구조로, 칩을 꽂으면 그 분야의 서비스가 됩니다. 첫 번째 칩은 TETR.IO 통계 코멘터리 서비스 **Finesse**입니다.

## 이 레포와 참고용 레포

| 레포 | 용도 |
|---|---|
| `SM-Finesse/CommentEngine` (여기) | 팀이 설계서를 보고 직접 개발하는 본 레포 |
| [`SM-Finesse/MSA-CommentEngine-reference`](https://github.com/SM-Finesse/MSA-CommentEngine-reference) | 먼저 만들어 본 참고용 구현. 구조를 확인하는 정답지이자 시연 대비책. 이 레포에서는 수정하지 않음 |

참고용을 그대로 옮기지 않습니다. 설계서에서 장점은 살리고 단점은 보완한 뒤, 그 설계대로 다시 만듭니다.

## 진행 방법

- 작업 규칙: [CONTRIBUTING.md](CONTRIBUTING.md)
- 업무: GitHub 이슈로 할당. 이슈 하나 = 작업 브랜치 하나 = PR 하나
- 리뷰·병합: 박덕현 (MSA 담당)
