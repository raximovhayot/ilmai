ALTER TABLE chat_sessions
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_chat_sessions_active
    ON chat_sessions (user_id, channel, active, created_at DESC);
