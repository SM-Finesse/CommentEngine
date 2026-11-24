package com.commentengine.engine;

/** 생성 요청의 종류. 선언 순서가 줄 순서다 — 앞에 있을수록 먼저 들어간다 */
enum GenerationKind {
    /** 짧은 코멘트 — 빨리 끝나므로 먼저 */
    LIGHT,
    /** 섹션 단위 긴 글 */
    SECTION
}
