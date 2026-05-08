package io.releasehub.infrastructure.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 加密基础设施配置。
 * <p>
 * 密钥通过 ${releasehub.crypto.secret-key} 注入，各 Profile 可在 application-*.yml 中独立配置。
 * 密钥格式：Base64 编码的 32 字节（AES-256）。
 * <p>
 * 生成新密钥示例：openssl rand -base64 32
 */
@Configuration
public class TokenCryptoConfig {

    @Bean
    public GitTokenCrypto gitTokenCrypto(
            @Value("${releasehub.crypto.secret-key}") String base64Key) {
        return new GitTokenCrypto(base64Key);
    }
}
