package dev.akume.storage.catalog.adapter.in.rest;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import tools.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/** HTTP input for changing an Item's category assignment. */
public class ReassignItemCategoryRequest {

    @NotNull
    private UUID itemCategoryId;
    @NotNull
    @PositiveOrZero
    @JsonDeserialize(using = StrictIntegralJsonIntegerDeserializer.class)
    private Integer expectedVersion;

    public ReassignItemCategoryRequest() {
    }

    public UUID getItemCategoryId() { return itemCategoryId; }
    public void setItemCategoryId(UUID itemCategoryId) { this.itemCategoryId = itemCategoryId; }
    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown request property");
    }
}
