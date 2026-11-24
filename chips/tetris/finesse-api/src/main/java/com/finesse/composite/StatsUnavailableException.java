package com.finesse.composite;

/**
 * 통계를 지금 넘길 수 없음 — tetris-analytics에 닿지 않음·답이 늦음·회로가 열림.
 * {@link ApiErrorHandler}가 모놀리식과 같은 502 TETRIO_API_UNAVAILABLE + Retry-After로 바꾼다.
 */
public class StatsUnavailableException extends RuntimeException {

    private final int retryAfterSeconds;

    public StatsUnavailableException(String message, int retryAfterSeconds, Throwable cause) {
        super(message, cause);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
