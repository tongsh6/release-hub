package io.releasehub.common.exception;

import lombok.Getter;

/**
 * 异常基类
 * <p>
 * 所有业务异常的基类，支持 i18n 消息参数
 */
@Getter
public class BaseException extends RuntimeException {

    /**
     * 错误码枚举
     */
    private final ErrorCode errorCode;

    private final Object[] args;

    public BaseException(ErrorCode errorCode, Object... args) {
        super(errorCode.getMessageKey());
        this.errorCode = errorCode;
        this.args = args;
    }

    public BaseException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.getMessageKey(), cause);
        this.errorCode = errorCode;
        this.args = args;
    }

    /**
     * 获取错误码字符串
     */
    public String getCode() {
        return errorCode.getCode();
    }

    /**
     * 获取 i18n 消息 key
     */
    public String getMessageKey() {
        return errorCode.getMessageKey();
    }

    /**
     * 获取 HTTP 状态码
     */
    public int getHttpStatus() {
        return errorCode.getHttpStatus();
    }

    /** 防御性复制，避免暴露内部数组引用 */
    public Object[] getArgs() {
        return args != null ? args.clone() : null;
    }
}
