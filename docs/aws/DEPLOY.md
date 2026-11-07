# Deploy MarketPlace on AWS

Branch **`AWS`** drives AWS deploys. Railway + Vercel (see `DEPLOYMENT.md`) remain the default; this is an
alternative target.

| Part | Service | Config |
|------|---------|--------|
| Web (Vue SPA) | **AWS Amplify Hosting** | `amplify.yml`, `frontend/customHttp.yml`, `docs/aws/amplify-custom-rules.json` |
| API (option 1, default) | **App Runner** from an ECR image | `backend/Dockerfile`, `.github/workflows/aws-api-apprunner.yml` |
| API (option 2) | **Elastic Beanstalk** (Docker, pre-built JAR) | `backend/eb/`, `scripts/aws/package-eb-docker.*`, `.github/workflows/aws-api-elastic-beanstalk.yml` |
| Database | **RDS for MySQL 8** | `SPRING_DATASOURCE_*` |

Every workflow runs the tests (`mvn -B test` / `mvn -B package`, `npm run test:run`) before it deploys.

---

## 1. Database — RDS for MySQL

1. Create an RDS MySQL 8 instance (db.t4g.micro is enough to start), database `marketplace`.
2. Allow inbound 3306 from the API only (App Runner VPC connector or the EB security group).
3. JDBC URL: `jdbc:mysql://<endpoint>:3306/marketplace?useSSL=true&serverTimezone=UTC&characterEncoding=UTF-8`

## 2. API environment variables

Same names as PROD (`ENV.md`, `.env.prod.example`), plus the profile:

| Variable | Value |
|----------|-------|
| `SPRING_PROFILES_ACTIVE` | `aws` (activates `prod` too; validated like PROD) |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | RDS values |
| `APP_JWT_SECRET` | 32+ random characters |
| `PROD_CORS_ORIGINS` | Amplify / custom domain URL(s), comma-separated |
| `PUBLIC_APP_BASE_URL` | Public web URL, no trailing slash |
| `SENDGRID_API_KEY`, `EMAIL_DOMAIN` (or `EMAIL_FROM`) | SendGrid |
| Optional | `PROD_PAYMENT_PROVIDER`, `APP_BANK_*`, `APP_SUBSCRIPTION_*`, `PEACH_*`, `TWILIO_*` |

Store secrets in App Runner / EB environment properties (or Secrets Manager references), never in git.

Health check path: **`/actuator/health`** (use `/actuator/health/liveness` if the DB is slow to warm up).

## 3a. API on App Runner (default)

1. Create an ECR repository, e.g. `marketplace-api`.
2. Add GitHub secrets (below) and push to `AWS` — the workflow tests, builds `backend/Dockerfile`
   (two-stage, non-root, `HEALTHCHECK`) and pushes `:<sha>` and `:latest`.
3. Create the App Runner service from `<registry>/marketplace-api:latest`: port **8080**, health check
   HTTP `/actuator/health`, env vars from step 2, 1 vCPU / 2 GB.
4. Copy the service ARN into secret `APP_RUNNER_SERVICE_ARN`; later pushes call `start-deployment`.

## 3b. API on Elastic Beanstalk (alternative)

Set repository variable `AWS_API_TARGET=elasticbeanstalk` so pushes deploy here instead of App Runner
(manual dispatch always works).

1. Create an EB application + environment: platform **Docker running on 64bit Amazon Linux 2023**,
   instance **t3.small** or larger (t3.micro runs out of memory).
2. Create an S3 bucket for application versions (or reuse the EB default bucket).
3. Set env vars from step 2 in *Configuration → Updates, monitoring, and logging → Environment properties*.
   `.ebextensions/01-health-and-timeout.config` sets `SPRING_PROFILES_ACTIVE=aws`, the
   `/actuator/health` health check and a long deploy timeout.
4. The workflow runs `scripts/aws/package-eb-docker.sh`: `mvn -B package` (tests included) → zip of
   `app.jar` + runtime-only `backend/eb/Dockerfile` + `Dockerrun.aws.json` + `.ebextensions`. EB only
   builds a tiny image, avoiding instance timeouts.

Manual upload: run `scripts/aws/package-eb-docker.ps1` (Windows) or `.sh`, then *Upload and deploy*
`backend/eb-docker-deploy.zip`.

## 4. Web on Amplify

1. Amplify console → *New app → Host web app* → GitHub → this repo, branch **`AWS`**. Monorepo: app root
   `frontend` (picked up from `amplify.yml`).
2. Environment variables: `VITE_API_BASE=https://<api-host>` (origin only, no `/api`), `VITE_APP_ENV=PROD`.
3. *Rewrites and redirects*: add the SPA rule from `docs/aws/amplify-custom-rules.json`
   (or `aws amplify update-app --app-id <id> --custom-rules file://docs/aws/amplify-custom-rules.json`).
4. Security headers come from `frontend/customHttp.yml` (mirrors `frontend/vercel.json`). If the console
   does not detect it, paste it under *Custom headers*.
5. Add the Amplify URL to the API's `PROD_CORS_ORIGINS` and `PUBLIC_APP_BASE_URL`.

## GitHub secrets / variables

| Name | Kind | Used by |
|------|------|---------|
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION` | secret | both API workflows |
| `ECR_REPOSITORY` | secret | App Runner |
| `APP_RUNNER_SERVICE_ARN` | secret (optional) | App Runner |
| `EB_APPLICATION_NAME`, `EB_ENVIRONMENT_NAME`, `EB_S3_BUCKET` | secret | Elastic Beanstalk |
| `AWS_API_TARGET` | variable (`apprunner` default / `elasticbeanstalk`) | push routing |

Use a dedicated IAM user (or OIDC role) limited to ECR push, `apprunner:StartDeployment`, S3 put to the
EB bucket and `elasticbeanstalk:*` on the one application.

## Smoke test

```bash
curl -fsS https://<api-host>/actuator/health      # {"status":"UP"}
curl -fsSI https://<web-host>/some/deep/link      # 200 (SPA rewrite) + security headers
```
