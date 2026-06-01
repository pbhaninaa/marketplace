# Deployment Guide — MarketPlace (Agric Market)

**Last updated:** June 2026

Agricultural marketplace: **Spring Boot** API (`backend/`) + **Vue** frontend (`frontend/`).

## Source control: SIT, UAT, PROD

| Branch | Purpose |
|--------|---------|
| `SIT` | Local / integration |
| `UAT` | Staging |
| `PROD` | Production |

## Components

| Component | Stack | Hosting |
|-----------|-------|---------|
| API | Spring Boot, PostgreSQL | Railway (recommended) |
| Web | Vue 3, Vite | Vercel or same domain as API |

Backend profiles: `application-sit.properties`, `application-uat.properties`, `application-prod.properties`.

## Pre-deployment checklist

- [ ] PostgreSQL database per environment
- [ ] `APP_JWT_SECRET` — strong random secret (32+ chars)
- [ ] `PUBLIC_APP_BASE_URL` — public frontend URL (password-reset links)
- [ ] `PROD_CORS_ORIGINS` (or UAT equivalent) includes frontend origin(s)
- [ ] Bank / subscription env vars set if using provider billing (see `.env.prod.example`)
- [ ] Payment provider configured (`PROD_PAYMENT_PROVIDER`, Stripe/Paystack keys as implemented)
- [ ] `app.seed-demo-data=false` in production

## Railway (backend — `backend/`)

| Variable | Description |
|----------|-------------|
| `SPRING_PROFILES_ACTIVE` | `prod` / `uat` / `sit` |
| `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` | Railway Postgres (auto-injected) or `PROD_DB_*` overrides |
| `APP_JWT_SECRET` | JWT signing secret |
| `PUBLIC_APP_BASE_URL` | e.g. `https://your-app.vercel.app` |
| `PROD_CORS_ORIGINS` | Comma-separated allowed origins |
| `PROD_PAYMENT_PROVIDER` | e.g. `stripe` |
| `APP_BANK_*` | EFT bank details for providers (optional) |

## Vercel (frontend — `frontend/`)

| Variable | Description |
|----------|-------------|
| `VITE_APP_ENV` | `PROD` / `UAT` / `SIT` |
| `VITE_API_BASE` | Backend base URL (no trailing slash), e.g. `https://api.up.railway.app` |

Redeploy after changing env vars.

## Local reference

Copy [.env.prod.example](.env.prod.example) to `.env.prod` for a variable checklist (do not commit secrets).

## Rollback

Redeploy previous Railway/Vercel builds. Avoid DB rollback without a backup if Hibernate `ddl-auto=update` has already applied schema changes.

See [README.md](README.md) and [USER_MANUAL.md](USER_MANUAL.md).
