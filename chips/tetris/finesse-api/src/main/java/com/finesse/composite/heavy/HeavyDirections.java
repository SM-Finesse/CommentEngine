package com.finesse.composite.heavy;

import com.commentengine.api.Degree;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * heavy 값 표기(value_text)·영역·방향 문장·degree.
 * 원본은 모놀리식 llm-server {@code app/prompt.py}의 fmt_num, heavy_zone, HEAVY_DIRECTIONS, strength_level,
 * STRENGTH_LABELS, _playstyle_lines, _comparison_line — 문장 글자는 바꾸지 않고 옮겼다 (조각 9 지침 2.2).
 * light와 규칙이 다르다(모놀리식도 따로였음).
 */
public final class HeavyDirections {

    /** 방향 문장과 degree 한 쌍 */
    public record Direction(String text, Degree degree) {
    }

    static final Set<String> PLAYSTYLE_KEYS = Set.of("delta_opener", "delta_plonk", "delta_stride", "delta_inf_ds");
    static final Set<String> RATE_KEYS = Set.of("comeback_rate", "comeback_rate_against");

    /** prompt.py HEAVY_DIRECTIONS (영역 high·low·mid+·mid-·zero, tr_trend_delta는 high·low·zero) */
    static final Map<String, Map<String, String>> DIRECTIONS = Map.of(
            "tr_trend_delta", Map.of(
                    "high", "최근 구간 TR이 전체 평균 TR보다 높습니다",
                    "low", "최근 구간 TR이 전체 평균 TR보다 낮습니다",
                    "zero", "최근 구간 TR이 전체 평균 TR과 같습니다"),
            "delta_app", Map.of(
                    "high", "블록당 공격력에서 상대보다 앞섭니다",
                    "low", "블록당 공격력에서 상대보다 밀립니다",
                    "mid+", "블록당 공격력에서 상대보다 조금 앞서지만 차이는 크지 않습니다",
                    "mid-", "블록당 공격력에서 상대보다 조금 밀리지만 차이는 크지 않습니다",
                    "zero", "블록당 공격력은 상대와 같습니다"),
            "delta_weighted_app", Map.of(
                    "high", "보정 공격 효율에서 상대보다 앞섭니다",
                    "low", "보정 공격 효율에서 상대보다 밀립니다",
                    "mid+", "보정 공격 효율에서 상대보다 조금 앞서지만 차이는 크지 않습니다",
                    "mid-", "보정 공격 효율에서 상대보다 조금 밀리지만 차이는 크지 않습니다",
                    "zero", "보정 공격 효율은 상대와 같습니다"),
            "delta_vs_apm", Map.of(
                    "high", "상대보다 수비 비중이 큰 편입니다",
                    "low", "상대보다 수비 비중이 작은 편입니다",
                    "mid+", "상대보다 수비 비중이 약간 큰 편이지만 차이는 크지 않습니다",
                    "mid-", "상대보다 수비 비중이 약간 작은 편이지만 차이는 크지 않습니다",
                    "zero", "상대와 같습니다"),
            "delta_cheese_index", Map.of(
                    "high", "상대보다 가비지 처리 성향이 짙게 나타납니다",
                    "low", "상대보다 가비지 처리 성향이 옅게 나타납니다",
                    "mid+", "상대보다 가비지 처리 성향이 약간 짙지만 차이는 크지 않습니다",
                    "mid-", "상대보다 가비지 처리 성향이 약간 옅지만 차이는 크지 않습니다",
                    "zero", "상대와 같습니다"),
            "session_vs_slope", Map.of(
                    "high", "라운드가 진행될수록 공방 능력이 크게 오릅니다",
                    "low", "라운드가 진행될수록 공방 능력이 처집니다",
                    "mid+", "라운드가 진행될수록 공방 능력이 조금 오르지만 변화는 크지 않습니다",
                    "mid-", "라운드가 진행될수록 공방 능력이 조금 처지지만 변화는 크지 않습니다",
                    "zero", "뚜렷한 추세가 없습니다"));

    /** prompt.py _playstyle_lines의 tails (끝의 마침표만 뗌 — 방향 문장은 마침표 없이 보낸다) */
    static final Map<String, String> PLAYSTYLE_TAILS = Map.of(
            "high", "상대보다 짙게 나타납니다",
            "low", "상대보다 옅게 나타납니다",
            "mid+", "상대보다 조금 짙지만 차이는 크지 않습니다",
            "mid-", "상대보다 조금 옅지만 차이는 크지 않습니다",
            "zero", "상대와 같습니다");

    /** prompt.py STRENGTH_LABELS */
    static final Map<String, String> STRENGTH_LABELS = Map.of(
            "pos", "강한 상대 구간에서는 약한 상대 구간보다 승률이 오히려 높습니다",
            "zero", "강한 상대 구간에서는 약한 상대 구간과 승률 차이가 없습니다",
            "low", "강한 상대 구간에서는 약한 상대 구간보다 승률이 크게 떨어집니다",
            "mid", "강한 상대 구간에서는 약한 상대 구간보다 승률이 떨어집니다",
            "high", "강한 상대 구간에서는 약한 상대 구간보다 승률이 조금 떨어지지만 그 폭은 작습니다");

    static final Map<String, Degree> STRENGTH_DEGREES = Map.of(
            "low", Degree.STRONG, "mid", Degree.MID, "high", Degree.WEAK, "pos", Degree.STRONG, "zero", Degree.ZERO);

