-- Run once on PostgreSQL if automatic Hibernate schema updates are disabled.
-- These are new tables; existing users, friends and messages are unaffected.
CREATE TABLE IF NOT EXISTS ai_saved_conversations (
    id VARCHAR(36) PRIMARY KEY,
    owner_public_id VARCHAR(36) NOT NULL,
    persona VARCHAR(30) NOT NULL,
    history_json TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ai_saved_owner_persona UNIQUE (owner_public_id, persona)
);

CREATE TABLE IF NOT EXISTS ai_daily_usage (
    bucket_key VARCHAR(100) PRIMARY KEY,
    usage_day DATE NOT NULL,
    requests BIGINT NOT NULL,
    reserved_micros BIGINT NOT NULL
);
