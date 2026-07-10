# Deployment Guide — MarketPlace (Agric Market)

**Last updated:** July 2026

Agricultural marketplace: **Spring Boot** API (`backend/`) + **Vue** frontend (`frontend/`).

## Branches

| Branch | Purpose |
|--------|---------|
| `SIT` | Local / integration (H2) |
| `UAT` | Staging (MySQL) |
| `PROD` | Production (MySQL) |

Deploy **from the matching branch**. Production changes must land on `PROD`.

## Stack

| Component | UAT / PROD | Local dev |
|-----------|------------|-----------|
| API | Spring Boot 17, MySQL | MySQL (`local` profile) |
| Web | Vue 3 + Vite | `npm run dev` |

Full variable reference: **[ENV.md](ENV.md)**

## Pre-deploy checklist

- [ ] MySQL provisioned per environment
- [ ] `SPRING_PROFILES_ACTIVE` = `uat` or `prod`
- [ ] `APP_JWT_SECRET` — 32+ random characters (not a dev default)
- [ ] `PUBLIC_APP_BASE_URL` + `UAT_CORS_ORIGINS` / `PROD_CORS_ORIGINS`
- [ ] `SENDGRID_API_KEY` + `EMAIL_FROM` (verified sender)
- [ ] `VITE_API_BASE` on frontend build when API is on a different host
- [ ] First admin created via setup UI after deploy
- [ ] `app.seed-demo-data=false` (default on UAT/PROD)

Startup **fails fast** if JWT, CORS, public URL, or SendGrid are misconfigured (`DeployedProfileValidator`).

---

## Option A — Railway (recommended)

### Backend (`backend/`)

1. New project → add **MySQL**
2. New service from this repo
   - **Root directory:** `backend`
   - **Branch:** `PROD` (or `UAT`)
   - **Builder:** Dockerfile (`backend/railway.toml`)
3. Health check: `/actuator/health`
4. Variables (see `railway-env-variables.example.txt` / [ENV.md](ENV.md)):

| Variable | PROD value |
|----------|------------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_*` or Railway `MYSQL*` | from MySQL plugin (`jdbc:mysql://…`) |
| `APP_JWT_SECRET` | long random secret |
| `PUBLIC_APP_BASE_URL` | `https://your-frontend.vercel.app` |
| `PROD_CORS_ORIGINS` | same as public URL (comma-separated if multiple) |
| `SENDGRID_API_KEY` | SendGrid key |
| `EMAIL_FROM` | verified sender |

### Frontend (`frontend/`)

1. Second service, **Root directory:** `frontend`, branch `PROD`
2. Builder: Dockerfile (`frontend/railway.toml`)
3. Build-time variables:

| Variable | Example |
|----------|---------|
| `VITE_APP_ENV` | `PROD` |
| `VITE_API_BASE` | `https://your-backend.up.railway.app` (no `/api`) |

Railway injects build args from service variables when using Docker. If the image builds without your API URL, set Docker build args in the service settings:

- `VITE_API_BASE`
- `VITE_APP_ENV=PROD`

### After deploy

1. Open frontend → create first admin if prompted (`/setup`)
2. `GET https://your-backend.../actuator/health` → `{"status":"UP"}`
3. Test forgot-password email (link uses `PUBLIC_APP_BASE_URL`)

---

## Option B — Docker Compose (single server)

```bash
cp .env.prod.example .env.prod
# fill all values — APP_JWT_SECRET, DB password, PUBLIC_APP_BASE_URL, PROD_CORS_ORIGINS, SendGrid
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

- Frontend + nginx on port **80** (proxies `/api` to backend)
- Backend is **not** published on the host (only internal)
- Leave `VITE_API_BASE` empty so the browser uses same-origin `/api`

TLS: put Caddy, nginx, or a cloud load balancer in front of port 80.

---

## Tests before promote

```bash
cd backend && mvn test
cd frontend && npm run test:run
```

CI runs the same on push/PR to `SIT`, `UAT`, `PROD`, and `main`.

### Staff / payroll smoke (Premium)

1. Enrol staff on **Team management** (pay method + rate + permissions).
2. As that staff user, mark an order **COLLECTED** (or owner re-assigns `completedByStaffId`).
3. Owner opens **Staff payments** → pending row appears → mark paid (optional include bonus) / pay all.
4. Staff opens **My income** (`/provider/staff-payments`) → sees expected unpaid / paid lines.
5. Confirm notification bell + email (+ SMS if Twilio enabled) for new-order / status / subscription events.

## Rollback

Redeploy the previous Railway build (or previous image tag). Avoid DB rollback without a backup when `ddl-auto=update` has already applied schema changes.

## Promote SIT → PROD

```bash
git checkout PROD
git merge SIT
git push origin PROD
```

See [README.md](README.md) and [USER_MANUAL.md](USER_MANUAL.md).
