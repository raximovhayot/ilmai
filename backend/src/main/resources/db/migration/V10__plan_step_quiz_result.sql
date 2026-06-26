ALTER TABLE plan_steps
    ADD COLUMN quiz_session_id UUID,
    ADD COLUMN quiz_passed     BOOLEAN,
    ADD COLUMN quiz_score      INTEGER;
