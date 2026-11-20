package com.finesse.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 엔드포인트(요청 하나를 끝내야 하는 마감) 레벨 타임아웃 — 타임아웃 기준 문서(23번) 9절.
 * "호출 1회" 타임아웃은 {@link TetrioProperties}에서 설정한다.
 * 엔드포인트는 재시도까지 포함한 요청 전체의 최악 경로 상한이고, 항상 안쪽(호출 1회 × 시도횟수)보다 길다.
 */
@ConfigurationProperties(prefix = "app.endpoint")
public record EndpointProperties(int statsSeconds) {
}
