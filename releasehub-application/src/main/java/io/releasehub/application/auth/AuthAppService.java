package io.releasehub.application.auth;

import io.releasehub.application.user.UserPort;
import io.releasehub.common.exception.AuthenticationException;
import io.releasehub.common.exception.ForbiddenException;
import io.releasehub.domain.user.User;
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
        return login(username, password, false);
    }

    @Transactional(readOnly = true)
    public TokenInfo login(String username, String password, boolean rememberMe) {
        // 使用通用错误信息防止枚举攻击
        User user = userPort.findByUsername(username)
                            .orElseThrow(AuthenticationException::failed);

        if (!passwordService.matches(password, user.getPasswordHash())) {
            throw AuthenticationException.failed();
        }

        if (!user.isEnabled()) {
            throw ForbiddenException.userDisabled();
        }

        return tokenProvider.createToken(user, rememberMe);
    }
}
