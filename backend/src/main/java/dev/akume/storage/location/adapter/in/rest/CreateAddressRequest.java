package dev.akume.storage.location.adapter.in.rest;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** HTTP input for creating a root or child Address. */
public class CreateAddressRequest {
    @NotBlank
    private String name;
    @NotNull
    private UUID addressTypeId;
    private UUID parentId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getAddressTypeId() { return addressTypeId; }
    public void setAddressTypeId(UUID addressTypeId) { this.addressTypeId = addressTypeId; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown request property");
    }
}
