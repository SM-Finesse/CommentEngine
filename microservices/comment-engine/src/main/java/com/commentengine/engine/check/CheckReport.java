package com.commentengine.engine.check;

import java.util.List;

/** 문장 하나에 검사 6종을 모두 돌린 결과 */
public record CheckReport(List<CheckResult> results) {

    public CheckReport {
        results = List.copyOf(results);
    }

    /** 6종을 모두 통과했는가 */
    public boolean passed() {
        return results.stream().allMatch(CheckResult::passed);
    }

    /** 실패한 코드 전부. CheckCode 선언 순서 */
    public List<CheckCode> failedCodes() {
        return results.stream().filter(r -> !r.passed()).map(CheckResult::code).toList();
    }
}
