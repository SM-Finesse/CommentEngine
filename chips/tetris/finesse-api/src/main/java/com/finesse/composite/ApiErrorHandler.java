package com.finesse.composite;

import com.finesse.composite.dto.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * finesse-api가 직접 만드는 에러 응답. 코드·문장은 모놀리식 GlobalExceptionHandler와 같게 해서 화면이 그대로 쓴다.
 * <p>
 * 통계: analytics가 답한 에러는 그대로 넘기므로 여기를 거치지 않는다. analytics에 닿지 않거나 늦거나 회로가 열렸을 때만 502.
 * <p>
 * 엔진 (지침서 3.4):
 * <pre>
 * LLM_UNAVAILABLE (엔진 503·닿지 않음·회로 열림) → 503 LLM_UNAVAILABLE + Retry-After
 * LLM_FORMAT      (엔진 502)                    → 502 LLM_FORMAT_ERROR
 * LLM_TIMEOUT     (엔진 504·읽기 45초·시간 부족)  → 504 LLM_TIMEOUT
 * INVALID_PAYLOAD (엔진 400 — 우리 요청이 틀림)   → 500 INTERNAL_ERROR + log.error
 * </pre>
 */
@RestControllerAdvice
public class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    /** 엔진이 Retry-After를 주지 않았을 때 */
    static final int DEFAULT_ENGINE_RETRY_AFTER_SECONDS = 5;

    @ExceptionHandler(StatsUnavailableException.class)
    ResponseEntity<ApiErrorResponse> statsUnavailable(StatsUnavailableException e) {
        return ResponseEntity.status(502)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                .body(new ApiErrorResponse("TETRIO_API_UNAVAILABLE", "일시적으로 조회할 수 없습니다, 잠시 후 다시 시도"));
    }

    @ExceptionHandler(EngineCallException.class)
    ResponseEntity<ApiErrorResponse> engine(EngineCallException e) {
        return switch (e.code()) {
            case LLM_UNAVAILABLE -> ResponseEntity.status(503)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(
                            e.retryAfterSeconds() == null ? DEFAULT_ENGINE_RETRY_AFTER_SECONDS : e.retryAfterSeconds()))
                    .body(new ApiErrorResponse("LLM_UNAVAILABLE", "일시적으로 코멘트를 생성할 수 없습니다, 잠시 후 다시 시도"));
            case LLM_FORMAT -> ResponseEntity.status(502)
                    .body(new ApiErrorResponse("LLM_FORMAT_ERROR", "AI 코멘트를 만들지 못했습니다, 잠시 후 다시 시도"));
            case LLM_TIMEOUT -> ResponseEntity.status(504)
                    .body(new ApiErrorResponse("LLM_TIMEOUT", "AI 코멘트 생성 시간이 초과되었습니다, 잠시 후 다시 시도"));
            case INVALID_PAYLOAD -> {
                log.error("엔진이 요청을 거절함 (INVALID_PAYLOAD): {}", e.getMessage());
                yield ResponseEntity.status(500)
                        .body(new ApiErrorResponse("INTERNAL_ERROR", "일시적인 오류가 발생했습니다, 잠시 후 다시 시도"));
            }
        };
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ApiErrorResponse> badRequest(BadRequestException e) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse("BAD_REQUEST", e.getMessage()));
    }

    /** 필수 파라미터 누락 (예: scope 없음) — 모놀리식과 같은 문장 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiErrorResponse> missingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("BAD_REQUEST", "필수 파라미터가 없습니다: " + e.getParameterName()));
    }
}
