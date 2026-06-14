# Environment variables — MarketPlace (Agri Marketplace)

Profiles: **local** (MySQL, dev machine) · **sit** (H2, tests) · **uat** (PostgreSQL, staging) · **prod** (PostgreSQL, live)

Set **`SPRING_PROFILES_ACTIVE`** to `uat` or `prod` on Railway. Never rely on a default profile in production.

Database: **PostgreSQL** on UAT/PROD · **MySQL** optional for local only.

---

## Railway layout (recommended)

| Service | Root directory | Branch |
|---------|----------------|--------|
| PostgreSQL | Railway addon | — |
| Backend API | `backend/` | `UAT` or `PROD` |
| Frontend (optional) | `frontend/` | `UAT` or `PROD` |

Frontend build: `npm run build:uat` or `npm run build:prod`.  
API paths use `/api/...` — set **`VITE_API_BASE`** to the backend **origin only** (no `/api`).

---

## Backend — UAT

### Profile
| Variable | Value |
|----------|--------|
| `SPRING_PROFILES_ACTIVE` | `uat` |

### Database (PostgreSQL)
| Variable | Notes |
|----------|--------|
| `UAT_DB_HOST` | Or Railway `${{Postgres.PGHOST}}` |
| `UAT_DB_PORT` | Or `${{Postgres.PGPORT}}` |
| `UAT_DB_NAME` | Or `${{Postgres.PGDATABASE}}` |
| `UAT_DB_USER` | Or `${{Postgres.PGUSER}}` |
| `UAT_DB_PASSWORD` | Or `${{Postgres.PGPASSWORD}}` |

Railway can also inject **`PGHOST`**, **`PGPORT`**, **`PGDATABASE`**, **`PGUSER`**, **`PGPASSWORD`** — UAT profile reads these when `UAT_DB_*` is unset.

### Security
| Variable | Notes |
|----------|--------|
| `APP_JWT_SECRET` | Min 32 characters |

### Frontend URLs
| Variable | Notes |
|----------|--------|
| `PUBLIC_APP_BASE_URL` | Public UI URL, no trailing slash |
| `UAT_CORS_ORIGINS` | Same URL(s), comma-separated |
| `APP_FRONTEND_URL` | Optional alias for email links |

### Email (SendGrid) — required on UAT
| Variable | Notes |
|----------|--------|
| `SENDGRID_API_KEY` | SendGrid API key |
| `EMAIL_FROM` | Verified sender |

### Optional
| Variable | Default |
|----------|---------|
| `UAT_PAYMENT_PROVIDER` | `stub` |
| `APP_BANK_*` | Platform EFT details for subscriptions |
| `APP_SUBSCRIPTION_BASIC_MONTHLY` | `199` |
| `APP_SUBSCRIPTION_PREMIUM_MONTHLY` | `499` |

---

## Backend — PROD

Same as UAT except:

| Variable | UAT | PROD |
|----------|-----|------|
| Profile | `uat` | `prod` |
| DB vars | `UAT_DB_*` or `PG*` | `PROD_DB_*` or `PG*` |
| CORS | `UAT_CORS_ORIGINS` | `PROD_CORS_ORIGINS` |
| Payments label | `UAT_PAYMENT_PROVIDER` | `PROD_PAYMENT_PROVIDER` |

---

## Frontend — UAT / PROD

| Variable | UAT example | PROD example |
|----------|-------------|--------------|
| `VITE_APP_ENV` | `UAT` | `PROD` |
| `VITE_API_BASE` | `https://backend-uat.up.railway.app` | `https://api.yourdomain.com` |

Leave `VITE_API_BASE` **empty** when using **docker-compose** (nginx proxies `/api` on the same host).

---

## First deploy

1. Deploy Postgres + backend with vars above.
2. Deploy frontend with `VITE_API_BASE` → backend public URL.
3. Open frontend → **`/api/public/setup-status`** — if no admin, create first admin via UI.
4. Verify `GET /actuator/health` → `UP`.
5. Test forgot-password email (link must use `PUBLIC_APP_BASE_URL`).

---

## Local only

| Variable | Purpose |
|----------|---------|
| `LOCAL_DB_PASSWORD` | MySQL root password |
| `APP_JWT_SECRET` | Optional local override |
| `SENDGRID_API_KEY` / `EMAIL_FROM` | Optional local email |

Run: `mvn spring-boot:run -Dspring-boot.run.profiles=local`

---

## Tests

```bash
cd backend && mvn test
cd frontend && npm run test:run
```

---

See [DEPLOYMENT.md](DEPLOYMENT.md) and [.env.prod.example](.env.prod.example).
