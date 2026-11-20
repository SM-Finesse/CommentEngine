package com.finesse.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    // stats 엔드포인트 상한(EndpointProperties.statsSeconds) 강제용 — TetrioClient 호출은 블로킹이라
    // 별도 스레드에서 돌리고 Future.get(timeout)으로 마감을 건다.
    @Bean
    public ExecutorService statsExecutor() {
        return Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
    }
}
