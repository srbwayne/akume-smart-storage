package dev.akume.storage.location.adapter.out.persistence;

import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.application.service.AddressTypeActivationService;
import dev.akume.storage.location.domain.model.AddressType;
import dev.akume.storage.location.domain.exception.AddressTypeCodeAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AddressTypePersistenceIntegrationTest {

    @Autowired
    private AddressTypeRepository addressTypes;

    @Autowired
    private SpringDataAddressTypeRepository jpaAddressTypes;

    @Autowired
    private AddressTypeActivationService activationService;

    @BeforeEach
    void clearAddressTypes() {
        jpaAddressTypes.deleteAll();
    }

    @Test
    void savesAndRetrievesAddressTypeWithAllPersistedValues() {
        AddressType source = AddressType.create("DRAWER", "Gaveta", "Small parts");
        source.deactivate();

        addressTypes.save(source);
        AddressType retrieved = addressTypes.findById(source.id()).orElseThrow();

        assertEquals(source.id(), retrieved.id());
        assertEquals("DRAWER", retrieved.code());
        assertEquals("Gaveta", retrieved.name());
        assertEquals("Small parts", retrieved.description());
        assertFalse(retrieved.active());
    }

    @Test
    void savesAndRetrievesAddressTypeWithOptionalDescription() {
        AddressType source = AddressType.create("SHELF", "Estante", null);

        addressTypes.save(source);

        AddressType retrieved = addressTypes.findByCode("SHELF").orElseThrow();
        assertEquals(source.id(), retrieved.id());
        assertEquals("SHELF", retrieved.code());
        assertEquals("Estante", retrieved.name());
        assertNull(retrieved.description());
        assertTrue(retrieved.active());
    }

    @Test
    void updatesExistingAddressTypeWithoutChangingIdentityCodeOrLifecycleState() {
        AddressType source = AddressType.create("DRAWER", "Drawer", "Original");
        AddressType persisted = addressTypes.save(source);

        persisted.updateDetails("Storage Drawer", "Updated");
        addressTypes.save(persisted);

        AddressType reloaded = addressTypes.findById(source.id()).orElseThrow();
        assertEquals(source.id(), reloaded.id());
        assertEquals("DRAWER", reloaded.code());
        assertEquals("Storage Drawer", reloaded.name());
        assertEquals("Updated", reloaded.description());
        assertTrue(reloaded.active());
    }

    @Test
    void persistsDeactivationAndActivationThroughApplicationLifecyclePath() {
        AddressType active = addressTypes.save(AddressType.create("LIFECYCLE", "Lifecycle", null));

        activationService.deactivate(active.id());
        AddressType inactiveReloaded = addressTypes.findById(active.id()).orElseThrow();
        assertFalse(inactiveReloaded.active());

        activationService.activate(active.id());
        AddressType activeReloaded = addressTypes.findById(active.id()).orElseThrow();
        assertTrue(activeReloaded.active());
        assertEquals(active.id(), activeReloaded.id());
        assertEquals("LIFECYCLE", activeReloaded.code());
    }

    @Test
    void databaseRejectsDuplicateCode() {
        jpaAddressTypes.saveAndFlush(entity(UUID.randomUUID(), "BIN"));

        assertThrows(DataIntegrityViolationException.class,
                () -> jpaAddressTypes.saveAndFlush(entity(UUID.randomUUID(), "BIN")));
    }

    @Test
    void translatesPostgresCodeUniqueViolationToApplicationConflict() {
        jpaAddressTypes.saveAndFlush(entity(UUID.randomUUID(), "BIN"));
        AddressType duplicate = AddressType.create("BIN", "Another bin", null);

        assertThrows(AddressTypeCodeAlreadyExistsException.class, () -> addressTypes.save(duplicate));
    }

    private static AddressTypeJpaEntity entity(UUID id, String code) {
        return new AddressTypeJpaEntity(id, code, "Bin", null, true);
    }
}
