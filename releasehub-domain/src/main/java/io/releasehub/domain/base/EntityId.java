package io.releasehub.domain.base;

/**
 * 实体标识符接口
 * <p>
 * 所有强类型 ID 都应实现此接口，提供统一的 ID 操作能力。
 */
public interface EntityId {

    /**
     * 获取 ID 的字符串值
     *
     * @return ID 字符串
     */
    String value();

    /**
     * 判断 ID 是否为空
     *
     * @return 如果 value 为 null 或空白则返回 true
     */
    default boolean isEmpty() {
        String v = value();
        return v == null || v.isBlank();
    }

    /**
     * 判断 ID 是否非空
     *
     * @return 如果 value 非 null 且非空白则返回 true
     */
    default boolean isPresent() {
        return !isEmpty();
    }
}
