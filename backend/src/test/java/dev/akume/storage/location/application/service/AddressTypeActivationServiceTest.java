package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressTypeInUseException;
import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.application.port.out.SerializableTransactionExecutor;
import dev.akume.storage.location.domain.model.AddressType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressTypeActivationServiceTest {

    @Mock
    private AddressTypeRepository addressTypes;

    @Mock
    private AddressRepository addresses;

    private final DirectExecutor transactions = new DirectExecutor();

    private AddressTypeActivationService service;

    @BeforeEach
    void setUp() {
        service = new AddressTypeActivationService(addressTypes, addresses, transactions);
    }

    @Test
    void activatesAndSavesExistingInactiveAddressType() {
        AddressType existing = addressType(false);
        when(addressTypes.findById(existing.id())).thenReturn(Optional.of(existing));
        when(addressTypes.save(existing)).thenAnswer(invocation -> invocation.getArgument(0));

        AddressType result = service.activate(existing.id());

        ArgumentCaptor<AddressType> saved = ArgumentCaptor.forClass(AddressType.class);
        verify(addressTypes).findById(existing.id());
        verify(addressTypes).save(saved.capture());
        assertSame(existing, saved.getValue());
        assertSame(existing, result);
        assertEquals(existing.id(), result.id());
        assertTrue(result.active());
    }

    @Test
    void activatingAlreadyActiveAddressTypeSucceedsAndRemainsActive() {
        AddressType existing = addressType(true);
        when(addressTypes.findById(existing.id())).thenReturn(Optional.of(existing));
        when(addressTypes.save(existing)).thenAnswer(invocation -> invocation.getArgument(0));

        AddressType result = service.activate(existing.id());

        verify(addressTypes).save(existing);
        assertSame(existing, result);
        assertTrue(result.active());
    }

    @Test
    void activationOfMissingAddressTypeThrowsAndDoesNotSave() {
        UUID id = UUID.randomUUID();
        when(addressTypes.findById(id)).thenReturn(Optional.empty());

        AddressTypeNotFoundException exception = assertThrows(AddressTypeNotFoundException.class,
                () -> service.activate(id));

        assertEquals(id, exception.addressTypeId());
        verify(addressTypes, never()).save(any(AddressType.class));
    }

    @Test
    void deactivatesAndSavesExistingActiveAddressType() {
        AddressType existing = addressType(true);
        when(addressTypes.findById(existing.id())).thenReturn(Optional.of(existing));
        when(addressTypes.save(existing)).thenAnswer(invocation -> invocation.getArgument(0));
        when(addresses.existsActiveByAddressTypeId(existing.id())).thenAnswer(invocation -> {
            org.junit.jupiter.api.Assertions.assertTrue(transactions.executing());
            return false;
        });

        AddressType result = service.deactivate(existing.id());

        ArgumentCaptor<AddressType> saved = ArgumentCaptor.forClass(AddressType.class);
        verify(addressTypes).findById(existing.id());
        verify(addresses).existsActiveByAddressTypeId(existing.id());
        verify(addressTypes).save(saved.capture());
        assertSame(existing, saved.getValue());
        assertSame(existing, result);
        assertEquals(existing.id(), result.id());
        assertFalse(result.active());
    }

    @Test
    void deactivatingAlreadyInactiveAddressTypeIsNoOpWithoutUsageQueryOrSave() {
        AddressType existing = addressType(false);
        when(addressTypes.findById(existing.id())).thenReturn(Optional.of(existing));
        AddressType result = service.deactivate(existing.id());

        verify(addressTypes).findById(existing.id());
        verify(addresses, never()).existsActiveByAddressTypeId(existing.id());
        verify(addressTypes, never()).save(any(AddressType.class));
        assertSame(existing, result);
        assertFalse(result.active());
    }

    @Test
    void deactivationOfMissingAddressTypeThrowsAndDoesNotSave() {
        UUID id = UUID.randomUUID();
        when(addressTypes.findById(id)).thenReturn(Optional.empty());

        AddressTypeNotFoundException exception = assertThrows(AddressTypeNotFoundException.class,
                () -> service.deactivate(id));

        assertEquals(id, exception.addressTypeId());
        verify(addressTypes, never()).save(any(AddressType.class));
        verify(addresses, never()).existsActiveByAddressTypeId(id);
    }

    @Test
    void activeAddressUseBlocksTypeDeactivationWithoutSaving() {
        AddressType existing = addressType(true);
        when(addressTypes.findById(existing.id())).thenReturn(Optional.of(existing));
        when(addresses.existsActiveByAddressTypeId(existing.id())).thenReturn(true);

        AddressTypeInUseException exception = assertThrows(AddressTypeInUseException.class,
                () -> service.deactivate(existing.id()));

        assertEquals(existing.id(), exception.addressTypeId());
        assertTrue(existing.active());
        verify(addresses).existsActiveByAddressTypeId(existing.id());
        verify(addressTypes, never()).save(any(AddressType.class));
    }

    @Test
    void deactivationRunsThroughSerializableExecutorCallback() {
        AddressType existing = addressType(true);
        when(addressTypes.findById(existing.id())).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return Optional.of(existing);
        });
        when(addresses.existsActiveByAddressTypeId(existing.id())).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return false;
        });
        when(addressTypes.save(existing)).thenAnswer(invocation -> {
            assertTrue(transactions.executing());
            return invocation.getArgument(0);
        });

        service.deactivate(existing.id());

        assertEquals(1, transactions.calls());
        assertFalse(transactions.executing());
        verify(addressTypes, times(1)).save(existing);
    }

    private static AddressType addressType(boolean active) {
        return AddressType.reconstitute(UUID.randomUUID(), "RACK", "Prateleira", null, active);
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
