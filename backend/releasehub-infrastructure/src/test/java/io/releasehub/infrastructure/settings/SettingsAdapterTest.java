package io.releasehub.infrastructure.settings;

import io.releasehub.application.settings.SettingsPort;
import io.releasehub.infrastructure.persistence.settings.SystemSettingsJpaEntity;
import io.releasehub.infrastructure.persistence.settings.SystemSettingsJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsAdapterTest {

    @Mock
    private SystemSettingsJpaRepository repository;

    private SettingsAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new SettingsAdapter(repository);
    }

    @Test
    void should_save_gitlab_settings() {
        SettingsPort.SettingsGitLab gitlab = new SettingsPort.SettingsGitLab("http://gitlab.com", "secret-token");
        
        when(repository.findById("GLOBAL")).thenReturn(Optional.empty());
        
        adapter.saveGitLab(gitlab);
        
        ArgumentCaptor<SystemSettingsJpaEntity> captor = ArgumentCaptor.forClass(SystemSettingsJpaEntity.class);
        verify(repository).save(captor.capture());
        
        SystemSettingsJpaEntity saved = captor.getValue();
        assertEquals("GLOBAL", saved.getId());
        assertEquals("http://gitlab.com", saved.getGitlabBaseUrl());
        assertEquals("secret-token", saved.getGitlabToken());
    }

    @Test
    void should_get_gitlab_settings() {
        SystemSettingsJpaEntity entity = new SystemSettingsJpaEntity();
        entity.setId("GLOBAL");
        entity.setGitlabBaseUrl("http://gitlab.com");
        entity.setGitlabToken("secret-token");
        
        when(repository.findById("GLOBAL")).thenReturn(Optional.of(entity));
        
        Optional<SettingsPort.SettingsGitLab> result = adapter.getGitLab();
        
        assertTrue(result.isPresent());
        assertEquals("http://gitlab.com", result.get().baseUrl());
        assertEquals("secret-token", result.get().token());
    }

    @Test
    void should_save_naming_settings() {
        SettingsPort.SettingsNaming naming = new SettingsPort.SettingsNaming("feat/{key}", "rel/{key}");
        
        when(repository.findById("GLOBAL")).thenReturn(Optional.empty());
        
        adapter.saveNaming(naming);
        
        ArgumentCaptor<SystemSettingsJpaEntity> captor = ArgumentCaptor.forClass(SystemSettingsJpaEntity.class);
        verify(repository).save(captor.capture());
        
        SystemSettingsJpaEntity saved = captor.getValue();
        assertEquals("feat/{key}", saved.getFeatureTemplate());
        assertEquals("rel/{key}", saved.getReleaseTemplate());
    }
}
