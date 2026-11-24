#!/usr/bin/env bash
# 칩 꽂기·뽑기 시험: .env 의 COMPOSE_FILE 한 줄로 tetris 칩을 뽑고 다시 꽂을 수 있는가 (본체 수정 없음)
#
#   1. 꽂힌 상태: gateway /api/** → finesse-api → tetris-analytics 가 돈다
#      (틀린 유저명 a → 400 INVALID_USERNAME. TETR.IO는 부르지 않는다)
#   2. 뽑기: 칩 compose 없이 다시 띄운다 → 칩 컨테이너가 내려가고, gateway에서 /api/** 경로가 사라져 404
#   3. 다시 꽂기: .env 그대로 띄운다 → 다시 400 INVALID_USERNAME
#   4. 레포 파일은 하나도 바뀌지 않았다 (git status)
#
# 본체와 칩이 docker compose 로 떠 있는 상태에서 레포 루트에서 실행한다:
#   bash e2e/plug-test.sh
# 확인한 환경: 아직 없음 (CI에서 처음 돌림). Windows Git Bash에서는 돌려보지 않았다.
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/.."

GW=http://localhost:8080
fail() { printf '\n실패: %s\n' "$*" >&2; exit 1; }

before=$(git status --porcelain --untracked-files=all)

# 결과는 CODE, BODY. gateway가 다시 뜨는 중이면 000
stats() {
  local out
  out=$(curl -sS -w '\n%{http_code}' "$GW/api/v1/stats/a" 2>/dev/null) || out=$'\n000'
  BODY=${out%$'\n'*}
  CODE=${out##*$'\n'}
}

wait_code() { # 기대 코드, 설명
  for _ in $(seq 1 60); do
    stats
    [ "$CODE" = "$1" ] && { echo "$2: $CODE $BODY"; return 0; }
    sleep 2
  done
  echo "$2: $CODE $BODY"
  docker compose logs --no-color --tail 40 gateway || true
  fail "$2: $1 이 아님"
}

echo "== 1. 꽂힌 상태"
wait_code 400 "GET /api/v1/stats/a"
case "$BODY" in *'"error_code":"INVALID_USERNAME"'*) ;; *) fail "INVALID_USERNAME 이 아님";; esac

restore() { docker compose up -d --wait --wait-timeout 180 >/dev/null 2>&1 || true; }
trap restore EXIT

echo "== 2. 뽑기: COMPOSE_FILE=docker-compose.yml (칩 줄을 뺀 것과 같음)"
COMPOSE_FILE=docker-compose.yml docker compose up -d --wait --wait-timeout 180 --remove-orphans
left=$(docker ps -a -q --filter label=com.docker.compose.service=finesse-api; docker ps -a -q --filter label=com.docker.compose.service=tetris-analytics)
[ -z "$left" ] || fail "칩 컨테이너가 남아 있음: $left"
echo "칩 컨테이너: 없음"
wait_code 404 "GET /api/v1/stats/a (칩 없음)"

echo "== 3. 다시 꽂기: .env 그대로"
docker compose up -d --wait --wait-timeout 180
wait_code 400 "GET /api/v1/stats/a (다시 꽂음)"
trap - EXIT

echo "== 4. 레포 파일은 그대로"
after=$(git status --porcelain --untracked-files=all)
[ "$before" = "$after" ] || { diff <(echo "$before") <(echo "$after") || true; fail "시험 중에 레포 파일이 바뀜"; }
echo "바뀐 파일 없음"

echo "== 통과: .env 한 줄로 칩을 뽑고 꽂는다. 본체 파일 수정 없음"
