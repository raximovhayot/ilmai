# Ilm AI — Backend

Spring Boot service for **Ilm AI**, a personal AI learning companion that turns a
user's own materials (PDFs, Word docs, plain text, pasted notes) into a
tutor: it chats, quizzes, detects knowledge gaps, plans study, and nudges via
Telegram. Every assistant answer is grounded in the user's uploaded content
and cited back to the source.

The authoritative product spec lives at
[`../docs/ilm-ai-project-brief.md`](../docs/ilm-ai-project-brief.md). Conventions
for working in this repo live at [`../AGENTS.md`](../AGENTS.md).

---

## Stack

| Layer            | Tech                                                                 |
| ---------------- | -------------------------------------------------------------------- |
| Language         | Java 25 (toolchain)                                                  |
| Framework        | Spring Boot 4.0.x                                                    |
| AI               | Spring AI 2.0.0-M6 (chat + advisors + chat-memory JDBC)              |
| Embeddings       | Google `gemini-embedding-2` via custom REST `EmbeddingModel`         |
| Chat models      | Google `gemini-2.0-flash-001` (swappable through `ChatClient`)       |
| Vector store     | `pgvector` (Postgres extension), 768-dim cosine, HNSW                |
| Persistence      | Postgres 16 + Hibernate (JPA), Flyway migrations                     |
| Cache / refresh  | Redis 7                                                              |
| Storage          | S3-compatible object store (Garage)                                  |
| Security         | Spring Security 6 + OAuth2 resource server (Google sign-in)          |
| Observability    | OpenTelemetry → Grafana LGTM stack (logs, metrics, traces)           |
| Build            | Gradle 9.x + Spring Boot bootJar (layered)                           |

---

## Prerequisites

- **Java 25** (the Gradle toolchain will fetch it; you only need a JDK on `PATH`
  to run the wrapper, the build will auto-provision the right version).
- **Docker + Docker Compose** for the dev infra (Postgres + pgvector, Redis,
  Grafana LGTM). `compose.yaml` in this folder boots all three.
- **A Google AI Studio API key** (`GOOGLE_GENAI_API_KEY`). Without it the chat
  and embedding code paths degrade gracefully — endpoints respond with
  `CHAT_AI_UNAVAILABLE` and materials are accepted but not indexed.

- **An S3-compatible object store** for uploaded materials. Production target
  is [Garage](https://garagehq.deuxfleurs.fr/) (lightweight, S3-compatible,
  low memory footprint); any other S3-compatible store (AWS S3, MinIO,
  Cloudflare R2, …) works too.

Optional, only needed for the features they belong to: Telegram bot token,
Stripe / Payme / Click keys.

---

## Quick start (local development)

```bash
# 1. Bring up dev infra (Postgres+pgvector, Redis, Grafana LGTM).
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
The app listens on `:8080`. Grafana lands on `:3001`.

### Run the JAR

```bash
./gradlew bootJar
java -jar build/libs/ilmai-backend-*.jar
```

### Run with Docker

```bash
docker build -t ilmai-backend:dev backend/
docker run --rm -p 8080:8080 \
  --network ilmai_default \
  -e DB_URL=jdbc:postgresql://pgvector:5432/mydatabase \
  -e DB_USERNAME=myuser -e DB_PASSWORD=secret \
  -e REDIS_HOST=redis -e REDIS_PORT=6379 \
  -e GOOGLE_GENAI_API_KEY=$GOOGLE_GENAI_API_KEY \
  -e JWT_SECRET=$JWT_SECRET \
  -e GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID \
  -e STORAGE_S3_ENDPOINT=http://garage:3900 \
  -e STORAGE_S3_BUCKET=ilmai-materials \
  -e STORAGE_S3_ACCESS_KEY=... -e STORAGE_S3_SECRET_KEY=... \
  ilmai-backend:dev
```

The image is multi-stage (Eclipse Temurin 25 JDK → JRE), runs as non-root
(`ilmai`, uid 1000), and ships a `HEALTHCHECK` against `/actuator/health`.
Spring Boot layered-jar extraction is used so the heaviest layer
(`dependencies/`) is cached across rebuilds.

---

## Configuration

Everything is environment-variable driven. The shipped `application.yml`
contains only **local-dev defaults**; production overrides come from the
deployment environment.

### Required in prod

| Env var               | What it does                                                  |
| --------------------- | ------------------------------------------------------------- |
| `DB_URL`              | JDBC URL for Postgres (must have `pgvector` extension)        |
| `DB_USERNAME`         | Postgres user                                                 |
| `DB_PASSWORD`         | Postgres password                                             |
| `REDIS_HOST`          | Redis host (refresh-token store)                              |
| `REDIS_PORT`          | Redis port                                                    |
| `JWT_SECRET`          | HS256 signing secret (>= 64 chars)                            |
| `GOOGLE_CLIENT_ID`    | Google OAuth client id for `POST /auth/google`                |
| `GOOGLE_GENAI_API_KEY`| AI Studio key for chat + embeddings                           |
| `CORS_ALLOWED_ORIGINS`| Comma-separated allowed origins for the SPA                   |

### AI / RAG

| Env var                              | Default                                          |
| ------------------------------------ | ------------------------------------------------ |
| `GOOGLE_GENAI_CHAT_MODEL`            | `gemini-2.0-flash-001`                           |
| `GOOGLE_GENAI_EMBEDDING_MODEL`       | `gemini-embedding-2`                             |
| `GOOGLE_GENAI_EMBEDDING_BASE_URL`    | `https://generativelanguage.googleapis.com/v1beta` |

