package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.domain.model.AddressType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressTypeActivationServiceTest {

    @Mock
    private AddressTypeRepository addressTypes;

    @InjectMocks
    private AddressTypeActivationService service;

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

        AddressType result = service.deactivate(existing.id());

        ArgumentCaptor<AddressType> saved = ArgumentCaptor.forClass(AddressType.class);
        verify(addressTypes).findById(existing.id());
        verify(addressTypes).save(saved.capture());
        assertSame(existing, saved.getValue());
        assertSame(existing, result);
        assertEquals(existing.id(), result.id());
        assertFalse(result.active());
    }

    @Test
    void deactivatingAlreadyInactiveAddressTypeSucceedsAndRemainsInactive() {
        AddressType existing = addressType(false);
        when(addressTypes.findById(existing.id())).thenReturn(Optional.of(existing));
        when(addressTypes.save(existing)).thenAnswer(invocation -> invocation.getArgument(0));

        AddressType result = service.deactivate(existing.id());

        verify(addressTypes).save(existing);
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
    }

    private static AddressType addressType(boolean active) {
        return AddressType.reconstitute(UUID.randomUUID(), "RACK", "Prateleira", null, active);
    }
}
