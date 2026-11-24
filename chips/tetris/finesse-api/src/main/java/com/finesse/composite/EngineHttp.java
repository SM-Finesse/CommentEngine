package com.finesse.composite;

import com.commentengine.util.ErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.function.IntFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * comment-engine HTTP 호출 한 번 — 조각 4 {@link AnalyticsHttp}와 같은 구조.
 * <ul>
 *   <li>엔진이 에러로 답함 → 몸통 {error_code, message}(util ErrorResponse)을 읽어 {@link EngineCallException}.
 *       다시 부르지 않고 회로에도 세지 않는다 (엔진은 살아 있음)</li>
 *   <li>닿지 않음 → {@link EngineUnreachableException}: 다시 시도 1번 + 회로 실패</li>
 *   <li>답이 늦음(읽기 45초) → {@link EngineTimeoutException}: 회로 실패만, 다시 시도 없음</li>
 * </ul>
 * {@code @Retry}·{@code @CircuitBreaker}는 다른 빈({@link EngineClient})에서 불러야 걸린다.
 */
@Component
public class EngineHttp {

    /** resilience4j.retry.instances.engine, resilience4j.circuitbreaker.instances.engine */
    static final String NAME = "engine";

    /** 남은 시간이 이보다 적으면 부르지 않는다 — 계약상 deadline_ms는 1000 이상 (미만이면 엔진이 400) */
    static final int MIN_REMAINING_MS = 1000;

    private final RestClient rest;
    private final JsonMapper errorMapper = JsonMapper.builder().build();

    public EngineHttp(RestClient.Builder builder,
                      @Value("${engine.base-url}") String baseUrl,
                      @Value("${engine.connect-timeout-ms}") int connectTimeoutMs,
                      @Value("${engine.read-timeout-ms}") int readTimeoutMs) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.rest = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    /**
     * POST. 몸통은 <b>부를 때마다 남은 시간(ms)으로 다시 만든다</b> — 다시 시도해도 처음 정한 마감은 그대로다.
     *
     * @param deadline 처음 부를 때 정한 마감 시각
     * @param body     남은 시간(ms) → 보낼 몸통
     */
    @Retry(name = NAME)
    @CircuitBreaker(name = NAME)
    public <T> T post(String path, Instant deadline, IntFunction<?> body, Class<T> type) {
        long remaining = Duration.between(Instant.now(), deadline).toMillis();
        if (remaining < MIN_REMAINING_MS) {
            throw new EngineCallException(ErrorCode.LLM_TIMEOUT, "엔진을 부를 시간이 남지 않음 (" + remaining + "ms)", null);
        }
        try {
            return rest.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.apply((int) remaining))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::throwEngineError)
                    .body(type);
        } catch (ResourceAccessException e) {
            if (AnalyticsHttp.isReadTimeout(e)) {
                throw new EngineTimeoutException(e);
            }
            throw new EngineUnreachableException(e);
        }
    }

    private void throwEngineError(HttpRequest request, ClientHttpResponse response) throws IOException {
        throw toEngineException(response.getStatusCode().value(),
                response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER),
                new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    /** 엔진 에러 몸통 → 예외. 모르는 코드(또는 몸통이 깨짐)는 상태로: 503 → LLM_UNAVAILABLE, 그 밖 → LLM_FORMAT */
    EngineCallException toEngineException(int status, String retryAfter, String body) {
        String codeName = null;
        String message = "HTTP " + status;
        try {
            JsonNode n = errorMapper.readTree(body);
            codeName = n.path("error_code").asString(null);
            message = n.path("message").asString(message);
        } catch (RuntimeException ignored) {
            // 몸통이 JSON이 아님 — 상태로 판단
        }
        ErrorCode code = codeOf(codeName);
        if (code == null) {
            code = status == 503 ? ErrorCode.LLM_UNAVAILABLE : ErrorCode.LLM_FORMAT;
        }
        return new EngineCallException(code, message, parseRetryAfter(retryAfter));
    }

    private static ErrorCode codeOf(String name) {
        for (ErrorCode c : ErrorCode.values()) {
            if (c.name().equals(name)) {
                return c;
            }
        }
        return null;
    }

    /** 초 단위 숫자만 읽는다. 날짜 형식이나 깨진 값은 무시(null) */
    private static Integer parseRetryAfter(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
