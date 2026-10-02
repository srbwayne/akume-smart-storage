CREATE TABLE address_types (
    id UUID PRIMARY KEY,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL,
    CONSTRAINT uk_address_types_code UNIQUE (code)
);
