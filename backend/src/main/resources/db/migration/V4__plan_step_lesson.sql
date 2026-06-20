ALTER TABLE plan_steps
    ADD COLUMN lesson_content      TEXT,
    ADD COLUMN lesson_citations    JSONB,
    ADD COLUMN lesson_generated_at TIMESTAMPTZ;
