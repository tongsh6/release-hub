package io.releasehub.interfaces.api.repo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitialVersionView {
    private String repoId;
    private String version;
}
