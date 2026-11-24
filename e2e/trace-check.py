"""한 요청이 Zipkin에 한 추적으로 이어지는지 (설계서 8장 추적, 교재 14장).

gateway → finesse-api → tetris-analytics 세 서비스가 한 추적(traceId) 안에 모두 나와야 통과.
(엔진이 들어오면 gateway → finesse-api → comment-engine 줄을 WANT 에 더한다 — 조각 8 이후)

본체와 tetris 칩이 docker compose 로 떠 있는 상태에서:
    python3 e2e/trace-check.py
요청을 한 번 보내고(틀린 유저명이라 TETR.IO는 부르지 않음) 최대 60초 동안 Zipkin을 본다.
"""
import json
import sys
import time
import urllib.error
import urllib.request

GATEWAY = "http://localhost:8080"
ZIPKIN = "http://localhost:9411"
WANT = {
    "gateway → finesse-api → tetris-analytics": {"gateway", "finesse-api", "tetris-analytics"},
}


def get(url):
    with urllib.request.urlopen(url, timeout=10) as r:
        return r.read().decode()


try:
    get(GATEWAY + "/api/v1/stats/a")
except urllib.error.HTTPError as e:
    print(f"요청 보냄: /api/v1/stats/a → {e.code} (400이 정상)")

found, last = {}, None
for _ in range(20):
    try:
        traces = json.loads(get(ZIPKIN + "/api/v2/traces?serviceName=gateway&limit=100"))
    except Exception as e:  # Zipkin이 아직 안 받았거나 뜨는 중
        traces, last = [], e
    for t in traces:
        names = {s.get("localEndpoint", {}).get("serviceName") for s in t} - {None}
        for label, services in WANT.items():
            if label not in found and services <= names:
                found[label] = f"trace {t[0]['traceId']} spans={len(t)} services={sorted(names)}"
    if len(found) == len(WANT):
        for label in WANT:
            print(f"OK: {label}: {found[label]}")
        sys.exit(0)
    time.sleep(3)

print("추적에 안 나온 경로:", [label for label in WANT if label not in found], last)
try:
    print("Zipkin에 보인 서비스:", get(ZIPKIN + "/api/v2/services"))
except Exception as e:
    print("Zipkin 서비스 목록도 못 받음:", e)
sys.exit(1)
