package io.releasehub.infrastructure.crypto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 启动时只读校验有标识密文，避免错钥或恢复错误被延迟成 GitLab 认证失败。 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "releasehub.crypto.verify-stored-tokens", havingValue = "true")
public class TokenCryptoStartupVerifier implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final GitTokenCrypto crypto;

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        int[] counts = new int[2];
        try {
            jdbc.query("""
                    SELECT git_token AS token FROM code_repository WHERE git_token IS NOT NULL AND git_token <> ''
                    UNION ALL
                    SELECT gitlab_token AS token FROM system_settings WHERE gitlab_token IS NOT NULL AND gitlab_token <> ''
                    """, statement -> statement.setFetchSize(100), (RowCallbackHandler) result -> {
                String value = result.getString("token");
                if (value.startsWith(GitTokenAttributeConverter.CIPHERTEXT_PREFIX)) {
                    crypto.decrypt(value.substring(GitTokenAttributeConverter.CIPHERTEXT_PREFIX.length()));
                    counts[0]++;
                } else {
                    counts[1]++;
                }
            });
        } catch (GitTokenCrypto.CryptoException e) {
            throw new IllegalStateException("Git Token 校验失败：请检查 RELEASEHUB_CRYPTO_SECRET_KEY 原密钥及数据库备份，禁止直接替换密钥", e);
        }
        log.info("Git Token 启动校验通过，已校验 {} 条有标识密文", counts[0]);
        if (counts[1] > 0) {
            log.warn("检测到 {} 条无格式标识凭据，未自动迁移；请按存量复核流程确认加密状态", counts[1]);
        }
    }
}
