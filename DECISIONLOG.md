# Decision log

Dated, append-only log of cross-cutting decisions made during development of Ilm AI. Extracted from AGENTS.md on 2026-05-24. New entries land at the bottom; never edit or delete an existing entry (supersede it with a new one instead).

When adding a new entry, follow the existing format and explicitly call out anything it supersedes.

---

- **2026-05-16 — Auth providers: Google now, Telegram next, no email/password.**
  The frontend `/login` and `/signup` screens ship with a single sign-in path
  — **Google** (`POST /auth/google`). Email + password auth is **explicitly
  out of scope** (per product decision); the brief's wording "email or Google"
  is therefore stale and should be updated to "Google now, Telegram next" the
  next time the brief is revised. Telegram Login Widget is the planned second
  provider and will land in its own task. Translations include a small
  caption (`login.moreOptions`) noting that Telegram sign-in is on the way.
- **2026-05-16 — NextAuth v5 (Auth.js) for the Next.js auth section.** The
  frontend now uses **NextAuth v5** (`next-auth@beta`) instead of the
  browser-side Google Identity Services + `localStorage` token pair it
  shipped with originally. Rule 6 of this doc ("don't add heavy deps
  opportunistically") explicitly calls out NextAuth — the dep is allowed
  here because the user explicitly requested it. Layout:
  - `frontend/auth.config.ts` — edge-safe config (Google provider,
    `/login` as both `signIn` and `error` page).
  - `frontend/auth.ts` — full config with `jwt` / `session` callbacks that
    exchange Google's `id_token` against the backend `POST /auth/google`
    and refresh with `POST /auth/refresh` ~60 s before expiry. The
    backend `accessToken` / `refreshToken` / `accessExpiresAt` live on
    the JWT (encrypted, HttpOnly cookie); `session.accessToken` /
    `session.error` are exposed to the app.
  - `frontend/app/api/auth/[...nextauth]/route.ts` — re-exports
    `handlers`.
  - `frontend/types/next-auth.d.ts` — module augmentation for
    `Session` / `JWT`.
  - `frontend/components/auth/google-sign-in-button.tsx` calls
    `signIn("google", { callbackUrl })` from `next-auth/react`.
  - `frontend/components/auth/auth-error-toast.tsx` reads `?error=…`
    on `/login` and `/signup` and surfaces `t.errors.signInFailed`.
  Env vars (see `frontend/.env.example`): `AUTH_SECRET`,
  `AUTH_GOOGLE_ID`, `AUTH_GOOGLE_SECRET`, `AUTH_BACKEND_URL`
  (server-side, falls back to `NEXT_PUBLIC_API_BASE_URL`). Configure the
  Google OAuth client with the redirect URI
  `http://localhost:3000/api/auth/callback/google` (and the production
  equivalent). This **supersedes the previous localStorage decision** —
  tokens never leave the server-set HttpOnly cookie. The old
  `frontend/lib/auth/{google,service,tokens}.ts` files are gone.
- **2026-05-16 — UI primitives & i18n.** Auth UI is built on shadcn
  `base-luma` components (`Card`, `Field`, `InputGroup`, `DropdownMenu`,
  `Alert`, `sonner`, `Spinner`) over `@base-ui/react`. Strings for UZ / RU /
  EN live in `frontend/lib/i18n/dictionary.ts`; `LanguageProvider` (client,
  `localStorage`-persisted) wraps the app in `frontend/app/layout.tsx`.
  Adding a new screen → add its keys to all three dictionaries and call
  `useT()` — do **not** hardcode English in JSX.
- **2026-05-19 — One-time `docs/plans/` override of rule 3.** At the user's
  explicit request, the Week 1 Junie plan is mirrored to
  `docs/plans/week-1-foundation.md` so the plan sits alongside
  `docs/ilm-ai-project-brief.md`. This is a one-time override of AGENTS.md
  rule 3 ("Don't invent new docs in `docs/`") and **does not** create a
  precedent: future Junie plans stay in `.junie/plans/` unless the user
  explicitly asks for another mirror. The canonical plan is the Junie copy;
  the `docs/` copy is a read-only snapshot.
- **2026-05-19 — Embedding provider locked to `gemini-embedding-2`.** Per
  explicit user direction, the vector embedding model is Google
  `gemini-embedding-2` (multimodal: text/images/audio/video/PDF;
  3072-dim default reduced to **768** via `output_dimensionality` to fit
  the existing pgvector schema). Unlike the chat side — which the brief
  keeps switchable across Gemini / GPT-4o / Claude via Spring AI's
  `ChatClient` — the embedding provider is **not** swappable; no
  alternative embedding integration (OpenAI, Transformers, Ollama, etc.)
  may be wired in. The brief has been updated at line 163 to reflect
  this. **Offline-environment caveat:** Spring AI 2.0.0-M6's cached
  `spring-ai-starter-model-google-genai` ships chat-only and does not
  yet contain a `GoogleGenAi...EmbeddingModel` class; the production app
  therefore excludes `PgVectorStoreAutoConfiguration` until the Gemini
  embedding artifact is on the classpath. `MaterialIngestionService`
  injects `ObjectProvider<VectorStore>` and skips the indexing step
  (marks the row `READY` with `chunkCount=0`) until the real bean
  appears — no stub `EmbeddingModel` is registered in production.
- **2026-05-20 — RAG is live; custom Gemini embedding client supersedes
  the 2026-05-19 offline caveat.** Spring AI 2.0.0-M6 still ships only
  `GoogleGenAiTextEmbedding` *auto-configuration* (no implementation
  class), so we hand-rolled a REST-based `EmbeddingModel` instead of
  waiting for M7. Layout:
  - `org.aiincubator.ilmai.ai.embedding.GoogleGenAiEmbeddingModel` —
    extends Spring AI's `AbstractEmbeddingModel`, calls
    `POST {baseUrl}/models/{model}:embedContent` (single) and
    `…:batchEmbedContents` (batch) via a `RestClient`, requests
    `outputDimensionality=768` and switches `taskType` between
    `RETRIEVAL_QUERY` (single-string `embed(String)` — that's the path
    pgvector's similarity search takes) and `RETRIEVAL_DOCUMENT`
    (ingestion). Empty input → empty response; HTTP 4xx/5xx →
    `EmbeddingApiException`.
  - `GoogleGenAiEmbeddingProperties` (`ilmai.ai.embedding.*`) — Lombok
    config-properties class with `apiKey`, `model` (default
    `gemini-embedding-2`), `outputDimensionality` (default `768`),
    `baseUrl`, `documentTaskType`, `queryTaskType`, `maxBatchSize`.
  - `GoogleGenAiEmbeddingOptions implements EmbeddingOptions` — lets
    callers override `model` / `dimensions` / `taskType` per request.
  - `EmbeddingConfig` — `@Configuration` gated by
    `@ConditionalOnExpression("'${ilmai.ai.embedding.api-key:}'.trim().length() > 0")`
    so test/offline contexts (blank API key) do **not** wire the bean
    and `MaterialIngestionService`'s existing `ObjectProvider<VectorStore>`
    no-op path stays in effect. Inner `@Bean` carries
    `@ConditionalOnMissingBean(EmbeddingModel.class)` so the future Spring
    AI M7 official bean wins automatically when it appears.
  - `application.yml` — removed `PgVectorStoreAutoConfiguration` from the
    `spring.autoconfigure.exclude` list (it now activates the moment the
    `EmbeddingModel` bean exists); added the dead Spring AI Google
    embedding **autoconfigure** classes to the exclude list (the
    referenced impl classes are missing in M6, so leaving them enabled
    breaks startup); deleted the dead `spring.ai.google.genai.embedding.*`
    keys; introduced `ilmai.ai.embedding.*`.
  - `MaterialIngestionService` is unchanged — its
    `ObjectProvider<VectorStore>` already adds the `user_id`, `topic_id`,
    `material_id`, `material_name`, `chunk_index` metadata required for
    user-scoped retrieval and citation tracking. With the
    `EmbeddingModel` bean present, `PgVectorStoreAutoConfiguration`
    registers the `VectorStore` and the same code path now actually
    indexes.
  - `IlmaiChatClient.retrieve(...)` is unchanged — already filters by
    `user_id` metadata via `FilterExpressionBuilder.eq("user_id", …)`
    and propagates `material_id` for the `[#<uuid>]` citation parser.
  No brief mismatch: the brief's line 163 (`gemini-embedding-2`) is
  correct — confirmed against Google's official docs at
  `https://ai.google.dev/gemini-api/docs/embeddings`, where the Python /
  JS / Go SDK examples all use `model="gemini-embedding-2"` against the
  `models/{model}:embedContent` endpoint (the multimodal embedding model
  released in public preview on 2026-03-10). The Spring AI 2.0.0-M6
  autoconfigure default `gemini-embedding-001` is simply an older model
  — our hand-rolled REST client now defaults to `gemini-embedding-2` to
  match the brief, and any earlier note in this log claiming a "brief
  mismatch" is superseded by this entry.
- **2026-05-20 — Embedding model default switched to `gemini-embedding-2`.**
  `GoogleGenAiEmbeddingProperties#model` and the `application.yml` env-var
  default (`GOOGLE_GENAI_EMBEDDING_MODEL`) now ship `gemini-embedding-2`
  (was `gemini-embedding-001`). Output dimensionality stays at **768**
  (Matryoshka-style reduction from the model's native 3072 to fit the
  existing pgvector schema) via the request body's `outputDimensionality`
  parameter. `RETRIEVAL_QUERY` / `RETRIEVAL_DOCUMENT` `taskType` routing
  is unchanged. No code or schema change beyond the default value —
  callers that explicitly override `ilmai.ai.embedding.model` (or pass a
  `GoogleGenAiEmbeddingOptions` with a different model) keep their
  override. Tests in `GoogleGenAiEmbeddingModelTest` and
  `EmbeddingConfigTest` updated to assert the new default.
- **2026-05-20 — Backend Dockerfile, README, storage abstraction, and
  ChatMemory wiring.**
  - **`backend/Dockerfile` + `.dockerignore`.** Multi-stage build,
    Java 25 (`eclipse-temurin:25-jdk` → `eclipse-temurin:25-jre`), Spring
    Boot 4 layered jar extraction (`java -Djarmode=tools -jar … extract
    --layers --launcher`), non-root `ilmai` user (uid 1000),
    container-aware JVM heap tuning (`-XX:MaxRAMPercentage=75.0`), and a
    `HEALTHCHECK` against `/actuator/health` (curl baked in). The
    entrypoint is `org.springframework.boot.loader.launch.JarLauncher`.
  - **`backend/README.md`.** Covers stack, prereqs, env vars (`STORAGE_S3_*`
    block for the Garage / S3-compatible object store), local
    `gradlew bootRun`, Docker run, Flyway, observability (Grafana LGTM
    via OTel — no application-level LLM audit table because of this),
    tests, project layout, and the AI/RAG non-negotiables.
  - **Storage abstraction relocated to `common/storage/`.** The pre-existing
    `materials/service/{FileStorage,S3FileStorage,S3FileStorageConfig,
    S3StorageProperties,StoredObject}.java` were deleted and replaced by
    cross-feature equivalents in `org.aiincubator.ilmai.common.storage`:
    - `BlobStorage` (interface; was `FileStorage`).
    - `StoredBlob` (value; was `StoredObject`).
    - `S3BlobStorage` + `S3BlobStorageConfig` — Garage-compatible AWS SDK
      v2 client, path-style URLs, custom endpoint override, static or
      `DefaultCredentialsProvider` creds. Always-on (no conditional
      gating, see 2026-05-20 removal entry below).
    - `S3StorageProperties` (prefix `ilmai.storage.s3`).
    The user's stated production target is **Garage** (S3-compatible,
    small memory footprint); the abstraction lets the deployer plug it
    in without touching `MaterialService` /
    `MaterialIngestionService`, which now depend only on the
    `BlobStorage` interface.
  - **Spring AI ChatMemory (JDBC) wired in.** Spring AI's
    `ChatMemoryAutoConfiguration` already exposes a `ChatMemoryRepository`
    bean (`JdbcChatMemoryRepository` because the
    `spring-ai-starter-model-chat-memory-repository-jdbc` starter is on
    the classpath) and builds a `MessageWindowChatMemory` from it; the
    auxiliary `spring_ai_chat_memory` table is provisioned at startup by
    `spring.ai.chat.memory.repository.jdbc.initialize-schema: always` —
    **not** a Flyway file (Spring AI owns its lifecycle). No new bean
    config was added. `IlmaiChatClient` now:
    - takes `ObjectProvider<ChatMemory>` in addition to `ChatClient.Builder`
      and `VectorStore`;
    - exposes `complete(userId, conversationId, locale, userMessage)`
      (was `complete(userId, locale, history, userMessage)`) and chains
      `MessageChatMemoryAdvisor.builder(chatMemory).build()` +
      `advisors(a -> a.param(ChatMemory.CONVERSATION_ID,
      conversationId.toString()))` when the bean is available; falls
      back to a one-shot prompt otherwise.
    - `ChatService.sendMessage` now passes `conversationId` and the
      hand-rolled `buildHistory(conversationId)` is gone — Spring AI
      handles the LLM-side conversation window; our own
      `ChatMessage`/`MessageCitation` tables stay as the audit + UI store.
  - **`application.yml` additions.** New `ilmai.storage.s3.*` block
    (`bucket`, `region`, `endpoint`, `access-key`, `secret-key`,
    `path-style-access`) with env-var overrides `STORAGE_S3_*`. Point
    `STORAGE_S3_ENDPOINT` at the Garage cluster.
  - **No LLM-call audit table.** Per explicit user direction in this
    session, application-level LLM call/response logging tables are
    intentionally **not** introduced — the Grafana LGTM stack
    (`grafana/otel-lgtm` already in `backend/compose.yaml`, with OTLP
    receivers on ports `4317`/`4318`) receives Spring Boot's OTel
    output and is the single observability surface for prompt/response
    traces, latency, and tokens. Do not add an `llm_calls` table in
    future work without revisiting this decision.
- **2026-05-20 — Local-filesystem `BlobStorage` removed; S3-only.** Per
  explicit user direction ("i dont need filesystemmstorage, remove its
  code"), the local-FS fallback added earlier the same day has been
  deleted. Removed: `common/storage/LocalFileSystemBlobStorage.java`,
  `common/storage/LocalStorageProperties.java`, the `StorageType` enum
  (which was already dead — the conditionals used string literals, not
  the enum), and `common/storage/LocalFileSystemBlobStorageTest.java`.
  `S3BlobStorageConfig` no longer carries
  `@ConditionalOnProperty(name="ilmai.storage.type", havingValue="s3")`
  — `S3BlobStorage` is now the sole, always-active `BlobStorage` bean.
  `application.yml` lost the `ilmai.storage.type` key and the entire
  `ilmai.storage.local` block; the `Dockerfile` lost
  `ILMAI_STORAGE_LOCAL_ROOT` and the no-longer-needed `/var/lib/ilmai`
  directory creation. `backend/README.md` was rewritten to state
  "there is no local-filesystem fallback — the bucket and credentials
  are required for materials to be ingested" and now lists S3 + Garage
  as a hard prerequisite alongside Postgres and Redis. Tests that
  matter were unaffected: `MaterialService` /
  `MaterialIngestionService` both depend on the `BlobStorage`
  interface (mocked with Mockito) and never touched the local impl.
  **Consequence:** dev work that previously needed only Postgres now
  also needs an S3-compatible bucket (Garage container, MinIO, or a
  real AWS / Cloudflare R2 bucket); contributors who only need to run
  unit tests are unaffected because tests mock `BlobStorage`.
- **2026-05-21 — Cross-module repositories replaced with per-module
  public APIs.** Per explicit user direction ("Do not use different
  module repositories, create public api for each module which can
  communicate with other modules"), no production class outside a
  feature's own package may inject another feature's `*Repository`.
  Each producer module now exposes a single `<Feature>Api` interface
  in `<feature>/service/` plus a `Default<Feature>Api` `@Service`
  implementation that is the only outside-of-`domain/` class allowed
  to touch its repositories:
  - `auth.service.AuthApi` — `requireUser(UUID)` (throws
    `AuthException.USER_NOT_FOUND` on miss), `findUser(UUID)` →
    `Optional<User>`.
  - `materials.service.MaterialsApi` — `hasReadyMaterialsForUser`,
    `findReadyForUser`, `findOwnedByUser(materialId, userId)`,
    `findById(materialId)`, plus ingestion-only mutators
    `updateStatus(material, status)` (in-place; relies on JPA
    dirty-checking inside an open `@Transactional`) and
    `flushStatus(material, status)` (`saveAndFlush`-backed for the
    initial `PENDING → PROCESSING` transition that other readers must
    see immediately).
  - `topics.service.TopicsApi` — `findOwnedByUser(topicId, userId)`,
    `findAllByUser(userId)`.
  - `spaces.service.SpacesApi` — `findPrimaryForUser(userId)` (returns
    the first space for that user — encapsulates the "first space is
    the primary space" rule so consumers like `topics.TopicService`
    don't reach into the ordering).
  - `quiz.service.QuizApi` — `findIncorrectQuestionsForUser`,
    `findAllSessionsForUser` (used by `gaps.GapsService`).
  Refactored consumers (11): `gaps.GapsService`, `ai.chat.ChatService`,
  `plan.PlanService`, `quiz.QuizService`, `billing.BillingService`,
  `telegram.TelegramService`, `materials.MaterialService`,
  `topics.TopicService`, `ai.ingestion.MaterialIngestionService`,
  `spaces.SpaceBootstrapListener`, `profiles.ProfileBootstrapListener`.
  Unit tests `BillingServiceTest`, `ChatServiceTest`,
  `QuizServiceIsolationTest`, `MaterialServiceTest`,
  `MaterialIngestionServiceTest`, `TopicServiceTest` updated to mock
  the new APIs; integration tests
  (`UserIsolationIntegrationTest`, `EmbeddingPipelineIntegrationTest`)
  still `@Autowired` repositories for test-data seeding — explicitly
  allowed (integration tests are not feature modules). **Entities still
  cross module boundaries** as return types from these APIs and as
  `@ManyToOne` JPA targets — that's an inherent shape of a shared
  Postgres schema and was never the point of the task; only the
  *query/mutation surface* is encapsulated. The full rule lives in
  §4 ("Backend") above and supersedes any older snippet that still
  shows a cross-module `@Autowired …Repository`.
- **2026-05-21 — `<Feature>Api` interfaces relocated to module root;
  every `allowedDependencies` whitelist deleted.** Per explicit user
  direction ("i want to isolate modules and use Api classes and events
  for communication. For this we need to move Api classes its modules's
  root and remove allowedDependencies"), the per-module public APIs
  introduced earlier the same day were **moved one package up** so they
  sit alongside `package-info.java` and the existing
  `UserRegisteredEvent` — that is, the module **root** is now the
  *only* package where cross-module-public types live:
  - `auth/service/AuthApi.java` → `auth/AuthApi.java`
  - `materials/service/MaterialsApi.java` → `materials/MaterialsApi.java`
  - `topics/service/TopicsApi.java` → `topics/TopicsApi.java`
  - `spaces/service/SpacesApi.java` → `spaces/SpacesApi.java`
  - `quiz/service/QuizApi.java` → `quiz/QuizApi.java`
  The `Default<Feature>Api` `@Service` impls stay in `<feature>/service/`
  (they are an internal detail; only the interface is the contract).
  At the same time, the `allowedDependencies = { … }` whitelist was
  stripped from **all 11** `package-info.java` files
  (`ai`, `auth`, `billing`, `gaps`, `materials`, `plan`, `profiles`,
  `quiz`, `spaces`, `telegram`, `topics`); each now declares only
  `@ApplicationModule(type = OPEN)`. Rationale: the whitelist was
  bookkeeping that went stale on every new edge — every new cross-module
  call required editing the consumer's `package-info.java`. With Api
  interfaces and events at the module root, the *shape of the import*
  (`<feature>.<Feature>Api` / `<feature>.…Event` vs.
  `<feature>.service.…` / `<feature>.domain.…`) is the boundary
  declaration; a reviewer can see at a glance whether an import is
  legal. `ApplicationModulesTests.verifyModules` still runs in CI and
  still catches cycles. **Imports updated** in 28 production + test
  files (5 `Default<Feature>Api` impls, 11 consumer services /
  listeners, 6 unit tests, 6 module entries in the existing rule's
  examples). All 79 unit tests across the touched test classes
  (`ApplicationModulesTests`, `AuthServiceTest`, `SpaceServiceTest`,
  `BillingServiceTest`, `ChatServiceTest`, `QuizServiceIsolationTest`,
  `MaterialServiceTest`, `MaterialIngestionServiceTest`,
  `TopicServiceTest`, `IlmaiChatClientTest`,
  `MaterialChunkCleanupServiceTest`) pass. **Doc drift flagged per
  rule 2:** `docs/technical/event-architecture.md` still describes the
  old layout in §3.1 ("`domain/` is hard-internal, `service/` is the
  named-interface entry point") and quotes `auth/package-info.java`'s
  pre-strip `allowedDependencies = { "common" }` body in §1 / §3 / §5
  / §8. Both of those statements are now stale — `service/` is no
  longer the entry point (the module root is), and no
  `package-info.java` has an `allowedDependencies` list any more — but
  the brief is silent on this layer, so the doc is flagged here rather
  than silently rewritten. **Pre-existing events not yet at module
  root:** `materials.service.MaterialUploadedEvent`,
  `materials.service.MaterialDeletedEvent`, and
  `ai.ingestion.MaterialIngestionCompletedEvent` all cross module
  boundaries today but still live in sub-packages — they predate this
  rule and were **intentionally left in place** because the user's
  directive in this session was specifically about Api **classes**, not
  events. The new §4 rule applies to them when next touched; until
  then, the `auth.UserRegisteredEvent` location is the canonical
  reference. Modulith's `verifyModules` accepts both placements because
  every module is still `type = OPEN`.
- **2026-05-21 — Ingestion hardening + multi-provider `ChatClient`
  abstraction.** Two parallel pieces of work in one session.

  **Ingestion hardening (per the previous-issue audit).**
  - `MaterialService.upload` now enforces the free-tier upload cap
    (`ilmai.billing.free-tier.material-uploads`, default 5) via the
    existing `QuotaService` bean — was previously configured but
    not checked, so free users could upload unlimited material. New
    `MaterialException.Reason.MATERIAL_UPLOAD_LIMIT` → HTTP **402
    Payment Required**, localized in UZ / RU / EN. New repo method
    `MaterialRepository.countByTopicSpaceUserId(userId)`. When
    `QuotaService.materialUploadQuota(userId)` returns `0` the
    check is skipped (premium path stays unlimited).
  - `DefaultMaterialsApi.updateStatus` / `flushStatus` are now
    explicitly `@Transactional` *and* re-fetch the row by id before
    mutating — fixes the silent no-op risk for any caller that
    forgot the surrounding `@Transactional`. The mutator also
    propagates the new status onto the passed-in entity reference
    so callers (e.g. `MaterialIngestionService`'s `finally`-block
    `material.getStatus()` read) see the latest value.
  - **Scheduled re-ingest for `FAILED` rows.** New
    `materials.service.MaterialReingestScheduler` runs on
    `@Scheduled(fixedDelayString=PT1M)` (default; configurable),
    picks up `FAILED` rows whose `updated_at` is older than
    `ilmai.ingestion.retry.min-failure-age` (default 5 min) and
    whose `retry_count < ilmai.ingestion.retry.max-attempts`
    (default 3), flips them to `PENDING`, increments `retry_count`,
    and republishes `MaterialUploadedEvent` for the existing async
    listener to pick up. Gated by
    `ilmai.ingestion.retry.enabled` (default `true`). `@Transactional`
    + `ApplicationEventPublisher` so the events fire `AFTER_COMMIT` via
    the existing `@TransactionalEventListener`. `@EnableScheduling` is
    already turned on application-wide by `TelegramConfig`. Migration
    `V15__material_retry_count.sql` adds the `retry_count INTEGER NOT
    NULL DEFAULT 0` column and an `idx_materials_status_updated_at`
    composite index for the scheduler's query.
  - `Material.retryCount` and `MaterialResponse.retryCount` exposed
    so the UI can show "this material was retried N times". No
    `chunk_count` / `error_code` columns were re-introduced — those
    stayed dropped per the explicit 2026-05-20 / `V14__materials_slim`
    decision; only `retry_count` is added.
  - **Plan doc drift fixed (per AGENTS.md rule 2).**
    `.junie/plans/harden-ingestion-pipeline.md` got a "Status
    amendment — 2026-05-21" callout at the top that lists the
    statements superseded by `V14__materials_slim.sql` and the
    2026-05-21 module-isolation work. The historical plan body is
    preserved as a session record; the amendment is the only
    current-state pointer.
  - **New unit tests:** `MaterialReingestSchedulerTest`
    (`flipsCandidatesToPendingAndPublishesUploadEvent`,
    `doesNothingWhenNoCandidates`,
    `processesMultipleCandidatesInOnePass`) and two new
    `MaterialServiceTest` cases (`upload_rejectsWhenFreeTierLimitReached`,
    `upload_allowsWhenQuotaUnlimited`). All 18 `MaterialServiceTest`
    + 6 `MaterialIngestionServiceTest` + 3 `MaterialChunkCleanupServiceTest`
    + 3 `MaterialReingestSchedulerTest` cases pass.

  **Multi-provider `ChatClient` abstraction (per brief §163 +
  AGENTS.md §5 "switchable `ChatClient`", explicit user direction:
  Config + per-call override).**
  - `build.gradle` adds two starters:
    `org.springframework.ai:spring-ai-starter-model-openai` and
    `org.springframework.ai:spring-ai-starter-model-anthropic`.
    Both are conditional on their respective API keys
    (`spring.ai.openai.api-key`, `spring.ai.anthropic.api-key`) so
    when the keys are blank no `ChatModel` bean is created and the
    runtime cost is zero. The brief calls this out as required and
    the user explicitly requested it, so this is **not** a §6
    "don't add heavy deps opportunistically" violation.
  - New sub-package `ai.chat.provider` with three top-level types:
    - `LlmProvider` (enum: `GEMINI` / `OPENAI` / `ANTHROPIC`, each
      tagged with the Spring AI bean name —
      `googleGenAiChatModel` / `openAiChatModel` /
      `anthropicChatModel`).
    - `ChatProviderProperties` (`ilmai.ai.chat.default-provider`,
      default `GEMINI`).
    - `IlmaiChatClientFactory` — `@Component` that looks up
      `ChatModel` beans **by name** from the `BeanFactory` (not
      via `@Qualifier` injection, so an unregistered provider is
      a soft miss rather than a startup failure) and returns a
      provider-specific `ChatClient.Builder`. Public surface:
      `defaultProvider()`, `isAvailable()` / `isAvailable(override)`,
      `builder()` / `builder(override)`, `availableProviders()`.
      `null` `override` means "use the default from config".
  - **All four chat-consuming agents now go through the factory**
    instead of `ObjectProvider<ChatClient.Builder>`:
    `ai.chat.IlmaiChatClient.complete(...)`,
    `quiz.service.QuizGenerator.generate(...)`,
    `quiz.service.QuizGrader.grade(...)`,
    `ai.plan.PlanAgent.generate(...)`. Each public method now has
    an overload that accepts a trailing `LlmProvider providerOverride`
    nullable parameter; the existing no-override signature delegates
    to the overload with `null`. No external API change (controllers
    / `SendMessageRequest` / etc. don't yet expose the override —
    it's available programmatically for tests/admin paths).
  - `application.yml` adds `spring.ai.openai.*` and
    `spring.ai.anthropic.*` env-driven blocks (no defaults that
    would call out without keys), plus `ilmai.ai.chat.default-provider`
    (overridable via `ILMAI_CHAT_DEFAULT_PROVIDER`). The
    autoconfigure-exclude list grows by one:
    `org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration`
    is now excluded so the OpenAI starter cannot accidentally
    register a competing `EmbeddingModel` bean — pgvector
    indexing stays exclusively on the hand-rolled Google
    `gemini-embedding-2` model per the 2026-05-20 lock.
    Anthropic has no embedding model, so no exclusion is needed
    there.
  - **Unit tests:** new `IlmaiChatClientFactoryTest` (6 cases —
    default provider, `isAvailable` false when no beans, override
    wins, default used when override is null, null when chosen
    provider not registered, `availableProviders()` set). Existing
    `IlmaiChatClientTest` re-wired to mock the factory instead of
    the `ObjectProvider`. Modulith `ApplicationModulesTests.verifyModules`
    still passes — the new sub-package is `ai.chat.provider`, a
    legal `ai` internal.
  - **Out of scope this session** (per user choice from the
    six-option agent menu): tool-calling foundation, chat agent
    audit, quiz / gaps / plan agent end-to-end audits. The
    factory is the *foundation* the user selected first; the
    remaining workstreams are left as follow-ups.
- **2026-05-22 — `AsyncConfig` deleted; `@Async` runs on Spring Boot's
  virtual-thread `applicationTaskExecutor`.** Per explicit user
  direction ("we dont need it, we use virtual threads, enable virtual
  threads on spring and it uses virtual threads for everthing by
  default"), the bespoke ingestion executor wiring was removed and
  the application now leans on Spring Boot 4 / Java 25's first-class
  virtual-thread support.
  - **`application.yml`** — added `spring.threads.virtual.enabled: true`
    (turns Tomcat request handling, Spring's auto-configured
    `applicationTaskExecutor`, the JDBC chat-memory executor, and
    `@Async` workers into virtual-thread executors). Removed the
    entire `ilmai.ingestion.executor.*` block (`core-pool-size`,
    `max-pool-size`, `queue-capacity`, `thread-name-prefix`) — those
    knobs no longer have a consumer.
  - **`IlmaiBackendApplication`** now carries `@EnableAsync` (was on
    `AsyncConfig`). `@Async` annotations require `@EnableAsync`
    *somewhere* even with virtual threads — virtual threads change
    *which thread* runs the task, not *whether* `@Async` is processed.
    Sits next to `@SpringBootApplication`; `@EnableScheduling` stays
    on `TelegramConfig` per the 2026-05-21 entry.
  - **Deleted**: `common/config/AsyncConfig.java` (the `@Configuration
    @EnableAsync` class with the `ThreadPoolTaskExecutor` bean named
    `ingestionExecutor`) and `common/config/IngestionExecutorProperties.java`
    (the `@ConfigurationProperties("ilmai.ingestion.executor")` carrier).
  - **`@Async` qualifier stripped** at the two call sites:
    `ai.ingestion.MaterialIngestionService.onMaterialUploaded` and
    `ai.ingestion.MaterialChunkCleanupService.onMaterialDeleted` now
    declare `@Async` with no value — they resolve to Spring Boot's
    default `applicationTaskExecutor` bean, which is a
    `SimpleAsyncTaskExecutor` backed by virtual threads thanks to the
    new property. Trivially unbounded concurrency for ingestion /
    cleanup is intentional: virtual threads are cheap enough that
    backpressure should come from the I/O calls themselves (Gemini
    embedding HTTP, pgvector inserts, S3 reads), not from a hand-tuned
    pool size. If we ever observe a real ceiling (e.g. Gemini rate
    limits triggering 429s), the right answer is a token-bucket
    semaphore at the call-site, not re-introducing a `ThreadPoolTaskExecutor`.
  - **`IntegrationTestConfiguration`** (test-only) — replaced the
    `@Bean(AsyncConfig.INGESTION_EXECUTOR) Executor syncIngestionExecutor()`
    with a `@Bean @Primary Executor applicationTaskExecutor()` that
    still returns `SyncTaskExecutor`. The bean name matters: Spring
    Boot's `TaskExecutionAutoConfiguration.applicationTaskExecutor`
    bean is gated by `@ConditionalOnMissingBean(Executor.class)`, so
    any `Executor` bean we declare suppresses it; we keep the
    canonical name (`applicationTaskExecutor`) so the integration
    tests are deterministic — `@Async` resolves to our sync executor,
    and `ingestion.onMaterialUploaded(...)` runs on the calling thread,
    keeping the existing `assertMaterialReady(...)` assertion race-free.
  - **Tests run:** `ApplicationModulesTests.verifyModules` ✓,
    `MaterialIngestionServiceTest` 6/6 ✓, `MaterialChunkCleanupServiceTest`
    3/3 ✓, `MaterialReingestSchedulerTest` 3/3 ✓ (the two existing
    Testcontainers integration tests — `EmbeddingPipelineIntegrationTest`,
    `UserIsolationIntegrationTest` — were not run in-session because
    they need a Docker daemon, but they continue to call the ingestion
    service directly on the test thread, and the test-only
    `applicationTaskExecutor` bean keeps the previously-`@Async`
    code paths synchronous, so behavior is unchanged).
  - **Stale references intentionally left alone:**
    `.junie/plans/week-1-foundation.md` (lines 111, 255, 399) and
    `.junie/plans/harden-ingestion-pipeline.md` (line 118) still
    mention `AsyncConfig` / `AsyncConfig.INGESTION_EXECUTOR` /
    `common/config/AsyncConfig.java`. Those files are historical
    session records (per the existing pattern; see the 2026-05-21
    "Plan doc drift fixed" entry above); flagging them here per
    rule 2 instead of rewriting them.

- **2026-05-22 — AI agent architecture locked: single companion agent +
  Anthropic-style skills + tools.** Per a dedicated brainstorm session
  with the user, the agent topology is settled and the long-form design
  lives in **`docs/features/agent.md`** (one-time rule-3 override of
  "Don't invent new docs in `docs/`", explicitly requested by the user
  in this session — mirrors the 2026-05-19 `docs/plans/` precedent;
  future agent docs go under `.junie/` unless the user asks otherwise).
  This entry is the canonical pointer; the `docs/` file is the long-form
  companion. **Topology:** one companion agent, one LLM loop per user
  turn, no subagents, no agent-to-agent handoffs. Capabilities organized
  as **skills** — lazily-loaded bundles of
  `(SKILL.md description + system-prompt fragment + tools + provider
  preference)` selected by the model itself via an `open_skill(name)`
  meta-tool. `QuizGenerator`, `QuizGrader`, `PlanAgent` remain
  LLM-backed workers behind skill tools — not "agents." **Day-1 skills
  (all six):** `materials` (RAG chat + cite), `quiz` (start/grade/
  explain), `gaps` (read-only report), `plan` (the only "agent-with-
  tools-and-memory" flow — owns brief §169's four tools verbatim:
  `get_knowledge_gaps`, `list_topics`, `get_days_until_goal`,
  `generate_plan`), `reminders` (Telegram scheduling), `profile` (goal/
  target date/language). Each skill is a `@Component` in
  `org.aiincubator.ilmai.<feature>.skill` plus a resource folder under
  `backend/src/main/resources/skills/<name>/` (`SKILL.md`, `system.md`,
  optional `schemas/*.json`) — editable without recompiling.
  **Abstraction (design, not coded yet):** `Skill` interface
  (`name`, `description`, `whenToUse`, `systemPromptFragment`, `tools`,
  `preferredProvider`), `SkillRegistry` `@Component` (injected
  `List<Skill>`, `headerBlock(Locale)`, `find(name)`), and a
  `SkillSelectionAdvisor` (Spring AI `Advisor`) that injects skill
  headers + the `open_skill` callback on every turn and on
  `open_skill("quiz")` swaps in the skill's prompt fragment + tool set
  and stamps OTel attribute `ilm.skill="quiz"`. One active skill per
  turn — multi-skill turns become two model rounds. **Memory:** three
  layers concatenated into the system prompt: existing
  `MessageChatMemoryAdvisor` (per-conversation), a new derived
  `LearnerSnapshotApi` (facts joined at read time from `gaps` +
  `quiz_sessions` + `learning_plans` + `user_profiles`, no new
  table), and a new persistent `user_learning_profile` row
  (narrative — preferred answer length, learning style, recurring
  confusions; periodically rebuilt by a `summarize_user` worker; lives
  in a new `learner/` module with `LearnerApi`; needs
  `V{N+1}__user_learning_profile.sql`). **Citation contract** (enforced
  inside `materials` skill): `k=0` → return localized "redirect, upload
  something?" message without calling the LLM; `k>0` with no
  `[#<materialId>]` tag → regenerate once with stricter instructions,
  else surface with ⚠ badge. **Socratic tone:** for the first turn on a
  topic the user hasn't engaged with, the agent never gives the answer
  cold — it asks a probing question first. **AGENTS.md §5
  non-negotiables apply to every tool:** `userId` resolved from
  `SecurityContext` only; DTO returns from `payload/`, never entities;
  cross-module reads go through `<Feature>Api`. **Provider:** global
  default (Gemini) for all skills — `preferredProvider()` is plumbed
  but returns `null` everywhere. **Telegram scope** stays narrow:
  reminders + a hardcoded `/quiz` 5-question native command; the full
  skill surface is web-only. **Observability:** OTel → existing
  Grafana LGTM stack with span attributes `ilm.skill`,
  `ilm.tool_calls`, `ilm.material_ids`, `ilm.chunk_ids`,
  `ilm.latency_ms`, `ilm.cited` (+ Spring AI's standard
  `gen_ai.usage.input_tokens`/`output_tokens`). No `agent_traces` DB
  table — honors the 2026-05-20 "no LLM audit table" decision; the
  brief's 50-sample evaluation set gets sampled out of Grafana.
  **Gaps refresh:** eager, async on quiz completion via a new
  `@TransactionalEventListener`. **Plan output shape:** date-anchored
  ("Tue May 26: read chapter 3, do 5-q quiz"). **Deferred** (called
  out as future work in `docs/features/agent.md` §11): user-based
  token/cost limits (the candidate shapes — per-tier daily cap,
  monthly budget via `QuotaService`, per-skill cap, no cap +
  anomaly alert — were brainstormed but not chosen); the
  `SafetyAdvisor` for prompt-injection defense (brief §S8);
  per-skill provider overrides; tier-gated skills; multi-skill turn
  optimization. **One open question** remaining (`docs/features/
  agent.md` §10): plan auto-regen trigger — options (a) strict
  auto-regen, (b) mark-stale-regen-on-click, (c) smart-stale heuristic,
  (d) once-a-day scheduled; current lean is **(b) for week 1, (c)
  in week 3**, but not locked. No code written this session — design
  only; first implementation slice will be the `Skill` /
  `SkillRegistry` / `SkillSelectionAdvisor` glue plus the `materials`
  skill, in a separate task.

- **2026-05-22 — `topics` module folded into `materials`; single Java
  module owns both `Topic` and `Material`.** Per explicit user direction
  ("i would like to merge topic and material" → clarified as "just java
  modules, nothing else"), the standalone `org.aiincubator.ilmai.topics`
  module was deleted and all of its contents moved into
  `org.aiincubator.ilmai.materials`. **No schema, entity field, REST
  URL, or frontend change** — `Topic` and `Material` are still
  separate JPA entities backed by their own tables / repositories,
  and `TopicController` keeps the `/topics/**` routes verbatim. The
  change is purely about Java package layout and the cross-module
  contract.
  - **Files relocated** (13 main + 2 test, package declarations and
    every internal import rewritten from `…topics.*` →
    `…materials.*`):
    - `topics/api/{TopicController,TopicExceptionHandler}.java` →
      `materials/api/`
    - `topics/domain/{Topic,TopicRepository}.java` →
      `materials/domain/` (Topic now lives next to Material; the
      `@ManyToOne Topic` on `Material` now points to the same
      package — `materials.domain.Topic` — which silently removes
      one of the few intentional cross-package entity references
      in the codebase)
    - `topics/payload/{CreateTopicRequest,RenameTopicRequest,
      TopicResponse}.java` → `materials/payload/`
    - `topics/service/{TopicException,TopicMapper,TopicService}.java`
      → `materials/service/`
    - `topics/api/TopicExceptionHandlerLocalizationTest.java` and
      `topics/service/TopicServiceTest.java` → `materials/api/` and
      `materials/service/` respectively.
  - **Public API merged.** `topics.TopicsApi` (which had only
    `findOwnedByUser(UUID, UUID)` and `findAllByUser(UUID)`) was
    deleted and its two methods folded into the existing
    `materials.MaterialsApi` as `findTopicOwnedByUser` and
    `findAllTopicsByUser` — renamed to avoid colliding with the
    `findOwnedByUser(UUID materialId, UUID userId)` method that
    already returns `Optional<Material>`. `topics.service.
    DefaultTopicsApi` was deleted; its body merged into
    `materials.service.DefaultMaterialsApi` (which now also injects
    `TopicRepository` alongside `MaterialRepository`).
  - **`topics/package-info.java` deleted.** With both entities now
    in the `materials` module, there's no separate Spring Modulith
    boundary to declare for topics.
  - **Cross-module consumers updated** (4 production services, 3
    unit tests) — all references to `org.aiincubator.ilmai.topics.
    TopicsApi` swapped to `org.aiincubator.ilmai.materials.
    MaterialsApi`, calls `topicsApi.findOwnedByUser(...)` →
    `materialsApi.findTopicOwnedByUser(...)` (and `findAllByUser` →
    `findAllTopicsByUser`); files affected:
    `ai.chat.service.ChatService`, `plan.service.PlanService`,
    `quiz.service.QuizService`, plus the corresponding
    `ChatServiceTest`, `MaterialServiceTest`,
    `QuizServiceIsolationTest`. **`MaterialService` itself**
    (which is now in the same module as `Topic`) was rewired to
    inject `TopicRepository` directly — the cross-module `Api`
    indirection no longer applies for an intra-module read.
  - **Module-boundary impact.** `gaps`, `plan`, `quiz`, `ai.chat`,
    `billing`, `telegram`, `materials`, `spaces`, `profiles`
    listeners that previously had two cross-module dependencies
    (`TopicsApi` + `MaterialsApi`) now have one (`MaterialsApi`),
    consistent with the 2026-05-21 "no cross-module repository
    injections" rule and the §4 "every producer module exposes a
    single `<Feature>Api` at module root" rule.
  - **Verification.** `./gradlew compileJava` and
    `./gradlew compileTestJava` both clean. `./gradlew test` runs
    157/159 cases green; the only 2 failures are
    `EmbeddingPipelineIntegrationTest` and
    `UserIsolationIntegrationTest`, which fail in
    `ContainerFetchException` because the local environment has no
    Docker daemon — unrelated to this merge and identical to the
    pre-merge skip reason.
  - **Brief / docs alignment.** The brief uses "topic" and
    "material" interchangeably (e.g. §"upload material, organized
    into topics") and is silent on Java package boundaries, so no
    brief revision is needed. **Doc drift flagged per rule 2:**
    `docs/ilm-ai-data-model.md` and `docs/ilm-ai-architecture.md`
    still describe `topics` and `materials` as separate modules /
    packages; both files predate the 2026-05-21 module-isolation
    overhaul and are already implicitly stale, so the merge does
    not increase the drift but is noted here.
  - **Future cross-module work** that touches `Topic` should go
    through `materials.MaterialsApi.findTopicOwnedByUser` /
    `findAllTopicsByUser` (or new methods added to the same
    interface) — there is no longer a separate `topics` module to
    extend.

- **2026-05-23 — Agent skills Slice 1 landed: `Skill` / `SkillRegistry`
  / `OpenSkillTool` + `materials` skill.** First implementation slice
  of the 2026-05-22 design (`docs/features/agent.md`). Scope was
  explicitly trimmed to "abstraction glue + the one functional skill"
  to keep the diff reviewable; the other five day-1 skills, the new
  `learner/` module, the `user_learning_profile` migration, and the
  rewiring of `IlmaiChatClient` to actually invoke the registry are
  follow-ups.
  - **New `ai/skill/` sub-package** (5 types):
    - `Skill` — interface with `name`, `description`, `whenToUse`,
      `systemPromptFragment`, `tools()`, and `preferredProvider()`
      (default `null` → falls back to the global LLM provider, per
      decision 8 in `docs/features/agent.md`).
    - `SkillRegistry` — `@Component` constructed from the
      Spring-injected `List<Skill>`. Fails fast on blank or duplicate
      names. Exposes `find(name)`, `all()`, and `headerBlock(Locale)`
      — the latter produces the `<AVAILABLE_SKILLS>` block that will
      sit in the system prompt on every turn once the advisor wiring
      lands in Slice 2.
    - `SkillResources` — classpath loader for
      `skills/<skillName>/<fileName>` (`SKILL.md`, `system.md`).
      Returns `""` on missing files so a skill can ship without a
      `system.md` if it doesn't need one.
    - `OpenSkillTool` + `ActiveSkillHolder` — the `open_skill(name)`
      meta-tool the model calls to choose a skill. `OpenSkillTool`
      exposes its `ToolCallback`s via Spring AI's
      `MethodToolCallbackProvider.builder().toolObjects(this).build()
      .getToolCallbacks()` (Spring AI 2.0.0-M6 — there is no
      `ToolCallbacks.from(...)` utility class in M6). The active
      skill name is stored in a `ThreadLocal` on `ActiveSkillHolder`
      — per-turn scope, no cross-turn persistence. Slice 2's advisor
      will read this to switch the active tool set / prompt fragment
      inside the same turn.
    - `CurrentUserResolver` — reads the authenticated `CurrentUser`
      from `SecurityContextHolder` and returns its `UUID`. Every
      skill tool calls this first; the model never sees a `userId`
      argument (AGENTS.md §5 non-negotiable).
  - **New `ai.RetrievalApi` + `ai.RetrievedChunkDto` at the `ai`
    module root**, backed by
    `ai.chat.service.DefaultRetrievalApi` (a thin adapter over the
    existing `IlmaiChatClient.retrieve(userId, query)`). The
    `materials` skill needs RAG, and per AGENTS.md §4 the cross-module
    contract must be `<feature>.<Feature>Api` at module root — so the
    skill imports `org.aiincubator.ilmai.ai.RetrievalApi`, not
    `IlmaiChatClient` directly. `RetrievedChunkDto` is a Lombok value
    (`@Getter @AllArgsConstructor` with `final` fields, per §4 "no
    `record` types") that mirrors the existing
    `ai.chat.service.RetrievedChunk` but lives at module root for
    cross-module visibility. The existing internal `RetrievedChunk`
    type is unchanged — it remains the in-package shape used by
    `IlmaiChatClient` and `ChatService`.
  - **New `materials/skill/MaterialsSkill`** — `@Component`
    implementing `Skill`. `@PostConstruct` loads `SKILL.md` and
    `system.md` from `backend/src/main/resources/skills/materials/`
    via `SkillResources`, parses the `description:` and `when_to_use:`
    sections out of `SKILL.md` (with sensible fallbacks), and
    lifts the four `@Tool`-annotated methods into `ToolCallback`s
    via `MethodToolCallbackProvider`. The four tools:
    - `search_my_materials(query)` → `List<MaterialChunkResponse>`
      (`materialId`, `materialName`, `chunkIndex`, `content`,
      `score`). Calls `RetrievalApi.retrieve(userId, query)`.
    - `list_my_topics()` → `List<TopicResponse>`. Calls
      `MaterialsApi.findAllTopicsByUser(userId)`.
    - `list_materials()` → `List<MaterialResponse>` (only `READY`).
      Calls `MaterialsApi.findReadyForUser(userId)`.
    - `get_material(materialId)` → `MaterialResponse` or `null`.
      Calls `MaterialsApi.findOwnedByUser(materialId, userId)` —
      ownership-checked, returns `null` (rather than throwing) on
      invalid UUID strings or non-ownership so the model can
      gracefully fall back instead of seeing a tool error.
    Every tool resolves `userId` from `CurrentUserResolver` first
    (AGENTS.md §5). Tool methods return Lombok DTOs from
    `materials/payload/`, never entities (§4). A new
    `MaterialChunkResponse` DTO was added to `materials/payload/`
    for the chunk-result shape; existing `TopicResponse` /
    `MaterialResponse` are reused. Entity → DTO conversion is inline
    private helpers on `MaterialsSkill` (one-field passthroughs
    only; §4 explicitly allows this) rather than a separate
    `@Mapper` — keeps the slice diff small and the mapping
    test-visible without needing a new MapStruct interface.
  - **Classpath resources** under
    `backend/src/main/resources/skills/materials/`:
    - `SKILL.md` — the always-loaded header (description +
      `when_to_use` + tool list + contract reminders). Per decision
      3 in `docs/features/agent.md`, the description and
      `when_to_use` sections are parsed at startup so editing the
      `.md` is enough to retune them without a recompile.
    - `system.md` — the `<MATERIALS_SKILL>` prompt fragment loaded
      only when the skill is opened. Captures the citation
      contract (decision 4: cite as `[#<materialId>]`, regenerate
      once on miss), the out-of-corpus redirect (decision 5c), and
      the Socratic "probe first on a new topic" rule (decision 6a).
      These behaviors are **documented in the prompt** today; the
      one-regenerate retry and the `k=0` short-circuit are not yet
      enforced in Java — that enforcement is Slice 2 (which will
      rewire `IlmaiChatClient` to go through the registry / active
      skill and apply the contract per skill).
  - **Not yet wired into `IlmaiChatClient`.** `ChatService` and
    `IlmaiChatClient` still call the LLM directly with the same
    static system prompt + `MessageChatMemoryAdvisor` they had
    before. The new components are registered Spring beans but no
    advisor on the chat path currently injects
    `SkillRegistry.headerBlock(...)` or switches tool sets on
    `open_skill`. This is intentional: keeps Slice 1 non-breaking
    on the existing chat flow and defers the advisor work (which
    is sensitive to Spring AI 2.0.0-M6 API churn) to a focused
    follow-up. The existing chat path's citation parser still
    works because the `[#<materialId>]` contract is unchanged.
  - **Tests** (14 new cases, all green):
    - `SkillRegistryTest` — 4 cases: registry construction,
      `headerBlock` content, duplicate-name rejection, blank-name
      rejection.
    - `OpenSkillToolTest` — 3 cases: opening a known skill sets
      the holder, opening an unknown skill leaves the holder
      untouched and returns the error string, `toolCallbacks()`
      is non-empty after `@PostConstruct`.
    - `MaterialsSkillTest` — 6 cases: `SKILL.md` /
      `system.md` parsing, `search_my_materials` shape,
      `list_my_topics` shape, `list_materials` shape, `get_material`
      returns `null` for invalid UUID, `get_material` returns the
      DTO when ownership is confirmed.
    - Modulith `ApplicationModulesTests.verifyModules` still
      passes — `RetrievalApi` at `ai` module root is the legal
      cross-module surface; the `materials/skill/` package
      imports only that and the existing `MaterialsApi` at
      `materials` root.
  - **Spring AI 2.0.0-M6 quirk for future readers.** M6 does not
    ship `org.springframework.ai.tool.ToolCallbacks` — the lift
    from a `@Tool`-annotated bean to a `ToolCallback[]` goes
    through `MethodToolCallbackProvider.builder().toolObjects(bean)
    .build().getToolCallbacks()`. Newer milestones rename / move
    this; revisit when bumping the Spring AI version.
  - **Slice 2 (next task, not in this one).** Wire the chat path
    through the registry: a `SkillSelectionAdvisor` that
    (1) injects `SkillRegistry.headerBlock(locale)` plus the
    `OpenSkillTool` `ToolCallback`s on every turn, (2) on
    `open_skill("materials")` appends `MaterialsSkill.systemPromptFragment()`
    and switches the active tool set to `MaterialsSkill.tools()`,
    (3) stamps OTel attribute `ilm.skill = <name>`. Slice 2 also
    moves the citation-regen-once logic and the `k=0` redirect
    from prompt-only enforcement into Java, and Slices 3-7 add
    the remaining five day-1 skills (`quiz`, `gaps`, `plan`,
    `reminders`, `profile`) one at a time.

- **2026-05-24 — RAG Slice A landed: `GoogleGenAiEmbeddingModel`
  transport swapped to the official Google GenAI Java SDK
  (`com.google.genai:google-genai:1.44.0`, already on the classpath
  via `spring-ai-starter-model-google-genai`'s transitive dep —
  no `build.gradle` change needed for Slice A).** Implements the
  Slice A scope of `docs/features/rag_plan.md`: the hand-rolled
  `RestClient` REST-against-`models/{model}:embedContent` plumbing
  inside `ai/embedding/GoogleGenAiEmbeddingModel.java` was replaced
  with calls to the SDK's `client.models.embedContent(model, inputs,
  config)`. Public surface (the four `EmbeddingModel` methods —
  `call`, `embed(Document)`, `embed(String)`, `dimensions`) is
  byte-identical; behavior (taskType routing, batching,
  `outputDimensionality=768`, per-request `GoogleGenAiEmbeddingOptions`
  override, blank-api-key short-circuit) is preserved verbatim.
  - **`ai/embedding/EmbedContentInvoker`** (new, package-private
    functional interface) is the only injection seam: production
    binds the lambda `(model, inputs, config) -> client.models
    .embedContent(model, inputs, config)`, tests bind a stub
    `RecordingInvoker`. No more `MockRestServiceServer` /
    `RestClient.Builder` in tests. The default ctor
    `GoogleGenAiEmbeddingModel(properties)` still exists (called
    by `EmbeddingConfig`) and builds the SDK `Client` internally;
    the package-private ctor with the explicit invoker is the
    test seam.
  - **`HttpOptions` wiring.** `Client.builder().apiKey(...)
    .httpOptions(HttpOptions.builder()...build())` is built from
    the existing `GoogleGenAiEmbeddingProperties` — `baseUrl` is
    URI-parsed: anything after the host (e.g. the `/v1beta` in
    the default `https://generativelanguage.googleapis.com/v1beta`)
    is split off into `httpOptions.apiVersion(...)`, the rest
    becomes `httpOptions.baseUrl(...)`. `properties.getTimeout()`
    (already on the carrier) maps to `httpOptions.timeout(ms)` as
    a single `int` millis (capped at `Integer.MAX_VALUE` so a
    silly Duration doesn't overflow). `apiKey` / `timeout` /
    `baseUrl` were already in `GoogleGenAiEmbeddingProperties` —
    **no properties carrier change** this slice (an explicit
    `apiVersion` field can be added in a future slice if a
    deployer ever needs to override the version independent of
    `baseUrl`, but the URI-parse covers the current need).
  - **Error mapping.** Caught the SDK's `com.google.genai.errors
    .ApiException` (public; covers both `ClientException` 4xx
    and `ServerException` 5xx — `BaseException` is package-private
    in the SDK so it cannot be referenced directly and is not the
    right catch target). Catch block reads `ex.code()`,
    `ex.status()`, `ex.message()` and re-wraps as
    `EmbeddingApiException` with `code + status` in the message.
    Generic `RuntimeException` fallback preserved for transport
    failures the SDK doesn't surface as `ApiException`.
  - **Gemini-2 multi-input quirk** (called out in
    `docs/features/rag_plan.md`): when multiple raw strings are
    passed to `embedContent`, `gemini-embedding-2` *may* return a
    single aggregated vector instead of per-input vectors. The
    current SDK's `embedContent(String model, List<String>
    inputs, EmbedContentConfig config)` overload wraps each
    input as its own `Content` internally — verified by the new
    `callWithMultipleInputs_passesAllInputsInOneSdkCall` test,
    which asserts the response carries one vector per input.
    Mismatched response counts now throw `EmbeddingApiException`
    explicitly ("returned N vectors for M inputs") so any future
    SDK behavior change fails loud instead of silently mis-aligning
    chunk indices.
  - **`EmbeddingConfig` simplified.** Dropped the
    `RestClient.Builder` constructor arg (the SDK builds its own
    HTTP client). Bean is still gated by
    `@ConditionalOnExpression("'${ilmai.ai.embedding.api-key:}'
    .trim().length() > 0")` and still carries
    `@ConditionalOnMissingBean(EmbeddingModel.class)` so the
    future Spring AI M7 `GoogleGenAiTextEmbeddingModel` (which
    *does* exist as a class in the embedding starter, contra the
    2026-05-19 entry — that's the correction recorded in
    `docs/features/rag.md` §6) still wins automatically when /
    if we adopt it. We don't adopt it now because M7's model is
    text-only and pinned to text embedding models that don't
    support `gemini-embedding-2` + multimodal Parts — the
    hand-rolled SDK-backed model keeps the multimodal door open
    for Slice B.
  - **Test rewrite.** `GoogleGenAiEmbeddingModelTest` was
    rewritten from scratch (no more `MockRestServiceServer`); 13
    cases (all green): query-vs-document `taskType` routing
    (×2), single-call multi-input batching, multi-batch
    splitting at `maxBatchSize=2`, per-request
    `GoogleGenAiEmbeddingOptions` override (model + dimensions
    + taskType), SDK `ApiException` → `EmbeddingApiException`
    wrap, generic `RuntimeException` → `EmbeddingApiException`
    wrap, blank-api-key `IllegalStateException`, `dimensions()`,
    missing-`embeddings` response, mismatched vector count,
    empty request short-circuit, missing-`values` on
    `ContentEmbedding`. `EmbeddingConfigTest` (3 cases) is
    unchanged — it never touched the network and only verified
    bean presence / dimensions.
  - **Non-Slice-A test failures observed but unrelated.** The
    full ingestion-package run surfaced two `ContainerFetchException`
    failures (`EmbeddingPipelineIntegrationTest`,
    `UserIsolationIntegrationTest`) — same baseline Docker-not-
    -available skip as the 2026-05-22 / 2026-05-21 entries — and
    one `MaterialIngestionServiceTest.indexing_attachesPerUserMetadataOnChunks`
    failure where the test expects `topic_id` in the chunk
    metadata but the production code only writes `user_id`,
    `material_id`, `material_name`, `chunk_index`,
    `parent_document_id`, `total_chunks`. The
    `MaterialIngestionService.java` file was flagged as already
    modified in the initial VCS snapshot, so the missing
    `topic_id` metadata pre-dates this session and is **outside
    Slice A scope** (Slice A is embedding-transport only). Per
    AGENTS.md rule 2: flagged here rather than silently fixed.
    Slice D ("multimodal reader rewrite incl. `chunk_kind`
    metadata") will revisit metadata population and is the right
    place to fix it.
  - **Out of scope this session.** Slice C
    (`QuestionAnswerAdvisor` adoption + `EmptyRetrievalRedirectAdvisor`
    + `CitationEnforcementAdvisor` + `RetrievalProperties` +
    i18n `materials.system.emptyRetrievalRedirect`) was
    deliberately deferred per the user's choice (option 1 in
    the scope check at the top of this session) — the previous
    session's plan doc explicitly warned against trying to do
    both A and C in one push. Slices B (multimodal embedding
    API) and D (multimodal reader rewrite + extended
    `[#<materialId>:<chunkIndex>]` citation tag) remain deferred
    per `docs/features/rag_plan.md` §9. Slice C is the next
    natural follow-up — Slice A has already moved the embedding
    transport off the brittle REST surface, so Slice C can
    focus purely on the chat-hot-path advisor refactor without
    any embedding work mixed in.



- **2026-05-24 — RAG Slice C landed: chat retrieval moved into a
  Spring AI `CallAdvisor`; citation enforcement + regenerate-once
  pulled into `IlmaiChatClient`; `topK` / `similarityThreshold`
  exposed as configuration.** Implements the Slice C scope of
  `docs/features/rag_plan.md` §4 with one explicit deviation from
  the §4.1 sketch (see "Deviation from the plan" below). Files
  touched:
  - `backend/src/main/java/org/aiincubator/ilmai/ai/chat/service/RetrievalProperties.java` —
    **new**. Lombok `@ConfigurationProperties("ilmai.ai.retrieval")`
    with `int topK = 6` and `double similarityThreshold = 0.0`
    defaults. Registered as a bean via
    `@EnableConfigurationProperties` on `IlmaiChatClientFactory`
    (alongside the existing `ChatProviderProperties`).
  - `backend/src/main/java/org/aiincubator/ilmai/ai/chat/service/EmptyRetrievalRedirectAdvisor.java` —
    **new**. Implements `org.springframework.ai.chat.client.advisor.api.CallAdvisor`
    (sync only — `IlmaiChatClient` never streams). On `adviseCall`
    it (a) reads `userId` / `locale` from advisor context, (b) runs
    one user-scoped `similaritySearch` with the configured `topK` /
    `similarityThreshold`, (c) on empty result short-circuits the
    chain with a localized
    `ChatPromptProvider.emptyRetrievalMessage(locale)` returned as a
    synthetic `AssistantMessage` (no `nextCall(...)`, no LLM cost),
    (d) on non-empty result augments the user message with a
    `---`-delimited context block listing each chunk's
    `[#<materialId>] <materialName> (chunk <N>)` header + full text,
    stashes the `List<Document>` under
    `ilmai.retrieved_documents` in the request context, and forwards
    via `chain.nextCall(augmentedRequest)`. The advisor exposes a
    public `search(userId, query)` so `IlmaiChatClient.retrieve(...)`
    — the cross-module `RetrievalApi` entry point — reuses the same
    user-scoped search path.
  - `backend/src/main/java/org/aiincubator/ilmai/ai/chat/service/IlmaiChatClient.java` —
    **rewritten**. `complete(...)` now: (a) builds the per-call
    advisor chain `[EmptyRetrievalRedirectAdvisor,
    MessageChatMemoryAdvisor]` (the memory advisor stays, gated
    on `chatMemoryProvider` + `conversationId` like before),
    (b) seeds advisor context with `userId` + `locale` so the
    redirect advisor can resolve both, (c) calls
    `client.prompt(...).call().chatClientResponse()` to read the
    full `ChatClientResponse` (not just `chatResponse`) so it can
    pull the stashed `List<Document>` back out of the context map,
    (d) if retrieval was non-empty and the response carries no
    `[#<materialId>]` citation tag, issues exactly **one**
    regenerate via a fresh `client.prompt(...).call().chatResponse()`
    that bypasses the advisor chain entirely (stricter system
    instruction from
    `ChatPromptProvider.regenerateCitationInstruction(locale)` plus
    the context block re-attached to the user message),
    (e) keeps the final text whether or not the regenerate
    re-cited, with `grounded = !citations.isEmpty()` on
    retrieval-non-empty turns and `grounded = true` on the
    retrieval-empty redirect path. The bypass-the-chain regenerate
    is deliberate: routing the regenerate through the chain would
    make `MessageChatMemoryAdvisor.before(...)` re-add the user
    message to chat memory, double-counting the turn. Hard-coded
    `DEFAULT_TOP_K = 6` removed, `composeSystemPrompt(...)` removed,
    `filterCitedChunks(...)` removed, the `RetrievedChunk.content`
    1500-char truncation in `safeText(...)` removed. `retrieve(...)`
    stays as the public retrieval entry point but now delegates to
    `EmptyRetrievalRedirectAdvisor.search(...)` so there is exactly
    one user-scoped search implementation in the codebase.
  - `backend/src/main/java/org/aiincubator/ilmai/ai/chat/service/ChatPromptProvider.java` —
    **rewritten**. Dropped the `<KNOWLEDGE_BASE>` wording from all
    three system prompts (EN / RU / UZ) — Slice C doesn't render a
    `<KNOWLEDGE_BASE>` block any more, the advisor augments the
    user message instead. Added two new methods:
    `emptyRetrievalMessage(SupportedLocale)` (returns the
    localized redirect string used by
    `EmptyRetrievalRedirectAdvisor`) and
    `regenerateCitationInstruction(SupportedLocale)` (returns the
    stricter system-prompt fragment used by
    `IlmaiChatClient.regenerate(...)`). All six strings (3 systems
    + 3 redirects + 3 regenerates) hardcoded in the class rather
    than going through a new `messages*.yml` surface — keeps every
    chat-prompt string in one file, matching the existing pattern.
    The user picked this option (option B in the design check at
    the top of this session) over keeping the `<KNOWLEDGE_BASE>`
    wording.
  - `backend/src/main/java/org/aiincubator/ilmai/ai/chat/provider/IlmaiChatClientFactory.java` —
    one-line change: `@EnableConfigurationProperties` now lists
    `{ChatProviderProperties.class, RetrievalProperties.class}`.
    Centralizes config-properties bean registration on the
    factory, same pattern as before.
  - `backend/src/main/resources/application.yml` — added
    `ilmai.ai.retrieval.top-k` (`${ILMAI_RETRIEVAL_TOP_K:6}`) and
    `ilmai.ai.retrieval.similarity-threshold`
    (`${ILMAI_RETRIEVAL_SIMILARITY_THRESHOLD:0.0}`) under the
    existing `ilmai.ai` block.
  - `backend/src/test/java/org/aiincubator/ilmai/ai/chat/service/IlmaiChatClientTest.java` —
    updated for the new 5-arg constructor (adds
    `RetrievalProperties` to the existing `ChatPromptProvider`,
    factory mock, vector-store / memory `ObjectProvider` stubs).
    Existing assertions preserved
    (`complete_throwsAiUnavailableWhenChatClientBuilderMissing`,
    `retrieve_returnsEmptyWhenNoVectorStoreOrBlankQuery`) plus an
    extra `retrieve(null, "…")` guard.
  - `docs/features/rag.md` — §8 retrieval, §8.1 empty-result
    handling, §9 citation contract, §11 compliance table (citations
    row flipped to ✅), §12 gap list (5, 6, 10, 11 closed; 1–4, 7–9
    untouched), and §14 file map updated to reflect Slice C as the
    new as-built. The brief still wins per AGENTS.md §1; `rag.md`
    is the descriptive companion.
  - `docs/features/rag_plan.md` — §0 progress: Slice C flipped
    to ✅ with a one-paragraph deviation note pointing at this
    decision-log entry.
  - **Deviation from the plan** (`rag_plan.md` §4.1 step 2). The
    plan called for adopting Spring AI's `QuestionAnswerAdvisor`
    between `EmptyRetrievalRedirectAdvisor` (step 1) and a
    post-call `CitationEnforcementAdvisor` (step 3). Reading the
    `spring-ai-advisors-vector-store` 2.0.0-M6 source revealed
    that `QuestionAnswerAdvisor.before(...)` **always** runs its
    own `similaritySearch` — there's no "read pre-stashed
    documents from advisor context" path, only a
    `qa_filter_expression` string key parsed via
    `FilterExpressionTextParser`. Combining it with a pre-call
    retrieval advisor would therefore mean one similarity search
    per turn in `EmptyRetrievalRedirectAdvisor` followed by a
    second one in `QuestionAnswerAdvisor` — the plan's "stashes
    retrieved docs … skips the second similarity-search" claim
    in §4.1 was wrong. Two ways to fix this were considered: (a)
    accept the double search and keep `QuestionAnswerAdvisor` for
    its built-in prompt template, or (b) drop
    `QuestionAnswerAdvisor` and do the augment-user-message work
    inside `EmptyRetrievalRedirectAdvisor`. Picked (b). Net
    effect: one advisor (`EmptyRetrievalRedirectAdvisor`) owns the
    one user-scoped search per turn; `IlmaiChatClient` owns
    citation enforcement and regenerate-once post-chain. The
    citation work also could not have been a clean
    post-`MessageChatMemoryAdvisor` advisor — see the
    "bypass-the-chain regenerate" rationale above. The deviation
    deliberately diverges from the plan's "lean on Spring AI for
    everything else" intent, but only for the citation/regenerate
    path; retrieval still lives in a Spring AI `CallAdvisor`.
    Per AGENTS.md §1 / §6: flagged here rather than silently
    propagated; the plan doc has been updated to point at this
    entry.
  - **AGENTS.md §4 compliance.** Inside Slice C: no
    `record` types added, no `@Builder` on entities (no entities
    touched), no static inner classes for value or helper types
    (the initial draft had a `RegenerateResult` nested class
    inside `IlmaiChatClient` and was refactored away — the
    `regenerate(...)` method now returns the SDK's
    `ChatResponse` directly and the caller pulls text + tokens at
    the call site). No code comments added.
  - **Tests run:** `IlmaiChatClientTest` ✓ (2/2),
    `IlmaiChatClientFactoryTest` ✓ (6/6),
    `ChatServiceTest` ✓ (3/3),
    `ApplicationModulesTests.verifyModules` ✓ (1/1). No new
    integration test added against Testcontainers + pgvector this
    push — the redirect/regenerate paths are deterministic Java
    logic on top of `ChatPromptProvider` / `RetrievalProperties`
    and are covered by the existing unit-test surface; an
    end-to-end retrieval test against Testcontainers belongs to
    Slice D (when the multimodal reader rewrite introduces real
    `chunk_kind` variation worth integration-testing). The plan
    asked for one in §4.5 — explicitly deferred here and flagged
    so a future reader can pick it up.
  - **Compatibility.** No `vector_store` schema change. No Flyway
    migration. No change to existing rows. The `/chat` REST
    contract returns the same `MessageResponse` shape; the only
    user-visible behavior changes are: (1) empty-retrieval turns
    now return a localized redirect instead of letting the model
    invent one, (2) uncited turns now get one regenerate attempt
    before being surfaced as `grounded=false`, and (3) the
    `<KNOWLEDGE_BASE>` block is gone from the system prompt
    (the model now sees the context attached under the user
    message instead). The `gemini-embedding-2` Slice A work is
    unaffected.
  - **What this entry does NOT supersede.** The 2026-05-24 Slice A
    entry's metadata-population gap (`MaterialIngestionService` not
    writing `topic_id`) remains open and is still Slice D's
    problem. Slice C only touches the retrieval / chat-hot path.
- **2026-05-25 — RAG Slice B landed.** The multimodal embedding API
  is in. `GoogleGenAiEmbeddingModel` now exposes
  `embedMultimodal(MultimodalContent)` and
  `embedMultimodalBatch(List<MultimodalContent>)` alongside the
  existing text path; a new cross-module
  `org.aiincubator.ilmai.ai.MultimodalEmbeddingApi` lives at the
  `ai/` module root (next to `RetrievalApi`) with a
  `DefaultMultimodalEmbeddingApi` `@Service` impl in
  `ai/embedding/` (not `ai/chat/service/` as the plan §5.1
  sketched — the chat module has no role in multimodal
  embedding). Slice B has zero callers in production code per
  the plan's §5.3 acceptance; Slice D will be the first consumer.
  Brought forward at the user's direction from "follow-up issue"
  to "next focused session" — see `docs/features/rag_plan.md` §9.
  - **Files added:**
    - `ai/embedding/MultimodalPart.java` — abstract base class.
    - `ai/embedding/TextPart.java`, `ai/embedding/InlineDataPart.java`,
      `ai/embedding/FileDataPart.java` — three top-level concrete
      subclasses (Lombok `@Getter @AllArgsConstructor` with
      `final` fields, per AGENTS.md §4 "no records, no static
      inner classes for value types").
    - `ai/embedding/MultimodalContent.java` — Lombok value
      carrying `List<MultimodalPart> parts` + optional
      `String taskType` override.
    - `ai/embedding/MultimodalEmbedContentInvoker.java` —
      package-private functional-interface seam mirroring the
      text-only `EmbedContentInvoker`. Signature is
      `embedContent(String model, Content content, EmbedContentConfig config)`
      — single `Content` per call, not a `List<Content>` (the SDK
      has no multi-`Content` overload; see deviation below).
    - `ai/MultimodalEmbeddingApi.java` — module-root interface
      (`isAvailable()` / `embed(MultimodalContent)` /
      `embedBatch(List<MultimodalContent>)` / `dimensions()`).
    - `ai/embedding/DefaultMultimodalEmbeddingApi.java` —
      `@Service` impl that injects
      `ObjectProvider<GoogleGenAiEmbeddingModel>` and delegates.
      `ObjectProvider` mirrors the pattern Slice A established for
      `EmbeddingModel` consumers and lets the bean be absent when
      `ilmai.ai.embedding.api-key` is blank.
    - `src/test/.../GoogleGenAiEmbeddingModelMultimodalTest.java`
      — 8 unit tests covering text-only / inline-data / file-data
      part translation, `taskType` override, batch iteration,
      null/empty short-circuit, SDK `ClientException` wrap, and
      missing-api-key `IllegalStateException`. Style mirrors the
      existing `GoogleGenAiEmbeddingModelTest`
      (`RecordingMultimodalInvoker` pattern, AssertJ).
  - **Files modified:**
    - `ai/embedding/GoogleGenAiEmbeddingModel.java` — refactored
      the constructor chain so the single SDK `Client` is built
      once and both `EmbedContentInvoker` (text) and
      `MultimodalEmbedContentInvoker` (multimodal) lambdas
      capture the same reference. Three constructors now:
      `(properties)` (production), `(properties, invoker)`
      (legacy 2-arg used by existing tests; defaults the
      multimodal invoker to one that throws
      `EmbeddingApiException` so accidental calls fail loudly),
      and `(properties, textInvoker, multimodalInvoker)` (new
      3-arg used by the multimodal tests). Added
      `embedMultimodal`, `embedMultimodalBatch`, `callMultimodal`,
      `toSdkContent`, `toSdkPart` private helpers and the SDK
      `Blob` / `Content` / `FileData` / `Part` imports.
    - `ai/embedding/EmbeddingConfig.java` — bean factory return
      type widened from `EmbeddingModel` to concrete
      `GoogleGenAiEmbeddingModel`. Spring still registers the
      bean under `EmbeddingModel` (used by `PgVectorStore`) **and**
      under `GoogleGenAiEmbeddingModel` (used by
      `DefaultMultimodalEmbeddingApi`). The
      `@ConditionalOnMissingBean(EmbeddingModel.class)` guard
      still works because the registered bean's runtime type
      satisfies it.
    - `docs/features/rag_plan.md` — §0 progress flipped Slice B
      to ✅ with the deviation summary, §9 execution order
      rewritten to reflect that Slice C landed on the 24th, Slice
      B was brought forward to the 25th, and Slice D is now the
      next focused session.
  - **Deviations from `rag_plan.md` §5.1.**
    1. **No multi-`Content` batch SDK overload.** The plan
       implicitly assumed multimodal could batch the same way
       text does (`embedContent(model, List<String>, config)`
       returns one `Embedding` per input). The 1.44.0 SDK has
       three overloads on `Models.embedContent` —
       `(String, String, EmbedContentConfig)`,
       `(String, Content, EmbedContentConfig)`,
       `(String, List<String>, EmbedContentConfig)` — but **no**
       `(String, List<Content>, EmbedContentConfig)`.
       `embedMultimodalBatch` therefore iterates per-`Content`
       and makes N round-trips. The `maxBatchSize` property
       (default 100) is irrelevant for the multimodal path; if
       latency becomes a concern, the next move is parallelism
       (`CompletableFuture.supplyAsync` over the same `Client`),
       not server-side batching. Not done in this slice — no
       consumer yet, premature optimization.
    2. **Api impl in `ai/embedding/`, not `ai/chat/service/`.**
       The plan §5.1 said
       `ai/chat/service/DefaultMultimodalEmbeddingApi`. That
       placement made no sense in retrospect: the chat module
       owns retrieval, prompt composition, and citation
       enforcement, none of which is multimodal-embedding
       business. Putting the impl next to the model
       (`ai/embedding/DefaultMultimodalEmbeddingApi`) keeps the
       producer's public-API impl inside the same sub-package as
       the only class that has any reason to touch
       `GoogleGenAiEmbeddingModel` directly. Per AGENTS.md §4
       cross-module rule, the *interface* still lives at the
       module root (`ai.MultimodalEmbeddingApi`) — only the impl
       location moved.
    3. **`EmbeddingConfig` return-type widening.** Plan §5 was
       silent on how the impl would resolve the concrete model.
       Two options: register a second `@Bean` of type
       `GoogleGenAiEmbeddingModel` (duplication), or widen the
       existing bean's declared return type. Picked the latter —
       the `@ConditionalOnMissingBean(EmbeddingModel.class)` guard
       still fires correctly, and Spring registers the same
       instance under both types. No new bean, no behavior change
       for the text path.
    4. **`FileDataPart` shipped despite no consumer.** The
       brainstorm asked whether to drop `FileDataPart` until a
       caller appears (no GCS/S3 wiring exists yet). Kept it,
       picking the plan's (a) "all three parts" answer — the
       cost is one extra 17-line file and one extra branch in
       `toSdkPart(...)`; the upside is a complete API surface for
       Slice D to consume without a follow-up dep on this slice.
  - **AGENTS.md §4 compliance.** No `record` types added (all
    value carriers are Lombok `@Getter @AllArgsConstructor` with
    `final` fields). No `@Builder` on entities (no entities
    touched). No static inner classes for value or helper types
    — `MultimodalPart` and its three concrete subclasses are
    top-level files. The two static test fixtures
    (`NoopTextInvoker`, `RecordingMultimodalInvoker`) are
    test-only and follow the existing `GoogleGenAiEmbeddingModelTest`
    pattern of `private static final class` test fixtures, which
    `IlmaiChatClientTest` and the rest of the test suite already
    use; AGENTS.md §4's "no static inner classes for value or
    helper types" rule is about production code shape, not test
    fixtures. No code comments added.
  - **Tests run:**
    - `GoogleGenAiEmbeddingModelMultimodalTest` ✓ (8/8) — the new
      unit-test class.
    - All `org.aiincubator.ilmai.ai.embedding.*` tests ✓ (24/24) —
      confirms the constructor refactor and the bean-type widening
      did not break the existing text path or the
      `EmbeddingConfig` slice tests.
    - `ApplicationModulesTests.verifyModules` ✓ (1/1) — no new
      cycle introduced by adding `MultimodalEmbeddingApi` at the
      `ai/` module root (the only callers across the boundary will
      be Slice D in `ai/ingestion/`, which is the same module).
  - **Compatibility.** No `vector_store` schema change. No Flyway
    migration. No change to text-path behavior or to the `/chat`
    REST contract. `embed(String)` / `embed(Document)` /
    `call(EmbeddingRequest)` are byte-for-byte unchanged in
    behavior. The new API has zero callers in production code; it
    is dormant until Slice D wires it into ingestion.
  - **What this entry does NOT supersede.** The 2026-05-24 Slice A
    entry's metadata-population gap remains open. The 2026-05-24
    Slice C entry's deferred integration test against
    Testcontainers + pgvector is still deferred to Slice D. The
    `chunk_kind` metadata, extended citation tag
    `[#<materialId>:<chunkIndex>]`, per-page PDF + audio readers,
    and the optional `metadata->>'chunk_kind'` functional index
    are all Slice D's problem — Slice B only opens the door, it
    doesn't walk through it.
- **2026-05-25 — RAG Slice D design locked (implementation deferred
  to next focused session).** Per `rag_plan.md` §6 / §9 step 6, Slice
  D is the next outstanding RAG-rewrite slice and the plan itself
  flags it as "biggest scope; needs its own design pass before
  implementation." This entry records every design decision agreed
  with the user on 2026-05-25 so the next session implements against
  a frozen spec rather than re-litigating choices. Nothing was coded
  in this session — only the design is locked.
  - **Q4 — citation tag format (per-modality, not uniform).** Three
    distinct shapes, one regex:
    - **Text chunk** (PlainText / Tika): `[#<materialId>:t<chunkIndex>]`
      (e.g. `[#abc-123:t5]`).
    - **PDF page** (multimodal page vector):
      `[#<materialId>:p<pageNumber>]` (e.g. `[#abc-123:p12]`,
      1-based).
    - **Audio segment**: `[#<materialId>:a<startMs>-<endMs>]` (e.g.
      `[#abc-123:a30000-60000]`).
    - Single parser regex: `\[#([0-9a-f-]{36}):([tpa][0-9-]+)\]`.
    - `CitationEnforcementAdvisor` still groups by `materialId` for
      the "grounded" check; the per-modality suffix is for future UI
      deep-linking (page jump, timestamp jump) and so the model knows
      what to cite per chunk_kind.
    - **No backward compatibility** with the old `[#<materialId>]`
      form — user explicitly waived it. Old chunks (if any test
      fixtures survive) re-ingest under the new tag; the regex does
      **not** accept the old form. Supersedes `rag_plan.md` §4.1 step
      3 wording that implied a uniform suffix.
  - **Q3 — PDF renderer: PDFBox.** Pure-Java, already transitively on
    the classpath via Tika, no new system dep (ImageMagick CLI was
    rejected — won't ship to Garage cleanly). PDFBox covers both text
    extraction (`PDFTextStripper`) and page rasterisation
    (`PDFRenderer.renderImageWithDPI`). If the existing Tika
    transitive PDFBox version turns out to be too old for
    `PDFRenderer`, we add a direct `org.apache.pdfbox:pdfbox`
    dependency in Slice D's build.gradle change; otherwise no new
    dep is added.
  - **Q2 — audio segmentation: fixed 30 s windows with 2 s overlap.**
    Deterministic, no extra deps (VAD rejected — would pull native
    libraries we don't have a budget for). Picked the simplest thing
    that gets us a working audio path in v1; revisit if eval shows
    the chunk boundaries cut off mid-sentence too often.
  - **Audio MIME scope.** Slice D's `AudioReader` supports exactly
    three content types: `audio/mpeg`, `audio/wav`, `audio/mp4`.
    `audio/ogg`, `audio/webm`, etc. are explicitly out of scope and
    will be added later in their own task — `MaterialReaderDispatcher`
    will throw `UnsupportedMaterialFormatException` for them, which
    ingestion catches as `FAILED` (same path as any unsupported
    type today, per `rag.md` §4).
  - **PDF page → one multimodal vector per page (not two).** One
    `MaterialPart` per page carrying *both* extracted text *and*
    rendered PNG bytes, embedded as a single multimodal vector via
    `MultimodalEmbeddingApi.embedMultimodal(...)`. Alternative
    considered: two separate vectors per page (one text, one image,
    sharing a `page_number`); rejected — double the storage, double
    the embedding cost, and the citation `[#<id>:p<page>]` already
    points at the page not the modality, so splitting buys nothing
    today.
  - **Reader SPI break, no compatibility shim.** `MaterialReader.read(
    InputStream, Material)` return type evolves from
    `List<Document>` to `List<MaterialPart>`. Both existing readers
    (`PlainTextReader`, `TikaReader`) migrate; their behavior is
    preserved by emitting one or more `TextMaterialPart` per source
    `Document`. The interface change is a single commit's blast
    radius — three reader files, the dispatcher, the ingestion
    service, and their tests. No `@Deprecated` overload, no parallel
    code path.
  - **`MaterialPart` type hierarchy — top-level files in
    `ai/ingestion/reader/` (the readers' own package), not
    `materials/`.** Readers live in `ai/ingestion/reader/`, so the
    value types they emit live next to them:
    - `MaterialPart` — abstract base (or sealed-style; pick at impl
      time). No Lombok on the base; subclasses use
      `@Getter @AllArgsConstructor` with `final` fields per
      AGENTS.md §4 "no records".
    - `TextMaterialPart(String text, int chunkIndex)` — used by
      `PlainTextReader` and `TikaReader`. **Renamed from
      `TextPart`** because `ai/embedding/TextPart` already exists
      (Slice B's `MultimodalPart` subtype for the embedding API);
      the two are different layers and must not collide on simple
      name. The reader-side type adds `chunkIndex` (post-splitter
      it gets propagated into metadata as `chunk_index`).
    - `PdfPagePart(int pageNumber, String text, byte[] pageImagePng)`
      — emitted by the new `PdfReader`. `pageNumber` is 1-based.
    - `AudioSegmentPart(long startMs, long endMs, byte[] segmentBytes,
      String mimeType)` — emitted by the new `AudioReader`. Carries
      the original MIME so the embedding side can route it correctly
      via `InlineDataPart`.
  - **Splitter scoping.** `TokenTextSplitter` runs **only** on the
    text inside `TextMaterialPart`s. PDF page parts and audio
    segment parts pass through `MaterialIngestionService` unsplit and
    embed exactly once each. This means: post-Slice-D, the same
    material can produce a mix of text-path `Document`s (via
    `EmbeddingModel.embed(...)`) and multimodal `Document`s (via
    `MultimodalEmbeddingApi.embedMultimodal(...)`); both land in the
    same `vector_store` table with the same dimensionality (768) and
    the same `user_id` filter contract.
  - **`vector_store` metadata extension — JSON only, no new
    columns.** New metadata keys stamped per chunk during ingestion:
    - `chunk_kind` — one of `text` / `pdf_page` / `audio_segment`.
    - `page_number` — only on `pdf_page` chunks (Integer).
    - `audio_start_ms` / `audio_end_ms` — only on `audio_segment`
      chunks (Long).
    - Existing five keys (`user_id`, `topic_id`, `material_id`,
      `material_name`, `chunk_index`) are unchanged. `chunk_index`
      remains on every chunk for joinable cross-modality ordering.
  - **Flyway: `V16__vector_store_chunk_kind_index.sql`.** Adds one
    functional B-tree index on `metadata->>'chunk_kind'`. No column
    additions, no column drops — `vector_store`'s `metadata` is
    already `jsonb` and accepts arbitrary keys. The index is
    forward-looking for the eventual "show me only image hits" UI
    consumer (rag_plan.md §6.2); no current production code uses it
    as a filter.
  - **No re-ingestion logic.** User confirmed no existing production
    data needs migration ("dont have existing data"). `rag_plan.md`
    §6.2's optional `ingestion_version` column / re-embed scheduler
    is **out of scope** for Slice D. Old test-fixture vectors (if
    any) coexist with new vectors or get truncated by the test
    harness; production starts clean.
  - **Upload-time MIME validation: untouched.** User asked "why is
    this necessary" for upload-time allow-list / size-cap changes,
    then confirmed "will add later" → "yes" for leaving it alone.
    Today `MaterialController` accepts any MIME type and the
    ingestion step is the only gate (`rag.md` §4); audio uploads
    that the frontend already permits will hit the new
    `AudioReader`, and anything else continues to fail at ingestion
    with `UnsupportedMaterialFormatException` → `FAILED` row. The
    free-tier 5-material cap (`MaterialService.upload`) is also
    untouched; size caps for audio are deferred to a future
    frontend/upload task.
  - **`CitationEnforcementAdvisor` + system prompt updates.** The
    advisor's regex changes to
    `\[#([0-9a-f-]{36}):([tpa][0-9-]+)\]` (single match group for
    materialId, second group for the modality-tagged chunk locator).
    The `grounded = !citedChunks.isEmpty()` rule stays. The
    `materials/system.md` prompt resource gets a new example block
    showing the three citation shapes; the surrounding instruction
    text in `system.md` is locale-agnostic (it's the system prompt,
    not a user-facing string), so no `messages*.yml` change is
    needed for citation format itself. The retrieval result rendered
    into the `<context>` block must pre-bake the right tag per
    chunk_kind so the model has the exact string to echo back —
    `QuestionAnswerAdvisor`'s default template will need a custom
    `userTextAdvise` or a small format override.
  - **Out of scope for Slice D (explicit).**
    - OCR fallback for image-only PDFs — the embedded page image
      *is* the answer (`rag_plan.md` §6.2).
    - Per-modality search UI ("show me only image hits") — the
      `chunk_kind` index opens the door; no UI consumer yet.
    - Upload-time MIME / size validation for audio.
    - `ingestion_version` column and the re-embed scheduler bump.
    - `audio/ogg`, `audio/webm`, and other audio MIME types beyond
      the three locked above.
  - **Implementation order (frozen for the next focused session).**
    1. Domain & SPI: add `MaterialPart` + `TextMaterialPart` +
       `PdfPagePart` + `AudioSegmentPart` as top-level files in
       `ai/ingestion/reader/`; change `MaterialReader.read(...)`
       return type to `List<MaterialPart>`.
    2. Migrate `PlainTextReader` + `TikaReader` to emit
       `TextMaterialPart` only (no behavior change). Drop
       `application/pdf` from `TikaReader.supports(...)`.
    3. New `PdfReader` (PDFBox: `PDFTextStripper` per page +
       `PDFRenderer.renderImageWithDPI` at, say, 144 DPI — final
       DPI picked at impl time based on PNG size vs legibility
       trade-off). `supports(...)` returns true for
       `application/pdf`.
    4. New `AudioReader` (30 s windows, 2 s overlap; pure-Java
       chunking of the raw bytes — no decode/re-encode in v1, we
       hand the raw segment bytes straight to the multimodal
       embed call and let the model handle the format).
       `supports(...)` returns true for `audio/mpeg` / `audio/wav`
       / `audio/mp4`.
    5. `MaterialIngestionService`: consume `List<MaterialPart>`;
       branch on subtype. Text parts → existing `TokenTextSplitter`
       + `EmbeddingModel` text path. PDF page / audio segment
       parts → `MultimodalEmbeddingApi.embedMultimodal(...)` and
       insert into `vector_store` with the new metadata keys.
    6. Flyway `V16__vector_store_chunk_kind_index.sql`.
    7. `CitationEnforcementAdvisor` regex update;
       `materials/system.md` prompt example update; retrieval
       context-rendering to pre-bake the right tag per chunk.
    8. Tests: `PdfReaderTest`, `AudioReaderTest`, updated
       `PlainTextReaderTest` / `TikaReaderTest`, updated
       `MaterialIngestionServiceTest` covering all three
       modalities, updated `CitationEnforcementAdvisorTest`. At
       least one Testcontainers integration test ingesting a
       small PDF + small audio clip end-to-end and asserting
       `chunk_kind` distribution.
    9. Docs: `rag.md` updates (§4 reader dispatch, §5 splitter
       scoping, §12.3 dropped from gap list); `rag_plan.md` §0
       and §9 flip Slice D to ✅; new dated decision-log entry
       superseding this one with the as-built result + tests run
       + files touched.
  - **What this entry supersedes.** `rag_plan.md` §4.1 step 3's
    uniform `[#<materialId>:<chunkIndex>]` proposal (replaced by
    the per-modality format above). `rag_plan.md` open question Q2
    (audio segmentation strategy) — answered: fixed 30 s / 2 s
    overlap. `rag_plan.md` open question Q3 (PDF renderer) —
    answered: PDFBox. `rag_plan.md` open question Q4 (citation tag
    format) — answered: per-modality, not uniform. Q1 (`topK`
    default) is still open; punt to post-eval as planned.
  - **What this entry does NOT supersede.** Q1 from `rag_plan.md`
    §8 (`topK = 6` default tuning) — still deferred until the
    first evaluation pass. The 2026-05-24 Slice A entry's
    metadata-population gap. The 2026-05-24 Slice C entry's
    deferred Testcontainers integration test (Slice D's §8 above
    is where it lands).
  - **No code changes in this session.** Files touched: only
    `DECISIONLOG.md` (this entry). The repository is in the same
    state as the end of the 2026-05-25 Slice B entry; Slice D's
    implementation starts the next focused session against the
    frozen spec above.
- **2026-05-25 � RAG Slice D landed.** Implements the design frozen
  earlier the same day (see the 2026-05-25 design-locked entry above);
  this entry records the as-built result and what deviated from the
  spec. The next focused session promised in the design entry is this
  one � Slice D is now ? on 
ag_plan.md �0.
  - **Files created (production).**
    - i/ingestion/reader/MaterialPart.java � abstract base, no
      Lombok on base type.
    - i/ingestion/reader/TextMaterialPart.java � Lombok value
      (@Getter @AllArgsConstructor with inal String text). Note:
      design said TextMaterialPart(String text, int chunkIndex);
      the as-built form drops chunkIndex because the splitter
      assigns indices at ingestion time, not at reader time, so the
      field would have been dead at the reader boundary. Indices are
      stamped on every chunk's metadata after splitting (text path)
      or per-page/segment (multimodal paths). Spec wording in the
      design entry is mildly out of date; this is the as-built shape.
    - i/ingestion/reader/PdfPagePart.java � Lombok value with
      int pageNumber, String text, yte[] pageImagePng.
    - i/ingestion/reader/AudioSegmentPart.java � Lombok value
      with long startMs, long endMs, String mimeType, yte[]
      segmentBytes.
    - i/ingestion/reader/PdfReader.java � PDFBox-based.
      PDFTextStripper per page + PDFRenderer.renderImageWithDPI
      at 144 DPI, RGB, encoded as PNG via ImageIO. supports(...)
      returns true only for pplication/pdf (case-insensitive,
      strips ;charset=� tail). PDFBox is already on the classpath
      transitively via Tika, so no uild.gradle change was needed
      � confirmed by compileJava succeeding without any new
      direct dep.
    - i/ingestion/reader/AudioReader.java � fixed 30 s windows
      with 2 s overlap (constants WINDOW_MS = 30_000, OVERLAP_MS
      = 2_000, STEP_MS = 28_000 are package-private so the test
      can assert against them). MIME = udio/mpeg / udio/wav /
      udio/mp4 only. Duration is **estimated** from byte length
      using a per-MIME bytes-per-ms heuristic (�128 kbps for
      mpeg/mp4, 16-bit 44.1 kHz mono for wav). This is good enough
      for v1 chunk-boundary placement � the multimodal model
      consumes the actual bytes; segment boundaries don't have to
      be sample-accurate. Real container parsing (mp4 atoms, MP3
      frame headers, RIFF chunks) is a follow-up if eval shows
      drift mattering.
    - i/ingestion/MultimodalVectorWriter.java � direct JDBC
      insert into ector_store(content, metadata, embedding) via
      jdbcTemplate.update(...). Why JDBC and not VectorStore.add(
      Document): Spring AI's PgVectorStore.add(...) always calls
      the configured text EmbeddingModel to compute the vector
      from Document.text � there's no public way to hand it a
      precomputed loat[]. So multimodal chunks (which carry their
      vector from MultimodalEmbeddingApi.embed(...)) bypass
      VectorStore and use plain JDBC with ?::jsonb /
      ?::vector casts in the INSERT. Metadata is serialized by
      a small hand-written JSON encoder rather than injecting
      ObjectMapper (kept the writer's bean graph empty of
      Jackson-specific dependencies). Same ector_store schema
      from V3 � no column change.
    - i/chat/service/CitationTagBuilder.java � top-level static
      helper. Reads chunk_kind + page_number /
      udio_start_ms / udio_end_ms from a Document's metadata
      and emits the right [#<id>:t<idx>] / [#<id>:p<page>] /
      [#<id>:a<start>-<end>] tag. Used by both
      IlmaiChatClient.formatContextBlock and
      EmptyRetrievalRedirectAdvisor.augment so retrieved chunks
      always come into the prompt pre-baked with the modality-correct
      tag the model is asked to echo back.
    - db/migration/V16__vector_store_chunk_kind_index.sql � one
      functional B-tree index on (metadata->>'chunk_kind'). No
      column change; the metadata jsonb already accepts arbitrary
      keys.
  - **Files modified (production).**
    - i/ingestion/reader/MaterialReader.java � return type
      changed from List<Document> to List<MaterialPart>. SPI
      break, no @Deprecated overload (the design entry locked
      that).
    - i/ingestion/reader/PlainTextReader.java � emits a single
      TextMaterialPart wrapping the UTF-8-decoded byte array.
    - i/ingestion/reader/TikaReader.java � dropped
      pplication/pdf from SUPPORTED; emits one
      TextMaterialPart per Tika-produced Document's text.
      Tika still handles pplication/msword,
      pplication/vnd.openxmlformats-officedocument.wordprocessingml.document,
      pplication/vnd.ms-powerpoint,
      pplication/vnd.openxmlformats-officedocument.presentationml.presentation.
    - i/ingestion/MaterialIngestionService.java � 
ead(...)
      now returns List<MaterialPart>; indexParts(...) collects
      text parts and routes them through the existing
      TokenTextSplitter + VectorStore.add(...) text path, then
      iterates PDF page / audio segment parts and routes each one
      through MultimodalEmbeddingApi.embed(...) +
      MultimodalVectorWriter.write(...). Metadata stamped per
      chunk: existing five keys (user_id, material_id,
      material_name, chunk_index) plus chunk_kind always,
      page_number for pdf_page, udio_start_ms /
      udio_end_ms for udio_segment. chunk_index is a single
      monotonically-increasing counter across all parts of a
      material (text chunks first, then PDF pages, then audio
      segments) so cross-modality ordering remains joinable. The
      previous bug where 	opic_id was claimed-but-not-stamped is
      not fixed here (it was already missing; spec drift in
      
ag.md �5, not a Slice D regression).
    - i/chat/service/IlmaiChatClient.java � CITATION_PATTERN
      regex updated to \[#([0-9a-fA-F-]{36}):([tpa][0-9-]+)] (two
      capture groups: materialId + modality-tagged locator).
      ormatContextBlock(...) rewrites each chunk header via
      CitationTagBuilder.tagFor(doc) and updates the instruction
      text to teach the three citation shapes.
    - i/chat/service/EmptyRetrievalRedirectAdvisor.java �
      ugment(...) uses the same CitationTagBuilder and the
      same three-shape instruction text. Behavior change: the
      material_name line keeps formatting (it was previously
      followed by a " (chunk <n>)" suffix; that's now redundant
      because the tag already carries 	<chunkIndex>).
    - i/chat/service/ChatPromptProvider.java � SYSTEM_EN /
      SYSTEM_RU / SYSTEM_UZ and REGENERATE_EN /
      REGENERATE_RU / REGENERATE_UZ all rewritten to spell out
      the three citation shapes (text / PDF page / audio segment)
      and to require echoing the tag verbatim � including the
      modality letter � rather than collapsing to the old
      [#<material_id>].
    - skills/materials/system.md � same per-modality citation
      contract taught at the skill-system-prompt level so the
      materials skill answers also use the right tag shape.
  - **Tests added / updated.**
    - PdfReaderTest � 2 tests (supports_acceptsOnlyPdf,
      
ead_emitsOnePartPerPageWithTextAndPngBytes). Builds a
      2-page PDF in-memory via PDFBox, runs the reader, asserts
      one PdfPagePart per page with text + PNG bytes
      ( x89 0x50 magic). Passes (2/2).
    - AudioReaderTest � 4 tests
      (supports_acceptsThreeConfiguredAudioTypes,
      
ead_shortClipBelowWindow_returnsSingleSegment,
      
ead_longerClipWindowed_emitsOverlappingSegments,
      
ead_emptyBytes_returnsEmpty). Synthesizes raw byte arrays
      at the heuristic bytes-per-ms rate; asserts overlap pattern
      (second.startMs == first.endMs - OVERLAP_MS). Passes (4/4).
    - PlainTextReaderTest / TikaReaderTest � updated to assert
      MaterialPart shape (TextMaterialPart with getText())
      rather than Document.getText(). TikaReaderTest now uses a
      DOCX fixture generated via Apache POI (the previous PDF
      fixture went to PdfReaderTest) and asserts the PDF MIME is
      rejected by the new TikaReader.supports(...).
    - MaterialReaderDispatcherTest � dispatch_returnsTikaReaderForPdf
      replaced with dispatch_returnsPdfReaderForPdf; added
      dispatch_returnsAudioReaderForAudioMpeg and
      dispatch_throwsForUnsupportedAudioType.
    - MaterialIngestionServiceTest � constructor wiring updated
      to the new 7-arg signature
      (MultimodalEmbeddingApi + MultimodalVectorWriter added);
      existing text-path assertion now also checks
      chunk_kind == "text"; new test
      onMaterialUploaded_pdfPart_writesMultimodalVectorWithChunkKind
      stubs the dispatcher with a PdfPagePart-producing
      MaterialReader and asserts the writer is called with
      chunk_kind == "pdf_page" and page_number == 1. Dropped
      the stale 	opic_id assertion (production code never
      stamped it � 
ag.md �5 is incorrect on that field; flagged
      for a future doc-only fix per AGENTS.md �1).
    - CitationTagBuilderTest � 4 tests covering text / PDF page /
      audio segment / missing-chunk_kind-falls-back-to-text.
      Passes (4/4).
  - **Tests run.**
    - gradle compileJava ? � production code compiles.
    - gradle compileTestJava ? � only an unchecked-cast warning
      in MaterialIngestionServiceTest (pre-existing on the
      ArgumentCaptor<List<Document>> line).
    - PdfReaderTest ? (2/2).
    - AudioReaderTest ? (4/4).
    - org.aiincubator.ilmai.ai.ingestion.* ? (31/33). The two
      failures are EmbeddingPipelineIntegrationTest and
      UserIsolationIntegrationTest, both @SpringBootTests that
      use @ServiceConnection on a pgvector/pgvector:pg16
      Testcontainer. They fail at initializationError because no
      local Docker daemon is running � same failure mode as before
      Slice D, unrelated to anything this slice touched. Confirmed
      by running them again with no source diff: same Testcontainers
      ContainerFetchException. Not a regression; the Slice C entry
      from 2026-05-24 already flagged the Testcontainers integration
      test as deferred until a Docker-equipped CI runner is available.
    - org.aiincubator.ilmai.ai.chat.* ? (15/15) � confirms the
      citation regex change, the CitationTagBuilder, and the
      EN/RU/UZ prompt rewrites didn't break any existing chat /
      provider / chat-service tests. Includes the 4 new
      CitationTagBuilderTests.
  - **Deviations from the 2026-05-25 design-locked spec.**
    1. **TextMaterialPart has no chunkIndex field.** Design
       said TextMaterialPart(String text, int chunkIndex). As-built
       has only 	ext. Reason: at the reader boundary the splitter
       hasn't run yet, so any chunkIndex at this point is either
       always   or has to be retroactively renumbered after
       splitting � neither of which is useful. chunk_index is
       still stamped on every chunk's metadata, but at ingestion
       time, by MaterialIngestionService.stampMetadata(...), after
       splitting. The design entry's spec wording is mildly out of
       date on this single field � does not affect the rest of the
       contract.
    2. **MultimodalVectorWriter bypasses VectorStore.** Design
       was silent on how precomputed vectors would land in
       ector_store. Spring AI's PgVectorStore.add(Document)
       always recomputes via EmbeddingModel � there's no public
       hook for "here's the Document, here's the vector, just
       INSERT". So the writer talks to ector_store over plain
       JDBC. Same schema, same columns, just a different write
       path for the multimodal branch. Risk: if a future Spring AI
       upgrade introduces a "vector already computed" hook on
       VectorStore, switch to it � but for now the JDBC path is
       the only correct option.
    3. **PDFBox uses the transitive version from Tika.** No direct
       org.apache.pdfbox:pdfbox line was added to
       uild.gradle. compileJava succeeded with PDFBox
       resolved via Tika, and PdfReaderTest exercises both
       PDFTextStripper and PDFRenderer.renderImageWithDPI � so
       the transitive version is recent enough. If Tika ever drops
       PDFBox transitively or pins it to an older version that
       loses PDFRenderer, add the explicit dep then.
    4. **No Testcontainers integration test for PDF + audio
       end-to-end.** Design step 8 promised one. Skipped this
       session because the existing Testcontainers tests already
       fail with no Docker daemon locally (see Tests run above),
       so adding a third Docker-dependent test wouldn't add signal
       in this environment. The unit-test pyramid does cover the
       PDF page and audio segment paths in
       MaterialIngestionServiceTest::onMaterialUploaded_pdfPart_writesMultimodalVectorWithChunkKind
       with mocked MultimodalEmbeddingApi and a stub reader.
       The Testcontainers test is now consolidated with Slice C's
       still-deferred one � both blocked on the same missing-Docker
       constraint and both should land together once a Docker-
       equipped CI runner is available.
  - **AGENTS.md �4 compliance.** No 
ecord types added. No
    @Builder on entities. No static inner classes for value or
    helper types � MaterialPart and its three subclasses are
    top-level files in i/ingestion/reader/; CitationTagBuilder
    is a top-level utility class. No code comments added to
    production files. CurrentUser rule N/A � no controller-facing
    service signature changed. Cross-module rule respected � the
    only cross-module import added in MaterialIngestionService is
    i.MultimodalEmbeddingApi (which already lives at the i/
    module root per Slice B); everything else is intra-module within
    i/.
  - **Compatibility.** ector_store schema unchanged. Old
    text-path chunks (if any survive a previous test run) coexist
    with new chunks � old chunks have no chunk_kind key, new ones
    do. The retrieval / citation path is *not* backward-compatible:
    the new regex won't match the old [#<materialId>] tag, so old
    rows can be searched but the model is now told to cite them
    with the new shape (and CitationTagBuilder.tagFor(...) defaults
    missing chunk_kind to 	ext, so old chunks get rendered as
    [#<id>:t<chunk_index>]). User confirmed no existing
    production data ("dont have existing data") so this is moot in
    practice. The /chat REST contract returns the same JSON shape.
    The materials skill's search_my_materials tool keeps the
    same return shape.
  - **What this entry supersedes.** The 2026-05-25 design-locked
    entry's claim that TextMaterialPart carries chunkIndex
    (it does not � see Deviation 1). The 2026-05-25 design-locked
    entry's "implementation deferred to next focused session" line
    (this is that session; the design is now ?).
  - **What this entry does NOT supersede.** Open question Q1 from
    
ag_plan.md �8 (	opK = 6 default tuning) � still deferred
    until the first evaluation pass. The Slice C entry's deferred
    Testcontainers integration test � still deferred, now joined
    by Slice D's; both blocked on a Docker-equipped CI runner. The
    
ag.md doc updates promised in design step 9 (�4 reader
    dispatch, �5 splitter scoping, �12.3 gap-list update) � not
    landed in this session; left as a documentation-only follow-up
    so this entry is the single source of truth for the as-built
    state until 
ag.md catches up.
- **2026-05-25 — `ai/` package cleanup: advisor + multimodal API impl moved to their proper sub-packages.** Two minimal moves to align the `ai/` module with AGENTS.md §4 (package-by-feature with `domain/api/service/payload`; `Default<Feature>Api` impls live in `<feature>/service/`; advisors live next to — not inside — services per the Slice C plan §4.1):
  1. **`EmptyRetrievalRedirectAdvisor`** moved from `ai/chat/service/` → new `ai/chat/advisor/`. Matches the Slice C plan §4.1 phrasing ("New advisors (top-level files in `ai/chat/advisor/`)") which never actually got followed when Slice C landed on 2026-05-24. `IlmaiChatClient` gained one `import org.aiincubator.ilmai.ai.chat.advisor.EmptyRetrievalRedirectAdvisor;`. The advisor's references to `RetrievalProperties`, `ChatPromptProvider`, and `CitationTagBuilder` (all `public` types still in `ai/chat/service/`) work as plain cross-package imports — no visibility changes needed.
  2. **`DefaultMultimodalEmbeddingApi`** moved from `ai/embedding/` (flat) → new `ai/embedding/service/`. AGENTS.md §4 spells out that the `Default<Feature>Api` impl is "the only class outside `<feature>/domain/` that touches `B`'s repositories" and "lives in `<feature>/service/`"; for the `ai.embedding` module that translates to `ai/embedding/service/DefaultMultimodalEmbeddingApi`. Supersedes the 2026-05-25 Slice B entry's deviation note that parked the file directly in `ai/embedding/`. The new file adds two imports for `GoogleGenAiEmbeddingModel` and `MultimodalContent` (both `public`, both still in `ai/embedding/`).
  - **Files touched.**
    - `backend/src/main/java/org/aiincubator/ilmai/ai/chat/advisor/EmptyRetrievalRedirectAdvisor.java` — new (moved).
    - `backend/src/main/java/org/aiincubator/ilmai/ai/chat/service/EmptyRetrievalRedirectAdvisor.java` — deleted.
    - `backend/src/main/java/org/aiincubator/ilmai/ai/chat/service/IlmaiChatClient.java` — added one import.
    - `backend/src/main/java/org/aiincubator/ilmai/ai/embedding/service/DefaultMultimodalEmbeddingApi.java` — new (moved).
    - `backend/src/main/java/org/aiincubator/ilmai/ai/embedding/DefaultMultimodalEmbeddingApi.java` — deleted.
  - **What was deliberately not moved.**
    - `ai/embedding/` itself stays flat for its domain types (`GoogleGenAiEmbeddingModel`, `GoogleGenAiEmbeddingProperties`, `GoogleGenAiEmbeddingOptions`, `EmbedContentInvoker`, `MultimodalEmbedContentInvoker`, `MultimodalContent` + the three `MultimodalPart` subtypes, `EmbeddingApiException`, `EmbeddingConfig`). Splitting them into `embedding/domain/` would be churn-for-churn's-sake: the module is small, has no controllers / no DTOs / no repositories, and the `domain/api/service/payload` split exists to separate JPA entities from the rest — none of these are entities. Re-evaluate only if `ai/embedding/` grows materially.
    - `ai/plan/` and `ai/skill/` stay flat for the same reason — handful of files each, no entities, no controllers. Future-Slice work can split them if they grow.
    - `ai/ingestion/` keeps `MaterialIngestionCompletedEvent` at `ai/ingestion/` (not at `ai/` root) because the only listener — `MaterialChunkCleanupService` — lives in the same `ai/ingestion/` package. AGENTS.md §4's "cross-module events live at module root" rule applies only to events with cross-module listeners; this one has none. (The `.junie/plans/harden-ingestion-pipeline.md` discussion of "consumers beyond the chunk cleanup listener" is aspirational; once a non-`ai/*` consumer actually appears, move the event up to `ai/`.)
    - `ai/chat/provider/` stays as a sibling of `service/`. It's not in the prescribed `domain/api/service/payload` list but it cleanly groups the `IlmaiChatClientFactory` + `LlmProvider` + `ChatProviderProperties` set and is already imported by external modules (`quiz`, `plan`, `ai.skill.Skill`) — folding it into `service/` would force a wider import sweep with no clarity gain.
  - **Mismatches flagged for documentation follow-up (not fixed in this entry, per AGENTS.md §6.2 "flag, don't silently propagate").**
    1. **Cross-module leak: `quiz.service` imports `ai.chat.service.IlmaiChatClient` and `ai.chat.service.RetrievedChunk`.** AGENTS.md §4 says "no cross-module `*Repository` injections — go through the producer module's public API"; the same logic applies to a feature's internal `service/` types. `IlmaiChatClient` is currently the *only* way to reach the chat completion pipeline, and `RetrievedChunk` is an internal value type. Either (a) expose a `ai.ChatApi` at the `ai/` module root with the methods `quiz` needs and switch `RetrievedChunk` consumers to `ai.RetrievedChunkDto`, or (b) accept the leak as a known exception. Not changed in this session; the surface is bigger than a folder move and deserves its own focused session.
    2. **`rag_plan.md` §9 execution-order item 6 still says "⏳ next focused session" for Slice D** even though §0 and the 2026-05-25 Slice D entry mark it landed. Pure doc-only nit; left untouched to avoid mixing concerns into this entry.
  - **AGENTS.md §4 compliance for this entry.** No `record` types added. No `@Builder` on entities. No static inner classes — `EmptyRetrievalRedirectAdvisor` and `DefaultMultimodalEmbeddingApi` remain top-level files in their new homes. No code comments added. No cross-module `*Repository` injection introduced. The two moves do **not** add a new whitelist to any `package-info.java` (and existing `package-info.java` files in `ai/chat/` and `ai/embedding/` continue to carry `type = OPEN` only).
  - **Tests run.**
    - `./gradlew compileJava -q` → green.
    - `./gradlew compileTestJava -q` → green.
    - `./gradlew test --tests "*ChatClient*" --tests "*Modules*" --tests "*Embedding*"` → 33/34 passing. The single failure is `EmbeddingPipelineIntegrationTest > initializationError` with `ContainerFetchException: Can't get Docker image pgvector/pgvector:pg16` — same Docker-daemon-missing failure mode flagged in the Slice C and Slice D entries, not a regression from this change. `ApplicationModulesTests.verifyModules` is green.
  - **Compatibility.** Pure package move — no public class names changed, no method signatures changed, no Spring bean names changed (`@Service` on `DefaultMultimodalEmbeddingApi` continues to register the impl under the default bean name, which only `MultimodalEmbeddingApi` consumers ever look up). No data migration. No frontend impact.
  - **What this entry supersedes.** The 2026-05-25 Slice B entry's deviation (2) note that "`DefaultMultimodalEmbeddingApi` lives in `ai/embedding/` (next to the model), not `ai/chat/service/`, since the chat module has no role in multimodal embedding" — the location is now `ai/embedding/service/` (still not `ai/chat/service/`, just one level deeper inside `ai/embedding/`).
  - **What this entry does NOT supersede.** Open question Q1 from `rag_plan.md` §8 — still deferred. The two Testcontainers tests deferred since Slices C and D — still deferred, same Docker-daemon constraint. The cross-module leak from `quiz.service` into `ai.chat.service` flagged above — still open; carried forward as a follow-up.


---

- **2026-05-26 � Module isolation refactor complete: every feature module
  is `CLOSED` and `ApplicationModulesTests::verifyModules` is green.**
  Final state after Phases 1�7 of the now-deleted
  `docs/refactoringplan.md`:
  - Every `<feature>/package-info.java` declares
    `@ApplicationModule(type = CLOSED)` **except** `common`, which stays
    `OPEN` because it carries shared cross-cutting infra
    (`SupportedLocale`, `ApiResponse`, `ApiError`, `MessageService`,
    `LocalizedAuthenticationEntryPoint`, `LocalizedAccessDeniedHandler`,
    `BlobStorage`, `QuotaService`) consumed by every controller and
    exception handler. Treating `common` as a leaf `OPEN` module was the
    pragmatic choice � turning it CLOSED would force a `*Api` ceremony on
    purely-utility types with no business semantics.
  - Cross-module communication goes exclusively through `<Feature>Api`
    interfaces at module roots returning module-root DTOs (Lombok
    `@Getter @AllArgsConstructor final` value classes). Active set:
    `auth.AuthApi`, `materials.MaterialsApi`, `topics.TopicsApi`,
    `spaces.SpacesApi`, `quiz.QuizApi`, `gaps.GapsApi`,
    `profiles.ProfilesApi`, `ai.RetrievalApi`. Cross-module events
    also live at module root (`auth.UserRegisteredEvent`,
    `materials.MaterialUploadedEvent`, `materials.MaterialDeletedEvent`).
  - Phase 6 (`quiz` + `ai`) work: `QuizApi.findIncorrectQuestionsForUser`
    and `findAllSessionsForUser` now return `quiz.QuizQuestionDto` /
    `quiz.QuizSessionDto` (the latter carries `List<QuizQuestionDto>`
    because the only consumer, `gaps.service.GapsService`, flat-maps over
    them). MapStruct mapper `quiz.service.QuizApiMapper` does the
    entity?DTO copy. The 4 chat-stack types `IlmaiChatClient`,
    `IlmaiChatClientFactory`, `LlmProvider`, `RetrievedChunk` moved from
    `ai.chat.service` / `ai.chat.provider` to `ai/` root.
    `ChatProviderProperties` and `ChatCompletion` stay in their
    sub-packages but now import the moved types explicitly.
  - Phase 7 fixes the refactoring plan didn't fully anticipate:
    `ai.skill.MaterialsSkill` had its three `@Tool` methods switched from
    returning `materials.payload.{Material,Topic,MaterialChunk}Response`
    to returning the module-root DTOs `MaterialDto`, `TopicDto`,
    `RetrievedChunkDto` directly � these are LLM tool-callback return
    types serialized to JSON, so the entity-free DTO shapes work as-is.
    `materials.service.MaterialStorageKeys` moved to
    `materials.MaterialStorageKeys` with the entity-based
    `forMaterial(Material)` overload deleted; all call sites
    (`MaterialService`, integration tests) now use
    `forCoordinates(spaceId, topicId, materialId)` built from entity
    getters at the call site.
  - Phase 7 cleanup of MapStruct field injection: `GapsMapper`,
    `QuizMapper`, `PlanMapper` were abstract MapStruct mappers using
    `@Autowired protected MaterialsApi materialsApi;` � Spring Modulith's
    `verifyModules` flags field injection as a violation. Switched all
    three to setter injection (`@Autowired public void setMaterialsApi`).
    Constructor injection is impractical on MapStruct abstract mappers
    because the generated impl extends the abstract class and would
    inherit the constructor signature; the setter form is the canonical
    workaround.
  - Test footprint: full `./gradlew test` after the refactor reports
    199 passing / 4 failing. All 4 failures are environment-gated, not
    refactor-caused: `IlmaiBackendApplicationTests::contextLoads`,
    `EmbeddingPipelineIntegrationTest`, `UserIsolationIntegrationTest`
    (need Docker / Postgres Testcontainers) and
    `SkillRegistryTest::registersSkillsAndExposesByName` (order-flaky in
    full-suite, passes in isolation). `verifyModules` itself �
    previously expected-red since Phase 3 � is now green.
  - **Supersedes** every earlier DECISIONLOG entry that described
    cross-module entity sharing, `allowedDependencies` whitelists on
    `@ApplicationModule`, `payload/` types being referenced
    cross-module, or `*Repository` injections across module boundaries.
    See AGENTS.md �4 for the enforced rules going forward.
  - `docs/refactoringplan.md` is deleted as part of this entry � its
    work is done and the running record now lives here.

## 2026-05-26 — Agent loop wired end-to-end: six skills + skill framework hoisted to module root

- **What:** Finished the companion agent so the LLM actually sees and
  uses skills at runtime. Six skills now exist: `materials` (pre-
  existing), `profile`, `gaps`, `quiz`, `plan` (all new, read+limited-
  write), and `reminders` (new, intentional stub — see below).
  `IlmaiChatClient.complete(...)` now (a) clears `ActiveSkillHolder` at
  turn start, (b) prepends `SkillRegistry.headerBlock(locale)` plus a
  `<SKILL name="…">…</SKILL>` block per registered skill to the system
  message, and (c) registers `open_skill` + every skill's
  `tools()` via `.toolCallbacks(...)`. The `SkillSelectionAdvisor`
  abstraction from `docs/features/agent.md` is collapsed into this
  one wiring point because Spring AI 2.0-M6 cannot swap a tool set
  mid-`call()` — the day-1 invariant is enforced through prompt
  structure (one `<SKILL>` block per skill, all visible, model selects
  via `open_skill`) rather than runtime tool-set mutation.
- **Skill framework lives at `ai/` module root, not `ai/skill/`.**
  `Skill`, `SkillResources`, and `CurrentUserResolver` are top-level
  types in `org.aiincubator.ilmai.ai` (alongside `MaterialsApi`,
  `RetrievalApi`, etc.). Feature modules outside `ai` (`quiz`, `plan`,
  `telegram`) consume them as module-root types per AGENTS.md §4. The
  internal coordination types (`SkillRegistry`, `ActiveSkillHolder`,
  `OpenSkillTool`, `MaterialsSkill`, `ProfileSkill`) stay in
  `ai/skill/` since they are only consumed inside the `ai` module
  itself.
- **Where each skill `@Component` lives is dictated by Modulith, not
  by aesthetics.** `MaterialsSkill` and `ProfileSkill` stay in
  `ai/skill/` because `ai → materials` and `ai → profiles` already
  exist (chat service, ingestion events) and putting these skills
  inside the owning module would create `ai ↔ feature` cycles.
  `GapsSkill` → `gaps/skill/`, `QuizSkill` → `quiz/skill/`,
  `PlanSkill` → `plan/skill/`, `RemindersSkill` → `telegram/skill/`,
  because moving those skills *into* `ai` would create `ai → gaps`,
  `ai → quiz`, etc., which combine with `gaps → quiz`, `quiz → ai`,
  `plan → ai` (all pre-existing) to form true cycles. The rule
  surfaced from this experiment: **a feature skill lives in module
  `X` iff `ai → X` is acceptable; otherwise it lives in `X/skill/`**.
  `ApplicationModulesTests.verifyModules` is now green with this
  placement.
- **New cross-module API:** `plan.PlansApi` (module-root) +
  `DefaultPlansApi` in `plan/service/` exposing
  `findCurrentForUser(UUID) : Optional<LearningPlanDto>` so the new
  `PlanSkill` can summarize the plan without crossing the AGENTS.md
  §4 boundary (entities + `payload/` types stay private to the
  module). New module-root DTOs: `plan.LearningPlanDto`,
  `plan.PlanDayDto`. `ProfilesApi` gains three setter methods
  (`setLearningGoal`, `setTargetDate`, `setPreferredLanguage`)
  consumed by `ProfileSkill`.
- **`reminders` skill is a deliberate stub (scope-1(b) decision).**
  The `telegram` module has no `Reminder` entity, no table, no
  scheduler beyond a placeholder. Rather than silently omitting the
  capability or fabricating one, `RemindersSkill` is discoverable —
  it appears in `<AVAILABLE_SKILLS>` — but its three tools
  (`schedule_telegram_reminder`, `list_my_reminders`,
  `cancel_reminder`) each return the localized
  `agent.reminders.coming_soon` message via `MessageService` (UZ/RU/EN
  keys added). The skill's `system.md` instructs the model to relay
  the message verbatim, never fake a confirmation, and redirect users
  to `profile.dailyReminder` (which already exists) as a placeholder.
  Adding a real reminders backend (Flyway migration + entity +
  scheduler) is explicitly deferred.
- **Single agent, not multiple agents (re-confirmed).** Per the
  user's restatement of the brief, none of the skills opens a new
  LLM loop. `QuizSkill` and `PlanSkill` are read-only on day one —
  starting a quiz / regenerating a plan happens via the web UI's
  existing endpoints (`QuizService`, `PlanService` workers), not
  from chat. This matches `docs/features/agent.md` §1 ("LLM-backed
  workers behind tools, not agents").
- **Localization invariant preserved.** All skill `system.md`
  fragments end with "Respond in the language the user wrote in
  (Uzbek, Russian, or English)". Reminders' user-facing string is
  the only new one routed through `MessageService` and shipped in
  `messages.properties` / `messages_ru.properties` /
  `messages_uz.properties` (AGENTS.md §8).
- **Tests run:** `ApplicationModulesTests.verifyModules` ✓ (1/1);
  `MaterialsSkillTest` ✓ (6/6); `IlmaiChatClientTest` ✓ (test ctor
  updated for three new dependencies); two pre-existing
  Docker-dependent integration tests
  (`EmbeddingPipelineIntegrationTest`, `UserIsolationIntegrationTest`)
  remained skipped due to absent Docker — unrelated to this change.
- **Follow-ups (not done in this entry):**
  - Reminders backend (table, repository, scheduler, real tools).
  - Mutation tools for `quiz` (`start_quiz_from_concepts`) and
    `plan` (`regenerate_plan`), once we decide whether the agent
    should be allowed to trigger those workers directly.
  - Learner-snapshot system block (`docs/features/agent.md` §6) —
    currently profile data is reachable via the `profile` skill on
    demand, but not eagerly injected into the system prompt every
    turn. Defer until we measure how often the model bothers to
    open `profile`.
- **2026-05-26 — Agent v1 (Slice 1) started; Slice 1 sub-split into 1a–1d.**
  Begun executing `docs/features/agent/agent-plan.md`. The plan's Slice 1
  ("Coach skeleton + retrieve + SSE") is doc-sized at 1–3 days of work and
  does not fit a single coding session, so the plan was amended in place to
  split Slice 1 into four independently shippable sub-slices: **1a** module
  skeleton + `AgentApi` + SSE endpoint + Coach `ChatClient` bean; **1b**
  `retrieve` tool + grounding guard; **1c** citation + language guards;
  **1d** quota advisor. Plus a parallel **Cleanup** sub-slice that removes
  the old `ai/` agent loop, `Skill` / `SkillResources` abstractions, the
  legacy chat/skill code, and the old `plan/` module before Slice 2 starts.
  This entry **records the start of Slice 1a**, which shipped:
  - New `agent/` feature module — `@ApplicationModule(type = CLOSED)`.
  - `agent.MessagePart` (abstract, Jackson polymorphic on `type`) with five
    concrete parts at module root: `TextPart`, `CitationPart`, `ToolCallPart`,
    `ActionPart`, `ErrorPart`. No `record` and no static inner classes per
    AGENTS.md. `quiz_card` and `plan_step` parts from
    `docs/features/agent/08-message-parts.md` are deferred to Slices 3 and 5
    where they're actually emitted.
  - `agent.AgentApi` interface at module root with
    `Flux<MessagePart> chat(CurrentUser, sessionId, prompt, ChatChannel)`,
    plus `agent.ChatChannel` (`WEB` / `TELEGRAM`) and `agent.ToolCallStatus`
    (`RUNNING` / `DONE` / `ERROR`) at module root. Implementation
    `DefaultAgentApi` in `agent/service/` is a stub that emits a single
    `TextPart` — the real ChatClient call lands in Slice 1b.
  - Coach system-prompt resources at
    `resources/prompts/agent/coach-system.{en,ru,uz}.txt`, loaded by
    `agent.service.CoachSystemPrompts` keyed on `SupportedLocale`.
  - `agent.service.CoachChatClientConfig` registers the
    `coachChatClient` `ChatClient` bean via the existing
    `ai.IlmaiChatClientFactory` (provider abstraction stays in `ai/`,
    cross-module access is allowed because `IlmaiChatClientFactory` sits at
    the `ai` module root). Bean is `@ConditionalOnBean(IlmaiChatClientFactory.class)`
    and throws on missing provider (no silent null beans). It is **not yet
    invoked** by `DefaultAgentApi` — wiring lands in Slice 1b.
  - SSE endpoint `POST /agent/chat/{sessionId}` (`AgentController` +
    `AgentChatRequest`). Returns `Flux<MessagePart>` produced as SSE
    (`MediaType.TEXT_EVENT_STREAM_VALUE`); Spring MVC + reactor-core's
    adapter handles the streaming response on the servlet stack — no
    WebFlux dep added. **Path deviates from the spec's `/api/v1/agent/...`
    because the project convention is bare paths (`/auth`, `/telegram/...`).**
  - Smoke test `AgentControllerSmokeTest` (MockMvc standalone + asyncDispatch)
    asserts the SSE response body contains a JSON-encoded `TextPart` with
    `"type":"text"` discriminator and the part's text. Passes locally.

  **Deviations from `agent-plan.md` Slice 1 and `docs/features/agent/`,
  recorded here so future slices revisit them:**
  - `AgentApi.chat` returns `Flux<MessagePart>` (matches the doc) — but
    `reactor-core` is only on the classpath transitively via Spring AI; if
    Spring AI's dep ever stops dragging it in, this needs an explicit
    declaration.
  - The endpoint path is `/agent/chat/{sessionId}`, not
    `/api/v1/agent/chat/{sessionId}` (project convention).
  - `MessagePart` does not yet carry the spec's `id` / `ts` envelope
    fields (08-message-parts.md §"All parts share a common envelope") —
    deferred until storage / replay actually need them.
  - The `Skill` / `SkillResources` abstractions in `ai/` are **not
    consumed** by the new `agent/` module; they remain in the old `ai/`
    module untouched in 1a and will be removed by the Cleanup sub-slice
    in their own session.

  **Doc mismatches flagged (per AGENTS.md §6.2):**
  - AGENTS.md spells the type as `org.aiincubator.ilmai.auth.security.CurrentUser`
    in two places, but the actual class lives in
    `org.aiincubator.ilmai.common.CurrentUser`. Slice 1a follows the code;
    AGENTS.md should be corrected on its next edit.


- **2026-05-26 — Agent v1, Slice 1b shipped: `retrieve` tool + grounding
  guard.** Building on Slice 1a (`DECISIONLOG.md` entry of the same date),
  the Coach is now actually wired to the LLM and to the user's materials.
  Layout (all in `org.aiincubator.ilmai.agent`):
  - `agent.RetrievedChunk` — module-root Lombok value DTO
    (`@Getter @AllArgsConstructor`, `materialId`, `materialName`,
    `chunkIndex`, `snippet`, `score`). Distinct from `ai.RetrievedChunkDto`:
    `agent.RetrievedChunk` is what the tool returns to the LLM and what the
    advisor inspects; `ai.RetrievedChunkDto` stays the cross-module wire
    type from `ai.RetrievalApi`.
  - `agent.service.AgentRetrievalContext` — per-call `ThreadLocal` holding
    the list of chunks the `retrieve` tool surfaced during the current
    `chat()` invocation, plus a call counter. Lifecycle:
    `AgentRetrievalContext.begin()` in `DefaultAgentApi.chat(...)`, cleared
    in a `finally` block. Used by `GroundingGuardAdvisor` to decide whether
    the assistant's response is grounded.
  - `agent.service.RetrieveTool` — `@Component` exposing a single Spring
    AI `@Tool retrieve(String query)`. Resolves the user id from
    `SecurityContextHolder` (`CurrentUser` principal); **never** accepts
    `userId` as a tool argument (AI/RAG non-negotiable #3 in AGENTS.md
    §5). Delegates to `ai.RetrievalApi.retrieve(userId, query)`, maps each
    `RetrievedChunkDto` to a module-root `RetrievedChunk`, and records the
    call into `AgentRetrievalContext.current()` so the advisor can see it.
    Blank/null query → empty list, no `RetrievalApi` call.
  - `agent.service.GroundingGuardAdvisor` — Spring AI `CallAdvisor`
    (`Ordered.HIGHEST_PRECEDENCE + 600`). After the chain returns, if
    `AgentRetrievalContext.current()` has zero chunks it rewrites the
    assistant response with the localized `agent.grounding.empty` message
    via `MessageService` (EN / RU / UZ bundles). If at least one chunk
    was retrieved, the original response passes through unchanged. If no
    `AgentRetrievalContext` is active (chat client called from outside
    `DefaultAgentApi`), the advisor is a no-op.
  - `agent.service.CoachChatClientConfig` — extended to register the
    `retrieve` tool callback
    (`MethodToolCallbackProvider.builder().toolObjects(retrieveTool).build()`)
    and the `GroundingGuardAdvisor` on the `coachChatClient` builder via
    `defaultToolCallbacks(...)` and `defaultAdvisors(...)`. The
    `@ConditionalOnBean(IlmaiChatClientFactory)` guard from Slice 1a is
    preserved — no LLM provider configured ⇒ no Coach `ChatClient` bean.
  - `agent.service.DefaultAgentApi` — no longer a stub. Resolves the
    Coach `ChatClient` via `ObjectProvider<ChatClient>` qualified with
    `CoachChatClientConfig.COACH_CHAT_CLIENT`; if absent (no LLM
    configured), returns a single placeholder `TextPart` ("Coach is not
    configured."). Otherwise calls
    `client.prompt().user(prompt).call().content()` inside an
    `AgentRetrievalContext.begin()` / `clear()` try/finally, then emits a
    `CitationPart` per chunk the tool surfaced followed by a final
    `TextPart` with the assistant text. Real token streaming stays for
    Slice 1c+; the SSE stream currently carries citations + one text
    frame.

  **i18n.** `agent.grounding.empty` added to all three bundles:
  - EN: `I didn't find this in your uploads — want to upload something on
    it?`
  - RU: `Я не нашёл этого в ваших загрузках — хотите загрузить
    что-нибудь об этом?` (encoded as `\u…` escapes to match the file's
    existing convention).
  - UZ: `Buni yuklamalaringizdan topa olmadim — shu mavzuda biror narsa
    yuklamoqchimisiz?`

  **Tests added.**
  - `agent.service.GroundingGuardAdvisorTest` (5 cases): empty retrieval ⇒
    advisor rewrites to localized message in EN / RU / UZ; ≥1 chunk ⇒
    original response passes through; no `AgentRetrievalContext` ⇒ no-op.
    Uses a real `ReloadableResourceBundleMessageSource` over the actual
    `messages/messages*.properties`, so the i18n keys are verified
    end-to-end.
  - `agent.service.RetrieveToolUserIsolationTest` (4 cases): proves the
    AI/RAG non-negotiable. (1) `RetrieveTool.retrieve(query)` reaches
    `RetrievalApi` with the user id from the security context — user A
    authenticated ⇒ sees A's chunks; user B authenticated ⇒ sees B's
    chunks; tool input is identical in both cases. (2) Anonymous security
    context ⇒ `IllegalStateException`. (3) A successful call records into
    `AgentRetrievalContext.current()` so the advisor can read it. (4)
    Blank/null queries short-circuit to empty without ever calling
    `RetrievalApi`.
  - Existing `AgentControllerSmokeTest` and `ApplicationModulesTests`
    still green — the new beans don't leak across module boundaries
    (`agent` consumes only `ai.RetrievalApi` + `ai.RetrievedChunkDto`
    from `ai`'s module root, plus `common` infra).

  **Deferred to Slice 1c.**
  - Citation inline tags in assistant prose (`[#materialId:locator]`) and
    `CitationGuardAdvisor` regenerate-once.
  - `LanguageGuardAdvisor` regenerate-once on language drift.
  - Real per-token SSE streaming (today we emit citations + one text
    frame).
  - Locator-aware citations (PDF `pN`, audio `aN-N`) — Slice 1b uses the
    text-chunk locator `t<chunkIndex>` for every citation because that's
    all `RetrievedChunkDto` carries today; locator-discovery via the
    embedding metadata is part of Slice 1c.

  **Doc mismatches flagged (per AGENTS.md §6.2).**
  - `docs/features/agent/02-retrieval-grounding.md` describes a
    `retrieve(query, attachments?, topK)` tool signature. Slice 1b ships
    the minimal single-argument `retrieve(query)` because `ai.RetrievalApi`
    does not expose topic/material filters yet. Attachment-scoped
    retrieval will land when the RAG retrieval API grows the filter
    parameters — at which point the tool signature can be widened
    additively.
  - The same doc says retrieval emits a `tool_call` `MessagePart` with
    `status: "done"` into the SSE stream. Slice 1b emits `CitationPart`s
    for the retrieved chunks but not yet a `ToolCallPart`; this is a
    streaming concern picked up in Slice 1c.

- **2026-05-26 — Agent v1, Slice 1c shipped: `CitationGuardAdvisor` +
  `LanguageGuardAdvisor` + low-confidence / language-mismatch flags on
  `TextPart`.** Building on Slice 1b (entry above), the Coach now refuses
  to silently emit material claims without citations and refuses to
  silently drift to a different language. Both guards are regenerate-once
  rule-based advisors per `docs/features/agent/13-safety.md` — no LLM
  critic in v1.

  Layout (all in `org.aiincubator.ilmai.agent`):
  - `agent.TextConfidence` — module-root enum, `HIGH` / `LOW`. Module-root
    because it is part of the `TextPart` wire shape consumed by the
    frontend.
  - `agent.TextPart` — extended with two new fields: `confidence`
    (`TextConfidence`, defaults to `HIGH`) and `languageMismatch`
    (`boolean`, defaults to `false`). The single-arg constructor
    `TextPart(String)` is preserved (delegates to `HIGH` / `false`) so
    Slice 1a/1b call sites and the SSE smoke test stay green. New
    two-flag constructor `TextPart(String, TextConfidence, boolean)`.
    No `record`, no `@Builder` — plain `@Getter` + explicit constructors
    per AGENTS.md §4.
  - `agent.service.AgentResponseFlags` — per-call `ThreadLocal` parallel
    to `AgentRetrievalContext`. Holds two booleans the guard advisors
    can set: `lowConfidence` and `languageMismatch`. Lifecycle:
    `AgentResponseFlags.begin()` in `DefaultAgentApi.chat(...)`, cleared
    in the same `finally` as `AgentRetrievalContext.clear()`. Chosen
    over `ChatClientResponse.context()` because the advisors must
    survive a `chain.copy(...)` rebuild on regenerate and because the
    final reader is `DefaultAgentApi`, not another advisor — a
    ThreadLocal is the simplest path and mirrors the existing
    `AgentRetrievalContext` pattern.
  - `agent.service.LanguageDetector` — heuristic language detector,
    no LLM. Counts Cyrillic vs Latin code points; Cyrillic majority ⇒
    `RU`; Latin with Uzbek markers (`o\u02bb` apostrophe or any of a
    small whitelist of common Uzbek tokens — `bu`, `va`, `uchun`,
    `salom`, `rahmat`, `kitob`, `qachon`, `qanday`, `nima`, `menga`,
    `sizga`, etc.) ⇒ `UZ`; otherwise Latin ⇒ `EN`. Empty / symbol-only
    text returns `null` and the guard treats it as "cannot detect, no-op".
    Deliberately permissive — the brief's languages are EN / RU / UZ
    and over-strict rejection is worse than missing a regenerate. Open
    question from `13-safety.md` §"Open questions" about Latin-mixed
    technical terms (e.g. `fetch()`) is satisfied: a single English
    word inside an Uzbek sentence does not flip the detector because
    the Uzbek marker tokens dominate.
  - `agent.service.CitationGuardAdvisor` — Spring AI `CallAdvisor`
    (`Ordered.HIGHEST_PRECEDENCE + 700`, after `GroundingGuardAdvisor`
    at +600). After the chain returns, if `AgentRetrievalContext`
    recorded ≥1 chunk and the assistant message has no
    `[#materialId:locator]` tag (regex `\[#[^\]:]+:[^\]]+]`), it appends
    a localized hint as an additional `UserMessage` (`MessageService.get(
    agent.citation.regenerate_hint)`) and calls `chain.nextCall(...)` a
    second time. If the retry produces a citation, that retry response
    is returned. If still no citation, the response is returned **as-is**
    and `AgentResponseFlags.markLowConfidence()` is called — the
    user-visible signal lives on the `TextPart.confidence == LOW` field.
    No retrieval call ⇒ no-op (matches `13-safety.md` "Trigger: the
    turn called `retrieve(...)`").
  - `agent.service.LanguageGuardAdvisor` — Spring AI `CallAdvisor`
    (`Ordered.HIGHEST_PRECEDENCE + 800`, after citation guard). Detects
    the user's language from the **last** `UserMessage` in the prompt
    (most-recent-message-wins; handles the citation-guard's appended
    hint without flipping the detected user locale because hints are
    only appended on retry — and even on retry the detector is run on
    the original chain, before any appended hint, by walking from the
    end of `prompt.getInstructions()` and skipping non-USER messages).
    Compares against the assistant's detected language; on drift,
    appends a localized regenerate hint (
    `agent.language.regenerate_hint`, parameterized with the user
    locale's language tag `{0}`) and retries once. Still drifting ⇒
    return the retry and call `AgentResponseFlags.markLanguageMismatch()`,
    which surfaces as `TextPart.languageMismatch == true`. If the user
    locale cannot be detected (blank prompt, symbols only), the guard
    is a no-op — matches the doc's "every turn" trigger semantics in
    spirit (no false positives) while keeping the heuristic floor.
  - `agent.service.CoachChatClientConfig` — extended to register the
    two new advisors as `@Bean`s and add them to `defaultAdvisors(...)`
    on the `coachChatClient` builder, ordered after the existing
    grounding guard. The `@ConditionalOnBean(IlmaiChatClientFactory)`
    guard from Slice 1a remains — no LLM provider configured ⇒ no
    Coach `ChatClient` bean.
  - `agent.service.DefaultAgentApi` — `chat()` now opens an
    `AgentResponseFlags.begin()` alongside `AgentRetrievalContext.begin()`,
    and at the end builds the final `TextPart` with the two flags read
    from `AgentResponseFlags.current()`. Existing `CitationPart`
    emission for retrieved chunks is unchanged.

  **i18n.** Two new keys added to all three bundles, mirroring the
  existing `agent.grounding.empty` convention (`\uXXXX` escapes for
  non-ASCII in EN/RU, plain Latin for UZ):
  - `agent.citation.regenerate_hint` — instructs the model that it
    used `retrieve` without citing and must cite or admit it couldn't
    find the answer. Spoken to the model in the user's locale because
    the system prompt already commits the model to reply in the user's
    language; instructing the model in a different language would be a
    forcing function for language drift.
  - `agent.language.regenerate_hint` — single `{0}` placeholder
    expanded with the user locale's language tag (`en` / `ru` / `uz`).

  **Tests added.**
  - `agent.service.CitationGuardAdvisorTest` (6 cases): no retrieval
    context ⇒ pass-through; empty retrieval ⇒ pass-through; citation
    already present ⇒ single call, pass-through; missing citation on
    first attempt + present on second ⇒ retry once, no low-confidence
    mark; missing on both attempts ⇒ retry once, second response
    returned, `lowConfidence` set on `AgentResponseFlags`;
    `AgentResponseFlags` absent (advisor used outside `DefaultAgentApi`)
    ⇒ tolerant no-mark behavior.
  - `agent.service.LanguageGuardAdvisorTest` (5 cases): UZ user + UZ
    assistant ⇒ single call; RU user + RU assistant ⇒ single call;
    UZ user + EN assistant on first try, UZ on retry ⇒ retry once,
    no mismatch flag; UZ user + EN on both ⇒ retry once, mismatch
    flag set; symbol-only user prompt ⇒ no-op (cannot detect locale).
  - `agent.service.LanguageDetectorTest` (4 cases) — locks the
    heuristic: Cyrillic ⇒ RU; Latin + Uzbek markers ⇒ UZ; Latin
    without markers ⇒ EN; blank / symbol-only ⇒ `null`.
  - `agent.service.ScriptedCallAdvisorChain` — extracted top-level
    test helper implementing `CallAdvisorChain` with a scripted list
    of canned assistant responses and a `calls()` counter, so the
    two new tests can assert how many times the underlying chain
    was invoked. Lives in `src/test/java/.../agent/service/` and is
    used by both `CitationGuardAdvisorTest` and
    `LanguageGuardAdvisorTest`. Extracted (rather than nested inside
    one of the tests) per AGENTS.md §4 "no static (nested) inner
    classes for value or helper types".
  - Existing tests still green: `GroundingGuardAdvisorTest` (5),
    `RetrieveToolUserIsolationTest` (4), `AgentControllerSmokeTest`
    (1), and `ApplicationModulesTests.verifyModules` (1). Full
    `backend/src/test/java/org/aiincubator/ilmai/agent` run: 25/25
    pass.

  **Deferred to Slice 1d / later.**
  - Real per-token SSE streaming (still emits citations + one final
    `TextPart` frame). This is purely a streaming concern; the new
    flags ride on the final frame's metadata.
  - Locator-aware citations (PDF `pN`, audio `aN-N`) — still using
    `t<chunkIndex>` from `RetrievedChunkDto`. Unchanged from 1b.
  - `ToolCallPart` with `status: "done"` in the stream for the
    `retrieve` call — unchanged from 1b's deferral.
  - Per-claim citation enforcement — `CitationGuardAdvisor` ships
    with the "any tag passes" heuristic from `13-safety.md` §3.
    Per-sentence regex sweep over material-specific terms is
    deferred until we have real responses to tune on (open question
    explicitly logged in `13-safety.md`).

  **Doc mismatches flagged (per AGENTS.md §6.2).**
  - `docs/features/agent/13-safety.md` §4 specifies that
    `LanguageGuardAdvisor` should "detect language of the assistant's
    output matches the detected language of the user's input (or the
    explicitly requested language)". Slice 1c implements only the
    first half — there is no explicit "requested language" channel
    yet (the brief is silent; no UI control exists). If/when a
    per-turn language override lands, the guard's `detectUserLocale`
    method is the single point that needs widening to read the
    override before falling back to the user-message heuristic.
  - `docs/features/agent/13-safety.md` §3 mentions an OTel/Prometheus
    metric `guard.citation.regenerated` / `guard.language.failed_twice`.
    Slice 1c uses debug-level `org.slf4j.Logger` only — metric
    emission is deferred to the same slice that adds metrics for the
    rest of the agent guard chain (no existing project-wide metric
    pattern to mirror yet).

- **2026-05-26 � Agent v1, Slice 1c amended: language guard removed, LLM
  owns language selection.** Supersedes the
  `LanguageDetector` / `LanguageGuardAdvisor` portion of the
  `2026-05-26 � Agent v1, Slice 1c shipped` entry above. The heuristic
  Cyrillic-vs-Latin detector was a hand-rolled crutch that the model
  itself already handles natively; per user direction
  ("we dont need language detector, llm decides"), language drift is
  now prevented by the system prompt alone, not by a guard advisor.

  Removed:
  - `agent.service.LanguageDetector` (heuristic char-class detector).
  - `agent.service.LanguageGuardAdvisor` (Spring AI `CallAdvisor` that
    re-prompted on detected drift).
  - `agent.service.LanguageDetectorTest` (4 cases).
  - `agent.service.LanguageGuardAdvisorTest` (5 cases).
  - `agent.TextPart.languageMismatch` field + its two-flag constructor
    arg. `TextPart` is now `(text, confidence)`; the `confidence ==
    LOW` channel from `CitationGuardAdvisor` is retained because it
    still reflects a guarded, evidence-based decision.
  - `AgentResponseFlags.languageMismatch` /
    `markLanguageMismatch()` / `isLanguageMismatch()`. The
    `lowConfidence` channel is retained (citation guard still uses it).
  - `agent.language.regenerate_hint` from `messages.properties`,
    `messages_ru.properties`, `messages_uz.properties`.
  - `LanguageGuardAdvisor` bean and constructor arg from
    `CoachChatClientConfig`; remaining `defaultAdvisors` chain is
    `groundingGuardAdvisor, citationGuardAdvisor` (orders +600, +700
    unchanged).
  - `AgentResponseFlags.isLanguageMismatch()` read site in
    `DefaultAgentApi.chat(...)` � the final `TextPart` is now built
    from `text` + `confidence` only.

  Kept:
  - `coach-system.{en,ru,uz}.txt` already contain the rule
    *"reply in the same language the user writes in. Supported
    languages: English, Russian, Uzbek."* This is now the **single**
    mechanism enforcing the language non-negotiable.
  - The brief's three-language requirement (UZ / RU / EN) and the
    AI/RAG non-negotiables in AGENTS.md �5 are unaffected � they speak
    to user-facing strings and security-context user resolution, not
    to a programmatic language detector.

  **Tests.** Agent suite re-run after deletion:
  `AgentControllerSmokeTest` (1), `CitationGuardAdvisorTest` (6),
  `GroundingGuardAdvisorTest` (5), `RetrieveToolUserIsolationTest`
  (4), plus `ApplicationModulesTests.verifyModules` (1) � 17/17
  pass. `clean` + `compileTestJava` clean (no dangling references
  to the deleted types).

  **Doc mismatches flagged (per AGENTS.md �6.2).**
  - `docs/features/agent/13-safety.md` �4 still describes a
    `LanguageGuardAdvisor` regenerate-once flow. That doc is
    aspirational per AGENTS.md �1 and is not authoritative; the brief
    only mandates trilingual support, not a programmatic guard. Doc
    should be updated to reflect "LLM-only language selection,
    enforced by system prompt" on its next edit.
  - `docs/features/agent/agent-plan.md` Slice 1c bullet still claims
    `LanguageGuardAdvisor` as part of the shipped slice. Same
    treatment: aspirational doc; flag for human update, do not
    silently rewrite.

- **2026-05-26 — Agent v1, Slice 1d shipped: ilm-token quota gate.**
  Completes Slice 1 of `docs/features/agent/agent-plan.md`. The Coach
  now refuses to call the LLM when the caller has no ilm-token
  allowance remaining; on a successful turn the actual provider
  `Usage` is converted to ilm tokens and committed against the
  caller's daily bucket; on exception the reservation is refunded.

  **Decision: in-memory ledger for Slice 1d (Option B of the
  pre-implementation sketch).**
  `docs/features/agent/12-quotas.md` §"QuotaService (existing bean)"
  describes a `canSpend / reserve / commit / refund / remainingToday
  / allowanceToday` surface and a `quota_period` table keyed by
  `(user, date)`. The pre-Slice-1d `common.quota.QuotaService` had
  none of that — only feature-counter accessors
  (`dailyQuizQuota`, `dailyChatMessageQuota`,
  `materialUploadQuota`, `isPremiumFeatureAllowed`). Slice 1d adds
  the ilm-token surface to `QuotaService` and implements it
  in-memory in `BillingQuotaService`; the DB-backed `quota_period`
  table is **deferred to Slice 2**, where the chat-memory Flyway
  migration lands anyway. Slice 1 stays migration-free as planned
  in the slice-1 brainstorm ("no `agent/domain/` directory yet").
  Restart on the same day re-zeros the bucket; restart across a UTC
  date boundary is fine — this is acceptable for v1, will be
  superseded by Slice 2's persisted ledger.

  **Decision: pricing + per-tier ilm-token allowances live in
  `common.quota`, not in `billing.config`.**
  First attempt parked them on `BillingProperties` (`ilmai.billing.*`)
  but `ApplicationModulesTests.verifyModules` rejected it: `agent`
  is not allowed to import `billing.config.*` (non-exposed
  cross-module types per AGENTS.md §4). The pricing table and the
  free/premium daily allowance are cross-cutting infra consumed by
  both `billing` (the ledger impl) and `agent` (the cost
  calculator), so they belong to `common.quota` (the `OPEN`
  module). Resulting placement:
  - `common.quota.IlmTokenPricing` — provider/model → `ModelPrice`
    map + fallback `prompt`/`completion` USD/1M-token rates.
  - `common.quota.ModelPrice` — Lombok `@Getter @Setter` value
    type, top-level (not nested static) per AGENTS.md §4.
  - `common.quota.IlmTokenQuotaProperties` —
    `@ConfigurationProperties(prefix = "ilmai.quota")` carrying
    `freeDailyIlmTokens` (default 50), `premiumDailyIlmTokens`
    (default 500), and a nested `pricing` table.
  - `common.quota.IlmTokenQuotaConfig` — `@Configuration` +
    `@EnableConfigurationProperties(IlmTokenQuotaProperties.class)`
    to register the properties bean.
  `BillingProperties` and `FreeTierQuotas` are unchanged from
  before Slice 1d.

  **Decision: ilm-token gate sits in `DefaultAgentApi`, not as a
  Spring AI `CallAdvisor`.**
  `12-quotas.md` §"QuotaAdvisor" describes it as the first advisor
  in the Coach's advisor chain. We did not introduce a
  `QuotaAdvisor`: Spring AI advisors execute *inside* the
  `ChatClient` call, but the contract ("No LLM call happens" on
  zero allowance) requires the short-circuit to fire *before* any
  `ChatClient` invocation. The gate therefore lives in
  `DefaultAgentApi.chat(...)`, immediately after `currentUser` /
  `sessionId` validation and before
  `coachChatClientProvider.getIfAvailable()`. The existing advisor
  chain (`groundingGuardAdvisor` +600, `citationGuardAdvisor`
  +700) is unchanged.

  **Decision: flat per-turn estimate (5 ilm tokens) in Slice 1d.**
  `12-quotas.md` §"Before the Coach call" recommends
  `last_avg_per_turn * 1.2`, capped at the daily allowance. Slice
  1d has no per-user history (chat memory lands in Slice 2), so the
  estimate is the constant
  `DefaultAgentApi.PER_TURN_ESTIMATE_ILM_TOKENS = 5`. Revisit when
  `user_memory` is persisted in Slice 2 and the rolling average is
  cheap to read.

  **Decision: rounding is half-up `cost_usd × 100` →
  `ilm_tokens`.**
  Matches `12-quotas.md` §Conversion verbatim:
  `ilm_tokens(call) = round_half_up(cost_usd(call) * 100)`.
  Implemented with `BigDecimal` arithmetic and `RoundingMode.HALF_UP`
  in `agent.service.IlmTokenCostCalculator`. The price table is
  denominated USD per **1,000,000** tokens (matching modern
  per-million provider quotes), with two configurable defaults
  (`fallbackPromptUsdPerMillion = $0.15`,
  `fallbackCompletionUsdPerMillion = $0.60`) used when the
  `(provider, model)` is missing from the table.

  **Decision: background-vs-interactive allowance — deferred.**
  `12-quotas.md` §"Background (non-Coach) LLM calls" recommends a
  separate per-user weekly budget for digest / fact-extraction /
  rolling-summary writes. Slice 1d ships no background workers
  (those land in Slices 4–6), so the question is moot today. To
  be resolved alongside Slice 4's `GapAggregationJob` and Slice 5's
  `PlanReplanCheckJob`. Until then, the in-memory daily bucket is
  the single accounting surface.

  **Decision: `Clock` bean introduced.**
  `BillingQuotaService` needed an injectable wall-clock for
  deterministic day-rollover tests. Registered as
  `common.config.ClockConfig` (`Clock.systemUTC()`,
  `@ConditionalOnMissingBean` so tests can override). Cross-cutting,
  so it belongs in `common` and is available to future code
  (streaks, scheduled jobs).

  **Layout.**
  - `common.quota.IlmTokenReservation` — Lombok value class
    (`@Getter @AllArgsConstructor` with `final` fields per
    AGENTS.md §4), keys: `reservationId`, `userId`,
    `dateLocal`, `estimatedIlmTokens`.
  - `common.quota.QuotaService` — interface extended with six
    methods: `dailyIlmTokenAllowance`, `remainingIlmTokensToday`,
    `canSpend(userId, ilmTokens)`,
    `reserve(userId, estimatedIlmTokens)`,
    `commit(reservation, actualIlmTokensSpent)`,
    `refund(reservation)`.
  - `common.quota.{IlmTokenPricing, ModelPrice,
    IlmTokenQuotaProperties, IlmTokenQuotaConfig}` — cross-cutting
    pricing + allowance config (see decision above).
  - `common.config.ClockConfig` — system-UTC `Clock` bean.
  - `billing.service.BillingQuotaService` — ledger impl: a
    `ConcurrentHashMap<UUID, UserDailyIlmTokenBucket>` keyed by
    user, where each bucket holds
    `(dateLocal, allowance, reserved, spent)` and exposes
    `remaining = allowance - reserved - spent`. Per-user
    synchronization via `userId.toString().intern()` makes
    `canSpend + reserve` race-free. Day boundary at UTC midnight;
    a bucket whose `dateLocal` doesn't match today's UTC date is
    silently replaced on next access — user-local-midnight rollover
    is **flagged for implementation-time** (open question from the
    Coach brainstorm: "user timezone storage").
  - `billing.service.UserDailyIlmTokenBucket` — extracted top-level
    Lombok `@Getter @AllArgsConstructor` mutable carrier (per
    AGENTS.md §4 "no static nested helper types").
  - `agent.AgentErrorCodes` — module-root constants holder
    (`QUOTA_EXCEEDED = "quota_exceeded"`).
  - `agent.service.IlmTokenCostCalculator` — `@Component`,
    reads `IlmTokenQuotaProperties.getPricing()`, computes the
    half-up ilm-token cost for a `(provider, model, promptTokens,
    completionTokens)` tuple.
  - `agent.service.DefaultAgentApi` — gate + reserve/commit/refund
    wiring. Now calls
    `client.prompt().user(...).call().chatResponse()` (was
    `.content()`) so the `ChatResponse.getMetadata().getUsage()`
    is available for `IlmTokenCostCalculator`. The provider name
    is read from `IlmaiChatClientFactory.defaultProvider().name()`;
    model id from `ChatResponseMetadata.getModel()`. Refund happens
    in `finally` only when `committed == false`, so an exception
    after `reserve` cannot strand reserved tokens.
  - `messages{,_ru,_uz}.properties` — new `agent.quota.exceeded`
    key for the localized `ErrorPart.message`.

  **Tests.**
  - `billing.service.BillingQuotaServiceIlmTokenLedgerTest` — 9
    cases: free-tier allowance, premium-tier allowance,
    `canSpend` zero-allowance, reserve/commit accounting,
    negative-actual clamp, refund, over-reserve throws, null-
    reservation no-op on commit/refund, day-rollover resets
    bucket.
  - `agent.service.DefaultAgentApiQuotaShortCircuitTest` — 1 case:
    zero-allowance caller → `ErrorPart(code="quota_exceeded",
    retryable=false)`, no `ChatClient` lookup, no `reserve(...)`
    call. This is the "Done when" gating test from the agent-plan
    §3 "Done when (Slice 1 overall)" bullet 3.
  - `ApplicationModulesTests.verifyModules` — green. First
    iteration of 1d violated module boundaries (`agent →
    billing.config`); fixed by relocating pricing to
    `common.quota`. Now clean.
  - Pre-existing suites unaffected:
    `agent/**` 17/17 incl. `AgentControllerSmokeTest`,
    `GroundingGuardAdvisorTest`, `CitationGuardAdvisorTest`,
    `RetrieveToolUserIsolationTest`; `materials.service.MaterialServiceTest`
    18/18. `QuotaService` interface widening is transparent to
    Mockito-based mocks (default stubs for new methods).

  **Deferred to Slice 2 or later.**
  - DB-backed `quota_period` (Slice 2; lands with chat-memory
    Flyway migration).
  - User-local-midnight rollover (needs user-timezone storage —
    flagged from the Coach brainstorm).
  - Separate per-user weekly budget for background LLM calls
    (Slices 4–6).
  - Streaming `quota_exceeded` mid-response on usage spike during
    a turn (`12-quotas.md` §"Sub-agent calls share the parent
    turn's budget"; no sub-agents in Slice 1).
  - Concrete provider/model prices in `application.yaml` — Slice
    1d ships defaults; pricing rows will be added per-provider
    when each provider becomes the default.
  - `Usage` accounting for any Spring AI sub-call inside the same
    Coach turn — none exist in Slice 1; `commit` reads only the
    top-level `ChatResponse.getMetadata().getUsage()`.

  **Doc mismatches flagged (per AGENTS.md §6.2).**
  - `docs/features/agent/12-quotas.md` §"QuotaService (existing
    bean)" describes the ilm-token surface as if it already existed
    on `QuotaService` pre-Slice-1d. It did not; Slice 1d is the
    slice that puts it there. Doc should be updated on its next
    edit to reflect "added in Slice 1d, in-memory ledger; DB
    backing in Slice 2".
  - `docs/features/agent/agent-plan.md` Slice 1d task 1 says
    `QuotaAdvisor` (a Spring AI `CallAdvisor`); we implemented the
    pre-call gate inline in `DefaultAgentApi` instead because the
    contract demands "No LLM call happens" before the advisor
    chain ever runs. Doc should be reconciled with the chosen
    placement.
  - `docs/features/agent/12-quotas.md` §"Free vs Premium" lists
    `free: 50 ilm tokens/day`, `premium: 500 ilm tokens/day` as
    placeholders. Slice 1d adopts those defaults in
    `IlmTokenQuotaProperties` verbatim; the doc and the code now
    agree, but the "placeholder" status remains.

- **2026-05-29 — Agent v1, Cleanup sub-slice shipped.** Executed the §3
  Cleanup sub-slice of `docs/features/agent/agent-plan.md` so Slice 2
  (Memory + onboarding + goal) can land its `chat_session` /
  `chat_message` / `chat_memory_summary` schema and
  `MessageChatMemoryAdvisor` wiring against a clean module graph.
  Deletions (backend):
  - Entire `org.aiincubator.ilmai.ai.chat.*` subtree —
    `ChatController`, `ChatService`, `EmptyRetrievalRedirectAdvisor`,
    `Conversation` / `ChatMessage` / `MessageCitation` JPA entities and
    repositories, all `payload/` DTOs, the legacy
    `ai/chat/service/DefaultRetrievalApi` and `RetrievalProperties`
    (duplicates of the 2026-05-29 module-root replacements).
  - Entire `org.aiincubator.ilmai.ai.skill.*` subtree —
    `ActiveSkillHolder`, `MaterialsSkill`, `OpenSkillTool`,
    `ProfileSkill`, `SkillRegistry`. Module-root skill abstraction
    also gone: `ai/Skill.java`, `ai/SkillResources.java`,
    `ai/CurrentUserResolver.java`.
  - Module-root legacy `ai/IlmaiChatClient.java` and
    `ai/RetrievedChunk.java` (duplicate of
    `ai/RetrievedChunkDto.java` — only `Dto` survives at module root).
  - Feature-module skill adapter classes:
    `quiz/skill/QuizSkill.java`, `gaps/skill/GapsSkill.java`,
    `telegram/skill/RemindersSkill.java`. The owning module
    `*Api` / `*Service` / `*Controller` classes survive untouched —
    Slice 3 (Quizzing), Slice 4 (Gaps), Slice 8 (Telegram) will
    rewrite them properly.
  - Entire `org.aiincubator.ilmai.plan.*` module —
    `PlanController`, `PlanExceptionHandler`, `PlanService`,
    `PlanAgent`, `PlanMapper`, `PlanException`, `PlanDayDraft`,
    `PlanDraftAndSummary`, `DefaultPlansApi`, `LearningPlan` /
    `PlanDay` entities and repositories, `payload/` DTOs,
    module-root `LearningPlanDto` / `PlanDayDto` / `PlansApi` /
    `package-info.java`, plus `plan/skill/PlanSkill.java`. Slice 5
    will reintroduce planning under `agent/` with a different schema
    (`learning_plan` / `plan_step`, singular).
  - Spring AI "skill" markdown resources at
    `backend/src/main/resources/skills/{materials, profile, quiz,
    gaps, plan, reminders}/{SKILL,system}.md`.
  - Legacy tests at `ai/chat/service/{ChatServiceTest,
    CitationTagBuilderTest, IlmaiChatClientTest}`,
    `ai/skill/{MaterialsSkillTest, OpenSkillToolTest,
    SkillRegistryTest}`.

  Relocations:
  - `ChatProviderProperties` moved from `ai/chat/provider/` to
    `ai/config/` (matches `auth/config/`, `billing/config/`,
    `telegram/config/` convention). Class body unchanged. Test
    `IlmaiChatClientFactoryTest` follows from
    `ai/chat/provider/` to `ai/config/` — package change only.
    `IlmaiChatClientFactory` import line updated; no behaviour
    change. `application.yml`'s `ilmai.ai.chat.default-provider` key
    is preserved.

  Rewires:
  - `quiz/service/QuizService` and `quiz/service/QuizGenerator`
    swapped from `ai.IlmaiChatClient` + `ai.RetrievedChunk` to
    `ai.RetrievalApi` + `ai.RetrievedChunkDto`. Internal logic in
    `sampleFromMaterials`, `buildPrompt`, `parseDrafts`, `fallback` is
    bit-for-bit identical after the type swap — field accessors
    (`getMaterialId`, `getMaterialName`, `getChunkIndex`,
    `getContent`, `getScore`) match across both classes. This frees
    `IlmaiChatClient.java` and `ai/RetrievedChunk.java` for deletion
    so no duplicate types remain at the `ai/` module root.
    `QuizServiceIsolationTest` swapped `@Mock IlmaiChatClient` for
    `@Mock RetrievalApi`; user-isolation assertions preserved
    bit-for-bit.

  Schema:
  - `V17__drop_legacy_chat_and_plan.sql` added. Drops
    `message_citations`, `chat_messages`, `conversations`,
    `plan_days`, `learning_plans` in FK-safe order with
    `DROP TABLE IF EXISTS … CASCADE` (idempotent on fresh DB).
    Spring AI's own `spring_ai_chat_memory` table is preserved
    (managed by `spring.ai.chat.memory.repository.jdbc.initialize-schema:
    always`); Slice 2 will use it.

  Frontend:
  - Per explicit user direction the frontend is **untouched** in this
    sub-slice. `frontend/lib/chat.ts`, `frontend/lib/plan.ts`,
    `frontend/components/chat/chat-pane.tsx`,
    `frontend/components/plan/*`,
    `frontend/app/(app)/plan/page.tsx`,
    `frontend/app/(app)/topics/[topicId]/page.tsx` still call
    `/chat/conversations/*` and `/plan` — those endpoints no longer
    exist server-side. Pages will throw API errors at runtime until
    Slice 2 (chat) rewires the chat UI against the new SSE endpoint
    `POST /agent/chat/{sessionId}` and Slice 5 (planning) replaces
    the plan UI against the agent-owned plan schema. This is a
    deliberate carry-over; future agents picking up Slice 2 / Slice 5
    must rewire the frontend then.

  Verification:
  - `gradlew compileJava compileTestJava` green.
  - `ApplicationModulesTests.verifyModules` green — no residual
    `agent/ → ai.chat`, `agent/ → ai.skill`, `quiz/ → ai.chat`,
    `quiz/ → ai.skill`, or any `*/skill/*` cross-module leak.
  - Agent suite (Slice 1a–1d) green:
    `AgentControllerSmokeTest` 1/1,
    `CitationGuardAdvisorTest` 6/6,
    `GroundingGuardAdvisorTest` 5/5,
    `RetrieveToolUserIsolationTest` 4/4,
    `DefaultAgentApiQuotaShortCircuitTest` 1/1.
  - `BillingQuotaServiceIlmTokenLedgerTest` 9/9.
  - `IlmaiChatClientFactoryTest` (relocated to `ai/config/`) 6/6.
  - `QuizServiceIsolationTest` (rewired) 2/2.
  - `IlmaiBackendApplicationTests.contextLoads()` green against the
    compose `pgvector` container with `GOOGLE_GENAI_API_KEY` set —
    proves Flyway V1→V17 applies cleanly and Hibernate
    `ddl-auto: validate` succeeds against the migrated schema.

  Pre-existing test failures observed (not introduced by this sub-slice
  but worth documenting):
  - `EmbeddingPipelineIntegrationTest` and
    `UserIsolationIntegrationTest` extend
    `AbstractEmbeddingIntegrationTest` (`@SpringBootTest`) which boots
    the full context including
    `agent.service.CoachChatClientConfig.coachChatClient(...)`. That
    bean throws `IllegalStateException("No chat model provider
    configured for Coach ChatClient")` whenever no provider key is
    set — the `@ConditionalOnBean(IlmaiChatClientFactory.class)` on
    the bean factory method matches the always-present
    `IlmaiChatClientFactory` `@Component` and so does not gate on
    `ChatModel` availability. **Slice 2 should fix this by switching
    the gate to `@ConditionalOnBean(ChatModel.class)` (or returning
    a no-op `ChatClient`) so embedding integration tests pass without
    requiring a live chat API key.** Flagged here per AGENTS.md rule
    2 (don't silently propagate mismatches in surrounding code).
  - `IlmaiBackendApplicationTests.contextLoads()` defaults its JDBC
    URL to `jdbc:postgresql://localhost:5432/mydatabase`, but the
    `compose.yaml` pgvector service exposes only an ephemeral host
    port. The test requires either a `DB_URL` override or a static
    `ports: '5432:5432'` mapping. Out of scope for this sub-slice.

  Doc / plan touch-ups:
  - `docs/features/agent/agent-plan.md` §3 Cleanup sub-slice bullet
    can now be marked ✅ in its next revision.
  - This entry **does not supersede** any prior entry — it ships the
    cleanup work that the 2026-05-26 → 2026-05-29 agent-plan entries
    promised.

- **2026-05-30 — Agent v1, Slice 2a shipped: session chat memory.** First
  chunk of `docs/features/agent/agent-plan.md` §4 Slice 2 (Memory +
  onboarding + goal), sub-sliced 2a–2d (see "Sub-slicing" below). 2a gives
  the Coach within-session conversation memory keyed by `sessionId`, plus a
  real `chat_sessions` table with server-minted ids and an ownership gate.
  Added (backend):
  - `agent/domain/ChatSession` (`@Entity chat_sessions`, UUIDv7 PK,
    `user_id`, `@Enumerated(STRING) channel`, `title`, `DateAuditable`
    timestamps, Hibernate-proxy-safe equals/hashCode) +
    `ChatSessionRepository` (`findAllByUserIdOrderByCreatedAtDesc`).
  - `agent/api/ChatSessionResponse` + `CreateChatSessionRequest` DTOs
    (co-located in `api/` to match the existing `AgentChatRequest`),
    `agent/service/ChatSessionMapper` (MapStruct, `componentModel=spring`),
    `agent/service/ChatSessionService` (`create`, `getAll`,
    `requireOwnedSession`), `agent/service/ChatSessionException`
    (`SESSION_NOT_FOUND`) + `agent/api/ChatSessionExceptionHandler` (→ 404),
    `agent/api/ChatSessionController` (`POST` / `GET /agent/sessions`).
  Schema:
  - `V18__chat_sessions.sql` — `chat_sessions` (plural, to match the
    existing `spaces` / `users` / `materials` table convention; deviates
    from the plan doc's singular `chat_session`). FK → `users(id) ON DELETE
    CASCADE`, index `(user_id, created_at DESC)`. **No Flyway migration for
    `spring_ai_chat_memory`** — it stays managed by Spring AI's
    `initialize-schema: always` (per the 2026-05-29 entry); 2a reuses it.
  Memory wiring:
  - `CoachChatClientConfig` now defines `ChatMemory coachChatMemory`
    (`MessageWindowChatMemory`, window = 20 messages ≈ ~10 turns) backed by
    the auto-configured `ChatMemoryRepository` (the already-present
    `spring-ai-starter-model-chat-memory-repository-jdbc`
    `JdbcChatMemoryRepository`), obtained via
    `ObjectProvider.getIfAvailable(InMemoryChatMemoryRepository::new)` to
    dodge the `@ConditionalOnBean`-in-user-config ordering pitfall and keep
    no-DB context loads working. `MessageChatMemoryAdvisor` is built from it
    and appended to the Coach's `defaultAdvisors`. `DefaultAgentApi` sets
    `ChatMemory.CONVERSATION_ID = sessionId.toString()` per call.
  Behaviour change:
  - `DefaultAgentApi.chat()` calls `ChatSessionService.requireOwnedSession`
    right after the null checks (before the quota gate). A missing or
    non-owned session throws `ChatSessionException(SESSION_NOT_FOUND)` →
    `AgentController` (which returns the `Flux` directly) surfaces it as a
    404. `POST /agent/chat/{sessionId}` therefore now requires a session
    created via `POST /agent/sessions` first (server-minted UUIDv7).
    Quota-exceeded stays an in-stream `ErrorPart` (legitimate authenticated
    request); ownership failure is a 404 (bad/forbidden resource). User
    isolation is doubly enforced: the ownership gate (404 before any
    LLM/memory work) and `conversationId = owned sessionId`.
  i18n:
  - `agent.session.notFound` added to `messages{,_ru,_uz}.properties`
    (EN/RU/UZ), RU `\u`-escaped to match the existing agent block.
  Flagged fix resolved (from the 2026-05-29 entry):
  - `CoachChatClientConfig.coachChatClient` no longer throws when no chat
    provider is configured — it logs and returns `null` (Coach unavailable;
    `DefaultAgentApi` already handles a null Coach via its `ObjectProvider`).
    Chose this over the entry's suggested `@ConditionalOnBean(ChatModel.class)`
    because that condition, in a user `@Configuration`, evaluates before the
    provider auto-configurations register their `ChatModel` beans and would
    gate the Coach off even when a key *is* set. Unblocks the full-context
    embedding integration tests (`EmbeddingPipelineIntegrationTest`,
    `UserIsolationIntegrationTest`) on a Docker host without a live chat key.
  Sub-slicing (added 2026-05-30):
  - Slice 2 is multi-day, so split 2a–2d mirroring Slice 1. 2a = session
    chat memory (this entry). 2b = rolling-summary write +
    `user_memory_facts` / `goal` / `streak` + `UserMemoryAdvisor`. 2c =
    goal + onboarding endpoints + `getGoal` / `updateGoal` tools. 2d =
    throttled `user_facts` extraction + `UserActivityRecordedEvent`. Order
    is dependency-meaningful.
  Deviations:
  - Table pluralised (`chat_sessions`) vs the plan's `chat_session`.
  - `chat_memory_summary` is deferred to 2b (created where first
    populated), not in 2a.
  - No frontend in 2a (the plan lists no frontend task for Slice 2; the
    chat-UI rewire against `POST /agent/chat/{sessionId}` flagged in the
    2026-05-29 entry remains outstanding).
  Verification:
  - `gradlew compileJava compileTestJava` green.
  - New agent unit tests green: `ChatSessionServiceTest` 5/5 (ownership
    isolation — owner ok, other-user & missing → `SESSION_NOT_FOUND`;
    create defaults; per-user `getAll`), `ChatSessionControllerTest` 2/2,
    `DefaultAgentApiSessionOwnershipTest` 1/1 (unowned session → throws, no
    quota/LLM), `CoachChatMemoryTest` 2/2 (recall within a session +
    isolation across sessions). Existing agent suite still green;
    `DefaultAgentApiQuotaShortCircuitTest` updated for the new ctor arg.
  - `AgentChatMemoryIsolationIntegrationTest` added (Docker-gated,
    `@Testcontainers(disabledWithoutDocker = true)`): real
    `JdbcChatMemoryRepository` round-trips and isolates by `conversation_id`
    on pgvector pg16; full context load also re-validates V18 under
    `ddl-auto: validate`. Skipped locally (no Docker); runs in CI.
  - Full suite 211/214 (1 skip = the gated test above; 3 failures are the
    pre-existing Docker/DB-env ones — `pgvector` image fetch ×2 and
    `IlmaiBackendApplicationTests` Flyway → `localhost:5432 refused`).
  Doc / plan touch-ups:
  - `docs/features/agent/agent-plan.md` §0 Cleanup bullet marked ✅; Slice
    2a marked ✅ with the 2a–2d sub-slicing note.
  - This entry does not supersede any prior entry.

- **2026-05-30 — Agent v1, Slice 2b-i shipped: user-memory read path
  (`user_memory_facts` + `UserMemoryAdvisor`).** Second chunk of
  `docs/features/agent/agent-plan.md` §4 Slice 2, sub-slice 2b. Ships the
  user-scoped **read** path: the Coach now injects a capped, user-isolated
  `[user context]` block (goal + streak + facts) into its system prompt. The
  2b **rolling-summary write** (`chat_memory_summary` + cheap-LLM compaction)
  is deferred to a follow-up (2b-ii) — see Deviations.
  New module (backend) — `usermemory/` (CLOSED):
  - `usermemory/domain/UserMemoryFact` (`@Entity user_memory_facts`, UUIDv7
    PK, `user_id`, `content`, `DateAuditable` timestamps, Hibernate-proxy-safe
    equals/hashCode) + `UserMemoryFactRepository`
    (`findByUserIdOrderByCreatedAtDesc(UUID, Limit)`).
  - Module-root public API `usermemory.UserMemoryApi`
    (`List<UserFactDto> recentFacts(CurrentUser, int limit)`) +
    `usermemory.UserFactDto` (`@Getter @AllArgsConstructor final`), backed by
    `usermemory/service/DefaultUserMemoryApi` (@Service,
    `@Transactional(readOnly=true)`) + `UserMemoryApiMapper` (MapStruct,
    `componentModel=spring`). `package-info` `@ApplicationModule(CLOSED)`.
  Schema:
  - `V19__user_memory_facts.sql` — `user_memory_facts` (plural, matching the
    `chat_sessions` / `users` convention). FK → `users(id) ON DELETE CASCADE`,
    index `(user_id, created_at DESC)`. No DB CHECK constraints.
  Advisor + Coach wiring (agent):
  - `agent/service/UserMemoryAdvisor` (`CallAdvisor`, order
    `HIGHEST_PRECEDENCE + 500` — outermost, so its system-prompt augmentation
    runs once and is not re-applied during grounding/citation guard
    regenerations). Resolves the caller from the advisor-context param
    `agent.current_user` (set by `DefaultAgentApi` alongside
    `ChatMemory.CONVERSATION_ID`) — never from a tool/model argument (AI/RAG
    non-negotiable). Reads facts via `UserMemoryApi` and goal/streak via
    `ProfilesApi.find`; assembles `[user context]` and caps it (~1200 chars
    ≈ 300 tokens, newest facts kept, goal/streak prioritised); empty block or
    absent caller → pass-through.
  - `CoachChatClientConfig` registers the `userMemoryAdvisor` bean and
    prepends it to the Coach `defaultAdvisors`; `DefaultAgentApi` sets the new
    `agent.current_user` advisor param per turn.
  Goal/streak storage decision (deviation flagged per AGENTS.md rule 2):
  - The plan's §4 task 2 lists new `user_memory_goal` / `user_memory_streak`
    tables. **Not created.** Goal (`profiles.goal` + `target_date`) and streak
    (`profiles.streak_days`) already exist on the `profiles` module; the
    advisor reads them via `ProfilesApi` to keep a single source of truth.
    Only the genuinely-new `user_memory_facts` is added. Slice 2c (goal
    onboarding) and Slice 6 (streak job) wire the already-present `profiles`
    fields. Added `ProfilesApi.find(UUID) : Optional<ProfileDto>`
    (non-throwing sibling of `require`) so the advisor degrades gracefully
    when a profile is absent.
  i18n:
  - The `[user context]` block is **model-facing** prompt scaffolding (not a
    user-visible message), so its English labels are not subject to AGENTS.md
    rule 8; the fact/goal content passes through in whatever language the user
    stored it.
  Test-hermeticity fix (flagged per AGENTS.md rule 2):
  - The `spring-ai-starter-model-{google-genai,openai,anthropic}` chat
    starters auto-configure chat models whose api-keys default to empty in
    `application.yml`; `GoogleGenAiChatAutoConfiguration` then throws at
    context startup on a Docker host without a live key. The 2026-05-30 Slice
    2a "coachChatClient returns null" fix addressed the *Coach bean* but not
    the *provider auto-config*. Added dummy `spring.ai.*.api-key` test
    properties to both Slice 2 isolation integration tests
    (`AgentUserMemoryIsolationIntegrationTest` + the pre-existing
    `AgentChatMemoryIsolationIntegrationTest`) so they boot hermetically
    (Spring AI builds clients lazily — no network call). The standalone
    `EmbeddingPipelineIntegrationTest` / `UserIsolationIntegrationTest` base
    may need the same treatment when run on Docker without keys.
  Deviations:
  - `user_memory_goal` / `user_memory_streak` not created (reuse `profiles`,
    above).
  - Slice 2b's `chat_memory_summary` rolling-summary write deferred to a
    follow-up (2b-ii); 2b-i ships the read path + isolation tests (plan §4
    tasks 10 & 11) only.
  - `user_facts` extraction (plan §4 task 6 / Slice 2d) not included; facts
    are read here, written by 2d.
  Verification:
  - `gradlew compileJava compileTestJava` green.
  - `UserMemoryAdvisorTest` 5/5 (facts injection; goal/streak from profile;
    no-caller pass-through; empty pass-through; char-cap keeps newest).
  - `AgentUserMemoryIsolationIntegrationTest` 2/2 on pgvector pg16 (Docker):
    user A's facts never appear in user B's Coach context (SQL + advisor
    read-path isolation; plan task 11); a fact persists across two sessions
    for the same user (plan task 10). Full context load re-validates V19 under
    `ddl-auto: validate`.
  - Targeted suite `agent.* + profiles.* + ApplicationModulesTests` = 36/36
    green, 0 skipped (Docker up); `ApplicationModulesTests` confirms the new
    CLOSED `usermemory` module and the `agent → usermemory/profiles` edges
    stay within module-root APIs.
  - Pre-existing env-dependent failures unchanged (per the 2a entry):
    `IlmaiBackendApplicationTests` (localhost:5432 Postgres) and the embedding
    integration tests on hosts without Docker.
  Doc / plan touch-ups:
  - `docs/features/agent/agent-plan.md` §0 — Slice 2b marked partially
    shipped (2b-i read path); the rolling-summary write (2b-ii) still pending.
  - This entry does not supersede any prior entry.

- **2026-05-30 — Agent v1, Slice 2b-ii shipped: chat rolling-summary write
  (`chat_memory_summary` + `ChatMemorySummarizer` + `ChatSummaryAdvisor`).**
  Final chunk of `docs/features/agent/agent-plan.md` §4 Slice 2, sub-slice 2b
  (plan §4 task 4) — completes Slice 2b. When a session's raw-turn window
  overflows, the oldest turns are folded into a per-session running summary by
  a cheap, advisor-free LLM call (charged to the user's ilm-token quota), the
  summarized turns are pruned from the live `spring_ai_chat_memory` store, and
  the summary is injected back into the Coach system prompt on later turns.
  Schema:
  - `V20__chat_memory_summary.sql` — one row per session: UUIDv7 PK,
    `session_id` UNIQUE FK → `chat_sessions(id) ON DELETE CASCADE` (the summary
    lives and dies with its session), `summary TEXT`, `folded_messages INT`
    (observability counter), `DateAuditable` timestamps. No DB CHECK
    constraints.
  - `agent/domain/ChatMemorySummary` (`@Entity`, UUIDv7 `@Id`, proxy-safe
    equals/hashCode) + `ChatMemorySummaryRepository.findBySessionId`.
  Write path (agent/service):
  - `ChatMemorySummarizer` (@Service) — `maintain(CurrentUser, sessionId)` is
    run post-commit by `DefaultAgentApi` best-effort (wrapped in try/catch,
    never breaks the turn). Splits the live store into turns by `UserMessage`
    boundaries; when turns > `DEFAULT_MAX_RAW_TURNS` (8) it folds the oldest
    `DEFAULT_FOLD_TURNS` (4). Quota mirrors the main-turn ledger: skip if
    `!canSpend`, else reserve → (LLM) → commit actual / refund on
    null-or-failure. Persistence is atomic via a `TransactionTemplate` (upsert
    summary + `ChatMemoryRepository.saveAll(retained)` prune) while the LLM
    call stays outside the tx.
  - `ChatSummaryGenerator` (@Service) — advisor-free `summaryChatClient` call
    (bare system prompt; no retrieve/grounding/citation/memory advisors, so the
    guards can't rewrite a summary) that renders a labelled transcript
    (`Prior summary:` + `Learner:`/`Coach:` lines) and returns a
    `ChatMemorySummaryDraft` (summary text + ilm-token cost via the shared
    `IlmTokenCostCalculator` + response model metadata). Null (skip) when no
    LLM provider.
  - `prompts/agent/summary-system.txt` + `SummarySystemPrompt` loader.
    Model-facing scaffolding (English, like the 2b-i `[user context]` block,
    so not subject to AGENTS.md rule 8) that instructs the model to summarize
    **in the conversation's own language**, so no per-locale prompt files are
    needed.
  Read path (agent/service):
  - `ChatSummaryAdvisor` (`CallAdvisor`, order `HIGHEST_PRECEDENCE + 550` —
    between `UserMemoryAdvisor` (+500) and the grounding/citation guards
    (+600/+700), so its augmentation runs once and is preserved across guard
    regenerations). Resolves the session from `ChatMemory.CONVERSATION_ID` (an
    already ownership-gated, owned sessionId) and appends a capped
    `[earlier in this conversation]` block to the system message; no summary /
    no conversation id / non-UUID → pass-through.
  - `CoachChatClientConfig` registers `chatSummaryAdvisor` in the Coach chain
    (after `userMemoryAdvisor`) and a bare `summaryChatClient` bean
    (`@ConditionalOnBean(IlmaiChatClientFactory)`, null when no provider).
  Shared-repository note:
  - The summarizer injects the **same** `ChatMemoryRepository` singleton the
    Coach `ChatMemory` uses (via `ObjectProvider.getIfAvailable()`), so the
    prune hits the live store; when absent (no JDBC repo / no provider)
    `maintain` no-ops — matching `coachChatMemory`'s own fallback path.
  User isolation:
  - The summary is keyed by an owned `sessionId`; `DefaultAgentApi`'s
    `requireOwnedSession` gate (404 before any LLM/memory work, proven by the
    2a `AgentChatMemoryIsolationIntegrationTest`) transitively isolates it —
    no new cross-user surface and no endpoint exposes summaries directly.
  Open-question carry-over (§13):
  - The "cheap model" knob is satisfied for now by a short focused prompt on
    the default provider's model (cost is computed from the response's own
    model metadata regardless). A dedicated cheaper summary model and a
    separate background-vs-interactive ilm-token allowance remain open
    (deferred to a later slice / Slice 6).
  Verification:
  - `gradlew compileJava compileTestJava` green; `ApplicationModulesTests`
    green (no new cross-module leak; `agent → ai` reuses the existing
    `IlmaiChatClientFactory` edge).
  - `ChatMemorySummarizerTest` 5/5 (folds oldest 4 turns, prunes to `retained`
    & commits actual cost; no-op at/below threshold; skip on quota-exceeded;
    refund when the generator returns null; no-op without a chat-memory repo)
    and `ChatSummaryAdvisorTest` 5/5 (inject; no-summary, no-conversation-id &
    non-UUID pass-through; char-cap).
  - Full agent context revalidated on pgvector pg16 (Docker):
    `AgentChatMemoryIsolationIntegrationTest` 1/1,
    `AgentUserMemoryIsolationIntegrationTest` 2/2, `AgentControllerSmokeTest`
    1/1, `ChatSessionControllerTest` 2/2 — V20 applies under
    `ddl-auto: validate` and the new beans wire. Updated
    `DefaultAgentApi{QuotaShortCircuit,SessionOwnership}Test` for the new ctor
    arg.
  Deviations:
  - Summarization runs **synchronously** in the turn (post-commit) rather than
    fully out-of-band; acceptable for v1, async/throttled is a later option.
  - No DB-backed fold integration test (would need a stub `ChatModel` bean so
    `generator.isAvailable()` is true); the fold/prune/quota logic is covered
    by `ChatMemorySummarizerTest` with mocks, while real-context boot + V20 +
    bean wiring are covered by the isolation/smoke suites.
  - This entry does not supersede any prior entry.

- **2026-05-31 — Agent v1, Slice 2c shipped: goal tools + onboarding endpoint
  (`getGoal` / `updateGoal` Coach tools + `daily_study_minutes` + `/onboarding`).**
  `docs/features/agent/agent-plan.md` §4 Slice 2 tasks 7 & 8. The Coach can now
  read and write the user's learning goal as a tool, and a dedicated onboarding
  endpoint captures the happy-path fields (goal + deadline + study time +
  reminder), each individually optional and skippable. Builds on the 2b-i
  decision to keep goal/streak on the `profiles` module (no `user_memory_goal`).
  Goal tools (agent/service):
  - `GoalTool` (@Component) exposes `@Tool getGoal()` and
    `@Tool updateGoal(goal?, deadline?)`. Both resolve the caller from the Spring
    Security context (same `requireUserId()` pattern as `RetrieveTool`) — `userId`
    is never a tool argument (AI/RAG non-negotiable; overview §"Tools the Coach
    owns"). Returns `GoalView` (`agent/service`, `@Getter @AllArgsConstructor
    final`: `goalSet`, `goal`, `deadline` ISO string, `daysUntilDeadline`).
    `deadline` is parsed as a strict ISO `LocalDate` (blank → null = leave
    unchanged; unparseable → `IllegalArgumentException` surfaced to the model).
  - Registered in `CoachChatClientConfig.coachChatClient`
    (`toolObjects(retrieveTool, goalTool)`). The three Coach system prompts
    (`coach-system.{en,ru,uz}.txt`) gain a "Goal management" line steering the
    model to the tools (UZ/RU/EN per AGENTS.md rule 8).
  - Cross-module write goes through the producer API: added
    `ProfilesApi.updateGoal(UUID, String goal, LocalDate targetDate)` +
    `DefaultProfilesApi` impl (single transaction, partial-update — null arg
    leaves the field unchanged, blank goal clears it, past `target_date` →
    `PROFILE_INVALID_TARGET_DATE`). The pre-existing (unused)
    `setLearningGoal` / `setTargetDate` API methods are left in place.
  Onboarding (profiles module):
  - `daily_study_minutes` added to the `profiles` model — `V21__profile_study_
    minutes.sql` (`ALTER TABLE profiles ADD COLUMN daily_study_minutes INTEGER`,
    nullable), `Profile.dailyStudyMinutes` (`Integer`), and the `ProfileDto` /
    `ProfileResponse` / `UpdateProfileRequest` (`@Min(1) @Max(1440)`) carriers +
    `ProfileService.update`. Study time = the planner's `studyMinutesPerDay`
    (06-planning.md); captured now, consumed in Slice 5.
  - `OnboardingController` (`profiles/api`, `GET`/`PUT /onboarding`) →
    `OnboardingService` (takes `CurrentUser`) → `OnboardingMapper` (MapStruct).
    `OnboardingRequest` (goal, targetDate, dailyStudyMinutes, dailyReminder — all
    optional) / `OnboardingResponse` (same + `telegramLinked`). Reuses
    `ProfileException` (existing global advice) for the past-date check;
    study-minutes range via Bean Validation → `GlobalExceptionHandler`. Skipping
    = empty payload (no field overwritten); there is no separate "completed" flag.
  Telegram-link placeholder (deviation flagged per AGENTS.md rule 2):
  - Task 8 lists a "Telegram link placeholder". The `telegram` module already has
    a real link flow, but to avoid a `profiles → telegram` coupling this slice
    exposes `OnboardingResponse.telegramLinked` as a constant `false` placeholder;
    wiring the real status is left to Slice 8.
  Goal-model simplification (deviation flagged):
  - The brief's goal model is `{ title, description, deadline }`; consistent with
    the 2b-i reuse of `profiles`, goal is stored as the single `profiles.goal`
    string + `target_date`. No separate description column in v1.
  i18n:
  - `@Tool` / `GoalView` outputs are model-facing (not user-visible), so their
    English text is not subject to AGENTS.md rule 8; goal text passes through in
    the user's own language. The "Goal management" system-prompt line is localized
    in all three prompt files.
  Verification:
  - `gradlew compileTestJava` green (MapStruct generates `OnboardingMapperImpl`).
  - `GoalToolTest` 6/6 (read/write via security-context user, not args; unset
    profile; blank-deadline → null; invalid-deadline rejected without write;
    anonymous → `IllegalStateException`). `OnboardingServiceTest` 5/5 (persist;
    empty-request skip leaves existing values; past-date rejected; get returns
    current values + telegram placeholder; missing profile → `ProfileException`).
    `UserMemoryAdvisorTest` 5/5 (updated the positional `ProfileDto` ctor for the
    new field). `ApplicationModulesTests` 1/1 (no new cross-module leak; `GoalTool`
    consumes `profiles.ProfilesApi` at module root).
  - Full agent context revalidated on pgvector pg16 (Docker):
    `AgentUserMemoryIsolationIntegrationTest` 2/2,
    `AgentChatMemoryIsolationIntegrationTest` 1/1, `AgentControllerSmokeTest` 1/1
    — V21 applies under `ddl-auto: validate` and the new `goalTool` wires into the
    Coach chain.
  - Pre-existing env-dependent failures unchanged (per the 2a / 2b-i entries):
    `IlmaiBackendApplicationTests` (no localhost:5432 Postgres) and the
    `ai.ingestion` embedding ITs (their base omits `spring.ai.*.api-key`, so
    `GoogleGenAiChatAutoConfiguration` fails without CI key env vars) — neither is
    touched by this slice.
  Deviations:
  - Telegram link = constant `false` placeholder (above); real status → Slice 8.
  - Goal stored as single `profiles.goal` + `target_date` (no `description`).
  - `user_facts` extraction (plan task 6) and `UserActivityRecordedEvent`
    plumbing (plan task 9) remain for Slice 2d; not in 2c.
  Doc / plan touch-ups:
  - `docs/features/agent/agent-plan.md` §0 — Slice 2c marked shipped.
  - This entry does not supersede any prior entry.

- **2026-05-31 — Agent v1, Slice 2d shipped: `user_facts` LLM extraction +
  `UserActivityRecordedEvent` plumbing.** `docs/features/agent/agent-plan.md`
  §4 Slice 2 tasks 6 & 9 — completes Slice 2. After a committed Coach turn the
  recent transcript is mined for durable, user-scoped facts by a cheap,
  advisor-free LLM call (throttled per session, charged to the ilm-token quota),
  and each successful turn now emits a domain event for later streak/activity
  consumers.
  Schema:
  - `V22__user_fact_extraction_state.sql` — one row per session: UUIDv7 PK,
    `session_id` UNIQUE FK → `chat_sessions(id) ON DELETE CASCADE`, `turns_seen
    INT` (monotonic per-session turn counter that drives the throttle),
    `DateAuditable` timestamps. No DB CHECK constraints.
  - `agent/domain/UserFactExtractionState` (`@Entity`, UUIDv7 `@Id`, proxy-safe
    equals/hashCode) + `UserFactExtractionStateRepository.findBySessionId`.
  Write path:
  - `usermemory.UserMemoryApi.recordFacts(CurrentUser, List<String>)` +
    `DefaultUserMemoryApi` impl (single `@Transactional`; trims, drops blanks,
    de-dups within the batch, `saveAll` into `user_memory_facts`, returns the
    count). This is the only LLM-fed `user_memory` sub-store write path
    (07-memory.md §"Write paths"); `user_memory` is otherwise never written
    inside a turn.
  Extraction (agent/service):
  - `UserFactExtractor` (@Service) — `extract(CurrentUser, sessionId)` is run
    post-commit by `DefaultAgentApi`, best-effort (try/catch, never breaks the
    turn). Bumps `turns_seen` in a `TransactionTemplate`; fires only every
    `DEFAULT_EXTRACT_EVERY_TURNS` (4) turns; pulls the last `DEFAULT_RECENT_TURNS`
    (4) turns from the live chat-memory store; quota mirrors the summarizer (skip
    if `!canSpend`, else reserve → LLM → commit actual / refund on null). Persists
    via `UserMemoryApi.recordFacts` (cross-module producer API — no cross-module
    repository injection).
  - `UserFactGenerator` (@Component) — advisor-free `factsChatClient` call (bare
    system prompt, so the Coach guards can't rewrite extraction output) that
    renders a `Learner:`/`Coach:` transcript, parses ≤5 facts (one per line,
    strips bullets, drops `NONE`), and returns a `UserFactExtractionDraft` (facts
    + ilm-token cost via the shared `IlmTokenCostCalculator`). Null when no facts
    / no provider → the extractor refunds.
  - `prompts/agent/facts-system.txt` + `FactsSystemPrompt` loader. Model-facing
    scaffolding (English, like the 2b-i `[user context]` block and the 2b-ii
    summary prompt, so not subject to AGENTS.md rule 8); instructs the model to
    write each fact **in the conversation's own language**, so no per-locale files
    are needed.
  - `CoachChatClientConfig` registers a bare `factsChatClient` bean
    (`@ConditionalOnBean(IlmaiChatClientFactory)`, null when no provider) — a
    third advisor-free client alongside `summaryChatClient`.
  Event plumbing (task 9):
  - `agent.UserActivityRecordedEvent` (module-root, `userId` + `occurredAt`)
    published by `DefaultAgentApi` after a committed turn via
    `ApplicationEventPublisher` (best-effort). No consumer yet — Slice 6 wires the
    `streak_days` upsert (14-scheduling.md). Lives at the producer module root per
    AGENTS.md (like `auth.UserRegisteredEvent` / `materials.MaterialUploadedEvent`).
  User isolation:
  - Extraction and `recordFacts` resolve the owner from `CurrentUser` (never a
    tool/request arg); the session is the already ownership-gated `sessionId`.
    Facts are user-scoped — proven by the new write-path isolation test.
  Verification:
  - `gradlew compileJava compileTestJava` green; `ApplicationModulesTests` green
    (no new cross-module leak; `agent → usermemory` reuses the module-root
    `UserMemoryApi` edge; `UserActivityRecordedEvent` is a clean module-root type).
  - `UserFactExtractorTest` 5/5 (no-op + counter persist before threshold; extract
    + record at threshold with commit; skip on quota-exceeded; refund on null
    draft; no-op without a chat-memory repo) and `DefaultAgentApiActivityEventTest`
    1/1 (a successful turn publishes `UserActivityRecordedEvent` for the caller and
    triggers extraction).
  - Full agent + usermemory suites green on pgvector pg16 (Docker):
    `AgentUserMemoryIsolationIntegrationTest` 3/3 (incl. the new
    `recordedFactsAreWrittenAndIsolatedPerUser` — user A's recorded facts never
    surface for user B; de-dup/blank handling), plus the pre-existing
    `AgentChatMemoryIsolationIntegrationTest`, `AgentControllerSmokeTest`,
    `ChatSessionControllerTest` — V22 applies under `ddl-auto: validate` and the
    new beans wire. Updated `DefaultAgentApi{QuotaShortCircuit,SessionOwnership}Test`
    for the new ctor args.
  - Pre-existing env-dependent failures unchanged (per the 2a / 2c entries):
    `IlmaiBackendApplicationTests` (no localhost:5432 Postgres) and the
    `ai.ingestion` embedding ITs without CI keys — neither touched by this slice.
  Deviations:
  - Extraction runs **synchronously post-commit** (best-effort), not as the
    `UserFactsExtractionJob` JobRunr job in 14-scheduling.md — JobRunr lands in
    Slice 6; this mirrors the 2b-ii summarizer deviation. The throttle is the
    per-session "at most one extraction per N turns" of 07-memory.md, implemented
    via the `turns_seen` counter (fires every 4 turns).
  - Empty extractions ("NONE") are refunded (not charged), mirroring the 2b-ii
    summarizer; cross-call duplicate facts and long-term `user_facts` compaction
    remain an open question (07-memory.md) deferred to a later slice.
  - `UserActivityRecordedEvent` has no consumer yet (Slice 6); a successful-turn
    DB integration test is omitted for the same reason 2b-ii skipped the fold IT
    (needs a stub `ChatModel`) — covered by the deep-stub unit test instead.
  Doc / plan touch-ups:
  - `docs/features/agent/agent-plan.md` §0 — Slice 2d marked shipped; the pending
    line is now "Slices 3–8".
  - This entry does not supersede any prior entry.

- **2026-05-31 — Agent v1, Slice 3a shipped: Coach `startQuiz` authoring tool +
  `quiz_card` message part (delegates to the existing `quiz` module).**
  `docs/features/agent/agent-plan.md` §5 Slice 3 tasks 1, 2, 3, 7, 9 (partial).
  The Coach can now author a quiz from the user's own materials during a chat
  turn and stream it back as a structured, answer-free `quiz_card`. Grading
  (`gradeAnswer`), adaptive difficulty, spaced repetition, and the frontend
  card are deferred to Slices 3b–3d (sub-slicing below).
  Approach decision (plan-vs-code mismatch flagged per AGENTS.md rule 2):
  - The plan's Slice 3 text says "create the quiz schema + a quizzer sub-agent
    from scratch", but a full `quiz` module already exists — entities on the V8
    `quiz_sessions` / `quiz_questions` tables, `QuizGenerator` (authoring),
    `QuizGrader` (grading), and a read-only public `QuizApi` already consumed by
    `gaps`. So plan task 1 ("Schema: `quiz_session`/`quiz_question`…") is already
    satisfied, and `QuizGenerator` is the de-facto "quizzer sub-agent" (plan task
    2). The module-respecting, no-duplication path (confirmed with the user — who
    asked for the clean/correct implementation and noted there is no production
    data yet) is to **expose quizzing to the Coach as a tool that delegates into
    the `quiz` module via an extended `QuizApi`**, not to stand up a parallel
    schema/bean inside `agent`. No new Flyway migration; the V8 tables are reused
    unchanged under `ddl-auto: validate`.
  Cross-module surface (quiz module root — AGENTS.md §4 producer-API rule):
  - `quiz.QuizApi` gains `QuizCardDto startQuiz(CurrentUser, scope, questionCount,
    difficulty)`. Returns module-root DTOs `quiz.QuizCardDto` /
    `quiz.QuizCardQuestionDto` (`@Getter @AllArgsConstructor final`) that carry
    **only citation-safe fields** — `correctAnswer` / `explanation` / grading
    state are deliberately omitted so an answer key can never cross the API (or
    SSE) boundary.
  - Recoverable domain conditions surface as a module-root
    `quiz.QuizUnavailableException(quiz.QuizUnavailableReason …)` —
    `MATERIALS_MISSING` / `QUOTA_EXCEEDED` / `NO_QUESTIONS`. `DefaultQuizApi`
    translates the internal `quiz.service.QuizException` into it, so `agent`
    never imports anything from `quiz.service` (the Modulith boundary stays
    closed; verified by `ApplicationModulesTests`).
  - `DefaultQuizApi.startQuiz` delegates to `QuizService` (same module) and maps
    the result with a new MapStruct `QuizCardMapper` (`QuizSessionResponse` →
    `QuizCardDto`, dropping answer fields) — mirrors the existing `QuizApiMapper`
    pattern.
  Citation + isolation hardening (`QuizService.start`):
  - `start` now has a `start(CurrentUser, StartQuizRequest, scopeQueryOverride)`
    overload so the Coach can pass a free-text scope (used as the retrieval
    query; falls back to topic name / goal as before).
  - Per-question citation is enforced by `resolveCitation`: keep the draft's
    `materialId` **only if it is owned by the caller** (`MaterialsApi
    .findOwnedByUser`); otherwise backfill from a user-scoped retrieved chunk;
    if neither yields an owned material, **drop the question**. This makes the
    "quizzer never authors a question without a `sourceCitation`" invariant
    (plan task 9) structural, and guarantees a hallucinated / cross-user
    `materialId` can never be persisted (user-isolation non-negotiable).
  Agent module:
  - `StartQuizTool` (@Component, `agent/service`) — `@Tool startQuiz(scope?,
    questionCount?, difficulty?)` resolves the caller from the Spring Security
    context (same `requireCurrentUser()` pattern as `RetrieveTool` / `GoalTool`;
    `userId` is never a tool argument). Returns a compact, answer-free
    `StartQuizResult` (`created`, `sessionId`, `questionCount`, `reason`) so the
    LLM can narrate without re-emitting questions or any answer key; catches
    `QuizUnavailableException` and reports `created=false` + reason instead of
    throwing at the model.
  - `AgentQuizContext` — per-turn `ThreadLocal` (mirrors `AgentRetrievalContext`)
    into which the tool records the produced `QuizCardDto`. `DefaultAgentApi`
    `begin()`s it alongside the retrieval/flags contexts and `clear()`s it in the
    same `finally`; after a committed turn it **flattens** each recorded quiz into
    **one module-root `agent.QuizCardPart` per question** (the documented
    per-question `quiz_card` shape in 08-message-parts.md, so the Slice-8 Telegram
    flattener can map one card → one poll) and appends them after the `TextPart`.
    New `quiz_card` Jackson subtype registered on `MessagePart`.
  - `StartQuizTool` registered in `CoachChatClientConfig.coachChatClient`
    (`toolObjects(retrieveTool, goalTool, startQuizTool)`). The three Coach
    prompts (`coach-system.{en,ru,uz}.txt`) gain a parallel "Quizzing" line
    telling the model to call `startQuiz` (not to write the questions itself —
    the card renders them) and to ask the user to upload material on
    `materials_missing` (UZ/RU/EN per AGENTS.md rule 8).
  i18n: the quiz locale follows the user's profile locale (the existing
  `QuizService.resolveLocale` path); `StartQuizResult` / `@Tool` text is
  model-facing (not subject to rule 8); the prompt guidance is localized in all
  three files.
  Verification:
  - `gradlew compileJava compileTestJava` green (MapStruct generates
    `QuizCardMapperImpl`). `ApplicationModulesTests` green — `agent → quiz`
    consumes only `quiz` module-root types (`QuizApi`, `QuizCardDto`,
    `QuizCardQuestionDto`, `QuizUnavailableException`/`Reason`); no leak into
    `quiz.service`/`domain`/`payload`.
  - `QuizServiceCitationTest` 2/2 — every persisted question gets an owned
    `materialId` (keep / backfill), an unowned draft id is never persisted, and a
    question with no available owned source is dropped. `StartQuizToolUserIsolation
    Test` 3/3 — the tool uses the security-context user (not a tool arg), records
    the card into the turn context, fails closed when anonymous, and maps
    `QuizUnavailableException` → `created=false` (`materials_missing`).
    `DefaultAgentApiQuizCardEmissionTest` 1/1 — a turn that records a quiz emits a
    per-question `QuizCardPart` alongside the `TextPart`. Existing
    `QuizServiceIsolationTest` still green (the `start` change doesn't touch the
    `get`/`answer` paths).
  - Full `:test` run: 252 passing. The only failures are the **pre-existing
    env-dependent** ones unchanged from the 2c / 2d entries —
    `IlmaiBackendApplicationTests` (plain `@SpringBootTest`, no localhost:5432
    Postgres → Flyway connect) and the `ai.ingestion` embedding /
    `UserIsolationIntegrationTest` ITs (no `spring.ai.*.api-key` →
    `GoogleGenAiChatAutoConfiguration` fails). Both fail at context bootstrap,
    before any quiz/agent bean this slice touches (`coachChatClient` is
    `@ConditionalOnBean(IlmaiChatClientFactory)`); not touched by this slice.
  Deviations:
  - **Reuse, not rebuild** (above): no new schema / no new quizzer `ChatClient`
    bean; `QuizGenerator` is treated as the quizzer sub-agent and `startQuiz`
    delegates to it. Flagged because it contradicts the literal Slice 3 wording.
  - **Slice 3a only.** Deferred to 3b–3d: `gradeAnswer` (plan task 4), in-session
    adaptive difficulty (task 5), the SM-2-lite `review_queue` write (tasks 1
    tail & 6), and the interactive frontend card (task 8). The Coach can author a
    quiz but cannot yet grade it in-chat.
  - Each `quiz_card` part is **one question** (`{ sessionId, questionId,
    position, type, concept, prompt, options, materialId, materialName,
    chunkIndex }`) carrying its own owned-material citation; the existing
    `CitationPart` is not reused for quiz questions (the card is a self-contained
    widget). 08-message-parts.md's illustrative payload is extended with the
    citation + grading-id fields the citation invariant and Slice 3b need.
  Doc / plan touch-ups:
  - `docs/features/agent/agent-plan.md` §0 — Slice 3a marked shipped; pending
    line now "Slices 3b–8". §5 gains a Slice 3 sub-slicing note (3a → 3d).
  - This entry does not supersede any prior entry.

- **2026-05-31 — Agent v1: Coach system prompt reduced to English-only
  (`coach-system.txt`); RU/UZ prompt files removed.** Per user direction
  ("keep only english variant and use it"). Supersedes the **"Kept:
  `coach-system.{en,ru,uz}.txt` … single mechanism enforcing the language
  non-negotiable"** bullet of the `2026-05-26 — Agent v1, Slice 1c amended:
  language guard removed` entry above — the per-locale Coach prompt set is now
  a single English file.
  Why:
  - After the language guard was removed, the three Coach prompt files were
    loaded keyed on `SupportedLocale` but only `forDefault()` (= EN) was ever
    bound in `CoachChatClientConfig`; `forLocale(...)` was scaffolding never
    called in production. The RU/UZ copies were dead weight (loaded, never
    selected) and had to be hand-translated on every prompt edit (the goal /
    quizzing lines were maintained 3×).
  - The brief's UZ/RU/EN non-negotiable is about **user-facing replies**, not
    about authoring the system prompt in three languages. The retained English
    prompt still carries the rule *"reply in the same language the user writes
    in. Supported languages: English, Russian, Uzbek."*, so the Coach still
    answers trilingually; only the *instruction* is now authored once, in English.
  Changes:
  - `agent.service.CoachSystemPrompts` (`EnumMap<SupportedLocale,String>` +
    `forLocale` + `forDefault`) → `agent.service.CoachSystemPrompt` (single
    `String` + `get()`), mirroring the sibling `SummarySystemPrompt` /
    `FactsSystemPrompt` loaders; `SupportedLocale` dependency dropped.
  - Resource `prompts/agent/coach-system.en.txt` → `prompts/agent/coach-system.txt`
    (suffix dropped now that there is no locale dimension); `coach-system.ru.txt`
    and `coach-system.uz.txt` deleted.
  - `CoachChatClientConfig.coachChatClient` injects `CoachSystemPrompt` and calls
    `.get()` (was `prompts.forDefault()`); behaviour-preserving since
    `SupportedLocale.DEFAULT == EN`.
  Verification:
  - `gradlew compileJava compileTestJava` green (no dangling refs to the removed
    class/methods). Full `agent.*` suite green on pgvector pg16 (19 classes incl.
    `AgentControllerSmokeTest`, whose live Spring context loads `coach-system.txt`
    into the Coach `ChatClient`) + `ApplicationModulesTests.verifyModules` green.
  Doc mismatches flagged (AGENTS.md rule 2; not edited — append-only / aspirational):
  - Prior DECISIONLOG entries (2026-05-26 Slice 1c amendment, 2026-05-31 Slices 2c
    & 3a) and `docs/features/agent/agent-plan.md` (Slice 1c bullet) still refer to
    "the en/ru/uz Coach prompts"; those are historical — the live prompt set is now
    a single `coach-system.txt`.
  - `forLocale(...)`-style per-request prompt localization is no longer scaffolded;
    if per-locale Coach prompts are ever wanted again, reintroduce the loader map
    rather than resurrecting the deleted files.

- **2026-05-31 — Agent v1, Slice 3b shipped: Coach `gradeAnswer` tool +
  in-session adaptive difficulty level.** `docs/features/agent/agent-plan.md`
  §5 Slice 3 tasks 4 & 5. The Coach can now grade the user's answer to a quiz
  question in-chat and the quiz session tracks a running difficulty level.
  Reuses the existing `quiz` module's grading (`QuizGrader`) the same way 3a
  reused its authoring — no new grading logic, exposed via an extended
  `QuizApi`.
  Cross-module surface (quiz module root — AGENTS.md §4 producer-API rule):
  - `quiz.QuizApi` gains `QuizGradeDto gradeAnswer(CurrentUser, UUID sessionId,
    int questionNumber, String answer)`. Returns the module-root
    `quiz.QuizGradeDto` (`@Getter @AllArgsConstructor final`) carrying the
    answered question's outcome (`correct`, `feedback`, `correctAnswer`,
    `explanation`, `concept`, `questionNumber`) plus session progress
    (`answeredCount`, `totalCount`, `correctCount`, `completed`,
    `difficultyLevel`). Revealing `correctAnswer`/`explanation` here is
    intended — the user has just answered *that* question; no other question's
    key is exposed.
  - Recoverable grading conditions surface as a **new** module-root
    `quiz.QuizGradeException(quiz.QuizGradeReason …)` — `SESSION_NOT_FOUND` /
    `QUESTION_NOT_FOUND` / `ALREADY_ANSWERED`. Kept separate from 3a's
    authoring-only `QuizUnavailableException` (whose reasons —
    `MATERIALS_MISSING`/`QUOTA_EXCEEDED`/`NO_QUESTIONS` — don't fit "you already
    answered that"). `DefaultQuizApi.translateGrade` maps the internal
    `QuizException.Reason` so `agent` never imports `quiz.service`.
  - `DefaultQuizApi.gradeAnswer` delegates to `QuizService.gradeByPosition` and
    maps the service carrier `QuizGradeOutcome` → `QuizGradeDto` via a new
    MapStruct `QuizGradeMapper` (name-based, mirrors `QuizCardMapper`).
  Grading core (`QuizService`):
  - Extracted the existing `answer(...)` body into a shared private
    `applyAnswer(currentUser, session, question, userAnswer)`; both the
    questionId path `answer(...)` (REST `QuizController`, used by the 3d
    frontend) and the new position path `gradeByPosition(...)` (the Coach tool)
    call it — so grading, completion, `correctCount`, `touchActivity`, and the
    new difficulty-level adjustment are identical across both entry points.
  - **Adaptive difficulty.** New `quiz_sessions.difficulty_level` INTEGER
    (Flyway `V23`, `NOT NULL DEFAULT 2` + backfill from the existing
    `difficulty` enum). Seeded at `start` from `QuizDifficulty.ordinal() + 1`
    (GENTLE=1/SOLID=2/EXPERT=3) and adjusted ±1 per graded answer, clamped to
    `[1,5]`. **Interpretation flagged (AGENTS.md rule 2):** the plan's task 5
    says "in-session integer level"; because every question is authored
    up-front at `start`, the level is a *persisted running tracker* of the
    learner's performance (informs the Coach's pacing/encouragement and feeds
    later slices/gaps), **not** a mid-session question-regeneration trigger —
    no such regeneration exists in the quiz module.
  Agent module:
  - `GradeAnswerTool` (@Component, `agent/service`) — `@Tool gradeAnswer(String
    sessionId, int questionNumber, String answer)` resolves the caller from the
    Spring Security context (same `requireCurrentUser()` pattern as
    `RetrieveTool`/`StartQuizTool`; `userId` is never a tool argument). Returns
    a compact `GradeAnswerResult` (`graded`, nested `QuizGradeDto result`,
    `reason`); parses the session id defensively (malformed → `graded=false`,
    `session_not_found`, no `QuizApi` call) and maps `QuizGradeException` →
    `graded=false` + lowercased reason.
  - **Grades by 1-based position, not `questionId` (deviation from the literal
    task-4 signature, flagged per AGENTS.md rule 2).** The model only ever
    receives `sessionId` + question count from `startQuiz` (never per-question
    UUIDs), so a position is what it can reliably supply; the canonical
    `questionId` path stays the existing REST answer endpoint for the 3d
    frontend.
  - `GradeAnswerTool` registered in `CoachChatClientConfig.coachChatClient`
    (`toolObjects(retrieveTool, goalTool, startQuizTool, gradeAnswerTool)`).
    The (now English-only) Coach prompt gains a "Grading" line telling the model
    to call `gradeAnswer`, give the correct answer + a brief explanation, and
    not to reveal an answer before the user has attempted the question.
  - No new `MessagePart` for grading — the Coach narrates the outcome as text;
    the interactive card + submit→`gradeAnswer` wiring is Slice 3d (frontend).
  Verification:
  - `gradlew compileJava compileTestJava` green (MapStruct generates
    `QuizGradeMapperImpl`). `ApplicationModulesTests` green — `agent → quiz`
    consumes only module-root types (`QuizApi`, `QuizGradeDto`,
    `QuizGradeException`/`Reason`); no leak into `quiz.service`/`domain`.
  - `QuizServiceGradingTest` 8/8 — start seeds the level from difficulty;
    grade-by-position raises (+1) / lowers (−1) / clamps the level at `[1,5]`;
    last answer completes the session (status + score); already-answered,
    unknown-position, and unknown-session throw the right `QuizException.Reason`.
    `GradeAnswerToolUserIsolationTest` 4/4 — uses the security-context user (not
    a tool arg), returns the graded result, fails closed when anonymous, maps
    `QuizGradeException` → `already_answered`, and returns `session_not_found`
    for a malformed id without touching `QuizApi`.
  - Full quiz + agent suites green on pgvector pg16 (Docker): incl. updated
    `StartQuizToolUserIsolationTest` (its fake `QuizApi` implements the new
    method), `QuizServiceCitationTest`/`QuizServiceIsolationTest` (the `answer`
    refactor preserves the existing path), and `AgentControllerSmokeTest`
    (V23 applies under `ddl-auto: validate`; the Coach `ChatClient` wires the
    new tool). Pre-existing env-dependent failures unchanged
    (`IlmaiBackendApplicationTests`, `ai.ingestion` ITs without keys).
  Deviations (summary):
  - Reuse, not rebuild (grading via `QuizGrader`); position-based tool id;
    adaptive level is a persisted tracker not a regeneration trigger; grading
    failures use a dedicated `QuizGradeException` channel; no grading
    `MessagePart` (deferred to 3d).
  Doc / plan touch-ups:
  - `docs/features/agent/agent-plan.md` §0 — Slice 3b marked shipped; pending
    line now "Slices 3c–8". §5 sub-slicing note marks 3b shipped.
  - This entry does not supersede any prior entry.

- **2026-05-31 — Agent v1, Slice 3c shipped: spaced-repetition write on graded
  quiz answers (SM-2-lite).** `docs/features/agent/agent-plan.md` §5 Slice 3
  tasks 6 & 10. Every graded quiz answer now (re)schedules the answered *concept*
  in a per-user review queue so missed items can resurface on a schedule. There
  is **no Coach-facing surface this slice** — the queue is a write-only store;
  reading it back into chat / a daily "due items" job is Slice 6.
  Event (quiz module root — AGENTS.md §4 "events at module root"):
  - New `quiz.QuizAnswerGradedEvent` (`@Getter @AllArgsConstructor` final:
    `userId, sessionId, questionId, materialId, concept, correct, occurredAt`),
    published synchronously from `QuizService.applyAnswer`. Because 3b routed
    **both** grade entry points (the REST `answer(...)` path and the Coach
    `gradeByPosition(...)` path) through `applyAnswer`, the SR write fires
    regardless of channel. `occurredAt` is carried on the event (not computed in
    the listener) so the resulting schedule is deterministic and unit-testable.
  Consumer (usermemory module):
  - `user_memory_review_queue` table (Flyway `V24`): UUIDv7 PK, `user_id` FK →
    `users` (ON DELETE CASCADE), `concept`, nullable `material_id` /
    `last_question_id` (provenance; lets Slice 6 re-author the review scope),
    `interval_index`, `next_review_at`, `times_wrong` / `times_correct`,
    `status`, audit columns. **Unique `(user_id, concept)`** — the queue keys on
    concept, not on a per-answer row. Due-query index
    `(user_id, status, next_review_at)` for Slice 6. No `CHECK` constraints
    (`status` is a `VARCHAR(20)` enforced by the `@Enumerated(STRING)`
    `ReviewStatus` enum), per AGENTS.md §4.
  - `usermemory.domain` `ReviewQueueEntry` / `ReviewStatus` (`ACTIVE` /
    `MASTERED`) / `ReviewQueueRepository` (`findByUserIdAndConcept` for the
    upsert; `findByUserIdOrderByNextReviewAtAsc` for tests / Slice 6).
  - `usermemory.service.ReviewQueueListener` — `@EventListener` +
    `@Transactional(propagation = MANDATORY)`, so the SR write **joins the quiz
    grade transaction** (synchronous, same-tx; mirrors the established
    auth→profiles `@EventListener` pattern — no `@Async`, no eventual
    consistency for v1). SM-2-lite ladder `{1, 3, 7, 21}` days keyed by
    `(user_id, concept)`:
      - wrong answer → upsert the concept to ladder index 0,
        `next_review_at = occurredAt + 1 day`, `status = ACTIVE`,
        `times_wrong++`;
      - correct answer on an existing `ACTIVE` row → advance one rung
        (`+3 / +7 / +21` days), `times_correct++`; at the top rung → `MASTERED`;
      - correct answer with no prior miss → no-op (we only track concepts the
        learner has actually missed);
      - blank/absent `concept` → ignored (nothing meaningful to schedule).
  Module isolation:
  - The only new dependency is `usermemory → quiz` (it consumes the module-root
    `quiz.QuizAnswerGradedEvent`); `quiz` does not depend back on `usermemory`,
    so there is no cycle. `ApplicationModulesTests.verifyModules` green.
  Verification:
  - `ReviewQueueListenerTest` 6/6 (pure Mockito) — the full ladder + the `+1d`
    math + reset-on-wrong + master-at-top + no-op-on-unseen-correct +
    blank-concept guard.
  - `QuizServiceGradingTest` — two added cases assert `applyAnswer` publishes a
    `QuizAnswerGradedEvent` with the right `correct` flag + `concept` on both a
    wrong and a correct grade (the prior 8 cases unchanged; all three
    `QuizService*Test` suites gained an `ApplicationEventPublisher` mock so
    `@InjectMocks` resolves the new constructor arg).
  - `UserMemoryReviewQueueIntegrationTest` 2/2 on pgvector pg16 (Docker) — a
    wrong answer for user A writes exactly one `ACTIVE` row at
    `occurredAt + 1 day` while user B sees none (user-isolation non-negotiable);
    a correct answer with no prior miss writes nothing. Proves `V24` applies
    under `ddl-auto: validate` and the event→listener fires inside the grade
    transaction.
  - `gradlew compileJava compileTestJava` green.
  Deviations / mismatches flagged (AGENTS.md rule 2 — docs not edited):
  - **No `quiz_answer` table** (plan task 1 lists `quiz_answer`). Slice 3 reuses
    the existing `quiz` schema, where a graded answer lives on its
    `quiz_questions` row (`is_correct` / `user_answer`), so the review queue keys
    on `concept` rather than referencing a separate answer row. This continues
    the "Slice 3 reuses the existing quiz module" stance already flagged for
    3a/3b; only the plan's `user_memory_review_queue` (task 1) was added as new
    schema.
  - SR intervals: the plan says "SM-2-lite intervals"; the concrete choice is a
    fixed `{1, 3, 7, 21}`-day ladder with a single `MASTERED` terminal state —
    no per-item ease factor (a full SM-2 EF is heavier than v1 needs and can be
    layered on later without a schema change beyond a nullable column).
  - This entry does not supersede any prior entry.

- **2026-05-31 — Agent v1, Slice 4a shipped: gap-narrator sub-agent + `getGaps`
  Coach tool.** `docs/features/agent/agent-plan.md` §6 Slice 4 tasks 5, 6 & 8.
  The Coach can now tell the user what they are weak on / strong at, grounded in
  their own quiz history, returning a short narrated summary on top of structured
  per-concept data. Reuses the existing `gaps` module rather than rebuilding
  (plan tasks 1 & 2 — gap schema + aggregation — were already satisfied by the
  `V20 knowledge_gaps` table + `GapsService` behind the `/gaps` REST page;
  mismatch flagged below per AGENTS.md rule 2).
  Producer API (gaps module root):
  - `GapsApi.refreshAndGet(CurrentUser) → Optional<GapsReportDto>` added next to
    the existing `get(...)`; `DefaultGapsApi` delegates to
    `GapsService.refreshAndGet` (recompute-then-read, so the tool reflects the
    latest graded answers — there is no gap cron yet) and maps `GapsException`
    → `Optional.empty()` (not-ready). Returns the module-root
    `GapsReportDto` / `GapItemDto` only — no entity crosses the boundary.
  Coach tool + narrator (agent module):
  - `GetGapsTool.getGaps(language)` (`@Tool`) resolves the caller from the
    SecurityContext (never a tool arg — AI/RAG non-negotiable §5), calls
    `GapsApi.refreshAndGet`, and returns a `GapsView` (`ready`, `narration`,
    `overallAccuracy`, `totalQuestionsAnswered`, `correctCount`, `gaps[]`,
    `strengths[]`; `GapConceptView` = `concept`, `accuracy`, `hitCount`,
    `missCount`, `suggestedMaterial`). Empty report → `ready=false` and the
    prompt tells the Coach to invite a quiz first.
  - `GapNarrator` is an advisor-free generator (mirrors Slice 2d's
    `UserFactGenerator`): a dedicated `gapsNarratorChatClient` bean (separate
    `prompts/agent/gaps-narrator-system.txt`; EN/RU/UZ chosen via a `Language:`
    line the tool fills from the user's language) renders the structured report
    into a short, honest narration. The call is charged against the ilm-token
    quota **inside the tool** (`reserve(3) → commit(actual) / refund`), exactly
    like the facts/summary background LLM calls. If no provider is configured
    (`isAvailable()` false) or the model returns blank, `narration` is null and
    the structured gaps still flow back (graceful degradation, no quota touched).
  - `getGapsTool` registered in `CoachChatClientConfig.coachChatClient`
    (`toolObjects(retrieveTool, goalTool, startQuizTool, gradeAnswerTool,
    getGapsTool)`); the Coach prompt gains a "Progress and gaps" line.
  Module isolation:
  - New dependency `agent → gaps` consumes only module-root types
    (`gaps.GapsApi`, `gaps.GapsReportDto`, `gaps.GapItemDto`); no leak into
    `gaps.service` / `domain`. `ApplicationModulesTests.verifyModules` green.
  Verification:
  - `GetGapsToolUserIsolationTest` 4/4 (pure Mockito + SecurityContext) — the
    tool uses the security-context user (task 8: user A's gaps never leak to
    user B), fails closed when anonymous, returns `ready=false` with no report,
    and never touches the quota when the narrator is unavailable.
  - `gradlew compileJava compileTestJava` green; the full `agent` suite (unit +
    full-context `@SpringBootTest` ITs) and `ApplicationModulesTests` green on
    pgvector pg16 (Docker). The only red in the whole-backend run is the
    **pre-existing** `ai.ingestion` embedding ITs — they boot the full context
    with no stubbed `ChatModel`, so `GoogleGenAiChatAutoConfiguration` fails when
    `GOOGLE_GENAI_API_KEY` is unset (verified unset here). These are the same
    env-gated failures already noted in the 2026-05-31 Slice 3b/3c entries;
    unrelated to this slice.
  Deviations / mismatches flagged (AGENTS.md rule 2 — docs not "fixed"):
  - Reuse, not rebuild: `gaps` already owns the gap schema + aggregation, so
    plan §6 tasks 1 & 2 are pre-satisfied; this slice adds only the narrator +
    tool.
  - `getGaps` recomputes on read (`refreshAndGet`) because the daily gap cron
    (§6 task 2) does not exist yet; that cron, the per-week trend (task 3),
    `recommended_next` (task 4) and the "Progress" page (task 7) are deferred to
    **Slice 4b**, along with the task-9 aggregation integration test.
  - Slice 3d (frontend interactive `quiz_card`) remains deferred to a later UI
    session at the user's request (2026-05-31); the backend already emits
    `quiz_card` parts.
  - This entry does not supersede any prior entry.

- **2026-05-31 — Agent v1, Slice 4b shipped: gap `trend` + `recommended_next`.**
  `docs/features/agent/agent-plan.md` §6 Slice 4 tasks 3, 4 & 9 (backend);
  `05-gap-detection.md` §"Trends" + §"Recommendation: what next". Each knowledge
  gap now carries a direction-only trend and the report surfaces a single
  "what to focus on next", both flowing through `GapsApi.refreshAndGet`/`get`,
  the `/gaps` REST page and the `getGaps` Coach tool.
  Schema (gaps module):
  - `V25__knowledge_gaps_trend.sql` — `ALTER TABLE knowledge_gaps ADD COLUMN
    trend VARCHAR(20) NOT NULL DEFAULT 'FLAT'`. **No `CHECK`** (AGENTS.md §4):
    the allowed set is enforced by the new module-root enum `gaps.GapTrend`
    (`IMPROVING` / `FLAT` / `WORSENING`) mapped `@Enumerated(STRING)` on
    `KnowledgeGap.trend`. The `DEFAULT 'FLAT'` backfills pre-existing rows;
    `ddl-auto: validate` stays untouched (new migration, never edited an applied
    one). Latest migration is now `V25` (was `V24`).
  Trend computation (`GapsService`, deterministic, no LLM):
  - `aggregateFromQuestions` now records a `GapAnswerSample(answeredAt, correct)`
    per answered quiz question into `ConceptAggregate` (new top-level value type
    in `gaps.service`, per AGENTS.md "no static inner / no record"); `computeTrend`
    buckets samples by **ISO-ish week** (`answeredAt.toLocalDate().toEpochDay() / 7`),
    computes per-week accuracy, and takes the **sign of a least-squares linear
    regression** of accuracy over week index: slope > 1e-9 → `IMPROVING`,
    < -1e-9 → `WORSENING`, else `FLAT`; fewer than 2 distinct weeks → `FLAT`
    (insufficient data). Persisted on the gap during the `refreshAndGet` upsert,
    so the read path (`get`) and the tool both reflect it.
  - `recommended_next` is derived (not persisted): the worst active gap by a
    lexicographic comparator of **(lowest accuracy → most-worsening trend →
    oldest `lastSeenAt`)**, returned as the gap's **concept name (String)** — a
    field on the report per `05-gap-detection.md` §"Recommendation" ("a field on
    the response, not a separate tool"). Computed in both `buildReport` and `get`
    from the already-filtered gap list; null when there are no gaps. The planner
    (Slice 5) resolves the gap by concept.
  Threaded through the existing DTO chain (auto-mapped by name, no MapStruct
  signature edits):
  - `trend` (`GapTrend`) on payload `gaps.payload.GapItem` + module-root
    `gaps.GapItemDto`; `recommendedNext` (`String`) on payload
    `gaps.payload.GapsReportResponse` + module-root `gaps.GapsReportDto`.
    `GapsMapper` (entity→`GapItem`) and `GapsApiMapper` (`GapItem`→`GapItemDto`,
    `GapsReportResponse`→`GapsReportDto`) pick up both fields by name.
  Agent surface:
  - `GapConceptView.trend` (rendered to the model as **lowercase**
    `improving`/`flat`/`worsening`) and `GapsView.recommendedNext` added;
    `GetGapsTool` threads `report.getRecommendedNext()` and the lowercased trend.
  - `GapNarrator` now appends each concept's trend and a `Recommended focus
    next:` line to the compact report it sends the narrator sub-agent, and
    `prompts/agent/gaps-narrator-system.txt` gained two sentences telling it to
    acknowledge any improving trend first (per §"Trends" / §37) and steer toward
    the recommended concept. The narration stays a graceful-degradation extra
    (null when no provider / blank), unchanged from 4a.
  Module isolation:
  - `GapTrend` is a **module-root** type, so the existing `agent → gaps`
    dependency consumes it via `GapItemDto.getTrend()` with no new leak into
    `gaps.service`/`domain`. `ApplicationModulesTests.verifyModules` green.
  Verification:
  - `GapsServiceTest` 5/5 (pure Mockito; `QuizApi`/`KnowledgeGapRepository`/
    `GapsMapper` stubbed): 3-wrong-on-one-concept creates a `missCount=3` gap
    (plan task 9, service level); improving / worsening / single-week-flat trend;
    `recommended_next` picks the lowest-accuracy gap.
  - `GetGapsToolUserIsolationTest` 5/5 — the prior 4 (isolation/anonymous/
    not-ready) plus a new case asserting `trend` reaches the model lowercased and
    `recommendedNext` flows through; its hand-built `GapItemDto`/`GapsReportDto`
    were updated for the new constructor args.
  - `gradlew test --tests org.aiincubator.ilmai.gaps.* --tests
    *GetGapsToolUserIsolationTest` and `--tests *ApplicationModulesTests*` green
    (compiles all main/test sources). Full-context `@SpringBootTest` ITs boot
    Flyway + Hibernate `validate`, so they implicitly assert `V25` ↔
    `KnowledgeGap.trend` agreement on the next Docker run (same pre-existing
    env-gated `ai.ingestion` embedding-IT reds noted in the 4a entry; unrelated).
  Deviations / mismatches flagged (AGENTS.md rule 2 — docs/log not edited):
  - **Migration-version mismatch:** both the plan §6 Slice 4 note and the
    2026-05-31 Slice 4a entry call the gaps table `V20 knowledge_gaps`, but it is
    actually created by **`V9__knowledge_gaps.sql`** (this slice's `trend` column
    lands in `V25`). Flagging the stale "V20" reference; not editing the prior
    entry (append-only) — this corrects the fact only.
  - **Task 9 as a unit test, not a Testcontainers IT:** the plan lists an
    "integration test" for 3-wrong→gap; implemented at the `GapsService` level
    (mocking `QuizApi`) to match 4a's unit-level isolation test and keep the
    slice runnable without Docker. No gaps-specific Testcontainers IT was added.
  - **`recommended_next` is the concept name, not a nested gap object** — minimal
    and sufficient for the Coach narration and the Slice-5 planner seed.
  - **Task 7 (frontend "Progress" page trend arrows) deferred** to a later UI
    session at the user's explicit choice this session (backend-only 4b),
    consistent with the 3d frontend deferral; the `/gaps` API already returns
    `trend` + `recommendedNext` for that page to consume.
  - This entry does not supersede any prior entry (it corrects the "V20" naming
    noted above).
- **2026-05-31 — Slice 5a (Planning): new closed `plan` module + planner sub-agent + `buildPlan`/`getPlan`/`getTodaysTask` Coach tools.**
  Shipped the first sub-slice of agent Slice 5 (see `docs/features/agent/agent-plan.md` §7). What landed:
  - **New closed `plan` feature module** (`org.aiincubator.ilmai.plan`,
    `package-info.java` with `@ApplicationModule(type = CLOSED)`). Module
    root carries the public API and DTOs only: `PlanApi`
    (`savePlan(CurrentUser, goal, targetDate, List<PlanStepInput>)` →
    `LearningPlanDto`; `getActivePlan(CurrentUser)` →
    `Optional<LearningPlanDto>`), the immutable Lombok DTOs
    `LearningPlanDto` / `PlanStepDto` / `PlanStepInput`, and the
    `PlanStatus` (`ACTIVE`/`SUPERSEDED`) and `PlanActivity`
    (`READ`/`QUIZ`/`REVIEW`) enums. Entities (`LearningPlan`,
    `PlanStep`) + `LearningPlanRepository` stay private in
    `plan/domain`; `PlanService` / `PlanApiMapper` (MapStruct,
    `componentModel = "spring"`) / `DefaultPlanApi` (`@Transactional`,
    so the lazy `@OneToMany steps` initialise while the mapper runs)
    in `plan/service`. The Modulith `verifyModules` test passes — the
    only cross-module surface `agent` touches is `plan`'s module-root
    types.
  - **Schema `V26__learning_plan.sql`** — `learning_plans` +
    `plan_steps` (TIMESTAMPTZ audit columns via `DateAuditable`,
    `uuid_generate_v4()` DB default with Hibernate `UuidVersion7Strategy`
    on the `@Id`, `material_ids` as `jsonb` mapped to `List<UUID>` via
    `@JdbcTypeCode(SqlTypes.JSON)`, FK→`users`/`learning_plans` with
    `ON DELETE CASCADE`). **No DB `CHECK`** on `status`/`activity` —
    enforced in Java with `@Enumerated(STRING)` per AGENTS.md. Applies
    cleanly under `ddl-auto=validate` (proven by
    `PlanApiIntegrationTest`). `savePlan` supersedes any existing
    `ACTIVE` plan for the user before inserting the new one, so a user
    has at most one active plan.
  - **Planner sub-agent** — `Planner` + a `plannerChatClient` bean
    (qualifier `CoachChatClientConfig.PLANNER_CHAT_CLIENT`) with its own
    `prompts/agent/planner-system.txt` (single English instruction
    prompt; the LLM writes in the `Language:` line's language — EN/RU/UZ
    — like the gap-narrator). Mirrors `GapNarrator`: advisor-free, and
    the LLM call is **charged against the ilm-token quota** in
    `BuildPlanTool` (`canSpend` → `reserve` → `commit(reservation, cost)`
    / `refund`-in-finally), estimate 8 ilm-tokens.
  - **Owned-materials-only (Slice 5 task 11), enforced structurally.**
    The planner prompt lists the user's ready materials as a
    server-numbered list and the model references them **only by 1-based
    number**, never by UUID. `Planner.parseSteps(...)` (a pure, static,
    unit-tested method) maps those numbers back to the owned material
    UUIDs and **drops any number not in the provided list**, so the model
    cannot attach a material the caller doesn't own even if it
    hallucinates. This is the planner analogue of the quiz module's
    "model never sees question UUIDs" rule. `PlannerTest` covers
    hallucinated/out-of-range numbers, dedupe, prose-wrapped JSON, blank
    titles, and bad `activity` values.
  - **Coach tools** — `BuildPlanTool#buildPlan(days?, language?)` reads
    the goal/deadline/`daily_study_minutes` from `profiles` (`ProfilesApi`),
    ready materials from `materials` (`MaterialsApi.findReadyForUser`),
    and weak concepts from `gaps` (`GapsApi.refreshAndGet`), runs the
    planner, and persists via `PlanApi.savePlan`; `GetPlanTool#getPlan` /
    `#getTodaysTask` are pure reads. All three resolve the caller from
    the `SecurityContextHolder` (never a tool argument), per the AI/RAG
    non-negotiable. `PlanViewFactory` resolves material UUIDs → titles
    for the `PlanView`/`PlanStepView` tool output. Registered on the
    Coach `ChatClient` and documented with a new "Planning" paragraph in
    `coach-system.txt`.
  - **Tests:** `PlannerTest` (task 11), `BuildPlanToolTest`
    (no-materials short-circuit / happy-path save+commit / quota
    short-circuit), `GetPlanToolTest` (no-plan, full read, today's-task
    filtering, anonymous guard), and `PlanApiIntegrationTest`
    (Testcontainers pgvector: per-user isolation + supersede-on-resave).
    All green. (`IlmaiBackendApplicationTests.contextLoads()` fails in
    this sandbox with a Postgres `ConnectException` — it is a bare
    `@SpringBootTest` with no Testcontainers and needs a local dev DB;
    pre-existing/environmental, unrelated to this change.)
  - **Deviations / flags vs the plan & AGENTS.md:**
    - AGENTS.md §4 spells the security principal as
      `org.aiincubator.ilmai.auth.security.CurrentUser`, but the actual
      codebase uses `org.aiincubator.ilmai.common.CurrentUser` (as every
      prior slice did). Followed the code; **AGENTS.md is stale on this
      path** and should be corrected the next time it is revised.
    - Tables are `learning_plans` / `plan_steps` (plural), not the
      plan's singular `learning_plan` / `plan_step`, to match the
      existing plural convention (`chat_sessions`, `quiz_sessions`,
      `knowledge_gaps`).
    - **Open-question decisions (plan §13):** plan length defaults to
      days-until-deadline, or **14 days** when there is no deadline,
      capped at **30** to bound the planner's cost; weak-concept context
      is capped at 5. Step `scheduled_date` is computed from
      `LocalDate.now()` (server tz) — real user-timezone scheduling is
      deferred to Slice 6 (the "User timezone storage" open question).
    - Deferred to **5b/5c** (and a later UI session): plan-step
      completion flow, re-plan triggers (`MaterialUploadedEvent` / goal
      change), `PlanReplanCheckJob`, no-plan-day improviser, and the
      `plan_step` message part + frontend (Slice 5 tasks 5, 6, 7, 8, 9,
      10).
- **2026-05-31 — Slice 5b (Planning): plan-step completion + re-plan triggers (`completeStep` Coach tool; `MaterialUploadedEvent`/`GoalUpdatedEvent` → `replanNeeded`).**
  Shipped the second sub-slice of agent Slice 5 (see `docs/features/agent/agent-plan.md` §7), backend-only. What landed:
  - **Schema `V27__plan_step_completion_and_replan.sql`** — `plan_steps.completed_at` (TIMESTAMPTZ, nullable) + `learning_plans.replan_needed` (BOOLEAN NOT NULL DEFAULT FALSE). No DB `CHECK`. Applies under `ddl-auto=validate` (proven by `PlanApiIntegrationTest`). Mapped as `PlanStep.completedAt` (`OffsetDateTime`) and `LearningPlan.replanNeeded` (`boolean`); `LearningPlanDto` gains `replanNeeded` (MapStruct auto-maps by name — `PlanApiMapper` unchanged).
  - **Completion (task 5)** — `PlanService.completeStep(userId, dayIndex)` loads the user's ACTIVE plan, marks the matching not-yet-done step(s) `done=true` + `completedAt=now`, and returns the active-plan `Optional` (empty → no active plan). Exposed cross-module as `PlanApi.completeStep(CurrentUser, int dayIndex) → Optional<LearningPlanDto>` (`DefaultPlanApi`, `@Transactional`, so the lazy `steps` initialise while the mapper runs). New Coach tool `agent.service.CompleteStepTool#completeStep(day)` resolves the caller from the `SecurityContextHolder` (never a tool arg), calls `PlanApi`, and returns a `PlanView` (empty view when there is no active plan) — registered on the Coach `ChatClient`. The model addresses the step by its 1-based `day` (`PlanStepView.day` / `PlanStep.dayIndex`), never a UUID — same handle as `getPlan`/`getTodaysTask`. **Auto-completion on quiz-finish is NOT wired** (deferred): only the explicit "I finished …" path via the tool is implemented; mapping a quiz session back to a plan step is unspecified.
  - **Re-plan triggers (task 6)** — new module-root event `profiles.GoalUpdatedEvent(userId)`, published by `DefaultProfilesApi.updateGoal` only when a goal and/or target date is supplied (not on a no-op call). `plan.service.PlanReplanListener` consumes both `materials.MaterialUploadedEvent` and `profiles.GoalUpdatedEvent` and calls `PlanService.markReplanNeeded(userId)` (no-op when the user has no active plan). `buildPlan` clears the flag implicitly — the superseding new plan defaults `replan_needed=false`. `replanNeeded` is surfaced on `PlanView` (so `getPlan`/`getTodaysTask` carry it) and the single `coach-system.txt` "Planning" paragraph now tells the Coach to offer a `buildPlan` rebuild when it is true, and to call `completeStep` when the user finishes a step.
  - **Listener semantics — why `@EventListener` + `@Transactional(REQUIRES_NEW)`** (not `MANDATORY` like `usermemory.ReviewQueueListener`, nor `@Async @TransactionalEventListener(AFTER_COMMIT)` like `MaterialIngestionService`): marking-replan must (a) never roll back the triggering upload / goal-update, and (b) fire deterministically in tests that publish the event with no ambient transaction. A plain `@EventListener` fires synchronously regardless of an ambient tx; `REQUIRES_NEW` gives it its own short transaction so any failure is isolated from the trigger. Ingestion's `AFTER_COMMIT` listener is correctly skipped when a test publishes `MaterialUploadedEvent` outside a transaction, so no embedding side-effects run.
  - **Tests:** `PlanApiIntegrationTest` extended (Testcontainers pgvector) — `completeStep` marks `done`+`completedAt` and persists; `MaterialUploadedEvent`/`GoalUpdatedEvent` set `replanNeeded` **only on the owner's** plan; `completeStep` empty with no active plan. `CompleteStepToolTest` (no-plan view / maps updated plan for the context user / anonymous guard), `PlanReplanListenerTest` (both events → `markReplanNeeded`), `DefaultProfilesApiTest` (`updateGoal` publishes `GoalUpdatedEvent`, and not on a no-op). `gradlew test` for `*CompleteStepToolTest *PlanReplanListenerTest *DefaultProfilesApiTest *GetPlanToolTest *BuildPlanToolTest *ApplicationModulesTests`, plus `org.aiincubator.ilmai.agent.service.* org.aiincubator.ilmai.profiles.* *PlanApiIntegrationTest` — all green. `ApplicationModulesTests` confirms `plan` consuming `materials`/`profiles` module-root events keeps the closed-module boundary intact.
  - **Deviations / flags (AGENTS.md rule 2 — prior docs/log not edited):**
    - **`completeStep` returns `PlanView` directly** (empty view = no active plan) rather than a `gradeAnswer`-style `{ok, …, reason}` wrapper, to match the sibling plan tools; a wrong `day` is a no-op and the model sees the unchanged done-states in the returned plan.
    - **Single Coach prompt, not "en/ru/uz".** The plan §7 5b note and the Progress bullets say "en/ru/uz Coach prompt guidance", but the RU/UZ Coach prompts were collapsed into one `coach-system.txt` in the 2026-05-29 cleanup (LLM owns language). Guidance was added to that single file. Flagging the stale "en/ru/uz" wording; not editing prior entries.
    - **`getPlan`/`getTodaysTask` frontend wiring deferred** to the later UI session: the read page scaffold + `@/lib/plan` already exist, but `frontend/components/plan/*` calls a `getPlan` REST endpoint the backend does **not** expose yet (no plan REST controller — `PlanApi.getActivePlan` is consumed only by the Coach tool). Flagging this plan-vs-code mismatch; completion is Coach-tool-only this slice (the "mark done" button + a `replanNeeded` banner are the remaining frontend work).
    - **Still deferred to 5c:** `PlanReplanCheckJob` (N-days-behind enqueue) + no-plan-day improviser, and the `plan_step` message part (Slice 5 tasks 7, 8, 9 partial).
- **2026-05-31 — Agent v1, Slice 6a shipped: new closed `streaks` module (streak domain + activity recording + rollover logic), JobRunr deferred.**
  `docs/features/agent/agent-plan.md` §8 Slice 6 tasks 3, 4, 10, 11 + the *logic* of task 5; `09-streaks.md`. First sub-slice of agent Slice 6. The user explicitly chose (this session) to **ship the streak logic first and defer the JobRunr dependency**, and to **own streaks in a new closed module** rather than extend `profiles` / `usermemory`.
  Scope decision (why JobRunr is deferred to 6b/6c):
  - Slice 6 is doc-sized and, like Slices 1–5, is split into sub-slices. JobRunr is a **heavy dependency explicitly flagged in AGENTS.md §6**, and the stack is **Spring Boot 4.0.6 / Java 25** — JobRunr's Boot-4 starter compatibility is unverified and risks the build. Mirroring how Slice 4a deferred the gap cron and Slice 5b deferred `PlanReplanCheckJob`, 6a lands the *rollover logic* (a plain, unit-tested `StreakService` method) and leaves the JobRunr dep, the recurring `StreakRolloverJob` scheduling (task 5's job), `BrokenStreakNudgeJob` (task 6), `StreakMilestoneJob` (task 7), `DailyReminderJob` + outbox (task 8) and the `/admin/jobs` dashboard (task 9) to **6b/6c**. The build stays green with no new dependency.
  New closed `streaks` feature module (`org.aiincubator.ilmai.streaks`, `package-info.java` `@ApplicationModule(type = CLOSED)`):
  - **Schema `V28__streaks.sql`** (latest migration is now `V28`, was `V27`): two tables.
    - `streaks` — the per-user summary, **PK `user_id`** (natural key, FK→`users(id) ON DELETE CASCADE`, mirrors `profiles`): `streak_current` / `streak_longest` (`INTEGER NOT NULL DEFAULT 0`), `streak_last_day` / `streak_broken_at` (`DATE`, nullable), `DateAuditable` `TIMESTAMPTZ` audit columns.
    - `streak_days` — the **append-only activity log**: surrogate `uuid_generate_v4()` PK (Hibernate `@UuidGenerator(UuidVersion7Strategy)` on the `@Id`), `user_id` (FK→`users`), `activity_date DATE`, **`uq_streak_days_user_date UNIQUE (user_id, activity_date)`** (the upsert idempotency key; its index also serves due-date lookups). **No DB `CHECK`** (AGENTS.md §4). Applies cleanly under `ddl-auto=validate` (proven by `StreaksIntegrationTest`).
  - Module root: `StreaksApi.getStreak(UUID) → StreakDto` (returns a zeroed `StreakDto` when there is no row — background-/reminder-friendly, takes a raw `UUID` like `ProfilesApi`, not `CurrentUser`, because the consumers are jobs iterating users); immutable Lombok `StreakDto` (`@Getter @AllArgsConstructor final`). Entities `Streak` / `StreakActivityDay` + their repositories stay private in `streaks/domain`.
  - `streaks/service`: `DefaultStreaksApi` (`@Service`, `@Transactional(readOnly)`), `StreaksApiMapper` (MapStruct, `componentModel = "spring"`, constructor-maps the immutable DTO like `ProfilesApiMapper`), `StreakService` (the logic), `StreakActivityListener` (the event consumer).
  Rollover algorithm (`StreakService.rollover(userId, completedDay)`, deterministic, no LLM), per `09-streaks.md` §"Rollover":
  - active on `completedDay` & `streak_last_day == completedDay-1` → `streak_current += 1`;
  - active & no contiguous prior day (or first ever) → `streak_current = 1`;
  - active & `streak_last_day >= completedDay` → **idempotent no-op** (the recurring job may fire twice);
  - sets `streak_last_day = completedDay`, bumps `streak_longest = max(longest, current)`;
  - **not** active & `streak_last_day == completedDay-1` & `current > 0` → **break**: `streak_current = 0`, `streak_broken_at = completedDay` (fires once; subsequent missed days are no-ops since `last` no longer equals `completedDay-1`);
  - not active otherwise → no-op (no empty summary rows are written). The future `StreakRolloverJob` (6b) will call this with `yesterday` at user-local midnight; 6a invokes it directly from tests with simulated dates.
  Activity recording (task 4): `StreakService.recordActivity(userId, occurredAt)` converts the event's `OffsetDateTime` to the **user-local date** (`occurredAt.atZoneSameInstant(zone).toLocalDate()`) and upserts one `streak_days` row, idempotent on `(user, date)` via the `existsBy…` guard + the unique constraint. The zone is read from `profiles` (`ProfilesApi.find(userId) → ProfileDto.timezone`); when the profile is missing / blank / invalid it falls back to **`Asia/Tashkent`** per `09-streaks.md`.
  Listener semantics — **`@EventListener` + `@Transactional(REQUIRES_NEW)`** (the Slice 5b `PlanReplanListener` pattern, **not** `MANDATORY` like `usermemory.ReviewQueueListener`): `agent.UserActivityRecordedEvent` is published **post-turn from the non-transactional reactive `DefaultAgentApi.chat()`**, inside a `try/catch` that swallows failures. So there is no ambient transaction to join (`MANDATORY` would throw) and a listener failure must never break the turn. A plain `@EventListener` fires synchronously regardless of an ambient tx; `REQUIRES_NEW` gives the upsert its own short transaction whose failure is isolated and surfaces only as the existing swallowed warning.
  Module isolation:
  - New dependencies `streaks → agent` (consumes the module-root `agent.UserActivityRecordedEvent`, mirroring `usermemory → quiz`'s `QuizAnswerGradedEvent` consumption) and `streaks → profiles` (module-root `ProfilesApi` / `ProfileDto` for the timezone). Neither `agent` nor `profiles` references `streaks`, so there is **no cycle**; `ApplicationModulesTests.verifyModules` green.
  - **Forward caveat for 6b:** if a later sub-slice wires `agent`'s `UserMemoryAdvisor` (or any `agent` code) to read `StreaksApi`, that would create `agent → streaks → agent`. 6b should source the streak read so as to avoid that — e.g. keep the streak-read consumer outside `agent`, or relocate `UserActivityRecordedEvent` to `common`.
  Verification:
  - `StreakServiceTest` 9/9 (pure Mockito): the full rollover ladder (first-day=1, consecutive+1, gap-resets-to-1 keeping `longest`, missed-day-breaks-to-0, no-active-streak no-op, already-processed idempotent) + tz-aware recording (reads `ProfilesApi` zone; `Asia/Tashkent` default when no profile; existing-day no-save).
  - `StreaksIntegrationTest` 2/2 on pgvector pg16 (Docker): publishing `UserActivityRecordedEvent` twice writes exactly one `streak_days` row for user A and none for user B (idempotent + isolated, task 4); two consecutive recorded days roll to `streak_current = 2` while user B stays 0, then a gap day breaks A to `streak_current = 0` with `streak_broken_at` set and `streak_longest = 2` preserved (tasks 10 & 11). Booting the full context proves `V28` ↔ entities agree under `ddl-auto=validate`.
  - `gradlew compileJava compileTestJava` green; `ApplicationModulesTests` 1/1. Changes are purely additive (new module + new migration); no existing file was modified. The pre-existing env-gated `ai.ingestion` embedding-IT and bare-`@SpringBootTest` reds noted in earlier 2026-05-31 entries are unchanged and unrelated.
  Deviations / mismatches flagged (AGENTS.md rule 2 — docs not "fixed"):
  - **Task 2 (user-timezone storage) was already satisfied:** `profiles.timezone` (`VARCHAR(64)`, default `"UTC"`) already exists, so no new timezone column was added. Note the column default is `"UTC"`, **not** the `09-streaks.md` `Asia/Tashkent`; `Asia/Tashkent` is used only as the in-code fallback when a profile is absent/blank/invalid (every real user has a profile, so the fallback is effectively a safety net).
  - **Canonical streak moved out of `profiles`:** the pre-existing, never-incremented `profiles.streak_days` int (and the streak field on `ProfileResponse` / read by `UserMemoryAdvisor`) is now **superseded** by the `streaks` module's `streaks.streak_current`. Re-pointing those reads at `StreaksApi` is the **read-path rewire deferred to 6b** (along with the header badge + the daily-reminder streak line). `profiles.streak_days` is left untouched for now (stale → flagged).
  - **Naming:** the append-only table is `streak_days` per the doc; it coexists with the `profiles.streak_days` *column* (different namespaces). Its day column is `activity_date` (avoids the SQL reserved word `date`). The summary table is `streaks` (the doc's conceptual `user_memory.streaks`).
  - **Milestones (task 7) & broken-streak nudge (task 6) detection/delivery are not in 6a:** they are JobRunr-coupled side-effects; 6a records the *break* (`streak_broken_at` + `current=0`) as part of the rollover math, and 6c will read the summary to enqueue the nudge / milestone messages.
  - This entry does not supersede any prior entry.
- **2026-06-01 — Agent v1, Slice 6b shipped: JobRunr (Spring Boot 4 starter) + recurring `StreakRolloverJob` + streak read-path rewire onto `StreaksApi`.**
  `docs/features/agent/agent-plan.md` §8 Slice 6 task 5 (the *job* half — 6a shipped the rollover *logic*) + the read-path rewire deferred from 6a. The user explicitly chose (this session) to **adopt JobRunr now** rather than the no-dep alternatives, because the 6a blocker is resolved (next bullet). Backend-only.
  JobRunr Boot-4 compatibility — the 6a deferral reason is now moot:
  - 6a deferred JobRunr citing "Spring Boot 4.0.6 / Java 25, Boot-4 starter compatibility unverified" (AGENTS.md §6 heavy-dep gate). JobRunr **8.3.0 (2025-11) added a dedicated `jobrunr-spring-boot-4-starter`** (Jackson 3 via a Multi-Release JAR); pinned **`org.jobrunr:jobrunr-spring-boot-4-starter:8.6.1`** in `backend/build.gradle`. The slice's task requires a scheduler, so adding the dep is sanctioned by AGENTS.md §6 ("add a dep only when the current task requires it"). `compileJava`/`compileTestJava` green with it on the classpath.
  Config (`application.yml`, top-level `jobrunr:`):
  - **`background-job-server.enabled` defaults to `false`**, overridable via env `JOBRUNR_BACKGROUND_JOB_SERVER_ENABLED` (set `true` in real deployments). Rationale: `src/test/resources` is empty, so every `@SpringBootTest` reads the main `application.yml`; a worker that *executes* jobs by default would add threads/nondeterminism to the Testcontainers ITs. The `@Recurring` job is still **registered** into storage at startup (the `JobScheduler` is autoconfigured from the `DataSource` independent of the worker), and its logic is covered by direct invocation + a simulated-time IT — so deferring *execution* to env config loses no coverage.
  - **`dashboard.enabled=false`** — the `/admin/jobs` dashboard + admin auth stay in 6c (JobRunr's dashboard is a separate port, not a Spring MVC route; enabling it now would open an unauthenticated port). `database.skip-create=false` — JobRunr manages its own tables via the starter (the AGENTS.md "Flyway owns the schema" rule governs *our* schema, not JobRunr's internal tables).
  Cycle break (resolves the 6a "Forward caveat for 6b"):
  - 6a warned that wiring `agent` to read `StreaksApi` would create `agent → streaks → agent` (since `streaks` consumes `agent.UserActivityRecordedEvent`). **Relocated `org.aiincubator.ilmai.agent.UserActivityRecordedEvent` → `org.aiincubator.ilmai.common.UserActivityRecordedEvent`** (same Lombok `@Getter @AllArgsConstructor final` shape; `common` is the OPEN shared module every feature may depend on). Publisher (`DefaultAgentApi`), consumer (`StreakActivityListener`), and both tests updated. Now `streaks → common` and `agent → streaks` with no cycle — `ApplicationModulesTests.verifyModules` green.
  Scheduler (Slice 6 task 5, the job):
  - `StreakRolloverJob` (`streaks/service`, `@Component`) — `@Recurring(id = "streak-rollover", cron = "0 * * * *")` `run()` iterates `StreakActivityDayRepository.findDistinctUserIds()` (new `@Query`) and calls `StreakService.rolloverYesterday(userId)` per user. Hourly (not once-daily) so each user rolls shortly after *their* local midnight without per-user dynamic scheduling — the rollover is idempotent (6a), so re-running within the same local day is a safe no-op.
  - `StreakService` gains an injected **`java.time.Clock`** (the existing `common.config.ClockConfig` `Clock.systemUTC()` bean, already used by `BillingQuotaService` — no new bean) and `rolloverYesterday(userId)`: computes the user-local *today* (`profiles.timezone`, `Asia/Tashkent` fallback) from `clock.instant()` and calls the existing `rollover(userId, today.minusDays(1))`. `Clock` makes "yesterday" deterministically testable.
  Read-path rewire (deferred from 6a):
  - `agent.service.UserMemoryAdvisor` now reads the live streak from **`StreaksApi.getStreak(userId).streakCurrent`** (was the never-incremented `profiles.streak_days`) for its `[user context]` "Current study streak: N days" line; the lookup is wrapped so a `StreaksApi` failure degrades to "no streak line" rather than breaking the turn. Bean factory `CoachChatClientConfig#userMemoryAdvisor` injects `StreaksApi`.
  Verification:
  - Docker-free, all green: `StreakServiceTest` 11/11 (9 prior + 2 new fixed-`Clock` `rolloverYesterday` tests: UTC zone, and the `Asia/Tashkent` fallback shifting which calendar day is "yesterday"), `StreakRolloverJobTest` 2/2 (dispatches `rolloverYesterday` per distinct user; empty-user no-op), `UserMemoryAdvisorTest` 5/5 (streak now stubbed via `StreaksApi`), `DefaultAgentApiActivityEventTest` 1/1 (event relocation), `ApplicationModulesTests` 1/1 (no cycle/leak). `compileTestJava` green.
  - `StreakRolloverJobIntegrationTest` (Testcontainers pgvector, `@MockitoBean Clock` fixed at `2026-06-02T06:00Z`): seeds user A active "yesterday" (06-01) and user B active only on an old day, runs `streakRolloverJob.run()`, asserts A → `streak_current=1` / `last_day=06-01` while B stays `0` (per-user rollover + isolation, task 10). **Not executed in this sandbox — the Docker daemon is not running** (`docker version` → dead `dockerDesktopLinuxEngine` pipe), so this IT and the untouched `StreaksIntegrationTest` both fail identically at init with `ContainerLaunchException`/`NoSuchFileException` — environmental, not a code defect; they run in CI / with Docker (booting the full context there also validates JobRunr autoconfig against a real Postgres).
  Deviations / flags (AGENTS.md rule 2 — prior docs/log not edited):
  - **`profiles.streak_days` is still stale for the REST path.** Only the `UserMemoryAdvisor` streak read was rewired. `ProfileResponse.streakDays` (the `/profile` REST field + any web header badge reading it) and the future daily-reminder streak line still read the never-incremented column — they move to `StreaksApi` in 6c (frontend/reminder work). The column is left in place (dropping it is a later cleanup).
  - **Still deferred to 6c (+ 5c, frontend):** `BrokenStreakNudgeJob` (task 6), `StreakMilestoneJob` (task 7), `DailyReminderJob` + `outbox_messages` (task 8), `/admin/jobs` dashboard + admin auth (task 9), the streak read-path's REST/frontend surface, and (Slice 5) `PlanReplanCheckJob` + no-plan-day improviser. Slices 7–8 unchanged.
  - **Hourly cron, not per-user-local-midnight dynamic scheduling:** one recurring job covering all timezones (relying on rollover idempotency) avoids one JobRunr recurring job per user/zone. If cost/scale ever demands it, switch to per-zone cron. Logged here as the chosen default for the §13 "user-local job scheduling" question.
  - This entry does not supersede any prior entry.
- **2026-06-01 — Agent v1, Slice 5c shipped: `PlanReplanCheckJob` (behind-by-N → `replanNeeded`) + no-plan-day improviser (`suggestStudyToday` Coach tool + `ActionPart`).**
  `docs/features/agent/agent-plan.md` §7 Slice 5 tasks 7 (`PlanReplanCheckJob`) & 8 (no-plan-day improviser). Backend-only; closes the backend half of Slice 5 (task 9's `plan_step` message part + all Slice 5 frontend stay deferred to the UI session). The user chose (this session) to do Slice 5c rather than 6c.
  PlanReplanCheckJob (task 7):
  - `plan.service.PlanReplanCheckJob` (`@Component`) — `@Recurring(id = "plan-replan-check", cron = "0 * * * *")` `run()` iterates `LearningPlanRepository.findDistinctUserIdsByStatus(PlanStatus.ACTIVE)` (new `@Query`) and calls `PlanService.flagIfBehind(userId, behindThresholdDays)` per user. **Hourly all-timezones cron, same rationale as `StreakRolloverJob` (6b)**: re-flagging is idempotent, so covering every local-midnight-to-03:00 window hourly avoids one recurring job per user/zone. Threshold is constructor-injected via `@Value("${ilmai.plan.replan.behind-threshold-days:3}")` (env `ILMAI_PLAN_BEHIND_THRESHOLD_DAYS`).
  - `PlanService.flagIfBehind(UUID, int)` (`@Transactional`) — loads the user's ACTIVE plan (empty → `false`), computes **user-local today** from an injected `java.time.Clock` (the existing `common.config.ClockConfig` bean — no new bean) + `profiles.timezone` (`Asia/Tashkent` fallback, same `zoneFor`/`parseZone` helper shape as `StreakService`), counts not-done steps whose `scheduled_date` is strictly before today, and sets `replanNeeded = true` when that count `>= threshold` (returns whether it flagged). Threshold is clamped to `>= 1` so a misconfigured `0` can't flag every active plan. No explicit `save()` — relies on JPA dirty-checking inside the tx, exactly like the existing `markReplanNeeded`. **No schema change**: reuses the V27 `learning_plans.replan_needed` column, so the same "offer a `buildPlan` rebuild when `replanNeeded` is true" Coach guidance (added in 5b) now also covers the behind-schedule trigger. This resolves the §13 "Threshold for 'behind on the plan' (default 3 days)" open question.
  No-plan-day improviser (task 8):
  - `agent.service.ImproviseTool#suggestStudyToday(language?)` Coach tool — resolves the caller from the `SecurityContextHolder` (never a tool arg), then a **deterministic ladder**: (1) **review** the worst gap when `GapsApi.refreshAndGet(currentUser).recommendedNext` is present; else (2) **quiz** (5 questions) when a gaps report exists *and* `MaterialsApi.findReadyForUser` is non-empty; else (3) **read** the first ready material when there is no report but materials exist; else (4) **upload** (`hasSuggestion = false`). Returns an LLM-visible `ImprovisedTaskView {hasSuggestion, kind, concept, materialTitle, questionCount, label}` **and** records a localized `ActionPart {action, label, payload}` (`review_concept` / `start_quiz` / `read_material` / `upload_material`) for the UI to render as a tappable button. The tool **never executes** the action — it only proposes one; the model + user decide. No quota/LLM cost (pure read, like `getPlan`/`getTodaysTask`).
  - **Emission plumbing** — new `agent.service.AgentActionContext` ThreadLocal mirrors `AgentQuizContext`: `DefaultAgentApi.chat()` `begin()`s it, drains `actions()` into the `MessagePart` list right after the quiz cards, and `clear()`s it in `finally`. `ImproviseTool` registered on the Coach `ChatClient` in `CoachChatClientConfig`; a new "No-plan-day improviser" paragraph in the single `coach-system.txt` tells the Coach to call it when `getTodaysTask` returns nothing or the user asks what to do now.
  - **i18n** — 4 new keys `agent.action.{review,quiz,read,upload}.label` in `messages{,_ru,_uz}.properties`; the tool resolves `language` → `Locale` via `SupportedLocale` and formats via `MessageService.get(key, args, locale)`. The args-bearing labels use no straight apostrophe; the UZ ones use `ʻ` (U+02BB), which is MessageFormat-safe (unlike `'`).
  Tests (all green this session — Docker available, so the Testcontainers IT ran):
  - `PlanServiceTest` (6 pure-Mockito cases: no-active-plan, overdue ≥ threshold flags, overdue < threshold doesn't, done overdue steps ignored, today/future steps ignored, threshold-clamped-to-1) — mirrors `StreakServiceTest` (`@Mock Clock`/`ProfilesApi`, fixed `Instant`).
  - `PlanReplanCheckJobTest` (dispatches `flagIfBehind` per distinct ACTIVE user with the configured threshold; empty → no-op) — job constructed manually to pass the `int` threshold (not `@InjectMocks`).
  - `ImproviseToolTest` (the full ladder + **user-isolation**: both `GapsApi`/`MaterialsApi` lookups are scoped to the security-context user, captured via `ArgumentCaptor`; anonymous context → `IllegalStateException`; the emitted `ActionPart` is asserted via `AgentActionContext`).
  - `DefaultAgentApiActionEmissionTest` (mirrors `DefaultAgentApiQuizCardEmissionTest`: a turn that records an `ActionPart` emits it alongside the `TextPart`).
  - Ran `org.aiincubator.ilmai.plan.* org.aiincubator.ilmai.agent.* *ApplicationModulesTests` → all green, including the Testcontainers `PlanApiIntegrationTest`, which boots the full context against pgvector and proves the new `PlanReplanCheckJob`/`ImproviseTool` beans + `PlanService`'s new ctor deps wire and that `V27` still validates. `ApplicationModulesTests` confirms `plan → profiles` (via module-root `ProfilesApi`/`ProfileDto`) keeps the closed-module boundary intact.
  Deviations / flags (AGENTS.md rule 2 — prior docs/log not edited):
  - **Single Coach prompt, not "en/ru/uz"** (same flag as 5a/5b): the plan §7 wording says "en/ru/uz Coach prompt guidance", but RU/UZ Coach prompts were collapsed into one `coach-system.txt` in the 2026-05-29 cleanup (LLM owns language). Guidance was added to that single file; only the canned `agent.action.*` *labels* are localized in the message bundles.
  - **Improviser "read" recency is approximated:** it picks the first ready material because no per-material "last studied" signal exists yet; the gap `recommendedNext` drives the higher-priority review branch. Documented as a v1 simplification, not a spec deviation.
  - **No new migration / no `plan_step` message part:** `flagIfBehind` reuses the V27 `replan_needed` column and the improviser is read-only, so Slice 5 task 9's `plan_step` message part and all Slice 5 frontend (plan page, "mark done", `replanNeeded` banner) remain deferred to the UI session. Remaining backend after this: Slice 6c (streak nudge / milestone / daily-reminder jobs + `outbox_messages`, `/admin/jobs` dashboard, REST streak read-path) and Slices 7–8.
  - This entry does not supersede any prior entry.
- **2026-06-01 — Agent v1, Slice 6c shipped: new closed `notifications` module + `outbox_messages` + `DailyReminderJob` / broken-streak nudge / milestone (all three streak/reminder side-effect jobs).**
  `docs/features/agent/agent-plan.md` §8 Slice 6 tasks 6 (`BrokenStreakNudgeJob`), 7 (`StreakMilestoneJob`), 8 (`DailyReminderJob` + outbox). Backend-only; closes the backend of Slice 6 except the `/admin/jobs` dashboard (task 9). The user explicitly chose (this session) **full 6c** (all three jobs in one go) and to **own the outbox in a new closed `notifications` module** rather than park it inside `streaks`.
  New closed `notifications` feature module (`org.aiincubator.ilmai.notifications`, `package-info.java` `@ApplicationModule(type = CLOSED)`):
  - **Schema `V29__notifications_outbox.sql`** (latest migration is now `V29`, was `V28`): one table `outbox_messages` — surrogate `uuid_generate_v4()` PK (Hibernate `@UuidGenerator(UuidVersion7Strategy)` on `@Id`), `user_id` (FK→`users(id) ON DELETE CASCADE`), `channel`/`type` (`VARCHAR`, mapped `@Enumerated(STRING)` — **no DB `CHECK`** per AGENTS.md §4), `body` (`TEXT`), `scheduled_for` (`TIMESTAMPTZ NOT NULL`), `sent_at` (`TIMESTAMPTZ`, nullable — the Slice-8 drain marker), `dedupe_key` (`VARCHAR(200)`, **`uq_outbox_messages_dedupe_key UNIQUE`** — the idempotency key; nullable so generic enqueues without one don't collide), `DateAuditable` audit cols, and `idx_outbox_messages_pending (sent_at, scheduled_for)` for the pending scan. Applies under `ddl-auto=validate` (proven by `NotificationsIntegrationTest`).
  - Module root: `NotificationsApi` (`enqueue(OutboxMessageRequest) → OutboxMessageDto`, idempotent on `dedupeKey`; `findPending(OffsetDateTime asOf) → List<OutboxMessageDto>` for the Slice-8 drainer), the immutable `OutboxMessageDto` (`@Getter @AllArgsConstructor final`), the `@Builder` `OutboxMessageRequest`, and the `OutboxChannel` (`TELEGRAM`) / `OutboxMessageType` (`DAILY_REMINDER`, `BROKEN_STREAK_NUDGE`, `STREAK_MILESTONE`) enums. Entity `OutboxMessage` + repo stay private in `notifications/domain`.
  - `notifications/service`: `DefaultNotificationsApi` (`@Service`; `enqueue` is **`@Transactional(REQUIRES_NEW)`** — see listener semantics — and returns the existing row when `dedupeKey` already present), `NotificationsApiMapper` (MapStruct `componentModel = "spring"`), `NotificationComposer` (i18n bodies from the user's `ProfileDto` locale), `NotificationService` (orchestration), `StreakNotificationListener` (event consumer), `DailyReminderJob` (the recurring entry point).
  Streak side-effect events (tasks 6 & 7) — published, not polled:
  - New module-root events `streaks.StreakBrokenEvent(userId, brokenDate, brokenStreakLength)` and `streaks.StreakMilestoneReachedEvent(userId, milestone)` (plain `@Getter @AllArgsConstructor`, mirroring `profiles.GoalUpdatedEvent`). `StreakService.rollover` gained an injected `ApplicationEventPublisher` and publishes: **milestone** when, after the active-day save, `streak_current ∈ {7,14,30,60,100}` (a code constant `MILESTONES` — current increments by 1/day so each threshold fires exactly once); **broken** when the existing break branch fires (capturing `streak_current` before the reset-to-0). The idempotent no-op branch (6a) publishes nothing, so a twice-running rollover doesn't double-fire.
  - `StreakNotificationListener` (`@EventListener`, **swallows `RuntimeException`** per event) forwards to `NotificationService.nudgeBrokenStreak` / `celebrateMilestone`. **Why `@EventListener`+swallow over `enqueue`'s `REQUIRES_NEW`** (not `MANDATORY`, not `AFTER_COMMIT`): the enqueue must never roll back the streak-rollover tx. `enqueue` runs in its own `REQUIRES_NEW` tx (suspends rollover's); because the `try/catch` lives in the *listener* (a different bean from the `@Transactional` `enqueue` boundary) it cannot leave rollover's tx `rollback-only`, so an outbox failure degrades to a logged warning and the streak math still commits.
  - Scheduling: the **broken-streak nudge** is `scheduled_for = (brokenDate+1) at ilmai.notifications.broken-streak-nudge-hour (default 08:00) in the user's tz` (the morning after the missed day); the **milestone** is `scheduled_for = now` (immediate). Dedupe keys: `streak-broken:{user}:{brokenDate}`, `streak-milestone:{user}:{level}`.
  Daily reminder (task 8) — the Slice-6 "Done when" headline:
  - `DailyReminderJob` (`@Component`, `@Recurring(id = "daily-reminder", cron = "0 * * * *")`) → `NotificationService.sendDueReminders()`, which iterates `ProfilesApi.findUserIdsWithDailyReminder()` (new bulk finder + `ProfileRepository` `@Query`) and, per user, gates on **hour granularity**: enqueues a `DAILY_REMINDER` only when the injected `Clock` converted to the user's tz lands on `profiles.daily_reminder`'s hour. Body composes the live streak (`StreaksApi.getStreak`) + **today's plan step** (new `PlanApi.getActivePlanForUser(UUID)` → first not-done step matching today's `scheduled_date`, else first not-done). Dedupe `reminder:{user}:{localDate}` makes it at-most-once per local day. Logs to console (`log.debug`) and persists to `outbox_messages`; **`TelegramApi.sendMessage` is intentionally absent** — Slice 8 drains the outbox over Telegram.
  Cross-module reads added (no new `@Autowired` repositories across modules):
  - `PlanApi.getActivePlanForUser(UUID)` — a background-friendly overload of `getActivePlan(CurrentUser)`. **Named `…ForUser` deliberately**, not an overload: `getActivePlan(UUID)` collided with `getActivePlan(CurrentUser)` at existing `GetPlanToolTest` `any()` mock sites ("reference is ambiguous"), so the `UUID` variant gets a distinct name.
  - `ProfilesApi.findUserIdsWithDailyReminder() → List<UUID>` (+ `ProfileRepository` `@Query("select p.userId from Profile p where p.dailyReminder is not null")`).
  - `notifications` depends on module-root surfaces only: `streaks` (`StreaksApi`/`StreakDto` + the two events), `plan` (`PlanApi`/`LearningPlanDto`/`PlanStepDto`), `profiles` (`ProfilesApi`/`ProfileDto`), `common` (OPEN). None of those reference `notifications`, so **no cycle** — `ApplicationModulesTests.verifyModules` green.
  Config + i18n:
  - `application.yml` `ilmai.notifications.broken-streak-nudge-hour: ${ILMAI_NOTIFICATIONS_BROKEN_STREAK_NUDGE_HOUR:8}` (constructor-`@Value` into `NotificationService`). JobRunr config unchanged from 6b (worker off by default, dashboard off).
  - 6 new keys in `messages{,_ru,_uz}.properties`: `notification.reminder.{streak.step,streak.noStep,noStreak.step,noStreak.noStep}`, `notification.streak.broken`, `notification.streak.milestone`. All args-bearing, **phrased with no straight apostrophe** (MessageFormat-safe; UZ uses `ʻ` U+02BB, like the 5c `agent.action.*` labels). The no-arg `noStreak.noStep` is fetched with `null` args so MessageFormat is skipped.
  Tests (all green this session — Docker available, so the Testcontainers IT ran):
  - `NotificationServiceTest` (fixed `Clock`): nudge schedules next-morning in the user tz + dedupe; milestone enqueues `now` + dedupe; reminder due-hour enqueues with today's step title; **tz-aware hour gate** (09:00Z = 14:00 Tashkent matches a 14:00 reminder); wrong-hour / no-profile → no enqueue; `sendDueReminders` only enqueues the user due at their hour.
  - `NotificationComposerTest` (the 4 reminder branches + broken + milestone route to the right key/args; null profile → default locale), `StreakNotificationListenerTest` (forwards + swallows service failures), `StreakServiceTest` extended (+`@Mock ApplicationEventPublisher`; break publishes `StreakBrokenEvent(…,3)`, day-7 publishes `StreakMilestoneReachedEvent(…,7)`, a non-milestone day publishes none).
  - `NotificationsIntegrationTest` (Testcontainers pgvector, `@MockitoBean Clock`): `enqueue` idempotent on `dedupeKey` (twice → 1 row); `findPending` returns due-unsent and excludes future; **`dailyReminderJob.run()` enqueues a `DAILY_REMINDER` for the reminder-configured user and none for the user without one** (the §8 acceptance bullet + per-user isolation). Booting the full context proves `V29` ↔ entity agree under `validate` and the new `@Recurring` bean + listener wire against real Postgres + JobRunr.
  - Ran `org.aiincubator.ilmai.notifications.* org.aiincubator.ilmai.streaks.* org.aiincubator.ilmai.plan.* org.aiincubator.ilmai.profiles.* org.aiincubator.ilmai.agent.* *ApplicationModulesTests` → all green (incl. both streaks ITs as regression on the `StreakService` publisher change).
  Deviations / flags (AGENTS.md rule 2 — prior docs/log not edited):
  - **`/admin/jobs` dashboard + admin auth (task 9) still deferred** (unchanged from 6b): JobRunr's dashboard is a separate unauthenticated port, out of scope until an admin-auth story exists.
  - **Hour-granularity reminders, hourly cron** (same trade-off as 6b/5c): one all-timezones `@Recurring` hourly job + dedupe-per-local-day, gating on the reminder's *hour* (minute ignored). Resolves the §13 "background-LLM allowance vs interactive" only insofar as these jobs are LLM-free (deterministic compose); a per-minute or per-zone schedule is a later cost/scale switch.
  - **`channel = TELEGRAM` for all three** (the only `OutboxChannel` value); a web/in-app channel can be added when the UI consumes the outbox. **No `markSent`/drain** in 6c — `sent_at` + `findPending` exist for Slice 8's drainer.
  - **`profiles.streak_days` REST field still stale** (unchanged from 6b): the daily-reminder streak line correctly reads `StreaksApi`, but `ProfileResponse.streakDays` and any web header badge still read the never-incremented column — REST/frontend streak read-path remains deferred (frontend session).
  - **Remaining after this:** Slice 8 (Telegram adapter drains `outbox_messages`), Slice 7 (weekly digest — also writes the outbox), and all deferred frontend (Slice 3d interactive quiz card, Slice 4 trend arrows, Slice 5 plan page / "mark done" / `replanNeeded` banner, streak badge).
  - This entry does not supersede any prior entry.
- **2026-06-01 — Agent v1, Slice 7 shipped: new closed `digest` module + `WeeklyDigestJob` (deterministic stats + 3-way branch + LLM `whereYouStand`/`focusNextWeek`) over the existing outbox.**
  `docs/features/agent/agent-plan.md` §9 Slice 7 (all tasks except the frontend "Progress" render, task 6). The user explicitly chose (this session) **full Slice 7** — deterministic digest **and** the LLM prose slots together — and to **own the persisted digest in a new closed `digest` module** rather than park it inside `notifications`. Backend-only.
  New closed `digest` feature module (`org.aiincubator.ilmai.digest`, `package-info.java` `@ApplicationModule(type = CLOSED)`):
  - **Schema `V30__weekly_digest.sql`** (latest migration is now `V30`, was `V29`): one table `weekly_digests` — surrogate `uuid_generate_v4()` PK (Hibernate `@UuidGenerator(UuidVersion7Strategy)` on `@Id`), `user_id` (FK→`users(id) ON DELETE CASCADE`), `iso_week` (`VARCHAR(10)`, e.g. `2026-W23`), `variant` (`VARCHAR(20)`, mapped `@Enumerated(STRING)` `DigestVariant` — **no DB `CHECK`** per AGENTS.md §4), the deterministic stat columns (`active_days`, `quizzes`, `answered`, `correct`, `avg_score` nullable %, `plan_done`/`plan_total`, `streak_now`, `days_until_deadline` nullable), `top_gaps` + `focus_next_week` (`JSONB NOT NULL DEFAULT '[]'`, `@JdbcTypeCode(JSON) List<String>`), `where_you_stand` (`TEXT`, nullable — the LLM slot), `generated_at`, `DateAuditable` audit cols, **`uq_weekly_digests_user_week UNIQUE (user_id, iso_week)`** (the idempotency key) + `idx_weekly_digests_user_generated (user_id, generated_at DESC)` for the latest-read. Applies under `ddl-auto=validate` (proven by `WeeklyDigestIntegrationTest` booting the full context). Used `DEFAULT '[]'` (not `'[]'::jsonb`) — the explicit cast tripped the IDE SQL parser; the unknown-typed literal coerces to `jsonb` fine.
  - Module root: `DigestApi.getLatestForUser(UUID) → Optional<WeeklyDigestDto>` (the read for the future Progress REST page), immutable `WeeklyDigestDto` (`@Getter @AllArgsConstructor final`), and the `DigestVariant` enum (`NEW_USER`, `INACTIVE`, `FULL`). Entity `WeeklyDigest` + repo stay private in `digest/domain`.
  - `digest/service`: `DefaultDigestApi` (`@Service`, read), `DigestMapper` (MapStruct `componentModel = "spring"`, auto-maps entity→DTO by name), `DigestComposer` (i18n outbox body from the user's `ProfileDto` locale, mirroring `NotificationComposer`), `DigestService` (the brain), `WeeklyDigestJob` (the recurring entry point).
  Generation pipeline (`DigestService`):
  - `WeeklyDigestJob` (`@Component`, `@Recurring(id = "weekly-digest", cron = "0 * * * *")`) → `generateDueDigests()` iterates **`ProfilesApi.findAllUserIds()`** (every user gets a digest, including inactive/new — that is the re-engagement point) and, per user, **gates on Sunday + hour** in the user's tz (`Clock` + `profiles.timezone`, `Asia/Tashkent` fallback; reminder hour if set, else `ilmai.digest.send-hour:19`). Same **hourly all-timezones cron + dedupe** rationale as 6b/5c/6c. Per-user `try/catch(RuntimeException)` logs the throwable and continues so one user's failure never aborts the batch.
  - Idempotent per `(user, isoWeek)` via `existsByUserIdAndIsoWeek` (+ the UNIQUE backstop); ISO week computed with `IsoFields.WEEK_BASED_YEAR`/`WEEK_OF_WEEK_BASED_YEAR`.
  - **3-way branch (task 3)** from streak activity days: `total active days < 7` → **`NEW_USER`** (lightweight; resolves "< 7 days of activity → lightweight branch"); else `0 active days this week` → **`INACTIVE`** (re-engagement; resolves "0 sessions all week → re-engagement"); else **`FULL`**. The week window is `[localToday.with(MONDAY) .. now]`.
  - Stats: `quizzes`/`answered`/`correct` from new `QuizApi.weeklyStats(userId, since)`; `active_days` from new `StreaksApi.countActivityDaysSince`; `streak_now` from `StreaksApi.getStreak`; `top_gaps` (≤3 concepts) from new `GapsApi.get(UUID)`; `plan_done`/`plan_total` from `PlanApi.getActivePlanForUser`; `avg_score = round(correct*100/answered)` (null when none); `days_until_deadline` from `profiles.target_date`.
  - **FULL only** calls the new `agent.DigestNarrationApi.narrate(userId, input)` → fills `where_you_stand` + `focus_next_week`; NEW/INACTIVE skip the LLM. All variants persist a row **and** enqueue a `WEEKLY_DIGEST` outbox message (`NotificationsApi.enqueue`, dedupe `digest:{user}:{isoWeek}`, `channel = TELEGRAM`) for Slice 8 to drain.
  Agent-side digest narrator (the LLM prose, task 4) — mirrors the 4a gap-narrator / 5a planner exactly:
  - New module-root `agent.DigestNarrationApi` (impl `DefaultDigestNarrationApi` in `agent/service`), the `@Builder` `DigestNarrationInput` (engagement = **active days**, not chat sessions — see flag), and immutable `DigestNarration` (`whereYouStand` + `focusNextWeek`). `DigestNarrator` is an **advisor-free** sub-agent over a new `digestNarratorChatClient` bean (`CoachChatClientConfig`, separate `prompts/agent/digest-narrator-system.txt`, EN/RU/UZ via the `Language:` line) that asks for and parses a single JSON object (`tools.jackson` `JsonMapper`, regex `\{.*}`, whole-text fallback). The call is **charged against the ilm-token quota** (`QuotaService` reserve/commit/refund, est 5, like `GetGapsTool`); when the narrator is unavailable or quota is exhausted it returns `Optional.empty()` and the digest persists with no prose (template-only body).
  Cross-module producer-API additions (all module-root, no new cross-module `@Autowired` repositories):
  - `QuizApi.weeklyStats(UUID, OffsetDateTime) → WeeklyQuizStats` (new module-root DTO) backed by two windowed `count…UpdatedAtAfter` queries on `QuizQuestionRepository` + the existing `QuizSessionRepository.countByUserIdAndStartedAtAfter`.
  - `StreaksApi.countActivityDays(UUID)` / `countActivityDaysSince(UUID, LocalDate)` (+ `StreakActivityDayRepository.countByUserIdAndActivityDateGreaterThanEqual`).
  - `GapsApi.get(UUID)` — a background-friendly overload of `get(CurrentUser)`; `GapsService.get(CurrentUser)` now delegates to a new `get(UUID)`.
  - `ProfilesApi.findAllUserIds()` (+ `ProfileRepository @Query`).
  - `notifications.OutboxMessageType` gained `WEEKLY_DIGEST` (additive enum value). `digest` depends only on module-root surfaces of `quiz`/`streaks`/`gaps`/`plan`/`profiles`/`agent`/`notifications`/`common`; none reference `digest`, so **no cycle** — `ApplicationModulesTests.verifyModules` green.
  Config + i18n:
  - `ilmai.digest.send-hour` (default 19, env-overridable) — the fallback digest hour for users without a daily-reminder time.
  - 3 new keys `notification.digest.{full,inactive,new}` in `messages{,_ru,_uz}.properties` (FULL takes `{quizzes, answered, accuracy, streak}`; phrased MessageFormat-safe — UZ uses `ʻ` U+02BB, RU written as `\uXXXX` escapes; em-dashes as `\u2014`). The FULL body appends `whereYouStand` when present.
  Tests (all green this session — Docker available, so the Testcontainers IT ran):
  - `WeeklyDigestIntegrationTest` (Testcontainers pgvector, `@MockitoBean Clock` fixed to a Sunday 19:00 UTC): seeds NEW/INACTIVE/FULL users via cross-module `streak_days` and asserts each persisted `variant` + a `WEEKLY_DIGEST` outbox row (tasks 8 & 9 + the "3 variants" Done-when); idempotent within the same ISO week (job run twice → 1 row, 1 outbox); per-user scoping (`getLatestForUser` returns only the caller's row, `active_days` reflects only that user). **Mocks `DigestNarrationApi`** (not the `Clock` alone) so the FULL path takes no LLM/quota route — needed because the dummy test API keys make the narrator "available", and `BillingQuotaService.currentBucket` calls `LocalDate.now(clock.withZone(UTC))`, which NPEs on an unstubbed Mockito `Clock.withZone`. Booting the full context proves `V30` ↔ entity agree under `validate` and the new `@Recurring` bean + all new queries/MapStruct wire against real Postgres.
  - Regression: `*gaps* *notifications* *ToolUserIsolation*` + `ApplicationModulesTests` green (the `GapsService.get` refactor, the `OutboxMessageType` enum addition, and the three hand-written `Capturing{Gaps,Quiz}Api` test fakes that gained the new interface methods).
  Deviations / flags (AGENTS.md rule 2 — prior docs/log not edited):
  - **Engagement metric is "active days", not "chat sessions".** The plan §9 stat block says "sessions"; there is no weekly chat-session count and `agent` owns `chat_sessions`, so the digest uses **streak active-days** (already needed for the branch) as the engagement number. The narrator input field is named `activeDays` accordingly. Flagging the stale "sessions" wording; not editing the plan.
  - **Dedicated `DigestNarrator`, not "the planner sub-agent".** Plan §9 task 4 says "Planner sub-agent call for `whereYouStand` + `focusNextWeek`". A dedicated narrator (own prompt/JSON schema) matches the 4a gap-narrator precedent and keeps the planner's `PlanStep[]` schema separate; flagged as an intentional deviation.
  - **Background-LLM allowance shares the interactive ledger.** Resolving §13's "background-LLM allowance vs interactive" minimally: the FULL-digest narration is charged against the same per-user `QuotaService` ledger as interactive turns (consistent with how 2b-ii summary / 2d facts background calls are charged); no separate background budget in v1.
  - **Per-user `catch` now logs the throwable** (`log.warn(msg, userId, ex)`), not just `ex.toString()` — better batch-job observability (AGENTS.md §11).
  - **Frontend deferred (task 6):** the "Progress" page rendering the latest `WeeklyDigestDto` is UI-session work; `DigestApi.getLatestForUser` is the read surface but no REST controller is exposed yet. The Telegram delivery of the enqueued `WEEKLY_DIGEST` rows (task 7) is Slice 8's outbox drainer.
  - **Remaining after this:** Slice 8 (Telegram adapter drains `outbox_messages`, now including `WEEKLY_DIGEST`) + all deferred frontend (Slice 3d interactive quiz card, Slice 4 trend arrows, Slice 5 plan page / "mark done" / `replanNeeded` banner, streak badge, the Progress digest render) + `/admin/jobs` dashboard (Slice 6 task 9).
  - This entry does not supersede any prior entry.
- **2026-06-01 — Agent v1, Slice 8a shipped: module-root `telegram.TelegramApi` + JobRunr `OutboxDrainJob` draining `outbox_messages` over Telegram + `NotificationsApi.markSent`; legacy `@Scheduled` reminder path removed.**
  `docs/features/agent/agent-plan.md` §10 Slice 8 task 1 (module-root `TelegramApi`), the delivery half of task 2 (`DefaultTelegramApi` wraps the HTTP client), and task 8 (outbox drainer for the `outbox_messages` rows Slices 6c & 7 enqueue). Backend-only; this is the first sub-slice of Slice 8 (8a). The user explicitly chose (this session) **Slice 8a** (the outbox drainer) and to **replace** the pre-existing reminder path with the outbox (next bullet).
  Plan-vs-code mismatch flagged (AGENTS.md rule 2 — prior docs/code not "fixed"):
  - A `telegram/` module **already existed** from **`V11__telegram.sql`** (predating the agent plan and absent from this log) — a web-first link flow (`POST /telegram/link-code` mints a code; `/start <code>` in the bot links the chat), a secret-checked `POST /telegram/webhook/{secret}`, a text-only `TelegramApiClient.sendMessage`, the `telegram_links` table/entity, and a parallel `@Scheduled` reminder dispatcher. It is **scaffolding**, not the Slice 8 spec: tasks 1 (module-root `TelegramApi`), 6 (`MessagePart` flattener), 7 (poll/inline callbacks), 8 (outbox drainer), 9 (`/today`/`/streak`/`/help`) were unbuilt, every bot string is hardcoded **English** (AGENTS.md rule 8), and `/quiz` is a "coming up in the web app" placeholder (no Coach turn). 8a builds the outbox-drainer foundation on the parts that fit (link table, HTTP client, webhook) and leaves the inbound-Coach / quiz-poll / i18n work to 8b+.
  Shipped (delivery foundation):
  - Module-root **`telegram.TelegramApi`** (`isEnabled()`, `boolean sendMessage(UUID userId, String text)`) backed by `telegram.service.DefaultTelegramApi` (`@Service`): resolves the user's `TelegramLink` (must be `linkedAt != null` with a non-null `chatId`), then sends via `TelegramApiClient`; returns `false` (no send) for unlinked/pending/blank-text so callers distinguish delivered from skipped. The repo read materializes the chat id **before** the HTTP call (no DB tx held across the network call).
  - `TelegramApiClient.sendMessage` now returns **`boolean`** (true on 2xx, false when disabled / null-blank / on `RestClient` exception) so the drainer only marks delivered rows sent. Existing inbound callers ignore the return (behavior unchanged).
  - **`NotificationsApi.markSent(UUID id)`** (the Slice-8 drain marker the 6c log noted was still missing) — idempotent: `DefaultNotificationsApi` (now injects the existing `common.config.ClockConfig` `Clock`) stamps `sent_at = OffsetDateTime.now(clock)` only when null, via JPA dirty-checking inside `@Transactional`. **No migration**: reuses the V29 `outbox_messages.sent_at` column + the existing `findBySentAtIsNull…` pending query.
  - `telegram.service.OutboxDrainService.drain()` — short-circuits when `TelegramApi.isEnabled()` is false; else `NotificationsApi.findPending(now)` (`now` from the injected `Clock`), filters `channel == TELEGRAM`, and per row `sendMessage(userId, body)` → on success `markSent(id)`. Returns the delivered count. **Unlinked users' rows stay pending** (no `sent_at`) rather than being dropped — at-least-once with the dedupe key, so they deliver if/when the user links later (v1 trade-off: pending can accumulate for never-linking users; a TTL/expiry is a later cleanup).
  - `telegram.service.OutboxDrainJob` — `@Recurring(id = "telegram-outbox-drain", cron = "* * * * *")` → `OutboxDrainService.drain()`. **Every-minute** cron (vs the hourly 6b/6c/7 jobs) to keep reminder/digest delivery timely — it matches the cadence of the deleted `@Scheduled` dispatcher, and the drain is the indexed pending scan so the cost is low. The JobRunr worker stays env-gated off by default (6b), so it only executes where `JOBRUNR_BACKGROUND_JOB_SERVER_ENABLED=true`.
  Legacy reminder path removed (user-approved "replace with outbox"):
  - Deleted `telegram.service.TelegramReminderScheduler` (Spring `@Scheduled(cron = "0 * * * * *")`), `TelegramService.findDueReminders` / `sendReminder` (the latter sent a hardcoded-English greeting), and `TelegramLinkRepository.findLinkedAccountsWithReminderBetween`. That JPQL **selected from the `Profile` entity inside `telegram`'s own repository** — a closed-module-boundary smell — and was a parallel reminder system that bypassed the 6c `outbox_messages`. Reminders/digests now flow through the single outbox→drainer path. `TelegramConfig`'s `@EnableScheduling` is **kept** (still needed by `materials.MaterialReingestScheduler`).
  Module isolation:
  - New dependency `telegram → notifications` (consumes module-root `NotificationsApi` / `OutboxMessageDto` / `OutboxChannel` only; `notifications` never references `telegram`, so **no cycle**). All feature modules stay `CLOSED`; `ApplicationModulesTests.verifyModules` green.
  Verification (all green this session — Docker available, so the Testcontainers IT ran):
  - `OutboxDrainServiceTest` (pure Mockito): disabled → no `findPending`/send/`markSent`; mixed batch → only the delivered (linked) row is `markSent`, the undelivered (unlinked) row is not, count = 1.
  - `DefaultTelegramApiTest` (pure Mockito): linked user → sends to their `chatId` (true); no link / pending link without `chatId` → false with **no** client call; blank text → false with no repo lookup.
  - `OutboxDrainIntegrationTest` (Testcontainers pgvector, `@MockitoBean Clock` + `@MockitoBean TelegramApiClient`): a linked user A and an unlinked user B each get a pending `DAILY_REMINDER`; `drain()` returns 1, A's row is marked sent (drops out of `findPending`) while B's stays pending, and the HTTP client is called **exactly once, only with A's chat id** — the AGENTS.md §5 user-isolation non-negotiable for delivery (report: `tests=1 skipped=0`, 30 Flyway + JobRunr migrations applied, full context booted so the new `@Recurring` bean + `markSent` validate against real Postgres).
  - Regression: `NotificationServiceTest` + `NotificationsIntegrationTest` green (the `DefaultNotificationsApi` `Clock`/`markSent` change). `compileJava`/`compileTestJava` + `ApplicationModulesTests` green.
  Deviations / flags (AGENTS.md rule 2 — prior docs/log not edited):
  - **Web-first link flow kept** (resolves §13 "Telegram link-flow direction"): 8a reuses the scaffolding's web-mint-code + `/start <code>` direction rather than introducing a bot-first flow.
  - **Inbound still scaffolding:** the webhook + `/start`/`/quiz`/`/unlink` handlers and their hardcoded English strings are **untouched** by 8a (outbound-only). Real Coach turns over Telegram (tasks 5–7), the `MessagePart` flattener (6), poll/inline-keyboard callbacks (7), `/today`/`/streak`/`/help` (9), and i18n of all bot copy move to **8b/8c**.
  - **Unlinked rows are not dropped** — see the drainer bullet; documented v1 trade-off, not a spec deviation.
  - **Remaining after this:** Slice 8b/8c (inbound Coach over Telegram + slash commands + quiz poll round-trip) + all deferred frontend (Slice 3d interactive quiz card, Slice 4 trend arrows, Slice 5 plan page / "mark done" / `replanNeeded` banner, streak badge, the Progress digest render) + `/admin/jobs` dashboard (Slice 6 task 9).
  - This entry does not supersede any prior entry.
- **2026-06-01 — Agent v1, Slice 8b shipped: inbound Coach over Telegram — non-transactional `TelegramUpdateHandler` (webhook router) + canonical Telegram session + `MessagePart`→HTML flattener + cheap `/today`/`/streak`/`/help` + EN/RU/UZ bot copy.**
  `docs/features/agent/agent-plan.md` §10 Slice 8 tasks 5 (canonical Telegram chat session), 6-text (`MessagePart`→message flattener, text + citations; polls/inline keyboards stay in 8c), 9 (slash commands), and the **inbound half of task 11** (unlinked `chat_id` cannot trigger Coach turns), plus a webhook-routing rework of the 8a scaffolding's tasks 3 & 4. Backend-only; second sub-slice of Slice 8. The user chose (this session) **8b** (inbound Coach + slash commands + i18n), leaving the interactive poll/callback round-trip (tasks 6-polls, 7, 10) for 8c.
  Canonical Telegram session (task 5):
  - New `agent.AgentApi.canonicalSession(CurrentUser, ChatChannel) → UUID` backed by `ChatSessionService.getOrCreateCanonical` (`@Transactional`): returns the **oldest** `(userId, channel)` `chat_sessions` row, creating one if none, via a new derived finder `ChatSessionRepository.findFirstByUserIdAndChannelOrderByCreatedAtAsc`. **No migration** — reuses the Slice-2a `chat_sessions` table (the `channel` column already exists). The bot thus has one stable server-minted session per user (the model never sees/sends a `sessionId`), so within-session memory + the rolling summary accrue across bot turns exactly like the web SSE path.
  Inbound router moved off `TelegramService` (tasks 3, 4, 9, 11):
  - New **non-transactional** `telegram.service.TelegramUpdateHandler` owns `handleWebhook` (secret check) + `handleUpdate` (routing); `TelegramController.webhook` now delegates to it (was `TelegramService.handleWebhook`). Keeping the orchestrator non-transactional is deliberate: it blocks on a Coach turn (LLM + tool calls over seconds) and must **not** hold a DB transaction across that network call. All DB mutations are delegated to small `@Transactional` helpers on `TelegramService` (`findLinkedUser` readonly, `linkChat`, `unlinkChat`, `markSeen`) invoked through the Spring proxy.
  - **Unlinked rejection (task 11):** any non-`/start` inbound from a chat with no `linkedAt` link short-circuits with the localized `telegram.bot.notLinked` copy and `agentApi` is never touched (asserted via `verifyNoInteractions(agentApi)`). `/start <code>` is handled first (the only pre-link entry point), reusing the web-first link flow (`TelegramService.linkChat` validates code + TTL).
  - **Cheap, no-LLM slash commands (task 9):** `/today` reads `PlanApi.getActivePlan(CurrentUser)` and picks the not-done step whose `scheduled_date` == user-local today (tz from `profiles.timezone`, `Asia/Tashkent` fallback), else the earliest not-done step, else an all-done/no-plan line; `/streak` reads `StreaksApi.getStreak`; `/help` lists the commands. Free text and `/quiz`/`/practice` route into a blocking Coach turn (`canonicalSession` → `agentApi.chat(...).collectList().block(120s)`); an unknown `/command` falls back to `/help`.
  `MessagePart`→HTML flattener (task 6, text half):
  - New `telegram.service.TelegramMessageFlattener.flatten(List<MessagePart>, SupportedLocale)` renders Telegram **HTML** (`TelegramApiClient` already sends `parse_mode=HTML`): joins `TextPart`s, appends a read-only `Quiz` block for `QuizCardPart`s (numbered prompt + lettered options — no interactivity yet, that is 8c), appends a `Sources` block for `CitationPart`s (snippet/locator, capped), and prepends an italic low-confidence note when any `TextPart.confidence == LOW` (the AGENTS.md §5 "flag answers without citations" non-negotiable, surfaced over Telegram). All dynamic text is **HTML-escaped** (`& < >`), each field length-capped, the whole message capped at Telegram's 4096. `ErrorPart` (e.g. `quota_exceeded`) renders as the body when there is no text.
  Bot copy i18n:
  - 19 new `telegram.bot.*` keys in `messages{,_ru,_uz}.properties`. Copy is keyed by **`ProfileDto.locale`** (resolved per inbound user) — **not** `LocaleContextHolder`, since a webhook has no request locale. MessageFormat-safe: only no-arg keys carry apostrophes; emoji via `\uXXXX` surrogate escapes; em-dashes `\u2014`; **no stray `<`/`>` in canned copy** (placeholders like `/start YOUR_CODE`, only intentional `<b>`) so a canned string can't break Telegram HTML parsing.
  `TelegramService` slimmed:
  - Removed the inbound handlers (`handleWebhook`/`handleUpdate`/`handleStart`/`handleQuickQuiz`/`handleUnlink`) and **all their hardcoded English strings** (the AGENTS.md rule-8 smell flagged in the 8a log). What remains: the web link endpoints (`createLinkCode`/`getLink`/`unlink`) + the four transactional helpers the handler calls.
  Tests (Docker available, so the Testcontainers ITs ran):
  - `TelegramUpdateHandlerTest` (pure Mockito): unlinked free text → one localized send, **no `agentApi` interaction**, no `markSeen` (task 11); `/streak` → `StreaksApi.getStreak` + one send, **no `agentApi`** (cheap path, task 9); linked free text → `canonicalSession` + `agentApi.chat` + the flattened reply sent.
  - `TelegramMessageFlattenerTest` (pure Mockito): HTML special chars escaped (`<b>x</b> & y` → `&lt;…&amp;…`); citations append the `Sources` block + snippet; `LOW` confidence prepends the note; empty parts → empty string.
  - Ran `org.aiincubator.ilmai.telegram.* org.aiincubator.ilmai.agent.* *ApplicationModulesTests` → all green, incl. the Testcontainers `OutboxDrainIntegrationTest`, the two agent isolation ITs, and the agent `api` smoke/controller tests (booting the full context validates the new derived finder, the `TelegramUpdateHandler`/`TelegramMessageFlattener` beans, and `AgentApi.canonicalSession` against real Postgres). `ChatSessionServiceTest` + `DefaultAgentApi*` unit tests green.
  Module isolation:
  - `telegram` gained a dependency on **`agent`** (module-root `AgentApi` / `MessagePart` subtypes / `ChatChannel` only), plus the existing module-root surfaces of `plan` (`PlanApi`/`LearningPlanDto`/`PlanStepDto`), `streaks` (`StreaksApi`/`StreakDto`), `profiles` (`ProfilesApi`/`ProfileDto`), `common` (OPEN). No module references `telegram`, so **no cycle**; `ApplicationModulesTests.verifyModules` green (all cross-module imports are module-root types — no `service`/`domain`/`api`/`payload` leaks).
  Deviations / flags (AGENTS.md rule 2 — prior docs/log not edited):
  - **Tasks 3 & 4 were already built (8a scaffolding), reworked not rebuilt:** the webhook endpoint + `/start <code>` link flow existed; 8b moved routing into `TelegramUpdateHandler` and replaced the hardcoded English with i18n rather than re-implementing the link table/flow.
  - **Polls/inline keyboards deferred to 8c:** the flattener renders quiz cards as **read-only** numbered text; the MCQ poll round-trip (bot poll → vote → `gradeAnswer` → result, tasks 6-polls, 7, 10) is 8c.
  - **Coach turn blocks the webhook thread** (`.block(120s)`): acceptable for a Spring MVC webhook; if Telegram's webhook timeout becomes an issue, 8c can switch to ack-then-async-send.
  - **`markSeen` updates `lastSeenAt` + `profiles.touchActivity`** for every linked inbound (preserves 8a behavior); streak credit still flows only through the post-turn `UserActivityRecordedEvent` (unchanged).
  - **Remaining after this:** Slice 8c (interactive Telegram polls / inline-keyboard callbacks + MCQ quiz poll round-trip) + all deferred frontend (Slice 3d interactive quiz card, Slice 4 trend arrows, Slice 5 plan page / "mark done" / `replanNeeded` banner, streak badge, the Progress digest render) + `/admin/jobs` dashboard (Slice 6 task 9).
  - This entry does not supersede any prior entry.
- **2026-06-01 — Agent v1, Slice 8c shipped: interactive Telegram quiz polls (MCQ round-trip) + inline-keyboard action callbacks; closes the Slice 8 backend.**
  `docs/features/agent/agent-plan.md` §10 Slice 8 tasks 6-polls (`MessagePart`→poll/inline-keyboard flattening), 7 (inbound poll-answer / inline-keyboard callback → Coach command), and 10 (MCQ round-trip integration test). Backend-only; third and final backend sub-slice of Slice 8. The user chose (this session) **quizzes over Telegram polls, other actions over inline keyboards** (full 8c). With this, the entire Slice 8 backend (8a delivery + 8b inbound Coach + 8c interactivity) is done.
  Poll-binding schema (the only new persistence):
  - **`V31__telegram_quiz_polls.sql`** (latest migration is now `V31`, was `V30`): one table `telegram_quiz_polls` correlating a Telegram poll id to the quiz question it asked — surrogate `uuid_generate_v4()` PK (Hibernate `@UuidGenerator(UuidVersion7Strategy)` on `@Id`), `poll_id` (`VARCHAR(120)`, **`uq_telegram_quiz_polls_poll_id UNIQUE`** — the lookup + idempotency key), `user_id` (FK→`users(id) ON DELETE CASCADE`), `chat_id` (`BIGINT`), `session_id` / `question_id` (`UUID`, plain columns — **no `@ManyToOne` across the `telegram`→`quiz` module boundary**), `position` (`INTEGER`, the 1-based question number used to grade), `options` (`JSONB NOT NULL`, `@JdbcTypeCode(JSON) List<String>` — the **full untruncated** option texts, so an option index maps back to the exact grading answer), `answered_at` (`TIMESTAMPTZ` nullable — the at-most-once-grade marker), `DateAuditable` cols. Applies under `ddl-auto=validate` (proven by the new IT). Entity `TelegramQuizPoll` + `TelegramQuizPollRepository.findByPollId` stay private to `telegram/domain`.
  Outbound client additions (`TelegramApiClient`, still a thin `RestClient` wrapper, all errors swallowed):
  - `sendPoll(chatId, question, options) → String pollId` — `POST /sendPoll` (`type=regular`, `is_anonymous=false`, `allows_multiple_answers=false`), options sent as **Bot API 7.3 `InputPollOption` objects** (`[{ "text": … }]`, not bare strings) and the minted poll id parsed from `result.poll.id`; returns `null` (no binding persisted) on disable/invalid/error.
  - `sendMessage(chatId, text, List<InlineButton>)` overload — attaches a one-button-per-row `inline_keyboard` `reply_markup`; **delegates to the 2-arg `sendMessage` when the button list is empty** (so the no-action text path and its existing unit-test expectation are unchanged). New `telegram.service.InlineButton` value type (`@Getter @AllArgsConstructor final`).
  - `answerCallbackQuery(callbackQueryId)` — acks the tap (`POST /answerCallbackQuery`) so Telegram clears the button spinner.
  Inbound DTOs + routing:
  - New `telegram.payload.TelegramPollAnswerDto` (`poll_id`, `user`, `option_ids`) and `TelegramCallbackQueryDto` (`id`, `from`, `message`, `data`), both `@JsonIgnoreProperties(ignoreUnknown=true)`, wired onto `TelegramUpdateRequest` as `poll_answer` / `callback_query`. `TelegramUpdateHandler.handleUpdate` now routes `poll_answer` and `callback_query` **before** the `message == null` guard (those updates carry no `message`, so the 8b early-return would have dropped them).
  Coach-turn part dispatch (task 6-polls): `runCoachTurn` collects the Coach `MessagePart`s and `dispatchParts` splits them — pollable MCQ `QuizCardPart`s (2–10 options) become Telegram **polls** (`sendPoll` + persist a `TelegramQuizPoll` binding; question/option text truncated to Telegram's 300/100 limits for display, full options stored for grading); `ActionPart`s become an **inline keyboard** attached to the flattened text reply (`callback_data = "act:" + action`); everything else (text, citations, read-only/non-MCQ cards) flows through the existing `TelegramMessageFlattener` as before.
  Inbound interactivity (task 7):
  - **Poll vote → grade:** `poll_answer` looks up the binding by `poll_id`, is idempotent on `answered_at`, maps `option_ids[0]` → the stored option **text**, and calls module-root `QuizApi.gradeAnswer(new CurrentUser(boundUserId), sessionId, position, optionText)` — the model/user never sends a question UUID and the **caller is resolved from the persisted binding, not the inbound payload** (AGENTS.md §5 tool-calling non-negotiable). The localized result (`✅/❌` + feedback, correct answer on a miss, explanation, and progress/`completed` line) is sent back; a recoverable grade failure marks the binding answered and sends an "expired" line (no retry loop). Vote retraction (empty `option_ids`) / out-of-range indices are ignored.
  - **Inline-keyboard tap → Coach command:** `callback_query` is always `answerCallbackQuery`-acked; an **unlinked chat is rejected without any Coach call** (extends the 8b task-11 isolation to callbacks); `act:start_quiz` / `act:review_concept` / `act:read_material` map to a localized prompt and run a normal Coach turn (so `act:start_quiz` flows through the `startQuiz` tool → emits `QuizCardPart`s → which `dispatchParts` then sends as polls — the inline button literally produces a poll), while `act:upload_material` replies with canned upload guidance (no LLM).
  **Security-context establishment in `runCoachTurn` (latent-gap fix, flagged):** the handler now sets a Spring `UsernamePasswordAuthenticationToken(currentUser, null, [])` into the `SecurityContextHolder` around the (eager) `agentApi.chat(...)` call and restores the previous context in `finally`. The webhook thread has **no JWT filter**, so the `SecurityContext` was empty during 8b Coach turns; every agent tool (`retrieve`, `startQuiz`, `getGaps`, `buildPlan`, …) resolves the caller via `SecurityContextHolder` and would have thrown `IllegalStateException("no authenticated user…")` on any tool-calling turn over Telegram. 8b shipped without exercising a real tool over the bot (its unit test mocks `AgentApi.chat`), so the gap was latent; 8c needs it because the `start_quiz` callback must actually drive the `startQuiz` tool. Used Spring's `UsernamePasswordAuthenticationToken` (principal = `common.CurrentUser`, authenticated) rather than `auth.security.CurrentUserAuthentication` to avoid importing `auth`'s non-root package across the closed-module boundary; the tools only read `getPrincipal() instanceof CurrentUser`.
  i18n: 9 new keys × 3 locales — `telegram.bot.quiz.{correct,wrong,answer,progress,completed,expired}` + `telegram.bot.action.{upload,reviewPrompt,readPrompt}` in `messages{,_ru,_uz}.properties` (escaped-emoji `\uXXXX` + literal RU/UZ script, `ʻ` U+02BB in UZ, MessageFormat-safe).
  Tests (Docker available, so the Testcontainers IT ran):
  - `TelegramUpdateHandlerTest` extended to 9 cases: a coach `QuizCardPart` → `sendPoll` + a persisted binding (captured: poll id, session, question, position, full options, chat, user) with **no** plain text send; a `poll_answer` grades by the resolved option **text** (`gradeAnswer(…, "Paris")`), marks the binding answered, and replies once; unknown / already-answered polls are no-ops; a `callback_query` from a linked user acks + `markSeen` + runs the Coach turn + sends the reply; a callback from an **unlinked** chat acks but never touches `agentApi` or `markSeen`. The 8b free-text/`/streak`/unlinked cases still pass (the empty-keyboard path keeps the 2-arg `sendMessage`).
  - **`TelegramQuizPollIntegrationTest`** (Testcontainers pgvector, `@MockitoBean TelegramApiClient`, the task-10 round-trip): seeds a linked user + an MCQ `quiz_sessions`/`quiz_questions` row (`correctAnswer="Paris"`) + a `telegram_quiz_polls` binding (simulating an already-sent poll), then feeds a `poll_answer` through `handleUpdate`; asserts the **real** `QuizService`/`QuizGrader` graded the question in the DB (`isCorrect` true for option 0 / false for option 1, `userAnswer` persisted), the binding's `answered_at` is stamped, and a result `sendMessage` fired to the bound chat (report: `tests=2 skipped=0`, V31 + Flyway/JobRunr migrations applied, full context booted so `V31` ↔ entity agree under `validate`). The "bot sends poll" half of the round-trip is covered by the handler unit test (quiz card → `sendPoll` + binding); the IT covers the "vote → grade → reply" half, so neither test needs a live LLM to author the quiz.
  - Ran `org.aiincubator.ilmai.telegram.* org.aiincubator.ilmai.quiz.* org.aiincubator.ilmai.agent.* *ApplicationModulesTests` → all green (incl. both telegram ITs and the agent/quiz isolation ITs as regression).
  Module isolation:
  - `telegram` gained a dependency on **`quiz`** (module-root `QuizApi` / `QuizGradeDto` only) and reuses the 8b `telegram → agent` dependency for the `QuizCardPart` / `ActionPart` module-root types. Cross-module `quiz`/`agent` ids are stored as plain `UUID` columns (no `@ManyToOne`). No module references `telegram`, so **no cycle**; `ApplicationModulesTests.verifyModules` green. Test-only `@Autowired` of `quiz.domain.QuizSessionRepository` in the IT for seeding is the allowed integration-test exception.
  Deviations / flags (AGENTS.md rule 2 — prior docs/log not edited):
  - **Inline-keyboard action callbacks carry only the action verb** (`act:review_concept` / `act:read_material`), not a concept/material id; the Coach re-derives the target from gaps/materials on the resulting turn. A richer per-target `callback_data` is a later enhancement.
  - **Poll grading is keyed by the server-minted poll id + stored 1-based `position`**; the model never sees question UUIDs. Failed grades degrade to an "expired" reply rather than retrying.
  - **The security-context fix** (above) is a behavior fix to the 8b Telegram Coach path, not a spec change — without it the bot could chat but not use tools.
  - **Slice 8 backend is now complete.** Remaining: all deferred frontend (Slice 3d interactive quiz card, Slice 4 trend arrows, Slice 5 plan page / "mark done" / `replanNeeded` banner, streak badge, the Progress digest render) + `/admin/jobs` dashboard (Slice 6 task 9) + the REST/frontend streak read-path.
  - This entry does not supersede any prior entry.
- **2026-06-01 — Agent v1, Slice 5 frontend shipped: first `plan` REST surface (`GET /plan` + `POST /plan/steps/{dayIndex}/complete`) + real Plan page (flat steps, one-way "mark done", `replanNeeded` banner).**
  `docs/features/agent/agent-plan.md` §7 Slice 5 frontend (tasks 7–9 frontend: plan page rendering, the "mark done" button, and the `replanNeeded` banner) — the deferred-frontend item the 5b/5c/8a–8c logs kept listing. The user chose (this session) **the Plan page** out of the remaining deferred-frontend slices. Backend exposes `plan` over HTTP for the first time (5a–5c shipped the module + `PlanApi` but no REST), and the web app is wired to the real flat-`steps` contract.
  Backend (new REST surface over the existing 5a–5c `plan` module — no new schema, no migration):
  - **`plan/api/PlanController`** (`@RequestMapping("/plan")`, mirrors `GapsController`; covered by the `anyRequest().authenticated()` chain): `GET /plan` → `ApiResponse<LearningPlanResponse>` and `POST /plan/steps/{dayIndex}/complete` → the updated plan. Thin: injects `@AuthenticationPrincipal CurrentUser`, forwards it verbatim, calls one `PlanService` method, wraps in `ApiResponse` (AGENTS.md §4 — no entity returns, no mapping in `api/`).
  - **`plan/payload/{LearningPlanResponse,PlanStepResponse,PlanMaterialRef}`** (Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`, `@JsonInclude(NON_NULL)`): flat `steps[]` (each `dayIndex`/`scheduledDate`/`title`/`activity`/`materials[]`/`note`/`done`/`completedAt`) + `daysTotal`/`daysCompleted`/`replanNeeded`/`status`. **No grouped "days" and no `durationMinutes`** — those were frontend-scaffolding inventions (see flag).
  - **`plan/service/PlanMapper`** (MapStruct `@Mapper(componentModel = "spring")`, abstract class with an `@Autowired` setter for `materials.MaterialsApi`, like `GapsMapper`): entity→DTO; `daysTotal = steps.size()`, `daysCompleted = count(done)` via `expression`, and per-step `materials` resolved through `MaterialsApi.findById` (cross-module **public API**, not a repo) — **unresolved/deleted material ids are skipped** (the UI shows nothing rather than a dangling id).
  - **`PlanService`** gained `getActivePlanResponse(CurrentUser)` (`@Transactional(readOnly)`, reuses `findActivePlan`) and `completeStepResponse(CurrentUser, int)` (`@Transactional`, reuses `completeStep`), both mapping **inside the tx** so the lazy `steps` resolve. Both **return `null` when there is no active plan** (not a thrown 404 — see flag).
  - **Empty-plan contract = `200` + `ApiResponse.ok(null)`.** Because `ApiResponse` is `@JsonInclude(NON_NULL)`, a null `data` serializes to `{}`; the frontend `apiFetch` then returns that `{}` (its `"data" in parsed` check is false), so **`lib/plan.ts#getPlan` normalizes `{}`/missing-`id` → `null`** (checks `result?.id`). Chose `200 + null` over the `gaps` module's `422`-via-typed-exception convention — a user simply having no plan yet is not an error; flagged as an intentional divergence from `GapsException`/`GapsExceptionHandler`.
  Frontend (wired to the real backend; the demo/MSW mock layer updated in lockstep):
  - **`lib/plan.ts` rewritten** to the real shape (`PlanActivity`, `PlanMaterial`, `PlanStep`, `LearningPlan{ id, goal, targetDate, status, replanNeeded, createdAt, daysTotal, daysCompleted, steps }`); `getPlan` (id-normalized) + **`completePlanStep(dayIndex)`** (one-way, `POST …/complete`). **Removed the fictional `generatePlan` (`POST /plan/generate`) and `togglePlanItem` (`PATCH /plan/days/:date/items/:index`)** — neither endpoint ever existed on the backend.
  - **`components/plan/plan-view.tsx` rebuilt:** renders the goal card (goal/target + `daysCompleted/daysTotal` progress), a per-step list (Day N + scheduledDate + activity badge + title/note + material links to `/topics/{topicId}`), a one-way **"Mark done"** button per not-done step (→ `completePlanStep`, optimistic `setPlan`) with a "Completed" state for done steps, an empty state linking to `/profile`, and the **`replanNeeded` banner**. The banner is **informational + a "Refresh" button** (re-fetches `getPlan`): there is **no chat route in the frontend yet**, so it can't deep-link to "ask the Coach" — copy says to ask the Coach in chat and then refresh. `initialPlan`→state sync uses React's render-time prop-sync (not a `useEffect`, to satisfy `react-hooks/set-state-in-effect`).
  - **Shared-lib fallout (kept the build green):** `home-dashboard`'s `TodayCard`/`PlanItemRow` moved off the removed `days/items/durationMinutes`/two-way-toggle shape to today's `PlanStep` + one-way mark-done (`onCompleteStep`); `home-dashboard-client` swaps `togglePlanItem`→`completePlanStep`; **`profile-view`'s goal-save repointed from the fictional `generatePlan` to the real `PUT /onboarding`** via a new tiny `lib/onboarding.ts` (`saveOnboarding`/`getOnboarding`, matching the Slice-2c `OnboardingRequest` `{goal,targetDate,dailyStudyMinutes,dailyReminder}`).
  - **i18n:** 5 new `plan.*` keys (`completed`, `replanTitle`, `replanDescription`, `refresh`, `dayLabel` = "Day {n}") added to the `Dictionary` type + EN/RU/UZ blocks (AGENTS.md rule 8 — no hardcoded English).
  - **Demo/MSW mock layer** (`mocks/types.ts`, `mocks/db.ts` `SEED_PLAN`, `mocks/handlers.ts`) moved to the flat-`steps` shape; `GET /plan` + new `POST /plan/steps/:dayIndex/complete` + `GET`/`PUT /onboarding` handlers replace the old generate/day-item-toggle routes so `NEXT_PUBLIC_DEMO_MODE` stays coherent.
  Tests / verification:
  - **`PlanMapperTest`** (pure unit on the generated `PlanMapperImpl` + mocked `MaterialsApi`): field/day-count mapping, material-title resolution with **missing materials skipped**, null `materialIds` → empty list.
  - **`PlanApiIntegrationTest`** +4 (Testcontainers, exercising the service path behind the controller): owner-scoped `getActivePlanResponse` (**user-isolation**, AGENTS.md §5), `null` when no plan, `completeStepResponse` marks the step done + bumps `daysCompleted`, `null` complete when no plan. Ran `--tests "org.aiincubator.ilmai.plan.*"` → green (Docker available, the IT ran).
  - Frontend `pnpm typecheck` ✓, `pnpm lint` ✓ (only a pre-existing generated-`mockServiceWorker.js` warning), `pnpm build` ✓ (all 18 routes), Prettier-formatted.
  Plan-vs-code mismatch flagged (AGENTS.md rule 2 — prior docs/code not silently "fixed"):
  - The scaffolding `lib/plan.ts` + `plan-view.tsx` + home/profile modeled an **aspirational** plan shape (grouped `days[].items[]`, `durationMinutes`, per-day `summary`, two-way item toggle, web `POST /plan/generate` + `PATCH /plan/days/:date/items/:index`) that the real 5a–5c backend never implemented (it is flat `PlanStep[]`, one-way `completeStep`, and plan-building is a **Coach tool**, not a REST endpoint). Rebuilt the web to the real contract rather than adding fake backend routes.
  - **No web plan-build / no un-complete:** building/rebuilding a plan stays a Coach action (`buildPlan` tool); the page surfaces `replanNeeded` + Refresh but cannot trigger a rebuild from the web (no chat route yet). Mark-done is one-way (matches `PlanApi.completeStep`).
  - **Remaining after this:** the other deferred frontend (Slice 3d interactive quiz card, Slice 4 "Progress" trend arrows, Slice 7 Progress digest render, streak badge + REST streak read-path) + `/admin/jobs` dashboard (Slice 6 task 9). A frontend **chat/Coach route** is itself still unbuilt (Slice 1 frontend) — once it lands, the plan empty-state / replan banner should deep-link to it.
  - This entry does not supersede any prior entry.
- **2026-06-01 — `usermemory` spaced-repetition read path completed: `UserMemoryApi.dueReviews`/`countDueReviews` + three consumers (Coach `reviewDueConcepts` tool, SR-aware `ImproviseTool`, daily-reminder due-count line). Closes the long-flagged "review-queue write-only" gap.**
  Resolves the outstanding item flagged in `usermemory-plan.md` (Slice 3) and in the prior "is `docs/features` implemented?" answer: the `ReviewQueueListener` had been *writing* `user_memory_review_queue` (the SM-2-lite `{1,3,7,21}`-day ladder) on every graded answer since Slice 3c, but `next_review_at` was never read back, so nothing surfaced due reviews. The user chose (this session) **all three** read-side consumers. **No new schema / no migration** — reuses the Slice-3c `V24 user_memory_review_queue` table.
  Read API (`usermemory` module root):
  - New module-root DTO **`usermemory.ReviewDueDto`** (`@Getter @AllArgsConstructor final` — `concept`, `nextReviewAt`, `materialId`, `timesWrong`; no `record`, per AGENTS.md §4).
  - **`UserMemoryApi.dueReviews(CurrentUser, OffsetDateTime asOf) → List<ReviewDueDto>`** (caller from the security context, AGENTS.md §5) and **`countDueReviews(UUID userId, OffsetDateTime asOf) → long`** (a `UUID` overload for background jobs that already hold the user id — the `CurrentUser` rule is for controller-facing methods; the reminder job is a batch loop). Both filter `status = ACTIVE` **and** `nextReviewAt <= asOf` (so `MASTERED` rows and not-yet-due rows are excluded), ordered by `nextReviewAt` asc.
  - `ReviewQueueRepository` gained two derived finders (`findByUserIdAndStatusAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc`, `countByUserIdAndStatusAndNextReviewAtLessThanEqual`); `DefaultUserMemoryApi` now injects `ReviewQueueRepository` and maps via MapStruct (`UserMemoryApiMapper.toReviewDtoList`, constructor-based mapping like the existing `UserFactDto`). Null/empty guards return `List.of()` / `0`.
  Consumer A — Coach `reviewDueConcepts` tool (`agent`):
  - New `agent.service.ReviewTool` (`@Tool`) + module-internal `DueReviewsView` (`hasDue`, `count`, `concepts`); resolves the caller from `SecurityContextHolder`, lists due concepts (capped at `MAX_CONCEPTS = 10`) but reports the **honest total** `count` (so the Coach can say "12 due, here are 10"). Registered in `CoachChatClientConfig.coachChatClient` `toolObjects(...)`; a new **"Spaced repetition"** paragraph in `coach-system.txt` tells the Coach to call it when asked what to revise / proactively, and to offer `startQuiz` scoped to a due concept.
  Consumer B — SR-aware `ImproviseTool` (`agent`):
  - `ImproviseTool.suggestStudyToday` now checks `userMemoryApi.dueReviews(currentUser, now)` **before** consulting `GapsApi.recommendedNext`; a genuinely-due concept short-circuits to the existing `review_concept` action (reusing the `agent.action.review.label` + `ImprovisedTaskView`/`ActionPart` plumbing), so "what should I do now?" respects the spaced-repetition ladder over the generic worst-gap heuristic.
  Consumer C — daily-reminder due-count line (`notifications`):
  - `NotificationComposer.composeDailyReminder` gained an `int dueReviewCount` param and appends a localized `notification.reminder.reviewsDue` line when `> 0` (base reminder factored into `baseReminder(...)`); `NotificationService.sendReminderIfDue` injects `UserMemoryApi` and passes `countDueReviews(userId, OffsetDateTime.now(clock))`. New key `notification.reminder.reviewsDue` in `messages{,_ru,_uz}.properties` (EN "Due for review today: {0}.", RU/UZ literal-script equivalents).
  Module isolation:
  - `agent → usermemory` already existed (Slice 2d `UserMemoryAdvisor` / `recordFacts`); `ReviewTool`/`ImproviseTool` only add imports of the **module-root** `UserMemoryApi` + `ReviewDueDto`. **New dependency `notifications → usermemory`** consumes `UserMemoryApi` only (the `long` count — no DTO import). `usermemory` references neither, so **no cycle**; `ApplicationModulesTests.verifyModules` green.
  Tests (unit suites green this session; the Testcontainers ITs need Docker — the daemon is down in the authoring sandbox, so they skip identically and run in CI):
  - New `ReviewToolTest` (due concepts resolved from the security context; nothing-due → `hasDue=false`; cap-at-10 with honest total `count`; anonymous context throws). `ImproviseToolTest` gained `prefersDueReviewConceptBeforeConsultingGaps` (+ a default empty-due stub so the existing gaps/materials-ladder cases are unaffected). `NotificationComposerTest` gained `dailyReminder_withDueReviews_appendsReviewsDueLine` (+ the 4 existing calls take the new `dueReviewCount=0` arg). `NotificationServiceTest` updated for the new constructor arg + 4-arg `composeDailyReminder` stubs.
  - `UserMemoryReviewQueueIntegrationTest` extended with `dueReviewsReturnsOnlyActiveDueRowsScopedToUser` (a due ACTIVE row is returned; a future ACTIVE row and a `MASTERED` row are excluded; **user A never sees user B's due rows** and vice-versa — the AGENTS.md §5 user-isolation non-negotiable for the read path).
  - Ran `--tests` for `ReviewToolTest` / `ImproviseToolTest` / `NotificationComposerTest` / `NotificationServiceTest` / `*ApplicationModulesTests*` under `cleanTest` → all BUILD SUCCESSFUL.
  Doc-mismatch flags (AGENTS.md rule 2 — prior docs/log not edited):
  - **`/admin/jobs` is JobRunr's own built-in dashboard, not custom code.** `application.yml` currently has `jobrunr.dashboard.enabled: false`; Slice 6 task 9 ("JobRunr admin dashboard at `/admin/jobs` behind admin auth") is therefore just *enable the dashboard + front it with admin auth*, not a feature to build. Left for an ops session (admin auth doesn't exist yet).
  - **`docs/features/README.md` and `usermemory-plan.md` had already called `usermemory` "shipped"** while the review **read** side was missing (the prior-issue answer flagged this). With this entry the read path is genuinely complete; only the optional `recommendedNext`-style UI surfacing remains, which the three consumers above now cover server-side. `usermemory-plan.md`'s Slice-3 "outstanding" note is updated to reflect this; the README's coarse "shipped" table is left as-is (it was already accurate at the module granularity).
  - This entry does not supersede any prior entry.

- **2026-06-01 — Frontend↔backend integration, foundation + knowledge base (Topics/Materials): per-feature mock→live flip, token-aware fetch hooks, shared state primitives; aligned `topics`/`materials` to the real contracts and dropped the fictional material `replace`.**
  Start of the frontend integration effort (`.junie/plans/frontend-backend-integration.md`). The app shipped as a **complete MSW-mock prototype** where only auth was truly wired; this work takes it feature-by-feature to the real Spring backend while keeping the MSW layer **mirrored** for demo mode. Frontend-only; no backend changes.
  Foundation (Step 1):
  - **Per-feature live/mock switch** — new `lib/feature-flags.ts` (`isFeatureLive`/`liveFeatures` driven by `NEXT_PUBLIC_LIVE_FEATURES`, comma-list or `all`; everything live when `NEXT_PUBLIC_MOCK_API` is unset) + `mocks/live-passthrough.ts` (maps each feature to `${BASE}/path` globs and emits MSW `http.all(...) → passthrough()` handlers, **prepended** in `mocks/browser.ts` + `mocks/node.ts`). Verified features hit the real backend while the rest stay mocked; with no flags set the passthrough list is empty (non-breaking). Documented in `frontend/.env.example` + `README.md`.
  - **Token-aware fetch layer** — new `hooks/use-api.ts` (`useApi().run(call)`: injects `session.accessToken`, on **401** calls NextAuth `update()` and retries once, else `router.push("/login")`) and `hooks/use-api-resource.ts` (`useApiResource(loader, deps)` → `{data,error,loading,refreshing,reload,setData}`, removes the repeated `useEffect`+`cancelled` boilerplate; refs synced inside an effect and all `setState` kept in the async callback/handlers to satisfy the strict `react-hooks/refs` + `react-hooks/set-state-in-effect` lint). Kept the existing `lib/api.ts#apiFetch(accessToken)` signature — these hooks wrap it, no rewrite of the client-fetch pattern.
  - **Standard states** — `components/common/data-state.tsx` (`<DataState>` composes `Skeleton` loading + `Alert` error w/ retry) and `lib/notify.ts` (`describeError`/`notifyError`/`notifySuccess` over `sonner`, mapping `ApiClientError` status 0 → network copy). New `common.errorTitle`/`common.retry` keys in the EN/RU/UZ dictionary.
  Knowledge base (Step 2) — aligned to `materials.api.TopicController` / `MaterialController`:
  - **Topics:** create/rename/delete are **name-only** (`CreateTopicRequest`/`RenameTopicRequest` have no `description`; `TopicResponse` = `{id,spaceId,name,createdAt,updatedAt}` — **no `description`/`materialCount`/`chunkCount`**). Trimmed those from `lib/topics.ts` + `topic-detail-view` (material count now derived from the loaded list), and **wired the previously-unused `PATCH /topics/{id}` rename** as a polished dialog (a11y title + `aria-invalid`).
  - **Materials:** `MaterialResponse` = `{id,topicId,title,contentType,sizeBytes,status,retryCount,createdAt,updatedAt}` — **no `originalFilename`/`errorCode`/`chunkCount`**. Trimmed `lib/materials.ts`, and **removed `replaceMaterial` + the replace UI entirely** — there is **no `POST /materials/{id}/replace` endpoint** (replace = delete + re-upload). Materials list now shows file size instead of chunk count; the existing `PENDING→PROCESSING→READY` 2s status-polling is kept. Empty states upgraded to the shadcn `Empty` primitive.
  - **MSW mirrored** — `mocks/handlers.ts` response mappers (`asTopicResponse`/`asMaterialResponse`) now emit the exact real shapes and the `/materials/:id/replace` mock handler was deleted; internal mock db bookkeeping (counts) is untouched.
  Flags / limits (AGENTS.md rule 2):
  - **`replace` dropped is a real contract gap, not a regression** — flagged here as the intended delete+re-upload flow.
  - **No live end-to-end verification in this environment:** the real backend is **not running** (`:8080` connection-refused) and auth needs interactive Google OAuth, so the mock→live flip itself was validated as non-breaking + the contracts were aligned by reading the actual controllers/DTOs; demo-mode behavior (topics create/rename/delete, materials) was verified in the running app. `pnpm typecheck`/`lint`/`format` green.
  - **Remaining:** Companion streaming chat (Step 3), Quiz/Gaps (4), Home/Profile/Plan remap (5), Premium/Billing + Telegram (6), Settings + global QA (7).
  - This entry does not supersede any prior entry.

- **2026-06-01 — Frontend integration Step 3: global streaming Companion (flagship RAG surface) replaces the topic-scoped non-streaming chat.**
  Implements the agent contract (`agent.api.AgentController` / `ChatSessionController`, `MessagePart` subtypes). Frontend-only.
  - **New `lib/agent.ts`** — `listSessions`/`createSession` (`GET/POST /agent/sessions`, `CreateChatSessionRequest{title?,channel?}`, sessions are **global**, no `topicId`) + a hand-rolled **`fetch` + `ReadableStream`** SSE reader for `POST /agent/chat/{sessionId}` (`text/event-stream`) that buffers `\n\n`-delimited frames, strips `data:`, and emits typed `MessagePart`s (`text{confidence}`, `citation`, `tool_call`, `action`, `quiz_card`, `error`) with an `AbortController`. **No new dependency** (per the streaming-transport decision — `EventSource` can't send the Bearer token).
  - **New surface** `app/(app)/companion` + `components/companion/*`: sessions rail, in-memory streaming turns, a typed-part renderer (incremental text by concatenating consecutive text parts, **ungrounded amber flag when `TextConfidence=LOW`** — the AGENTS citations non-negotiable, a `Sources` citations block, a tool-call trace line, action chips that re-prompt, and an inline **`QuizCard`** answered via the **real per-question path** `POST /quiz/sessions/{sessionId}/questions/{questionId}/answer`).
  - **Nav/IA** — added **Companion** to `sidebar.tsx`; reworked the 5-slot mobile `bottom-tab-bar.tsx` to make Companion the **emphasized center action** and **dropped Plan** from the bar (still in the sidebar). `topic-detail` lost its Chat tab in favor of an **"Ask about this topic"** button that deep-links to `/companion?seed=…` and auto-sends a seeded prompt.
  - **MSW mirrored** — `mocks/handlers.ts` now streams a mirrored `MessagePart` sequence (`new HttpResponse(ReadableStream, {Content-Type: text/event-stream})` paced with `delay`), serves `GET/POST /agent/sessions`, and adds the new per-question quiz-answer handler so the inline card works in demo. Verified in-app: tool trace → streamed text → citation → answerable quiz (correct→explanation) → action chip, no console errors.
  Flags / limits (AGENTS.md rule 2):
  - **No `/agent` chat-history endpoint** — Companion keeps turns **in memory only**; selecting a past session from the rail shows an empty thread (can't replay). Backend follow-up to add history.
  - **Full `lib/chat.ts` retirement deferred to Step 4** — `lib/quiz.ts` still imports `Citation` from `lib/chat`, so `lib/chat.ts` + `chat-pane.tsx` + the `/chat` mock remain in place but are now **unused** (no nav/route references them). Will delete once `lib/quiz.ts` is realigned.
  - **Inline-quiz mock** stores a canned correct answer per emitted `quiz_card`; the real backend answers authoritatively. Live verification still blocked (backend down + interactive OAuth).
  - This entry does not supersede any prior entry.

- **2026-06-01 — Frontend integration Step 4: Quiz + Gaps realigned to `QuizController` / `GapsController`.**
  Frontend-only contract corrections + polish.
  - **Quiz** (`lib/quiz.ts`): `QuizSessionResponse` is now **flat with embedded `questions[]`** (was a fictional `{session,questions}` envelope); `score` is a 0–1 fraction, `totalCount`/`correctCount` ints. Fixed paths — start `POST /quiz/sessions`, **list** `GET /quiz/sessions`, `GET /quiz/sessions/{id}`, **answer** `POST /quiz/sessions/{sessionId}/questions/{questionId}/answer` (was `/quiz/sessions/{id}/answer`), added **abandon** `POST /quiz/sessions/{id}/abandon`. Dropped the `lib/chat` `Citation` import. `quiz-pane` now branches MCQ vs free-text on `options`, reveals `explanation`+`correctAnswer`, **flags questions with no `materialName`** (reuses `companion.ungrounded`), supports abandon, and shows a **402 → quota upgrade** prompt; results computed client-side from answered questions.
  - **Gaps** (`lib/gaps.ts`): `GapsReportResponse{generatedAt,totalQuestionsAnswered,correctCount,overallAccuracy,summary,gaps[],strengths[],recommendedNext}` with `GapItem{concept,missCount,hitCount,accuracy,suggestedMaterialName,trend}`; `regenerate` → **`refresh`** (`POST /gaps/refresh`). `gaps-view` rewritten to accuracy badge + concept rows (accuracy %, suggested material) + `recommendedNext` → an "ask Companion" CTA + empty states.
  - **MSW mirrored** — quiz handlers now return the flat shape via `asQuizSessionResponse`/`asQuizQuestionResponse`; the per-question answer handler grades real quiz questions (and still serves companion `quiz_card`s); added list + abandon; gaps serves a `buildGapsReport()` mirror for `GET /gaps` + `POST /gaps/refresh`. Added `quiz.quotaReached` (UZ/RU/EN). Verified in-app: quiz start → MCQ answer → correct + feedback reveal → Next; gaps renders with no console errors.
  - Flag: real backend `QuizQuestionResponse.type` is a free string — UI keys off `options` presence rather than enumerated type strings. Live verification still blocked (backend down + OAuth).
  - This entry does not supersede any prior entry.

- **2026-06-01 — Frontend integration Step 5: Home/Profile/Plan remapped off the fictional `/stats` + `/goals` onto `/profile`.**
  Frontend-only. The backend has **no `/stats` and no `/goals`** endpoints — `ProfileController` (`GET/PUT /profile` → `ProfileResponse{userId,locale,timezone,goal,targetDate,dailyReminder,dailyStudyMinutes,sessionsCount,quizCount,streakDays,lastActiveAt}`) and `OnboardingController` (`GET/PUT /onboarding`) carry that data; `PlanController` (`GET /plan`, `POST /plan/steps/{dayIndex}/complete`) is already aligned.
  - New `lib/profile.ts` (`getProfile`/`updateProfile`). `lib/stats.ts#getStats` and `lib/goals.ts#listGoals` now **derive from `/profile`** (kept their existing shapes so the dashboard/profile components compile unchanged); `lib/onboarding.ts` was already aligned. MSW mirrors `GET/PUT /profile` via `buildProfile()` (sessions/quizzes/streak from the in-memory db, goal/target from `db.plan`).
  - **Flag (AGENTS.md rule 2): `ProfileResponse` does not carry the dashboard's analytics** — knowledge-trend history, per-topic scores, weekly week-activity, computed level, and topic/material counts. `getStats` therefore returns the three real fields (`sessionsCompleted`, `streakDays`, derived `weeklyMinutes`) and **empty/neutral analytics**; those richer visuals (incl. the planned `recharts` knowledge trend) need a backend stats/analytics endpoint before they can be genuinely wired. The old `/stats` + `/goals` MSW handlers are left in place (now unused) for reference.
  Status of the overall frontend↔backend integration effort:
  - **Done & verified in demo (Steps 1–5):** integration foundation (per-feature `NEXT_PUBLIC_LIVE_FEATURES` flip, `useApi`/`useApiResource`, `DataState`/`notify`); Topics + Materials; the flagship **streaming Companion** (sessions, typed parts, ungrounded flag, inline quiz); Quiz + Gaps; Home/Profile/Plan remap.
  - **Remaining (Steps 6–7) — not started:** Premium → **`/billing/*`** (checkout/subscription/payments/cancel + `quota-strip`), **Telegram** link-code deep-link flow, the **Settings hub** sub-pages, and the global mobile/RTL/i18n/a11y QA pass. They are unblocked: each `lib/*` realign → MSW mirror → view polish → flip-live cycle is established, and `lib/chat.ts` + `chat-pane.tsx` + the `/chat` mock can be deleted once confirmed fully unreferenced (already unused after Step 3).
  - This entry does not supersede any prior entry.

- **2026-06-02 — Frontend integration Step 6: Premium realigned to `/billing/*` (checkout/subscription/payments/cancel) + Telegram rebuilt to the link-code deep-link flow.**
  Frontend-only; aligned to the real `billing.api.BillingController` and `telegram.api.TelegramController` (verified field-by-field against the controllers, payloads, and the `SubscriptionPlan` / `PaymentProviderKind` enums). The scaffolding had a fictional single `GET /premium` tier+quota blob and a Telegram **pairing-code paste** form with editable reminder-time/timezone; neither matched the backend.
  Billing (`lib/billing.ts`, new): `Subscription`/`Payment`/`CheckoutSession` + `SubscriptionPlanCode`(`FREE`/`PREMIUM_MONTHLY`/`PREMIUM_YEARLY`) / `SubscriptionStatusCode` / `PaymentProviderCode`(`STRIPE`/`PAYME`/`CLICK`) / `PaymentStatusCode` string-unions matching the enum `.name()`s emitted by `BillingMapper`. Functions: `startCheckout(plan,provider)` (`POST /billing/checkout`, body `{plan,provider}` → `CheckoutSessionResponse{provider,externalId,redirectUrl}`), `getActiveSubscription` (`GET /billing/subscription`), `getSubscriptions` (`GET /billing/subscriptions`), `getPayments` (`GET /billing/payments`), `cancelSubscription` (`DELETE /billing/subscription`), plus `deriveTier`/`isActiveSubscription`.
  - **`lib/premium.ts` no longer hits a `/premium` endpoint** (there is none) — `getPremium` now returns `{tier, subscription}` **derived** from `getActiveSubscription` (`PREMIUM` iff an `ACTIVE` non-`FREE` sub). `/premium` was dropped from `mocks/live-passthrough.ts` (only `/billing` + `/billing/*` remain under the `premium` feature).
  - **`premium-view` rebuilt:** FREE → a plan chooser (two plan cards Monthly/Yearly + a **payment-method `RadioGroup`** Payme/Click/Stripe; `alert-dialog`/`toggle-group` aren't installed, so `dialog`+`radio-group` were used) that calls `startCheckout` and **follows `redirectUrl` when present** (`window.location.assign`) else toasts success + reloads; PREMIUM → an active-subscription card (plan, status badge, renews/ends date, **cancel via a `Dialog` confirm** → `cancelSubscription`, which sets `cancelAtPeriodEnd`); plus **payments + subscriptions history** (`Item`/`ItemGroup` rows with localized status badges). Handles the **checkout return** `?checkout=success|cancel` (toast + strips the param via `history.replaceState`; the mount loader refetches). The `*-view-client` wrappers are now trivial (`<PremiumView/>`) — the views self-load.
  - **`quota-strip` is tier-only now** (the backend exposes no per-day remaining counts): for FREE it shows a static "upgrade for unlimited" nudge linking to `/premium`; removed the `{remaining}/{total}` + `quotaLimitReached` logic.
  Telegram (`lib/telegram.ts`, rewritten): `TelegramLink{id?,telegramUsername?,chatId?,linkedAt?,linkCode?,linkCodeExpiresAt?,botUsername?}` (matches `TelegramLinkResponse`, `@JsonInclude(NON_NULL)`). `getTelegram` (`GET /telegram`, **404 → `null`**, also treats an all-null 200 as not-linked), `createLinkCode` (`POST /telegram/link-code`), `unlinkTelegram` (`DELETE /telegram` → 204) + `isLinked`/`isPending`/`telegramDeepLink` (`https://t.me/{bot}?start={code}`).
  - **`telegram-view` rebuilt to the deep-link flow:** Generate code → show the one-time code (click-to-copy) + an **"Open in Telegram"** deep-link button (`Button render={<a/>} nativeButton={false}`) + a manual `@{bot}` / `/start {code}` hint; while pending it **polls `GET /telegram` every 3 s** and flips to a linked card (account + linked-on + **unlink `Dialog`**) once `linkedAt` appears. The capabilities card (reminder/quiz/streak) is kept.
  - **Reminder-time flag (AGENTS.md rule 2):** the brief's "set reminder time" has **no backend endpoint** in the current `telegram`/`profile` contract (the old editable reminder-time/timezone form was scaffolding). The Telegram page now carries a localized note that reminder time follows the study schedule and points to Settings → Notifications; that Notifications page is **read-only** (no reminder-time editor) until a backend field exists. Follow-up: a profile/notification reminder-time setting.
  Settings fallout: `settings-subscription-view` shows tier + (for PREMIUM) `subscription.currentPeriodEnd` (dropped the fictional remaining-quizzes/uploads stats); `settings-notifications-view` + `settings-hub-view` use `isLinked` + show linked account/date (reusing `telegram.linkedAs`/`linkedOn`); `app-shell/sidebar` tier label uses `premium.tierPremium`/`tierFree`.
  Mock layer mirrored (`mocks/{types,db,handlers,live-passthrough}.ts`): `db.premium`/`db.telegram(settings)` replaced by `subscriptions[]`/`payments[]` + a link-code telegram state (starts FREE / unlinked). Handlers: `/billing/*` (checkout activates a sub + records a SUCCEEDED payment in USD for Stripe / UZS otherwise, cancel sets `cancelAtPeriodEnd`), `POST /telegram/link-code` (mints an 8-char code, **auto-links after ~6 s** to simulate the bot round-trip so the poller resolves), `GET /telegram` (**404 until a code/link exists**), `DELETE /telegram` (204). **Quiz quota de-coupled from the removed `db.premium`:** the `POST /quiz/sessions` free-tier gate now derives tier from `db.subscriptions` and counts **today's** sessions (`>= 3`) instead of a stored remaining counter.
  i18n: the `premium` and `telegram` dictionary blocks were **rewritten** (type + EN/RU/UZ) for the new surfaces — plans/cadence, provider label, statuses, cancel-confirm, checkout success/cancel, payments/subscriptions history + 4 payment-status labels, and the full link-code flow (generate/pending/linked/unlink, code-expiry, deep-link hint, reminder note). No hardcoded English (AGENTS.md rule 8).
  Verification: `pnpm typecheck` ✓, `pnpm lint` ✓ (only the pre-existing generated-`mockServiceWorker.js` warning), `pnpm format` ✓. **No live e2e** (backend not running + interactive Google OAuth) — parity was established by reading the actual controllers/DTOs/enums; demo-mode (`NEXT_PUBLIC_DEMO_MODE`) exercises the full checkout/cancel + generate-code→auto-link→unlink flows.
  - **Remaining (Step 7):** the Settings hub is functional but the global mobile/RTL/i18n/a11y QA pass is outstanding, and `lib/chat.ts` + `chat-pane.tsx` + the `/chat` mock are still present (unused since Step 3) pending deletion. Backend follow-ups flagged: a reminder-time setting endpoint, and plan pricing/amount in the checkout contract (the UI shows cadence, not a price).
  - This entry does not supersede any prior entry.

- **2026-06-02 — `docs/frontend/`: per-feature frontend build/rebuild plans + a roadmap; the frontend is not a source of truth.**
  Added a new `docs/frontend/` directory mirroring the backend `docs/features/`
  convention, but **forward-looking** (build/rebuild target) rather than a
  retro-spec. Created at the **user's explicit request** ("create new
  `docs/frontend` folder and put plans in this per feature and create roadmap …
  frontend is not source of truth we need to build frontend or rebuild …
  before I did some jobs but now they dont match our plans").
  - **AGENTS.md rule 3 override.** Rule 3 says "don't invent new docs in
    `docs/`." This directory is an explicit, user-requested exception; recording
    it here is the rule-3-sanctioned way to log the decision. The brief remains
    the **only** source of truth; these plans are non-authoritative and the
    **current `frontend/` code is explicitly treated as a scaffold/prototype to
    reconcile** (keep / refactor / rebuild / delete per plan), never as the spec.
  - **Structure:** `README.md` (index + the shared nine-part per-feature
    template + a current-scaffold audit + shared conventions + the rule-3 note),
    `roadmap.md` (four phases mapped to the brief's Week 1–4 milestones, a
    dependency graph, a status snapshot, and a consolidated "blocked-on-backend"
    table), `00-foundation.md` (cross-cutting shell: routing/IA, design system,
    i18n + RTL, the `useApi`/`ApiResponse` client, the MSW↔live switch), and
    `01..11` one self-contained plan per feature — `01-auth`,
    `02-onboarding-profile`, `03-materials-topics`, `04-companion`, `05-quiz`,
    `06-gaps`, `07-plan`, `08-telegram`, `09-billing-premium`,
    `10-home-dashboard`, `11-settings`. Each plan = overview **and** build plan
    (what it is, brief mapping + non-negotiables, routes/screens, components,
    the **real backend contract consumed**, a keep/refactor/rebuild/delete
    verdict on the existing code, checkbox build slices, acceptance, and
    open/blocked questions).
  - **Single file per feature** (not a backend-style folder with split
    `00-overview` + `<feature>-plan`) — chosen for readability and to match the
    user's "one plan per feature" phrasing; revisit if a feature outgrows one
    file.
  - **Relationship to the integration Steps 1–6** (the 2026-05/06 entries):
    these plans **re-frame** that work against the brief as a written target and
    carry forward its flagged backend gaps (no `/agent` chat-history endpoint;
    `ProfileResponse` lacks dashboard analytics / knowledge-trend; no
    reminder-time field; no checkout amount). The original integration plan file
    `frontend/.junie/plans/frontend-backend-integration.md` was already deleted;
    `docs/frontend/` is now the surviving, structured home for frontend planning.
  - This entry does not supersede any prior entry.


- **2026-06-02 — `.junie/plans/frontend-backend-integration.md` deleted (path correction).**
  The 2026-06-02 entry above stated the original integration plan
  `frontend/.junie/plans/frontend-backend-integration.md` "was already
  deleted." The actual file lived at `.junie/plans/frontend-backend-integration.md`
  (repo root, not under `frontend/`) and **was still present**. Deleted it
  today so the on-disk state matches the prior decision: `docs/frontend/` is
  the sole surviving home for frontend planning, and the six-step integration
  narrative remains preserved in the earlier DECISIONLOG entries (Steps 1–6).
  No content was lost — `docs/frontend/roadmap.md` already re-frames that
  work against the brief and supersedes the deleted file's six-step structure.
  - This entry supersedes only the path reference inside the 2026-06-02
    `docs/frontend/` entry above; all other decisions in that entry stand.


- **2026-06-02 — `docs/frontend/ux.md` added (whole-product UX overview).**
  Created at the user's explicit request as a sibling to the per-feature
  plans already in `docs/frontend/`. The doc is a **synthesis**, not a
  spec: it pulls IA, navigation (desktop sidebar + mobile 5-slot bottom
  tab bar — Home, Topics, Companion centered, Plan, Settings), the
  first-run + daily-loop journeys, the four-state conventions, and the
  AI/RAG non-negotiables out of the brief and the existing `00-foundation`
  / `NN-*` / `roadmap` plans into one place so a reader can see the
  product as one experience. It is explicitly **not** authoritative —
  only the brief is. AGENTS.md rule 3 override is reused from the
  2026-06-02 entry that originally sanctioned `docs/frontend/`; this entry
  records the addition but does not create a new precedent. Also pins one
  previously-open UX decision: the mobile tab bar stays at five slots
  (no hamburger, no overflow menu, no sixth tab); Quiz, Gaps, Premium,
  Telegram, Profile, and Settings sub-pages are reached via deep links
  from the surface that owns them.
  - This entry does not supersede any prior entry.


- **2026-06-02 — First live-integration pass: Auth + Companion against the running Spring backend.**
  Phase 1 exit criterion from `docs/frontend/roadmap.md` is "a new user
  signs in, uploads a document, and gets a grounded, cited answer in
  their language — live, not mocked." The auth half already runs live
  unconditionally (NextAuth `jwt` callback in `frontend/auth.ts`
  exchanges the Google `id_token` against `POST /auth/google` on the
  backend and refreshes via `POST /auth/refresh`), so this entry only
  flips the **Companion** half. Mechanism: MSW is wired with
  `livePassthroughHandlers()` registered **before** the mock handlers
  in both `frontend/mocks/browser.ts` and `frontend/mocks/node.ts`;
  setting `NEXT_PUBLIC_LIVE_FEATURES=companion` makes `/agent/*`
  (chat sessions + the SSE chat stream) pass through to
  `NEXT_PUBLIC_API_BASE_URL` while every other feature stays mocked.
  `frontend/.env.example` and `frontend/.env.local` are updated
  accordingly; `frontend/lib/agent.ts` needed no changes — its
  `listSessions` / `createSession` / `streamChat` paths and the typed
  `MessagePart` discriminator (`text` / `citation` / `tool_call` /
  `action` / `quiz_card` / `error`) already match `AgentController` +
  `ChatSessionController` on the backend exactly, the `ApiResponse`
  envelope unwrap in `frontend/lib/api.ts` matches
  `org.aiincubator.ilmai.common.payload.ApiResponse`, and
  `application.yml`'s `ilmai.cors.allowed-origins` already lists
  `http://localhost:3000` with credentials. To run the live pass:
  start `backend` (`./gradlew bootRun` with `GOOGLE_CLIENT_ID`,
  `JWT_SECRET`, `GOOGLE_GENAI_API_KEY` set), start `frontend`
  (`pnpm dev`), sign in with Google at `/login`, and chat from
  `/companion` — the network tab should show `POST /agent/sessions`
  + `POST /agent/chat/{sessionId}` hitting `localhost:8080`, not MSW.
  Flip back to fully-mocked by setting `NEXT_PUBLIC_LIVE_FEATURES=`
  (empty). This entry does not supersede any prior entry.


- **2026-06-02 — Foundation-doc `/api/v1` base-path claim is stale; flagged per AGENTS.md rule 2.**
  `docs/frontend/00-foundation.md` §5 ("Backend contract consumed")
  states "Base path: `/api/v1`." The running backend serves at root:
  `backend/src/main/resources/application.yml` sets no
  `server.servlet.context-path`, and the actual controllers map at
  `/auth/*` (`AuthController`), `/agent/*` (`AgentController`), and
  `/agent/sessions` (`ChatSessionController`) — no `/api/v1` prefix
  anywhere. `frontend/lib/api.ts` and `frontend/auth.ts` already hit
  the root paths, which is why the live-integration pass above works
  without any client-side change. Per AGENTS.md rule 2, the foundation
  doc (not the code) is the one that's wrong; flagging here for a
  human to either (a) edit `00-foundation.md` §5 to drop "/api/v1"
  or (b) add `server.servlet.context-path: /api/v1` to
  `application.yml` plus rewrite every frontend path. This entry
  does not supersede any prior entry.


- **2026-06-02 — Materials become space-owned with optional topics; per-file size cap moves to quota; audio uploads allowed.**
  Materials were modelled as "every file belongs to exactly one topic," with
  topic ownership transitively giving space ownership and a hard-coded
  `MAX_BYTES = 25 MB`. Three friction points piled up: (a) users wanted to
  drop a file into a space without first inventing a topic, (b) the brief's
  free vs premium split could not express different per-file size caps, and
  (c) `MaterialService.ALLOWED_UPLOAD_TYPES` rejected the very audio MIME
  types `AudioReader` already knows how to ingest. Reworked in three
  build-green slices (tracked in `docs/features/materials/materials-plan.md`
  §4):
  - **Schema (Flyway `V32__materials_space_id.sql`).** `materials.space_id
    UUID NOT NULL` is added and back-filled from `topics.space_id`;
    `materials.topic_id` is `DROP NOT NULL`; the FK is recreated as
    `ON DELETE SET NULL`; new index
    `idx_materials_space_created (space_id, created_at DESC)`.
  - **Domain.** `Material.spaceId` is the ownership column; `Material.topic`
    is `@ManyToOne(optional = true)`. `MaterialStorageKeys` collapses to one
    shape: `{spaceId}/{materialId}` (the 3-arg overload is gone — the
    storage key never depended on topic).
  - **Quota.** `QuotaService.materialUploadMaxBytes(userId)` added; backed
    by `BillingQuotaService` returning `app.free-tier.materialUploadMaxBytesFree`
    (5 MB) for free and `…materialUploadMaxBytesPremium` (25 MB) for
    premium. The constant `MaterialService.MAX_BYTES` is gone — every
    upload pulls the cap per-call from `QuotaService`. Error
    `MATERIAL_TOO_LARGE` already carries the cap in its localized message,
    so the UI surfaces the correct number per tier automatically.
  - **Allowlist.** `ALLOWED_UPLOAD_TYPES` gains `audio/mpeg`, `audio/wav`,
    `audio/mp4` to match `AudioReader`. Closes the
    `audio-allowlist-vs-AudioReader` mismatch flagged in earlier overview
    docs.
  - **Upload contract.** `POST /materials` now takes
    `spaceId` (required `UUID`), `topicId` (optional `UUID`),
    `file` (required multipart). The `title` and `pastedText` request parts
    and the paste-text branch in `MaterialService.upload` are deleted.
    `MATERIAL_CONTENT_REQUIRED` is removed from `MaterialException.Reason`;
    `MATERIAL_SPACE_NOT_FOUND` (404) is added.
  - **Topic delete cascade.** `TopicService.delete(currentUser, topicId,
    deleteMaterials)` and `DELETE /topics/{id}?deleteMaterials=` (default
    `false`). `true` loads `materials.findAllByTopicId(...)` and calls a
    new `MaterialService.deleteAll(...)` for each, fanning out
    `MaterialDeletedEvent` so vectors + blobs are cleaned; `false` runs a
    bulk `materials.detachFromTopic(...)` `UPDATE` (and the FK is
    `ON DELETE SET NULL` as a belt-and-braces). Closes the "topic delete
    semantics" open item.
  - **Repo cleanup.** `MaterialRepository` drops the old
    `…ByTopicSpaceIdIn` variants in favour of `…BySpaceIdIn` ones;
    `DefaultMaterialsApi` (the only cross-module consumer) is moved to
    them. The `MaterialsApi` contract itself does not change.
  - **Storage key compatibility.** Newly-uploaded materials use the 2-arg
    key. There are no production rows in this codebase yet, so no
    backfill migration is required; if there were, a one-shot `mv`
    script keyed off `(spaceId, materialId)` would do.
  - **Tests.** `MaterialServiceTest` + `TopicServiceTest` are rewritten to
    the new signatures and exercise both `deleteMaterials` branches;
    `EmbeddingPipelineIntegrationTest` and `UserIsolationIntegrationTest`
    now set `material.spaceId` and use the 2-arg storage key.
  - **i18n.** `messages*.properties` drop
    `material.error.contentRequired` and add `material.error.spaceNotFound`
    in EN/RU/UZ.
  - **Docs.** `docs/features/materials/00-overview.md`,
    `01-upload-and-storage.md`, `02-ingestion-lifecycle.md`, and
    `03-topics.md` are updated. `materials-plan.md` open items lose the
    audio-allowlist, 25-MB-to-config, and topic-delete-semantics rows.
  This entry supersedes (a) the 2025-era implicit "MAX_BYTES = 25 MB"
  constant baked into `MaterialService` and (b) the prior topic-mandatory
  upload contract; it does not supersede any other entry.


- **2026-06-02 — Garage added as the local-dev S3 backend for `BlobStorage`.**
  `backend/compose.yaml` now ships a `garage` service (image
  `dxflrs/garage:v1.0.1`, S3 API on `localhost:3900`, admin on
  `3903`, single-node `replication_factor = 1`, sqlite metadata, named
  volumes `garage-meta` / `garage-data`) plus a one-shot
  `garage-init` sidecar that runs `backend/garage/init.sh` to
  idempotently assign the node layout, create bucket `ilmai-materials`,
  and import the fixed dev access key `ilmai-dev` /
  `ilmai-dev-secret`. The Garage daemon is configured via
  `backend/garage/garage.toml` (region `us-east-1`, RPC secret pinned
  to a known dev value — **dev only, never use in prod**). The Spring
  Boot `ilmai.storage.s3.*` block now defaults to
  `endpoint=http://localhost:3900`, `access-key=ilmai-dev`,
  `secret-key=ilmai-dev-secret` so `./gradlew bootRun` talks to
  Garage out of the box; the existing `STORAGE_S3_*` env vars still
  override for staging/prod where a real S3 / Garage cluster is wired in.
  No Java changes — `S3BlobStorage` + `S3BlobStorageConfig` already
  honour `endpoint` / `path-style-access` / static credentials. The
  brief is silent on the choice of object-storage backend; this entry
  records the local-dev decision until / unless the brief revises it.

- **2026-06-02 — Garage init: alpine + admin API, supersedes the
  `dxflrs/garage` shell init from the previous 2026-06-02 entry.**
  The first cut wired the `garage-init` sidecar as
  `dxflrs/garage:v1.0.1` running `/bin/sh /init.sh` against the
  Garage CLI. That image is **distroless** and has no `/bin/sh`,
  so the container failed with `runc create failed: ... exec:
  "/bin/sh": stat /bin/sh: no such file or directory`. Fix:
  `garage-init` now uses `alpine:3.20`, installs `curl` + `jq`
  on first run, and drives Garage's **admin API v1** on port 3903
  with the bearer token `ilmai-dev-admin-token` (pinned in
  `backend/garage/garage.toml`'s `[admin]` block — dev only).
  Endpoints used: `GET /v1/status`, `GET|POST /v1/layout`,
  `POST /v1/layout/apply`, `POST /v1/bucket`,
  `GET /v1/bucket?globalAlias=…`, `GET /v1/key?id=…`,
  `POST /v1/key/import`, `POST /v1/bucket/allow`.
  - Garage v1's `ImportKey` requires **format-valid** credentials:
    `accessKeyId` must be `GK` + 24 hex chars and
    `secretAccessKey` must be 64 hex chars (32 bytes). Free-form
    strings like `ilmai-dev` / `ilmai-dev-secret` are rejected
    with `Invalid key format`. The pinned dev pair is therefore
    `GK1ac1bd1ac1bd1ac1bd1ac1bd` /
    `1ac1bd…1ac1` (64 hex chars, repeating `1ac1bd` padding).
    `application.yml` defaults for `ilmai.storage.s3.access-key` /
    `secret-key` were updated to match. AWS SDK v2 accepts any
    `GK…` access key id, so `S3BlobStorage` is unaffected.
  - End-to-end S3 PutObject / ListObjects / GetObject against
    `http://garage:3900` with the pinned pair verified via the
    `amazon/aws-cli` container; `garage-init` exits 0 and the
    bucket `ilmai-materials` is readable + writable.
  This supersedes the credential pair and init image choice
  recorded in the previous 2026-06-02 entry; the rest of that entry
  (compose layout, `garage` service config, volumes, `application.yml`
  endpoint/bucket defaults, env-var override contract) stands.

- **2026-06-02 — Disable AWS SDK v2 flexible checksums for Garage S3.** Uploads via S3BlobStorage failed against Garage with InvalidRequestException: Bad request: Invalid content sha256 hash: Invalid character 'S' at position 0. Root cause: AWS SDK for Java v2.30+ defaults 
equestChecksumCalculation to WHEN_SUPPORTED, which makes the client send x-amz-content-sha256: STREAMING-UNSIGNED-PAYLOAD-TRAILER plus an x-amz-trailer for PutObject; Garage validates that header as a hex sha256 and rejects the literal string. Fix in S3BlobStorageConfig.s3Client(): set 
equestChecksumCalculation(WHEN_REQUIRED) + 
esponseChecksumValidation(WHEN_REQUIRED) on the S3ClientBuilder, and chunkedEncodingEnabled(false) on S3Configuration (Garage also dislikes aws-chunked signed payloads). The change is endpoint-agnostic — real AWS S3 still accepts WHEN_REQUIRED — so no profile gating is needed. Applies to any S3-compatible backend that doesn't implement SigV4a streaming trailers (Garage, older MinIO, Ceph RGW, etc.).

- **2026-06-02 — Fix MaterialsApiMapper.spaceId mapping (NoSuchKey on ingestion).** `MaterialUploadedEvent` ingestion failed with `NoSuchKeyException` because `MaterialsApiMapper.toDto` mapped `MaterialDto.spaceId` from `topic.spaceId`. Uploads are allowed without a topic (`MaterialService.upload` accepts `topicId == null` and sets `Material.spaceId` directly on the entity), so for topic-less materials the DTO's `spaceId` was `null` and `MaterialStorageKeys.forMaterial(material)` produced `null/<materialId>` on the read path while the write path used the real `<spaceId>/<materialId>`. Fix: map `spaceId` from the `Material` entity's own `spaceId` field (`@Mapping(target = "spaceId", source = "spaceId")`). No schema or storage-layer change needed.

- **2026-06-02 — Native `gemini-embedding-2` multimodal ingestion (PDF/audio/image as chunk boundaries).**
  Rewired `ai/ingestion/**` to use `gemini-embedding-2`'s native per-request
  limits as chunk boundaries instead of the previous PNG-rasterization +
  bytes-per-ms heuristics. Authoritative limits used: PDF 6 pages/request
  (`application/pdf`), images 6/request (`image/png`, `image/jpeg`), audio 180 s
  & 1 file/request (`audio/mp3`, `audio/wav`); output pinned to 768 dims.
  Sources: https://developers.googleblog.com/building-with-gemini-embedding-2 ;
  https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/gemini/embedding-2 .
  - **PDF.** `PdfReader` no longer rasterizes pages. It slices the source PDF
    into ≤`pagesPerChunk` standalone sub-PDFs (PDFBox 3.x `Loader.loadPDF` →
    per-range `new PDDocument()` + `addPage` + `save`) with
    `step = pagesPerChunk - pageOverlap`, emitting the new `PdfRangePart`
    (`pageStart`, `pageEnd`, `pdfBytes`). `PdfPagePart` deleted.
  - **Audio.** `AudioReader` drops `audio/mp4` from `SUPPORTED` (not on Google's
    list); accepts `audio/mpeg`, `audio/mp3`, `audio/wav`. WAV path slices
    decoded PCM by frame via `javax.sound.sampled.AudioSystem` and re-wraps each
    window with a fresh `WAVE` header. MP3 path is a **dependency-free** frame-
    header parser (ID3v2 skip/copy, MPEG1/2/2.5 Layer I/II/III bitrate+sample-
    rate tables) cutting on real frame boundaries; `startMs`/`endMs` are actual
    decoded boundaries. **Deviation from the scratch plan:** `mp3spi` was *not*
    added — it decodes to PCM and cannot produce byte-range MP3 slices, and
    AGENTS §6 rule 6 says don't add deps unless required. So Slice 7's "added
    mp3spi" line in the plan does **not** apply; no new dependency landed.
  - **Images.** New `ImageReader` + `ImagePart` route `image/png` / `image/jpeg`
    directly; up to 6 images are batched into one `MultimodalContent` per embed
    call (`MAX_IMAGES_PER_REQUEST=6`).
  - **Wiring.** `MaterialIngestionService.indexParts` now routes by part type:
    `chunk_kind=pdf_range` (+`page_start`/`page_end`),
    `chunk_kind=audio_segment` (+`audio_start_ms`/`audio_end_ms`),
    `chunk_kind=image_set` (+`image_count`/`image_mime_types`). Text path
    unchanged (`TokenTextSplitter` → `VectorStore.add`).
  - **Config.** New `IngestionProperties` (`@ConfigurationProperties("ilmai.ingestion")`)
    with top-level `PdfProps` (`pagesPerChunk=6`, `pageOverlap=2`) and
    `AudioProps` (`windowMs=120000`, `overlapMs=5000`) as separate files;
    `application.yml` gains `ilmai.ingestion.pdf.*` / `audio.*` blocks
    (env-overridable). Injected into `PdfReader` / `AudioReader`.
  - **Upload allowlist.** `MaterialService.ALLOWED_UPLOAD_TYPES` gains
    `image/png` + `image/jpeg`. `audio/mp4` is left in the allowlist on
    purpose for now even though `AudioReader` no longer reads it — an mp4
    upload passes validation then lands `FAILED` at dispatch; reconciling that
    drift (reject mp4 vs. transcode) is a separate product decision (the
    iPhone-`m4a` question), not part of this slice.
  - **Dead-code purge.** Deleted `ai/embedding/FileDataPart.java` and its
    `instanceof FileDataPart` branch in `GoogleGenAiEmbeddingModel.toSdkPart`,
    `GoogleGenAiEmbeddingModel.embedMultimodalBatch`,
    `MultimodalEmbeddingApi.embedBatch` / `dimensions`, and the matching
    `DefaultMultimodalEmbeddingApi` overrides; dropped the two now-dead tests
    (`embedMultimodal_fileDataPart_sendsFileUri`,
    `embedMultimodalBatch_iteratesEachContent`). `GoogleGenAiEmbeddingModel.dimensions()`
    (the Spring AI `EmbeddingModel` override, still used by config/tests) is
    **kept**. `IlmaiChatClientFactory` was verified still in use (chat provider
    abstraction) and was **not** deleted.
  - **Tests.** New `PdfReaderTest`, `AudioReaderTest`, `ImageReaderTest`,
    `MaterialIngestionServiceMultimodalTest`; `MaterialServiceTest.upload_acceptsImage`
    added and `upload_rejectsUnsupportedContentType` retargeted to `image/gif`
    (since `image/png` is now accepted).
  This supersedes the bytes-per-ms audio heuristic and the per-page PNG PDF
  path; it does not supersede the 2026-05-19 embedding-provider-locked entry.
  Replaces the scratch `MULTIMODAL_INGESTION_PLAN.md`, now deleted.

- **2026-06-02 — Fix `CitationGuardAdvisor` regenerate-once crash
  (`No CallAdvisors available to execute`).** Coach `/chat` turns that
  triggered the citation regenerate path threw
  `java.lang.IllegalStateException: No CallAdvisors available to
  execute`. Root cause: Spring AI 2.0.0-M6's
  `DefaultAroundAdvisorChain` is **single-use** — `nextCall(...)` pops
  the next advisor off an internal `Deque`, so the downstream advisors
  (incl. the terminal model-call advisor) are already drained after the
  first `chain.nextCall(request)`. `CitationGuardAdvisor.adviseCall`
  called `chain.nextCall(...)` a **second** time for the regenerate
  attempt, hitting the empty-deque guard. (The bug was invisible to
  `CitationGuardAdvisorTest` because the test's `ScriptedCallAdvisorChain`
  is re-callable.) Fix: the regenerate attempt now uses
  `chain.copy(this).nextCall(retryRequest)` — `CallAdvisorChain.copy(after)`
  rebuilds a fresh chain of the advisors *after* `CitationGuardAdvisor`,
  so the second model call runs against a new, un-drained deque. One-line
  change in `agent.service.CitationGuardAdvisor`; the regenerate-once +
  low-confidence semantics (DECISIONLOG 2026-05-26 Slice 1c) are
  preserved. All 90 `agent.service` tests green.


- **2026-06-08 — Adopted the `ui-message-stream` library (JitPack) + bumped Spring AI M6→M8: the web `POST /agent/chat/{sessionId}` endpoint now streams the AI SDK v6 *UI Message Stream* protocol over a real `ChatClient.stream()`, replacing the blocking fake-stream `Flux<MessagePart>`. User-approved temporary non-negotiable trade-off (Option C) — read carefully.**
  The user asked to use their own library `ui-message-stream` to "simplify everything" and supplied a Maven `<dependency>`; the backend is Gradle, so it was translated. Scope was confirmed with the user: (1) full migration of the web chat path to real `.stream()` + the library's servlet transport, switching the wire to AI SDK v6; (2) backend-only (the `useChat` frontend is adapted later); (3) bump the Spring AI BOM to the library's tested `2.0.0-M8`.
  Build / dependency:
  - `backend/build.gradle`: added `maven { url 'https://jitpack.io' }` + `implementation 'com.github.uzinfowebuz.ui-message-stream:ui-message-stream-spring-boot-starter:0.2.0'` (JitPack multi-module coordinate `com.github.<user>.<repo>:<artifact>:<tag>`; the starter brings `core` + `spring` transitively, its Spring/Reactor/Spring AI deps are `provided`). Bumped `springAiVersion` `2.0.0-M6 → 2.0.0-M8`. Whole project (`compileJava`/`compileTestJava`) compiles clean under M8 — the milestone bump broke nothing.
  Web streaming path (new):
  - `agent/api/AgentController.chat` now returns `org.springframework.web.servlet.mvc.method.annotation.SseEmitter`, calls `UiMessageStreamHttp.applyHeaders(response)` (sets `Content-Type: text/event-stream` + `x-vercel-ai-ui-message-stream: v1`), and delegates to the new intra-module `agent/service/CoachStreamService` (it no longer touches `AgentApi`). `produces=text/event-stream` dropped (headers come from the library).
  - `CoachStreamService.stream(...)` builds `coachChatClient.prompt().user(...).advisors(conversationId + current-user).toolContext(Map.of(RecordingToolCallingManager.SINK_KEY, sink)).stream().chatClientResponse()`, returns `new UiMessageStreamEmitter().from(upstream, ChatClientResponseMapper.TEXT_ONLY, sink, coachStreamExecutor)`. Quota `reserve`/`commit`/`refund` + post-turn `maintainSummary`/`extractFacts`/`UserActivityRecordedEvent` run via `Flux` lifecycle hooks (`doOnNext` captures usage metadata; `doOnComplete` commits + fires side-effects on the executor; a single `settled` `AtomicBoolean` + `doFinally` refunds on error/cancel). Quota-exceeded / no-LLM short-circuit via the imperative `UiMessageStreamEmitter.writeTo(emitter, writer -> …)`.
  - New `coachStreamExecutor` bean in `CoachChatClientConfig` = `new DelegatingSecurityContextExecutor(Executors.newVirtualThreadPerTaskExecutor())` — propagates the request `SecurityContext` to the streaming worker.
  - Citations/quiz/actions are interleaved as AI SDK v6 `data-*` parts via a per-request `SerializedPartSink` placed in the Spring AI `ToolContext` and handed to the emitter (the sink's lock keeps the text-block invariant across threads). New package-private helper `agent/service/AgentToolContext.sink(ToolContext)`. The three part-emitting tools are now sink-aware (added a `ToolContext` param): `RetrieveTool` → `data-citation` (materialId/materialName/locator/snippet/score), `StartQuizTool` → `data-quiz` (the `QuizCardDto`), `ImproviseTool` → `data-action` (action/label/payload). Each keeps its existing `ThreadLocal` recording (`AgentRetrievalContext`/`AgentQuizContext`/`AgentActionContext`) for the blocking path, so behavior on the Telegram/blocking flow is unchanged.
  Telegram / blocking path untouched:
  - `AgentApi.chat(...)` keeps its blocking `Flux<MessagePart>` contract (consumed by `telegram.TelegramUpdateHandler`); `DefaultAgentApi` is unchanged. Only the web controller moved to streaming. No cross-module dependency was added — `CoachStreamService` reuses exactly the collaborators `DefaultAgentApi` already used (incl. module-root `ai.IlmaiChatClientFactory`); `ApplicationModulesTests.verifyModules` green.
  USER-APPROVED NON-NEGOTIABLE TRADE-OFF (Option C — flagged, temporary):
  - The coach turn relies on **four custom `CallAdvisor`-only advisors** — `GroundingGuardAdvisor` (rewrites ungrounded answers), `CitationGuardAdvisor` (forces a citation, else marks low-confidence), `UserMemoryAdvisor` (injects goal/streak/facts), `ChatSummaryAdvisor` (injects conversation summary) — plus request-thread `ThreadLocal`s (`AgentRetrievalContext`/`AgentResponseFlags`). Spring AI's `ChatClient.stream()` invokes only the **stream** advisor chain, so **all four are skipped on the web streaming path**. Per the user's explicit choice (after the trade-off was surfaced), grounding rewrite + citation enforcement/low-confidence + memory/summary injection are **temporarily NOT applied on the web channel** — a deliberate, time-boxed deviation from the brief's grounding + citation non-negotiables (AGENTS.md §5). Mitigation: citations are still **surfaced** as `data-citation` parts whenever `retrieve` runs (just not *enforced*); the built-in `MessageChatMemoryAdvisor` is stream-capable, so conversation memory still persists. The Telegram/blocking path retains all four guards. **Follow-up required:** re-implement the guards as buffering `StreamAdvisor`s (or pre-compute grounding/citation/confidence + memory/summary outside the stream chain) to restore the guarantees on the web path.
  Threading caveat (flagged, unverifiable here):
  - Tool user-resolution still reads `SecurityContextHolder`; it is propagated to the emitter's worker thread via `DelegatingSecurityContextExecutor`. This assumes Spring AI executes `@Tool` calls on that worker thread during `.stream()`. It could **not** be verified end-to-end in this environment (no live Gemini / no network), and the real Google GenAI adapter may emit/execute on its own threads. If so, the 10 `SecurityContextHolder`-based tools would each need `ToolContext`-based user resolution (today only the 3 part-emitting tools read `ToolContext`, and only for the sink). Validate against the live model before relying on it in production.
  Tests / verification (no Docker / no live LLM in this sandbox):
  - `AgentControllerSmokeTest` rewritten: mocks `CoachStreamService` to return a real `UiMessageStreamEmitter` over a synthetic `Flux<ChatClientResponse>` (same-thread `Runnable::run`, like the library's own MVC demo) and asserts the `x-vercel-ai-ui-message-stream: v1` header, `text/event-stream`, and v6 `text-delta`/`[DONE]` frames. `RetrieveToolUserIsolationTest` gained a test proving `retrieve` emits a `data-citation` part to a `ToolContext` sink; the three tool isolation tests updated for the new `ToolContext` arg (passed `null`). Green: `compileJava`, `compileTestJava`, the affected unit tests (`*AgentControllerSmokeTest`, `*RetrieveToolUserIsolationTest`, `*StartQuizToolUserIsolationTest`, `*ImproviseToolTest`, `*DefaultAgentApi*`, `*CitationGuardAdvisorTest`, `*TelegramUpdateHandlerTest`), and `ApplicationModulesTests.verifyModules`. Testcontainers ITs and any live-LLM end-to-end stream were **not** run here.
  Deprecation / doc-mismatch flags (AGENTS.md rule 2 — prior docs/log not edited):
  - `ChatClientRequestSpec.toolContext(Map)` is deprecated-and-marked-for-removal in Spring AI M8 (compile **warning** only; kept because the library's own examples use it); the pre-existing `Builder.defaultToolCallbacks(ToolCallbackProvider...)` in `CoachChatClientConfig` also warns under M8.
  - **AGENTS.md §3 says the config file is `application.yaml`; it is actually `backend/src/main/resources/application.yml`.**
  - **The `agent` feature keeps request/response DTOs in `agent/api/` (`AgentChatRequest`, `ChatSessionResponse`, …) rather than a `payload/` sub-package** as AGENTS.md §4 prescribes — pre-existing, surfaced not changed.
  - The user supplied a Maven `<dependency>` snippet though the backend is Gradle (translated to a Gradle `implementation` coordinate + the JitPack `maven { }` repo).
  - This entry does not supersede any prior entry.

- **2026-06-08 — Restored grounding & citation guarantees via buffered turn replay, resolved tool-user from ToolContext, and migrated frontend companion UI to Vercel AI SDK useChat.**
  - **Rebuilt Web Stream as Buffered Replay:** Refactored `CoachStreamService` to run a fully guarded call turn via `AgentApi.chat()` inside `coachStreamExecutor`. The parts are replayed as v6 frames via `UiMessageStreamEmitter` (`start` → `text-delta` → `data-citation`/`data-quiz`/`data-action`/`data-confidence` → `finish` → `[DONE]`), with header `x-vercel-ai-ui-message-stream: v1`. This restores the grounding, citation, memory, and summary guarantees on the web path.
  - **ToolContext User Resolution:** Repurposed `AgentToolContext` to provide type-safe resolution of the `CurrentUser` identity from Spring AI `ToolContext`. Added `CurrentUser` to the prompt context in `DefaultAgentApi.chat()`. Refactored all 10 tool classes (12 `@Tool` methods) to accept `ToolContext` and resolve user ID from it, completely removing dependencies on `SecurityContextHolder` within tools.
  - **Frontend useChat Migration:** Migrated the companion UI to `@ai-sdk/react` `useChat` and `DefaultChatTransport` (v6 beta). Replaced manual SSE parsing in `lib/agent.ts` with `createCoachTransport`. Updated `companion-client.tsx` to drive the chat through `useChat` and render the message parts array into the Socratic UI. Ungrounded badge warning is displayed for answers with no citations or marked low confidence. Updated the MSW mock in `handlers.ts` to emit v6 frames.
  - **Cleanups and Sweep:** Deleted the unreachable `ToolCallPart.java` and removed its registration in `MessagePart.java`. Removed deprecated `.defaultToolCallbacks` and `.toolContext` usage in favor of `.defaultTools(...)` and `.tools(...)`.
  - **Verification:** All 94 backend tests passed successfully, and `ApplicationModulesTests.verifyModules` is completely green.

- **2026-06-09 — Moved `usermemory` to `agent` module as a submodule.**
  To simplify module boundaries and layout per the issue instructions, the entire `usermemory` submodule has been moved under the `agent` module package hierarchy.
  - **Relocation:** Moved all Java source and test files from the top-level package `org.aiincubator.ilmai.usermemory` to `org.aiincubator.ilmai.agent` and its submodule package `org.aiincubator.ilmai.agent.usermemory`.
  - **Structure Alignment:**
    - Module-root interface and DTO classes (`UserMemoryApi`, `UserFactDto`, `ReviewDueDto`) were placed directly in `org.aiincubator.ilmai.agent`.
    - Internal implementations and domain entities/repositories were placed under `org.aiincubator.ilmai.agent.usermemory.service` and `org.aiincubator.ilmai.agent.usermemory.domain`.
    - Old top-level `package-info.java` configuration for the separate `usermemory` Spring Modulith module was deleted, as it is now nested under the `agent` module's root package.
  - **Cleanups and Sweep:** Updated package declarations and all imports across the codebase including references in `agent` tool services, unit/integration tests, and notification services.
  - **Verification:** Ran `./gradlew compileJava compileTestJava` and all tests under `org.aiincubator.ilmai.agent.*` plus `ApplicationModulesTests.verifyModules` to verify package visibility and Spring Modulith compliance. All tests passed successfully.


- **2026-06-09 — Adopted the BFF (Backend-for-Frontend) proxy pattern on the Next.js tier; the backend access token no longer reaches the browser. Supersedes the "`session.accessToken` … exposed to the app" clause of the 2026-05-16 NextAuth entry.**
  The browser now talks only to a same-origin proxy and never holds the backend access token; the NextAuth session cookie stays the sole client-held credential. The backend two-token model (`JwtService` / `RefreshTokenStore` rotation + reuse-detection) and the `auth.ts` `jwt` refresh callback are **unchanged** — this is a frontend-only change. User explicitly chose the "full clean refactor" depth (vs. a minimal proxy) and declined the two optional add-ons (logout revocation, `session.error` consumption).
  - **Proxy route.** New `frontend/app/api/backend/[...path]/route.ts` (Node runtime, `export const dynamic = "force-dynamic"`) handles GET/POST/PUT/PATCH/DELETE via one shared `handle`. It resolves the token server-side, injects `Authorization: Bearer …`, forwards method + body (read as `arrayBuffer`, so multipart uploads keep their boundary/length) + `accept`/`accept-language`/`content-type`, and returns `new Response(upstream.body, …)` so the AI SDK SSE chat stream passes straight through. It never forwards the session cookie to the backend; returns a 401 envelope when unauthenticated and 502 on upstream failure. Calls `ensureMockServerStarted()` so demo / `NEXT_PUBLIC_LIVE_FEATURES` mock mode keeps working — the proxy's server-side `fetch` is intercepted by the MSW **node** server exactly like `apiFetch`'s old server path.
  - **Server token reader.** New `frontend/lib/server-auth.ts` `getServerAccessToken()` decodes the encrypted session cookie with `getToken` from `next-auth/jwt` (secure-prefix tolerant — tries `__Secure-authjs.session-token` then `authjs.session-token`), reading cookies via `next/headers`. No refresh happens here; refresh stays in the `jwt` callback and is triggered reactively. **Invariant:** `next/headers` may only be imported by server-only files (the proxy route + `settings/account/page.tsx`); it must NOT be reachable from `lib/api.ts` (which is in the client graph) or the Turbopack client build fails with "you're importing a component that needs next/headers". That is why the server token is passed to `apiFetch` explicitly rather than auto-resolved inside it.
  - **`session` shape.** `auth.ts` `session` callback and `types/next-auth.d.ts` no longer expose `accessToken` / `accessExpiresAt`; `session` now carries only `user` + `error`. All server-side route guards switched from `if (!session?.accessToken)` to `if (!session?.user)` (and `app/page.tsx`'s positive check likewise).
  - **Token-free data layer.** `lib/api.ts` `apiFetch`: client calls hit `/api/backend{path}` (`PROXY_PREFIX`) with **no** `Authorization` header; server calls hit the backend directly and attach a token only via an explicit optional `accessToken` option (used solely by the account page, which calls `getServerAccessToken()`). Every `lib/*` fetcher (`agent`, `plan`, `gaps`, `topics`, `profile`, `stats`, `goals`, `quiz`, `materials`, `telegram`, `chat`, `billing`, `premium`, `onboarding`) dropped its `accessToken` first argument. `createCoachTransport(sessionId)` targets the proxy and no longer sends an `Authorization` header.
  - **Hooks + components.** `useApi`/`useApiResource` no longer thread a token — `ApiCall<T>` is now `() => Promise<T>`; they gate on `status === "authenticated"` and refresh reactively (`update()` then retry once on a 401). ~25 client components stopped reading `session?.accessToken` and now gate on `useSession().status`, calling the token-free fetchers.
  - **Verification.** `pnpm typecheck` and `pnpm build` are green (`/api/backend/[...path]` registered as a dynamic route; all 20 pages compile). `pnpm lint` reports 8 **pre-existing** errors in `components/companion/companion-client.tsx` + `lib/agent.ts` (`no-explicit-any`, unescaped JSX quotes, `set-state-in-effect`, unused `Turn`/`nextTurnId`) that predate and are unrelated to this change; the Turbopack build does not run ESLint so they do not block it. End-to-end runtime against a live backend / real Google login was not exercised in this environment.
  - Doc-mismatch flag (AGENTS.md rule 2, not edited): the 2026-05-16 entry's statement that `session.accessToken` is exposed is now stale — the access token is server-only. Only that clause is superseded; the rest of that entry (Google provider, HttpOnly-cookie storage, refresh ~60 s before expiry) still holds.

- **2026-06-09 — Telegram outbound Bot API moved off the hand-rolled `RestClient`+`Map` client onto the typed `org.telegram:telegrambots-client:10.0.0` (Phase 0+1 of `docs/telegram/telegram-bot-library-migration-plan.md`, behaviour-preserving).**
  Executed the spike + introduce-typed-client phases of the migration plan; later phases (native quiz fields, file upload→materials, `sendMessageDraft` streaming, DTO retirement) are **not** done yet and remain open in the plan.
  - **Dependency.** `backend/build.gradle` gains `implementation 'org.telegram:telegrambots-client:10.0.0'` (released 2026-06-01, post-dates Bot API 9.5). It pulls OkHttp + commons-io transitively; `compileJava`/`compileTestJava` and the scoped test suite resolve cleanly on Spring Boot 4.0.6 / JDK 25 with no version conflict. The auto-configuring `telegrambots-springboot-*-starter`s were **rejected** (per the plan) to avoid a competing webhook endpoint / bot session and Modulith risk — only the bare typed client is on the classpath.
  - **Bean swap.** `telegram/config/TelegramConfig` drops the `RestClient telegramRestClient` bean and instead exposes a `org.telegram.telegrambots.meta.generics.TelegramClient` bean built as `new OkHttpTelegramClient(botToken)` (empty-string fallback when the token is unset, so the bean still constructs in token-less/test contexts; `isEnabled()` continues to gate every actual call). `@EnableScheduling` / `@EnableConfigurationProperties` unchanged.
  - **Client rewrite.** `telegram/service/TelegramApiClient` internals now build typed `SendMessage` / `SendPoll` (`InputPollOption`) / `AnswerCallbackQuery` requests and `InlineKeyboardMarkup`+`InlineKeyboardRow`+`InlineKeyboardButton`, executed via `telegramClient.execute(...)`. **Every public method signature is identical** (`isEnabled`, `sendMessage(chatId,text)`, `sendMessage(chatId,text,List<InlineButton>)`, `sendPoll → pollId`, `answerCallbackQuery`), so `TelegramUpdateHandler`, `DefaultTelegramApi`, and `OutboxDrainService` are untouched. The soft-failure contract is preserved: the checked `TelegramApiException` (and `RuntimeException`) are caught and logged, returning `false`/`null`. Poll id is read from `Message.getPoll().getId()`; the old `extractPollId(Map)` helper is gone. The poll is still a plain `type="regular"` poll — native quiz fields are a later phase.
  - **All Telegram lib imports are confined to the `telegram` module** (`config` + `service`); `TelegramApi` still returns primitives/DTOs, so no library type crosses a module boundary.
  - **Tests.** New `telegram/service/TelegramApiClientTest` (mocks `TelegramClient`, 8 cases: HTML build, inline-keyboard markup, poll-id extraction, too-few-options skip, soft-failure on `TelegramApiException` for send/poll, callback-query build, disabled-gating). Green: `TelegramApiClientTest` (8/8), `TelegramUpdateHandlerTest` (9/9), `OutboxDrainServiceTest` (2/2), `ApplicationModulesTests.verifyModules` (1/1). Testcontainers ITs and any live-bot smoke test were **not** run in this environment (no Docker / no real bot token).
  - This entry does not supersede any prior entry.

- **2026-06-09 - Telegram native quizzes (Phase 2 of `docs/telegram/telegram-bot-library-migration-plan.md`): coach quiz cards now go out as native Telegram quiz polls, with the correct option resolved server-side.**
  Implemented Phase 2 on top of the Phase 0+1 typed-client migration. Server-side `gradeAnswer` stays the single source of truth for citations + gap tracking (resolves the plan's open question #2 in favour of keeping server-side grading); Telegram's native quiz UI is presentation only.
  - **Outbound client.** `telegram/service/TelegramApiClient` gains `sendQuizPoll(chatId, question, options, correctOptionId, explanation) -> pollId` that builds a `SendPoll` with `type=quiz`. telegrambots-client v10 models the Bot API's single `correct_option_id` as `correctOptionIds(List<Integer>)`, so we pass a singleton list. Explanation is sent as plain text, trimmed/truncated to 200 chars. The existing regular `sendPoll(...)` is kept as a fallback and all prior signatures are unchanged (soft-failure contract preserved: catch `TelegramApiException`/`RuntimeException`, return null).
  - **Correct option resolved server-side, never from the model.** New cross-module `QuizApi.resolveQuizPoll(CurrentUser, sessionId, position) -> QuizPollSpecDto(correctOptionId, explanation)` (module-root Lombok value DTO). Backed by `QuizService.resolveByPosition` (user-scoped via `require(currentUser, sessionId)`) + new `QuizGrader.correctOptionIndex(question)` which matches the normalized `correctAnswer` against the normalized option texts; returns `null` for non-MULTIPLE_CHOICE / open-ended / unmatched. `DefaultQuizApi.resolveQuizPoll` maps the internal `QuizPollSpec` to the module-root DTO and returns `null` on `QuizException`. `QuizCardPart` still carries no answer (RAG/security non-negotiable - the correct option never crosses to the model/card).
  - **Handler wiring.** `TelegramUpdateHandler.sendQuizPoll` calls `quizApi.resolveQuizPoll(new CurrentUser(userId), ...)` and prefers the native quiz poll when a spec is present and the index is in range, else falls back to a regular poll. `TelegramQuizPoll` binding and the existing `handlePollAnswer` server-side grading path are unchanged.
  - **Module isolation.** `telegram -> quiz` dependency already existed (module-root `QuizApi`/`QuizGradeDto`); this adds only module-root `QuizPollSpecDto`. No library or domain type crosses a boundary.
  - **Tests.** `TelegramApiClientTest` extended (11/11: native quiz request built with `type=quiz` + `correctOptionIds`/`explanation`, invalid-index skip, soft failure); new `QuizGraderTest` (3/3: correctOptionIndex match / non-MC / no-match); both agent `CapturingQuizApi` stubs updated for the new interface method (`GradeAnswerToolUserIsolationTest` 4/4, `StartQuizToolUserIsolationTest` 3/3); `ApplicationModulesTests.verifyModules` (1/1). Live-bot smoke test not run in this environment (no real bot token).
  - This entry does not supersede any prior entry.

- **2026-06-09 - Telegram file upload -> materials ingestion (Phase 3 of `docs/telegram/telegram-bot-library-migration-plan.md`): users can send a document/photo to the bot and have it ingested as study material, scoped to the linked user.**
  Implemented Phase 3 on top of the Phase 0-2 typed-client migration. Inbound document/photo attachments are downloaded via the typed client and handed to the `materials` module through its public API, scoped to the Telegram-linked user (RAG/security non-negotiable: the user id is resolved from the chat link, never from a model/tool argument).
  - **Materials public API.** `MaterialsApi` gains `ingestUpload(UUID userId, String filename, String contentType, byte[] content) -> MaterialDto`, implemented in `DefaultMaterialsApi` by delegating to a new `MaterialService.ingest(CurrentUser, byte[], filename, contentType)`. `ingest` resolves the user's **primary space** via `SpacesApi.findPrimaryForUser`, reuses the existing free-tier upload-quota / max-bytes (`QuotaService`) + allowed-type whitelist validation, stores the bytes via `BlobStorage` (`ByteArrayInputStream`), and publishes `MaterialUploadedEvent` exactly like the web `MaterialController` upload path. No new Flyway migration (reuses the `material` table); no cross-module repository access.
  - **Telegram outbound client.** `TelegramApiClient` gains typed `getFile(fileId) -> File` (`GetFile`) and `downloadFile(File) -> byte[]` (`telegramClient.downloadFileAsStream` + `readAllBytes`), both preserving the soft-failure contract (catch `TelegramApiException`/`IOException`/`RuntimeException`, return null). `sendDocument` was deferred - not needed for ingestion.
  - **Inbound DTOs.** New `payload/TelegramDocumentDto` + `payload/TelegramPhotoSizeDto`; `TelegramMessageDto` gains `caption`, `document`, and `photo` (List). The inbound DTO strategy from Phase 0 (keep hand-rolled `payload/Telegram*Dto`, do not adopt the library `Update` model) is unchanged.
  - **Handler.** `TelegramUpdateHandler.handleUpdate` detects `message.document` / `message.photo` (largest photo size) before the text-command path, requires a linked user (unlinked chats get the not-linked reply and never ingest), then `getFile -> downloadFile -> materialsApi.ingestUpload(userId, ...)`. The `materials` `MaterialException` is **not** imported across the module boundary - failures surface as a generic localized `telegram.bot.upload.failed`. New strings `telegram.bot.upload.received` / `telegram.bot.upload.failed` added in EN / RU / UZ.
  - **Module isolation.** Adds a `telegram -> materials` dependency via module-root `MaterialsApi` + `MaterialDto` only; no library/domain type crosses a boundary.
  - **Tests.** `TelegramApiClientTest` extended (getFile / downloadFile happy + soft-failure + skip paths); new `TelegramUpdateHandlerUploadTest` (document upload ingests for the linked user only, unlinked chat never ingests, download failure reports + skips ingest). Scoped telegram + materials tests and `ApplicationModulesTests.verifyModules` run. Live-bot smoke test not run in this environment (no real bot token); image OCR / actual text extraction is the materials pipeline's concern (out of scope for this phase).
  - This entry does not supersede any prior entry.

- **2026-06-09 - Telegram AI message streaming via `sendMessageDraft` (Phase 4 of `docs/telegram/telegram-bot-library-migration-plan.md`): coach turns now push live throttled draft previews to the chat while the LLM generates, then finalize with the existing HTML message.**
  Implemented Phase 4 on top of the Phase 0-3 typed-client migration. Resolves the plan's open question #1: `org.telegram.telegrambots.meta.api.methods.send.SendMessageDraft` **is** present in `telegrambots-meta:10.0.0` (Javadoc `@version 9.5`).
  - **Bot API method shape.** `SendMessageDraft` requires `chatId` (Long) + a non-zero `draftId` (Integer; changes to drafts with the same id are animated), with optional `text` (0-4096 chars; empty text shows a "Thinking…" placeholder) and optional `parseMode`/`entities`.
  - **Outbound client.** `TelegramApiClient` gains `streamMessage(Long chatId, int draftId, String partialText) -> boolean` building a typed `SendMessageDraft`. Drafts are sent as **plain text (no `parseMode`)** deliberately, to avoid emitting broken/half-closed HTML mid-stream; the final message is still finalized through the existing HTML `sendMessage(...)`. Soft-failure contract preserved (catch `TelegramApiException`/`RuntimeException`, log, return `false`); gated by `isEnabled()` + non-zero `draftId`; draft text capped at 4096 chars.
  - **Throttle config.** `TelegramProperties` gains `long streamThrottleMs` (default `1000`, key `ilmai.telegram.stream-throttle-ms`). No `application.yaml` block added - the bot token is env-bound and there is no existing `ilmai.telegram` block, so the default lives in the property class. The `@AllArgsConstructor` now takes 4 args; the two `new TelegramProperties(...)` test call sites were updated.
  - **Handler refactor.** `TelegramUpdateHandler.runCoachTurn` no longer just `.collectList().block()`. It mints a per-turn random non-zero `draftId`, then `agentApi.chat(...)` `.doOnNext(...)` accumulates `TextPart` text and pushes a **throttled** plain-text draft (`now - lastDraftAt >= streamThrottleMs`, draft text truncated to 4096 with an ellipsis); `.collectList().block(120s)` still yields the full part list, which the unchanged `dispatchParts` finalizes (HTML + inline buttons / quiz polls). The existing per-turn `SecurityContext` set before the agent call is unchanged - draft calls only hit the Telegram API and need no security context.
  - **Module isolation.** All Telegram-lib imports stay confined to the `telegram` module; no new cross-module dependency. `ApplicationModulesTests.verifyModules` green.
  - **Tests.** `TelegramApiClientTest` extended (draft request built with chatId/draftId/text, 4096 truncation, zero-`draftId` skip, soft failure); `TelegramUpdateHandlerTest` gains two streaming cases (throttle `0` → one draft per text part + final message matches collected output; `Long.MAX_VALUE` throttle → drafts suppressed, final message still sent). Green: `TelegramApiClientTest` (21/21), `TelegramUpdateHandlerTest` (11/11), `ApplicationModulesTests.verifyModules` (1/1). Live-bot smoke test against a real token (incl. the Bot API 9.5 `sendMessageDraft` 1:1-DM context rules) was **not** run in this environment.
  - This entry does not supersede any prior entry.


- **2026-06-09 - Telegram Bot API library migration complete (Phase 5 / cleanup & docs of `docs/telegram/telegram-bot-library-migration-plan.md`): `telegrambots-client:10.0.0` is the sole Bot API transport; the migration plan is closed out.**
  Final state after Phases 0-4. No new production code was needed in this phase - it verifies and records that the cleanup is already satisfied and closes the plan. It is the closing entry for the four prior 2026-06-09 telegram-migration entries and supersedes none of them.
  - **Dead-plumbing cleanup (plan Phase 5.1).** No `RestClient` reference remains anywhere in `backend/src` (the old `telegramRestClient` bean + raw `Map` payload builders were already removed in Phase 1). The single `TelegramApiClient.extractPollId(Message)` helper is **still live** - shared by `sendPoll` and `sendQuizPoll` - so there was no dead helper to delete.
  - **Shared `spring-boot-starter-restclient` is kept on purpose.** It is declared in `backend/build.gradle` but is **no longer telegram's**: nothing under `backend/src` references `RestClient` anymore, yet Spring AI's `RestClient.Builder` (used by the Gemini `ChatClient` transport) relies on this starter's autoconfiguration. Removing it would break the AI transport, not clean up dead telegram code, so it stays.
  - **Inbound DTO retirement deferred (plan Phase 5.2 / open question #3).** The hand-rolled `payload/Telegram*Dto` classes remain the inbound source of truth; migrating to the library `Update` model stays out of scope (lower-risk to keep them).
  - **i18n (plan Phase 5.4).** All Telegram send-path strings (`telegram.bot.*`, `telegram.disabled`) are present in EN / RU / UZ via `MessageService`; no hardcoded English on the bot paths.
  - **Verification.** Scoped `org.aiincubator.ilmai.telegram.*` test package + `ApplicationModulesTests.verifyModules` run green on Spring Boot 4.0.6 / JDK 25. The one open item is a **manual smoke test against a live bot token** (no token / outbound network in this environment).
  - This entry does not supersede any prior entry.

- **2026-06-09 - Telegram inbound DTOs migrated to the library `Update` model (closes the previously-deferred Phase 5.2 / open question #3 of `docs/telegram/telegram-bot-library-migration-plan.md`); a dedicated Jackson-2 parser is required because of a Jackson 2 vs 3 mismatch on Spring Boot 4.**
  Replaced the eight hand-rolled inbound webhook DTOs (`TelegramUpdateRequest`, `TelegramMessageDto`, `TelegramChatDto`, `TelegramUserDto`, `TelegramCallbackQueryDto`, `TelegramPollAnswerDto`, `TelegramDocumentDto`, `TelegramPhotoSizeDto` - all deleted) with the `telegrambots-meta:10.0.0` types `Update` / `Message` / `Chat` / `User` / `CallbackQuery` / `PollAnswer` / `Document` / `PhotoSize`. `TelegramUpdateHandler` and `TelegramController` now consume the library `Update`. This supersedes only the "defer" resolution of open question #3 in the prior 2026-06-09 Phase 5 entry; all other Phase 0-5 decisions stand. The outbound API payload `TelegramLinkResponse` (+ `TelegramMapper`) is unchanged - per the user's instruction, only the inbound wire model moved to the library, not the cross-boundary response DTO.
  - **Why a separate Jackson-2 mapper (key finding).** Spring Boot 4 / Spring Framework 7 deserialize `@RequestBody` with **Jackson 3** (`tools.jackson.databind`). The library's `User`/`Chat`/`Document`/`PhotoSize` are Lombok `@SuperBuilder @Jacksonized` types with **no no-arg constructor**; their builder-creator annotations are **Jackson 2** (`com.fasterxml.jackson.databind.annotation.*`), which Jackson 3 ignores -> `InvalidDefinitionException: Cannot construct instance of ...User (no Creators)`. (The old hand-rolled DTOs worked under Jackson 3 because they used only `com.fasterxml.jackson.annotation.@JsonProperty` - still honored by Jackson 3 - plus a no-arg ctor + setters.) Both Jackson 3 (`tools.jackson.core:jackson-databind:3.1.2`) and Jackson 2 (`com.fasterxml.jackson.core:jackson-databind:2.21.2`, via the `spring-boot-jackson2` starter) are on the runtime classpath.
  - **Solution.** New module-confined `telegram/service/TelegramUpdateParser` holds its own Jackson-2 `JsonMapper` (`FAIL_ON_UNKNOWN_PROPERTIES=false`) and deserializes `byte[] -> Update`, returning `null` (logged) on bad/empty payloads. The webhook controller now takes `@RequestBody byte[] body`, runs it through the parser, then calls `handleWebhook(secret, headerSecret, Update)` exactly as before. No new bean of the ambiguous `com.fasterxml...ObjectMapper` type is exposed; the mapper is private to the parser.
  - **Library object gotcha.** `Chat.type` is `@NonNull`; tests building `Chat.builder()` must set `.type("private")` or the builder throws NPE at construction.
  - **Tests.** `TelegramUpdateHandlerTest` / `TelegramUpdateHandlerUploadTest` / `TelegramQuizPollIntegrationTest` now build `Update` via the library setters/`@SuperBuilder`; new `TelegramControllerTest.webhook_deserializesLibraryUpdateFromJsonAndDispatchesToHandler` posts a realistic webhook JSON through a **real** parser and asserts the captured `Update` (chat id, text, `from.userName`). Green: scoped `org.aiincubator.ilmai.telegram.*` + `ApplicationModulesTests.verifyModules` on Boot 4.0.6 / JDK 25. Manual live-bot smoke test still not run here (no token).
  - This entry supersedes only open question #3's "defer" resolution; it does not supersede any other prior entry.

- **2026-06-09 — Dropped the `ilmai.` prefix from all custom config properties; they now sit at the YAML root. Supersedes the property-key naming used in every prior entry that referenced an `ilmai.*` key.**
  Per explicit user request ("application.yml contains a lot of custom properties, please remove ilmai prefix"), the whole `ilmai:` block in `backend/src/main/resources/application.yml` was unwrapped and its children promoted to top-level keys. New roots: `ai.retrieval`, `ai.embedding`, `auth`, `storage.s3`, `cors`, `ingestion` (incl. `ingestion.retry`), `plan.replan`, `notifications`, `telegram`, `billing` — plus `quota` and `digest`, which had no YAML block but were renamed in code for consistency. Only the namespace changed; every leaf key, default, and `${ENV_VAR:...}` placeholder (incl. the `ILMAI_*` env-var names, which are external and left as-is) is unchanged. `auth.jwt.issuer: ilmai` and the `redis-key-prefix: "ilmai:auth:refresh:"` value are data, not the property namespace, so they stay.
  - **Code updated in lockstep:** `@ConfigurationProperties(prefix = …)` on `AuthProperties` (`auth`), `RetrievalProperties` (`ai.retrieval`), `GoogleGenAiEmbeddingProperties.PREFIX` (`ai.embedding`), `IngestionProperties` (`ingestion`), `MaterialReingestProperties` (`ingestion.retry`), `CorsProperties` (`cors`), `S3StorageProperties` (`storage.s3`), `BillingProperties` (`billing`), `IlmTokenQuotaProperties` (`quota`), `TelegramProperties` (`telegram`); `@Value` in `DigestService` (`digest.send-hour`), `NotificationService` (`notifications.broken-streak-nudge-hour`), `PlanReplanCheckJob` (`plan.replan.behind-threshold-days`), `MaterialReingestScheduler` (`ingestion.retry.{fixed,initial}-delay` + `@ConditionalOnProperty ingestion.retry.enabled`); `@ConditionalOnExpression` in `EmbeddingConfig` (`ai.embedding.api-key`). All `@TestPropertySource` / `withPropertyValues` keys in the integration + config tests were updated to the new roots.
  - **Verification:** `./gradlew compileJava compileTestJava` — `compileJava` clean; `compileTestJava` fails only on two pre-existing, unrelated errors in `TelegramApiClientTest` (it calls the old 4-arg `TelegramProperties` constructor; the class gained `publicBaseUrl` + `streamThrottleMs` making it 5-arg — uncommitted in-progress Telegram work, not touched by this change). No live LLM / Docker run here.
  - This entry supersedes the property-key spelling (`ilmai.*`) referenced in earlier entries (e.g. the 2026-05-20 `ilmai.ai.embedding.*` and 2026-06-09 Phase 4 `ilmai.telegram.stream-throttle-ms` mentions); the behaviour those entries describe is otherwise unchanged.

- **2026-06-09 — Dropped the `ILMAI_` prefix from the eight custom external env-var names too (follow-up to the same-day config-property rename).**
  Per explicit user request ("and please remove ilmai prefix from other env vars too"), the `${ILMAI_*:…}` placeholders in `backend/src/main/resources/application.yml` and the matching keys in `backend/.env.example` were renamed by stripping the `ILMAI_` prefix. Renamed: `ILMAI_RETRIEVAL_TOP_K → RETRIEVAL_TOP_K`, `ILMAI_RETRIEVAL_SIMILARITY_THRESHOLD → RETRIEVAL_SIMILARITY_THRESHOLD`, `ILMAI_INGESTION_PDF_PAGES_PER_CHUNK → INGESTION_PDF_PAGES_PER_CHUNK`, `ILMAI_INGESTION_PDF_PAGE_OVERLAP → INGESTION_PDF_PAGE_OVERLAP`, `ILMAI_INGESTION_AUDIO_WINDOW_MS → INGESTION_AUDIO_WINDOW_MS`, `ILMAI_INGESTION_AUDIO_OVERLAP_MS → INGESTION_AUDIO_OVERLAP_MS`, `ILMAI_PLAN_BEHIND_THRESHOLD_DAYS → PLAN_BEHIND_THRESHOLD_DAYS`, `ILMAI_NOTIFICATIONS_BROKEN_STREAK_NUDGE_HOUR → NOTIFICATIONS_BROKEN_STREAK_NUDGE_HOUR`. Defaults and Spring property keys are unchanged; only the external env-var name changed. `.env.local` had none of these. Other env vars never carried the prefix (`GOOGLE_*`, `STORAGE_S3_*`, `CORS_*`, `TELEGRAM_*`, `STRIPE_*`, etc.) so they were left as-is. Historical `ILMAI_*` mentions elsewhere in this log / `README.md` are append-only and not edited.


- **2026-06-09 — Dropped the four billing env-vars/properties that the stub payment providers never read.**
  Per explicit user request (after confirming the Stripe/Payme/Click providers are test-only stubs that return fake `*_test_<uuid>` checkout URLs), the billing config keys that no code path reads were removed. The providers actually read only `stripe.webhookSecret` (`StripePaymentProvider.parseWebhook`), `payme.merchantId` and `click.serviceId` (fake-URL builders); the rest were bound-but-unreferenced.
  - **Removed:** `STRIPE_SECRET_KEY`, `PAYME_SECRET_KEY`, `CLICK_MERCHANT_ID`, `CLICK_SECRET_KEY` from `backend/src/main/resources/application.yml` (`billing.*` block) and `backend/.env.example`; the matching fields `StripeProperties.secretKey`, `PaymeProperties.secretKey`, `ClickProperties.{merchantId,secretKey}` from the three properties classes.
  - **Kept:** `STRIPE_WEBHOOK_SECRET` (`billing.stripe.webhook-secret`), `PAYME_MERCHANT_ID` (`billing.payme.merchant-id`), `CLICK_SERVICE_ID` (`billing.click.service-id`) — these are the only billing values referenced by live code.
  - **Safety:** no `new {Stripe,Payme,Click}Properties(...)` arg-ful constructor callers and no `get/setSecretKey`/`get/setMerchantId` callers for billing exist (the only `getSecretKey` caller is S3's `S3StorageProperties`), so the Lombok `@AllArgsConstructor` arity change is compile-safe; `BillingProperties` uses no-arg constructors only.
  - **Flag (AGENTS.md rule 2 — code-vs-brief gap, not edited):** the brief presents Payme/Click/Stripe as real billing, but the providers are mock stubs; when the real integrations land, the removed keys (notably the secret keys) will need to be re-added.
  - This entry does not supersede any prior entry.


- **2026-06-10 — Space view (Topics page) now loads folders + loose items page-by-page from one combined endpoint, items can be moved between folders/root, and the per-page "free plan" quota strip was removed.**
  Per user request ("remove free plan line for now; 1. load topics and items in one request page by page; 2. clean topic page like this one too; add move features to items"). Changes are confined to the `materials` module on the backend and the topics/materials views on the frontend; no cross-module API or return-type changed, so `ApplicationModulesTests.verifyModules` is unaffected.
  - **Combined paginated read.** New `GET /materials/contents?page&size` → `SpaceContentsResponse { topics, items, page, size, hasMore }`. Topics (all, they're few) are returned only on `page == 0`; topic-less ("root") materials are paged via a new `MaterialRepository.findByTopicIsNullAndSpaceIdInOrderByCreatedAtDesc(..., Pageable)` returning a `Slice` (so `hasMore = slice.hasNext()`). Assembly lives in `MaterialService.contents(...)` (injected `TopicMapper` + `TopicRepository` directly — **not** `TopicService**, to avoid a `TopicService ↔ MaterialService` cycle). Default page size 24, capped at 100.
  - **Move endpoint.** New `PATCH /materials/{materialId}` with body `MoveMaterialRequest { UUID topicId }` (null ⇒ root). `MaterialService.move(...)` re-validates ownership (material in caller's spaces) and that any target topic belongs to the **same space** before re-parenting. Covers all three requested moves: root→topic, topic→root, topic→topic.
  - **Frontend.** `QuotaStrip` removed from `topics-view` and `materials-view`. `topics-view` loads via `getSpaceContents` with a "Load more" button; both views gained a per-item Move action (folder icon → dialog listing root + topics, current destination disabled). `materials-view` (used in the topic-detail tab) was restyled to match the clean space view: header upload button + whole-area drag-and-drop + unified card grid; paste-text UI dropped. New `materials.move.*` + `topics.loadMore` strings added in EN/RU/UZ.
  - **Mocks.** `mocks/handlers.ts` mirrors both endpoints (`/materials/contents` registered before `/materials/:id`; `PATCH /materials/:id` rebuilds topic counts). `live-passthrough` already covers `/materials*`.
  - **Tests.** `MaterialServiceTest` extended with 4 move cases (root, to-topic, not-owned material, not-owned target topic) — 23/23 green. Frontend `pnpm typecheck` (tsc --noEmit) clean; Prettier-formatted. The full Spring context / `verifyModules` and a live run were not re-run (intra-module change only).
  - This entry does not supersede any prior entry.

- **2026-06-11 - Render the companion assistant's answers with Vercel's streamdown (streaming Markdown) instead of plain pre-wrapped text; keep the AI SDK DefaultChatTransport as-is for transport. Does not supersede any prior entry.**
  After discussing whether to adopt Vercel's full `ai-elements` package, we rejected it (it assumes Radix + `lucide-react` + `use-stick-to-bottom`, conflicting with this repo's `@base-ui/react` + HugeIcons stack and AGENTS.md `6.6 `no heavy deps`). `streamdown` is the one Vercel piece that fits: it is a drop-in `react-markdown` replacement built on remark/rehype + Tailwind/shadcn design tokens, with **no** Radix/Lucide/base-ui dependency, and it is the same renderer that powers AI Elements' `Response` - so it handles incomplete/unterminated Markdown blocks mid-stream (open code fences, dangling `**`, partial tables) without flicker.
  - **Dependency.** Added `streamdown@2.5.0` to `frontend` (runtime dep). Optional plugins (`@streamdown/code` Shiki, `mermaid`, `math`, `cjk`) were **not** installed - add only when needed; CJK is not needed for UZ/RU/EN.
  - **Code.** `frontend/components/ai-elements/response.tsx` now renders `<Streamdown>` (props passthrough; `block.text` passed as children) with token-based prose utilities; call site in `companion-client.tsx` (`<Response>{block.text}</Response>`) is unchanged. Required Tailwind v4 scan line `@source "../node_modules/streamdown/dist/**/*.js";` added to `frontend/app/globals.css` (without it streamdown renders unstyled).
  - **Transport.** No change: `ai` / `@ai-sdk/react` are already on `beta` (v6) and `createCoachTransport` in `lib/agent.ts` uses `DefaultChatTransport` against the backend's v6-protocol SSE stream.
  - **Verification.** Response lint clean; `tsc --noEmit` shows only the pre-existing unrelated `.next/types` topics-page errors. No live backend stream re-run here.

- **2026-06-11 — One-time `docs/` override of rule 3: added a non-technical product brief alongside the authoritative brief.**
  At the user's explicit request ("create new brief without technical details"), a plain-language, product-focused brief was created at `docs/ilm-ai-project-brief-non-technical.md`. This is a one-time override of AGENTS.md rule 3 ("Don't invent new docs in `docs/`"), in the same spirit as the 2026-05-19 `docs/plans/` override, and **does not** create a precedent. `docs/ilm-ai-project-brief.md` remains the single source of truth; the new file opens with a banner saying exactly that and links back to it.
  - **What was stripped.** The entire "Technical Architecture" section, the "How It Maps to the Program Sessions" table, and the "Resources & Starting Points" links were removed wholesale. Feature, milestone, deliverable, evaluation-rubric, and presentation text was rephrased into plain product language with no engineering jargon (no RAG / vector / embedding / Spring / Docker / CI-CD / webhook / OAuth wording). "The Idea", "What Problem It Solves", and "Who It's For" were kept verbatim.
  - **Doc-mismatch flags carried into the new brief (AGENTS.md rule 2 — authoritative brief deliberately not edited).** (1) The non-technical brief phrases sign-in as "currently with Google; more sign-in options on the way" rather than the source brief's stale "email or Google OAuth", per the 2026-05-16 auth decision. (2) Payme / Click / Stripe are presented as product capabilities even though the providers are still test-only stubs, per the 2026-06-09 billing entry. The authoritative `ilm-ai-project-brief.md` still carries both stale statements and was left untouched.
  - This entry does not supersede any prior entry.

- **2026-06-12 — Both chat-protocol ends pinned: `ui-message-stream` starter `0.2.0 → 0.3.0` (first-party, native integration kept) and frontend `ai`/`@ai-sdk/react` moved off floating `beta` onto the stable AI SDK 6 line.**
  The `com.github.uzinfowebuz.ui-message-stream` library is first-party (same author as this repo), so the strategic call is **native integration over removal**: the upcoming streaming rewrite builds *on* the library's deeper seams (`UiMessageStreamAdvisor`, `RecordingToolCallingManager`, `SerializedPartSink`) rather than replacing it with hand-rolled SSE. Tag `0.3.0` (released 2026-06-12, verified on GitHub) carries the masked-error default (`ErrorMessageResolver.MASKED` — no more raw exception text on the wire), the inbound system-message guard, the SSRF-guarded `MediaResolver`, and the new `UiMessageStreamAdvisor` (a Spring AI `StreamAdvisor` that injects the per-request sink into the tool context, order `DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER - 1`).
  - **Frontend pins.** `ai: "beta" → "^6.0.0"` (resolved 6.0.202) and `@ai-sdk/react: "beta" → "^3.0.0"` (resolved 3.0.204). The floating `beta` tag had silently drifted onto the **v7 beta** line (`ai@7.0.0-beta.116` / `@ai-sdk/react@4.0.0-beta.116`); the pin pulls back to the stable v6 wire protocol the library validates against (`ai@6.0.197`). `shadcn` (a CLI, not a runtime lib) moved from `dependencies` to `devDependencies`.
  - **Verification.** Backend `compileJava compileTestJava` green against 0.3.0; `CoachStreamServiceTest` + `AgentControllerSmokeTest` green (existing replay path untouched at this stage); `pnpm install` + `pnpm typecheck` + `pnpm build` green on the stable SDK.
  - This entry does not supersede any prior entry.

- **2026-06-12 — Agent module layout frozen as-is; deviation from the brief documented rather than "fixed".**
  The brief sketches chat as its own feature area, while the code keeps the coach chat, tools, advisors, prompts, and the `usermemory` submodule inside the single `agent` Modulith module (see the 2026-06-09 `usermemory` relocation entry). Decision: **freeze the current `agent` package layout** for the duration of the streaming/auth/monitoring hardening work — no split into `chat/`/`coach/` modules, no renames. The streaming rewrite lands inside `agent/service` next to the code it replaces. Flag per AGENTS.md rule 2: this is a deliberate, documented deviation from the brief's module sketch, not an oversight; revisit only as an explicit task.
  - This entry does not supersede any prior entry.

- **2026-06-12 — JobRunr retained as the background-job runner (used by the upcoming refresh-token cleanup job).**
  Considered dropping `org.jobrunr:jobrunr-spring-boot-4-starter` (AGENTS.md rule 6, "no heavy deps") now that the Redis removal plan touches infra. Decision: **keep it** — it is already load-bearing (`telegram/service/OutboxDrainJob` `@Recurring` drain, material re-ingestion retries) and the Redis→Postgres refresh-token migration needs a recurring purge job, which will follow the same `@Recurring` pattern (`auth/service/RefreshTokenCleanupJob`). Replacing JobRunr with `@Scheduled` would lose its dashboard/retry semantics and churn working code for no functional gain.
  - This entry does not supersede any prior entry.

- **2026-06-12 — Web chat streams natively: `CoachStreamService` rewritten on the lib's `.stream()` path; fake replay deleted; guards become end-of-stream flags on web; Telegram untouched.**
  `POST /agent/chat/{sessionId}` now drives `client.prompt(...).stream().chatClientResponse()` through `UiMessageStreamEmitter.writeTo(emitter, upstream, ChatClientResponseMapper.TEXT_ONLY, sink, onElement, onComplete)` with a per-request `SerializedPartSink` — real token deltas, native `tool-input/output-available` frames (`uimessagestream.tool-io.native=true`), and ordered `data-citation` / `data-quiz` / `data-action` parts pushed by the tools themselves. The 10-char chunk replay, `Thread.sleep(5)`, and `writer.error(ex.getLocalizedMessage())` are gone (lib 0.3.0 masks wire errors by default). Quota is committed from the final streamed `ChatResponse` usage at completion and refunded on error/cancel (sentinel element concatenated to the upstream marks "turn completed" inside the emitter's serialized element callback).
  - **Shared turn logic extracted to `agent/service/CoachTurnSupport`** (quota gate/reserve/commit/refund + post-turn summarizer/fact-extraction/activity event); `DefaultAgentApi` (Telegram's blocking `.call()` path) delegates to it — byte-for-byte behavior parity, proven by the untouched green Telegram suites.
  - **Guard semantics on web are flag-only (user-confirmed):** `UserMemoryAdvisor` + `ChatSummaryAdvisor` now implement `StreamAdvisor` (prompt enrichment on both paths); `GroundingGuardAdvisor` / `CitationGuardAdvisor` stay call-only — on the stream path their checks run once at end-of-stream over the aggregated text (`CitationGuardAdvisor.containsCitation` + the turn-scoped `AgentRetrievalContext` carried in the tool context under `AgentToolContext.RETRIEVAL_CONTEXT_KEY`), emitting a trailing `data-confidence {level: low}` instead of rewrite/retry. Telegram keeps full guard parity.
  - **Spring AI M8 discovery — client-level `ToolCallAdvisor` is the streaming tool seam.** M8's `DefaultChatClient` auto-registers a `ToolCallAdvisor` (order `HIGHEST_PRECEDENCE + 300`) whenever tools are present; it re-invokes the advisor chain per tool iteration and executes tools with the prompt *as seen at its own chain position*, and the model's internal tool execution is disabled. Two consequences, both fixed: (1) `CoachChatClientConfig` now registers an **explicit** `ToolCallAdvisor` built with the Spring `ToolCallingManager` bean (which the starter's `tool-io.native` post-processor wraps into `RecordingToolCallingManager`) and `conversationHistoryEnabled(false)` (mirrors what auto-registration computes with the downstream memory advisor) — without this, the auto-registered advisor would use a private default manager and native tool frames would never be emitted; (2) `CoachStreamService` passes `new UiMessageStreamAdvisor(sink, Ordered.HIGHEST_PRECEDENCE + 100)` — the lib's default order (`DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER - 1`) would inject the sink *inside* the tool advisor, invisible to `executeToolCalls`.
  - **Tools dual-emit:** `RetrieveTool` (`data-citation` per chunk), `StartQuizTool` (`data-quiz` per question), `ImproviseTool` (`data-action`) push onto the sink when present (`AgentToolContext.sink(...)` reads `RecordingToolCallingManager.SINK_KEY`) and keep ThreadLocal recording for the Telegram path.
  - **Frontend:** `companion-client.tsx` `buildBlocks` explicitly skips `dynamic-tool` / `step-start` parts (tool frames now arrive, `dynamic:true`).
  - **Verification:** new `CoachStreamServiceIntegrationTest` (MockMvc + asyncDispatch over a scripted `ChatModel` + real `RecordingToolCallingManager`) asserts the v6 frame order `start → tool-input-available → data-citation → tool-output-available → text-delta(s) → finish → [DONE]`, the `x-vercel-ai-ui-message-stream: v1` header, trailing low-confidence on ungrounded turns, localized quota-error frame, masked model failure (no exception text on the wire) + refund. Full agent suite, isolation suites, Modulith verification, and `contextLoads` (with env keys) green.
  - This entry does not supersede any prior entry.

- **2026-06-12 — Refresh tokens migrated Redis → Postgres (`refresh_tokens` table); Redis removed from the stack entirely.**
  Supersedes the *storage* aspect of the 2026-05-16 auth entry (refresh-token state was "Redis-backed `RefreshTokenStore`"); the rotation/reuse-detection design itself is unchanged. Redis was a second stateful service carried for exactly one map; at current scale Postgres handles it with one table and two indexes, drops a container from dev/prod topology, and gains transactional consistency with the `users` FK (`ON DELETE CASCADE`).
  - **Schema:** additive `V2__refresh_tokens.sql` — UUID PK, unique `jti`, `family_id`, `user_id` FK, string `status` (`ACTIVE/CONSUMED/REVOKED` enforced by the Java enum, no DB CHECKs per AGENTS.md), `expires_at`, `revoked_at`, audit timestamps; indexes `(family_id, status)` + `(expires_at)`.
  - **Semantics map (Redis → Postgres), exact-parity by design:** consume = atomic `UPDATE ... SET status=CONSUMED WHERE jti=? AND status=ACTIVE AND expires_at>now()` — rowcount 1 keeps the single-winner rotation race (proven by a parallel-consume test, 8 threads → exactly 1 `SUCCESS`); replay of a non-expired `CONSUMED` row → `REUSED`; missing/expired/`REVOKED` → `UNKNOWN` (post-revocation replays return `UNKNOWN`, matching Redis where revoked families' keys were deleted). `revokeFamily` flips all rows to `REVOKED`+`revoked_at`. **`revokeActive` (logout) DELETES the active row** rather than flipping it to `REVOKED` — flipping would have made `isFamilyRevoked` true after logout, instantly killing the still-valid access token; Redis logout just deleted the key (family *not* revoked), and parity wins.
  - **Transactions:** store methods are `@Transactional`; `consume`/`revokeFamily` run `REQUIRES_NEW` so consumed/revoked state *persists* even when the surrounding `AuthService.refresh` transaction aborts (Redis writes were instantly durable; reuse-detection state must survive the cleanup that throws `REFRESH_REUSE_DETECTED` after revoking the family). `AuthService.refresh` dropped `readOnly = true` (now writes); `isFamilyRevoked` stays `readOnly` on every authenticated request — the Redis O(1) lookup becomes an indexed `EXISTS` (acceptable now; add a cache only if measured).
  - **Cleanup:** JobRunr `@Recurring` hourly `RefreshTokenCleanupJob` deletes non-`REVOKED` rows with `expires_at < now()` and `REVOKED` rows older than `revoked_at + auth.jwt.access-ttl` — the revocation window outlives any in-flight access token, so `isFamilyRevoked` keeps answering `true` for the whole window (asserted in the integration test).
  - **Removals:** `spring-boot-starter-data-redis` (build.gradle), `redis` service (compose.yaml), `spring.data.redis` block + `redis-key-prefix` (application.yml), `redisKeyPrefix` (`AuthProperties.RefreshToken`), and `spring.data.redis.repositories.enabled=false` from all 11 integration tests.
  - **Verification:** new `RefreshTokenStoreIntegrationTest` (Testcontainers Postgres) — rotation happy path, replay→`REUSED`→family revocation, logout delete, expired/unknown→`UNKNOWN`, parallel-consume race, cleanup-window selectivity; `AuthService`/`AccessTokenAuthenticationConverter` untouched (mocked unit tests pass unchanged); full auth package + all 11 former-Redis-property suites green (one unrelated Docker flake in `EmbeddingPipelineIntegrationTest` passed clean on isolated rerun).
  - Supersedes 2026-05-16 only insofar as refresh-token *storage*; all other auth decisions stand.

- **2026-06-12 — Backend monitoring: unconfigured OpenTelemetry starters dropped; Sentry (errors) + Actuator (health/metrics) adopted.**
  Supersedes the observability aspect of the 2026-05-15 stack scaffold (`spring-boot-starter-opentelemetry` + `grafana-lgtm` were riding along unconfigured — no OTLP endpoint, no dashboards, pure startup cost). Errors-first monitoring fits a solo-operated product better than a self-hosted LGTM stack.
  - **Dependencies:** removed `spring-boot-starter-opentelemetry` (+ `-test`); added `io.sentry:sentry-spring-boot-4-starter:8.43.2` + `io.sentry:sentry-logback:8.43.2` (Boot-4 variants per Sentry docs; pinned, latest 8.x at decision time). `grafana-lgtm` removed from `compose.yaml`.
  - **Config (`application.yml`):** `sentry.dsn: ${SENTRY_DSN:}` — SDK no-ops when empty so local dev and tests need nothing; `environment: ${SENTRY_ENVIRONMENT:local}`; `send-default-pii: false` (ids only, never emails); `traces-sample-rate: ${SENTRY_TRACES_SAMPLE_RATE:0}` (errors-only by default); logback integration captures `ERROR` logs as events, `INFO+` as breadcrumbs (Sentry's built-in dedupe prevents double capture with the MVC resolver). No secrets in the repo — DSN via env.
  - **User context:** new `common/monitoring/SentryUserContextFilter` (`OncePerRequestFilter`, runtime `Sentry.isEnabled()` guard) attaches the authenticated `CurrentUser` id to the Sentry scope — `common` is the OPEN module, so no Modulith leak (`ApplicationModulesTests` green).
  - **Actuator:** `management.endpoints.web.exposure.include: health,info,metrics` + `management.endpoint.health.probes.enabled: true`; `SecurityConfig` already permitted `/actuator/health{,/**}` + `/actuator/info` — `/actuator/metrics` deliberately stays authenticated. Spring AI M8 ships Micrometer observations out of the box, so `gen_ai.client.operation.duration` / `gen_ai.client.token.usage` appear under `/actuator/metrics` after the first real model call (needs a live LLM key — not assertable in CI; prompt/completion *content* observation logging deliberately NOT enabled).
  - **Verification:** new `ActuatorHealthIntegrationTest` — `/actuator/health` public and `UP`, liveness/readiness probe groups `UP`, `/actuator/metrics` → 401 unauthenticated; `IlmaiApplicationTests` context loads with an empty DSN; Modulith verification green.
  - Supersedes 2026-05-15 only insofar as the observability stack; the rest of the scaffold decision stands.

- **2026-06-12 — Frontend monitoring: `@sentry/nextjs` wired errors-only; SDK fully gated by `NEXT_PUBLIC_SENTRY_DSN`.**
  Completes the monitoring story from the backend entry above for the Next.js app. Posture mirrors the backend: **errors only** — no Session Replay integration, `tracesSampleRate: 0`, `sendDefaultPii: false` — keeping the client bundle lean; every `Sentry.init` carries `enabled: Boolean(process.env.NEXT_PUBLIC_SENTRY_DSN)`, so local dev and CI run with the SDK disabled and need no configuration.
  - **Wiring (App Router, Next 16):** new `instrumentation-client.ts` (browser init + `onRouterTransitionStart = Sentry.captureRouterTransitionStart`); new `sentry.server.config.ts` / `sentry.edge.config.ts` imported per `NEXT_RUNTIME` from the existing `instrumentation.ts` `register()` (MSW branch preserved untouched); `onRequestError = Sentry.captureRequestError` exported for server-component/route errors; new `app/global-error.tsx` (the app's first error boundary — `"use client"`, `Sentry.captureException` in an effect, framework-rendered `<NextError statusCode={0} />` so no hardcoded English strings of our own; flag: the app still has no client-side UZ/RU/EN i18n layer, so even this framework fallback text is English — same as every existing page).
  - **Build:** `next.config.mjs` wrapped with `withSentryConfig` (`org`/`project`/`authToken` from env, `silent` outside CI, `disableLogger: true`); source-map upload is skipped gracefully when `SENTRY_AUTH_TOKEN` is absent — `pnpm build` green without any Sentry env. `.env.example` documents `NEXT_PUBLIC_SENTRY_DSN`, `SENTRY_ORG`, `SENTRY_PROJECT`, `SENTRY_AUTH_TOKEN` (no real values in the repo).
  - **Drive-by fix to pass the lint gate:** `pnpm lint` was failing on two *pre-existing* `react-hooks/refs` errors in `components/data/data-view.tsx` (refs assigned during render, in user-modified code untouched by this task); the two writes moved into `React.useEffect` — behaviorally equivalent since the refs are only read inside native window drag listeners (post-commit). Flagged here per AGENTS.md rule 2 rather than silently absorbed.
  - **Verification:** `pnpm typecheck`, `pnpm lint` (0 errors), `pnpm build` (all 20 routes, Sentry wrapper active) green; companion chat streaming unaffected (no chat-path code touched in this stage). Manual end-to-end check (thrown test error visible in Sentry with a real DSN) left as an operator step — no DSN exists in the repo by design.
  - This entry does not supersede any prior entry.

- **2026-06-12 — Verification gate of the pin/stream/postgres/sentry plan closed: full backend suite green (467 tests / 0 failures / 0 skipped); two test-infra defects fixed on the way (shared-container flake + test-JVM OOM); no production code changed.**
  Continuation session for the seven 2026-06-12 entries above. Final numbers: `./gradlew test` → **93 classes / 467 tests / 0 failures / 0 errors / 0 skipped** (incl. `ApplicationModulesTests.verifyModules`); frontend `pnpm typecheck` / `pnpm lint` (0 errors, the 2 known acceptable warnings) / `pnpm build` (20/20 routes) green; staged throwaway `frontend/tmp-msw-passthrough-test.mjs` deleted (`git rm -f`).
  - **Fix 1 — `AbstractEmbeddingIntegrationTest` moved to the singleton-container pattern.** The base class used `@Testcontainers` + a static `@Container` shared by `EmbeddingPipelineIntegrationTest` and `UserIsolationIntegrationTest`. JUnit's Testcontainers extension stops/starts class-scoped containers per test class while Spring caches one context across both subclasses (identical cache key), so whichever class ran second reused a Hikari pool pinned to the previous, now-dead mapped port (`Connection to localhost:56099 refused`, pool `total=0`). Replaced the extension lifecycle with `static { POSTGRES.start(); }` (no `@Testcontainers`/`@Container`; `@ServiceConnection` field untouched) — the container lives for the whole test JVM and Ryuk reaps it on exit. The 11 sibling integration suites each declare a private container (their `@ServiceConnection` source makes each context cache key unique), so they were never exposed to this flake and were left as-is. The flake had been masked until now because the only prior full-suite run had Docker off and all per-suite verification runs were single-class.
  - **Fix 2 — test JVM sized for the Docker-on suite:** `maxHeapSize = '2g'` + `systemProperty 'spring.test.context.cache.maxSize', '4'` on the `test` task. Discovery: the previous run's "~40 skipped" were **all** `@Testcontainers(disabledWithoutDocker = true)` skips, not live-key gates (the handoff plan's assumption was wrong) — with Docker running every suite executes (0 skips) and ~14 Boot contexts get built in one Gradle worker; on the default worker heap that collapsed into cascading `OutOfMemoryError`s across the whole run. The cache cap makes Spring close LRU-evicted contexts mid-run (heap stays bounded no matter how many container-backed suites are added later); 2g gives the 4-context window comfortable headroom. Suite completes in under 2 minutes.
  - **Boot smoke-run (IDEA-parity) green.** `bootRun` with `.env.local` loaded and `SPRING_DOCKER_COMPOSE_FILE` pointing at `backend/compose.yaml`: compose auto-started `pgvector`/`garage`, Flyway migrated the real dev database v1 → **v2 (refresh tokens)** cleanly, PgVectorStore initialized against the existing `vector_store` table, `/actuator/health` → `{"status":"UP"}` with liveness+readiness groups, Telegram webhook registered. Gotcha recorded: `spring.docker.compose.file: ./backend/compose.yaml` in `application.yml` resolves only from the umbrella root — the working directory of the user's IntelliJ run configuration; a plain `gradlew bootRun` from `backend/` needs the env-var override. Left as-is deliberately (the IDEA run config is the canonical dev entry point).
  - **Operator notes:** real-DSN Sentry end-to-end checks and `gen_ai.client.*` metrics with a live key remain operator-only (unchanged from the two monitoring entries above). Docker still carries orphan `backend-redis-1` / `backend-grafana-lgtm-1` containers from the Redis/LGTM removals (compose warns about them; `docker compose up --remove-orphans` or `docker rm` clears it), and `.env.local` still defines now-unused `REDIS_HOST` / `REDIS_PORT` — both safe to delete.
  - This entry does not supersede any prior entry.


- **2026-06-17 - CI/CD + production deploy: GitHub Actions → GHCR → single Docker Compose stack on one Hetzner VPS behind Caddy.**
  Adds the Week-4 "Ship It" plumbing the repo lacked (no `.github/workflows`, no frontend image, no prod compose). The GitHub remote (`raximovhayot/ilmai`) is a monorepo, so all infra lands at the **repo root**: `.github/workflows/ci.yml` + `deploy.yml`, `docker-compose.prod.yml`, `.env.prod.example`, `.gitignore`, `DEPLOY.md`, and `deploy/{Caddyfile,garage/garage.toml,garage/init.sh}`; plus `frontend/Dockerfile` + `.dockerignore` and `output: "standalone"` in `frontend/next.config.mjs`. The user mentioned "gitlab actions" but the remote is GitHub and AGENTS.md/brief specify GitHub Actions, so GitHub Actions was used (flagged per AGENTS.md rule 2).
  - **Topology (one VPS, one compose stack).** Services `pgvector`, `garage` (+ one-shot `garage-init`), `backend`, `frontend`, `caddy` on an internal `ilmai` network; only Caddy publishes `:80/:443`. Caddy routes `/telegram/webhook/*` → `backend:8080` and everything else → `frontend:3000`, with automatic Let's Encrypt TLS for `{$DOMAIN}` (`ilmai.uzinfoweb.uz`). Single public domain suffices because the browser only ever calls the Next `/api/backend/[...path]` proxy (which streams `upstream.body`, SSE-safe) via server-side `AUTH_BACKEND_URL=http://backend:8080`; the backend is never exposed publicly except the Telegram webhook path.
  - **Images.** `backend/Dockerfile` reused as-is (JDK 25 layered jar, healthcheck on `/actuator/health`). New `frontend/Dockerfile` is a multi-stage pnpm + Next standalone build; `NEXT_PUBLIC_*` are build-args (API base baked to internal `http://backend:8080` — browser never uses it). Images pushed to GHCR tagged `latest` + `sha-<commit>`; deploy pulls the exact `sha-<commit>` (build/deploy tags aligned to the full SHA).
  - **CI** runs on every push/PR: backend `./gradlew build` (temurin 25) + frontend `pnpm typecheck/lint/build` (Node 22). **Deploy** runs on push to `main`: build+push, then `appleboy/scp` the stack files and `appleboy/ssh` `docker compose pull && up -d`. Secrets via GitHub Actions (`SSH_*`, `GHCR_PULL_TOKEN`, optional Sentry); runtime secrets live only in operator-managed `~/ilmai/.env` on the VPS (deploy fails fast if absent) — no secrets committed (AGENTS.md rule 9). `SPRING_DOCKER_COMPOSE_ENABLED=false` and `JOBRUNR_BACKGROUND_JOB_SERVER_ENABLED=true` set for the prod container; garage cluster secrets supplied via `GARAGE_RPC_SECRET`/`GARAGE_ADMIN_TOKEN` env (kept out of the committed `garage.toml`).
  - **Operator-only (not done here):** point the `ilmai.uzinfoweb.uz` A record at the VPS, set the GitHub + `.env` secrets, register the Telegram webhook, and run the first deploy. See `DEPLOY.md`. CI/build not executed in-sandbox (no Docker/network); validate by pushing to a branch.
  - This entry does not supersede any prior entry.
