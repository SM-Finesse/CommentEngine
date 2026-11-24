package com.finesse.composite.heavy;

import static org.assertj.core.api.Assertions.assertThat;

import com.finesse.tetris.api.StatsResponse.RivalItem;
import com.finesse.tetris.api.StatsResponse.Rivals;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 조각 9 1일차 시험 7 — 라이벌 각주.
 * 정답 문장은 모놀리식 llm-server fallback.py _rivals(show=True)를 파이썬으로 직접 돌린 값이다.
 */
class RivalsFootnoteTest {

    static RivalItem rival(String nick, int matches, int wins, int losses) {
        return new RivalItem(nick, matches, wins, losses, null);
    }

    static Rivals rivals(RivalItem... items) {
        return new Rivals(new ArrayList<>(Arrays.asList(items)), 1, 10, items.length);
    }

    @Test
    void 라이벌이_없으면_고정_문장() {
        String fixed = "5경기 이상 만난 상대가 아직 없어 상성을 판단하기 이릅니다.";
        assertThat(RivalsFootnote.of(rivals())).isEqualTo(fixed);
        assertThat(RivalsFootnote.of(null)).isEqualTo(fixed);
        assertThat(RivalsFootnote.of(new Rivals(null, 1, 10, 0))).isEqualTo(fixed);
        // 닉네임이 있는 상대가 하나도 없어도 같은 문장
        assertThat(RivalsFootnote.of(rivals(rival(null, 6, 1, 5)))).isEqualTo(fixed);
    }

    @ParameterizedTest(name = "{0}승 {1}패")
    @CsvSource(delimiter = '|', value = {
            "3 | 7 | ab*cd에게는 10경기 3승 7패로 내가 자주 져서 천적입니다.",
            "7 | 3 | ab*cd에게는 10경기 7승 3패로 내가 자주 이겨 우세합니다.",
            "5 | 5 | ab*cd와는 10경기 5승 5패로 승패가 비슷한 접전입니다.",
            "6 | 4 | ab*cd와는 10경기 6승 4패입니다."
    })
    void 배지_4종(int wins, int losses, String expected) {
        assertThat(RivalsFootnote.of(rivals(rival("ab*cd", 10, wins, losses)))).isEqualTo(expected);
    }

    @Test
    void 배지_경계값() {
        assertThat(RivalsFootnote.badge(0.40)).isEqualTo("천적");
        assertThat(RivalsFootnote.badge(0.65)).isEqualTo("우세");
        assertThat(RivalsFootnote.badge(0.45)).isEqualTo("접전");
        assertThat(RivalsFootnote.badge(0.55)).isEqualTo("접전");
        assertThat(RivalsFootnote.badge(0.42)).isEqualTo("-");
        assertThat(RivalsFootnote.badge(0.60)).isEqualTo("-");
    }

    @Test
    void 앞에서_2명만_닉네임_없는_상대는_건너뜀() {
        Rivals r = rivals(
                rival(null, 5, 1, 4),
                rival("ab*cd", 10, 3, 7),
                rival("xy*z", 10, 7, 3),
                rival("q*q", 10, 5, 5));
        assertThat(RivalsFootnote.of(r)).isEqualTo(
                "ab*cd에게는 10경기 3승 7패로 내가 자주 져서 천적입니다. xy*z에게는 10경기 7승 3패로 내가 자주 이겨 우세합니다.");
        assertThat(RivalsFootnote.of(r)).doesNotContain("q*q");
    }

    @Test
    void 승률은_문장에_쓰지_않는다() {
        List<String> all = List.of(
                RivalsFootnote.of(rivals(rival("a*b", 10, 3, 7))),
                RivalsFootnote.of(rivals(rival("a*b", 10, 6, 4))));
        assertThat(all).allSatisfy(s -> assertThat(s).doesNotContain("%").doesNotContain("0.3").doesNotContain("승률"));
    }
}
