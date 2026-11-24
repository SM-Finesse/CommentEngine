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

## 띄우기 (Docker)

레포 루트에서 (JDK 25, Docker Desktop 필요):

```
./gradlew build -x test
docker compose up -d --build
```

| 주소 | 무엇 |
|---|---|
| http://localhost:8080 | gateway (모든 API의 입구) |
| http://localhost:9411 | Zipkin 추적 화면 |

- 꽂힌 칩 목록은 `.env`의 `COMPOSE_FILE` 한 줄입니다. 칩을 뽑으려면 그 줄에서 `:chips/<칩>/compose.yml`만 지웁니다
- 참고용 레포와 포트가 같아 같은 PC에서 동시에 띄우지 않습니다
- 엔진·화면 개발 중 생성 서버가 필요하면 시험용 생성 서버를 씁니다: `java e2e/test-gen/TestGenServer.java` (9200, llm-server `POST /v1/generate`와 같은 모양)
