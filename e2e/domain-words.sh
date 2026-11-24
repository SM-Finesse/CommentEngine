#!/usr/bin/env bash
# 본체에 분야 단어가 0건인지 (설계서 10장). 칩(chips/)과 e2e/ 는 검사하지 않는다.
#
# 본체 = 슬롯(api) · 공통(util) · gateway · 엔진 · 서비스 선택 화면 · 본체 설정 · 본체 compose
# 아직 없는 폴더(조각 5의 microservices, 조각 11의 select-screen)는 생기면 자동으로 검사에 들어간다.
#   bash e2e/domain-words.sh
set -euo pipefail
cd "$(dirname "$0")/.."

BODY=(api util spring-cloud microservices select-screen config-repo docker-compose.yml)
present=()
for p in "${BODY[@]}"; do [ -e "$p" ] && present+=("$p"); done
echo "검사 대상: ${present[*]}"

# --untracked: 아직 커밋하지 않은 새 파일도 같이 본다 (로컬에서 돌릴 때)
if hits=$(git grep --untracked -n -i -E 'tetris|tetrio|finesse' -- "${present[@]}"); then
  echo "$hits"
  printf '\n실패: 본체에 분야 단어가 %s건 있음\n' "$(echo "$hits" | wc -l)" >&2
  exit 1
fi
echo "통과: 본체 분야 단어 0건"