    private final Map<String, List<Double>> ranges;

    /** @param ranges 사전의 {@code ranges} — 키 → [Q1, Q3] */
    public HeavyDirections(Map<String, List<Double>> ranges) {
        this.ranges = Map.copyOf(ranges);
    }

    // ---- 값 표기 (prompt.py fmt_num) ----

    /**
     * 소수 둘째 자리(가비지 처리 성향은 첫째). 반올림해서 0이 되는데 원값이 0이 아니면 한 자리씩 더(최대 넷째).
     * 원값이 0이면 "0". 차이 값은 +/- 부호, 비율(역전 2개)은 부호 없음.
     * 반올림은 double의 정확한 값을 HALF_EVEN으로 — 파이썬 round와 같다 (0.125 → 0.12, 0.005 → 0.01).
     */
    public static String valueText(String key, double value) {
        if (value == 0) {
            return "0";
        }
        int nd = "delta_cheese_index".equals(key) ? 1 : 2;
        BigDecimal exact = new BigDecimal(value);
        BigDecimal r = exact.setScale(nd, RoundingMode.HALF_EVEN);
        while (r.signum() == 0 && nd < 4) {
            nd++;
            r = exact.setScale(nd, RoundingMode.HALF_EVEN);
        }
        String digits = r.abs().toPlainString();
        if (RATE_KEYS.contains(key)) {
            return digits;
        }
        // 파이썬 f"{r:+.4f}"는 -0.0을 "-0.0000"으로 쓴다 → 부호는 원값으로 정한다
        return (value < 0 ? "-" : "+") + digits;
    }

    // ---- 영역 (prompt.py heavy_zone, mid는 부호로 나눔) ----

    /** "high" · "low" · "mid+" · "mid-" · "zero". 범위가 없는 키(tr_trend_delta)는 high·low·zero만 */
    public String zone(String key, double value) {
        if (value == 0) {
            return "zero";
        }
        List<Double> q = ranges.get(key);
        if (q == null) {
            return value > 0 ? "high" : "low";
        }
        if (value > q.get(1)) {
            return "high";
        }
        if (value < q.get(0)) {
            return "low";
        }
        return value > 0 ? "mid+" : "mid-";
    }

    /** prompt.py strength_level — "pos" · "zero" · "low" · "mid" · "high" (경계값 Q1·Q3는 mid) */
    public String strengthLevel(double value) {
        if (value > 0) {
            return "pos";
        }
        if (value == 0) {
            return "zero";
        }
        List<Double> q = ranges.get("strength_split");
        if (value < q.get(0)) {
            return "low";
        }
        if (value <= q.get(1)) {
            return "mid";
        }
        return "high";
    }

    // ---- 방향 문장과 degree ----

    /**
     * 역전 2개를 뺀 지표의 방향.
     *
     * @param label 사전의 label — 플레이스타일 문장의 주어("Opener 성향은 …")로 쓴다
     */
    public Direction direction(String key, String label, double value) {
        if ("strength_split".equals(key)) {
            String level = strengthLevel(value);
            return new Direction(STRENGTH_LABELS.get(level), STRENGTH_DEGREES.get(level));
        }
        String zone = zone(key, value);
        if ("tr_trend_delta".equals(key)) {
            return new Direction(DIRECTIONS.get(key).get(zone), "zero".equals(zone) ? Degree.ZERO : Degree.MID);
        }
        Degree degree = zoneDegree(zone);
        if (PLAYSTYLE_KEYS.contains(key)) {
            // 플레이스타일 4개는 모두 '성향'으로 끝나 받침이 있다 → '은'
            return new Direction(label + "은 " + PLAYSTYLE_TAILS.get(zone), degree);
        }
        Map<String, String> sentences = DIRECTIONS.get(key);
        if (sentences == null) {
            throw new IllegalArgumentException("heavy 방향 문장이 없는 지표: " + key);
        }
        return new Direction(sentences.get(zone), degree);
    }

    /**
     * 역전 챕터 (prompt.py _comparison_line을 항목마다 나눔). 두 값을 견줘서만 방향이 정해진다.
     *
     * @param key     comeback_rate 또는 comeback_rate_against
     * @param rate    역전승률
     * @param against 역전 허용률
     */
    public static Direction comeback(String key, double rate, double against) {
        int cmp = Double.compare(rate, against);
        Degree degree = cmp == 0 ? Degree.ZERO : Degree.MID;
        String text = switch (key) {
            case "comeback_rate" -> cmp > 0 ? "역전승률이 역전 허용률보다 높습니다"
                    : cmp < 0 ? "역전승률이 역전 허용률보다 낮습니다"
                    : "역전승률과 역전 허용률이 같습니다";
            case "comeback_rate_against" -> cmp > 0 ? "역전 허용률이 역전승률보다 낮습니다"
                    : cmp < 0 ? "역전 허용률이 역전승률보다 높습니다"
                    : "역전 허용률과 역전승률이 같습니다";
            default -> throw new IllegalArgumentException("역전 챕터 지표가 아님: " + key);
        };
        return new Direction(text, degree);
    }

    private static Degree zoneDegree(String zone) {
        return switch (zone) {
            case "high", "low" -> Degree.STRONG;
            case "mid+", "mid-" -> Degree.WEAK;
            default -> Degree.ZERO;
        };
    }
}
