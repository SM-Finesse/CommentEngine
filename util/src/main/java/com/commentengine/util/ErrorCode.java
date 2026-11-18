package com.commentengine.util;

public enum ErrorCode {
    INVALID_PAYLOAD(400),
    LLM_FORMAT(502),
    LLM_UNAVAILABLE(503),
    LLM_TIMEOUT(504);

    private final int status;

    ErrorCode(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }
}
