CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id         UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    username   VARCHAR(320) NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE user_identities (
    id                UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id           UUID         NOT NULL,
    provider          VARCHAR(50)  NOT NULL,
    provider_user_id  VARCHAR(255) NOT NULL,
    provider_username VARCHAR(255),
    raw_profile       JSONB,
    last_login_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_identities_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_user_identities_provider_subject UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_user_identities_user_id ON user_identities (user_id);

CREATE TABLE spaces (
    id         UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    name       VARCHAR(120) NOT NULL,
    user_id    UUID         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_spaces_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_spaces_user_id ON spaces (user_id);

CREATE TABLE vector_store (
    id        UUID    PRIMARY KEY DEFAULT uuid_generate_v4(),
    content   TEXT,
    metadata  JSONB,
    embedding vector(768)
);

CREATE INDEX vector_store_hnsw_cosine
    ON vector_store USING hnsw (embedding vector_cosine_ops);

CREATE INDEX idx_vector_store_user_id
    ON vector_store ((metadata->>'user_id'));

CREATE INDEX idx_vector_store_material_id
    ON vector_store ((metadata->>'material_id'));

CREATE INDEX idx_vector_store_chunk_kind
    ON vector_store ((metadata->>'chunk_kind'));

CREATE TABLE topics (
    id         UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    space_id   UUID         NOT NULL,
    name       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_topics_space_id
        FOREIGN KEY (space_id) REFERENCES spaces (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_topics_space_name_ci
    ON topics (space_id, lower(name));

CREATE INDEX idx_topics_space_id
    ON topics (space_id);

CREATE TABLE materials (
    id                UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    space_id          UUID         NOT NULL,
    topic_id          UUID,
    title             VARCHAR(255) NOT NULL,
    content_type      VARCHAR(120),
    size_bytes        BIGINT,
    status            VARCHAR(20)  NOT NULL,
    retry_count       INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    CONSTRAINT fk_materials_space_id
        FOREIGN KEY (space_id) REFERENCES spaces (id) ON DELETE CASCADE,
    CONSTRAINT fk_materials_topic_id
        FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE SET NULL
);

CREATE INDEX idx_materials_topic_created
    ON materials (topic_id, created_at DESC);

CREATE INDEX idx_materials_space_created
    ON materials (space_id, created_at DESC);

CREATE INDEX idx_materials_status
    ON materials (status);

CREATE INDEX idx_materials_status_updated_at
    ON materials (status, updated_at);

CREATE TABLE profiles (
    user_id             UUID         PRIMARY KEY,
    locale              VARCHAR(10)  NOT NULL DEFAULT 'EN',
    timezone            VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    goal                VARCHAR(500),
    target_date         DATE,
    daily_reminder      TIME,
    daily_study_minutes INTEGER,
    sessions_count      INTEGER      NOT NULL DEFAULT 0,
    quiz_count          INTEGER      NOT NULL DEFAULT 0,
    streak_days         INTEGER      NOT NULL DEFAULT 0,
    last_active_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_profiles_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE quiz_sessions (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID         NOT NULL,
    topic_id         UUID,
    difficulty       VARCHAR(20)  NOT NULL,
    difficulty_level INTEGER      NOT NULL DEFAULT 2,
    locale           VARCHAR(10)  NOT NULL DEFAULT 'EN',
    status           VARCHAR(20)  NOT NULL,
    score            DOUBLE PRECISION,
    correct_count    INTEGER      NOT NULL DEFAULT 0,
    total_count      INTEGER      NOT NULL DEFAULT 0,
    started_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_quiz_sessions_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_sessions_topic_id
        FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE SET NULL
);

CREATE INDEX idx_quiz_sessions_user_created
    ON quiz_sessions (user_id, created_at DESC);

CREATE TABLE quiz_questions (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id      UUID         NOT NULL,
    position        INTEGER      NOT NULL,
    type            VARCHAR(30)  NOT NULL,
    concept         VARCHAR(255),
    prompt          TEXT         NOT NULL,
    options         JSONB,
    correct_answer  TEXT,
    explanation     TEXT,
    material_id     UUID,
    chunk_index     INTEGER,
    user_answer     TEXT,
    is_correct      BOOLEAN,
    feedback        TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_quiz_questions_session_id
        FOREIGN KEY (session_id) REFERENCES quiz_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_questions_material_id
        FOREIGN KEY (material_id) REFERENCES materials (id) ON DELETE SET NULL,
    CONSTRAINT uk_quiz_questions_session_position UNIQUE (session_id, position)
);

CREATE INDEX idx_quiz_questions_session
    ON quiz_questions (session_id, position);

CREATE TABLE knowledge_gaps (
    id                    UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id               UUID         NOT NULL,
    concept               VARCHAR(255) NOT NULL,
    miss_count            INTEGER      NOT NULL DEFAULT 0,
    hit_count             INTEGER      NOT NULL DEFAULT 0,
    last_seen_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    suggested_material_id UUID,
    trend                 VARCHAR(20)  NOT NULL DEFAULT 'FLAT',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_knowledge_gaps_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_knowledge_gaps_material_id
        FOREIGN KEY (suggested_material_id) REFERENCES materials (id) ON DELETE SET NULL,
    CONSTRAINT uk_knowledge_gaps_user_concept UNIQUE (user_id, concept)
);

CREATE INDEX idx_knowledge_gaps_user_miss
    ON knowledge_gaps (user_id, miss_count DESC);

CREATE TABLE telegram_links (
    id                   UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id              UUID         NOT NULL,
    chat_id              BIGINT,
    telegram_user_id     BIGINT,
    telegram_username    VARCHAR(120),
    link_code            VARCHAR(20),
    link_code_expires_at TIMESTAMPTZ,
    linked_at            TIMESTAMPTZ,
    last_seen_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_telegram_links_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_telegram_links_user_id UNIQUE (user_id),
    CONSTRAINT uk_telegram_links_chat_id UNIQUE (chat_id),
    CONSTRAINT uk_telegram_links_link_code UNIQUE (link_code)
);

CREATE INDEX idx_telegram_links_telegram_user
    ON telegram_links (telegram_user_id);

CREATE TABLE subscriptions (
    id                   UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id              UUID         NOT NULL,
    plan                 VARCHAR(20)  NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    provider             VARCHAR(20)  NOT NULL,
    external_id          VARCHAR(255),
    current_period_start TIMESTAMPTZ,
    current_period_end   TIMESTAMPTZ,
    cancel_at_period_end BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_subscriptions_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_subscriptions_user_status
    ON subscriptions (user_id, status);

CREATE UNIQUE INDEX uk_subscriptions_provider_external
    ON subscriptions (provider, external_id)
    WHERE external_id IS NOT NULL;

CREATE TABLE payments (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID         NOT NULL,
    subscription_id  UUID,
    provider         VARCHAR(20)  NOT NULL,
    external_id      VARCHAR(255),
    amount_minor     BIGINT       NOT NULL,
    currency         VARCHAR(8)   NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    raw_payload      JSONB,
    occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_payments_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_payments_subscription_id
        FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE SET NULL
);

CREATE INDEX idx_payments_user_occurred
    ON payments (user_id, occurred_at DESC);

CREATE UNIQUE INDEX uk_payments_provider_external
    ON payments (provider, external_id)
    WHERE external_id IS NOT NULL;

CREATE TABLE chat_sessions (
    id         UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID         NOT NULL,
    channel    VARCHAR(16)  NOT NULL DEFAULT 'WEB',
    title      VARCHAR(200),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_chat_sessions_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_chat_sessions_user_id ON chat_sessions (user_id, created_at DESC);

CREATE TABLE user_memory_facts (
    id         UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID         NOT NULL,
    content    TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_memory_facts_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_memory_facts_user_id ON user_memory_facts (user_id, created_at DESC);

CREATE TABLE chat_memory_summary (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id      UUID         NOT NULL UNIQUE,
    summary         TEXT         NOT NULL,
    folded_messages INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_chat_memory_summary_session_id
        FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE CASCADE
);

CREATE TABLE user_fact_extraction_state (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id  UUID         NOT NULL UNIQUE,
    turns_seen  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_fact_extraction_state_session_id
        FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE CASCADE
);

CREATE TABLE user_memory_review_queue (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID         NOT NULL,
    concept          VARCHAR(255) NOT NULL,
    material_id      UUID,
    last_question_id UUID,
    interval_index   INTEGER      NOT NULL DEFAULT 0,
    next_review_at   TIMESTAMPTZ  NOT NULL,
    times_wrong      INTEGER      NOT NULL DEFAULT 0,
    times_correct    INTEGER      NOT NULL DEFAULT 0,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_memory_review_queue_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_memory_review_queue_user_concept
        UNIQUE (user_id, concept)
);

CREATE INDEX idx_user_memory_review_queue_due
    ON user_memory_review_queue (user_id, status, next_review_at);

CREATE TABLE learning_plans (
    id            UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       UUID         NOT NULL,
    goal          TEXT,
    target_date   DATE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    replan_needed BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_learning_plans_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_learning_plans_user_status
    ON learning_plans (user_id, status, created_at);

CREATE TABLE plan_steps (
    id             UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan_id        UUID         NOT NULL,
    day_index      INTEGER      NOT NULL,
    scheduled_date DATE,
    title          TEXT         NOT NULL,
    activity       VARCHAR(20)  NOT NULL DEFAULT 'READ',
    material_ids   JSONB,
    note           TEXT,
    done           BOOLEAN      NOT NULL DEFAULT FALSE,
    completed_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_plan_steps_plan_id
        FOREIGN KEY (plan_id) REFERENCES learning_plans (id) ON DELETE CASCADE
);

CREATE INDEX idx_plan_steps_plan_day
    ON plan_steps (plan_id, day_index);

CREATE INDEX idx_plan_steps_scheduled_date
    ON plan_steps (scheduled_date);

CREATE TABLE streaks (
    user_id          UUID        PRIMARY KEY,
    streak_current   INTEGER     NOT NULL DEFAULT 0,
    streak_longest   INTEGER     NOT NULL DEFAULT 0,
    streak_last_day  DATE,
    streak_broken_at DATE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_streaks_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE streak_days (
    id            UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       UUID        NOT NULL,
    activity_date DATE        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_streak_days_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_streak_days_user_date
        UNIQUE (user_id, activity_date)
);

CREATE TABLE outbox_messages (
    id            UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       UUID         NOT NULL,
    channel       VARCHAR(32)  NOT NULL,
    type          VARCHAR(64)  NOT NULL,
    body          TEXT         NOT NULL,
    scheduled_for TIMESTAMPTZ  NOT NULL,
    sent_at       TIMESTAMPTZ,
    dedupe_key    VARCHAR(200),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_outbox_messages_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_outbox_messages_dedupe_key
        UNIQUE (dedupe_key)
);

CREATE INDEX idx_outbox_messages_pending
    ON outbox_messages (sent_at, scheduled_for);

CREATE TABLE weekly_digests (
    id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID         NOT NULL,
    iso_week            VARCHAR(10)  NOT NULL,
    variant             VARCHAR(20)  NOT NULL,
    generated_at        TIMESTAMPTZ  NOT NULL,
    active_days         INT          NOT NULL DEFAULT 0,
    quizzes             INT          NOT NULL DEFAULT 0,
    answered            INT          NOT NULL DEFAULT 0,
    correct             INT          NOT NULL DEFAULT 0,
    avg_score           INT,
    plan_done           INT          NOT NULL DEFAULT 0,
    plan_total          INT          NOT NULL DEFAULT 0,
    streak_now          INT          NOT NULL DEFAULT 0,
    days_until_deadline INT,
    top_gaps            JSONB        NOT NULL DEFAULT '[]',
    where_you_stand     TEXT,
    focus_next_week     JSONB        NOT NULL DEFAULT '[]',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_weekly_digests_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_weekly_digests_user_week
        UNIQUE (user_id, iso_week)
);

CREATE INDEX idx_weekly_digests_user_generated
    ON weekly_digests (user_id, generated_at DESC);

CREATE TABLE telegram_quiz_polls (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    poll_id     VARCHAR(120) NOT NULL,
    user_id     UUID         NOT NULL,
    chat_id     BIGINT       NOT NULL,
    session_id  UUID         NOT NULL,
    question_id UUID         NOT NULL,
    position    INTEGER      NOT NULL,
    options     JSONB        NOT NULL,
    answered_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_telegram_quiz_polls_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_telegram_quiz_polls_poll_id
        UNIQUE (poll_id)
);
