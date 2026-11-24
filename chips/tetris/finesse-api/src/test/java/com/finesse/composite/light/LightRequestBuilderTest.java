package com.finesse.composite.light;

import static org.assertj.core.api.Assertions.assertThat;

import com.commentengine.api.Degree;
import com.commentengine.api.Item;
import com.commentengine.api.LightRequest;
import com.finesse.tetris.api.StatsResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * 조각 8 1일차 시험 6개 (지침서 2.5) — 스프링 없이. 정답 문장은 모놀리식 {@code LightCommentTemplates.direction()}에서 복사했다.
 */
class LightRequestBuilderTest {

    static final LightMetrics METRICS = LightCommentTemplatesTest.METRICS;
    static final LightCommentTemplates T = LightCommentTemplatesTest.T;
    static final LightRequestBuilder BUILDER = new LightRequestBuilder(METRICS, T);
    static final JsonMapper JSON = JsonMapper.builder().build();

    // ---- 1. 방향 규칙 7칸 표 × 묶음 7개 ----

    /** 7칸: (값, 영역) → state. 값 ±0.12, 0칸은 반올림하면 0이 되는 0.004 */
    static final Object[][] CELLS = {
            {0.12, "high", "high", Degree.STRONG},
            {0.12, "normal", "mid+", Degree.MID},
            {0.12, "low", "weak+", Degree.WEAK},
            {-0.12, "high", "weak-", Degree.WEAK},
            {-0.12, "normal", "mid-", Degree.MID},
            {-0.12, "low", "low", Degree.STRONG},
            {0.004, "high", "zero", Degree.ZERO},
    };

