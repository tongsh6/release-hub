package io.releasehub.application.settings;

import java.util.Optional;

public interface SettingsPort {
    void saveGitLab(SettingsGitLab gitlab);
    Optional<SettingsGitLab> getGitLab();

    void saveNaming(SettingsNaming naming);
    Optional<SettingsNaming> getNaming();

    void saveRef(SettingsRef ref);
    Optional<SettingsRef> getRef();

    void saveBlocking(SettingsBlocking blocking);
    Optional<SettingsBlocking> getBlocking();

    record SettingsGitLab(String baseUrl, String tokenMasked) {}
    record SettingsNaming(String featureTemplate, String releaseTemplate) {}
    record SettingsRef() {}
    record SettingsBlocking(String defaultPolicy) {}
}
