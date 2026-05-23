package io.releasehub.interfaces.api.repo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitialVersionView {
    private String repoId;
    private String version;
    private String versionSource;
    private String branch;
    private List<String> checkedPaths;
    private String errorType;
    private String message;
}
