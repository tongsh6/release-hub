package io.releasehub.interfaces.api.releasewindow;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

import java.time.Instant;

@Getter
public class ConfigureReleaseWindowRequest {
    @NotBlank
    private String startAt;

    @NotBlank
    private String endAt;

    public void setStartAt(String startAt) {
        this.startAt = startAt;
    }

    public void setEndAt(String endAt) {
        this.endAt = endAt;
    }

    public Instant getStartAtInstant() {
        return Instant.parse(startAt);
    }

    public Instant getEndAtInstant() {
        return Instant.parse(endAt);
    }
}
