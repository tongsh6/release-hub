package io.releasehub.application.auth;

import io.releasehub.domain.user.User;
import io.releasehub.application.user.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthAppService {

    private final UserPort userPort;
    private final PasswordPort passwordService;
    private final TokenPort tokenProvider;

    @Transactional(readOnly = true)
    public TokenInfo login(String username, String password) {
        // 使用通用错误信息防止枚举攻击
        User user = userPort.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordService.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        if (!user.isEnabled()) {
            throw new IllegalStateException("User is disabled");
        }

        return tokenProvider.createToken(user);
    }
}
