package com.finesse.composite;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 통계 경로 GET /api/v1/stats/{username} — finesse-api가 tetris-analytics 응답을 고치지 않고 넘기는지, 다시 시도·회로 차단기가
 * 할당 문서대로 도는지 (1일차 시험 7개).
 * <p>
 * 시험 안에서 가짜 tetris-analytics(JDK HttpServer)를 띄우고 analytics.base-url을 그쪽으로 돌린다. 가짜 서버의 답:
 * OK(200 통계), ERROR(503 + Retry-After), DROP(답 없이 연결을 끊음 = 닿지 않음), SLOW(읽기 타임아웃보다 늦게 답함).
 * 시험이 빨리 끝나게 다시 시도 간격은 0.2초, 회로가 열려 있는 시간은 1초, 읽기 타임아웃은 0.8초로 줄였다.
 * <p>
 * 다시 시도 횟수는 가짜 서버가 받은 수가 아니라 Resilience4j 지표로 센다 — 답 없이 끊긴 연결은 JDK HttpClient가 GET을
 * 스스로 한 번 더 보낼 수 있어서(JDK 내장 동작) 받은 수가 Resilience4j 시도 수보다 많을 수 있다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "resilience4j.retry.instances.analytics.wait-duration=200ms",
        "resilience4j.circuitbreaker.instances.analytics.wait-duration-in-open-state=1s",
        "analytics.read-timeout-ms=800"})
class StatsRelayTest {

    enum Mode { OK, ERROR, DROP, SLOW }

    /** 통계 본문 — 공백·숫자 표기·칸 순서까지 바이트로 비교하려고 일부러 손으로 쓴 모양 */
    static final String STATS_JSON =
            "{\"username\":\"abc\",\"cold_start\":false,\"match_count\":40,\"fixed_metrics\":{\"win_rate\":0.50,\"tr_trend\":[21000.0]}}";
    static final String BUSY_JSON =
            "{\"error_code\":\"SERVER_BUSY\",\"message\":\"사용자가 많습니다, 잠시 후 다시 시도\",\"retry_after_seconds\":5}";
    static final String UNAVAILABLE_JSON =
            "{\"error_code\":\"TETRIO_API_UNAVAILABLE\",\"message\":\"일시적으로 조회할 수 없습니다, 잠시 후 다시 시도\"}";

    static volatile Mode mode = Mode.OK;
    /** 이 시각(System.nanoTime) 전까지 온 요청은 mode와 상관없이 끊는다 — "잠깐 닿지 않다가 돌아옴" */
    static volatile long dropUntilNanos;
    /** 가짜 analytics가 받은 요청 수 */
    static final AtomicInteger hits = new AtomicInteger();
    /** 가짜 analytics가 마지막으로 받은 경로와 쿼리 (인코딩된 그대로) */
    static volatile String lastRaw;

    static final HttpServer ANALYTICS;

