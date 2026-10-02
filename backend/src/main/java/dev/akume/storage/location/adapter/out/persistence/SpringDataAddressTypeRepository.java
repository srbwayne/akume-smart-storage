package dev.akume.storage.location.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataAddressTypeRepository extends JpaRepository<AddressTypeJpaEntity, UUID> {

    Optional<AddressTypeJpaEntity> findByCode(String code);

    boolean existsByCode(String code);
}
