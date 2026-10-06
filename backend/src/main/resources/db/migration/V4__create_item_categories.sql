CREATE TABLE item_categories (
    id UUID NOT NULL,
    name TEXT NOT NULL,
    normalized_name_key TEXT COLLATE "C" NOT NULL,
    active BOOLEAN NOT NULL,
    version INTEGER NOT NULL,
    CONSTRAINT pk_item_categories PRIMARY KEY (id),
    CONSTRAINT uk_item_categories_normalized_name_key UNIQUE (normalized_name_key),
    CONSTRAINT ck_item_categories_version_non_negative CHECK (version >= 0)
);
