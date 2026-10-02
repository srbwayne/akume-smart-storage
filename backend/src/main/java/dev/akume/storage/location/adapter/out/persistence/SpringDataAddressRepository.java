package dev.akume.storage.location.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface SpringDataAddressRepository extends JpaRepository<AddressJpaEntity, UUID> {

    @Query("select address from AddressJpaEntity address order by address.id")
    List<AddressJpaEntity> findAllInIdOrder();

    @Query(value = """
            SELECT *
            FROM addresses
            WHERE parent_id IS NULL
            ORDER BY normalized_name_key COLLATE "C", id
            """, nativeQuery = true)
    List<AddressJpaEntity> findRootAddresses();

    @Query(value = """
            SELECT *
            FROM addresses
            WHERE parent_id = :parentId
            ORDER BY normalized_name_key COLLATE "C", id
            """, nativeQuery = true)
    List<AddressJpaEntity> findDirectChildAddresses(@Param("parentId") UUID parentId);

    boolean existsByParentIdIsNullAndNormalizedNameKeyAndIdNot(
            String normalizedNameKey,
            UUID excludedAddressId);

    boolean existsByParentIdAndNormalizedNameKeyAndIdNot(
            UUID parentId,
            String normalizedNameKey,
            UUID excludedAddressId);

    @Query(value = """
            WITH RECURSIVE descendants(id) AS (
                SELECT child.id
                FROM addresses child
                WHERE child.parent_id = :ancestorId

                UNION

                SELECT child.id
                FROM addresses child
                JOIN descendants parent ON child.parent_id = parent.id
            )
            SELECT (:candidateId <> :ancestorId
                    AND EXISTS (SELECT 1 FROM descendants WHERE id = :candidateId))
            """, nativeQuery = true)
    boolean isStrictDescendant(
            @Param("ancestorId") UUID ancestorId,
            @Param("candidateId") UUID candidateId);

    boolean existsByParentIdAndActiveTrue(UUID parentId);
}
