package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressConcurrentModificationException;
import dev.akume.storage.location.application.exception.AddressCycleDetectedException;
import dev.akume.storage.location.application.exception.AddressNotFoundException;
import dev.akume.storage.location.application.exception.InactiveAddressParentException;
import dev.akume.storage.location.application.port.in.MoveAddressCommand;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.application.port.out.SerializableTransactionExecutor;
import dev.akume.storage.location.domain.exception.AddressSiblingNameAlreadyExistsException;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class MoveAddressServiceTest {

    private final AddressRepository addresses = mock(AddressRepository.class);
    private final DirectExecutor transactions = new DirectExecutor();
    private MoveAddressService service;

    @BeforeEach
    void setUp() {
        service = new MoveAddressService(addresses, transactions);
    }

    @Test
    void missingSourceFailsWithoutFurtherPersistenceWork() {
        UUID id = UUID.randomUUID();
        when(addresses.findById(id)).thenReturn(Optional.empty());

        assertThrows(AddressNotFoundException.class,
                () -> service.move(new MoveAddressCommand(id, null, 0)));

        verify(addresses).findById(id);
        verifyNoMoreInteractions(addresses);
    }

    @Test
    void staleVersionFailsBeforeSameParentNoOpOrOtherQueries() {
        UUID parentId = UUID.randomUUID();
        Address source = address("Drawer", parentId, true, 4);
        when(addresses.findById(source.id())).thenReturn(Optional.of(source));

        assertThrows(AddressConcurrentModificationException.class,
                () -> service.move(new MoveAddressCommand(source.id(), parentId, 3)));

        verify(addresses).findById(source.id());
        verifyNoMoreInteractions(addresses);
    }

    @Test
    void sameNonRootParentIsNoOpInsideExecutor() {
        UUID parentId = UUID.randomUUID();
        Address source = address("Drawer", parentId, true, 4);
        when(addresses.findById(source.id())).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return Optional.of(source);
        });

        Address result = service.move(new MoveAddressCommand(source.id(), parentId, 4));

        assertSame(source, result);
        assertEquals(1, transactions.calls());
        assertEquals(4, result.version());
        verify(addresses).findById(source.id());
        verifyNoMoreInteractions(addresses);
    }

    @Test
    void rootToRootIsNoOp() {
        Address source = address("Drawer", null, true, 2);
        when(addresses.findById(source.id())).thenReturn(Optional.of(source));

        Address result = service.move(new MoveAddressCommand(source.id(), null, 2));

        assertSame(source, result);
        assertEquals(2, result.version());
        verify(addresses).findById(source.id());
        verifyNoMoreInteractions(addresses);
    }

    @Test
    void staleRootToRootFailsBeforeNoOp() {
        Address source = address("Drawer", null, true, 2);
        when(addresses.findById(source.id())).thenReturn(Optional.of(source));

        assertThrows(AddressConcurrentModificationException.class,
                () -> service.move(new MoveAddressCommand(source.id(), null, 1)));

        verify(addresses).findById(source.id());
        verifyNoMoreInteractions(addresses);
    }

    @Test
    void movesChildToRootWithRootUniquenessAndNoDestinationOrCycleQuery() {
        Address source = address("Drawer", UUID.randomUUID(), true, 5);
        when(addresses.findById(source.id())).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return Optional.of(source);
        });
        when(addresses.existsRootNameKey(source.normalizedNameKey(), source.id())).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return false;
        });
        when(addresses.update(source)).thenAnswer(invocation -> persistedAfterMove(source));

        Address result = service.move(new MoveAddressCommand(source.id(), null, 5));

        assertEquals(source.id(), result.id());
        assertEquals(null, result.parentId());
        assertEquals("Drawer", result.name());
        assertEquals("drawer", result.normalizedNameKey());
        assertEquals(source.addressTypeId(), result.addressTypeId());
        assertTrue(result.active());
        assertEquals(6, result.version());
        verify(addresses).findById(source.id());
        verify(addresses).existsRootNameKey(source.normalizedNameKey(), source.id());
        verify(addresses).update(source);
        verify(addresses, never()).isDescendant(any(UUID.class), any(UUID.class));
        verifyNoMoreInteractions(addresses);
    }

    @Test
    void duplicateRootNamePreventsChildToRootUpdate() {
        Address source = address("Drawer", UUID.randomUUID(), true, 1);
        when(addresses.findById(source.id())).thenReturn(Optional.of(source));
        when(addresses.existsRootNameKey(source.normalizedNameKey(), source.id())).thenReturn(true);

        assertThrows(AddressSiblingNameAlreadyExistsException.class,
                () -> service.move(new MoveAddressCommand(source.id(), null, 1)));

        assertEquals(source.parentId(), addressSourceParent(source));
        verify(addresses, never()).update(any(Address.class));
        verify(addresses).findById(source.id());
        verify(addresses).existsRootNameKey(source.normalizedNameKey(), source.id());
        verifyNoMoreInteractions(addresses);
    }

    @Test
    void movesBetweenParentsAndPreservesOtherState() {
        UUID currentParent = UUID.randomUUID();
        Address source = address("Drawer", currentParent, true, 7);
        Address destination = address("Office", null, true, 3);
        when(addresses.findById(source.id())).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return Optional.of(source);
        });
        when(addresses.findById(destination.id())).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return Optional.of(destination);
        });
        when(addresses.isDescendant(source.id(), destination.id())).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return false;
        });
        when(addresses.existsChildNameKey(destination.id(), source.normalizedNameKey(), source.id()))
                .thenAnswer(invocation -> {
                    assertTrue(transactions.executing());
                    return false;
                });
        when(addresses.update(source)).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return persistedAfterMove(source);
        });

        Address result = service.move(new MoveAddressCommand(source.id(), destination.id(), 7));

        assertEquals(destination.id(), result.parentId());
        assertEquals(source.id(), result.id());
        assertEquals("Drawer", result.name());
        assertEquals(source.normalizedNameKey(), result.normalizedNameKey());
        assertEquals(source.addressTypeId(), result.addressTypeId());
        assertEquals(source.active(), result.active());
        assertEquals(8, result.version());
        verify(addresses).isDescendant(source.id(), destination.id());
        verify(addresses).existsChildNameKey(destination.id(), source.normalizedNameKey(), source.id());
    }

    @Test
    void missingDestinationFailsBeforeCycleSiblingOrUpdate() {
        Address source = address("Drawer", UUID.randomUUID(), true, 0);
        UUID destinationId = UUID.randomUUID();
        when(addresses.findById(source.id())).thenReturn(Optional.of(source));
        when(addresses.findById(destinationId)).thenReturn(Optional.empty());

        assertThrows(AddressNotFoundException.class,
                () -> service.move(new MoveAddressCommand(source.id(), destinationId, 0)));

        verify(addresses, never()).isDescendant(any(UUID.class), any(UUID.class));
        verify(addresses, never()).existsChildNameKey(any(UUID.class), any(String.class), any(UUID.class));
        verify(addresses, never()).update(any(Address.class));
    }

    @Test
    void selfParentFailsBeforeRecursiveQuery() {
        Address source = address("Drawer", null, true, 0);
        when(addresses.findById(source.id())).thenReturn(Optional.of(source));

        assertThrows(AddressCycleDetectedException.class,
                () -> service.move(new MoveAddressCommand(source.id(), source.id(), 0)));

        verify(addresses, times(2)).findById(source.id());
        verify(addresses, never()).isDescendant(any(UUID.class), any(UUID.class));
        verify(addresses, never()).update(any(Address.class));
    }

    @Test
    void activeSourceCannotMoveUnderInactiveDestination() {
        Address source = address("Drawer", UUID.randomUUID(), true, 0);
        Address destination = address("Archive", null, false, 0);
        when(addresses.findById(source.id())).thenReturn(Optional.of(source));
        when(addresses.findById(destination.id())).thenReturn(Optional.of(destination));

        assertThrows(InactiveAddressParentException.class,
                () -> service.move(new MoveAddressCommand(source.id(), destination.id(), 0)));

        verify(addresses, never()).isDescendant(any(UUID.class), any(UUID.class));
        verify(addresses, never()).existsChildNameKey(any(UUID.class), any(String.class), any(UUID.class));
        verify(addresses, never()).update(any(Address.class));
    }

    @Test
    void inactiveSourceMayMoveUnderInactiveDestinationAndRemainsInactive() {
        Address source = address("Drawer", UUID.randomUUID(), false, 4);
        Address destination = address("Archive", null, false, 0);
        when(addresses.findById(source.id())).thenReturn(Optional.of(source));
        when(addresses.findById(destination.id())).thenReturn(Optional.of(destination));
        when(addresses.isDescendant(source.id(), destination.id())).thenReturn(false);
        when(addresses.existsChildNameKey(destination.id(), source.normalizedNameKey(), source.id()))
                .thenReturn(false);
        when(addresses.update(source)).thenAnswer(invocation -> persistedAfterMove(source));

        Address result = service.move(new MoveAddressCommand(source.id(), destination.id(), 4));

        assertEquals(destination.id(), result.parentId());
        assertFalse(result.active());
        assertEquals(5, result.version());
    }

    @Test
    void inactiveSourceMayMoveUnderActiveDestinationAndRemainsInactive() {
        Address source = address("Drawer", UUID.randomUUID(), false, 2);
        Address destination = address("Office", null, true, 0);
        when(addresses.findById(source.id())).thenReturn(Optional.of(source));
        when(addresses.findById(destination.id())).thenReturn(Optional.of(destination));
        when(addresses.isDescendant(source.id(), destination.id())).thenReturn(false);
        when(addresses.existsChildNameKey(destination.id(), source.normalizedNameKey(), source.id()))
                .thenReturn(false);
        when(addresses.update(source)).thenAnswer(invocation -> persistedAfterMove(source));

        Address result = service.move(new MoveAddressCommand(source.id(), destination.id(), 2));

        assertFalse(result.active());
        assertEquals(destination.id(), result.parentId());
    }

    @Test
    void directDescendantIsRejected() {
        assertDescendantRejected("Child", false);
    }

    @Test
    void deepDescendantIsRejected() {
        assertDescendantRejected("Grandchild", true);
    }

    @Test
    void duplicateDestinationSiblingPreventsUpdate() {
        Address source = address("Drawer", UUID.randomUUID(), true, 3);
        Address destination = address("Office", null, true, 0);
        when(addresses.findById(source.id())).thenReturn(Optional.of(source));
        when(addresses.findById(destination.id())).thenReturn(Optional.of(destination));
        when(addresses.isDescendant(source.id(), destination.id())).thenReturn(false);
        when(addresses.existsChildNameKey(destination.id(), source.normalizedNameKey(), source.id()))
                .thenReturn(true);

        assertThrows(AddressSiblingNameAlreadyExistsException.class,
                () -> service.move(new MoveAddressCommand(source.id(), destination.id(), 3)));

        verify(addresses, never()).update(any(Address.class));
    }

    private void assertDescendantRejected(String destinationName, boolean deep) {
        Address source = address("Root", null, true, 0);
        Address intermediate = deep ? address("Intermediate", source.id(), true, 0) : null;
        Address destination = address(destinationName,
                deep ? intermediate.id() : source.id(), true, 0);
        when(addresses.findById(source.id())).thenReturn(Optional.of(source));
        when(addresses.findById(destination.id())).thenReturn(Optional.of(destination));
        when(addresses.isDescendant(source.id(), destination.id())).thenReturn(true);

        assertThrows(AddressCycleDetectedException.class,
                () -> service.move(new MoveAddressCommand(source.id(), destination.id(), 0)));

        verify(addresses).isDescendant(source.id(), destination.id());
        verify(addresses, never()).existsChildNameKey(any(UUID.class), any(String.class), any(UUID.class));
        verify(addresses, never()).update(any(Address.class));
    }

    private static Address address(String name, UUID parentId, boolean active, int version) {
        UUID typeId = UUID.randomUUID();
        Address created = Address.create(name, typeId, parentId);
        return Address.reconstitute(created.id(), created.name(), created.normalizedNameKey(), typeId,
                parentId, active, version);
    }

    private static Address persistedAfterMove(Address address) {
        return Address.reconstitute(address.id(), address.name(), address.normalizedNameKey(),
                address.addressTypeId(), address.parentId(), address.active(), address.version() + 1);
    }

    private static UUID addressSourceParent(Address address) {
        return address.parentId();
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

        int calls() {
            return calls;
        }

        boolean executing() {
            return executing;
        }
    }
}
