# Ilm AI — Frontend

The web client for **Ilm AI**, a personal AI learning companion. Users upload
their own material and the app becomes a tutor for it: chat, quizzes, knowledge
gaps, a learning plan, and Telegram reminders. Mobile-first, RTL-aware, and
fully localised in **Uzbek · Russian · English**.

For the big picture across both services, see the [root README](../README.md).

---

## Table of contents

- [Stack](#stack)
- [Prerequisites](#prerequisites)
- [Quick start](#quick-start)
- [Scripts](#scripts)
- [Environment](#environment)
- [Mock backend (MSW)](#mock-backend-msw)
- [Project layout](#project-layout)
- [Conventions](#conventions)
- [Production build & Docker](#production-build--docker)

---

## Stack

| Area          | Tech                                                            |
| ------------- | -------------------------------------------------------------- |
| Framework     | Next.js 16 (App Router, Turbopack) · React 19                  |
| Language      | TypeScript 5 (strict)                                           |
| Styling       | Tailwind CSS v4 · `class-variance-authority` · `cn` helper      |
| UI primitives | shadcn on [`@base-ui/react`](https://base-ui.com/) (not Radix) |
| Icons         | HugeIcons (`@hugeicons/react` + `@hugeicons/core-free-icons`)  |
| Auth          | NextAuth v5 (Google)                                            |
| AI chat UI    | Vercel AI SDK (`ai`, `@ai-sdk/react`) + `streamdown` rendering |
| Charts        | Recharts                                                        |
| Toasts        | Sonner                                                          |
| Mocking       | MSW (in-memory backend for offline dev)                        |
| Monitoring    | Sentry (`@sentry/nextjs`)                                       |
| Tooling       | ESLint · Prettier + `prettier-plugin-tailwindcss` · pnpm        |

> Exact versions live in [`package.json`](package.json) — trust it over this
> table.

---

## Prerequisites

- **Node 22** (or any version `next@16` supports).
- **pnpm 9** (`packageManager` is pinned in `package.json`; enable via
  `corepack enable`).

No backend is required to run the UI — see [Mock backend](#mock-backend-msw).

---

## Quick start

```bash
pnpm install
cp .env.example .env.local      # sensible mock-mode defaults are pre-filled
pnpm dev                        # http://localhost:3000 (Turbopack)
```

Out of the box the app runs in **mock mode** (`NEXT_PUBLIC_MOCK_API=1`), so the
whole product works without the Spring backend. To talk to a real backend, point
`NEXT_PUBLIC_API_BASE_URL` / `AUTH_BACKEND_URL` at it and flip features live (see
below).

---

## Scripts

| Command          | Does                                              |
| ---------------- | ------------------------------------------------- |
| `pnpm dev`       | Dev server with Turbopack on `:3000`              |
| `pnpm build`     | Production build                                  |
| `pnpm start`     | Serve the production build                        |
| `pnpm typecheck` | `tsc --noEmit`                                     |
| `pnpm lint`      | ESLint (`eslint-config-next`)                     |
| `pnpm format`    | Prettier + Tailwind class sorting                 |

Run `pnpm typecheck && pnpm lint` before pushing — CI runs both.

---

## Environment

Copy [`.env.example`](.env.example) to `.env.local` and fill in what you need.

| Variable                    | Purpose                                                            |
| --------------------------- | ----------------------------------------------------------------- |
| `NEXT_PUBLIC_API_BASE_URL`  | Backend base URL the browser/server calls.                        |
| `AUTH_BACKEND_URL`          | Backend URL NextAuth exchanges the Google token with.             |
| `AUTH_SECRET`               | NextAuth session secret (any random string in dev).               |
| `AUTH_GOOGLE_ID`            | Google OAuth client id.                                           |
| `AUTH_GOOGLE_SECRET`        | Google OAuth client secret.                                       |
| `NEXT_PUBLIC_DEMO_MODE`     | Shows a demo sign-in button + reset.                              |
| `NEXT_PUBLIC_MOCK_API`      | `1` → MSW intercepts API calls with an in-memory db.              |
| `NEXT_PUBLIC_LIVE_FEATURES` | Comma-separated feature ids that bypass MSW (or `all`).           |
| `NEXT_PUBLIC_SENTRY_DSN`    | Enables Sentry; SDK stays off while empty.                        |
| `SENTRY_ORG` / `SENTRY_PROJECT` / `SENTRY_AUTH_TOKEN` | CI-only — source-map upload.            |

No secrets are committed; real OAuth and Sentry credentials come from the
environment / CI.

---

## Mock backend (MSW)

When `NEXT_PUBLIC_MOCK_API=1`, [MSW](https://mswjs.io/) (`mocks/handlers.ts`)
intercepts every API call against an in-memory db, so the whole app works with no
backend running.

To verify a feature against the **real** backend while the rest stay mocked, list
it in `NEXT_PUBLIC_LIVE_FEATURES` (comma-separated, or `all`) — e.g.
`NEXT_PUBLIC_LIVE_FEATURES=companion`. Those requests pass through MSW to
`NEXT_PUBLIC_API_BASE_URL`; everything else keeps using the mock db. With
`NEXT_PUBLIC_MOCK_API` unset, all features are live. Feature ids are defined in
`lib/feature-flags.ts`.

---

## Project layout

```
app/
  (auth)/login, signup        - public auth screens
  (app)/                      - authenticated shell (sidebar + bottom tab bar)
    home/                     - dashboard (today, paths, streak)
    topics/, topics/[id]/    - knowledge base (materials, chat, quiz tabs)
    plan/                     - learning plan
    gaps/                     - knowledge gap report
    stats/                    - learning stats
    premium/                  - free / premium plan
    telegram/                 - bot connection
    settings/*                - account, notifications, subscription, privacy
components/
  app-shell/                  - sidebar (lg+), bottom-tab-bar + mobile-top-bar (<lg)
  ui/                         - shadcn primitives over @base-ui/react
  <feature>/                  - per-feature components
lib/
  api.ts, chat.ts, ...        - typed fetchers (one per feature)
  i18n/                       - dictionary + provider
  feature-flags.ts            - live-vs-mock feature ids
mocks/                        - MSW handlers + in-memory db
auth.ts, auth.config.ts       - NextAuth setup
```

Prefer **Server Components** for reads; add `"use client"` only when you need
interactivity. The `@/` path alias maps to the project root — import from
`@/components`, `@/lib`, etc.

---

## Conventions

### Adding UI components

```bash
pnpm dlx shadcn@latest add <component>
```

Components install against `@base-ui/react` (configured in `components.json`,
`rtl: true`). Merge classes with the `cn` helper in `@/lib/utils`; define variants
with `class-variance-authority`.

**Icons:** HugeIcons only (`@hugeicons/react` + `@hugeicons/core-free-icons`).
Don't add Lucide / react-icons.

### i18n

Every user-facing string lives in `lib/i18n/dictionary.ts` with **EN / RU / UZ**
versions; read them with `useT()` in client components. Adding a screen → add the
keys to all three dictionaries. Never hardcode English in user-facing copy.

### Formatting

Prettier with `prettier-plugin-tailwindcss` is the formatter of record. Run
`pnpm format` before committing.

---

## Production build & Docker

```bash
pnpm build && pnpm start        # local production run
```

The included [`Dockerfile`](Dockerfile) builds the production image used by the
[root `docker-compose.yml`](../docker-compose.yml) stack (behind Caddy). In
production the frontend talks to the backend over the internal Docker network;
deployment specifics are in [`../DEPLOY.md`](../DEPLOY.md).
