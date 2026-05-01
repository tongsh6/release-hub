package io.releasehub.domain.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private String id;
    private String username;
    private String passwordHash;
    private String displayName;
    private boolean enabled;

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }
}