    /** 묶음마다 대표 지표와 7칸 정답 (CELLS 순서: high, mid+, weak+, weak-, mid-, low, zero) */
    static Stream<Arguments> directionTable() {
        Object[][] groups = {
                {"rank", "delta_app", List.of(
                        "블록당 공격력에서 뚜렷하게 앞섭니다",
                        "블록당 공격력에서 앞서는 편입니다",
                        "블록당 공격력에서 조금 앞서지만 그 폭은 작은 편입니다",
                        "블록당 공격력에서 조금 밀리지만 그 폭은 작은 편입니다",
                        "블록당 공격력에서 밀리는 편입니다",
                        "블록당 공격력에서 뚜렷하게 밀립니다",
                        "블록당 공격력은 같은 수준입니다")},
                {"vs_apm", "delta_vs_apm", List.of(
                        "수비 비중이 뚜렷하게 큰 편입니다",
                        "수비 비중이 큰 편입니다",
                        "수비 비중이 조금 큰 편이지만 그 폭은 크지 않습니다",
                        "수비 비중이 조금 작은 편이지만 그 폭은 크지 않습니다",
                        "수비 비중이 작은 편입니다",
                        "수비 비중이 뚜렷하게 작은 편입니다",
                        "수비 비중은 같은 수준입니다")},
                {"tendency", "delta_opener", List.of(
                        "Opener 성향이 뚜렷하게 짙습니다",
                        "Opener 성향이 짙은 편입니다",
                        "Opener 성향이 조금 짙지만 그 폭은 작은 편입니다",
                        "Opener 성향이 조금 옅지만 그 폭은 작은 편입니다",
                        "Opener 성향이 옅은 편입니다",
                        "Opener 성향이 뚜렷하게 옅습니다",
                        "Opener 성향은 같은 수준입니다")},
                {"cheese", "delta_cheese_index", List.of(
                        "가비지 처리 성향이 뚜렷하게 짙습니다",
                        "가비지 처리 성향이 짙은 편입니다",
                        "가비지 처리 성향이 조금 짙지만 그 폭은 작은 편입니다",
                        "가비지 처리 성향이 조금 옅지만 그 폭은 작은 편입니다",
                        "가비지 처리 성향이 옅은 편입니다",
                        "가비지 처리 성향이 뚜렷하게 옅습니다",
                        "가비지 처리 성향은 같은 수준입니다")},
                {"strength_split", "strength_split", List.of(
                        "강한 상대 구간에서는 약한 상대 구간보다 승률이 오히려 높습니다",
                        "강한 상대 구간에서는 약한 상대 구간보다 승률이 오히려 높은 편입니다",
                        "강한 상대 구간에서는 약한 상대 구간보다 승률이 오히려 조금 높지만 그 폭은 작은 편입니다",
                        "강한 상대를 만나도 약한 상대 때와 비교해 승률이 크게 흔들리지 않는 편입니다",
                        "강한 상대 구간에서는 약한 상대 구간보다 승률이 떨어지는 편입니다",
                        "강한 상대 구간에서는 약한 상대 구간보다 승률이 크게 떨어집니다",
                        "강한 상대 구간에서는 약한 상대 구간과 승률이 같은 수준입니다")},
                {"comeback", "delta_comeback", List.of(
                        "역전승 경향이 역전패 경향보다 뚜렷하게 강합니다",
                        "역전승 경향이 역전패 경향보다 강한 편입니다",
                        "역전승 경향이 역전패 경향보다 조금 강하지만 그 폭은 작은 편입니다",
                        "역전패 경향이 역전승 경향보다 조금 강하지만 그 폭은 작은 편입니다",
                        "역전패 경향이 역전승 경향보다 강한 편입니다",
                        "역전패 경향이 역전승 경향보다 뚜렷하게 강합니다",
                        "역전승 경향과 역전패 경향이 같은 수준입니다")},
                {"session", "session_vs_slope", List.of(
                        "라운드가 진행될수록 공방 능력이 뚜렷하게 오릅니다",
                        "라운드가 진행될수록 공방 능력이 오르는 흐름입니다",
                        "라운드가 진행될수록 공방 능력이 오르기는 하지만 그 폭은 작은 편입니다",
                        "라운드가 진행될수록 공방 능력이 처지기는 하지만 그 폭은 작은 편입니다",
                        "라운드가 진행될수록 공방 능력이 처지는 흐름입니다",
                        "라운드가 진행될수록 공방 능력이 뚜렷하게 처집니다",
                        "라운드가 진행되어도 공방 능력에 뚜렷한 추세가 없습니다")},
        };
        Stream.Builder<Arguments> out = Stream.builder();
        for (Object[] g : groups) {
            @SuppressWarnings("unchecked")
            List<String> expected = (List<String>) g[2];
            for (int i = 0; i < CELLS.length; i++) {
                out.add(Arguments.of(g[0], g[1], CELLS[i][0], CELLS[i][1], CELLS[i][2], CELLS[i][3], expected.get(i)));
            }
        }
        return out.build();
    }

    @ParameterizedTest(name = "{0} {1} 값 {2} 영역 {3} → {4}")
    @MethodSource("directionTable")
    void 방향_규칙_7칸_표(String group, String key, double value, String zone, String state, Degree degree, String direction) {
        assertThat(METRICS.metric(key).group()).isEqualTo(group);
        assertThat(T.state(key, value, zone)).isEqualTo(state);
        assertThat(LightCommentTemplates.degree(state)).isEqualTo(degree);
        assertThat(T.direction(key, state)).isEqualTo(direction);

        Item item = BUILDER.item(stats(List.of()), new LightItems.Pick(key, value, zone), new Random(1));
        assertThat(item.direction()).isEqualTo(direction);
        assertThat(item.degree()).isEqualTo(degree);
    }

    // ---- 2. 값 표기 ----

