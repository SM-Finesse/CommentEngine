package com.finesse.composite;

/**
 * comment-engine이 요청은 받았는데 읽기 타임아웃(engine.read-timeout-ms, 45초) 안에 답하지 않음.
 * 엔진은 deadline_ms(최대 40초) 안에 스스로 504로 답하게 되어 있으니 45초가 지나도 답이 없으면 멈춰 있다는 뜻이다.
 * 회로 차단기는 실패로 세지만 다시 시도는 하지 않는다 — 다시 부르면 생성 서버를 또 쓰고 화면은 그만큼 더 기다린다.
 * 화면에는 504 LLM_TIMEOUT.
 */
public class EngineTimeoutException extends RuntimeException {

    public EngineTimeoutException(Throwable cause) {
        super("comment-engine이 제때 답하지 않음: " + cause.getMessage(), cause);
    }
}
