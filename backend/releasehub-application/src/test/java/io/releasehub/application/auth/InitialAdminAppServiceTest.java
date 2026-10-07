package io.releasehub.application.auth;

import io.releasehub.application.user.UserPort;
import io.releasehub.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InitialAdminAppServiceTest {
    @Mock UserPort users;
    @Mock PasswordPort passwords;
    private InitialAdminAppService service;

    @BeforeEach
    void setUp() {
        service = new InitialAdminAppService(users, passwords);
    }

    @Test
    void createsFirstUserWithEncodedPassword() {
        when(passwords.encode("A-long-test-password!")).thenReturn("encoded-password");
        assertThat(service.initialize("release-owner", "A-long-test-password!")).isTrue();
        var captured = ArgumentCaptor.forClass(User.class);
        verify(users).save(captured.capture());
        assertThat(captured.getValue().getUsername()).isEqualTo("release-owner");
        assertThat(captured.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(captured.getValue().isEnabled()).isTrue();
    }

    @Test
    void preservesAnyExistingUserEvenWithoutBootstrapCredentials() {
        when(users.existsAny()).thenReturn(true);
        assertThat(service.initialize(null, null)).isFalse();
        verify(users, never()).save(any());
        verifyNoInteractions(passwords);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "            "})
    void rejectsMissingOrShortPasswordWithoutWriting(String password) {
        assertThatThrownBy(() -> service.initialize("admin", password))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELEASEHUB_BOOTSTRAP_ADMIN_PASSWORD");
        verify(users, never()).save(any());
        verifyNoInteractions(passwords);
    }

    @Test
    void rejectsPasswordExceedingBcryptByteLimitWithoutLeakingIt() {
        String password = "密".repeat(25);
        assertThatThrownBy(() -> service.initialize("admin", password))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(password);
        verify(users, never()).save(any());
        verifyNoInteractions(passwords);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"a", "invalid user", "用户", "invalid\nusername"})
    void rejectsInvalidUsernameWithoutWriting(String username) {
        assertThatThrownBy(() -> service.initialize(username, "A-long-test-password!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELEASEHUB_BOOTSTRAP_ADMIN_USERNAME");
        verify(users, never()).save(any());
        verifyNoInteractions(passwords);
    }
}
