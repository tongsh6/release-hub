package io.releasehub.interfaces.api.version;

import io.releasehub.domain.version.VersionPolicy;
import lombok.Data;

import java.time.Instant;

/**
 * 版本策略 View 对象
 */
@Data
public class VersionPolicyView {
    private String id;
    private String name;
    private String scheme;
    private String bumpRule;
    private Instant createdAt;
    private Instant updatedAt;

    public static VersionPolicyView fromDomain(VersionPolicy policy) {
        VersionPolicyView view = new VersionPolicyView();
        view.setId(policy.getId().value());
        view.setName(policy.getName());
        view.setScheme(policy.getScheme().name());
        view.setBumpRule(policy.getBumpRule().name());
        view.setCreatedAt(policy.getCreatedAt());
        view.setUpdatedAt(policy.getUpdatedAt());
        return view;
    }
}
