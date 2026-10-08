CREATE TABLE items (
    id UUID NOT NULL,
    name TEXT NOT NULL,
    description TEXT NULL,
    item_category_id UUID NOT NULL,
    active BOOLEAN NOT NULL,
    version INTEGER NOT NULL,
    CONSTRAINT pk_items PRIMARY KEY (id),
    CONSTRAINT fk_items_item_category_id FOREIGN KEY (item_category_id)
        REFERENCES item_categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_items_version_non_negative CHECK (version >= 0)
);
