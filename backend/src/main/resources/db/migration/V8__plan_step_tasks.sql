ALTER TABLE plan_steps
    ADD COLUMN order_in_day   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN reflection_note TEXT,
    ADD COLUMN quiz_score      INTEGER;

DROP INDEX IF EXISTS idx_plan_steps_plan_day;

CREATE INDEX idx_plan_steps_plan_day
    ON plan_steps (plan_id, day_index, order_in_day);
