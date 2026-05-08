package io.releasehub.infrastructure.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GitTokenCrypto AES-GCM 加解密测试")
class GitTokenCryptoTest {

    private GitTokenCrypto crypto;

    @BeforeEach
    void setUp() {
        // Generate a valid AES-256 key (32 random bytes → Base64)
        byte[] keyBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(keyBytes);
        crypto = new GitTokenCrypto(Base64.getEncoder().encodeToString(keyBytes));
    }

    @Test
    @DisplayName("加密后解密应得到原始明文")
    void shouldEncryptAndDecrypt() {
        String token = "glpat-abcdefghijklmnopqrst";
        String encrypted = crypto.encrypt(token);
        assertNotNull(encrypted);
        assertNotEquals(token, encrypted);

        String decrypted = crypto.decrypt(encrypted);
        assertEquals(token, decrypted);
    }

    @Test
    @DisplayName("每次加密产生不同密文（随机IV）")
    void shouldProduceDifferentCiphertexts() {
        String token = "glpat-test-token";
        String enc1 = crypto.encrypt(token);
        String enc2 = crypto.encrypt(token);
        assertNotEquals(enc1, enc2, "Same plaintext should produce different ciphertexts due to random IV");
        // Both should decrypt to the original
        assertEquals(token, crypto.decrypt(enc1));
        assertEquals(token, crypto.decrypt(enc2));
    }

    @Test
    @DisplayName("null 和空字符串应原样返回")
    void shouldHandleNullAndEmpty() {
        assertNull(crypto.encrypt(null));
        assertEquals("", crypto.encrypt(""));
        assertNull(crypto.decrypt(null));
        assertEquals("", crypto.decrypt(""));
    }

    @Test
    @DisplayName("篡改密文应导致解密失败")
    void shouldDetectTampering() {
        String token = "glpat-sensitive-token-value";
        String encrypted = crypto.encrypt(token);
        // flip a byte in the ciphertext
        byte[] tampered = Base64.getDecoder().decode(encrypted);
        tampered[tampered.length - 5] ^= 0x01; // flip bit
        String tamperedB64 = Base64.getEncoder().encodeToString(tampered);

        assertThrows(GitTokenCrypto.CryptoException.class, () -> crypto.decrypt(tamperedB64));
    }

    @Test
    @DisplayName("无效的 Base64 输入应抛出异常")
    void shouldRejectInvalidBase64() {
        assertThrows(GitTokenCrypto.CryptoException.class, () -> crypto.decrypt("not-valid-base64!!!"));
    }

    @Test
    @DisplayName("StandardCharsets.UTF_8 编码的 token 应正确往返")
    void shouldHandleUtf8() {
        String token = "glpat-测试-トークン-émoji-🚀";
        String encrypted = crypto.encrypt(token);
        assertEquals(token, crypto.decrypt(encrypted));
    }
}
