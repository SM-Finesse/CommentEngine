package com.finesse.composite.light;

import com.finesse.tetris.api.StatsResponse;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 통계 → light로 쓸 지표 고르기 (최대 3개). 모놀리식 {@code CommentService.lightCommentItems}·{@code displayValue}를
 * tetris-api DTO({@link StatsResponse}) 위에서 옮긴 것 — 통계 응답이 바뀌면 컴파일에서 잡히게 JsonNode로 파지 않는다(설계서 5.2절).
 */
public final class LightItems {

    private static final Logger log = LoggerFactory.getLogger(LightItems.class);

    /** 하이라이트 최대 개수 */
    public static final int MAX = 3;

    /**
     * 고른 지표 하나
     *
     * @param key   지표 키 (예: delta_app)
     * @param value 화면과 같은 원값 (delta_metrics의 그 칸 — picks의 판정 값이 아님)
     * @param zone  high·normal·low
     */
    public record Pick(String key, double value, String zone) {
    }

    private LightItems() {
    }

    /**
     * {@code highlight_judgement.picks} 순서대로 — 영역이 high·normal·low가 아니거나, 원값이 없거나 무한대이거나,
     * 지표 사전에 없거나, 이미 고른 지표면 뺀다. 최대 3개. highlight_judgement가 없으면 빈 목록.
     */
    public static List<Pick> from(StatsResponse stats, LightMetrics metrics) {
        StatsResponse.HighlightJudgement judgement = stats.highlightJudgement();
        if (judgement == null || judgement.picks() == null) {
            return List.of();
        }
        List<Pick> picks = new ArrayList<>();
        for (StatsResponse.JudgedMetric pick : judgement.picks()) {
            if (picks.size() == MAX) {
                break;
            }
            String key = pick.key();
            String zone = pick.zone();
            if (key == null || !("high".equals(zone) || "normal".equals(zone) || "low".equals(zone))) {
                continue;
            }
            if (!metrics.has(key)) {
                log.warn("light 지표 사전에 없는 지표라 뺌: {}", key); // 서버가 죽지 않게 (지침서 2.1)
                continue;
            }
            Double value = displayValue(stats.deltaMetrics(), key);
            if (value == null || !Double.isFinite(value) || picks.stream().anyMatch(p -> p.key().equals(key))) {
                continue;
            }
            picks.add(new Pick(key, value, zone));
        }
        return List.copyOf(picks);
    }

    /** 화면(delta_metrics)에 보이는 원값 — 후보 11개 키 → 통계 칸 */
    static Double displayValue(StatsResponse.DeltaMetrics d, String key) {
        if (d == null) {
            return null;
        }
        StatsResponse.PlaystyleRelative p = d.playstyleRelative();
        return switch (key) {
            case "delta_opener" -> p == null ? null : p.deltaOpener();
            case "delta_plonk" -> p == null ? null : p.deltaPlonk();
            case "delta_stride" -> p == null ? null : p.deltaStride();
            case "delta_inf_ds" -> p == null ? null : p.deltaInfDs();
            case "delta_app" -> d.attack() == null ? null : d.attack().deltaApp();
            case "delta_weighted_app" -> d.attack() == null ? null : d.attack().deltaWeightedApp();
            case "delta_vs_apm" -> d.defense() == null ? null : d.defense().deltaVsApm();
            case "delta_cheese_index" -> d.defense() == null ? null : d.defense().deltaCheeseIndex();
            case "strength_split" -> d.strengthSplit();
            case "delta_comeback" -> d.deltaComeback();
            case "session_vs_slope" -> d.sessionVsSlope();
            default -> null;
        };
    }
}
