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
 * 兼容旧数据：读取时同时支持历史无前缀密文；解密失败时原样返回，支持渐进式迁移。
 */
@Component
@Converter(autoApply = false)
public class GitTokenAttributeConverter implements AttributeConverter<String, String> {

    private static final String CIPHERTEXT_PREFIX = "enc:v1:";

    private final GitTokenCrypto crypto;

    public GitTokenAttributeConverter(Optional<GitTokenCrypto> crypto) {
        this.crypto = crypto.orElse(null);
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null || plaintext.isEmpty() || crypto == null) {
            return plaintext;
        }
        if (isEncryptedValue(plaintext)) {
            return plaintext;
        }
        return CIPHERTEXT_PREFIX + crypto.encrypt(plaintext);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty() || crypto == null) {
            return dbData;
        }
        String ciphertext = dbData;
        if (dbData.startsWith(CIPHERTEXT_PREFIX)) {
            ciphertext = dbData.substring(CIPHERTEXT_PREFIX.length());
        }
        try {
            return crypto.decrypt(ciphertext);
        } catch (GitTokenCrypto.CryptoException e) {
            return dbData;
        }
    }

    private boolean isEncryptedValue(String value) {
        if (value.startsWith(CIPHERTEXT_PREFIX)) {
            return canDecrypt(value.substring(CIPHERTEXT_PREFIX.length()));
        }
        return canDecrypt(value);
    }

    private boolean canDecrypt(String value) {
        try {
            crypto.decrypt(value);
            return true;
        } catch (GitTokenCrypto.CryptoException e) {
            return false;
        }
    }
}
