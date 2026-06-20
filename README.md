<div align="center">

# Ilm AI

**Your personal AI learning companion.**

Bring your own material — a textbook chapter, a course transcript, a research
paper, your own notes — and Ilm AI becomes the tutor for *that* material. It
chats, quizzes you, finds your knowledge gaps, builds a day-by-day learning
plan, and nudges you to keep going via Telegram. Every answer is grounded in
what **you** uploaded and cited back to the source.

Replies in the language you write in — **Uzbek · Russian · English**.

</div>

---

## Table of contents

- [What it does](#what-it-does)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Repository layout](#repository-layout)
- [Quick start](#quick-start)
- [AI / RAG ground rules](#ai--rag-ground-rules)
- [Deployment](#deployment)
- [Documentation](#documentation)
- [Contributing](#contributing)

---

## What it does

| Feature | Summary |
| ------- | ------- |
| **Auth & profiles** | Email / Google sign-in; a private learning space per user; learning goal + target date. |
| **Personal knowledge base** | Upload PDF / Word / text or paste content; auto chunk → embed → store in pgvector; organise into your own topics. |
| **AI companion (chat)** | Warm, Socratic tutor grounded strictly in your material, with citations back to the exact source chunk. |
| **Quiz & practice** | Fresh MCQ / short-answer / open questions per topic and difficulty, with explanations and scoring. |
| **Knowledge-gap detection** | Aggregates quiz history into a plain-language "Gaps Report" that updates every session. |
| **Learning plan** | An agent maps your materials and gaps to a realistic day-by-day plan toward your goal date. |
| **Telegram bot** | Daily reminders, on-demand 5-question quizzes, streak nudges. |
| **Billing** | Free vs Premium tiers; Payme / Click (UZ) or Stripe; webhook-activated upgrades. |

Per-service setup and details live in [`backend/README.md`](backend/README.md)
and [`frontend/README.md`](frontend/README.md).

---

## Architecture

```
                         ┌──────────── Caddy (:80/:443, automatic HTTPS) ────────────┐
   Browser / Telegram ──▶│  /telegram/webhook/* ─▶ backend   ·   everything else ─▶ frontend │
                         └───────────────────────────────────────────────────────────┘
                                          │                              │
                                   ┌──────▼──────┐                ┌──────▼──────┐
                                   │  frontend   │  ── REST ──▶   │   backend   │
                                   │  Next.js 16 │                │ Spring Boot │
                                   └─────────────┘                └──────┬──────┘
                                                                         │
                          ┌──────────────────┬───────────────────────────┼───────────────┐
                          ▼                  ▼                           ▼               ▼
                  Postgres + pgvector   Garage (S3 object store)   Google Gemini    Telegram API
                  (data + embeddings)   (raw uploaded files)       (chat + embed)
```

- **RAG pipeline:** `DocumentReader` (Apache Tika) → chunk → Gemini embeddings
  (768-dim) → pgvector → retrieval filtered per user → cited chat answer.
- **Modularity:** the backend is a [Spring Modulith](https://spring.io/projects/spring-modulith)
  monolith — package-by-feature, each feature isolated behind a public `*Api`.
- **Background work:** [JobRunr](https://www.jobrunr.io/) handles ingestion
  retries, plan re-generation, and reminder dispatch.

---

## Tech stack

| Area | Choice |
| ---- | ------ |
| **Frontend** | Next.js 16 (App Router) · React 19 · Tailwind v4 · shadcn on `@base-ui/react` · NextAuth v5 · HugeIcons |
| **Backend** | Java 25 · Spring Boot 4 · Spring Modulith · Spring Security (OAuth2 + JWT) · Spring Data JPA · MapStruct · Lombok |
| **AI** | Spring AI · Google Gemini (chat + `gemini-embedding-2`) · Spring AI Advisors / `ChatClient` / `VectorStore` |
| **Data** | PostgreSQL 16 + pgvector · Flyway migrations · Garage (S3-compatible) for files |
| **Jobs / integrations** | JobRunr · Telegram Bot API · Payme / Click / Stripe |
| **Ops** | Docker · Docker Compose · Caddy · GitHub Actions (GHCR) · Sentry |

> Versions drift — always trust [`backend/build.gradle`](backend/build.gradle)
> and [`frontend/package.json`](frontend/package.json) over any prose.

---

## Repository layout

```
IlmAI/
├── README.md             ← you are here
├── DEPLOY.md             — production deployment runbook
├── docker-compose.yml    — full production stack (Caddy + frontend + backend + pgvector + garage)
├── deploy/               — Caddyfile, Garage config & init scripts
├── backend/              — Spring Boot service  → backend/README.md
└── frontend/             — Next.js app          → frontend/README.md
```

---

## Quick start

You need **Docker + Docker Compose**, **JDK 25** (for the Gradle wrapper) and
**Node 22 + pnpm 9**.

### 1. Backend

```bash
# Boot dev infra (Postgres + pgvector, Garage) and run the API on :8080
docker compose -f backend/compose.yaml up -d
export GOOGLE_GENAI_API_KEY=your-ai-studio-key
export JWT_SECRET=$(openssl rand -hex 64)
export GOOGLE_CLIENT_ID=...apps.googleusercontent.com
cd backend && ./gradlew bootRun
```

### 2. Frontend

```bash
cd frontend
pnpm install
cp .env.example .env.local      # fill in AUTH_* + API base URL
pnpm dev                        # http://localhost:3000
```

The frontend ships an in-memory mock backend (MSW). Set
`NEXT_PUBLIC_MOCK_API=1` to run the whole UI without the backend at all — see
[`frontend/README.md`](frontend/README.md).

Per-service setup, configuration and environment variables live in each
service's README:

- **Backend** → [`backend/README.md`](backend/README.md)
- **Frontend** → [`frontend/README.md`](frontend/README.md)

---

## AI / RAG ground rules

These are non-negotiable:

1. **User isolation.** Every retrieval, chat-memory read, and quiz/plan/gaps
   query is scoped to the authenticated user. Covered by integration tests.
2. **Citations.** Assistant answers reference the chunk(s) they came from; the
   UI flags any answer without citations.
3. **Tool calling.** The user id is resolved from the security context — never
   accepted as a tool argument from the model.
4. **Swappable LLM.** Providers stay behind Spring AI's `ChatClient`
   abstraction so Gemini / GPT-4o / Claude can be swapped.

---

## Deployment

A single Docker Compose stack runs the whole app on one VPS behind Caddy
(automatic HTTPS). GitHub Actions builds and pushes `backend` / `frontend`
images to GHCR, then redeploys on push to `main`. Full runbook:
[`DEPLOY.md`](DEPLOY.md).

```bash
cp .env.example .env            # set DOMAIN, secrets, API keys
docker compose pull
docker compose up -d
```

---

## Documentation

- **Backend service:** [`backend/README.md`](backend/README.md)
- **Frontend app:** [`frontend/README.md`](frontend/README.md)
- **Deployment:** [`DEPLOY.md`](DEPLOY.md)

For exact dependencies and versions, trust the build files
([`backend/build.gradle`](backend/build.gradle),
[`frontend/package.json`](frontend/package.json)) over any prose.

---

## Contributing

1. Read the relevant service README before changing anything.
2. Keep changes scoped per feature module; don't break user isolation or
   citations.
3. Schema changes go through new Flyway `V{N}__*.sql` files — never edit an
   applied migration.
