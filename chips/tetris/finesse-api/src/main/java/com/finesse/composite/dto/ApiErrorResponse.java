package com.finesse.composite.dto;

/** 모놀리식 공통 에러 응답과 같은 모양 — {"error_code", "message"} (SNAKE_CASE 설정으로 errorCode → error_code) */
public record ApiErrorResponse(String errorCode, String message) {
}
