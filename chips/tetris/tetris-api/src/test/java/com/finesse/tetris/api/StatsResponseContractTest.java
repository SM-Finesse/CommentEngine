package com.finesse.tetris.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class StatsResponseContractTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void analyzedStatsRoundTripWithTypedHighlightPicks() {
        String json = """
                {
                  "username": "example_user",
                  "cold_start": false,
                  "match_count": 40,
                  "updated_at": "2026-11-20T08:35:10.123456Z",
                  "profile": {
                    "rank": "u", "tr": 21000.0, "glicko": 2050.0, "rd": 62.0,
                    "joined_at": "2020-11-20T03:20:00Z",
                    "play_time_seconds": -1.0,
                    "window_delta": {"recent_matches": 12, "wr_delta_pct": 8.0}
                  },
                  "fixed_metrics": {"win_rate": 0.5, "tr_trend": [21000.0], "recent_form": ["W", "L"]},
                  "delta_metrics": {
                    "playstyle_relative": {"delta_inf_ds": -0.17},
                    "comeback_rate": 0.2,
                    "comeback_rate_against": 0.1,
                    "reliability": {"delta_app": {"metric_level": "caution", "low_sample": true}}
                  },
                  "round_curves": {"pps": [1.2], "vs": [80.1], "samples": [40]},
                  "rivals": {
                    "items": [{"nickname_masked": "e***", "matches": 2, "wins": 1, "losses": 1}],
                    "page": 1, "page_size": 20, "total": 1
                  },
                  "chapters": {"partial": false, "dropped_records": 0},
                  "highlight_judgement": {
                    "ranges_version": "ref-2026-10-15-g2",
                    "range_group": "t1",
                    "metrics": [
                      {"key": "delta_app", "group": "attack", "value": 0.093,
                       "q1": -0.034, "q3": 0.042, "zone": "high", "deviation": 0.67},
                      {"key": "delta_inf_ds", "group": "playstyle", "value": -0.17,
                       "q1": -0.10, "q3": 0.05, "zone": "low", "deviation": -0.47}
                    ],
                    "picks": [
                      {"key": "delta_app", "group": "attack", "value": 0.093,
                       "q1": -0.034, "q3": 0.042, "zone": "high", "deviation": 0.67},
                      {"key": "delta_inf_ds", "group": "playstyle", "value": -0.17,
                       "q1": -0.10, "q3": 0.05, "zone": "low", "deviation": -0.47}
                    ]
                  }
                }
                """;

        StatsResponse stats = mapper.readValue(json, StatsResponse.class);
        List<StatsResponse.JudgedMetric> picks = stats.highlightJudgement().picks();

        assertEquals(List.of("delta_app", "delta_inf_ds"), picks.stream().map(StatsResponse.JudgedMetric::key).toList());
        assertEquals(0.2, stats.deltaMetrics().comebackRate());
        assertEquals("caution", stats.deltaMetrics().reliability().get("delta_app").metricLevel());
        assertEquals(Instant.parse("2026-11-20T08:35:10.123456Z"), stats.updatedAt());
        assertEquals(mapper.readTree(json), mapper.readTree(mapper.writeValueAsString(stats)));
    }

    @Test
    void coldStartOmitsUnavailableFieldsAndKeepsEmptyLists() {
        String json = """
                {
                  "username": "new_player",
                  "cold_start": true,
                  "match_count": 0,
                  "updated_at": "2026-11-20T08:35:10Z",
                  "profile": {"rank": "z", "tr": -1.0, "glicko": -1.0, "rd": -1.0},
                  "fixed_metrics": {"tr_trend": [], "recent_form": []},
                  "round_curves": {"pps": [], "vs": [], "samples": []},
                  "rivals": {"items": [], "page": 1, "page_size": 20, "total": 0},
                  "chapters": {"note": "콜드스타트 — 챕터 데이터 없음", "cold_start_reason": "FEW_GAMES_TOTAL"}
                }
                """;

        StatsResponse stats = mapper.readValue(json, StatsResponse.class);

        assertNull(stats.deltaMetrics());
        assertNull(stats.highlightJudgement());
        assertNull(stats.fixedMetrics().winRate());
        assertEquals(mapper.readTree(json), mapper.readTree(mapper.writeValueAsString(stats)));
    }
}
