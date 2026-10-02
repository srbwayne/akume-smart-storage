package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.port.in.CreateAddressTypeCommand;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.domain.exception.AddressTypeCodeAlreadyExistsException;
import dev.akume.storage.location.domain.model.AddressType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateAddressTypeServiceTest {

    @Mock
    private AddressTypeRepository addressTypes;

    @InjectMocks
    private CreateAddressTypeService service;

    @Test
    void createsAndSavesAddressTypeReturningDomainResult() {
        CreateAddressTypeCommand command = new CreateAddressTypeCommand("DRAWER", "Gaveta", "Small parts");
        when(addressTypes.existsByCode("DRAWER")).thenReturn(false);
        when(addressTypes.save(org.mockito.ArgumentMatchers.any(AddressType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AddressType result = service.create(command);

        ArgumentCaptor<AddressType> saved = ArgumentCaptor.forClass(AddressType.class);
        verify(addressTypes).existsByCode("DRAWER");
        verify(addressTypes).save(saved.capture());
        assertNotNull(result.id());
        assertEquals(result.id(), saved.getValue().id());
        assertEquals("DRAWER", saved.getValue().code());
        assertEquals("Gaveta", saved.getValue().name());
        assertEquals("Small parts", saved.getValue().description());
        assertEquals("DRAWER", result.code());
        assertEquals("Gaveta", result.name());
        assertEquals("Small parts", result.description());
        assertTrue(result.active());
    }

    @Test
    void rejectsDuplicateCodeWithoutSaving() {
        when(addressTypes.existsByCode("DRAWER")).thenReturn(true);

        assertThrows(AddressTypeCodeAlreadyExistsException.class,
                () -> service.create(new CreateAddressTypeCommand("DRAWER", "Gaveta", null)));

        verify(addressTypes).existsByCode("DRAWER");
        verify(addressTypes, never()).save(org.mockito.ArgumentMatchers.any(AddressType.class));
    }
}