Embedding output dimensionality is fixed at **768** (Matryoshka reduction from
the model's native 3072) to fit the pgvector schema.

### Storage (S3 / Garage)

| Env var                       | Default              |
| ----------------------------- | -------------------- |
| `STORAGE_S3_BUCKET`           | `ilmai-materials`    |
| `STORAGE_S3_REGION`           | `us-east-1`          |
| `STORAGE_S3_ENDPOINT`         | *(empty — AWS S3)*   |
| `STORAGE_S3_ACCESS_KEY`       | —                    |
| `STORAGE_S3_SECRET_KEY`       | —                    |
| `STORAGE_S3_PATH_STYLE_ACCESS`| `true`               |

Uploaded materials are written to an S3-compatible object store via the AWS
SDK v2. Point `STORAGE_S3_ENDPOINT` at your Garage cluster (or any
S3-compatible store — AWS S3, MinIO, Cloudflare R2). Path-style access is on
by default because Garage doesn't support virtual-hosted-style URLs. Region
can be any string Garage accepts; the AWS SDK only uses it for signing.
There is no local-filesystem fallback — the bucket and credentials are
required for materials to be ingested.

### Telegram

`TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `TELEGRAM_WEBHOOK_SECRET`.
Empty `TELEGRAM_BOT_TOKEN` makes the outbound Telegram client a no-op so
reminders silently fail-closed instead of crashing the scheduler.

### Billing

`STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `PAYME_MERCHANT_ID`,
`PAYME_SECRET_KEY`, `CLICK_MERCHANT_ID`, `CLICK_SERVICE_ID`,
`CLICK_SECRET_KEY`. The current providers are **abstraction stubs** —
real signature verification and checkout calls are the deployer's
responsibility (see `billing/service/PaymentProvider.java`).

---

## Database schema

Schema is **owned by Flyway** (`spring.jpa.hibernate.ddl-auto: validate`).
Migrations live in `src/main/resources/db/migration/` and are applied
in version order at startup. The auxiliary table `spring_ai_chat_memory`
used by `MessageChatMemoryAdvisor` is provisioned at startup by Spring AI
(`spring.ai.chat.memory.repository.jdbc.initialize-schema: always`) — it is
**not** a Flyway file because Spring AI owns its lifecycle and dialect.

Adding a migration:

```
V{N}__short_description.sql
```

Where `{N}` is the next free integer. **Never** edit an applied migration and
**never** switch `ddl-auto` away from `validate`.

---

## Observability

The app is wired for the **Grafana LGTM** stack via Spring Boot's
`spring-boot-starter-opentelemetry`. The dev `compose.yaml` boots
`grafana/otel-lgtm:latest` on port `3001` and exposes OTLP receivers on
`4317` (gRPC) and `4318` (HTTP) — no extra wiring needed; logs, metrics, and
traces flow there automatically.

Spring Boot Actuator is on at:

- `GET /actuator/health` — the Docker healthcheck target
- `GET /actuator/info`
- `GET /actuator/prometheus` — Micrometer scrape endpoint

There is intentionally **no application-level LLM-call audit table** — every
prompt/response is already covered by the OTel traces and Grafana logs.

---

## Project layout

The codebase is organised **package-by-feature**, not by layer. Inside each
feature use sub-packages `domain/`, `service/`, `api/`, `payload/`.

```
src/main/java/org/aiincubator/ilmai/
├── IlmaiBackendApplication.java
├── ai/embedding/        — custom Gemini REST EmbeddingModel
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

### Cross-cutting modules

- `common/storage/` — `BlobStorage` interface + `S3BlobStorage`
  (Garage-friendly, AWS SDK v2, path-style URLs).
- `common/quota/` — free-tier vs premium gating, raises `FeatureLockedException`
  → `HTTP 402` when the user hits a limit.
- `common/i18n/` — `SupportedLocale` (UZ / RU / EN) + the `messages*.properties`
  bundle used by every `*ExceptionHandler`.

---

## Tests

```bash
./gradlew test                            # full suite
./gradlew test --tests "*AuthServiceTest" # focused
./gradlew test --tests "*Materials*"      # by package pattern
```

`IlmaiBackendApplicationTests.contextLoads` runs a full Spring context and
therefore needs Postgres on `localhost:5432` (Flyway connects at startup).
`docker compose -f backend/compose.yaml up -d pgvector` covers that.

All other suites are pure unit / slice tests and run offline.

---

## AI / RAG non-negotiables

Pulled from `../AGENTS.md` §5 — verified by integration tests:

- **User isolation.** Every vector search filters by
  `metadata.user_id == authenticated user id` (`FilterExpressionBuilder.eq("user_id", …)`).
- **Citations.** Assistant responses must reference the chunk(s) they came from
  using the `[#<material_id>]` pattern; the chat client parses these and sets
  `grounded=false` if none are found.
- **Tool calling.** The user id is resolved from the security context
  (`CurrentUser`) — never accepted as a tool argument from the model.
- **LLM strategy.** Providers stay swappable through Spring AI's
  `ChatClient` abstraction; the wrapper is `chat/service/IlmaiChatClient.java`.

---

## Useful endpoints (manual smoke test)

```bash
# Health
curl localhost:8080/actuator/health

# Sign in (after wiring the SPA → backend with a real Google id_token)
curl -X POST localhost:8080/auth/google \
  -H 'Content-Type: application/json' \
  -d '{"idToken":"…"}'
```

The full API surface is large — see the controllers under `*/api/` for the
canonical list. There is no `springdoc-openapi` integration yet.
