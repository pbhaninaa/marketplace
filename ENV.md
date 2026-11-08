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

### Login and sign-up protection — optional, defaults are safe
| Variable | Default | Notes |
|----------|---------|-------|
| `TRUSTED_PROXY_HOPS` | `1` | Proxies in front of the API that append to `X-Forwarded-For`, used to find the real client IP for rate limits. Railway only = `1` (browsers call the Railway API directly; Vercel only serves the UI). Load balancer + nginx = `2`; nothing in front = `0`. Too high lets clients spoof their IP; too low treats everyone behind the proxy as one IP. Must be 0–5 on UAT/PROD. |
| `RATE_LIMIT_ENABLED` | `true` | Per-IP limits (429 + `Retry-After`) on login, sign-up, forgot/reset password and email codes. Must stay `true` on UAT/PROD. |
| `RATE_LIMIT_LOGIN_PER_MINUTE` / `RATE_LIMIT_LOGIN_PER_HOUR` | `10` / `100` | Login attempts per IP |
| `RATE_LIMIT_REGISTER_PER_HOUR` | `10` | Provider sign-ups (and first-admin setup) per IP |
| `RATE_LIMIT_FORGOT_PASSWORD_PER_15_MIN` / `RATE_LIMIT_RESET_PASSWORD_PER_15_MIN` | `5` / `10` | Per IP |
| `RATE_LIMIT_OTP_REQUEST_PER_15_MIN` / `RATE_LIMIT_OTP_VERIFY_PER_15_MIN` | `5` / `20` | Guest client and sign-up email codes, per IP |
| `RATE_LIMIT_CHANGE_PASSWORD_PER_15_MIN` | `10` | Per IP |
| `FORGOT_PASSWORD_EMAILS_PER_HOUR` / `OTP_SENDS_PER_15_MIN` | `3` / `3` | Per email address; over the cap nothing is sent but the response is unchanged |
| `LOGIN_MAX_FAILURES` / `LOGIN_LOCK_MINUTES` | `5` / `15` | Wrong passwords in a row before the account is locked, and for how long. A password reset unlocks it. |
| `SIGNUP_REQUIRE_EMAIL_VERIFICATION` | `true` | Provider sign-up confirms the owner email with an emailed 6-digit code. Needs email sending on UAT/PROD. |
| `UNPAID_EXPIRY_CARD_MINUTES` / `UNPAID_EXPIRY_EFT_HOURS` / `UNPAID_EXPIRY_CASH_DAYS` | `60` / `72` / `14` | Unpaid orders and rentals are cancelled automatically after this long (by payment method), returning stock and freeing rental dates. `0` turns a rule off; `UNPAID_EXPIRY_ENABLED=false` turns the job off. |
| `DELIVERY_ROAD_FACTOR` / `DELIVERY_MAX_KM` | `1.3` / `1000` | Delivery is priced from the seller's pickup pin to the buyer's pin: straight-line distance × road factor × the seller's rate per km. Longer than max km is refused. |
| `SIGNUP_EXPOSE_TEST_CODE` | `true` on SIT/local, `false` elsewhere | SIT/local only: no email or SMS is sent for provider sign-up codes or guest sign-in codes; the code is returned and auto-filled on the form. UAT/PROD refuse to start if this is `true`, so real codes are always sent there. |

Rate-limit counters are in memory per API instance; account lockout is stored in the `users` table (`failed_login_attempts`, `locked_until`, added automatically by Hibernate). Password reset tokens are stored as SHA-256 hashes. New passwords must be 8–64 characters, not a common password, and must not contain the email name.

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
| `EMAIL_DOMAIN` | Verified domain (for example `example.com`); creates purpose senders such as `security@example.com` |
| `EMAIL_FROM` | Verified sender fallback when `EMAIL_DOMAIN` is unset |

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
Single platform Peach merchant account (not per-provider). Client checkout exposes **Cash**,
**Manual EFT**, and **Peach**. After selecting Peach, the client chooses **Card** or **Instant EFT**;
Hosted Checkout is forced to that method (`CARD` or `PAYBYBANK`). Providers opt in to Cash, Manual
EFT, and/or Peach via **Provider → Settings → payment methods** (Manual EFT requires bank details).
New providers get a one-time 14-day free trial; after expiry, subscriptions are paid through Peach
and activate from the signed callback. Customer cart Cash / Manual EFT / Peach is independent of
subscription billing.
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

### PayFast hosted checkout (optional, platform account only)
PayFast serves the same single online option ("Card / Instant EFT", payment method `PEACH` in the
database) as Peach. **Admin → Settings → Online payments** picks the gateway: `AUTO` (default:
PayFast when configured, else Peach), `PAYFAST` or `PEACH` (strict: if the chosen gateway is not
configured, buyers see no online option). PayFast is used for buyer orders/rentals and for provider
subscriptions. Notifications are processed for every configured gateway, so switching gateway does not
break payments already in flight.

