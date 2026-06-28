ALTER TABLE spaces RENAME TO rooms;
ALTER TABLE rooms RENAME COLUMN user_id TO owner_id;

ALTER TABLE rooms ADD COLUMN personal BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE rooms ADD COLUMN goal VARCHAR(500);
ALTER TABLE rooms ADD COLUMN target_date DATE;
ALTER TABLE rooms ADD COLUMN daily_study_minutes INTEGER;

UPDATE rooms SET personal = TRUE;

DROP INDEX uk_spaces_user_id;
CREATE UNIQUE INDEX uk_rooms_owner_personal ON rooms (owner_id) WHERE personal;
CREATE INDEX idx_rooms_owner_id ON rooms (owner_id);

CREATE TABLE room_members (
    id         UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    room_id    UUID         NOT NULL,
    user_id    UUID         NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_room_members_room_id
        FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_room_members_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_room_members_room_user ON room_members (room_id, user_id);
CREATE INDEX idx_room_members_user_id ON room_members (user_id);

INSERT INTO room_members (room_id, user_id, role)
SELECT id, owner_id, 'OWNER' FROM rooms;
