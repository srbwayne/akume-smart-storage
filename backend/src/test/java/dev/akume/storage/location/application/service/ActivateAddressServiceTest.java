package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressConcurrentModificationException;
import dev.akume.storage.location.application.exception.AddressNotFoundException;
import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.exception.InactiveAddressParentException;
import dev.akume.storage.location.application.exception.InactiveAddressTypeException;
import dev.akume.storage.location.application.port.in.ActivateAddressCommand;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.application.port.out.SerializableTransactionExecutor;
import dev.akume.storage.location.domain.model.Address;
import dev.akume.storage.location.domain.model.AddressType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ActivateAddressServiceTest {

    private final AddressRepository addresses = mock(AddressRepository.class);
    private final AddressTypeRepository addressTypes = mock(AddressTypeRepository.class);
    private final DirectExecutor transactions = new DirectExecutor();
    private ActivateAddressService service;

    @BeforeEach
    void setUp() {
        service = new ActivateAddressService(addresses, addressTypes, transactions);
    }

    @Test
    void missingAddressFailsWithoutFurtherLookups() {
        UUID id = UUID.randomUUID();
        when(addresses.findById(id)).thenReturn(Optional.empty());

        assertThrows(AddressNotFoundException.class,
                () -> service.activate(new ActivateAddressCommand(id, 0)));

        verify(addresses).findById(id);
        verifyNoMoreInteractions(addresses, addressTypes);
    }

    @Test
    void staleInactiveAddressFailsBeforeTypeLookup() {
        Address address = address(null, false, 4);
        when(addresses.findById(address.id())).thenReturn(Optional.of(address));

        assertThrows(AddressConcurrentModificationException.class,
                () -> service.activate(new ActivateAddressCommand(address.id(), 3)));

        verify(addresses).findById(address.id());
        verifyNoMoreInteractions(addresses, addressTypes);
    }

    @Test
    void staleAlreadyActiveAddressFailsBeforeTypeLookup() {
        Address address = address(null, true, 4);
        when(addresses.findById(address.id())).thenReturn(Optional.of(address));

        assertThrows(AddressConcurrentModificationException.class,
                () -> service.activate(new ActivateAddressCommand(address.id(), 3)));

        verify(addresses).findById(address.id());
        verifyNoMoreInteractions(addresses, addressTypes);
    }

    @Test
    void activatesInactiveRootAndPreservesStructuralStateInsideExecutor() {
        Address address = address(null, false, 5);
        AddressType type = addressType(address.addressTypeId(), true);
        when(addresses.findById(address.id())).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return Optional.of(address);
        });
        when(addressTypes.findById(type.id())).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return Optional.of(type);
        });
        when(addresses.update(address)).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return persisted(address, 6);
        });

        Address result = service.activate(new ActivateAddressCommand(address.id(), 5));

        assertTrue(result.active());
        assertEquals(6, result.version());
        assertEquals(address.id(), result.id());
        assertEquals(address.name(), result.name());
        assertEquals(address.normalizedNameKey(), result.normalizedNameKey());
        assertEquals(address.addressTypeId(), result.addressTypeId());
        assertEquals(null, result.parentId());
        assertEquals(1, transactions.calls());
        verify(addresses).update(address);
        verify(addresses, times(1)).findById(address.id());
    }

    @Test
    void alreadyActiveRootValidatesTypeAndDoesNotUpdate() {
        Address address = address(null, true, 2);
        AddressType type = addressType(address.addressTypeId(), true);
        when(addresses.findById(address.id())).thenReturn(Optional.of(address));
        when(addressTypes.findById(type.id())).thenReturn(Optional.of(type));

        Address result = service.activate(new ActivateAddressCommand(address.id(), 2));

        assertSame(address, result);
        assertEquals(2, result.version());
        verify(addressTypes).findById(type.id());
        verify(addresses, times(1)).findById(address.id());
        verify(addresses, never()).update(any(Address.class));
        verifyNoMoreInteractions(addressTypes);
    }

    @Test
    void activatesInactiveChildAfterValidatingTypeAndParent() {
        UUID parentId = UUID.randomUUID();
        Address address = address(parentId, false, 7);
        Address parent = addressWithId(parentId, null, true, 0, address.addressTypeId());
        AddressType type = addressType(address.addressTypeId(), true);
        when(addresses.findById(address.id())).thenReturn(Optional.of(address));
        when(addresses.findById(parentId)).thenReturn(Optional.of(parent));
        when(addressTypes.findById(type.id())).thenReturn(Optional.of(type));
        when(addresses.update(address)).thenAnswer(invocation -> persisted(address, 8));

        Address result = service.activate(new ActivateAddressCommand(address.id(), 7));

        assertTrue(result.active());
        assertEquals(8, result.version());
        assertEquals(address.id(), result.id());
        assertEquals(address.name(), result.name());
        assertEquals(address.normalizedNameKey(), result.normalizedNameKey());
        assertEquals(address.addressTypeId(), result.addressTypeId());
        assertEquals(parentId, result.parentId());
        verify(addresses).findById(address.id());
        verify(addressTypes).findById(type.id());
        verify(addresses).findById(parentId);
        verify(addresses).update(address);
    }

    @Test
    void alreadyActiveChildValidatesTypeAndParentButDoesNotUpdate() {
        UUID parentId = UUID.randomUUID();
        Address address = address(parentId, true, 7);
        Address parent = addressWithId(parentId, null, true, 0, address.addressTypeId());
        AddressType type = addressType(address.addressTypeId(), true);
        when(addresses.findById(address.id())).thenReturn(Optional.of(address));
        when(addresses.findById(parentId)).thenReturn(Optional.of(parent));
        when(addressTypes.findById(type.id())).thenReturn(Optional.of(type));

        Address result = service.activate(new ActivateAddressCommand(address.id(), 7));

        assertSame(address, result);
        assertEquals(7, result.version());
        verify(addressTypes).findById(type.id());
        verify(addresses).findById(parentId);
        verify(addresses, never()).update(any(Address.class));
    }

    @Test
    void missingTypeRejectsInactiveAddressBeforeParentLookup() {
        Address address = address(UUID.randomUUID(), false, 0);
        when(addresses.findById(address.id())).thenReturn(Optional.of(address));
        when(addressTypes.findById(address.addressTypeId())).thenReturn(Optional.empty());

        assertThrows(AddressTypeNotFoundException.class,
                () -> service.activate(new ActivateAddressCommand(address.id(), 0)));

        verify(addresses, times(1)).findById(address.id());
        verify(addressTypes).findById(address.addressTypeId());
        verify(addresses, never()).update(any(Address.class));
        verifyNoMoreInteractions(addresses, addressTypes);
    }

    @Test
    void inactiveTypeRejectsInactiveAddressBeforeParentLookup() {
        Address address = address(UUID.randomUUID(), false, 0);
        AddressType type = addressType(address.addressTypeId(), false);
        when(addresses.findById(address.id())).thenReturn(Optional.of(address));
        when(addressTypes.findById(type.id())).thenReturn(Optional.of(type));

        assertThrows(InactiveAddressTypeException.class,
                () -> service.activate(new ActivateAddressCommand(address.id(), 0)));

        verify(addresses, times(1)).findById(address.id());
        verify(addressTypes).findById(type.id());
        verify(addresses, never()).update(any(Address.class));
        verifyNoMoreInteractions(addresses, addressTypes);
    }

    @Test
    void alreadyActiveAddressWithMissingTypeFailsInsteadOfNoOp() {
        Address address = address(null, true, 0);
        when(addresses.findById(address.id())).thenReturn(Optional.of(address));
        when(addressTypes.findById(address.addressTypeId())).thenReturn(Optional.empty());

        assertThrows(AddressTypeNotFoundException.class,
                () -> service.activate(new ActivateAddressCommand(address.id(), 0)));

        verify(addresses, never()).update(any(Address.class));
    }

    @Test
    void alreadyActiveAddressWithInactiveTypeFailsInsteadOfNoOp() {
        Address address = address(null, true, 0);
        AddressType type = addressType(address.addressTypeId(), false);
        when(addresses.findById(address.id())).thenReturn(Optional.of(address));
        when(addressTypes.findById(type.id())).thenReturn(Optional.of(type));

        assertThrows(InactiveAddressTypeException.class,
                () -> service.activate(new ActivateAddressCommand(address.id(), 0)));

        verify(addresses, never()).update(any(Address.class));
    }

    @Test
    void missingParentRejectsInactiveChild() {
        UUID parentId = UUID.randomUUID();
        Address address = address(parentId, false, 0);
        AddressType type = addressType(address.addressTypeId(), true);
        when(addresses.findById(address.id())).thenReturn(Optional.of(address));
        when(addresses.findById(parentId)).thenReturn(Optional.empty());
        when(addressTypes.findById(type.id())).thenReturn(Optional.of(type));

        assertThrows(AddressNotFoundException.class,
                () -> service.activate(new ActivateAddressCommand(address.id(), 0)));

        verify(addresses, never()).update(any(Address.class));
    }

    @Test
    void inactiveParentRejectsInactiveChild() {
        UUID parentId = UUID.randomUUID();
        Address address = address(parentId, false, 0);
        Address parent = addressWithId(parentId, null, false, 0, address.addressTypeId());
        AddressType type = addressType(address.addressTypeId(), true);
        when(addresses.findById(address.id())).thenReturn(Optional.of(address));
        when(addresses.findById(parentId)).thenReturn(Optional.of(parent));
        when(addressTypes.findById(type.id())).thenReturn(Optional.of(type));

        assertThrows(InactiveAddressParentException.class,
                () -> service.activate(new ActivateAddressCommand(address.id(), 0)));

        verify(addresses, never()).update(any(Address.class));
    }

    @Test
    void missingParentRejectsAlreadyActiveChildInsteadOfNoOp() {
        UUID parentId = UUID.randomUUID();
        Address address = address(parentId, true, 0);
        AddressType type = addressType(address.addressTypeId(), true);
        when(addresses.findById(address.id())).thenReturn(Optional.of(address));
        when(addresses.findById(parentId)).thenReturn(Optional.empty());
        when(addressTypes.findById(type.id())).thenReturn(Optional.of(type));

        assertThrows(AddressNotFoundException.class,
                () -> service.activate(new ActivateAddressCommand(address.id(), 0)));

        verify(addresses, never()).update(any(Address.class));
    }

    @Test
    void inactiveParentRejectsAlreadyActiveChildInsteadOfNoOp() {
        UUID parentId = UUID.randomUUID();
        Address address = address(parentId, true, 0);
        Address parent = addressWithId(parentId, null, false, 0, address.addressTypeId());
        AddressType type = addressType(address.addressTypeId(), true);
        when(addresses.findById(address.id())).thenReturn(Optional.of(address));
        when(addresses.findById(parentId)).thenReturn(Optional.of(parent));
        when(addressTypes.findById(type.id())).thenReturn(Optional.of(type));

        assertThrows(InactiveAddressParentException.class,
                () -> service.activate(new ActivateAddressCommand(address.id(), 0)));

        verify(addresses, never()).update(any(Address.class));
    }

    private static Address address(UUID parentId, boolean active, int version) {
        return addressWithId(UUID.randomUUID(), parentId, active, version, UUID.randomUUID());
    }

    private static Address addressWithId(UUID id, UUID parentId, boolean active, int version, UUID typeId) {
        String name = "Address" + id.toString().substring(0, 8);
        return Address.reconstitute(id, name, name.toLowerCase(), typeId, parentId, active, version);
    }

    private static AddressType addressType(UUID id, boolean active) {
        return AddressType.reconstitute(id, "TYPE", "Type", null, active);
    }

    private static Address persisted(Address address, int version) {
        return Address.reconstitute(address.id(), address.name(), address.normalizedNameKey(),
                address.addressTypeId(), address.parentId(), address.active(), version);
    }

    private static final class DirectExecutor implements SerializableTransactionExecutor {

        private int calls;
        private boolean executing;

        @Override
        public <T> T execute(Supplier<T> operation) {
            calls++;
            executing = true;
            try {
                return operation.get();
            } finally {
                executing = false;
            }
        }

        int calls() { return calls; }
        boolean executing() { return executing; }
    }
}
