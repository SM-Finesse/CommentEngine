#!/usr/bin/env bash
# 시험용 생성 서버(e2e/test-gen)가 llm-server POST /v1/generate 규약대로 답하는지
#
#   1. 맞는 요청 → 200, text · finish_reason · elapsed_ms
#   2. 틀린 요청 → 422 (messages 비었음, max_tokens 없음 · 범위 밖, 모르는 칸, role 틀림, JSON 아님)
#   3. FAIL_RATE=100 → 503,  BUSY_LOCK=1 에서 두 요청이 겹치면 하나는 503
#   4. GET /health → {"status":"ok"}
#
# 서버는 이 스크립트가 직접 띄우고 끝나면 내린다 (JDK 25의 java 가 PATH 에 있어야 함). 레포 루트에서:
#   bash e2e/test-gen-check.sh
# 확인한 환경: Linux (Ubuntu, OpenJDK 25). Windows Git Bash에서는 돌려보지 않았다.

set -euo pipefail
cd "$(dirname "$0")/.."

SRC=e2e/test-gen/TestGenServer.java
PIDS=()
cleanup() { for p in "${PIDS[@]}"; do kill "$p" 2>/dev/null || true; done; }
trap cleanup EXIT

fail() { printf '\n실패: %s\n' "$*" >&2; exit 1; }

start() { # 포트, 그 뒤는 환경 변수
  local port=$1; shift
  env "$@" java "$SRC" "$port" >"/tmp/test-gen-$port.log" 2>&1 &
  PIDS+=($!)
  for _ in $(seq 1 60); do
    curl -s -o /dev/null "http://localhost:$port/health" && return 0
    sleep 0.5
  done
  cat "/tmp/test-gen-$port.log"
  fail "test-gen :$port 이 뜨지 않음"
}

# 결과는 CODE, BODY
post() {
  local out
  out=$(curl -sS -w '\n%{http_code}' -X POST -H 'Content-Type: application/json' --data-binary "$2" "http://localhost:$1/v1/generate")
  BODY=${out%$'\n'*}
  CODE=${out##*$'\n'}
}

expect_code() { # 포트, 기대 코드, 요청, 설명
  post "$1" "$3"
  printf '%-34s %s %s\n' "$4" "$CODE" "$BODY"
  [ "$CODE" = "$2" ] || fail "$4: $2 이 아님"
}

OK='{"messages":[{"role":"system","content":"규칙"},{"role":"user","content":"한 문장으로 답하세요."}],"max_tokens":160,"temperature":0.4,"top_p":0.9}'

echo "== 1. 맞는 요청"
start 9290
expect_code 9290 200 "$OK" "모든 칸"
case "$BODY" in *'"text":"'*'"finish_reason":"stop"'*'"elapsed_ms":'*) ;; *) fail "응답 모양이 다름";; esac
expect_code 9290 200 '{"messages":[{"role":"user","content":"a"}],"max_tokens":1}' "필수 칸만 (max_tokens 1)"
expect_code 9290 200 '{"messages":[{"role":"user","content":"a"}],"max_tokens":512}' "max_tokens 512"

echo "== 2. 틀린 요청은 422"
expect_code 9290 422 '{"messages":[],"max_tokens":10}' "messages 비었음"
expect_code 9290 422 '{"messages":[{"role":"user","content":"a"}]}' "max_tokens 없음"
expect_code 9290 422 '{"messages":[{"role":"user","content":"a"}],"max_tokens":0}' "max_tokens 0"
expect_code 9290 422 '{"messages":[{"role":"user","content":"a"}],"max_tokens":513}' "max_tokens 513"
expect_code 9290 422 '{"messages":[{"role":"user","content":"a"}],"max_tokens":10,"seed":1}' "모르는 칸 seed"
expect_code 9290 422 '{"messages":[{"role":"tool","content":"a"}],"max_tokens":10}' "role 틀림"
expect_code 9290 422 '{"messages":[{"role":"user","content":"a"}],"max_tokens":10,"temperature":3}' "temperature 3"
expect_code 9290 422 '{"messages":[{"role":"user","content":"a"}],"max_tokens":10,"top_p":1.5}' "top_p 1.5"
expect_code 9290 422 'not json' "JSON 아님"

echo "== 3. 503 흉내"
start 9291 FAIL_RATE=100
expect_code 9291 503 "$OK" "FAIL_RATE=100"
start 9292 BUSY_LOCK=1 DELAY_MS=1500
post 9292 "$OK" & first=$!
sleep 0.5
expect_code 9292 503 "$OK" "BUSY_LOCK 겹친 두 번째 요청"
wait "$first"

echo "== 4. 헬스"
h=$(curl -sS http://localhost:9290/health)
echo "GET /health                        $h"
[ "$h" = '{"status":"ok"}' ] || fail "헬스 응답이 다름"

echo "== 통과: 시험용 생성 서버가 /v1/generate 규약대로 답한다"
