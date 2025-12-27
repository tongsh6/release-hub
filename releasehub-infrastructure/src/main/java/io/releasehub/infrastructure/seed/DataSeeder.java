package io.releasehub.infrastructure.seed;

import io.releasehub.application.auth.PasswordPort;
import io.releasehub.application.user.UserPort;
import io.releasehub.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 数据库种子数据初始化
 * 包含默认的管理员账号
 *
 * @author tongshuanglong
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "releasehub.seed.enabled", havingValue = "true")
public class DataSeeder {

    private final UserPort userPort;
    private final PasswordPort passwordService;
    private final JdbcTemplate jdbcTemplate;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            try {
                jdbcTemplate.execute("DELETE FROM code_repository");
                log.info("Cleared code_repository table to ensure schema compatibility");
            } catch (Exception e) {
                log.warn("Failed to clear code_repository: {}", e.getMessage());
            }

            // 检查是否存在 admin 用户，不存在则创建
            if (userPort.findByUsername("admin").isEmpty()) {
                String rawPassword = "admin";
                // 密码必须经过 BCrypt 加密
                String encodedPassword = passwordService.encode(rawPassword);

                User admin = new User(
                        "11111111-1111-1111-1111-111111111111", // Fixed ID for admin
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
