package com.finesse.composite.heavy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commentengine.api.Item;
import com.commentengine.api.SectionRequest;
import com.finesse.composite.heavy.HeavyChapterBook.Chapter;
import com.finesse.composite.heavy.HeavyChapterBook.ChapterItem;
import com.finesse.tetris.api.StatsResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 조각 9 1일차 시험 3~6 — 챕터 → SectionRequest */
class HeavyChaptersTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final HeavyChapterBook BOOK = HeavyChapterBook.load(Path.of("../config/heavy-chapters.json"));

    final HeavyChapters chapters = new HeavyChapters(BOOK, new Random(7));

    /** 지표가 모두 있는 통계. username·라이벌 닉네임은 시험 6에서 찾는 식별자 */
    static final String FULL_STATS = """
            {
              "username": "secret_user",
              "cold_start": false,
              "match_count": 40,
              "updated_at": "2026-11-24T08:00:00Z",
              "delta_metrics": {
                "tr_trend_delta": 120.5,
                "playstyle_relative": {"delta_opener": 0.3, "delta_plonk": -0.05, "delta_stride": 0.1, "delta_inf_ds": -0.3},
                "attack": {"delta_app": 0.034, "delta_weighted_app": -0.013},
                "defense": {"delta_vs_apm": 0.069, "delta_cheese_index": -8.949},
                "strength_split": -0.2,
                "comeback_rate": 0.42,
                "comeback_rate_against": 0.30,
                "session_vs_slope": 0.685,
                "reliability": {"delta_app": {"metric_level": "caution", "low_sample": true}}
              },
              "rivals": {
                "items": [{"nickname_masked": "ab*cd", "matches": 6, "wins": 1, "losses": 5}],
                "page": 1, "page_size": 10, "total": 1
              }
            }
            """;

    static StatsResponse stats(String json) {
        return JSON.readValue(json, StatsResponse.class);
    }

    static final Map<String, List<String>> EXPECTED_ITEMS = Map.of(
            "tr_trend", List.of("tr_trend_delta"),
            "playstyle", List.of("delta_opener", "delta_plonk", "delta_stride", "delta_inf_ds"),
            "attack", List.of("delta_app", "delta_weighted_app"),
            "defense", List.of("delta_vs_apm", "delta_cheese_index"),
            "strength_split", List.of("strength_split"),
            "comeback_rate", List.of("comeback_rate", "comeback_rate_against"),
            "session_vs_slope", List.of("session_vs_slope"));

    static final Map<String, String> EXPECTED_TITLES = Map.of(
            "tr_trend", "TR · 능력치 추이",
            "playstyle", "플레이스타일 상대비교",
            "attack", "공격 효율",
            "defense", "수비 · 가비지 처리",
            "strength_split", "상대 강도별 승률",
            "comeback_rate", "역전승 퍼포먼스",
            "session_vs_slope", "경기 내 컨디션 변화");

    /** 3. 챕터 7개 요청 — key·title·항목 순서, 비교 챕터만 끝에 비교 기준 줄 */
    @Test
    void 챕터_7개_요청() {
        StatsResponse s = stats(FULL_STATS);
        for (String key : EXPECTED_ITEMS.keySet()) {
            SectionRequest req = chapters.request(key, s, 60_000).orElseThrow();
            assertThat(req.section().key()).isEqualTo(key);
            assertThat(req.section().title()).isEqualTo(EXPECTED_TITLES.get(key));
            assertThat(req.items()).extracting(Item::key).containsExactlyElementsOf(EXPECTED_ITEMS.get(key));
            assertThat(req.deadlineMs()).isEqualTo(60_000);
            assertThat(req.context().name()).isEqualTo("TETR.IO 전적 분석 리포트");

            String[] lines = req.section().description().split("\n");
            String last = lines[lines.length - 1];
            boolean compare = List.of("playstyle", "attack", "defense").contains(key);
            assertThat(last.startsWith("비교 기준은 '")).as(key).isEqualTo(compare);
            if (compare) {
                assertThat(BOOK.comparePhrases()).anyMatch(p -> last.equals(
                        "비교 기준은 '" + p + "'를 문장마다 한 번 쓰세요. 비교 기준이 없는 문장은 쓰지 마세요."));
            }
        }
    }

    /** 3. 항목 칸 — value_text·reliability·rules·must_not */
    @Test
    void 항목_칸() {
        SectionRequest attack = chapters.request("attack", stats(FULL_STATS), 60_000).orElseThrow();
        Item app = attack.items().get(0);
        assertThat(app.label()).isEqualTo("블록당 공격력");
        assertThat(app.valueText()).isEqualTo("+0.03");
        assertThat(app.direction()).isEqualTo("블록당 공격력에서 상대보다 앞섭니다");
        assertThat(app.reliability()).isEqualTo("insufficient");   // low_sample: true
        assertThat(app.rules()).isEmpty();
        assertThat(app.mustNot()).containsAll(BOOK.commonMustNot());
        assertThat(attack.items().get(1).reliability()).isEqualTo("sufficient");

        SectionRequest comeback = chapters.request("comeback_rate", stats(FULL_STATS), 60_000).orElseThrow();
        assertThat(comeback.items()).extracting(Item::valueText).containsExactly("0.42", "0.30");

        SectionRequest tr = chapters.request("tr_trend", stats(FULL_STATS), 60_000).orElseThrow();
        assertThat(tr.items().get(0).mustNot()).contains("상대");
    }

    /** 4. 값 없음 — 챕터 전체가 비면 empty, 일부만 비면 그 항목만 빠짐 */
    @Test
    void 값이_없으면_항목에서_뺀다() {
        String noStrength = FULL_STATS.replace("\"strength_split\": -0.2,", "");
        assertThat(chapters.request("strength_split", stats(noStrength), 60_000)).isEmpty();

        // 지침서 예시는 defense였지만 Defense의 두 값은 double(null 불가)이라, Double 칸인 playstyle로 확인
        String noStride = FULL_STATS.replace("\"delta_stride\": 0.1, ", "");
        SectionRequest playstyle = chapters.request("playstyle", stats(noStride), 60_000).orElseThrow();
        assertThat(playstyle.items()).extracting(Item::key)
                .containsExactly("delta_opener", "delta_plonk", "delta_inf_ds");

        String noDefense = FULL_STATS.replace(
                "\"defense\": {\"delta_vs_apm\": 0.069, \"delta_cheese_index\": -8.949},", "");
        assertThat(chapters.request("defense", stats(noDefense), 60_000)).isEmpty();

        // 역전은 두 값을 견줘야 방향이 나온다 — 하나만 있으면 챕터가 빈다
        String noAgainst = FULL_STATS.replace("\"comeback_rate_against\": 0.30,", "");
        assertThat(chapters.request("comeback_rate", stats(noAgainst), 60_000)).isEmpty();

        String noDelta = """
                {"username": "u", "cold_start": true, "match_count": 0, "updated_at": "2026-11-24T08:00:00Z"}
                """;
        for (String key : EXPECTED_ITEMS.keySet()) {
            assertThat(chapters.request(key, stats(noDelta), 60_000)).as(key).isEqualTo(Optional.empty());
        }
    }

    @Test
    void rivals는_엔진_요청을_만들지_않는다() {
        assertThatThrownBy(() -> chapters.request("rivals", stats(FULL_STATS), 60_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(HeavyChapters.CHAPTER_ORDER).hasSize(8).last().isEqualTo("rivals");
        assertThat(BOOK.chapters()).extracting(Chapter::key).containsExactlyElementsOf(HeavyChapters.CHAPTER_ORDER);
    }

    /** 5. 자기 충돌 없음 — 모든 항목 × 모든 영역에서 must_not 낱말이 그 항목의 label·direction에 없음 */
    @Test
    void must_not이_자기_문장과_부딪히지_않는다() {
        HeavyDirections directions = new HeavyDirections(BOOK.ranges());
        double[] values = {-1000, -20, -8.5, -0.5, -0.395, -0.2, -0.139, -0.1, -0.03, -0.001, 0,
                0.001, 0.02, 0.1, 0.2, 0.5, 1.5, 14, 1000};
        double[][] comebackPairs = {{0.4, 0.3}, {0.3, 0.4}, {0.3, 0.3}};
        int checked = 0;
        for (Chapter chapter : BOOK.chapters()) {
            List<String> mustNot = new ArrayList<>(BOOK.commonMustNot());
            mustNot.addAll(chapter.mustNot());
            for (ChapterItem item : chapter.items()) {
                List<String> texts = new ArrayList<>();
                texts.add(item.label());
                if (HeavyDirections.RATE_KEYS.contains(item.key())) {
                    for (double[] p : comebackPairs) {
                        texts.add(HeavyDirections.comeback(item.key(), p[0], p[1]).text());
                    }
                } else {
                    for (double v : values) {
                        texts.add(directions.direction(item.key(), item.label(), v).text());
                    }
                }
                for (String text : texts) {
                    for (String word : mustNot) {
                        assertThat(text).as("%s / %s", item.key(), word).doesNotContain(word);
                        checked++;
                    }
                }
            }
        }
        assertThat(checked).isGreaterThan(1000);
    }

    /** 6. 식별자 없음 — username·라이벌 닉네임이 엔진 몸통에 없음 */
    @Test
    void 엔진_몸통에_식별자가_없다() {
        StatsResponse s = stats(FULL_STATS);
        for (String key : EXPECTED_ITEMS.keySet()) {
            String body = JSON.writeValueAsString(chapters.request(key, s, 60_000).orElseThrow());
            assertThat(body).as(key).doesNotContain("secret_user").doesNotContain("ab*cd");
        }
    }
}
