package com.finesse.composite;

import com.commentengine.api.LightRequest;
import com.commentengine.api.LightResponse;
import com.commentengine.util.ErrorCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 엔진 계약 호출 — {@link EngineHttp}(다시 시도·회로 차단기)를 부르고, 끝내 닿지 않음·회로 열림·늦음을
 * 화면용 엔진 에러({@link EngineCallException})로 바꾼다. 조각 9에서 section 호출을 여기에 더한다.
 */
@Component
public class EngineClient {

    /** 엔진에 닿지 않을 때 화면에 줄 Retry-After(초) */
    static final int UNREACHABLE_RETRY_AFTER_SECONDS = 5;

    private final EngineHttp http;
    /** 회로가 열려 있을 때 화면에 줄 Retry-After(초) = 회로가 열려 있는 시간 */
    private final int circuitOpenSeconds;

    public EngineClient(EngineHttp http,
                        @Value("${resilience4j.circuitbreaker.instances.engine.wait-duration-in-open-state}")
                        Duration circuitOpen) {
        this.http = http;
        this.circuitOpenSeconds = (int) Math.max(1, circuitOpen.toSeconds());
    }

    /** POST /v1/comments/light. 마감은 지금 + request.deadlineMs, 다시 부를 때는 남은 시간으로 deadline_ms를 다시 만든다 */
    public LightResponse light(LightRequest request) {
        Instant deadline = Instant.now().plusMillis(request.deadlineMs());
        return guard(() -> http.post("/v1/comments/light", deadline,
                remaining -> new LightRequest(request.context(), request.items(), remaining),
                LightResponse.class));
    }

    private <T> T guard(Supplier<T> call) {
        try {
            return call.get();
        } catch (EngineUnreachableException e) {
            throw new EngineCallException(ErrorCode.LLM_UNAVAILABLE, e.getMessage(), UNREACHABLE_RETRY_AFTER_SECONDS);
        } catch (CallNotPermittedException e) {
            throw new EngineCallException(ErrorCode.LLM_UNAVAILABLE, "comment-engine 회로 열림", circuitOpenSeconds);
        } catch (EngineTimeoutException e) {
            throw new EngineCallException(ErrorCode.LLM_TIMEOUT, e.getMessage(), null);
        }
    }
}
