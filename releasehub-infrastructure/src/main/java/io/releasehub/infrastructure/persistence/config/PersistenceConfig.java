package io.releasehub.infrastructure.persistence.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * @author tongshuanglong
 */
@Configuration
@EnableJpaRepositories(basePackages = "io.releasehub.infrastructure.persistence")
@EntityScan(basePackages = "io.releasehub.infrastructure.persistence")
public class PersistenceConfig {
}
