package dev.akume.storage.catalog.adapter.in.rest;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** HTTP input for creating a catalog Item. */
public class CreateItemRequest {

    @NotBlank
    private String name;
    private String description;
    @NotNull
    private UUID itemCategoryId;

    public CreateItemRequest() {
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UUID getItemCategoryId() { return itemCategoryId; }
    public void setItemCategoryId(UUID itemCategoryId) { this.itemCategoryId = itemCategoryId; }

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown request property");
    }
}
