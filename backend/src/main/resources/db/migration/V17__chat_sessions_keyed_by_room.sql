ALTER TABLE chat_sessions
    ADD COLUMN room_id UUID;

UPDATE chat_sessions cs
SET room_id = r.id
FROM rooms r
WHERE r.owner_id = cs.user_id
  AND r.personal;

ALTER TABLE chat_sessions
    ALTER COLUMN room_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_chat_sessions_room_user
    ON chat_sessions (room_id, user_id);
