package com.finesse.composite;

/** 화면 요청이 틀림 — 400 BAD_REQUEST (모놀리식 GlobalExceptionHandler와 같은 코드, 메시지는 그대로 화면에) */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