    static {
        try {
            ANALYTICS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ANALYTICS.createContext("/", StatsRelayTest::handle);
            ANALYTICS.setExecutor(Executors.newCachedThreadPool()); // SLOW가 다른 요청을 막지 않게
            ANALYTICS.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void handle(HttpExchange ex) throws IOException {
        hits.incrementAndGet();
        URI u = ex.getRequestURI();
        lastRaw = u.getRawPath() + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery());
        if (mode == Mode.DROP || System.nanoTime() < dropUntilNanos) {
            ex.close(); // 답 없이 연결을 끊는다
            return;
        }
        switch (mode) {
            case ERROR -> {
                ex.getResponseHeaders().add("Retry-After", "5");
                reply(ex, 503, BUSY_JSON);
            }
            case SLOW -> {
                sleep(2000);
                reply(ex, 200, STATS_JSON);
            }
            default -> reply(ex, 200, STATS_JSON);
        }
    }

    private static void reply(HttpExchange ex, int status, String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        try {
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException ignored) {
            // SLOW: 부른 쪽이 이미 끊고 갔다
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @DynamicPropertySource
    static void analytics(DynamicPropertyRegistry r) {
        r.add("analytics.base-url", () -> "http://127.0.0.1:" + ANALYTICS.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        ANALYTICS.stop(0);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    @Autowired
    RetryRegistry retries;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void reset() {
        circuitBreakers.circuitBreaker(AnalyticsHttp.NAME).reset();
        mode = Mode.OK;
        dropUntilNanos = 0;
        hits.set(0);
        lastRaw = null;
    }

    @Test
    void 통계_본문을_바이트까지_그대로_넘긴다() throws Exception {
        HttpResponse<byte[]> resp = getBytes("/api/v1/stats/abc");

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).isEqualTo(STATS_JSON.getBytes(StandardCharsets.UTF_8)); // 0.50 같은 표기도 그대로
        assertThat(resp.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).startsWith("application/json"));
        assertThat(lastRaw).isEqualTo("/api/v1/stats/abc");
    }

    @Test
    void 경로와_쿼리를_받은_그대로_넘긴다() throws Exception {
        // %2D(-)를 풀거나 다시 인코딩하지 않는다. refresh=abc처럼 틀린 값도 판단은 analytics가 한다
        get("/api/v1/stats/a%2Db?refresh=abc");
        assertThat(lastRaw).isEqualTo("/api/v1/stats/a%2Db?refresh=abc");

        get("/api/v1/stats/abc?refresh=true");
        assertThat(lastRaw).isEqualTo("/api/v1/stats/abc?refresh=true");
    }

    @Test
    void 에러_응답도_상태_본문_Retry_After를_그대로_넘기고_다시_부르지_않는다() throws Exception {
        mode = Mode.ERROR;

        HttpResponse<String> resp = get("/api/v1/stats/abc");

        assertThat(resp.statusCode()).isEqualTo(503);
        assertThat(resp.body()).isEqualTo(BUSY_JSON);
        assertThat(resp.headers().firstValue("Retry-After")).hasValue("5");
        assertThat(hits.get()).as("analytics가 받은 요청 수").isEqualTo(1);
        assertThat(state()).as("답했으니 살아 있음").isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void 닿지_않으면_한_번만_다시_부른다() throws Exception {
        // 처음 0.1초 동안은 끊고 그 뒤로는 답한다. 다시 시도 간격(0.2초) 뒤의 두 번째 시도는 성공한다
        dropUntilNanos = System.nanoTime() + 100_000_000L;
        long before = retryMetrics().getNumberOfSuccessfulCallsWithRetryAttempt();

        HttpResponse<String> resp = get("/api/v1/stats/abc");

        assertThat(resp.statusCode()).as(resp.body()).isEqualTo(200);
        assertThat(resp.body()).isEqualTo(STATS_JSON);
        assertThat(retryMetrics().getNumberOfSuccessfulCallsWithRetryAttempt() - before)
                .as("다시 시도해서 성공한 호출 수").isEqualTo(1);
    }

    @Test
    void 계속_닿지_않으면_502_TETRIO_API_UNAVAILABLE과_Retry_After() throws Exception {
        mode = Mode.DROP;
        long before = retryMetrics().getNumberOfFailedCallsWithRetryAttempt();

        HttpResponse<String> resp = get("/api/v1/stats/abc");

        assertThat(resp.statusCode()).isEqualTo(502);
        assertThat(resp.body()).isEqualTo(UNAVAILABLE_JSON);
        assertThat(resp.headers().firstValue("Retry-After")).hasValue("5");
        assertThat(retryMetrics().getNumberOfFailedCallsWithRetryAttempt() - before)
                .as("다시 시도하고도 실패한 호출 수 (처음 1 + 다시 1 = 2번 시도)").isEqualTo(1);
    }

    @Test
    void 답이_늦으면_다시_부르지_않고_502() throws Exception {
        mode = Mode.SLOW;
        long before = retryMetrics().getNumberOfFailedCallsWithoutRetryAttempt();

        long start = System.nanoTime();
        HttpResponse<String> resp = get("/api/v1/stats/abc");
        long ms = (System.nanoTime() - start) / 1_000_000;

        assertThat(resp.statusCode()).isEqualTo(502);
        assertThat(resp.body()).isEqualTo(UNAVAILABLE_JSON);
        assertThat(resp.headers().firstValue("Retry-After")).hasValue("5");
        assertThat(hits.get()).as("analytics가 받은 요청 수").isEqualTo(1);
        assertThat(retryMetrics().getNumberOfFailedCallsWithoutRetryAttempt() - before)
                .as("다시 시도 없이 실패한 호출 수").isEqualTo(1);
        assertThat(ms).as("걸린 시간(ms) — 읽기 타임아웃 0.8초에서 끊음").isLessThan(1800);
    }

    @Test
    void 계속_닿지_않으면_회로가_열리고_열린_동안은_analytics를_부르지_않는다() throws Exception {
        mode = Mode.DROP;
        // 요청 1번에 2번 시도(처음 + 다시). 최근 5번 중 50% 실패면 열리므로 3번째 요청 안에서 열린다
        for (int i = 0; i < 3; i++) {
            assertThat(get("/api/v1/stats/abc").statusCode()).isEqualTo(502);
        }
        assertThat(state()).isEqualTo(CircuitBreaker.State.OPEN);

        int before = hits.get();
        long start = System.nanoTime();
        HttpResponse<String> resp = get("/api/v1/stats/abc");
        long ms = (System.nanoTime() - start) / 1_000_000;

        assertThat(resp.statusCode()).isEqualTo(502);
        assertThat(resp.body()).isEqualTo(UNAVAILABLE_JSON);
        assertThat(resp.headers().firstValue("Retry-After")).as("회로가 열려 있는 시간 (시험은 1초)").hasValue("1");
        assertThat(hits.get()).as("회로가 열린 뒤 analytics가 받은 요청 수").isEqualTo(before);
        assertThat(ms).as("걸린 시간(ms) — 부르지 않고 바로").isLessThan(500);
    }

    private Retry.Metrics retryMetrics() {
        return retries.retry(AnalyticsHttp.NAME).getMetrics();
    }

    private CircuitBreaker.State state() {
        return circuitBreakers.circuitBreaker(AnalyticsHttp.NAME).getState();
    }

    private HttpResponse<String> get(String pathAndQuery) throws IOException, InterruptedException {
        return client.send(request(pathAndQuery), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<byte[]> getBytes(String pathAndQuery) throws IOException, InterruptedException {
        return client.send(request(pathAndQuery), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpRequest request(String pathAndQuery) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + pathAndQuery)).GET().build();
    }
}
