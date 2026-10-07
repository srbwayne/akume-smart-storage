package dev.akume.storage.catalog.adapter.in.rest;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;

/** HTTP input for creating an ItemCategory. */
public class CreateItemCategoryRequest {

    @NotBlank
    private String name;

    public CreateItemCategoryRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown request property");
    }
}
