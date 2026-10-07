package io.releasehub.application.window;

import io.releasehub.application.settings.SettingsPort;

public final class ReleaseBranchNaming {
    private ReleaseBranchNaming() {}

    public static String resolve(String windowKey, SettingsPort.SettingsNaming naming) {
        String template = naming == null ? null : naming.releaseTemplate();
        return template == null || template.isBlank()
                ? "release/" + windowKey : template.replace("{key}", windowKey);
    }
}
