package com.finesse.composite;

import com.finesse.composite.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 화면이 부르는 코멘트 경로 {@code GET /api/v1/comment/{username}?scope=light|heavy} — 모놀리식 CommentController와 같은 동작.
 * scope가 없으면 400 "필수 파라미터가 없습니다: scope"({@link ApiErrorHandler}), light·heavy가 아니면 400.
 * heavy는 조각 9 전까지 501 NOT_IMPLEMENTED (지침서 부록 A-7).
 */
@RestController
public class CommentController {

    private static final String PREFIX = "/api/v1/comment/";

    private final LightCommentService light;

    public CommentController(LightCommentService light) {
        this.light = light;
    }

    @GetMapping(PREFIX + "{username}")
    public ResponseEntity<?> comment(@RequestParam String scope, HttpServletRequest request) {
        return switch (scope) {
            case "light" -> light.light(rawUsername(request));
            case "heavy" -> ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(new ApiErrorResponse("NOT_IMPLEMENTED", "heavy는 아직 준비 중입니다"));
            default -> throw new BadRequestException("scope는 light 또는 heavy만 허용됩니다: " + scope);
        };
    }

    /** 경로의 유저명을 받은 그대로(인코딩된 채로) — analytics에 다시 인코딩하지 않고 넘기기 위해 (조각 4와 같음) */
    private static String rawUsername(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.substring(uri.indexOf(PREFIX) + PREFIX.length());
    }
}
