package com.finesse.composite;

import static org.assertj.core.api.Assertions.assertThat;

import com.finesse.tetris.api.StatsResponse;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * 칩 안 계약(tetris-api)과 맞는지 — 가짜 analytics가 돌려준 통계 본문을 finesse-api가 넘긴 그대로 tetris-api의 StatsResponse로
 * 읽을 수 있어야 한다 (2일차). 화면에는 여전히 받은 바이트를 그대로 넘긴다: DTO로 읽었다가 다시 JSON으로 쓰면 null 칸·숫자 표기가
 * 바뀌어 모놀리식과 달라질 수 있어서다. DTO를 실제로 쓰는 건 하이라이트를 읽는 조각 8이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StatsDtoTest {

    /** 분석된 유저 통계 (tetris-api 계약 시험의 예시와 같은 모양) */
    static final String STATS_JSON = """
            {
              "username": "example_user",
              "cold_start": false,
              "match_count": 40,
              "updated_at": "2026-11-20T08:35:10.123456Z",
              "profile": {"rank": "u", "tr": 21000.0, "glicko": 2050.0, "rd": 62.0,
                          "window_delta": {"recent_matches": 12, "wr_delta_pct": 8.0}},
              "fixed_metrics": {"win_rate": 0.5, "tr_trend": [21000.0], "recent_form": ["W", "L"]},
              "delta_metrics": {"comeback_rate": 0.2, "comeback_rate_against": 0.1,
                                "reliability": {"delta_app": {"metric_level": "caution", "low_sample": true}}},
              "round_curves": {"pps": [1.2], "vs": [80.1], "samples": [40]},
              "rivals": {"items": [], "page": 1, "page_size": 20, "total": 0},
              "chapters": {"partial": false},
              "highlight_judgement": {
                "ranges_version": "ref-2026-10-15-g2", "range_group": "t1", "metrics": [],
                "picks": [{"key": "delta_app", "group": "attack", "value": 0.093,
                           "q1": -0.034, "q3": 0.042, "zone": "high", "deviation": 0.67}]
              }
            }
            """;

    static final HttpServer ANALYTICS;

    static {
        try {
            ANALYTICS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ANALYTICS.createContext("/", ex -> {
                byte[] bytes = STATS_JSON.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
            });
            ANALYTICS.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void analytics(DynamicPropertyRegistry r) {
        r.add("analytics.base-url", () -> "http://127.0.0.1:" + ANALYTICS.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        ANALYTICS.stop(0);
    }

    @Value("${local.server.port}")
    int port;

    @Test
    void 넘긴_통계_본문을_tetris_api_StatsResponse로_읽을_수_있다() throws Exception {
        HttpResponse<String> resp = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/stats/example_user")).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).as("화면에는 받은 그대로").isEqualTo(STATS_JSON);

        StatsResponse stats = JsonMapper.builder().build().readValue(resp.body(), StatsResponse.class);
        assertThat(stats.username()).isEqualTo("example_user");
        assertThat(stats.coldStart()).isFalse();
        assertThat(stats.matchCount()).isEqualTo(40);
        assertThat(stats.highlightJudgement().picks()).extracting(StatsResponse.JudgedMetric::key).containsExactly("delta_app");
        assertThat(stats.deltaMetrics().reliability().get("delta_app").metricLevel()).isEqualTo("caution");
    }
}
