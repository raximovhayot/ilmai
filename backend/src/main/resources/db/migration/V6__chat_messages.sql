CREATE TABLE chat_messages (
    id             UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id     UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    role           VARCHAR(16)  NOT NULL,
    content        TEXT         NOT NULL,
    citations      JSONB,
    low_confidence BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_chat_messages_session_id
        FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_chat_messages_session
    ON chat_messages (session_id, created_at, id);
