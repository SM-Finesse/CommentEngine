package com.finesse.composite.light;

import com.commentengine.api.EngineContext;
import com.commentengine.api.Item;
import com.commentengine.api.LightRequest;
import com.finesse.tetris.api.StatsResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 고른 지표 → 엔진 요청 {@link LightRequest} (설계서 5.1절, 조각 8 지침서 2.4).
 * 엔진은 분야를 모르므로 칩이 항목마다 이름·뜻·값 표기·방향 문장·강도·신뢰도·규칙·금지 낱말을 다 채워 보낸다.
 * <p>
 * 유저명·updated_at·상대 닉네임 같은 <b>식별자는 넣지 않는다</b> — 통계에서 지표 값·영역·신뢰도만 꺼낸다.
 */
public final class LightRequestBuilder {

    /** 성향 지표(우열이 아님)에 더하는 금지 낱말 */
    static final List<String> NO_RANK_MUST_NOT = List.of("앞서", "앞섭", "밀리", "밀립", "우위", "열세", "강점", "약점");
    static final String NO_RANK = "잘하고 못함이 아니라 성향이므로 '앞섭니다', '밀립니다', '강점', '약점'은 쓰지 않습니다.";

    private final LightMetrics metrics;
    private final LightCommentTemplates templates;

    public LightRequestBuilder(LightMetrics metrics, LightCommentTemplates templates) {
        this.metrics = metrics;
        this.templates = templates;
    }

    /**
     * @param stats      통계 (신뢰도 low_sample을 읽는 데만 씀)
     * @param picks      {@link LightItems#from}이 고른 지표 (1~3개)
     * @param deadlineMs 엔진 마감 (남은 시간)
     * @param rng        비교 표현 고르기
     */
    public LightRequest build(StatsResponse stats, List<LightItems.Pick> picks, int deadlineMs, Random rng) {
        List<Item> items = new ArrayList<>();
        for (LightItems.Pick p : picks) {
            items.add(item(stats, p, rng));
        }
        LightMetrics.Context c = metrics.context();
        return new LightRequest(new EngineContext(c.name(), List.copyOf(c.rules())), List.copyOf(items), deadlineMs);
    }

    Item item(StatsResponse stats, LightItems.Pick p, Random rng) {
        LightMetrics.Metric m = metrics.metric(p.key());
        String state = templates.state(p.key(), p.value(), p.zone());
        return new Item(
                p.key(),
                m.label(),
                m.meaning(),
                templates.formatValue(p.key(), p.value()),
                templates.direction(p.key(), state),
                LightCommentTemplates.degree(state),
                reliability(stats, p.key()),
                rules(m, state, rng),
                mustNot(m));
    }

    /** low_sample이면 insufficient, 아니면 sufficient — metric_level(caution·unreliable)은 쓰지 않는다 (지침서 부록 A-2) */
    static String reliability(StatsResponse stats, String key) {
        StatsResponse.DeltaMetrics d = stats.deltaMetrics();
        Map<String, StatsResponse.MetricReliability> r = d == null ? null : d.reliability();
        StatsResponse.MetricReliability mr = r == null ? null : r.get(key);
        return mr != null && Boolean.TRUE.equals(mr.lowSample()) ? "insufficient" : "sufficient";
    }

    /**
     * ① 비교 기준 ② 서술어 — 모놀리식 llm-server light_comment.py {@code _compare_rule()}·{@code _predicate_rule()}와 같은 문장.
     * 비교 표현은 항목마다 비교 표현 5개 중 하나를 무작위로 (값이 0이면 "…보다"로 끝나는 것은 빼고)
     */
    List<String> rules(LightMetrics.Metric m, String state, Random rng) {
        return switch (m.group()) {
            case LightCommentTemplates.STRENGTH_SPLIT ->
                    List.of("비교 기준은 본인 기록 안의 강한 상대 구간과 약한 상대 구간입니다. '상대보다', '상대 대비'는 쓰지 않습니다.");
            case LightCommentTemplates.COMEBACK ->
                    List.of("비교 기준은 역전승 경향(불리할 때 뒤집는 경향)과 역전패 경향(유리하다가 뒤집히는 경향)입니다. "
                            + "'역전승 경향', '역전패 경향'이라는 말로 쓰고, 몇 판 차이인지는 단정하지 않습니다.");
            case LightCommentTemplates.SESSION -> List.of("비교 기준은 한 매치 안의 라운드 진행입니다. '상대'라는 말은 쓰지 않습니다.");
            default -> {
                List<String> phrases = "zero".equals(state) ? templates.zeroPhrases() : templates.comparePhrases();
                String phrase = phrases.get(rng.nextInt(phrases.size()));
                yield List.of("비교 기준으로 '" + phrase + "'를 문장 앞에 그대로 씁니다.", predicate(m.group()));
            }
        };
    }

    private static String predicate(String group) {
        return switch (group) {
            case LightCommentTemplates.RANK -> "서술어는 '앞섭니다', '밀립니다'를 씁니다. '높습니다', '강합니다'는 쓰지 않습니다.";
            case LightCommentTemplates.VS_APM -> "서술어는 '큽니다', '작습니다'(큰/작은 편)만 씁니다. '짙게', '옅게'는 쓰지 않습니다. " + NO_RANK;
            case LightCommentTemplates.CHEESE -> "서술어는 '짙습니다/옅습니다' 또는 '강합니다/약합니다'를 씁니다. '크다/작다'는 쓰지 않습니다. " + NO_RANK;
            case LightCommentTemplates.TENDENCY ->
                    "서술어는 '짙습니다', '옅습니다'(짙은/옅은 편)만 씁니다. '크다/작다', '강하다/약하다'는 쓰지 않습니다. " + NO_RANK;
            default -> throw new IllegalArgumentException("비교 지표가 아닌 묶음: " + group);
        };
    }

    /** 공통 금지 낱말 + 묶음별 낱말 */
    List<String> mustNot(LightMetrics.Metric m) {
        List<String> words = new ArrayList<>(metrics.commonMustNot());
        switch (m.group()) {
            case LightCommentTemplates.VS_APM, LightCommentTemplates.CHEESE, LightCommentTemplates.TENDENCY ->
                    words.addAll(NO_RANK_MUST_NOT);
            case LightCommentTemplates.STRENGTH_SPLIT -> words.addAll(List.of("상대보다", "상대 대비"));
            case LightCommentTemplates.SESSION -> words.add("상대");
            default -> {
                // rank·comeback은 더하지 않음
            }
        }
        return List.copyOf(words);
    }
}
