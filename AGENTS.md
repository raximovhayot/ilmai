# AGENTS.md — Ilm AI

Guide for AI coding agents (Junie, Claude Code, Copilot, Codex, etc.) working in
this repository. Read this file before making any change.

---

## 1. Source of truth

There is exactly **one** authoritative document in this repo:

- [`docs/ilm-ai-project-brief.md`](docs/ilm-ai-project-brief.md) — the product brief.

Everything else — the rest of `docs/` and the current source code — is
**scaffolding or aspirational planning** and **must not be trusted** as a
specification. When the brief contradicts any other doc or existing code, the
brief wins. Do **not** "fix" the brief to match the code. If you spot mismatches
in other docs, flag them in your response instead of silently propagating them.

---

## 2. What Ilm AI is

A **personal AI learning companion**. The user uploads their own material (PDFs,
Word docs, text, paste), and the app becomes a tutor for that material: it
chats, quizzes, detects knowledge gaps, generates a learning plan, and sends
reminders via Telegram. Every assistant answer is **grounded in the user's
uploaded content** and **cited** back to the source. Languages: **Uzbek,
Russian, English** — the companion replies in the language the user writes in.
Free tier vs Premium tier (Payme / Click in UZ, Stripe internationally).

Full feature list, target users, evaluation rubric and milestones live in the
brief.

---

## 3. Repository layout

```
IlmAI/
├── AGENTS.md     ← this file
├── docs/         ← brief is source of truth; everything else aspirational
├── backend/      ← Spring Boot / Java service (Gradle, pgvector, Spring AI)
└── frontend/     ← Next.js / React app (pnpm, Tailwind, shadcn on @base-ui/react)
```

For exact versions, dependencies and run scripts, read `backend/build.gradle`,
`backend/compose.yaml`, `backend/src/main/resources/application.yaml` and
`frontend/package.json` directly. Don't trust what other docs claim about the
stack — check the build files.

---

## 4. Coding conventions

Mirror the **current** code in the repo unless the brief or an explicit user
request says otherwise.

### Backend (Java)

- **Package:** `org.aiincubator.ilmai.*`.
- **Package-by-feature**, not by layer (e.g. `auth/`, `agent/`, future
  `materials/`, `chat/`, `quiz/`, `gaps/`, `plan/`, `telegram/`, `billing/`).
  Inside a feature, use sub-packages `domain/`, `api/`, `service/`.
- **Lombok** is used on entities/DTOs — keep it consistent with existing classes.
- **JPA entities:** `UUID` keys with
  `@UuidGenerator(algorithm = UuidVersion7Strategy.class)`, `OffsetDateTime`
  timestamps, Hibernate-proxy-safe `equals`/`hashCode`.
- **UUIDv7 for DB primary keys only.** Entity `@Id` fields use
  `@UuidGenerator(algorithm = UuidVersion7Strategy.class)` so primary keys
  are sortable, time-ordered, and play well with B-tree index locality —
  this is the *only* reason we care about UUID version. Other UUIDs the app
  mints (JWT `jti`s, refresh-token family ids, synthetic ids in tests, etc.)
  can use plain `UUID.randomUUID()`; the version doesn't matter outside the
  database. The brief is silent on UUID version — keep this rule here in
  AGENTS.md until / unless the brief revises it.
- **Schema is owned by Flyway** (`ddl-auto=validate`). Add new
  `V{N}__short_description.sql` files; **never edit an applied migration**, and
  **never bypass `validate`** by switching to `update`/`create`.
- **No DB-level `CHECK` constraints.** Don't write `CHECK (column IN (...))`
  or similar value constraints in DDL. Enforce them in Java instead — enum
  columns via `@Enumerated(EnumType.STRING)` + the Java enum, other invariants
  via Bean Validation or service-layer checks. A `CHECK` turns every domain
  rule change into a schema migration; keep that flexibility in code.
- **No Java `record` types.** Don't use `record` — neither for DTOs/payloads,
  nor for internal value carriers, nor for private inline helpers. Use regular
  classes with Lombok instead — `@Getter @Setter @NoArgsConstructor
  @AllArgsConstructor` for request/response DTOs, `@Getter @AllArgsConstructor`
  with `final` fields for immutable internal value types. Keeps every value
  type Lombok-based and avoids two parallel accessor styles (`x()` vs `getX()`)
  across the codebase.
- **No static (nested) inner classes for value or helper types.** Don't park
  DTOs, value carriers, validator chains, mapper helpers, or any other
  reusable type inside another class as a `static class` / `static final
  class`. Extract them as their own top-level files in the appropriate
  feature sub-package (e.g. `auth/service/TokenPair.java`, not
  `AuthService.TokenPair`). Improves discoverability via package navigation,
  avoids `Host.Inner` qualified names at call sites, and keeps each type's
  imports/responsibilities scoped to its own file.
