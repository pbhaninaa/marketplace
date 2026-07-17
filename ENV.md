# Environment variables — MarketPlace (Agri Marketplace)

Profiles: **local** (MySQL, dev machine) · **sit** (H2, tests) · **uat** (MySQL, staging) · **prod** (MySQL, live)

Set **`SPRING_PROFILES_ACTIVE`** to `uat` or `prod` on Railway. Never rely on a default profile in production.

Database: **MySQL** on local / UAT / PROD.

---

## Railway layout (recommended)

| Service | Root directory | Branch |
|---------|----------------|--------|
| MySQL | Railway addon | — |
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

### Database (MySQL)
| Variable | Notes |
|----------|--------|
| `SPRING_DATASOURCE_URL` | Must be `jdbc:mysql://...` (rewrite Railway `MYSQL_URL` if it is `mysql://`) |
| `SPRING_DATASOURCE_USERNAME` | Or `${{MySQL.MYSQLUSER}}` |
| `SPRING_DATASOURCE_PASSWORD` | Or `${{MySQL.MYSQLPASSWORD}}` |

If `SPRING_DATASOURCE_*` is unset, UAT/PROD fall back to **`MYSQLHOST`**, **`MYSQLPORT`**, **`MYSQLDATABASE`**, **`MYSQLUSER`**, **`MYSQLPASSWORD`**.

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

### SMS (Twilio) — optional
| Variable | Notes |
|----------|--------|
| `APP_SMS_ENABLED` | `true` to enable SMS channel |
| `TWILIO_ACCOUNT_SID` | Twilio account SID |
| `TWILIO_AUTH_TOKEN` | Twilio auth token |
| `TWILIO_FROM_NUMBER` | E.164 sender (or use messaging service) |
| `TWILIO_MESSAGING_SERVICE_SID` | Optional MG… SID instead of from number |
| `APP_PHONE_COUNTRY_CODE` | Default `+27` when guest phone has no country code |

### Optional
| Variable | Default |
|----------|---------|
| `UAT_PAYMENT_PROVIDER` | `stub` |
| `APP_BANK_*` | Legacy platform banking details (not used by new subscription payments) |
| `APP_SUBSCRIPTION_BASIC_MONTHLY` | `199` |
| `APP_SUBSCRIPTION_PREMIUM_MONTHLY` | `499` |

### Peach Payments Hosted Checkout V2 — optional, platform account only
Single platform Peach merchant account (not per-provider). Client checkout exposes **Cash** and
**Peach**. After selecting Peach, the client chooses **Card** or **Instant EFT**; Hosted Checkout is
forced to that method (`CARD` or `PAYBYBANK`). Providers opt in to PEACH via **Provider → Settings →
payment methods**. New subscriptions are paid through Peach and activate from the signed callback.
Legacy subscription bank-details, proof-upload, plan-select, and proof-decision endpoints return
`410 Gone`; authorized historical proof-file reads are retained.

| Variable | Notes |
|----------|--------|
| `PEACH_ENABLED` | `true` to expose the "Pay online" option. Default `false`. |
| `PEACH_SANDBOX` | `true` for sandbox (`testsecure.peachpayments.com`), `false` for live. Default `true`. |
| `PEACH_CLIENT_ID` / `PEACH_CLIENT_SECRET` / `PEACH_MERCHANT_ID` | OAuth credentials for `/api/oauth/token`. |
| `PEACH_ENTITY_ID` | Checkout `authentication.entityId`. |
| `PEACH_SECRET_TOKEN` | Required when enabled; verifies every webhook/return HMAC-SHA256 signature. |

Webhook: `POST /api/public/peach/webhook`. Shopper return: `POST /api/public/peach/return`, which
verifies/processes the callback and responds with a 303 to the frontend polling route. Forwarded
headers are used to derive the public backend callback URLs; `PUBLIC_APP_BASE_URL` is the frontend
origin and the required Checkout V2 `Referer`.

In the Peach dashboard, allowlist the exact `PUBLIC_APP_BASE_URL` origin, register the public backend
webhook domain, and activate both Card and Pay by Bank/Instant EFT for the entity. These are
account-side steps; no database SQL is required during deployment.

---

## Backend — PROD

Same as UAT except:

| Variable | UAT | PROD |
|----------|-----|------|
| Profile | `uat` | `prod` |
| DB vars | `SPRING_DATASOURCE_*` or `MYSQL*` | same |
| CORS | `UAT_CORS_ORIGINS` | `PROD_CORS_ORIGINS` |
| Payments label | `UAT_PAYMENT_PROVIDER` | `PROD_PAYMENT_PROVIDER` |

---

## Frontend — UAT / PROD

| Variable | UAT example | PROD example |
|----------|-------------|--------------|
| `VITE_APP_ENV` | `UAT` | `PROD` |
| `VITE_API_BASE` | `https://backend-uat.up.railway.app` | `https://your-backend.up.railway.app` |

Leave `VITE_API_BASE` **empty** when using **docker-compose** (nginx proxies `/api` on the same host).

### Vercel (frontend) + Railway (API)

| Where | What |
|-------|------|
| Railway | MySQL + `backend/` service (`SPRING_PROFILES_ACTIVE=prod` or `uat`) |
| Vercel | Project root `frontend/`, build `npm run build:prod`, output `dist` |

1. Deploy backend on Railway first; copy its public URL.
2. On Vercel set `VITE_API_BASE` to that URL (no `/api`) and `VITE_APP_ENV=PROD`.
3. Set Railway `PUBLIC_APP_BASE_URL` and `PROD_CORS_ORIGINS` to the Vercel URL (e.g. `https://your-app.vercel.app`).
4. Redeploy backend if CORS/URL were wrong on first start.

Paste-ready lists: **[railway-env-variables.example.txt](railway-env-variables.example.txt)**

---

## First deploy

1. Deploy MySQL + backend with vars above.
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
