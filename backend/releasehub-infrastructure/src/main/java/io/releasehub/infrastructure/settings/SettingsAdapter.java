package io.releasehub.infrastructure.settings;

import io.releasehub.application.settings.SettingsPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SettingsAdapter implements SettingsPort {
    private final AtomicReference<SettingsGitLab> gitlab = new AtomicReference<>();
    private final AtomicReference<SettingsNaming> naming = new AtomicReference<>();
    private final AtomicReference<SettingsRef> ref = new AtomicReference<>();
    private final AtomicReference<SettingsBlocking> blocking = new AtomicReference<>();

    @Override
    public void saveGitLab(SettingsGitLab v) {
        gitlab.set(v);
    }

    @Override
    public Optional<SettingsGitLab> getGitLab() {
        return Optional.ofNullable(gitlab.get());
    }

    @Override
    public void saveNaming(SettingsNaming v) {
        naming.set(v);
    }

    @Override
    public Optional<SettingsNaming> getNaming() {
        return Optional.ofNullable(naming.get());
    }

    @Override
    public void saveRef(SettingsRef v) {
        ref.set(v);
    }

    @Override
    public Optional<SettingsRef> getRef() {
        return Optional.ofNullable(ref.get());
    }

    @Override
    public void saveBlocking(SettingsBlocking v) {
        blocking.set(v);
    }

    @Override
    public Optional<SettingsBlocking> getBlocking() {
        return Optional.ofNullable(blocking.get());
    }
}
