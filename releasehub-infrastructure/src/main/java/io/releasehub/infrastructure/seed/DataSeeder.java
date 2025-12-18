package io.releasehub.infrastructure.seed;

import io.releasehub.application.auth.PasswordPort;
import io.releasehub.domain.user.User;
import io.releasehub.application.user.UserPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.UUID;

/**
 * 数据库种子数据初始化
 * 包含默认的管理员账号
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "releasehub.seed.enabled", havingValue = "true")
public class DataSeeder {

    private final UserPort userPort;
    private final PasswordPort passwordService;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            // 检查是否存在 admin 用户，不存在则创建
            if (userPort.findByUsername("admin").isEmpty()) {
                String rawPassword = "admin";
                // 密码必须经过 BCrypt 加密
                String encodedPassword = passwordService.encode(rawPassword);
                
                User admin = new User(
                    UUID.randomUUID().toString(),
                    "admin",
                    encodedPassword,
                    "Admin User",
                    true
                );
                userPort.save(admin);
                log.info("Initialized admin user with password 'admin'");
            }
        };
    }
}
