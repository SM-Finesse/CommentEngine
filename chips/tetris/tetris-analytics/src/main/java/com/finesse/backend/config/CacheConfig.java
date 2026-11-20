package com.finesse.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 인메모리 캐시(Caffeine) 설정 — Finesse-API명세서 5장.
 * 통계 캐시는 username을 키로 사용한다.
 * TTL은 최대 ttlMinutes(기본 10분) 유지 후 자동 소멸 — 만료되면 다음 요청에서 자동 재조회된다.
 */
@Configuration
public class CacheConfig {

    public static final String STATS_CACHE = "stats";

    @Bean
    public CacheManager cacheManager(AppCacheProperties appCacheProperties) {
        CaffeineCacheManager manager = new CaffeineCacheManager(STATS_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(appCacheProperties.ttlMinutes(), TimeUnit.MINUTES));
        return manager;
    }
}
