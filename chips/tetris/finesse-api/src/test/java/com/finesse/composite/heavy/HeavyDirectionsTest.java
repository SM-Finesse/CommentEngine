package com.finesse.composite.heavy;

import static org.assertj.core.api.Assertions.assertThat;

import com.commentengine.api.Degree;
import com.finesse.composite.heavy.HeavyDirections.Direction;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 조각 9 1일차 시험 1·2 — heavy 방향 문장·degree, 값 표기.
 * 정답은 모놀리식(11/24 main 3859825) llm-server app/prompt.py의 _direction·_playstyle_lines·fmt_num을 파이썬으로 직접 돌려 뽑은 값이다.
 */
class HeavyDirectionsTest {

    static final HeavyChapterBook BOOK = HeavyChapterBook.load(Path.of("../config/heavy-chapters.json"));
    final HeavyDirections directions = new HeavyDirections(BOOK.ranges());

    /** 1. HEAVY_DIRECTIONS 5개 × 영역 5칸, 플레이스타일 5칸, tr_trend 3칸, strength_split(경계값 포함) */
    @ParameterizedTest(name = "{0} {2} → {3}")
    @CsvSource({
            "delta_app, -, 0.034, 블록당 공격력에서 상대보다 앞섭니다, STRONG",
            "delta_app, -, -0.053, 블록당 공격력에서 상대보다 밀립니다, STRONG",
            "delta_app, -, 0.012, 블록당 공격력에서 상대보다 조금 앞서지만 차이는 크지 않습니다, WEAK",
            "delta_app, -, -0.0215, 블록당 공격력에서 상대보다 조금 밀리지만 차이는 크지 않습니다, WEAK",
            "delta_app, -, 0.0, 블록당 공격력은 상대와 같습니다, ZERO",
            "delta_weighted_app, -, 0.036, 보정 공격 효율에서 상대보다 앞섭니다, STRONG",
            "delta_weighted_app, -, -0.036, 보정 공격 효율에서 상대보다 밀립니다, STRONG",
            "delta_weighted_app, -, 0.013, 보정 공격 효율에서 상대보다 조금 앞서지만 차이는 크지 않습니다, WEAK",
            "delta_weighted_app, -, -0.013, 보정 공격 효율에서 상대보다 조금 밀리지만 차이는 크지 않습니다, WEAK",
            "delta_weighted_app, -, 0.0, 보정 공격 효율은 상대와 같습니다, ZERO",
            "delta_vs_apm, -, 0.148, 상대보다 수비 비중이 큰 편입니다, STRONG",
            "delta_vs_apm, -, -0.11, 상대보다 수비 비중이 작은 편입니다, STRONG",
            "delta_vs_apm, -, 0.069, 상대보다 수비 비중이 약간 큰 편이지만 차이는 크지 않습니다, WEAK",
            "delta_vs_apm, -, -0.05, 상대보다 수비 비중이 약간 작은 편이지만 차이는 크지 않습니다, WEAK",
            "delta_vs_apm, -, 0.0, 상대와 같습니다, ZERO",
            "delta_cheese_index, -, 14.852, 상대보다 가비지 처리 성향이 짙게 나타납니다, STRONG",
            "delta_cheese_index, -, -8.949, 상대보다 가비지 처리 성향이 옅게 나타납니다, STRONG",
            "delta_cheese_index, -, 6.926, 상대보다 가비지 처리 성향이 약간 짙지만 차이는 크지 않습니다, WEAK",
            "delta_cheese_index, -, -3.9745, 상대보다 가비지 처리 성향이 약간 옅지만 차이는 크지 않습니다, WEAK",
            "delta_cheese_index, -, 0.0, 상대와 같습니다, ZERO",
            "session_vs_slope, -, 1.38, 라운드가 진행될수록 공방 능력이 크게 오릅니다, STRONG",
            "session_vs_slope, -, -0.09, 라운드가 진행될수록 공방 능력이 처집니다, STRONG",
            "session_vs_slope, -, 0.685, 라운드가 진행될수록 공방 능력이 조금 오르지만 변화는 크지 않습니다, WEAK",
            "session_vs_slope, -, -0.04, 라운드가 진행될수록 공방 능력이 조금 처지지만 변화는 크지 않습니다, WEAK",
            "session_vs_slope, -, 0.0, 뚜렷한 추세가 없습니다, ZERO",
            "delta_opener, Opener 성향, 0.3, Opener 성향은 상대보다 짙게 나타납니다, STRONG",
            "delta_opener, Opener 성향, -0.3, Opener 성향은 상대보다 옅게 나타납니다, STRONG",
            "delta_opener, Opener 성향, 0.1, Opener 성향은 상대보다 조금 짙지만 차이는 크지 않습니다, WEAK",
            "delta_opener, Opener 성향, -0.1, Opener 성향은 상대보다 조금 옅지만 차이는 크지 않습니다, WEAK",
            "delta_opener, Opener 성향, 0.0, Opener 성향은 상대와 같습니다, ZERO",
            "tr_trend_delta, TR, 120.5, 최근 구간 TR이 전체 평균 TR보다 높습니다, MID",
            "tr_trend_delta, TR, -80.0, 최근 구간 TR이 전체 평균 TR보다 낮습니다, MID",
            "tr_trend_delta, TR, 0.0, 최근 구간 TR이 전체 평균 TR과 같습니다, ZERO",
            "strength_split, -, -0.5, 강한 상대 구간에서는 약한 상대 구간보다 승률이 크게 떨어집니다, STRONG",
            "strength_split, -, -0.395, 강한 상대 구간에서는 약한 상대 구간보다 승률이 떨어집니다, MID",
            "strength_split, -, -0.2, 강한 상대 구간에서는 약한 상대 구간보다 승률이 떨어집니다, MID",
            "strength_split, -, -0.139, 강한 상대 구간에서는 약한 상대 구간보다 승률이 떨어집니다, MID",
            "strength_split, -, -0.1, 강한 상대 구간에서는 약한 상대 구간보다 승률이 조금 떨어지지만 그 폭은 작습니다, WEAK",
            "strength_split, -, 0.0, 강한 상대 구간에서는 약한 상대 구간과 승률 차이가 없습니다, ZERO",
            "strength_split, -, 0.05, 강한 상대 구간에서는 약한 상대 구간보다 승률이 오히려 높습니다, STRONG"

    })
    void 방향_문장은_모놀리식과_같다(String key, String label, double value, String text, Degree degree) {
        Direction d = directions.direction(key, label, value);
        assertThat(d.text()).isEqualTo(text);
        assertThat(d.degree()).isEqualTo(degree);
    }

