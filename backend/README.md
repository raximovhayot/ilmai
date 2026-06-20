# Ilm AI — Backend

Spring Boot service for **Ilm AI**, a personal AI learning companion that turns a
user's own materials (PDFs, Word docs, plain text, pasted notes) into a tutor:
it chats, quizzes, detects knowledge gaps, plans study, and nudges via Telegram.
Every assistant answer is grounded in the user's uploaded content and cited back
to the source.

For the big picture across both services, see the [root README](../README.md).

---

## Table of contents

- [Stack](#stack)
- [Prerequisites](#prerequisites)
- [Quick start](#quick-start-local-development)
- [Configuration](#configuration)
- [Project layout](#project-layout)
- [Database & migrations](#database--migrations)
- [Background jobs](#background-jobs)
- [Observability](#observability)
- [Testing](#testing)
- [AI / RAG non-negotiables](#ai--rag-non-negotiables)
- [API smoke test](#api-smoke-test)

---

## Stack

| Layer            | Tech                                                                 |
| ---------------- | -------------------------------------------------------------------- |
| Language         | Java 25 (Gradle toolchain auto-provisions it)                        |
| Framework        | Spring Boot 4.0.x · Spring Modulith 2.0.x (package-by-feature)       |
| AI               | Spring AI 2.0.0-M8 — `ChatClient`, Advisors, JDBC chat memory        |
| Chat model       | Google `gemini-3.1-flash-lite` (swappable via `ChatClient`)          |
| Embeddings       | Google `gemini-embedding-2`, 768-dim (Matryoshka reduction)          |
| Vector store     | `pgvector` (Postgres extension) — 768-dim cosine, HNSW               |
| Persistence      | Postgres 16 + Hibernate (JPA) · Flyway migrations (`validate`)       |
| Mapping          | MapStruct (entity ↔ DTO in the service layer)                        |
| Storage          | S3-compatible object store ([Garage](https://garagehq.deuxfleurs.fr/)) via AWS SDK v2 |
| Security         | Spring Security · OAuth2 resource server · HS256 JWT (Google sign-in)|
| Background jobs  | [JobRunr](https://www.jobrunr.io/) (ingestion retries, reminders, replanning) |
| Doc extraction   | Apache Tika (`spring-ai-tika-document-reader`)                       |
| Streaming        | `ui-message-stream` starter (token + tool streaming to the SPA)      |
| Integrations     | Telegram Bot API · Payme / Click / Stripe (abstraction stubs)        |
| Observability    | Sentry (errors, traces, logs) · Spring Boot Actuator                 |
| Build            | Gradle 9.x · Spring Boot layered `bootJar`                           |

> The exact dependency versions live in [`build.gradle`](build.gradle) — trust it
> over this table.

---

## Prerequisites

- **A JDK on `PATH`** to run the Gradle wrapper. The build's Java 25 toolchain is
  auto-provisioned; you don't need to install Java 25 yourself.
- **Docker + Docker Compose** for dev infra. [`compose.yaml`](compose.yaml) in
  this folder boots Postgres + pgvector and a Garage S3 store (auto-initialised
  with a dev bucket and key).
- **A Google AI Studio API key** (`GOOGLE_GENAI_API_KEY`). Without it the chat and
  embedding paths degrade gracefully — endpoints respond `CHAT_AI_UNAVAILABLE`
  and materials are accepted but not indexed.

Optional, only for the features they belong to: Telegram bot token, Stripe /
Payme / Click keys, Sentry DSN.

---

## Quick start (local development)

```bash
# 1. Bring up dev infra (Postgres + pgvector, Garage S3).
docker compose -f backend/compose.yaml up -d

# 2. Set the bare-minimum env (everything else has a localhost default).
export GOOGLE_GENAI_API_KEY=ai-studio-key-here
export JWT_SECRET=$(openssl rand -hex 64)
export GOOGLE_CLIENT_ID=...apps.googleusercontent.com

# 3. Run.
./gradlew bootRun
```

The first start runs Flyway, creates the schema, and provisions `vector_store`
plus `spring_ai_chat_memory` (the latter via Spring AI's `initialize-schema`).
The app listens on `:8080`.

A full copy of the supported environment variables lives in
[`.env.example`](.env.example).

### Run the JAR

```bash
./gradlew bootJar
java -jar build/libs/ilmai-backend-*.jar
```

### Run with Docker

The image is multi-stage (Eclipse Temurin 25 JDK → JRE), runs as non-root, and
ships a `HEALTHCHECK` against `/actuator/health`. Layered-jar extraction keeps
the heavy `dependencies/` layer cached across rebuilds. In practice the backend
runs as part of the [root `docker-compose.yml`](../docker-compose.yml) stack — see
[`../DEPLOY.md`](../DEPLOY.md).

```bash
docker build -t ilmai-backend:dev backend/
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/mydatabase \
  -e DB_USERNAME=myuser -e DB_PASSWORD=secret \
  -e GOOGLE_GENAI_API_KEY=$GOOGLE_GENAI_API_KEY \
  -e JWT_SECRET=$JWT_SECRET \
  -e GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID \
  -e STORAGE_S3_ENDPOINT=http://host.docker.internal:3900 \
  -e STORAGE_S3_ACCESS_KEY=... -e STORAGE_S3_SECRET_KEY=... \
  ilmai-backend:dev
```

---

## Configuration

Everything is environment-variable driven. The shipped
[`application.yml`](src/main/resources/application.yml) contains only
**local-dev defaults**; production overrides come from the deployment
environment. Real keys never live in the repo.

### Required in prod

| Env var                | What it does                                            |
| ---------------------- | ------------------------------------------------------- |
| `DB_URL`               | JDBC URL for Postgres (must have the `pgvector` extension) |
| `DB_USERNAME`          | Postgres user                                           |
| `DB_PASSWORD`          | Postgres password                                       |
| `JWT_SECRET`           | HS256 signing secret (≥ 64 chars)                       |
| `GOOGLE_CLIENT_ID`     | Google OAuth client id for `POST /auth/google`          |
| `GOOGLE_GENAI_API_KEY` | AI Studio key for chat + embeddings                     |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins for the SPA             |
| `STORAGE_S3_*`         | Bucket + credentials for the object store (see below)   |

### AI / RAG

| Env var                           | Default                                             |
| --------------------------------- | --------------------------------------------------- |
| `GOOGLE_GENAI_CHAT_MODEL`         | `gemini-3.1-flash-lite`                             |
| `GOOGLE_GENAI_EMBEDDING_MODEL`    | `gemini-embedding-2`                                |
| `GOOGLE_GENAI_EMBEDDING_BASE_URL` | `https://generativelanguage.googleapis.com/v1beta`  |
| `RETRIEVAL_TOP_K`                 | `8`                                                 |
| `RETRIEVAL_SIMILARITY_THRESHOLD`  | `0.6`                                               |

Embedding output dimensionality is fixed at **768** (Matryoshka reduction from
the model's native 3072) to match the pgvector schema. The embedding provider is
intentionally **not** swappable by design; only the chat model is.

### Storage (S3 / Garage)

| Env var                        | Default              |
| ------------------------------ | -------------------- |
| `STORAGE_S3_BUCKET`            | `ilmai-materials`    |
| `STORAGE_S3_REGION`            | `us-east-1`          |
| `STORAGE_S3_ENDPOINT`          | *(empty — AWS S3)*   |
| `STORAGE_S3_ACCESS_KEY`        | —                    |
| `STORAGE_S3_SECRET_KEY`        | —                    |
| `STORAGE_S3_PATH_STYLE_ACCESS` | `true`               |

Uploaded materials are written via AWS SDK v2. Point `STORAGE_S3_ENDPOINT` at
your Garage cluster (or any S3-compatible store — AWS S3, MinIO, Cloudflare R2).
Path-style access is on by default because Garage doesn't support
virtual-hosted-style URLs. There is no local-filesystem fallback — the bucket
and credentials are required for materials to be ingested.

### Telegram

`TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `TELEGRAM_WEBHOOK_SECRET`,
`TELEGRAM_PUBLIC_BASE_URL`. An empty `TELEGRAM_BOT_TOKEN` makes the outbound
client a no-op, so reminders fail-closed instead of crashing the scheduler.

### Billing

`STRIPE_*`, `PAYME_*`, `CLICK_*`. The current providers are **abstraction
stubs** — real signature verification and checkout calls are the deployer's
responsibility (see `billing/service/`). `BILLING_TEST_PROVIDER_ENABLED`
toggles a no-real-money test provider (on by default in dev).

---

## Project layout

The codebase is **package-by-feature**, not by layer. Each feature is a
[Spring Modulith](https://spring.io/projects/spring-modulith) module, isolated
behind a public `*Api` at its package root; internals live in `domain/`,
`service/`, `api/`, `payload/` sub-packages.

```
src/main/java/org/aiincubator/ilmai/
├── IlmaiBackendApplication.java
├── ai/                  — custom Gemini REST EmbeddingModel + retrieval API
├── auth/                — signup, Google sign-in, JWT, refresh tokens
├── billing/             — Stripe / Payme / Click abstraction + quotas
├── chat/                — RAG conversations + citations
├── common/              — shared infra (api, config, i18n, payload, quota, storage)
├── gaps/                — knowledge-gap aggregation
├── materials/           — uploads + ingestion + chunking + vector indexing
├── plan/                — day-by-day study planner agent
├── profiles/            — per-user profile (locale, goal, reminder time…)
├── quiz/                — LLM-generated MCQs + scoring
├── spaces/              — per-user workspace (auto-created at signup)
├── telegram/            — link-code flow, webhook, reminder dispatcher
└── topics/              — folders inside a space
```

Cross-module rule: callers depend only on another module's root `*Api` interface
and root `*Event` types — never on its `domain/` / `service/` / `api/` /
`payload/` internals. `ApplicationModulesTests.verifyModules` enforces this in
CI.

Cross-cutting bits in `common/`:

- `common/storage/` — `BlobStorage` interface + `S3BlobStorage` (Garage-friendly).
- `common/quota/` — free-tier vs premium gating; raises `FeatureLockedException`
  → `HTTP 402` when a user hits a limit.
- `common/i18n/` — `SupportedLocale` (UZ / RU / EN) + the `messages*.properties`
  bundle used by every exception handler.

---

## Database & migrations

Schema is **owned by Flyway** (`spring.jpa.hibernate.ddl-auto: validate`).
Migrations live in `src/main/resources/db/migration/` and run in version order at
startup. The auxiliary `spring_ai_chat_memory` table is provisioned by Spring AI
itself (`spring.ai.chat.memory.repository.jdbc.initialize-schema: always`) — not
a Flyway file, because Spring AI owns its lifecycle and dialect.

Add a migration as:

```
V{N}__short_description.sql
```

where `{N}` is the next free integer. **Never** edit an applied migration and
**never** switch `ddl-auto` away from `validate`. Value constraints
(`CHECK (... IN ...)`) are enforced in Java (enums / Bean Validation), not in DDL.

---

## Background jobs

Recurring and retried work runs on **JobRunr** (ingestion retries, learning-plan
re-generation, Telegram reminder dispatch, streak nudges). The background job
server is **off by default** in dev (`JOBRUNR_BACKGROUND_JOB_SERVER_ENABLED=false`)
and **on** in the production compose stack. The JobRunr dashboard is disabled.

---

## Observability

Error and performance monitoring is wired through **Sentry**
(`io.sentry:sentry-spring-boot-4-starter` + Logback appender). The SDK stays
disabled while `SENTRY_DSN` is empty, so local dev needs no setup. Tune with
`SENTRY_ENVIRONMENT` and `SENTRY_TRACES_SAMPLE_RATE`.

Spring Boot Actuator is exposed at:

- `GET /actuator/health` — the Docker healthcheck target (probes enabled)
- `GET /actuator/info`
- `GET /actuator/metrics`

There is intentionally **no application-level LLM-call audit table** — prompts
and responses are covered by Sentry traces/logs.

---

## Testing

```bash
./gradlew test                            # full suite
./gradlew test --tests "*AuthServiceTest" # focused
./gradlew test --tests "*Materials*"      # by package pattern
```

Slice and unit tests run offline. Integration tests (`*IntegrationTest`, the
Spring-context `contextLoads`, and `ApplicationModulesTests`) spin up Postgres
via **Testcontainers** — Docker must be running. JUnit 5 throughout; the test
task caps heap at 2 GB and the Spring context cache at 4.

---

## AI / RAG non-negotiables

Verified by integration tests:

- **User isolation.** Every vector search filters by
  `metadata.user_id == authenticated user id`.
- **Citations.** Assistant responses must reference the chunk(s) they came from;
  the chat client parses these and marks the answer ungrounded if none are found.
- **Tool calling.** The user id is resolved from the security context
  (`CurrentUser`) — never accepted as a tool argument from the model.
- **Swappable LLM.** Providers stay behind Spring AI's `ChatClient` abstraction.

---

## API smoke test

```bash
# Health
curl localhost:8080/actuator/health

# Sign in (needs a real Google id_token from the SPA)
curl -X POST localhost:8080/auth/google \
  -H 'Content-Type: application/json' \
  -d '{"idToken":"…"}'
```

The full API surface lives under each feature's `api/` package — there is no
`springdoc-openapi` integration yet.

---

> **Doc drift note:** [`.env.example`](.env.example) still lists `REDIS_HOST` /
> `REDIS_PORT`, but the current build has no Redis dependency or configuration —
> refresh tokens are stored in Postgres. Treat the Redis entries as stale until
> they're removed.
