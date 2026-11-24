package com.finesse.composite;

/**
 * comment-engine에 닿지 않음 — 연결 거부, 연결 타임아웃, 답 없이 연결이 끊김.
 * 다시 시도(1번)와 회로 차단기가 모두 세는 실패다.
 */
public class EngineUnreachableException extends RuntimeException {

    public EngineUnreachableException(Throwable cause) {
        super("comment-engine에 닿지 않음: " + cause.getMessage(), cause);
    }
}
