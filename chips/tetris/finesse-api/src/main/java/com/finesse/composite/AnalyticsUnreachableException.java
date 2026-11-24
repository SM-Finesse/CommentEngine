package com.finesse.composite;

/**
 * tetris-analytics에 닿지 않음 — 연결 거부, 연결 타임아웃, 답 없이 연결이 끊김.
 * 다시 시도(1번)와 회로 차단기가 모두 세는 실패다. GET이라 다시 불러도 안전하다.
 */
public class AnalyticsUnreachableException extends RuntimeException {

    public AnalyticsUnreachableException(Throwable cause) {
        super("tetris-analytics에 닿지 않음: " + cause.getMessage(), cause);
    }
}