- **No `@Builder` / `@SuperBuilder` on entities.** Don't put Lombok's
  `@Builder`, `@SuperBuilder`, or `@Builder.Default` on `@Entity` or
  `@MappedSuperclass` classes (incl. `Auditable` / `DateAuditable`). Construct
  entities with `new X()` + `@Setter` calls (or a `@NoArgsConstructor` +
  field initializers for defaults). Builders on entities encourage building
  partially-valid rows in code that JPA then has to reconcile, hide which
  fields are actually required, and conflict awkwardly with auditing
  (`@CreatedDate` / `@CreatedBy`) and `@UuidGenerator`. `@Builder` on
  request/response DTOs in `payload/` is fine.
- Don't return entities from controllers — use DTOs. Endpoints in `api/`,
  business logic in `service/`, entities/repositories in `domain/`.
- **Mappings live in the service layer, not controllers, and use MapStruct.**
  Don't perform entity-to-DTO (or DTO-to-entity) conversions inside classes
  in `api/`. Services in `service/` must return ready-to-serialize response
  DTOs from `payload/` and accept request DTOs (or `CurrentUser` /
  primitives resolved from the security context) directly. Controllers stay
  thin: validate input, call one service method, wrap the result in
  `ApiResponse`. Don't add static `from(Entity)` / `toEntity()` factory
  methods on `payload/` DTOs and don't invoke them from controllers — keep
  DTOs as plain Lombok data carriers. Define every reusable mapping as a
  **MapStruct** interface in the feature's `service/` sub-package (e.g.
  `auth/service/AuthMapper.java`, `spaces/service/SpaceMapper.java`) with
  `@Mapper(componentModel = "spring")` so Spring injects the generated
  impl. Use `@Mapping(target = "...", source = "...")` only when names
  diverge; otherwise let MapStruct match by name. Inline private mapper
  methods on a service are acceptable only for one-field passthroughs.
  Exception → response mapping in `@RestControllerAdvice` handlers is not a
  data-mapping concern and stays in `api/`. Keeps controllers focused on
  HTTP and makes the service the single source of truth for which fields
  cross the API boundary.
- **Pass `CurrentUser`, not raw `UUID`.** Service methods that act on
  behalf of the caller must accept `org.aiincubator.ilmai.auth.security.CurrentUser`
  as their first argument, never a bare `UUID userId`. Controllers inject
  `@AuthenticationPrincipal CurrentUser currentUser` and forward it
  verbatim. The user id never crosses the API boundary as a request field
  or path variable (AI/RAG non-negotiable §5 spelled out: resolve from the
  security context). Internal hooks that already hold the persisted
  `User` entity (e.g. signup-time `SpaceService.createForUser(User, ...)`)
  may take the entity directly — `CurrentUser` only applies to
  controller-facing service methods.
