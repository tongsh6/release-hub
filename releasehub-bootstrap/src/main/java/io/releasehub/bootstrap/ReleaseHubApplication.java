package io.releasehub.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.releasehub")
public class ReleaseHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReleaseHubApplication.class, args);
    }
}
