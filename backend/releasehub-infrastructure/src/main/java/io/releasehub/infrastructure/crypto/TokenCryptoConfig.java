package io.releasehub.infrastructure.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 加密基础设施配置。
 * <p>
 * 仅在 {@code releasehub.crypto.enabled=true} 时激活。
 * 激活后必须提供 Base64 编码的 32 字节 AES-256 密钥，并在 Bean 初始化时执行 round-trip 自检。
 * <p>
 * 生成新密钥示例：openssl rand -base64 32
 */
@Configuration
@ConditionalOnProperty(name = "releasehub.crypto.enabled", havingValue = "true")
public class TokenCryptoConfig {

    @Bean
    public GitTokenCrypto gitTokenCrypto(
            @Value("${releasehub.crypto.secret-key}") String base64Key) {
        GitTokenCrypto crypto = new GitTokenCrypto(base64Key);
        crypto.verify();
        return crypto;
    }
}
