package com.finesse.composite;

import com.finesse.composite.dto.ApiErrorResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * finesse-api가 직접 만드는 에러 응답. 코드·문장은 모놀리식 GlobalExceptionHandler와 같게 해서 화면이 그대로 쓴다.
 * <p>
 * 통계: analytics가 답한 에러는 {@link StatsRelayController}가 그대로 넘기므로 여기를 거치지 않는다.
 * analytics에 닿지 않거나 답이 늦거나 회로가 열렸을 때만 502 TETRIO_API_UNAVAILABLE + Retry-After.
 * (엔진 에러 변환은 조각 8에서 더한다)
 */
@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(StatsUnavailableException.class)
    ResponseEntity<ApiErrorResponse> statsUnavailable(StatsUnavailableException e) {
        return ResponseEntity.status(502)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                .body(new ApiErrorResponse("TETRIO_API_UNAVAILABLE", "일시적으로 조회할 수 없습니다, 잠시 후 다시 시도"));
    }
}