    /** 1. 플레이스타일 4개 모두 '{label}은 …' — 같은 영역은 같은 꼬리 */
    @Test
    void 플레이스타일_4개는_label이_주어() {
        assertThat(directions.direction("delta_plonk", "Plonk 성향", 0.2).text())
                .isEqualTo("Plonk 성향은 상대보다 짙게 나타납니다");
        assertThat(directions.direction("delta_stride", "Stride 성향", -0.01).text())
                .isEqualTo("Stride 성향은 상대보다 조금 옅지만 차이는 크지 않습니다");
        assertThat(directions.direction("delta_inf_ds", "Infinite Downstack 성향", 0.3).degree())
                .isEqualTo(Degree.STRONG);
    }

    /** 1. 역전 3칸 — 두 항목이 짝을 이룬다 */
    @ParameterizedTest(name = "{0} vs {1}")
    @CsvSource({
            "0.42, 0.30, 역전승률이 역전 허용률보다 높습니다, 역전 허용률이 역전승률보다 낮습니다, MID",
            "0.20, 0.35, 역전승률이 역전 허용률보다 낮습니다, 역전 허용률이 역전승률보다 높습니다, MID",
            "0.30, 0.30, 역전승률과 역전 허용률이 같습니다, 역전 허용률과 역전승률이 같습니다, ZERO"
    })
    void 역전_방향(double rate, double against, String rateText, String againstText, Degree degree) {
        Direction r = HeavyDirections.comeback("comeback_rate", rate, against);
        Direction a = HeavyDirections.comeback("comeback_rate_against", rate, against);
        assertThat(r.text()).isEqualTo(rateText);
        assertThat(a.text()).isEqualTo(againstText);
        assertThat(r.degree()).isEqualTo(degree);
        assertThat(a.degree()).isEqualTo(degree);
    }

    /** 2. 값 표기 — fmt_num을 실제로 돌린 값 */
    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource({
            "delta_app, 0.36645, +0.37",
            "delta_app, -0.004, -0.004",
            "delta_app, 0.00004, +0.0000",
            "delta_app, -0.00004, -0.0000",
            "delta_app, 0, 0",
            "delta_app, 0.125, +0.12",
            "delta_app, 0.005, +0.01",
            "delta_app, 0.015, +0.01",
            "delta_app, -1.0, -1.00",
            "delta_app, 2.5, +2.50",
            "tr_trend_delta, 120.5, +120.50",
            "delta_cheese_index, 13.86, +13.9",
            "delta_cheese_index, 0.04, +0.04",
            "delta_cheese_index, -0.25, -0.2",
            "comeback_rate, 0.425, 0.42",
            "comeback_rate_against, 0.3, 0.30"
    })
    void 값_표기(String key, double value, String expected) {
        assertThat(HeavyDirections.valueText(key, value)).isEqualTo(expected);
    }
}
