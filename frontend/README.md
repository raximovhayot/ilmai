# Ilm AI — Frontend

Next.js (App Router) + Tailwind v4 + shadcn on `@base-ui/react`.
Mobile-first; UZ / RU / EN; auth via NextAuth (Google).

Source-of-truth product brief: [`../docs/ilm-ai-project-brief.md`](../docs/ilm-ai-project-brief.md).
Repo-wide conventions: [`../AGENTS.md`](../AGENTS.md).

## Setup

```bash
pnpm install
cp .env.example .env.local
```

Fill in `.env.local`:

```env
AUTH_SECRET=...              # any random string for local dev
AUTH_GOOGLE_ID=...           # Google OAuth client id
AUTH_GOOGLE_SECRET=...       # Google OAuth client secret
AUTH_BACKEND_URL=http://localhost:8080
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_DEMO_MODE=1      # demo sign-in button + reset
NEXT_PUBLIC_MOCK_API=1       # MSW intercepts API calls
NEXT_PUBLIC_LIVE_FEATURES=   # while mocks are on, send these features to the real backend
```

## Run

```bash
pnpm dev          # http://localhost:3000 with Turbopack
pnpm typecheck    # tsc --noEmit
pnpm lint
pnpm format       # prettier + tailwind plugin
```

When `NEXT_PUBLIC_MOCK_API=1`, [MSW](https://mswjs.io/) (`mocks/handlers.ts`) intercepts every API call against an in-memory db so the whole app works without a backend.

To verify a feature against the real backend while the rest stay mocked, list it in `NEXT_PUBLIC_LIVE_FEATURES` (comma-separated, or `all`) — e.g. `NEXT_PUBLIC_LIVE_FEATURES=data`. Those requests pass through MSW to `NEXT_PUBLIC_API_BASE_URL`; everything else keeps using the mock db. With `NEXT_PUBLIC_MOCK_API` unset, all features are live. Feature names are defined in `lib/feature-flags.ts`.

## Layout

```
app/
  (auth)/login,signup        - public auth screens
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
mocks/                        - MSW handlers + in-memory db
```

## Adding components

```bash
pnpm dlx shadcn@latest add <component>
```

Icons: `@hugeicons/react` + `@hugeicons/core-free-icons` only.

## i18n

Every user-facing string lives in `lib/i18n/dictionary.ts` with EN / RU / UZ versions.
Use `useT()` inside client components. Adding a new screen → add keys to all three dictionaries.
