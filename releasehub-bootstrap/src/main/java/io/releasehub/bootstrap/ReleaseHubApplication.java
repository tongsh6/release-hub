package io.releasehub.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "io.releasehub")
@EnableJpaRepositories(basePackages = "io.releasehub.persistence")
@EntityScan(basePackages = "io.releasehub.persistence")
public class ReleaseHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReleaseHubApplication.class, args);
    }
}
