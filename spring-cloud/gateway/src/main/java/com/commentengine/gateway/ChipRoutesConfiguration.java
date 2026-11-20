package com.commentengine.gateway;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 칩이 들고 온 경로를 gateway에 꽂는다 (설계서 2장 슬롯 ①, 9장 chips/&lt;칩&gt;/gateway.yml).
 * <p>
 * 칩은 자기 폴더의 {@code gateway.yml}에 {@code app.routes.<이름>}(uri, paths)으로 "이 경로는 내 서비스로"를 적어 온다.
 * gateway는 그 파일들을 설정으로 함께 읽고(application.yml의 {@code chips/*}{@code /gateway.yml}), 여기서 경로로 만든다.
 * 칩을 꽂거나 뺄 때 gateway 코드와 config-repo는 바뀌지 않는다.
 * <p>
 * {@code spring.cloud.gateway...routes} 목록에 적지 않는 이유: 스프링 설정에서 목록은 파일끼리 합쳐지지 않고 마지막 파일의 것으로
 * 통째로 바뀐다. 이름이 붙은 묶음(map)은 파일끼리 합쳐져서 칩마다 파일을 따로 둘 수 있다.
 */
@Configuration
@EnableConfigurationProperties(ChipRoutesConfiguration.ChipRoutes.class)
public class ChipRoutesConfiguration {

    /** 이름 → 넘길 곳 ({@code app.routes}). 칩이 하나도 없으면 null */
    @ConfigurationProperties("app")
    public record ChipRoutes(Map<String, Target> routes) {
    }

    /**
     * @param uri   넘길 서비스 주소 (예: http://localhost:9999)
     * @param paths 그 서비스로 보낼 경로 무늬 (예: /api/**)
     */
    public record Target(String uri, List<String> paths) {
    }

    @Bean
    RouteLocator chipRoutes(RouteLocatorBuilder builder, ChipRoutes chips) {
        RouteLocatorBuilder.Builder routes = builder.routes();
        if (chips.routes() == null) {
            return routes.build();
        }
        chips.routes().forEach((name, target) -> {
            check(name, target);
            routes.route(name, r -> r.path(target.paths().toArray(String[]::new)).uri(target.uri()));
        });
        return routes.build();
    }

    /** 잘못 적힌 칩 경로는 조용히 넘기지 않는다 — gateway가 뜨지 않게 해서 바로 알게 한다 */
    static void check(String name, Target target) {
        if (target == null || target.uri() == null || target.uri().isBlank()) {
            throw new IllegalStateException("app.routes." + name + ".uri 가 없습니다 (칩 gateway.yml 확인)");
        }
        if (target.paths() == null || target.paths().isEmpty()
                || target.paths().stream().anyMatch(p -> p == null || p.isBlank())) {
            throw new IllegalStateException("app.routes." + name + ".paths 가 없거나 빈 값이 있습니다 (칩 gateway.yml 확인)");
        }
    }
}
