package io.releasehub.common.exception;

import lombok.Getter;

public class BizException extends RuntimeException {
    @Getter
    private final String code;
    private final String message;

    public BizException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
