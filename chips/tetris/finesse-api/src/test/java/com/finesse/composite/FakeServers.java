package com.finesse.composite;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 2일차 시험용 가짜 tetris-analytics와 가짜 comment-engine (JDK HttpServer). 조각 4 StatsRelayTest와 같은 방식.
 */
final class FakeServers {

    private FakeServers() {
    }

    static final JsonMapper JSON = JsonMapper.builder().build();

    // ---------------- 가짜 tetris-analytics ----------------

    /** picks 3개 유저의 updated_at — 바꾸면 캐시 열쇠가 바뀐다 */
    static volatile String updatedAt = "2026-11-24T09:00:00Z";

    static final String NOT_FOUND_JSON =
            "{\"error_code\":\"USER_NOT_FOUND\",\"message\":\"존재하지 않는 TETR.IO 유저입니다\"}";

    /**
     * 유저명(경로 끝)으로 답을 고른다: cold = 콜드스타트, nopick = 고른 지표 0개, nosuch = 404, 그 밖 = picks 3개
     * (delta_comeback high · delta_app normal · session_vs_slope low).
     */
    static HttpServer analytics() {
        return start(0, ex -> {
            String path = ex.getRequestURI().getRawPath();
            String user = path.substring(path.lastIndexOf('/') + 1);
            switch (user) {
                case "cold" -> reply(ex, 200, """
                        {"username":"cold","cold_start":true,"match_count":3,"updated_at":"2026-11-24T09:00:00Z"}""");
                case "nopick" -> reply(ex, 200, """
                        {"username":"nopick","cold_start":false,"match_count":40,"updated_at":"2026-11-24T09:00:00Z",
                         "delta_metrics":{"delta_comeback":0.2},
                         "highlight_judgement":{"ranges_version":"r","range_group":"t1","metrics":[],"picks":[]}}""");
                case "nosuch" -> reply(ex, 404, NOT_FOUND_JSON);
                default -> reply(ex, 200, statsWithPicks(user));
            }
        });
    }

    static String statsWithPicks(String user) {
        return """
                {"username":"%s","cold_start":false,"match_count":40,"updated_at":"%s",
                 "delta_metrics":{
                   "attack":{"delta_app":0.12,"delta_weighted_app":0.05},
                   "delta_comeback":0.36645,
                   "session_vs_slope":-0.5,
                   "reliability":{"delta_app":{"low_sample":true}}},
                 "highlight_judgement":{"ranges_version":"r","range_group":"t1","metrics":[],"picks":[
                   {"key":"delta_comeback","group":"comeback","value":0.11,"q1":0,"q3":0,"zone":"high"},
                   {"key":"delta_app","group":"attack","value":0.12,"q1":0,"q3":0,"zone":"normal"},
                   {"key":"session_vs_slope","group":"session","value":-0.5,"q1":0,"q3":0,"zone":"low"}]}}
                """.formatted(user, updatedAt);
    }

    // ---------------- 가짜 comment-engine ----------------

    enum EngineMode { OK, FAIL_APP, EMPTY_SUMMARY, ERROR }

    static final class Engine {
        volatile EngineMode mode = EngineMode.OK;
        volatile int errorStatus;
        volatile String errorCode;
        volatile String retryAfter;
        final AtomicInteger hits = new AtomicInteger();
        volatile String lastBody;
        HttpServer server;

        void reset() {
            mode = EngineMode.OK;
            errorStatus = 0;
            errorCode = null;
            retryAfter = null;
            hits.set(0);
            lastBody = null;
        }

        void error(int status, String code, String retryAfter) {
            this.mode = EngineMode.ERROR;
            this.errorStatus = status;
            this.errorCode = code;
            this.retryAfter = retryAfter;
        }

        /** 포트를 정해 띄운다 (0이면 아무 포트) */
        void start(int port) {
            server = FakeServers.start(port, this::handle);
        }

        void stop() {
            if (server != null) {
                server.stop(0);
                server = null;
            }
        }

        int port() {
            return server.getAddress().getPort();
        }

        private void handle(HttpExchange ex) throws IOException {
            hits.incrementAndGet();
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastBody = body;
            if (mode == EngineMode.ERROR) {
                if (retryAfter != null) {
                    ex.getResponseHeaders().add("Retry-After", retryAfter);
                }
                reply(ex, errorStatus, "{\"error_code\":\"" + errorCode + "\",\"message\":\"engine says no\"}");
                return;
            }
            List<String> keys = new ArrayList<>();
            for (JsonNode item : JSON.readTree(body).path("items")) {
                keys.add(item.path("key").asString());
            }
            StringBuilder items = new StringBuilder();
            List<String> failed = new ArrayList<>();
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                if (mode == EngineMode.FAIL_APP && key.equals("delta_app")) {
                    failed.add(key);
                    continue;
                }
                if (!items.isEmpty()) {
                    items.append(',');
                }
                int attempts = i == 1 ? 2 : i == 2 ? 1 : 0; // 최댓값 2
                items.append("{\"key\":\"").append(key).append("\",\"sentence\":\"엔진 문장 ").append(key)
                        .append(".\",\"attempts\":").append(attempts).append('}');
            }
            // 엔진 순서를 믿지 않는지 보려고 items를 뒤집어 보낸다
            String summary = mode == EngineMode.EMPTY_SUMMARY ? "\"\"" : "\"엔진 요약입니다.\"";
            String json = "{\"summary\":" + summary + ",\"items\":[" + reversed(items.toString()) + "],\"failed\":"
                    + JSON.writeValueAsString(failed)
                    + ",\"checks\":{\"NUMBER\":0},\"versions\":{\"engine\":\"e1\",\"prompt\":\"p1\",\"model\":\"m1\"}}";
            reply(ex, 200, json);
        }

        private static String reversed(String items) {
            if (items.isEmpty()) {
                return items;
            }
            List<String> parts = new ArrayList<>(List.of(items.split("(?<=\\}),(?=\\{)")));
            java.util.Collections.reverse(parts);
            return String.join(",", parts);
        }
    }

    // ---------------- 공통 ----------------

    interface Handler {
        void handle(HttpExchange ex) throws IOException;
    }

    static HttpServer start(int port, Handler handler) {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            s.createContext("/", ex -> {
                try {
                    handler.handle(ex);
                } finally {
                    ex.close();
                }
            });
            s.setExecutor(Executors.newCachedThreadPool());
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static void reply(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 아무도 듣지 않는 포트 하나 — "엔진에 닿지 않음"용 */
    static int closedPort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
