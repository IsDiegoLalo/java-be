CREATE TABLE categories (
    id          UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100)             NOT NULL,
    description VARCHAR(500),
    slug        VARCHAR(120)             NOT NULL UNIQUE
);

-- Case-insensitive unique constraint on category name
CREATE UNIQUE INDEX idx_categories_name_lower ON categories (LOWER(name));
