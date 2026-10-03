package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressNotFoundException;
import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.exception.InactiveAddressParentException;
import dev.akume.storage.location.application.exception.InactiveAddressTypeException;
import dev.akume.storage.location.application.port.in.CreateAddressCommand;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.application.port.out.SerializableTransactionExecutor;
import dev.akume.storage.location.domain.exception.AddressSiblingNameAlreadyExistsException;
import dev.akume.storage.location.domain.model.Address;
import dev.akume.storage.location.domain.model.AddressType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class CreateAddressServiceTest {

    private final AddressRepository addresses = mock(AddressRepository.class);
    private final AddressTypeRepository addressTypes = mock(AddressTypeRepository.class);
    private final DirectExecutor transactions = new DirectExecutor();
    private CreateAddressService service;

    @BeforeEach
    void setUp() {
        service = new CreateAddressService(addresses, addressTypes, transactions);
    }

    @Test
    void createsRootUsingDomainCanonicalKeyInsideSerializableCallback() {
        AddressType type = addressType(true);
        Address[] inserted = new Address[1];
        when(addressTypes.findById(type.id())).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return Optional.of(type);
        });
        when(addresses.existsRootNameKey(eq("café"), any(UUID.class))).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return false;
        });
        when(addresses.insert(any(Address.class))).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            inserted[0] = invocation.getArgument(0);
            return inserted[0];
        });

        Address result = service.create(new CreateAddressCommand("  Café  ", type.id(), null));

        assertEquals(1, transactions.calls());
        assertSame(inserted[0], result);
        assertEquals("Café", result.name());
        assertEquals("café", result.normalizedNameKey());
        assertEquals(type.id(), result.addressTypeId());
        assertNull(result.parentId());
        assertTrue(result.active());
        verify(addresses).existsRootNameKey(eq("café"), any(UUID.class));
        verify(addresses, never()).existsChildNameKey(any(UUID.class), anyString(), any(UUID.class));
    }

    @Test
    void createsChildUnderActiveParentAndChecksThatSiblingSetInsideCallback() {
        AddressType type = addressType(true);
        Address parent = Address.create("Room", type.id(), null);
        when(addressTypes.findById(type.id())).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return Optional.of(type);
        });
        when(addresses.findById(parent.id())).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return Optional.of(parent);
        });
        when(addresses.existsChildNameKey(eq(parent.id()), eq("drawer"), any(UUID.class)))
                .thenAnswer(invocation -> {
                    assertTrue(transactions.executing());
                    return false;
                });
        when(addresses.insert(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Address result = service.create(new CreateAddressCommand("Drawer", type.id(), parent.id()));

        assertEquals(parent.id(), result.parentId());
        assertEquals(type.id(), result.addressTypeId());
        assertTrue(result.active());
        assertEquals(1, transactions.calls());
        verify(addresses).existsChildNameKey(eq(parent.id()), eq("drawer"), any(UUID.class));
    }

    @Test
    void rejectsMissingTypeBeforeParentSiblingOrInsertWork() {
        UUID typeId = UUID.randomUUID();
        when(addressTypes.findById(typeId)).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return Optional.empty();
        });

        assertThrows(AddressTypeNotFoundException.class,
                () -> service.create(new CreateAddressCommand("Room", typeId, UUID.randomUUID())));

        assertEquals(1, transactions.calls());
        verify(addresses, never()).findById(any(UUID.class));
        verify(addresses, never()).insert(any(Address.class));
    }

    @Test
    void rejectsInactiveTypeBeforeParentOrSiblingWork() {
        AddressType type = addressType(false);
        when(addressTypes.findById(type.id())).thenReturn(Optional.of(type));

        assertThrows(InactiveAddressTypeException.class,
                () -> service.create(new CreateAddressCommand("Room", type.id(), UUID.randomUUID())));

        verify(addresses, never()).findById(any(UUID.class));
        verify(addresses, never()).insert(any(Address.class));
    }

    @Test
    void rejectsMissingParentAndDoesNotInsert() {
        AddressType type = addressType(true);
        UUID parentId = UUID.randomUUID();
        when(addressTypes.findById(type.id())).thenReturn(Optional.of(type));
        when(addresses.findById(parentId)).thenReturn(Optional.empty());

        AddressNotFoundException failure = assertThrows(AddressNotFoundException.class,
                () -> service.create(new CreateAddressCommand("Drawer", type.id(), parentId)));

        assertEquals(parentId, failure.addressId());
        verify(addresses, never()).insert(any(Address.class));
        verify(addresses, never()).existsChildNameKey(any(UUID.class), anyString(), any(UUID.class));
    }

    @Test
    void rejectsInactiveParentAndDoesNotInsert() {
        AddressType type = addressType(true);
        Address parent = Address.reconstitute(
                UUID.randomUUID(), "Room", "room", type.id(), null, false, 0);
        when(addressTypes.findById(type.id())).thenReturn(Optional.of(type));
        when(addresses.findById(parent.id())).thenReturn(Optional.of(parent));

        InactiveAddressParentException failure = assertThrows(InactiveAddressParentException.class,
                () -> service.create(new CreateAddressCommand("Drawer", type.id(), parent.id())));

        assertEquals(parent.id(), failure.parentId());
        verify(addresses, never()).insert(any(Address.class));
        verify(addresses, never()).existsChildNameKey(any(UUID.class), anyString(), any(UUID.class));
    }

    @Test
    void rejectsDuplicateRootSiblingBeforeInsert() {
        AddressType type = addressType(true);
        when(addressTypes.findById(type.id())).thenReturn(Optional.of(type));
        when(addresses.existsRootNameKey(eq("room"), any(UUID.class))).thenReturn(true);

        assertThrows(AddressSiblingNameAlreadyExistsException.class,
                () -> service.create(new CreateAddressCommand("Room", type.id(), null)));

        verify(addresses, never()).insert(any(Address.class));
    }

    @Test
    void rejectsDuplicateChildSiblingBeforeInsert() {
        AddressType type = addressType(true);
        Address parent = Address.create("Office", type.id(), null);
        when(addressTypes.findById(type.id())).thenReturn(Optional.of(type));
        when(addresses.findById(parent.id())).thenReturn(Optional.of(parent));
        when(addresses.existsChildNameKey(eq(parent.id()), eq("drawer"), any(UUID.class))).thenReturn(true);

        assertThrows(AddressSiblingNameAlreadyExistsException.class,
                () -> service.create(new CreateAddressCommand("Drawer", type.id(), parent.id())));

        verify(addresses, never()).insert(any(Address.class));
    }

    private static AddressType addressType(boolean active) {
        return AddressType.reconstitute(UUID.randomUUID(), "ROOM", "Room", null, active);
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
