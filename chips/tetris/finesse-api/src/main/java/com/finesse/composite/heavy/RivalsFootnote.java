package com.finesse.composite.heavy;

import com.finesse.tetris.api.StatsResponse.RivalItem;
import com.finesse.tetris.api.StatsResponse.Rivals;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * '자주 만난 상대' 챕터 각주 — 엔진을 부르지 않고 칩이 직접 쓴다 (조각 9 지침 2.4, 설계서 13장 "지표가 아닌 챕터는 칩이 직접").
 * 모놀리식 llm-server fallback.py {@code _rivals}(show=True)와 prompt.py {@code _rival_badge}를 그대로 옮겼다.
 * 닉네임이 들어가는 유일한 챕터라 엔진으로 보내지 않는다.
 */
public final class RivalsFootnote {

    /** 모놀리식 백엔드 CommentService.NO_RIVALS_FOOTNOTE */
    public static final String NO_RIVALS = "5경기 이상 만난 상대가 아직 없어 상성을 판단하기 이릅니다.";

    private RivalsFootnote() {
    }

    public static String of(Rivals rivals) {
        List<RivalItem> items = rivals == null || rivals.items() == null ? List.of() : rivals.items();
        List<RivalItem> named = items.stream()
                .filter(Objects::nonNull)
                .filter(r -> r.nicknameMasked() != null && !r.nicknameMasked().isEmpty())
                .limit(2)   // 각주는 1–2문장 (v1.2 5.3절)
                .toList();
        if (named.isEmpty()) {
            return NO_RIVALS;
        }
        return named.stream().map(RivalsFootnote::sentence).collect(Collectors.joining(" "));
    }

    static String sentence(RivalItem r) {
        String n = r.nicknameMasked();
        int m = r.matches();
        int w = r.wins();
        int l = r.losses();
        String record = m + "경기 " + w + "승 " + l + "패";   // 승률은 문장에 쓰지 않는다 (31번 3.2절)
        return switch (badge(m == 0 ? 0.0 : (double) w / m)) {
            case "천적" -> n + "에게는 " + record + "로 내가 자주 져서 천적입니다.";
            case "우세" -> n + "에게는 " + record + "로 내가 자주 이겨 우세합니다.";
            case "접전" -> n + "와는 " + record + "로 승패가 비슷한 접전입니다.";
            default -> n + "와는 " + record + "입니다.";
        };
    }

    /** prompt.py _rival_badge (calc 05 공식 해설 7.3절) */
    static String badge(double rate) {
        if (rate <= 0.40) {
            return "천적";
        }
        if (rate >= 0.65) {
            return "우세";
        }
        if (rate >= 0.45 && rate <= 0.55) {
            return "접전";
        }
        return "-";
    }
}
