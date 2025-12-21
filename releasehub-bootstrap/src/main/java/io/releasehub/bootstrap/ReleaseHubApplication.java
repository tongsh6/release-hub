package io.releasehub.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.releasehub")
public class ReleaseHubApplication {

    public static void main(String[] args) {
        System.setProperty("springdoc.swagger-ui.tagsSorter", "alpha");
        System.setProperty("springdoc.override-with-generic-response", "false");
        System.setProperty("springdoc.api-docs.groups.enabled", "true");
        SpringApplication.run(ReleaseHubApplication.class, args);
    }
}
