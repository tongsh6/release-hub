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
 * 有格式标识的密文必须成功解密；无标识旧数据保留兼容读取，不自动迁移。
 */
@Component
@Converter(autoApply = false)
public class GitTokenAttributeConverter implements AttributeConverter<String, String> {

    static final String CIPHERTEXT_PREFIX = "enc:v1:";

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
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        if (dbData.startsWith(CIPHERTEXT_PREFIX)) {
            if (crypto == null) {
                throw new GitTokenCrypto.CryptoException("已加密的 Git Token 需要启用加密并配置原密钥");
            }
            try {
                return crypto.decrypt(dbData.substring(CIPHERTEXT_PREFIX.length()));
            } catch (GitTokenCrypto.CryptoException e) {
                throw new GitTokenCrypto.CryptoException("Git Token 解密失败，请检查原密钥和数据库备份", e);
            }
        }
        if (crypto == null) {
            return dbData;
        }
        // 无标识明文和历史密文无法仅凭格式可靠区分，保持既有兼容边界。
        try {
            return crypto.decrypt(dbData);
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
