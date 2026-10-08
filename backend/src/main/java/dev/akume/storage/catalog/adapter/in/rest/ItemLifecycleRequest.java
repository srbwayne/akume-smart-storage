package dev.akume.storage.catalog.adapter.in.rest;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import tools.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** HTTP input for an Item lifecycle operation. */
public class ItemLifecycleRequest {

    @NotNull
    @PositiveOrZero
    @JsonDeserialize(using = StrictIntegralJsonIntegerDeserializer.class)
    private Integer expectedVersion;

    public ItemLifecycleRequest() {
    }

    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown request property");
    }
}
