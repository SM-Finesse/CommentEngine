package com.commentengine.gateway;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 잘못 적힌 칩 경로 파일이 있으면 gateway가 뜨지 않는다 (조용히 그 칩만 빠진 채 뜨면 늦게 알아챔).
 */
class GatewayBrokenChipTests {

    @Test
    void uri가_빠진_칩_파일이면_gateway가_뜨지_않는다() {
        // 시험용 잘못된 칩: src/test/resources/chips-no-uri/broken/gateway.yml (paths만 있고 uri 없음)
        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(GatewayApplication.class)
                    .properties("CHIPS_DIR=src/test/resources/chips-no-uri", "server.port=0")
                    .run()) {
                // 여기까지 오면 뜬 것 — 실패
            }
        }).rootCause().hasMessageContaining("app.routes.broken-api.uri");
    }

    @Test
    void paths가_없거나_빈_값이면_거부한다() {
        assertThatThrownBy(() -> ChipRoutesConfiguration.check("a", new ChipRoutesConfiguration.Target("http://x", null)))
                .hasMessageContaining("app.routes.a.paths");
        assertThatThrownBy(() -> ChipRoutesConfiguration.check("b", new ChipRoutesConfiguration.Target("http://x", List.of(" "))))
                .hasMessageContaining("app.routes.b.paths");
        assertThatThrownBy(() -> ChipRoutesConfiguration.check("c", new ChipRoutesConfiguration.Target(" ", List.of("/x/**"))))
                .hasMessageContaining("app.routes.c.uri");
    }

    @Test
    void uri와_paths가_있으면_통과한다() {
        assertThatCode(() -> ChipRoutesConfiguration.check("ok", new ChipRoutesConfiguration.Target("http://x", List.of("/x/**"))))
                .doesNotThrowAnyException();
    }
}
