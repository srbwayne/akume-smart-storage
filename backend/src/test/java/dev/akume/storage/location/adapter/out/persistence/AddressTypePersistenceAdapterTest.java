package dev.akume.storage.location.adapter.out.persistence;

import dev.akume.storage.location.domain.model.AddressType;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AddressTypePersistenceAdapterTest {

    @Test
    void doesNotTranslateUnrelatedDataIntegrityFailure() {
        SpringDataAddressTypeRepository repository = mock(SpringDataAddressTypeRepository.class);
        AddressTypePersistenceAdapter adapter = new AddressTypePersistenceAdapter(repository);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("unrelated constraint failure");
        when(repository.saveAndFlush(any(AddressTypeJpaEntity.class))).thenThrow(failure);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> adapter.save(AddressType.create("DRAWER", "Gaveta", null)));

        assertSame(failure, thrown);
    }

    @Test
    void requiresAddressTypeIdForActiveUsageQuery() {
        SpringDataAddressRepository repository = mock(SpringDataAddressRepository.class);
        AddressPersistenceAdapter adapter = new AddressPersistenceAdapter(repository);

        assertThrows(NullPointerException.class, () -> adapter.existsActiveByAddressTypeId(null));
        verifyNoInteractions(repository);
    }
}
