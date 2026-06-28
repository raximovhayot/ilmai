CREATE TABLE room_invites (
    id         UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    room_id    UUID         NOT NULL,
    code       VARCHAR(64)  NOT NULL,
    created_by UUID         NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_room_invites_room_id
        FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_room_invites_created_by
        FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_room_invites_code ON room_invites (code);
CREATE UNIQUE INDEX uk_room_invites_room_active ON room_invites (room_id) WHERE NOT revoked;
CREATE INDEX idx_room_invites_room_id ON room_invites (room_id);
