ALTER TABLE learning_plans
    ADD COLUMN goal_id UUID;

UPDATE learning_plans
SET goal_id = id
WHERE goal_id IS NULL;

ALTER TABLE learning_plans
    ALTER COLUMN goal_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_learning_plans_user_status ON learning_plans (user_id, status);
CREATE INDEX IF NOT EXISTS idx_learning_plans_goal ON learning_plans (goal_id);
