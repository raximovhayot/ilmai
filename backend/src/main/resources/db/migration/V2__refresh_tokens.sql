CREATE TABLE refresh_tokens (
    id         UUID         PRIMARY KEY,
    jti        VARCHAR(64)  NOT NULL,
    family_id  UUID         NOT NULL,
    user_id    UUID         NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_refresh_tokens_jti UNIQUE (jti),
    CONSTRAINT fk_refresh_tokens_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_family_status ON refresh_tokens (family_id, status);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
