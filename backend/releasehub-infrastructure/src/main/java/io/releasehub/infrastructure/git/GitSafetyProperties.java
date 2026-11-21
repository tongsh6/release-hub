package io.releasehub.infrastructure.git;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 默认保护远端写入；关闭时只允许读取，不得绕过 journal 和保留。 */
@Component @ConfigurationProperties(prefix="releasehub.git-safety")
public class GitSafetyProperties {
    private boolean enabled = true;
    public boolean isEnabled(){return enabled;}
    public void setEnabled(boolean enabled){this.enabled=enabled;}
}
