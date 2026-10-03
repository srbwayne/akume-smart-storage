package dev.akume.storage.location.adapter.in.rest;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/** HTTP input requiring an explicit destination, where JSON null means the root. */
public class MoveAddressRequest {
    private UUID newParentId;
    private boolean newParentIdPresent;
    @NotNull
    @PositiveOrZero
    private Integer expectedVersion;

    public UUID getNewParentId() { return newParentId; }

    @JsonProperty("newParentId")
    public void setNewParentId(UUID newParentId) {
        this.newParentId = newParentId;
        this.newParentIdPresent = true;
    }

    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }

    @AssertTrue(message = "newParentId must be present")
    @JsonIgnore
    public boolean isNewParentIdPresent() { return newParentIdPresent; }

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown request property");
    }
}
