CREATE TABLE authors (
    id         UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100)             NOT NULL,
    email      VARCHAR(255)             NOT NULL UNIQUE,
    bio        VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL    DEFAULT NOW()
);
