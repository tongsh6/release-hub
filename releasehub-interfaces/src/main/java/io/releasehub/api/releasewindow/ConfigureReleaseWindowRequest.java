package io.releasehub.api.releasewindow;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public class ConfigureReleaseWindowRequest {
    @NotBlank
    private String startAt;

    @NotBlank
    private String endAt;

    public String getStartAt() {
        return startAt;
    }

    public void setStartAt(String startAt) {
        this.startAt = startAt;
    }

    public String getEndAt() {
        return endAt;
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
