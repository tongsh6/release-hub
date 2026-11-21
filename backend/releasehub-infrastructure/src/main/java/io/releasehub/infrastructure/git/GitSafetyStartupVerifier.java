package io.releasehub.infrastructure.git;

import io.releasehub.application.gitoperation.GitOperationJournalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class GitSafetyStartupVerifier implements SmartInitializingSingleton {
    private final GitSafetyProperties properties;
    private final GitOperationJournalPort journal;
    // 在 WebServer 生命周期启动前拒绝错误配置，不能等端口开放后的 ApplicationRunner。
    @Override public void afterSingletonsInstantiated() {
        if(!properties.isEnabled()&&journal.hasActiveGuards()) {
            throw new IllegalStateException("存在未结束的仓库保留，禁止以未启用写入保护的模式启动");
        }
    }
}
