package com.finesse.composite.light;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** light 지표 사전을 시작할 때 한 번 읽고, 그 사전으로 틀·요청 만들기를 준비한다 */
@Configuration
public class LightConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LightConfiguration.class);

    @Bean
    LightMetrics lightMetrics(@Value("${light.metrics-file}") String file) {
        LightMetrics m = LightMetrics.load(Path.of(file));
        log.info("light 지표 사전 {} — 지표 {}개", m.version(), m.metrics().size());
        return m;
    }

    @Bean
    LightCommentTemplates lightCommentTemplates(LightMetrics metrics) {
        return new LightCommentTemplates(metrics);
    }

    @Bean
    LightRequestBuilder lightRequestBuilder(LightMetrics metrics, LightCommentTemplates templates) {
        return new LightRequestBuilder(metrics, templates);
    }
}
