CREATE TABLE addresses (
    id UUID NOT NULL,
    name TEXT NOT NULL,
    normalized_name_key TEXT COLLATE "C" NOT NULL,
    address_type_id UUID NOT NULL,
    parent_id UUID,
    active BOOLEAN NOT NULL,
    version INTEGER NOT NULL,
    CONSTRAINT pk_addresses PRIMARY KEY (id),
    CONSTRAINT fk_addresses_address_type_id
        FOREIGN KEY (address_type_id)
        REFERENCES address_types (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    CONSTRAINT fk_addresses_parent_id
        FOREIGN KEY (parent_id)
        REFERENCES addresses (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    CONSTRAINT ck_addresses_parent_not_self
        CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_addresses_version_non_negative
        CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_addresses_root_normalized_name_key
    ON addresses (normalized_name_key)
    WHERE parent_id IS NULL;

CREATE UNIQUE INDEX uk_addresses_parent_normalized_name_key
    ON addresses (parent_id, normalized_name_key)
    WHERE parent_id IS NOT NULL;

CREATE INDEX ix_addresses_address_type_id
    ON addresses (address_type_id);
