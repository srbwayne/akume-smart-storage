package dev.akume.storage.location.adapter.in.rest;

import dev.akume.storage.location.domain.model.Address;

import java.util.UUID;

/** Public HTTP representation of an Address. */
public record AddressResponse(UUID id, String name, UUID addressTypeId, UUID parentId, boolean active, int version) {

    public static AddressResponse from(Address address) {
        return new AddressResponse(address.id(), address.name(), address.addressTypeId(),
                address.parentId(), address.active(), address.version());
    }
}
