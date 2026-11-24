package com.finesse.composite;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 화면이 부르는 통계 경로 {@code GET /api/v1/stats/{username}} — 모놀리식과 같은 경로 (설계서 7장 통계).
 * <p>
 * 계산은 tetris-analytics가 하고 여기서는 <b>그대로 넘기기만</b> 한다: 받은 경로·쿼리를 그대로 붙여 보내고,
 * 돌아온 상태 코드·본문 바이트·Content-Type·Retry-After를 그대로 화면에 준다.
 * 그래서 400 INVALID_USERNAME, 404 USER_NOT_FOUND, 502 TETRIO_API_UNAVAILABLE, 503 SERVER_BUSY까지 모놀리식과 같다.
 * <p>
 * analytics에 닿지 않거나 답이 늦거나 회로가 열려 있을 때만 여기서 502를 만든다 ({@link ApiErrorHandler}).
 */
@RestController
public class StatsRelayController {

    private static final Logger log = LoggerFactory.getLogger(StatsRelayController.class);

    /** 닿지 않음·늦음일 때 화면에 줄 Retry-After(초) */
    static final int UNREACHABLE_RETRY_AFTER_SECONDS = 5;

    private final AnalyticsHttp analytics;
    /** 회로가 열려 있을 때 화면에 줄 Retry-After(초) = 회로가 열려 있는 시간 (설정 하나만 고치면 같이 바뀜) */
    private final int circuitOpenSeconds;

    public StatsRelayController(AnalyticsHttp analytics,
                                @Value("${resilience4j.circuitbreaker.instances.analytics.wait-duration-in-open-state}")
                                Duration circuitOpen) {
        this.analytics = analytics;
        this.circuitOpenSeconds = (int) Math.max(1, circuitOpen.toSeconds());
    }

    @GetMapping("/api/v1/stats/{username}")
    public ResponseEntity<byte[]> stats(HttpServletRequest request) {
        // getRequestURI()·getQueryString()은 디코딩하지 않은 원래 문자열 — @PathVariable로 받으면 디코딩된 값이라 다시 인코딩해야 함
        String query = request.getQueryString();
        String raw = request.getRequestURI() + (query == null ? "" : "?" + query);

        AnalyticsHttp.Relayed r;
        try {
            r = analytics.get(raw);
        } catch (AnalyticsUnreachableException | AnalyticsTimeoutException e) {
            log.warn("통계를 넘기지 못함: {}", e.getMessage());
            throw new StatsUnavailableException(e.getMessage(), UNREACHABLE_RETRY_AFTER_SECONDS, e);
        } catch (CallNotPermittedException e) {
            log.warn("tetris-analytics 회로가 열려 있어 부르지 않음");
            throw new StatsUnavailableException("tetris-analytics 회로 열림", circuitOpenSeconds, e);
        }

        ResponseEntity.BodyBuilder out = ResponseEntity.status(r.status());
        if (r.contentType() != null) {
            out.contentType(r.contentType());
        }
        if (r.retryAfter() != null) {
            out.header(HttpHeaders.RETRY_AFTER, r.retryAfter());
        }
        return out.body(r.body());
    }
}
