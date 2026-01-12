package io.releasehub.interfaces.rest;

import io.releasehub.common.exception.BaseException;
import io.releasehub.common.exception.BizException;
import io.releasehub.common.exception.ErrorCode;
import io.releasehub.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.NoSuchElementException;

/**
 * 全局异常处理器
 * <p>
 * 统一处理所有异常，返回标准 API 响应格式，支持 i18n
 *
 * @author tongshuanglong
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final ExceptionMessageResolver messageResolver;

    /**
     * 处理所有自定义业务异常（新架构）
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseException(BaseException ex) {
        String message = messageResolver.resolve(ex);

        log.warn("Business exception: code={}, message={}", ex.getCode(), message);

        return ResponseEntity
                .status(HttpStatus.valueOf(ex.getHttpStatus()))
                .body(ApiResponse.error(ex.getCode(), message));
    }

    /**
     * 兼容旧的 BizException（过渡期保留）
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException ex) {
        log.warn("Legacy BizException: code={}, message={}", ex.getCode(), ex.getMessage());

        HttpStatus status = determineHttpStatus(ex.getCode());
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    /**
     * 处理 NoSuchElementException（来自 Optional.orElseThrow()）
     */
    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoSuchElement(NoSuchElementException ex) {
        String message = messageResolver.resolve(ErrorCode.RESOURCE_NOT_FOUND);
        log.warn("Resource not found: {}", ex.getMessage());
        return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND.getCode(), message);
    }

    /**
     * 处理资源不存在异常（Spring MVC）
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("No resource found: {}", ex.getMessage());
        return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND.getCode(), ex.getMessage());
    }

    /**
     * 处理参数校验异常（@Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        
        log.warn("Validation error: {}", message);
        return ApiResponse.error(ErrorCode.INVALID_PARAMETER.getCode(), message);
    }

    /**
     * 处理参数异常
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(RuntimeException ex) {
        log.warn("Illegal argument/state: {}", ex.getMessage());
        return ApiResponse.error(ErrorCode.INVALID_PARAMETER.getCode(), ex.getMessage());
    }

    /**
     * 处理所有未捕获异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        String message = messageResolver.resolve(ErrorCode.INTERNAL_ERROR);
        return ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(), message);
    }

    /**
     * 根据旧错误码确定 HTTP 状态码（兼容期）
     */
    private HttpStatus determineHttpStatus(String code) {
        if (code == null) return HttpStatus.INTERNAL_SERVER_ERROR;
        if (code.contains("NOT_FOUND")) return HttpStatus.NOT_FOUND;
        if (code.contains("AUTH") || code.equals("AUTH_FAILED")) return HttpStatus.UNAUTHORIZED;
        if (code.contains("FORBIDDEN") || code.contains("DISABLED")) return HttpStatus.FORBIDDEN;
        if (code.contains("EXISTS") || code.contains("CONFLICT")) return HttpStatus.CONFLICT;
        return HttpStatus.BAD_REQUEST;
    }
}
