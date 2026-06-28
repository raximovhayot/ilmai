ALTER TABLE quiz_sessions
    ADD COLUMN room_id UUID;

UPDATE quiz_sessions qs
SET room_id = r.id
FROM rooms r
WHERE r.owner_id = qs.user_id
  AND r.personal;

ALTER TABLE quiz_sessions
    ALTER COLUMN room_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_quiz_sessions_room_user
    ON quiz_sessions (room_id, user_id);

ALTER TABLE knowledge_gaps
    ADD COLUMN room_id UUID;

UPDATE knowledge_gaps kg
SET room_id = r.id
FROM rooms r
WHERE r.owner_id = kg.user_id
  AND r.personal;

ALTER TABLE knowledge_gaps
    ALTER COLUMN room_id SET NOT NULL;

ALTER TABLE knowledge_gaps
    DROP CONSTRAINT uk_knowledge_gaps_user_concept;

ALTER TABLE knowledge_gaps
    ADD CONSTRAINT uk_knowledge_gaps_room_concept UNIQUE (room_id, concept);

CREATE INDEX IF NOT EXISTS idx_knowledge_gaps_room_miss
    ON knowledge_gaps (room_id, miss_count DESC);
