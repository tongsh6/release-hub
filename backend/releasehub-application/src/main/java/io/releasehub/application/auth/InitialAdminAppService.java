package io.releasehub.application.auth;

import io.releasehub.application.user.UserPort;
import io.releasehub.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/** 只负责空用户表的首次账户创建，不提供密码重置能力。 */
@Service
@RequiredArgsConstructor
public class InitialAdminAppService {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{2,49}");
    private final UserPort userPort;
    private final PasswordPort passwordPort;

    @Transactional
    public boolean initialize(String username, String password) {
        userPort.lockForInitialization();
        if (userPort.existsAny()) {
            return false;
        }
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new IllegalStateException("请配置 RELEASEHUB_BOOTSTRAP_ADMIN_USERNAME："
                    + "3 至 50 位字母、数字、点、下划线或连字符，以字母或数字开头");
        }
        if (password == null || password.isBlank()
                || password.codePointCount(0, password.length()) < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("空用户表首次启动需要配置 RELEASEHUB_BOOTSTRAP_ADMIN_PASSWORD："
                    + "至少 12 个字符，UTF-8 编码不超过 72 字节");
        }
        userPort.save(new User(UUID.randomUUID().toString(), username,
                passwordPort.encode(password), "管理员", true));
        return true;
    }
}
