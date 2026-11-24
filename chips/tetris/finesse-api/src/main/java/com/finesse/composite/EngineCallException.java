package com.finesse.composite;

import com.commentengine.util.ErrorCode;

/**
 * 엔진 쪽 에러를 화면 에러로 바꾸기 위한 예외 — 엔진이 에러로 답했거나(util ErrorCode), 닿지 않음·회로 열림·시간 부족을
 * {@link EngineClient}가 같은 코드로 바꾼 것. {@link ApiErrorHandler}가 모놀리식과 같은 코드·문장으로 바꾼다.
 * <p>
 * 엔진이 <b>답한</b> 에러는 다시 부르지 않고 회로에도 세지 않는다 (resilience4j 설정의 retry-exceptions·record-exceptions에 없음).
 */
public class EngineCallException extends RuntimeException {

    private final ErrorCode code;
    /** 화면에 넘길 Retry-After(초). 없으면 null */
    private final Integer retryAfterSeconds;

    public EngineCallException(ErrorCode code, String message, Integer retryAfterSeconds) {
        super(message);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode code() {
        return code;
    }

    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
