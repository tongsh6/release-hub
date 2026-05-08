package io.releasehub.infrastructure.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JPA AttributeConverter — 透明的 gitToken 加解密。
 * <p>
 * 当 {@code releasehub.crypto.enabled=true} 且有有效的 {@link GitTokenCrypto} Bean 时，
 * 数据库存储密文（Base64），Java 对象持有明文。
 * 当加密未启用时，退化为透传——数据库存储明文。
 * <p>
 * 兼容旧数据：解密失败时原样返回，支持渐进式迁移。
 */
@Component
@Converter(autoApply = false)
public class GitTokenAttributeConverter implements AttributeConverter<String, String> {

    private final GitTokenCrypto crypto;

    public GitTokenAttributeConverter(Optional<GitTokenCrypto> crypto) {
        this.crypto = crypto.orElse(null);
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null || plaintext.isEmpty() || crypto == null) {
            return plaintext;
        }
        if (looksEncrypted(plaintext)) {
            return plaintext;
        }
        return crypto.encrypt(plaintext);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty() || crypto == null) {
            return dbData;
        }
        try {
            return crypto.decrypt(dbData);
        } catch (GitTokenCrypto.CryptoException e) {
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
