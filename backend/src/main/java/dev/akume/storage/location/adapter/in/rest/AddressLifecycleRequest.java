package dev.akume.storage.location.adapter.in.rest;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** HTTP input carrying the expected Address version for a lifecycle change. */
public class AddressLifecycleRequest {
    @NotNull
    @PositiveOrZero
    private Integer expectedVersion;

    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown request property");
    }
}