- **No cross-module `*Repository` injections — go through the producer
  module's public API.** Spring Data repositories in `<feature>/domain/`
  are an **intra-module implementation detail**: only classes inside the
  same feature package may inject them. When module `A` needs data owned
  by module `B`, inject `B`'s public API instead — a `<Feature>Api`
  **interface that lives at module root** (`<feature>/<Feature>Api.java`,
  next to `package-info.java`) backed by a `Default<Feature>Api`
  `@Service` impl in `<feature>/service/` (the only class outside
  `<feature>/domain/` that touches `B`'s repositories). Current public
  APIs: `auth.AuthApi` (`requireUser`, `findUser`),
  `materials.MaterialsApi` (`hasReadyMaterialsForUser`,
  `findReadyForUser`, `findOwnedByUser`, `findById`, `updateStatus`,
  `flushStatus`, `findAllTopicsByUser`), `topics.TopicsApi`
  (`findOwnedByUser`, `findAllByUser`), `spaces.SpacesApi`
  (`findPrimaryForUser`, `findSpaceIdsForUser`), `quiz.QuizApi`
  (`findIncorrectQuestionsForUser`, `findAllSessionsForUser`),
  `gaps.GapsApi` (`get`), `profiles.ProfilesApi` (`require`,
  `touchActivity`), `ai.RetrievalApi` (`retrieve`). Cross-module
  events also live at the module root
  (`auth.UserRegisteredEvent`, `materials.MaterialUploadedEvent`,
  `materials.MaterialDeletedEvent`). **`<Feature>Api` methods MUST
  return module-root DTOs (Lombok `@Getter @AllArgsConstructor final`
  value classes) — NOT entities and NOT `payload/` types.** Entities
  stay private to their owning module's `domain/` sub-package; cross-
  module JPA references are stored as plain `UUID` columns (no
  `@ManyToOne` across module boundaries). When you need a new cross-module
  operation, add a method to the existing `<Feature>Api` (or, for a
  brand-new producer module, create one) — do **not** add a second
  `@Autowired` cross-module repository instead. Test-only `@Autowired`
  of cross-module repositories in `*IntegrationTest`s for seeding data
  is acceptable; integration tests are not feature modules.
- **All feature modules are `CLOSED`; only `common` is `OPEN`.** Every
  feature's `package-info.java` carries
  `@ApplicationModule(type = CLOSED)` so only types at the module root
  are cross-module visible. `common` stays `OPEN` because it carries
  shared cross-cutting infra (`SupportedLocale`, `ApiResponse`,
  `ApiError`, `MessageService`, `BlobStorage`, `QuotaService`, etc.)
  consumed by every controller. Do **not** add an
  `allowedDependencies = { … }` whitelist — the isolation contract is
  expressed in code shape: cross-module callers consume
  `<feature>.<Feature>Api` (interfaces at module root) and
  `<feature>.…Event` (events at module root); they must not import
  anything from another module's `service/`, `api/`, `payload/`, or
  `domain/` sub-packages. The Modulith verification test
  (`ApplicationModulesTests.verifyModules`) runs in CI and fails on
  any cross-module leak into a non-root package.
- Tests in `src/test/java/...` mirroring main; JUnit 5.

### Frontend (TypeScript / React)

- **App Router** under `app/`. Prefer **Server Components** for reads; use
  client components (`"use client"`) only when needed.
- **Path alias `@/`** is configured — import from `@/components`, `@/lib`, etc.
- **UI primitives:** shadcn on **`@base-ui/react`** (not Radix). Install with
  `pnpm dlx shadcn@latest add <component>`.
- **Icons:** HugeIcons (`@hugeicons/react` + `@hugeicons/core-free-icons`).
  Don't add Lucide / react-icons unless asked.
- **Styling:** Tailwind v4 utility classes; class merging via the `cn` helper
  in `@/lib/utils`; variants via `class-variance-authority`.
- **RTL-aware** (`components.json` has `rtl: true`).
- **Formatting:** Prettier with `prettier-plugin-tailwindcss`. Run
  `pnpm format` before committing.

---

## 5. AI / RAG non-negotiables

Straight from the brief — do not relax them:

- **User isolation.** Any RAG retrieval, chat memory read, or quiz/plan/gaps
  query must be scoped to the authenticated user. Add integration tests
  proving user A cannot see user B's data.
- **Citations.** Assistant answers must reference the chunk(s) they came from.
  UI must flag answers without citations.
- **Tool calling:** resolve the user id from the security context — never
  accept it as a tool argument from the model.
- **LLM strategy:** keep providers behind a switchable `ChatClient` abstraction
  so Gemini / GPT-4o / Claude can be swapped (per the brief).

---

## 6. Rules for agents

1. **Always start from the brief.** If a task mentions a feature, locate it in
   `docs/ilm-ai-project-brief.md` first.
2. **Don't trust the other docs or current code as a spec.** Implement what the
   brief says; flag mismatches in your output so a human can update stale
   docs/code.
3. **Don't invent new docs in `docs/`.** If you need to record a decision, add
   it to [`DECISIONLOG.md`](DECISIONLOG.md) at the repo root (append-only,
   dated entries — never edit existing ones, supersede them with a new entry).
4. **Don't rewrite applied Flyway migrations.** Add new `V{N}__...` files.
5. **Don't bypass `ddl-auto=validate`** — fix the migration instead.
6. **Don't add heavy dependencies opportunistically** (Security, Redis, S3,
   JobRunr, NextAuth, AI SDKs, etc.). Add a dep only when the current task
   requires it.
7. **Don't write code comments unless asked.** No inline `//` / `/* */`
   explanations, no JSDoc/Javadoc/KDoc blocks, no `TODO`/`FIXME` notes. Let the
   code speak for itself; if behavior is non-obvious, rename or refactor instead.
   Only add comments when the user explicitly requests them or when an existing
   file's style already uses them consistently.
8. **Languages.** Product strings must work in **UZ / RU / EN**. Don't hardcode
   English in user-facing messages.
9. **No secrets in the repo.** `application.yaml` contains only local dev
   credentials. Real keys (Gemini, OpenAI, Anthropic, Stripe, Payme, Click,
   Telegram) must come from environment variables / CI secrets.
10. **Commits.** Don't commit unless the user asks. When you do, add
    `Co-authored-by: <Your Agent Name> <agent@example>` as a trailer.

---

## Decision log

The dated, append-only decision log lives in [DECISIONLOG.md](DECISIONLOG.md). When you make a cross-cutting decision worth recording, append a new entry there (do not edit existing entries — supersede them with a new dated entry). Read it before changing anything in this repo; it is the running record of every nontrivial call made about the stack, conventions, and overrides of the rules above.

---

*Keep this file short, factual, and aligned with
`docs/ilm-ai-project-brief.md`.*
