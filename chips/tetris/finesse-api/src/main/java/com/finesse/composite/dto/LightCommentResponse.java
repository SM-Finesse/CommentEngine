package com.finesse.composite.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 화면이 받는 light 응답 — 모놀리식과 같은 모양 {light_summary, highlights[{stat, sentence}], attempt_count}.
 * attempt_count는 엔진을 부르지 않은 응답(콜드스타트·고른 지표 0개)에서는 칸째 빠진다 (모놀리식 non_null).
 * 설정 전체에 non_null을 걸면 통계 DTO 시험에 영향이 갈 수 있어 이 DTO에만 붙였다 (지침서 3.5).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LightCommentResponse(String lightSummary, List<Highlight> highlights, Integer attemptCount) {

    /** 엔진을 부르지 않은 응답 */
    public LightCommentResponse(String lightSummary, List<Highlight> highlights) {
        this(lightSummary, highlights, null);
    }

    public record Highlight(String stat, String sentence) {
    }
}
