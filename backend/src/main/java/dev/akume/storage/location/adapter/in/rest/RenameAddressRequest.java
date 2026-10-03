package dev.akume.storage.location.adapter.in.rest;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** HTTP input for renaming an Address. */
public class RenameAddressRequest {
    @NotBlank
    private String name;
    @NotNull
    @PositiveOrZero
    private Integer expectedVersion;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown request property");
    }
}
