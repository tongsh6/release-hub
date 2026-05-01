package io.releasehub.interfaces.rest;

import io.releasehub.common.exception.BaseException;
import io.releasehub.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 异常消息解析器
 * <p>
 * 根据当前请求的 Locale 解析 i18n 消息
 */
@Component
@RequiredArgsConstructor
public class ExceptionMessageResolver {

    private final MessageSource messageSource;

    /**
     * 解析异常消息（使用当前请求的 Locale）
     *
     * @param ex 异常
     * @return 解析后的消息
     */
    public String resolve(BaseException ex) {
        return resolve(ex, LocaleContextHolder.getLocale());
    }

    /**
     * 解析异常消息（指定 Locale）
     *
     * @param ex     异常
     * @param locale 语言环境
     * @return 解析后的消息
     */
    public String resolve(BaseException ex, Locale locale) {
        String key = ex.getMessageKey();
        Object[] args = ex.getArgs();

        try {
            return messageSource.getMessage(key, args, locale);
        } catch (Exception e) {
            // Fallback: 返回 key + args
            return formatFallback(key, args);
        }
    }

    /**
     * 通过 ErrorCode 解析消息（使用当前 Locale）
     *
     * @param code 错误码
     * @param args 消息参数
     * @return 解析后的消息
     */
    public String resolve(ErrorCode code, Object... args) {
        return resolve(code, LocaleContextHolder.getLocale(), args);
    }

    /**
     * 通过 ErrorCode 解析消息（指定 Locale）
     *
     * @param code   错误码
     * @param locale 语言环境
     * @param args   消息参数
     * @return 解析后的消息
     */
    public String resolve(ErrorCode code, Locale locale, Object... args) {
        try {
            return messageSource.getMessage(code.getMessageKey(), args, locale);
        } catch (Exception e) {
            return formatFallback(code.getMessageKey(), args);
        }
    }

    /**
     * 格式化 fallback 消息
     */
    private String formatFallback(String key, Object[] args) {
        if (args == null || args.length == 0) {
            return key;
        }
        StringBuilder sb = new StringBuilder(key).append(": ");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
