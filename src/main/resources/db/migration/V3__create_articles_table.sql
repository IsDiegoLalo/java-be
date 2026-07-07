CREATE TABLE articles (
    id            UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    title         VARCHAR(255)             NOT NULL,
    body          TEXT                     NOT NULL,
    summary       VARCHAR(500),
    author_id     UUID                     NOT NULL REFERENCES authors(id),
    category_id   UUID                     NOT NULL REFERENCES categories(id),
    tags          TEXT[]                   NOT NULL    DEFAULT '{}',
    status        VARCHAR(20)             NOT NULL    DEFAULT 'draft'
                  CONSTRAINT chk_articles_status CHECK (status IN ('draft', 'review', 'published')),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL    DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL    DEFAULT NOW(),
    published_at  TIMESTAMP WITH TIME ZONE,
    search_vector tsvector
);

-- Indexes for common query patterns
CREATE INDEX idx_articles_author_id      ON articles (author_id);
CREATE INDEX idx_articles_category_id    ON articles (category_id);
CREATE INDEX idx_articles_status         ON articles (status);
CREATE INDEX idx_articles_tags           ON articles USING GIN (tags);
CREATE INDEX idx_articles_search_vector  ON articles USING GIN (search_vector);
