package com.commentengine.engine.check;

/** 검사 하나의 결과 — 통과 여부와 이유 코드 */
public record CheckResult(CheckCode code, boolean passed) {
}
