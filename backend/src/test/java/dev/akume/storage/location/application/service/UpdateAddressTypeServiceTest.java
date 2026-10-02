package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.port.in.UpdateAddressTypeCommand;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateAddressTypeServiceTest {

    @Mock
    private AddressTypeRepository addressTypes;

    @InjectMocks
    private UpdateAddressTypeService service;

    @Test
    void updatesAndSavesDetailsWithoutChangingCodeOrActiveState() {
        UUID id = UUID.randomUUID();
        AddressType existing = AddressType.reconstitute(id, "RACK", "Old name", "Old description", false);
        when(addressTypes.findById(id)).thenReturn(Optional.of(existing));
        when(addressTypes.save(any(AddressType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressType result = service.update(new UpdateAddressTypeCommand(id, "New name", null));

        verify(addressTypes).findById(id);
        ArgumentCaptor<AddressType> saved = ArgumentCaptor.forClass(AddressType.class);
        verify(addressTypes).save(saved.capture());
        assertSame(existing, saved.getValue());
        assertSame(existing, result);
        assertEquals(id, result.id());
        assertEquals("RACK", result.code());
        assertEquals("New name", result.name());
        assertNull(result.description());
        assertFalse(result.active());
    }

    @Test
    void throwsNotFoundAndDoesNotSaveWhenIdDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(addressTypes.findById(id)).thenReturn(Optional.empty());

        AddressTypeNotFoundException exception = assertThrows(AddressTypeNotFoundException.class,
                () -> service.update(new UpdateAddressTypeCommand(id, "New name", null)));

        assertEquals(id, exception.addressTypeId());
        verify(addressTypes, never()).save(any(AddressType.class));
    }
}
