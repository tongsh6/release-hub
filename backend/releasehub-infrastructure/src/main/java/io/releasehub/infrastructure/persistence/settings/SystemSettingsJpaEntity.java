package io.releasehub.infrastructure.persistence.settings;

import io.releasehub.infrastructure.crypto.GitTokenAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "system_settings")
@Data
public class SystemSettingsJpaEntity {
    @Id
    private String id = "GLOBAL";

    @Column(name = "gitlab_base_url")
    private String gitlabBaseUrl;

    @Column(name = "gitlab_token", length = 800)
    @Convert(converter = GitTokenAttributeConverter.class)
    private String gitlabToken;

    @Column(name = "feature_template")
    private String featureTemplate;

    @Column(name = "release_template")
    private String releaseTemplate;

    @Column(name = "default_blocking_policy")
    private String defaultBlockingPolicy;
}
