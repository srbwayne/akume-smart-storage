package dev.akume.storage.location.adapter.in.rest;

import dev.akume.storage.location.domain.model.AddressType;

import java.util.UUID;

/** Public HTTP representation of an address type. */
public record AddressTypeResponse(UUID id, String code, String name, String description, boolean active) {

    public static AddressTypeResponse from(AddressType addressType) {
        return new AddressTypeResponse(
                addressType.id(),
                addressType.code(),
                addressType.name(),
                addressType.description(),
                addressType.active());
    }
}
