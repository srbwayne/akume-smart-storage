package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressConcurrentModificationException;
import dev.akume.storage.location.application.exception.AddressHasActiveChildrenException;
import dev.akume.storage.location.application.exception.AddressNotFoundException;
import dev.akume.storage.location.application.port.in.DeactivateAddressCommand;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.application.port.out.SerializableTransactionExecutor;
import dev.akume.storage.location.domain.model.Address;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DeactivateAddressServiceTest {

    private final AddressRepository addresses = mock(AddressRepository.class);
    private final DirectExecutor transactions = new DirectExecutor();
    private DeactivateAddressService service;

    @BeforeEach
    void setUp() {
        service = new DeactivateAddressService(addresses, transactions);
    }

    @Test
    void missingAddressFailsWithoutFurtherPersistenceWork() {
        UUID id = UUID.randomUUID();
        when(addresses.findById(id)).thenReturn(Optional.empty());

        assertThrows(AddressNotFoundException.class,
                () -> service.deactivate(new DeactivateAddressCommand(id, 0)));

        verify(addresses).findById(id);
        verifyNoMoreInteractions(addresses);
    }

    @Test
    void staleActiveAddressFailsBeforeChildQuery() {
        Address address = address(true, 4, null);
        when(addresses.findById(address.id())).thenReturn(Optional.of(address));

        assertThrows(AddressConcurrentModificationException.class,
                () -> service.deactivate(new DeactivateAddressCommand(address.id(), 3)));

        verify(addresses).findById(address.id());
        verifyNoMoreInteractions(addresses);
    }

    @Test
    void staleInactiveAddressFailsBeforeNoOp() {
        Address address = address(false, 4, null);
        when(addresses.findById(address.id())).thenReturn(Optional.of(address));

        assertThrows(AddressConcurrentModificationException.class,
                () -> service.deactivate(new DeactivateAddressCommand(address.id(), 3)));

        verify(addresses).findById(address.id());
        verifyNoMoreInteractions(addresses);
    }

    @Test
    void alreadyInactiveAddressReturnsWithoutChildQueryOrUpdate() {
        Address address = address(false, 8, UUID.randomUUID());
        when(addresses.findById(address.id())).thenReturn(Optional.of(address));

        Address result = service.deactivate(new DeactivateAddressCommand(address.id(), 8));

        assertSame(address, result);
        assertFalse(result.active());
        assertEquals(8, result.version());
        assertEquals(1, transactions.calls());
        verify(addresses).findById(address.id());
        verify(addresses, never()).hasActiveDirectChild(address.id());
        verify(addresses, never()).update(any(Address.class));
        verifyNoMoreInteractions(addresses);
    }

    @Test
    void activeDirectChildBlocksDeactivationBeforeMutationOrUpdate() {
        Address address = address(true, 5, UUID.randomUUID());
        when(addresses.findById(address.id())).thenReturn(Optional.of(address));
        when(addresses.hasActiveDirectChild(address.id())).thenReturn(true);

        AddressHasActiveChildrenException failure = assertThrows(
                AddressHasActiveChildrenException.class,
                () -> service.deactivate(new DeactivateAddressCommand(address.id(), 5)));

        assertEquals(address.id(), failure.addressId());
        assertTrue(address.active());
        assertEquals(5, address.version());
        verify(addresses).findById(address.id());
        verify(addresses).hasActiveDirectChild(address.id());
        verify(addresses, never()).update(any(Address.class));
        verifyNoMoreInteractions(addresses);
    }

    @Test
    void deactivatesWhenNoActiveDirectChildExistsAndPreservesStructureInsideExecutor() {
        Address address = address(true, 6, UUID.randomUUID());
        when(addresses.findById(address.id())).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return Optional.of(address);
        });
        when(addresses.hasActiveDirectChild(address.id())).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return false;
        });
        when(addresses.update(address)).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return persisted(address, 7);
        });

        Address result = service.deactivate(new DeactivateAddressCommand(address.id(), 6));

        assertFalse(result.active());
        assertEquals(7, result.version());
        assertEquals(address.id(), result.id());
        assertEquals(address.name(), result.name());
        assertEquals(address.normalizedNameKey(), result.normalizedNameKey());
        assertEquals(address.addressTypeId(), result.addressTypeId());
        assertEquals(address.parentId(), result.parentId());
        assertEquals(1, transactions.calls());
        verify(addresses).findById(address.id());
        verify(addresses).hasActiveDirectChild(address.id());
        verify(addresses).update(address);
        verifyNoMoreInteractions(addresses);
    }

    private static Address address(boolean active, int version, UUID parentId) {
        UUID id = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        String name = "Address" + id.toString().substring(0, 8);
        return Address.reconstitute(id, name, name.toLowerCase(), typeId, parentId, active, version);
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
