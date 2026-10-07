package io.releasehub.infrastructure.seed;

import io.releasehub.application.auth.InitialAdminAppService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 从部署配置读取首次凭据，不提供公开注册入口，不记录凭据内容。 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(name = "releasehub.bootstrap-admin.enabled", havingValue = "true")
public class InitialAdminInitializer implements ApplicationRunner {
    private final InitialAdminAppService service;
    private final String username;
    private final String password;
    private final boolean seedEnabled;

    public InitialAdminInitializer(InitialAdminAppService service,
            @Value("${releasehub.bootstrap-admin.username:admin}") String username,
            @Value("${releasehub.bootstrap-admin.password:}") String password,
            @Value("${releasehub.seed.enabled:false}") boolean seedEnabled) {
        this.service = service;
        this.username = username;
        this.password = password;
        this.seedEnabled = seedEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seedEnabled) {
            throw new IllegalStateException("首次管理员初始化不能与开发 seed 同时启用，请关闭 releasehub.seed.enabled");
        }
        if (service.initialize(username, password)) {
            log.info("首次管理员账户已创建；请移除部署配置中的初始密码并重建后端容器");
        } else {
            log.info("已有用户，跳过首次管理员初始化，账户和密码保持不变");
        }
    }
}
