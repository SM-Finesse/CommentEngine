# 프롬프트 틀 (config-repo/prompts)

comment-engine이 llm-server `POST /v1/generate`에 보낼 `messages`를 만드는 틀입니다. 코드가 아니라 설정이라, 문구를 바꿔도 엔진을 다시 빌드하지 않습니다 (`docker compose restart comment-engine`).

| 파일 | 쓰는 곳 | 결과 |
|---|---|---|
| `sentence.yml` | `/v1/comments/light`의 항목 하나 | 한 문장 (40~60자) |
| `summary.yml` | `/v1/comments/light`의 항목 전체 | 2~3문장 (70~100자), 숫자 없음 |
| `section.yml` | `/v1/comments/section` | 1~2문장 (40~70자) |

틀에는 **분야 단어가 없습니다.** 분야 지식(지표 이름, 비교 기준, 서술어)은 칩이 요청에 담아 보냅니다 (설계서 5.1절, 6.1절).

## 1. 파일 칸

| 칸 | 뜻 | 누가 씀 |
|---|---|---|
| `version` | 틀 버전. 응답 `versions.prompt`에 담음 | 엔진 |
| `decoding` | `temperature`, `top_p`, `max_tokens` → `/v1/generate`에 그대로 | 엔진 (4단계) |
| `check.length` | 글자 수 범위 (공백 포함) | 7b `FORMAT` |
| `check.numbers` | `true`면 괄호 안 `value_text` 인용만 허용, `false`면 숫자 금지 | 7b `NUMBER` |
| `system`, `user` | 메시지 두 개의 본문 | 엔진 (2단계) |
| `item_line` | 항목이 여러 개인 틀에서 항목 한 줄 모양 (`summary`, `section`) | 엔진 (2단계) |
| `degree_rules` | `{{degree_rule}}`에 넣을 줄 (`sentence`만) | 엔진 (2단계) |
| `reliability_rules` | `{{reliability_rule}}`에 넣을 줄 | 엔진 (2단계) |
| `degree_forbidden` | degree별로 문장에 나오면 실패하는 말 (`sentence`만. 나머지는 `{}`) | 7b `DEGREE` |
| `leak_markers` | 문장에 나오면 실패하는 지시문 조각 | 7b `LEAK` |

## 2. 칸 채우기 (`{{…}}`)

| 자리 | 채우는 값 | 비었을 때 |
|---|---|---|
| `{{context.name}}` | 요청 `context.name` — 문장이 놓일 자리 (예: "○○ 분석 페이지 첫 화면") | `이 페이지` |
| `{{context.rules}}` | 요청 `context.rules[]` 한 줄에 하나, 앞에 `- ` | 그 줄을 지움 |
| `{{item.rules}}` | 항목 `rules[]` 한 줄에 하나, 앞에 `- ` (`sentence`만) | 그 줄을 지움 |
| `{{label}}`, `{{meaning}}`, `{{value_text}}` | 항목의 같은 이름 칸 그대로 | `meaning`이 비면 `뜻:` 줄을 지움 |
| `{{direction}}` | 항목 `direction`. **끝의 마침표는 떼고** 넣음 (틀이 마침표를 붙임) | — (필수 칸) |
| `{{degree_rule}}` | `degree_rules[항목 degree]` | — |
| `{{reliability_rule}}` | `reliability_rules[항목 reliability]`. `summary`·`section`은 항목 중 하나라도 `insufficient`면 넣음 | 그 줄을 지움 |
| `{{items}}` | 항목마다 `item_line`을 채운 줄, 입력 순서대로 | — (항목 1개 이상) |
| `{{section.title}}`, `{{section.description}}` | 요청 `section`의 같은 칸 | `description`이 비면 그 줄을 지움 |

"그 줄을 지움": 칸을 채운 결과가 빈 문자열이면 그 줄 전체를 지웁니다 (빈 `- `가 남지 않게).

채운 뒤 `{{`가 남아 있으면 틀이 잘못된 것이므로 엔진은 생성하지 않고 500을 냅니다 (배포 전에 시험으로 잡을 것).

## 3. 메시지

```json
[
  { "role": "system", "content": "<채운 system>" },
  { "role": "user",   "content": "<채운 user>" }
]
```

## 4. 칩이 보내야 하는 것 (예: 비교 기준·서술어)

모놀리식 `light_comment.py`에 있던 아래 규칙은 분야 규칙이라 틀에서 뺐습니다. 칩이 `context.rules` 또는 항목 `rules`로 보냅니다.

| 모놀리식 규칙 | 보내는 곳 |
|---|---|
| 첫 줄 무대 ("…전적 분석 페이지 첫 화면") | `context.name` |
| 연승·연패·티어·TR 금지, 다른 유저와 비교 금지 | `context.rules` |
| `_compare_rule()` 비교 기준 ("비교 기준으로 '상대 대비'를 문장 앞에 …") | 항목 `rules` |
| `_predicate_rule()` 서술어 ("서술어는 '앞섭니다', '밀립니다'를 …") | 항목 `rules` |
| 영어 약어 금지, 성향 지표를 잘하고 못함으로 쓰지 않기 (요약) | `context.rules` |
| 금지 낱말 (`_FORBIDDEN`, 다른 유저 낱말 등) | 항목 `must_not` |

## 5. 버전

- 문구를 바꾸면 `version`의 숫자를 올립니다 (`sentence-v1` → `sentence-v2`)
- light 응답의 `versions.prompt`는 두 틀을 이어 씀: `sentence-v1+summary-v1`
- section 응답은 `section-v1`

## 6. 출처

| 틀 | 원본 (`SM-Finesse/Finesse` main `3859825`) |
|---|---|
| sentence | `llm-server/app/light_comment.py` (`light-v5.6e`) `build_sentence()`, `NUMBER_RULE`, `COMMON_RULES` |
| summary | 같은 파일 `SUMMARY_SYSTEM`, `build_summary()` |
| section | `llm-server/app/prompt.py` `HEAVY_COMMON`, `build_heavy_messages()` |
| 검사 말 목록 | `llm-server/app/validation.py` `_INTENSIFIERS`, `light_comment.py` `_VAGUE`, `light6.py` `_ECHO` |
