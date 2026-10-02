package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.domain.model.AddressType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressTypeQueryServiceTest {

    @Mock
    private AddressTypeRepository addressTypes;

    @InjectMocks
    private AddressTypeQueryService service;

    @Test
    void getsExistingAddressTypePreservingIdentityAndInactiveState() {
        UUID id = UUID.randomUUID();
        AddressType expected = AddressType.reconstitute(id, "RACK", "Prateleira", null, false);
        when(addressTypes.findById(id)).thenReturn(Optional.of(expected));

        AddressType result = service.getById(id);

        verify(addressTypes).findById(id);
        assertSame(expected, result);
        assertEquals(id, result.id());
        assertFalse(result.active());
    }

    @Test
    void throwsSpecificExceptionWhenAddressTypeDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(addressTypes.findById(id)).thenReturn(Optional.empty());

        AddressTypeNotFoundException exception = assertThrows(AddressTypeNotFoundException.class,
                () -> service.getById(id));

        assertEquals(id, exception.addressTypeId());
    }

    @Test
    void listsBothActiveAndInactiveAddressTypes() {
        AddressType active = AddressType.create("SHELF", "Estante", null);
        AddressType inactive = AddressType.reconstitute(UUID.randomUUID(), "RACK", "Prateleira", null, false);
        when(addressTypes.findAll()).thenReturn(List.of(active, inactive));

        List<AddressType> results = service.listAll();

        verify(addressTypes).findAll();
        assertEquals(2, results.size());
        assertSame(active, results.get(0));
        assertSame(inactive, results.get(1));
        assertTrue(results.get(0).active());
        assertFalse(results.get(1).active());
    }

    @Test
    void returnsEmptyListWhenRepositoryHasNoAddressTypes() {
        when(addressTypes.findAll()).thenReturn(List.of());

        assertTrue(service.listAll().isEmpty());
    }
}
