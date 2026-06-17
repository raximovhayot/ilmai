# Deploying Ilm AI (Hetzner VPS · single Docker Compose stack)

This repo ships a CI/CD pipeline (GitHub Actions) and a single production
Docker Compose stack that runs the whole app on one Hetzner VPS behind Caddy
(automatic HTTPS).

```
Internet ──HTTPS──> Caddy (:80/:443)
                      ├── /telegram/webhook/*  ──> backend:8080
                      └── everything else       ──> frontend:3000 (Next.js)
                                                      └── /api/backend/* proxy ──> backend:8080
backend ──> pgvector:5432 (Postgres + pgvector)   garage:3900 (S3 storage)
```

Only Caddy publishes ports. `backend`, `frontend`, `pgvector`, and `garage`
stay private on the internal `ilmai` Docker network.

---

## 1. Pipelines

- **`.github/workflows/ci.yml`** — on every push / PR: backend `./gradlew build`
  (JDK 25) and frontend `pnpm typecheck && lint && build` (Node 22 / pnpm).
- **`.github/workflows/deploy.yml`** — on push to `main` (or manual dispatch):
  builds the `backend` and `frontend` images, pushes them to **GHCR**
  (`ghcr.io/raximovhayot/ilmai-backend` / `-frontend`, tagged `latest` and
  `sha-<commit>`), then SSHes into the VPS, copies the stack files, and runs
  `docker compose pull && up -d`.

---

## 2. One-time DNS

Point an **A record** for `ilmai.uzinfoweb.uz` at the VPS public IP.
Caddy issues the Let's Encrypt certificate automatically on first start.
The Telegram webhook is served on the same host at
`https://ilmai.uzinfoweb.uz/telegram/webhook/<secret>` — no extra DNS needed.

---

## 3. One-time VPS setup

```bash
# As a non-root sudo user on the Hetzner VPS:
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker "$USER"   # re-login afterwards

mkdir -p ~/ilmai && cd ~/ilmai
# Copy .env.example from the repo to the server as .env and fill it in:
#   scp .env.example user@vps:~/ilmai/.env
nano .env                          # set DOMAIN, secrets, API keys, etc.
```

`~/ilmai/.env` is **operator-managed and never committed**. The deploy job
refuses to run if it is missing. Generate the strong values:

```bash
openssl rand -base64 32   # AUTH_SECRET, JWT_SECRET
openssl rand -hex 32      # GARAGE_RPC_SECRET, GARAGE_ADMIN_TOKEN, S3 keys
```

---

## 4. GitHub repository secrets

Set these under **Settings → Secrets and variables → Actions**:

| Secret | Purpose |
| --- | --- |
| `SSH_HOST` | VPS IP / hostname |
| `SSH_USER` | SSH login user (in the `docker` group) |
| `SSH_KEY` | Private SSH key for that user |
| `SSH_PORT` | SSH port (e.g. `22`) |
| `GHCR_PULL_TOKEN` | PAT with `read:packages` so the VPS can pull GHCR images |
| `NEXT_PUBLIC_SENTRY_DSN` | (optional) frontend Sentry DSN, baked at build |
| `SENTRY_ORG` / `SENTRY_PROJECT` / `SENTRY_AUTH_TOKEN` | (optional) frontend source-map upload |

> If you make the GHCR packages public, `GHCR_PULL_TOKEN` is not needed for the
> pull, but the deploy script still logs in defensively.

---

## 5. First deploy

Push to `main` (or run the **Deploy** workflow manually). To bring the stack up
by hand on the VPS instead:

```bash
cd ~/ilmai
docker login ghcr.io -u <github-user>        # if images are private
docker compose pull
docker compose up -d
docker compose ps
```

Verify:

```bash
curl -fsS https://ilmai.uzinfoweb.uz/            # frontend
docker compose exec backend \
  curl -fsS http://localhost:8080/actuator/health
```

---

## 6. Telegram webhook (when you go live)

```bash
curl "https://api.telegram.org/bot<TELEGRAM_BOT_TOKEN>/setWebhook" \
  --data-urlencode "url=https://ilmai.uzinfoweb.uz/telegram/webhook/<TELEGRAM_WEBHOOK_SECRET>"
```

---

## 7. Notes & limitations

- **Migrations** run automatically (Flyway, `ddl-auto=validate`) on backend start.
- **Garage** is single-node (`replication_factor = 1`); back up the
  `garage-meta` / `garage-data` / `pg-data` volumes for durability.
- The frontend's `NEXT_PUBLIC_API_BASE_URL` is baked at build time to the
  internal `http://backend:8080`; the browser never uses it (it calls the
  Next `/api/backend/*` proxy), so no internal hostname is reachable publicly.
- This is a single-VPS topology. For HA, split Postgres / storage onto managed
  services and run multiple app replicas behind a load balancer.
