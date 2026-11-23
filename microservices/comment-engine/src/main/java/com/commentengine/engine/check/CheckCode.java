package com.commentengine.engine.check;

/** 검사 6종의 이유 코드. 응답 checks의 이름으로 그대로 쓴다 */
public enum CheckCode {
    /** 형식 — 빈 문장, 길이 범위, 줄바꿈 */
    FORMAT,
    /** 숫자 대조 — 괄호 안 value_text 인용만 허용 */
    NUMBER,
    /** 강도 — degree와 맞지 않는 표현 */
    DEGREE,
    /** 금지 낱말 — 항목의 must_not */
    MUST_NOT,
    /** 프롬프트 노출 — 틀의 지시문 조각 */
    LEAK,
    /** 외국 문자 */
    CHARSET
}
