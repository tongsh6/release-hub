package io.releasehub.infrastructure.settings;

import io.releasehub.application.settings.SettingsPort;
import io.releasehub.infrastructure.persistence.settings.SystemSettingsJpaEntity;
import io.releasehub.infrastructure.persistence.settings.SystemSettingsJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SettingsAdapter implements SettingsPort {
    private final SystemSettingsJpaRepository repository;

    @Override
    @Transactional
    public void saveGitLab(SettingsGitLab v) {
        SystemSettingsJpaEntity entity = getOrCreate();
        entity.setGitlabBaseUrl(v != null ? v.baseUrl() : null);
        entity.setGitlabToken(v != null ? v.token() : null);
        repository.save(entity);
    }

    @Override
    public Optional<SettingsGitLab> getGitLab() {
        return repository.findById("GLOBAL")
                .filter(e -> hasText(e.getGitlabBaseUrl()) || hasText(e.getGitlabToken()))
                .map(e -> new SettingsGitLab(e.getGitlabBaseUrl(), e.getGitlabToken()));
    }

    @Override
    @Transactional
    public void saveNaming(SettingsNaming v) {
        SystemSettingsJpaEntity entity = getOrCreate();
        entity.setFeatureTemplate(v.featureTemplate());
        entity.setReleaseTemplate(v.releaseTemplate());
        repository.save(entity);
    }

    @Override
    public Optional<SettingsNaming> getNaming() {
        return repository.findById("GLOBAL")
                .map(e -> new SettingsNaming(e.getFeatureTemplate(), e.getReleaseTemplate()));
    }

    @Override
    public void saveRef(SettingsRef v) {
        // SettingsRef currently has no fields
    }

    @Override
    public Optional<SettingsRef> getRef() {
        return Optional.of(new SettingsRef());
    }

    @Override
    @Transactional
    public void saveBlocking(SettingsBlocking v) {
        SystemSettingsJpaEntity entity = getOrCreate();
        entity.setDefaultBlockingPolicy(v.defaultPolicy());
        repository.save(entity);
    }

    @Override
    public Optional<SettingsBlocking> getBlocking() {
        return repository.findById("GLOBAL")
                .map(e -> new SettingsBlocking(e.getDefaultBlockingPolicy()));
    }

    private SystemSettingsJpaEntity getOrCreate() {
        return repository.findById("GLOBAL").orElseGet(() -> {
            SystemSettingsJpaEntity entity = new SystemSettingsJpaEntity();
            entity.setId("GLOBAL");
            return entity;
        });
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
