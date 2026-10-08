package dev.akume.storage.catalog.adapter.out.persistence;

import dev.akume.storage.catalog.application.model.ItemReadView;
import dev.akume.storage.catalog.application.port.out.ItemReadProjectionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reads Item and current category summary in one PostgreSQL statement. */
@Repository
class ItemReadProjectionPersistenceAdapter implements ItemReadProjectionRepository {

    private static final String SELECT_FIELDS = """
            SELECT i.id AS item_id,
                   i.name AS item_name,
                   i.description AS item_description,
                   i.item_category_id,
                   i.active AS item_active,
                   i.version AS item_version,
                   c.id AS category_id,
                   c.name AS category_name,
                   c.active AS category_active
              FROM items i
              LEFT JOIN item_categories c ON c.id = i.item_category_id
            """;

    private final JdbcTemplate jdbc;

    ItemReadProjectionPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ItemReadView> findById(UUID id) {
        List<ItemReadView> rows = jdbc.query(
                SELECT_FIELDS + " WHERE i.id = ?", ItemReadProjectionPersistenceAdapter::mapRow, id);
        return rows.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemReadView> findAll() {
        return jdbc.query(SELECT_FIELDS + " ORDER BY i.id", ItemReadProjectionPersistenceAdapter::mapRow);
    }

    private static ItemReadView mapRow(ResultSet row, int rowNumber) throws SQLException {
        UUID itemCategoryId = row.getObject("item_category_id", UUID.class);
        UUID projectedCategoryId = row.getObject("category_id", UUID.class);
        if (projectedCategoryId == null || !projectedCategoryId.equals(itemCategoryId)) {
            throw new IllegalStateException("Item category reference could not be resolved");
        }

        ItemReadView.CategorySummary category = new ItemReadView.CategorySummary(
                projectedCategoryId,
                row.getString("category_name"),
                row.getBoolean("category_active"));
        return new ItemReadView(
                row.getObject("item_id", UUID.class),
                row.getString("item_name"),
                row.getString("item_description"),
                itemCategoryId,
                row.getBoolean("item_active"),
                row.getInt("item_version"),
                category);
    }
}
