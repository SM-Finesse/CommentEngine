package com.finesse.composite.heavy;

import com.commentengine.api.EngineContext;
import com.commentengine.api.Item;
import com.commentengine.api.Section;
import com.commentengine.api.SectionRequest;
import com.finesse.composite.heavy.HeavyChapterBook.Chapter;
import com.finesse.composite.heavy.HeavyChapterBook.ChapterItem;
import com.finesse.composite.heavy.HeavyDirections.Direction;
import com.finesse.tetris.api.StatsResponse;
import com.finesse.tetris.api.StatsResponse.DeltaMetrics;
import com.finesse.tetris.api.StatsResponse.MetricReliability;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * 통계(StatsResponse) → 챕터마다 엔진 계약 {@link SectionRequest} (조각 9 지침 2.3).
 * 엔진에 보내는 것은 지표 값을 문장으로 바꾼 항목뿐이다 — username·닉네임 같은 식별자는 넣지 않는다.
 */
public final class HeavyChapters {

    /** 화면에 보내는 챕터 순서 (모놀리식 CommentService와 같음) */
    public static final List<String> CHAPTER_ORDER = List.of(
            "tr_trend", "playstyle", "attack", "defense", "strength_split", "comeback_rate", "session_vs_slope", "rivals");

    /** 값이 없어 엔진을 부르지 않은 챕터의 각주 (모놀리식 llm-server fallback.py NO_DATA) */
    public static final String NO_DATA = "이 챕터를 설명할 값이 아직 충분하지 않습니다.";

    private final HeavyChapterBook book;
    private final HeavyDirections directions;
    private final RandomGenerator random;

    /** @param random 비교 기준 표현을 고를 때 쓴다 (시험에서는 씨앗을 고정) */
    public HeavyChapters(HeavyChapterBook book, RandomGenerator random) {
        this.book = book;
        this.directions = new HeavyDirections(book.ranges());
        this.random = random;
    }

    public HeavyChapterBook book() {
        return book;
    }

    /**
     * 챕터 하나의 엔진 요청.
     *
     * @return 보낼 항목이 0개면 empty — 엔진을 부르지 않고 {@link #NO_DATA}로 끝낸다
     * @throws IllegalArgumentException 엔진을 부르지 않는 챕터(rivals)나 사전에 없는 챕터
     */
    public Optional<SectionRequest> request(String chapterKey, StatsResponse stats, int deadlineMs) {
        Chapter chapter = book.chapter(chapterKey);
        if (!chapter.callsEngine()) {
            throw new IllegalArgumentException("엔진을 부르지 않는 챕터: " + chapterKey);
        }
        DeltaMetrics dm = stats == null ? null : stats.deltaMetrics();
        List<Item> items = new ArrayList<>();
        if (dm != null) {
            List<String> mustNot = new ArrayList<>(book.commonMustNot());
            mustNot.addAll(chapter.mustNot());
            for (ChapterItem ci : chapter.items()) {
                Direction direction = direction(ci, dm);
                if (direction == null) {
                    continue;   // 값이 없는 지표는 항목에서 뺀다
                }
                items.add(new Item(
                        ci.key(),
                        ci.label(),
                        ci.meaning(),
                        HeavyDirections.valueText(ci.key(), value(ci.key(), dm)),
                        direction.text(),
                        direction.degree(),
                        reliability(ci.key(), dm),
                        List.of(),   // section 틀은 항목 rules를 쓰지 않는다. 챕터 규칙은 description으로
                        List.copyOf(mustNot)));
            }
        }
        if (items.isEmpty()) {
            return Optional.empty();
        }
        Section section = new Section(chapter.key(), chapter.title(), description(chapter));
        EngineContext context = new EngineContext(book.context().name(), book.context().rules());
        return Optional.of(new SectionRequest(context, section, List.copyOf(items), deadlineMs));
    }

    String description(Chapter chapter) {
        List<String> lines = new ArrayList<>(chapter.description());
        if (chapter.compare()) {
            List<String> phrases = book.comparePhrases();
            String phrase = phrases.get(random.nextInt(phrases.size()));
            lines.add(book.compareRule().replace("{phrase}", phrase));
        }
        return String.join("\n", lines);
    }

    private Direction direction(ChapterItem ci, DeltaMetrics dm) {
        Double v = value(ci.key(), dm);
        if (v == null) {
            return null;
        }
        if (HeavyDirections.RATE_KEYS.contains(ci.key())) {
            // 역전 2개는 서로 견줘야 방향이 나온다 — 하나라도 없으면 둘 다 뺀다
            if (dm.comebackRate() == null || dm.comebackRateAgainst() == null) {
                return null;
            }
            return HeavyDirections.comeback(ci.key(), dm.comebackRate(), dm.comebackRateAgainst());
        }
        return directions.direction(ci.key(), ci.label(), v);
    }

    /** 통계에서 지표 값 하나. 없으면 null */
    static Double value(String key, DeltaMetrics dm) {
        var ps = dm.playstyleRelative();
        var at = dm.attack();
        var df = dm.defense();
        return switch (key) {
            case "tr_trend_delta" -> dm.trTrendDelta();
            case "delta_opener" -> ps == null ? null : ps.deltaOpener();
            case "delta_plonk" -> ps == null ? null : ps.deltaPlonk();
            case "delta_stride" -> ps == null ? null : ps.deltaStride();
            case "delta_inf_ds" -> ps == null ? null : ps.deltaInfDs();
            case "delta_app" -> at == null ? null : at.deltaApp();
            case "delta_weighted_app" -> at == null ? null : at.deltaWeightedApp();
            case "delta_vs_apm" -> df == null ? null : df.deltaVsApm();
            case "delta_cheese_index" -> df == null ? null : df.deltaCheeseIndex();
            case "strength_split" -> dm.strengthSplit();
            case "comeback_rate" -> dm.comebackRate();
            case "comeback_rate_against" -> dm.comebackRateAgainst();
            case "session_vs_slope" -> dm.sessionVsSlope();
            default -> throw new IllegalArgumentException("heavy 사전에 모르는 지표: " + key);
        };
    }

    /** light(조각 8)와 같은 규칙: low_sample이면 insufficient */
    static String reliability(String key, DeltaMetrics dm) {
        Map<String, MetricReliability> map = dm.reliability();
        MetricReliability r = map == null ? null : map.get(key);
        return r != null && Boolean.TRUE.equals(r.lowSample()) ? "insufficient" : "sufficient";
    }
}
