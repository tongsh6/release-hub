package io.releasehub.infrastructure.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitTokenAttributeConverterTest {

    private GitTokenCrypto crypto;
    private GitTokenAttributeConverter converter;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (i + 1);
        }
        crypto = new GitTokenCrypto(Base64.getEncoder().encodeToString(key));
        converter = new GitTokenAttributeConverter(Optional.of(crypto));
    }

    @Test
    void should_encrypt_long_plaintext_token_instead_of_treating_it_as_ciphertext() {
        String longPlaintextToken = "glpat-" + "a".repeat(96);

        String dbValue = converter.convertToDatabaseColumn(longPlaintextToken);

        assertNotEquals(longPlaintextToken, dbValue);
        assertTrue(dbValue.startsWith("enc:v1:"));
        assertEquals(longPlaintextToken, converter.convertToEntityAttribute(dbValue));
    }

    @Test
    void should_read_legacy_ciphertext_without_reencrypting_it() {
        String token = "glpat-legacy-token";
        String legacyCiphertext = crypto.encrypt(token);

        assertEquals(token, converter.convertToEntityAttribute(legacyCiphertext));
        assertEquals(legacyCiphertext, converter.convertToDatabaseColumn(legacyCiphertext));
    }

    @Test
    void should_encrypt_plaintext_even_when_it_starts_with_ciphertext_prefix() {
        String prefixedPlaintext = "enc:v1:not-a-real-ciphertext-token";

        String dbValue = converter.convertToDatabaseColumn(prefixedPlaintext);

        assertNotEquals(prefixedPlaintext, dbValue);
        assertTrue(dbValue.startsWith("enc:v1:"));
        assertEquals(prefixedPlaintext, converter.convertToEntityAttribute(dbValue));
    }

    @Test
    void should_pass_through_when_crypto_is_disabled() {
        GitTokenAttributeConverter disabledConverter = new GitTokenAttributeConverter(Optional.empty());

        assertEquals("glpat-plain", disabledConverter.convertToDatabaseColumn("glpat-plain"));
        assertEquals("glpat-plain", disabledConverter.convertToEntityAttribute("glpat-plain"));
    }
}
