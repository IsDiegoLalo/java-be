CREATE TABLE outbox_events (
    id                UUID                     PRIMARY KEY,
    aggregate_type    VARCHAR(50)              NOT NULL,
    aggregate_id      UUID                     NOT NULL,
    event_type        VARCHAR(50)              NOT NULL,
    payload           JSONB                    NOT NULL,
    status            VARCHAR(20)              NOT NULL DEFAULT 'pending',
    retry_count       INT                      NOT NULL DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    last_attempted_at TIMESTAMP WITH TIME ZONE
);

-- Index for the outbox processor to find pending events efficiently
CREATE INDEX idx_outbox_events_status ON outbox_events (status) WHERE status = 'pending';
