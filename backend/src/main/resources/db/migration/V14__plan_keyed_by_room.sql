ALTER TABLE learning_plans
    ADD COLUMN room_id UUID;

UPDATE learning_plans lp
SET room_id = r.id
FROM rooms r
WHERE r.owner_id = lp.user_id
  AND r.personal;

ALTER TABLE learning_plans
    ALTER COLUMN room_id SET NOT NULL;

UPDATE learning_plans
SET goal_id = room_id;

CREATE INDEX IF NOT EXISTS idx_learning_plans_room_status ON learning_plans (room_id, status);
