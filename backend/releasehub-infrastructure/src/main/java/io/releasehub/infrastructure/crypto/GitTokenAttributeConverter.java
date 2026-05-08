package io.releasehub.infrastructure.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter — 透明的 gitToken 加解密。
 * <p>
 * 数据库存储密文（Base64），Java 对象持有明文。
 * 业务代码无需感知加解密过程。
 */
@Component
@Converter(autoApply = false) // 仅应用于明确标注的字段，避免全局影响
public class GitTokenAttributeConverter implements AttributeConverter<String, String> {

    private final GitTokenCrypto crypto;

    public GitTokenAttributeConverter(GitTokenCrypto crypto) {
        this.crypto = crypto;
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        // 已经是密文的（Base64 格式），直接透传不重复加密
        if (looksEncrypted(plaintext)) {
            return plaintext;
        }
        return crypto.encrypt(plaintext);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        try {
            return crypto.decrypt(dbData);
        } catch (GitTokenCrypto.CryptoException e) {
            // 解密失败 → 可能是旧明文数据，原样返回（允许渐进式迁移）
            return dbData;
        }
    }

    /**
     * heuristic: GCM 加密后的 Base64 密文长度远超原始 token
     * token 格式如 "glpat-xxxxxxxx" (20 chars) → 加密后 Base64 ~88 chars
     */
    private boolean looksEncrypted(String value) {
        return value.length() > 60;
    }
}
