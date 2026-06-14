# Deployment Guide — MarketPlace (Agric Market)

**Last updated:** June 2026

Agricultural marketplace: **Spring Boot** API (`backend/`) + **Vue** frontend (`frontend/`).

## Branches

| Branch | Purpose |
|--------|---------|
| `SIT` | Local / integration (H2) |
| `UAT` | Staging (PostgreSQL) |
| `PROD` | Production (PostgreSQL) |

## Stack

| Component | UAT / PROD | Local dev |
|-----------|------------|-----------|
| API | Spring Boot 17, PostgreSQL | MySQL (`local` profile) |
| Web | Vue 3 + Vite | `npm run dev` |

Full variable reference: **[ENV.md](ENV.md)**

## Pre-deploy checklist

- [ ] PostgreSQL provisioned per environment
- [ ] `SPRING_PROFILES_ACTIVE` = `uat` or `prod`
- [ ] `APP_JWT_SECRET` — 32+ random characters
- [ ] `PUBLIC_APP_BASE_URL` + `UAT_CORS_ORIGINS` / `PROD_CORS_ORIGINS`
- [ ] `SENDGRID_API_KEY` + `EMAIL_FROM` (verified sender)
- [ ] `VITE_API_BASE` on frontend build (split hosting)
- [ ] First admin created via setup UI after deploy
- [ ] `app.seed-demo-data=false` (default on UAT/PROD)

## Railway — backend (`backend/`)

- Root directory: **`backend`**
- Branch: **`UAT`** or **`PROD`**
- Builder: **Dockerfile** (see `backend/railway.toml`)
- Health check: **`/actuator/health`**
- Add **PostgreSQL** in the same project

Startup **fails fast** if JWT, CORS, public URL, or SendGrid are misconfigured (`DeployedProfileValidator`).

## Railway / Vercel — frontend (`frontend/`)

| Variable | Example |
|----------|---------|
| `VITE_APP_ENV` | `UAT` or `PROD` |
| `VITE_API_BASE` | `https://your-backend.up.railway.app` (no `/api`) |

Build: `npm run build:uat` or `npm run build:prod`

Docker build args: `VITE_API_BASE`, `VITE_APP_ENV`

## Docker Compose (single server)

```bash
cp .env.prod.example .env.prod
# fill values
docker compose -f docker-compose.prod.yml up -d --build
```

Uses Postgres + backend + nginx frontend on port **80**.

## Tests before promote

```bash
cd backend && mvn test
cd frontend && npm run test:run
```

## Rollback

Redeploy previous Railway build. Avoid DB rollback without backup when `ddl-auto=update` has run.

See [README.md](README.md) and [USER_MANUAL.md](USER_MANUAL.md).
