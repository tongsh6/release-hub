package io.releasehub.bootstrap.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI releaseHubOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ReleaseHub API")
                        .version("v1.0.0")
                        .description("ReleaseHub API Documentation"));
    }

    /**
     * HTTP 的仓库级执行项与可选收尾值允许 null；API 描述留在边界层，不给领域模型引入 Swagger。
     */
    @Bean
    public OpenApiCustomizer nullableRunFields() {
        var fieldsBySchema = Map.of(
                "RunItemView", List.of("iterationKey"),
                "ItemView", List.of("iterationKey"),
                "ItemReport", List.of("iterationKey"),
                "Item", List.of("iterationKey", "featureBranch", "featureBranchMode",
                        "baseVersion", "devVersion", "targetVersion", "tagName", "ciRef"),
                "RunView", List.of("finishedAt"),
                "TaskView", List.of("startedAt", "finishedAt", "errorMessage"));
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) return;
            var schemas = openApi.getComponents().getSchemas();
            fieldsBySchema.forEach((name, fields) -> {
                Schema<?> schema = schemas.get(name);
                if (schema == null || schema.getProperties() == null) return;
                fields.forEach(field -> {
                    var property = schema.getProperties().get(field);
                    if (property != null) {
                        property.setTypes(new LinkedHashSet<>(List.of("string", "null")));
                    }
                });
            });
        };
    }

}
