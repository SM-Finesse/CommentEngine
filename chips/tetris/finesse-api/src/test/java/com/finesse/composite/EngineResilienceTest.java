package com.finesse.composite;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 엔진 장애 (지침서 3.6의 11~13). 엔진 주소는 아무도 듣지 않는 포트 — 시험 12에서만 그 포트에 가짜 엔진을 띄워 "살아남"을 본다.
 * 시험이 빨리 끝나게 다시 시도 간격 0.1초, 회로 열림 1초.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "resilience4j.retry.instances.engine.wait-duration=100ms",
        "resilience4j.circuitbreaker.instances.engine.wait-duration-in-open-state=1s"})
class EngineResilienceTest {

    static final HttpServer ANALYTICS = FakeServers.analytics();
    static final int ENGINE_PORT = FakeServers.closedPort();
    static final FakeServers.Engine ENGINE = new FakeServers.Engine();

    static final String UNAVAILABLE_JSON =
            "{\"error_code\":\"LLM_UNAVAILABLE\",\"message\":\"일시적으로 코멘트를 생성할 수 없습니다, 잠시 후 다시 시도\"}";

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry r) {
        r.add("analytics.base-url", () -> "http://127.0.0.1:" + ANALYTICS.getAddress().getPort());
        r.add("engine.base-url", () -> "http://127.0.0.1:" + ENGINE_PORT);
    }

    @AfterAll
    static void stopAll() {
        ANALYTICS.stop(0);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    @Autowired
    RetryRegistry retries;

    @Autowired
    LightCommentService service;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void reset() {
        ENGINE.stop();
        ENGINE.reset();
        circuitBreakers.circuitBreaker(EngineHttp.NAME).reset();
        service.clearCache();
    }

    @AfterEach
    void stopEngine() {
        ENGINE.stop();
    }

    @Test
    void 엔진에_닿지_않으면_두_번_시도하고_503_Retry_After_5() throws Exception {
        long before = retryMetrics().getNumberOfFailedCallsWithRetryAttempt();

        HttpResponse<String> resp = get("/api/v1/comment/abc?scope=light");

        assertThat(resp.statusCode()).isEqualTo(503);
        assertThat(resp.body()).isEqualTo(UNAVAILABLE_JSON);
        assertThat(resp.headers().firstValue("Retry-After")).hasValue("5");
        assertThat(retryMetrics().getNumberOfFailedCallsWithRetryAttempt() - before)
                .as("다시 시도하고도 실패한 호출 (처음 1 + 다시 1)").isEqualTo(1);
    }

    @Test
    void 계속_닿지_않으면_회로가_열려_부르지_않고_503_엔진이_살아나면_다시_200() throws Exception {
        // 요청 1번 = 시도 2번. 최근 5번 중 50% 실패면 열리므로 3번째 요청 안에서 열린다
        for (int i = 0; i < 3; i++) {
            assertThat(get("/api/v1/comment/abc?scope=light").statusCode()).isEqualTo(503);
        }
        assertThat(state()).isEqualTo(CircuitBreaker.State.OPEN);

        long start = System.nanoTime();
        HttpResponse<String> open = get("/api/v1/comment/abc?scope=light");
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertThat(open.statusCode()).isEqualTo(503);
        assertThat(open.body()).isEqualTo(UNAVAILABLE_JSON);
        assertThat(open.headers().firstValue("Retry-After")).as("회로 열린 시간 (시험은 1초)").hasValue("1");
        assertThat(ms).as("부르지 않고 바로").isLessThan(500);

        ENGINE.start(ENGINE_PORT); // 엔진이 살아남
        Thread.sleep(1300);        // 1초 뒤 반쯤 열림
        HttpResponse<String> back = get("/api/v1/comment/abc?scope=light");
        assertThat(back.statusCode()).as(back.body()).isEqualTo(200);
        assertThat(ENGINE.hits.get()).isEqualTo(1);
    }

    @Test
    void 엔진이_죽어도_통계는_200() throws Exception {
        HttpResponse<String> resp = get("/api/v1/stats/abc");

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).contains("\"username\":\"abc\"");
    }

    private Retry.Metrics retryMetrics() {
        return retries.retry(EngineHttp.NAME).getMetrics();
    }

    private CircuitBreaker.State state() {
        return circuitBreakers.circuitBreaker(EngineHttp.NAME).getState();
    }

    private HttpResponse<String> get(String pathAndQuery) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + pathAndQuery)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
