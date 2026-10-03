package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressConcurrentModificationException;
import dev.akume.storage.location.application.exception.AddressNotFoundException;
import dev.akume.storage.location.application.port.in.RenameAddressCommand;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.domain.exception.AddressSiblingNameAlreadyExistsException;
import dev.akume.storage.location.domain.model.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RenameAddressServiceTest {

    private final AddressRepository addresses = mock(AddressRepository.class);
    private RenameAddressService service;

    @BeforeEach
    void setUp() {
        service = new RenameAddressService(addresses);
    }

    @Test
    void renamesRootUsingDomainKeyExcludingItselfAndReturnsPersistedVersion() {
        UUID typeId = UUID.randomUUID();
        Address existing = address("Old", typeId, null, true, 7);
        when(addresses.findById(existing.id())).thenReturn(Optional.of(existing));
        when(addresses.existsRootNameKey("café", existing.id())).thenReturn(false);
        when(addresses.update(any(Address.class))).thenAnswer(invocation -> nextVersion(invocation.getArgument(0)));

        Address result = service.rename(new RenameAddressCommand(existing.id(), "  Café  ", 7));

        assertEquals(existing.id(), result.id());
        assertEquals("Café", result.name());
        assertEquals("café", result.normalizedNameKey());
        assertEquals(typeId, result.addressTypeId());
        assertEquals(null, result.parentId());
        assertTrue(result.active());
        assertEquals(8, result.version());
        verify(addresses).existsRootNameKey("café", existing.id());
        verify(addresses, never()).existsChildNameKey(any(UUID.class), anyString(), any(UUID.class));
    }

    @Test
    void renamesChildUsingCurrentParentAndPreservesStructuralAndLifecycleState() {
        UUID typeId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Address existing = address("Drawer", typeId, parentId, false, 3);
        when(addresses.findById(existing.id())).thenReturn(Optional.of(existing));
        when(addresses.existsChildNameKey(parentId, "drawer", existing.id())).thenReturn(false);
        when(addresses.update(any(Address.class))).thenAnswer(invocation -> nextVersion(invocation.getArgument(0)));

        Address result = service.rename(new RenameAddressCommand(existing.id(), "DRAWER", 3));

        assertEquals("DRAWER", result.name());
        assertEquals("drawer", result.normalizedNameKey());
        assertEquals(existing.id(), result.id());
        assertEquals(typeId, result.addressTypeId());
        assertEquals(parentId, result.parentId());
        assertFalse(result.active());
        assertEquals(4, result.version());
        verify(addresses).existsChildNameKey(parentId, "drawer", existing.id());
    }

    @Test
    void staleExpectedVersionRejectsBeforeSiblingQueryOrUpdate() {
        Address existing = address("Room", UUID.randomUUID(), null, true, 4);
        when(addresses.findById(existing.id())).thenReturn(Optional.of(existing));

        assertThrows(AddressConcurrentModificationException.class,
                () -> service.rename(new RenameAddressCommand(existing.id(), "New name", 3)));

        verify(addresses, never()).existsRootNameKey(anyString(), any(UUID.class));
        verify(addresses, never()).update(any(Address.class));
    }

    @Test
    void missingAddressRejectsWithoutUpdate() {
        UUID id = UUID.randomUUID();
        when(addresses.findById(id)).thenReturn(Optional.empty());

        AddressNotFoundException failure = assertThrows(AddressNotFoundException.class,
                () -> service.rename(new RenameAddressCommand(id, "Room", 0)));

        assertEquals(id, failure.addressId());
        verify(addresses, never()).update(any(Address.class));
    }

    @Test
    void duplicateRootNameRejectsBeforeUpdate() {
        Address existing = address("Old", UUID.randomUUID(), null, true, 0);
        when(addresses.findById(existing.id())).thenReturn(Optional.of(existing));
        when(addresses.existsRootNameKey("taken", existing.id())).thenReturn(true);

        assertThrows(AddressSiblingNameAlreadyExistsException.class,
                () -> service.rename(new RenameAddressCommand(existing.id(), "Taken", 0)));

        verify(addresses, never()).update(any(Address.class));
    }

    @Test
    void duplicateChildNameRejectsBeforeUpdate() {
        UUID parentId = UUID.randomUUID();
        Address existing = address("Old", UUID.randomUUID(), parentId, true, 0);
        when(addresses.findById(existing.id())).thenReturn(Optional.of(existing));
        when(addresses.existsChildNameKey(parentId, "taken", existing.id())).thenReturn(true);

        assertThrows(AddressSiblingNameAlreadyExistsException.class,
                () -> service.rename(new RenameAddressCommand(existing.id(), "Taken", 0)));

        verify(addresses, never()).update(any(Address.class));
    }

    private static Address address(String name, UUID typeId, UUID parentId, boolean active, int version) {
        Address created = Address.create(name, typeId, parentId);
        return Address.reconstitute(
                created.id(), created.name(), created.normalizedNameKey(), typeId, parentId, active, version);
    }

    private static Address nextVersion(Address address) {
        return Address.reconstitute(
                address.id(), address.name(), address.normalizedNameKey(), address.addressTypeId(),
                address.parentId(), address.active(), address.version() + 1);
    }
}
