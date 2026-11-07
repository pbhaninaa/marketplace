# Deploy MarketPlace on Azure

Branch **`Azure`** drives Azure deploys. Railway + Vercel (see `DEPLOYMENT.md`) remain the default; this is
an alternative target.

| Part | Service | Config |
|------|---------|--------|
| Web (Vue SPA) | **Static Web Apps** | `frontend/public/staticwebapp.config.json`, `.github/workflows/azure-web-staticwebapp.yml` |
| API (option 1, default) | **App Service** (Java SE 17, JAR) | `.github/workflows/azure-api-appservice.yml` |
| API (option 2) | **Container Apps** (image in ACR) | `backend/Dockerfile`, `.github/workflows/azure-api-containerapps.yml` |
| Database | **Azure Database for MySQL – Flexible Server** | `SPRING_DATASOURCE_*` |

Every workflow runs the tests (`mvn -B package` / `mvn -B test`, `npm run test:run`) before it deploys.

---

## 1. Database — Azure Database for MySQL

1. Create a Flexible Server (MySQL 8, Burstable B1ms to start) and database `marketplace`.
2. Networking: allow the App Service / Container Apps outbound IPs (or use VNet integration).
3. JDBC URL: `jdbc:mysql://<server>.mysql.database.azure.com:3306/marketplace?useSSL=true&serverTimezone=UTC&characterEncoding=UTF-8`

## 2. API application settings

Same names as PROD (`ENV.md`, `.env.prod.example`), plus the profile:

| Setting | Value |
|---------|-------|
| `SPRING_PROFILES_ACTIVE` | `azure` (activates `prod` too; validated like PROD) |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Flexible Server values |
| `APP_JWT_SECRET` | 32+ random characters |
| `PROD_CORS_ORIGINS` | Static Web App / custom domain URL(s), comma-separated |
| `PUBLIC_APP_BASE_URL` | Public web URL, no trailing slash |
| `SENDGRID_API_KEY`, `EMAIL_DOMAIN` (or `EMAIL_FROM`) | SendGrid |
| Optional | `PROD_PAYMENT_PROVIDER`, `APP_BANK_*`, `APP_SUBSCRIPTION_*`, `PEACH_*`, `TWILIO_*` |

Prefer Key Vault references (`@Microsoft.KeyVault(...)`) for secrets; never commit them.

Health check path: **`/actuator/health`** (use `/actuator/health/liveness` if the DB is slow to warm up).

## 3a. API on App Service (default)

1. Create a Web App: *Publish: Code*, *Runtime stack: Java 17*, *Java web server stack: Java SE*, Linux,
   plan B1 or larger.
2. *Configuration → Application settings*: values from step 2. App Service injects `PORT`, which the
   prod profile binds to. Enable *Always On*.
3. *Monitoring → Health check*: path `/actuator/health`.
4. Download the publish profile → secret `AZURE_WEBAPP_PUBLISH_PROFILE`; app name → `AZURE_WEBAPP_NAME`.
5. Push to `Azure`: the workflow runs `mvn -B package` (tests included) and deploys `app.jar`.

## 3b. API on Container Apps (alternative)

Set repository variable `AZURE_API_TARGET=containerapps` so pushes deploy here instead of App Service
(manual dispatch always works).

1. Create an Azure Container Registry (admin user enabled, or use a service principal with `AcrPush`).
2. Create a Container Apps environment + app: ingress external, **target port 8080**, env vars from
   step 2, health probes HTTP `/actuator/health` (liveness `/actuator/health/liveness`), min replicas 1.
3. Create a service principal for the resource group and save its JSON as `AZURE_CREDENTIALS`:
   `az ad sp create-for-rbac --name marketplace-gh --role contributor --scopes /subscriptions/<id>/resourceGroups/<rg> --sdk-auth`
4. Push to `Azure`: tests run, `backend/Dockerfile` (two-stage, non-root, `HEALTHCHECK`) is built, pushed
   as `marketplace-api:<sha>` and `:latest`, and the container app is updated to the new image.

## 4. Web on Static Web Apps

1. Create a Static Web App (Free plan is fine), deployment source *Other* (the workflow deploys).
2. Copy the deployment token → secret `AZURE_STATIC_WEB_APPS_API_TOKEN`.
3. Repository **variable** `VITE_API_BASE=https://<api-host>` (origin only, no `/api`).
4. `frontend/public/staticwebapp.config.json` ships in `dist/`: SPA navigation fallback to `/index.html`,
   long cache for `/assets/*`, and the same security headers as `frontend/vercel.json`.
5. Add the Static Web App URL to the API's `PROD_CORS_ORIGINS` and `PUBLIC_APP_BASE_URL`.

## GitHub secrets / variables

| Name | Kind | Used by |
|------|------|---------|
| `AZURE_WEBAPP_NAME`, `AZURE_WEBAPP_PUBLISH_PROFILE` | secret | App Service |
| `AZURE_CREDENTIALS` | secret | Container Apps |
| `ACR_LOGIN_SERVER`, `ACR_USERNAME`, `ACR_PASSWORD` | secret | Container Apps |
| `AZURE_RESOURCE_GROUP`, `AZURE_CONTAINERAPP_NAME` | secret | Container Apps |
| `AZURE_STATIC_WEB_APPS_API_TOKEN` | secret | Static Web Apps |
| `VITE_API_BASE` | variable | Static Web Apps build |
| `AZURE_API_TARGET` | variable (`appservice` default / `containerapps`) | push routing |

## Smoke test

```bash
curl -fsS https://<api-host>/actuator/health      # {"status":"UP"}
curl -fsSI https://<web-host>/some/deep/link      # 200 (navigation fallback) + security headers
```
