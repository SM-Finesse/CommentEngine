package com.commentengine.engine.check;

/** 문장을 만든 틀의 종류 */
public enum TemplateKind {
    /** 항목 하나의 한 문장. 숫자 있음 */
    SENTENCE,
    /** 항목 여러 개의 요약. 숫자 없음 */
    SUMMARY,
    /** 섹션 글. 숫자 있음 */
    SECTION
}
