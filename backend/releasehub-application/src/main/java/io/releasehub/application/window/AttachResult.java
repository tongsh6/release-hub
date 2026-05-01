package io.releasehub.application.window;

import io.releasehub.domain.window.WindowIteration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Attach 操作结果，包含成功的绑定和每个仓库的分支操作结果。
 */
public class AttachResult {

    private final WindowIteration windowIteration;
    private final List<RepoError> errors;

    private AttachResult(WindowIteration windowIteration, List<RepoError> errors) {
        this.windowIteration = windowIteration;
        this.errors = Collections.unmodifiableList(errors);
    }

    public static AttachResult success(WindowIteration wi) {
        return new AttachResult(wi, List.of());
    }

    public static AttachResult partial(WindowIteration wi, List<RepoError> errors) {
        return new AttachResult(wi, errors);
    }

    public WindowIteration getWindowIteration() { return windowIteration; }
    public List<RepoError> getErrors() { return errors; }
    public boolean hasErrors() { return !errors.isEmpty(); }

    public record RepoError(String repoId, String repoName, String message) {
        public static RepoError of(String repoId, String repoName, String message) {
            return new RepoError(repoId, repoName, message);
        }
    }
}