    @Test
    void 값_표기는_사전_자릿수로_HALF_EVEN_반올림() {
        assertThat(T.formatValue("delta_comeback", 0.36645)).isEqualTo("+0.37");
        assertThat(T.formatValue("delta_app", 0.36645)).isEqualTo("+0.37");
        assertThat(T.formatValue("delta_app", -0.66)).isEqualTo("-0.66");
        assertThat(T.formatValue("delta_app", 0.004)).isEqualTo("0.00");
        assertThat(T.formatValue("delta_app", -0.004)).isEqualTo("0.00");
        assertThat(T.formatValue("delta_cheese_index", 1.24)).isEqualTo("+1.2");
        assertThat(T.formatValue("delta_cheese_index", 0.04)).isEqualTo("0.0");
        assertThat(T.formatValue("delta_app", 0.125)).isEqualTo("+0.12"); // 0.125는 이진수로 정확 → 짝수 쪽(HALF_EVEN)
    }

    // ---- 3. 항목 고르기 ----

    @Test
    void 항목_고르기는_picks_순서로_none_값없음_중복을_빼고_최대_3개() {
        StatsResponse s = stats(List.of(
                pick("delta_opener", "none", 0.5),        // 영역 none → 뺌
                pick("delta_plonk", "high", 0.5),         // 원값 null → 뺌 (playstyle에 plonk 없음)
                pick("delta_comeback", "high", 0.11),     // 판정 값 0.11이 아니라 delta_metrics 원값 0.36645
                pick("delta_comeback", "high", 0.11),     // 같은 지표 두 번 → 한 번
                pick("delta_app", "normal", 0.09),
                pick("session_vs_slope", "low", -1.0),
                pick("delta_vs_apm", "high", 0.2)));      // 4번째 → 뺌
        List<LightItems.Pick> picks = LightItems.from(s, METRICS);

        assertThat(picks).extracting(LightItems.Pick::key).containsExactly("delta_comeback", "delta_app", "session_vs_slope");
        assertThat(picks.get(0).value()).as("delta_comeback은 화면 원값").isEqualTo(0.36645);
        assertThat(picks.get(1).value()).isEqualTo(0.09);
        assertThat(picks.get(1).zone()).isEqualTo("normal");

        assertThat(LightItems.from(statsWithoutJudgement(), METRICS)).as("highlight_judgement 없음").isEmpty();
        assertThat(LightItems.from(stats(List.of(pick("unknown_metric", "high", 1.0))), METRICS)).as("사전에 없는 키").isEmpty();
    }

    // ---- 4. 자기 충돌 없음 ----

    @Test
    void 금지_낱말이_그_항목의_이름과_방향_문장에_들어_있지_않다() {
        double[] values = {0.12, 0.12, 0.12, -0.12, -0.12, -0.12, 0.004};
        String[] zones = {"high", "normal", "low", "high", "normal", "low", "high"};
        int checked = 0;
        for (String key : METRICS.metrics().keySet()) {
            for (int i = 0; i < values.length; i++) {
                Item item = BUILDER.item(stats(List.of()), new LightItems.Pick(key, values[i], zones[i]), new Random(i));
                for (String word : item.mustNot()) {
                    assertThat(item.label()).as(key + " label vs " + word).doesNotContain(word);
                    assertThat(item.direction()).as(key + " " + zones[i] + " direction vs " + word).doesNotContain(word);
                }
                checked++;
            }
        }
        assertThat(checked).isEqualTo(11 * 7);
    }

    // ---- 5. 식별자 없음 ----

    @Test
    void 엔진_요청에_유저명과_updated_at이_없다() {
        StatsResponse s = JSON.readValue("""
                {"username": "secret_user", "cold_start": false, "match_count": 40,
                 "updated_at": "2026-11-24T09:15:00.123Z",
                 "rivals": {"items": [{"nickname_masked": "r***", "matches": 3, "wins": 2, "losses": 1}],
                            "page": 1, "page_size": 20, "total": 1},
                 "delta_metrics": {"attack": {"delta_app": 0.12, "delta_weighted_app": 0.05},
                                   "delta_comeback": 0.2, "session_vs_slope": -0.5},
                 "highlight_judgement": {"picks": [
                   {"key": "delta_app", "zone": "high", "value": 0.12, "q1": 0, "q3": 0},
                   {"key": "delta_comeback", "zone": "normal", "value": 0.1, "q1": 0, "q3": 0}]}}
                """, StatsResponse.class);
        LightRequest req = BUILDER.build(s, LightItems.from(s, METRICS), 40000, new Random(1));
        String json = JSON.writeValueAsString(req);

        assertThat(req.items()).hasSize(2);
        assertThat(json).doesNotContain("secret_user").doesNotContain("2026-11-24").doesNotContain("r***")
                .doesNotContain("username").doesNotContain("updated_at");
    }

