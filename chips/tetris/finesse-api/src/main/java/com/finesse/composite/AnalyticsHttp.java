package com.finesse.composite;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * tetris-analytics를 한 번 부른다. 받은 응답(상태 코드, 본문 바이트, Content-Type, Retry-After)을 고치지 않고 돌려준다.
 * <ul>
 *   <li>analytics가 4xx·5xx로 <b>답한 것은 예외가 아니라 응답</b>이다. 답했다면 살아 있다는 뜻이라 회로에도 세지 않는다.</li>
 *   <li>닿지 않음 → {@link AnalyticsUnreachableException}: 다시 시도 1번 + 회로 실패로 셈</li>
 *   <li>답이 늦음(읽기 타임아웃) → {@link AnalyticsTimeoutException}: 회로 실패로만 셈, 다시 시도하지 않음</li>
 * </ul>
 * {@code @Retry}·{@code @CircuitBreaker}는 스프링 AOP라서 <b>다른 빈에서 불러야</b> 걸린다. 그래서 컨트롤러와 빈을 나눴다.
 * 두 애너테이션이 같이 있으면 Resilience4j 기본 순서대로 Retry가 바깥, CircuitBreaker가 안쪽이다 —
 * 시도 한 번 한 번이 회로에 세어지고, 회로가 열리면 {@code CallNotPermittedException}은 다시 시도 대상이 아니라 바로 올라간다.
 */
@Component
public class AnalyticsHttp {

    /** resilience4j.retry.instances.analytics, resilience4j.circuitbreaker.instances.analytics */
    static final String NAME = "analytics";

    /** 화면에 그대로 넘길 응답 */
    public record Relayed(int status, MediaType contentType, String retryAfter, byte[] body) {
    }

    private final RestClient rest;
    private final String baseUrl;

    public AnalyticsHttp(RestClient.Builder builder,
                         @Value("${analytics.base-url}") String baseUrl,
                         @Value("${analytics.connect-timeout-ms}") int connectTimeoutMs,
                         @Value("${analytics.read-timeout-ms}") int readTimeoutMs) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.rest = builder.requestFactory(factory).build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * GET. 경로와 쿼리는 화면에서 받은 <b>인코딩된 문자열 그대로</b> 붙인다.
     * {@code URI.create}는 다시 인코딩하지 않으므로 {@code %2D} 같은 값이 그대로 간다 (RestClient의 uri(String)은 템플릿으로 다시 인코딩함).
     */
    @Retry(name = NAME)
    @CircuitBreaker(name = NAME)
    public Relayed get(String rawPathAndQuery) {
        URI uri = URI.create(baseUrl + rawPathAndQuery);
        try {
            // exchange: 상태 코드와 상관없이 응답을 직접 읽는다 (retrieve와 달리 4xx·5xx에도 예외를 던지지 않음)
            return rest.get().uri(uri).exchange((request, response) -> new Relayed(
                    response.getStatusCode().value(),
                    response.getHeaders().getContentType(),
                    response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER),
                    response.getBody().readAllBytes()));
        } catch (ResourceAccessException e) {
            // RestClient는 입출력 실패를 모두 ResourceAccessException으로 감싼다. 원인을 보고 둘로 나눈다
            if (isReadTimeout(e)) {
                throw new AnalyticsTimeoutException(e);
            }
            throw new AnalyticsUnreachableException(e);
        }
    }

    /**
     * 연결은 됐는데 답이 늦은 것만 true.
     * JDK HttpClient에서 연결 타임아웃({@link HttpConnectTimeoutException})은 읽기 타임아웃({@link HttpTimeoutException})의
     * 하위 클래스라서 먼저 걸러야 한다 — 연결 타임아웃은 "닿지 않음"이다.
     */
    static boolean isReadTimeout(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof HttpConnectTimeoutException) {
                return false;
            }
            if (c instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
