package io.releasehub.interfaces.rest;

import io.releasehub.common.exception.BizException;
import io.releasehub.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author tongshuanglong
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> handleBizException(BizException e) {
        log.warn("BizException: code={}, message={}", e.getCode(), e.getMessage());
        if ("AUTH_FAILED".equals(e.getCode())) {
             return org.springframework.http.ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                     .body(ApiResponse.error(e.getCode(), e.getMessage()));
        }
        return org.springframework.http.ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unhandled Exception", e);
        return ApiResponse.error("INTERNAL_ERROR", "Internal Server Error");
    }
}