    // ---- 6. 계약 검사 통과 ----

    @Test
    void 만든_요청이_엔진_계약_검사를_통과한다() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        StatsResponse s = stats(List.of(
                pick("delta_comeback", "high", 0.11), pick("delta_app", "normal", 0.09), pick("delta_cheese_index", "low", -3.0)));
        LightRequest req = BUILDER.build(s, LightItems.from(s, METRICS), 40000, new Random(1));

        assertThat(req.items()).hasSize(3);
        assertThat(validator.validate(req)).isEmpty();
        // 다른 칸도 채워졌는지 (엔진은 분야를 모르므로 칩이 다 채운다)
        Item cheese = req.items().get(2);
        assertThat(cheese.label()).isEqualTo("가비지 처리 성향");
        assertThat(cheese.valueText()).isEqualTo("-3.0");
        assertThat(cheese.reliability()).isEqualTo("insufficient"); // low_sample
        assertThat(cheese.rules()).hasSize(2);
        assertThat(cheese.mustNot()).contains("다른 유저", "강점");
        assertThat(req.context().name()).isEqualTo("TETR.IO 전적 분석 페이지 첫 화면");
        assertThat(req.items().get(0).reliability()).isEqualTo("sufficient"); // metric_level만 있음 → sufficient
        assertThat(req.items().get(0).rules()).hasSize(1);                    // comeback은 비교 기준 한 줄
    }

    @Test
    void 비교_표현은_값이_0이면_보다로_끝나는_것을_빼고_고른다() {
        for (int seed = 0; seed < 50; seed++) {
            Item zero = BUILDER.item(stats(List.of()), new LightItems.Pick("delta_app", 0.001, "high"), new Random(seed));
            assertThat(zero.rules().get(0)).doesNotContain("상대들보다'");
        }
    }

    // ---- 시험용 통계 ----

    static StatsResponse.JudgedMetric pick(String key, String zone, double value) {
        return new StatsResponse.JudgedMetric(key, "g", value, 0, 0, zone, 0.0, null);
    }

    /** picks와, 그 picks가 읽을 delta_metrics 원값 */
    static StatsResponse stats(List<StatsResponse.JudgedMetric> picks) {
        StatsResponse base = JSON.readValue("""
                {"username": "abc", "cold_start": false, "match_count": 40,
                 "delta_metrics": {
                   "playstyle_relative": {"delta_opener": 0.5},
                   "attack": {"delta_app": 0.09, "delta_weighted_app": 0.05},
                   "defense": {"delta_vs_apm": 0.2, "delta_cheese_index": -3.0},
                   "delta_comeback": 0.36645,
                   "session_vs_slope": -1.0,
                   "reliability": {"delta_comeback": {"metric_level": "unreliable"},
                                   "delta_cheese_index": {"low_sample": true}}}}
                """, StatsResponse.class);
        return new StatsResponse(base.username(), base.coldStart(), base.matchCount(), base.updatedAt(), base.profile(),
                base.fixedMetrics(), base.deltaMetrics(), base.roundCurves(), base.rivals(), base.chapters(),
                new StatsResponse.HighlightJudgement("ref", "t1", List.of(), picks));
    }

    static StatsResponse statsWithoutJudgement() {
        StatsResponse s = stats(List.of());
        return new StatsResponse(s.username(), s.coldStart(), s.matchCount(), s.updatedAt(), s.profile(),
                s.fixedMetrics(), s.deltaMetrics(), s.roundCurves(), s.rivals(), s.chapters(), null);
    }
}
