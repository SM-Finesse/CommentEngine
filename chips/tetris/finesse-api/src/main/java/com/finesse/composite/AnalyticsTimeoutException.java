package com.finesse.composite;

/**
 * tetris-analytics가 요청은 받았는데 읽기 타임아웃(analytics.read-timeout-ms) 안에 답하지 않음.
 * analytics는 자기 마감(stats 20초)에 스스로 502로 답하게 되어 있으니, 25초가 지나도 답이 없으면 멈춰 있다는 뜻이다.
 * 회로 차단기는 실패로 세지만 다시 시도는 하지 않는다 — 다시 부르면 화면이 그만큼 더 기다리고, analytics가 TETR.IO를 또 부른다.
 */
public class AnalyticsTimeoutException extends RuntimeException {

    public AnalyticsTimeoutException(Throwable cause) {
        super("tetris-analytics가 제때 답하지 않음: " + cause.getMessage(), cause);
    }
}
