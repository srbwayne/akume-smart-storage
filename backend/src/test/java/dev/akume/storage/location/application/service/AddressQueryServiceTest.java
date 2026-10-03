package dev.akume.storage.location.application.service;

import dev.akume.storage.location.application.exception.AddressNotFoundException;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.domain.model.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressQueryServiceTest {

    @Mock
    private AddressRepository addresses;

    @InjectMocks
    private AddressQueryService service;

    @Test
    void getsExistingAddress() {
        Address expected = address();
        when(addresses.findById(expected.id())).thenReturn(Optional.of(expected));

        Address result = service.getById(expected.id());

        assertSame(expected, result);
        verify(addresses).findById(expected.id());
    }

    @Test
    void throwsAddressNotFoundWhenGetIdDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(addresses.findById(id)).thenReturn(Optional.empty());

        AddressNotFoundException exception = assertThrows(
                AddressNotFoundException.class, () -> service.getById(id));

        assertEquals(id, exception.addressId());
        verify(addresses).findById(id);
    }

    @Test
    void rejectsNullGetIdBeforeRepositoryCall() {
        assertThrows(NullPointerException.class, () -> service.getById(null));

        verifyNoInteractions(addresses);
    }

    @Test
    void listsAllAddressesWithoutTransformingResults() {
        List<Address> expected = List.of(address(), address());
        when(addresses.findAll()).thenReturn(expected);

        assertSame(expected, service.listAll());

        verify(addresses).findAll();
    }

    @Test
    void returnsEmptyListWhenThereAreNoAddresses() {
        when(addresses.findAll()).thenReturn(List.of());

        assertEquals(List.of(), service.listAll());

        verify(addresses).findAll();
    }

    @Test
    void listsRootsOnlyFromRootQuery() {
        List<Address> expected = List.of(address());
        when(addresses.findRoots()).thenReturn(expected);

        assertSame(expected, service.listRoots());

        verify(addresses).findRoots();
        verify(addresses, never()).findAll();
        verify(addresses, never()).findDirectChildren(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsEmptyRootListWhenThereAreNoAddresses() {
        when(addresses.findRoots()).thenReturn(List.of());

        assertEquals(List.of(), service.listRoots());

        verify(addresses).findRoots();
    }

    @Test
    void listsDirectChildrenOfExistingParent() {
        Address parent = address();
        List<Address> expected = List.of(address(), address());
        when(addresses.findById(parent.id())).thenReturn(Optional.of(parent));
        when(addresses.findDirectChildren(parent.id())).thenReturn(expected);

        assertSame(expected, service.listDirectChildren(parent.id()));

        verify(addresses).findById(parent.id());
        verify(addresses).findDirectChildren(parent.id());
    }

    @Test
    void returnsEmptyChildrenForExistingLeaf() {
        Address parent = address();
        when(addresses.findById(parent.id())).thenReturn(Optional.of(parent));
        when(addresses.findDirectChildren(parent.id())).thenReturn(List.of());

        assertEquals(List.of(), service.listDirectChildren(parent.id()));

        verify(addresses).findById(parent.id());
        verify(addresses).findDirectChildren(parent.id());
    }

    @Test
    void missingChildrenParentFailsBeforeDirectChildrenQuery() {
        UUID parentId = UUID.randomUUID();
        when(addresses.findById(parentId)).thenReturn(Optional.empty());

        AddressNotFoundException exception = assertThrows(
                AddressNotFoundException.class, () -> service.listDirectChildren(parentId));

        assertEquals(parentId, exception.addressId());
        verify(addresses).findById(parentId);
        verify(addresses, never()).findDirectChildren(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsNullChildrenParentBeforeRepositoryCall() {
        assertThrows(NullPointerException.class, () -> service.listDirectChildren(null));

        verifyNoInteractions(addresses);
    }

    private static Address address() {
        UUID id = UUID.randomUUID();
        String name = "Address " + id.toString().substring(0, 8);
        return Address.create(name, UUID.randomUUID(), null);
    }
}
