package dev.akume.storage.catalog.adapter.in.rest;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import tools.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** HTTP input for updating Item descriptive metadata. */
public class UpdateItemMetadataRequest {

    @NotBlank
    private String name;
    private String description;
    @NotNull
    @PositiveOrZero
    @JsonDeserialize(using = StrictIntegralJsonIntegerDeserializer.class)
    private Integer expectedVersion;

    public UpdateItemMetadataRequest() {
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown request property");
    }
}