| Variable | Purpose |
|----------|---------|
| `PAYFAST_ENABLED` | `true` to use PayFast. Default `false` (UAT/PROD), `true` (local/SIT). |
| `PAYFAST_SANDBOX` | `true` posts to `sandbox.payfast.co.za`, `false` to `www.payfast.co.za`. Forced `true` on local/SIT, default `false` on UAT/PROD. |
| `PAYFAST_MERCHANT_ID` / `PAYFAST_MERCHANT_KEY` | Merchant credentials from the PayFast dashboard. |
| `PAYFAST_PASSPHRASE` | Signature passphrase (set the same value in the PayFast dashboard). Required on UAT/PROD. |
| `PAYFAST_PUBLIC_BASE_URL` | Public https URL of this **backend**; `notify_url` = `{base}/api/public/payfast/notify`. |
| `PAYFAST_VALIDATE_WITH_SERVER` | Default `true`: every ITN is posted back to `/eng/query/validate` and must answer `VALID`. |

Per environment:

| Environment | PayFast mode | Merchant | Notes |
|-------------|--------------|----------|-------|
| local | sandbox, on by default | PayFast's public sandbox merchant `10000100` / `46f0cd694581a` / passphrase `jt7NOE43FZPn` (public test credentials, not secrets; override with `PAYFAST_*`) | PayFast cannot reach `http://localhost:8080`, so the ITN never arrives unless `PAYFAST_PUBLIC_BASE_URL` is a tunnel (e.g. `ngrok http 8080` https URL). Without it the return page keeps saying "waiting for confirmation" and the order is released after 60 minutes. |
| SIT | sandbox, on by default | same public sandbox merchant (overridable) | Set `PAYFAST_PUBLIC_BASE_URL` to the SIT backend's public URL to receive ITNs. |
| UAT | **live**, off unless `PAYFAST_ENABLED=true` | real merchant from env vars only | Startup fails if sandbox is on, the merchant id is `10000100`, a credential/passphrase is missing, or the base URL is not https. |
| PROD (and aws/azure) | **live**, off unless `PAYFAST_ENABLED=true` | real merchant from env vars only | Same startup checks as UAT. |

The same rule applies to Peach on UAT and PROD: `PEACH_ENABLED=true` with `PEACH_SANDBOX=true`
refuses to start (UAT takes real payments through the real merchant accounts, like PROD).

**ITN verification** (`POST /api/public/payfast/notify`, public, answers 200 with an empty body; 503
only if PayFast's validate endpoint could not be reached, so PayFast retries): signature over all
posted fields in the order received except `signature`, empty fields included, plus the passphrase;
`merchant_id` must match; the payment is found by `m_payment_id` (row locked); `amount_gross` must
equal the amount due to the cent; then server validation. `COMPLETE` marks the order/rental or
subscription paid (repeat ITNs change nothing), `CANCELLED` releases unpaid rentals.

**Seller settlement.** Every online order/rental payment (either gateway) records the platform fee
(**Admin → Settings → Platform fee on online orders**, default 0%) and the amount owed to the seller.
Admins pay sellers by bank transfer and record it on **Admin → Seller payouts**.

---

## Backend — PROD

Same as UAT except:

| Variable | UAT | PROD |
|----------|-----|------|
| Profile | `uat` | `prod` |
| DB vars | `SPRING_DATASOURCE_*` or `MYSQL*` | same |
| CORS | `UAT_CORS_ORIGINS` | `PROD_CORS_ORIGINS` |
| Payments label | `UAT_PAYMENT_PROVIDER` | `PROD_PAYMENT_PROVIDER` |

### Cloud profiles — AWS / Azure

| Profile | Where | Notes |
|---------|-------|-------|
| `aws` | App Runner / Elastic Beanstalk / ECS | Group activates **prod** — same variables as PROD. See `docs/aws/DEPLOY.md` |
| `azure` | App Service / Container Apps | Group activates **prod** — same variables as PROD. See `docs/azure/DEPLOY.md` |

Set `SPRING_PROFILES_ACTIVE=aws` (or `azure`). `DeployedProfileValidator` treats both like PROD, so
`APP_JWT_SECRET`, `PROD_CORS_ORIGINS`, `PUBLIC_APP_BASE_URL` and SendGrid must be set. Point
`SPRING_DATASOURCE_URL` at RDS / Azure Database for MySQL (`jdbc:mysql://...?useSSL=true`).
Health check path: `/actuator/health` (liveness only: `/actuator/health/liveness`).

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
| `SENDGRID_API_KEY` / `EMAIL_DOMAIN` (or `EMAIL_FROM`) | Optional local email |

Run: `mvn spring-boot:run -Dspring-boot.run.profiles=local`

---

## Tests

```bash
cd backend && mvn test
cd frontend && npm run test:run
```

---

See [DEPLOYMENT.md](DEPLOYMENT.md) and [.env.prod.example](.env.prod.example).
