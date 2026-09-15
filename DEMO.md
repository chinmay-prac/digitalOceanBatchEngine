# Deployment and Demo Runbook

## Recommended path: GitHub to App Platform

This repository does not need DigitalOcean Container Registry. App Platform can
read branch `v1`, build `Dockerfile`, provision PostgreSQL, bind its credentials,
run Flyway, and expose the web service using `.do/app.yaml`.

### One-time setup

1. In DigitalOcean, open **App Platform** and connect the GitHub account.
2. Grant DigitalOcean access to `chinmay-prac/digitalOceanBatchEngine`.
3. In DigitalOcean **API → Tokens**, create a token with write access.
4. In GitHub, open **Settings → Secrets and variables → Actions**.
5. Add repository secret `DIGITALOCEAN_ACCESS_TOKEN` with that token.

Do not commit the token or database credentials.

### Deploy from GitHub Actions

1. Push the demo commit to branch `v1`.
2. Open **Actions → CI** and confirm the `verify` job is green.
3. Open **Actions → Deploy Demo → Run workflow**.
4. Select branch `v1` and run it.
5. Open the workflow summary for the generated App Platform URL.

`Deploy Demo` reruns Maven verification, creates the app on its first run,
updates it on later runs, waits for deployment, and calls `/actuator/health`.

### Deploy from a terminal

Use this if GitHub Actions setup is not available:

```bash
doctl auth init
doctl apps create --spec .do/app.yaml --wait
```

For later updates:

```bash
APP_ID="$(doctl apps list --format ID,Spec.Name --no-header \
  | awk '$2 == "batch-inference-engine" {print $1; exit}')"
doctl apps update "$APP_ID" \
  --spec .do/app.yaml \
  --update-sources \
  --wait
```

The app spec uses the database bindable variables
`${batch-db.JDBC_DATABASE_URL}`, `${batch-db.USERNAME}`, and
`${batch-db.PASSWORD}`. If configuring the app manually in the Control Panel,
use `JDBC_DATABASE_URL`, `DB_USERNAME`, and `DB_PASSWORD`. A plain
`DATABASE_URL=postgresql://...` is not the JDBC URL expected by this application.

## Test before the demo

### Automated tests

```bash
./mvnw --batch-mode clean verify
```

This covers JSON and TXT ingestion, real HTTP 429 handling, retry/backoff,
bounded concurrency, overload behavior, persistence, recovery, status, and
ordered results.

### Local Docker test

```bash
docker build -t batch-inference-engine .
docker run --rm --name batch-inference-demo -p 8080:8080 \
  batch-inference-engine
```

In a second terminal:

```bash
bash scripts/demo-smoke.sh http://localhost:8080
```

### Deployed test

```bash
bash scripts/demo-smoke.sh https://YOUR-APP.ondigitalocean.app
```

## Five-minute demonstration

1. Show a green GitHub Actions CI run.
2. Show `/actuator/health` returning `UP`.
3. Run `scripts/demo-smoke.sh` against the deployed URL.
4. Point out that prompt index `3` receives HTTP 429 on attempt 1 and succeeds
   on attempt 2.
5. Show ordered results and the persisted `attempts` field.
6. Explain that a semaphore bounds accepted work, a fixed executor bounds
   concurrency, PostgreSQL stores state, and Flyway owns schema creation.

## If deployment fails

- `401 Unable to authenticate`: replace `DIGITALOCEAN_ACCESS_TOKEN`.
- Repository access error: reconnect GitHub under App Platform and grant access.
- Database connection error: verify all three JDBC database bindings in the app.
- Flyway error: inspect the app runtime logs for migration `V1`.
- Health timeout: inspect build/runtime logs and verify service port `8080`.
- Existing app name conflict: update the existing `batch-inference-engine` app
  instead of creating a second app.
