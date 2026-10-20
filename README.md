# RoseIAM

RoseIAM is a single-organization workforce IAM REST API. It provides email/password authentication, employee invitations, RBAC, profile and settings APIs, and audit history. The repository contains no frontend.

## Local requirements

- Java 25
- Docker with Compose
- Maven is optional; use the included Maven Wrapper

## Run locally

```sh
cp .env.sample .env
docker compose up --wait
./mvnw spring-boot:run
```

PostgreSQL is available on port `5432`. Mailpit accepts SMTP on `localhost:1025`; inspect messages at <http://localhost:8025>. Database tables are created by Flyway migrations. Persistence uses MyBatis-Plus Mapper interfaces; schema changes belong in versioned migrations.

The readiness probe is `GET /actuator/health/readiness`; it includes the database health check. Health responses expose component status only and never connection details.

## Initialize the first platform administrator

Set `APP_BOOTSTRAP_ADMIN_EMAIL` and start the API to run the one-time bootstrap:

```sh
APP_BOOTSTRAP_ADMIN_EMAIL=admin@example.com ./mvnw spring-boot:run
```

The command creates an invitation and sends a verification link to Mailpit. The employee sets a password and name when accepting the invitation. No shared default password is created. Re-running initialization after a platform administrator exists reports that initialization is already complete and does not overwrite data.

For a containerized run, set `APP_BOOTSTRAP_ADMIN_EMAIL` in `.env` before running `docker compose -f compose.full.yaml up --build -d`.

## Configuration

Copy `.env.sample` to `.env` for local values. Production secrets belong in the deployment secret manager. Configure exact browser origins with `APP_CORS_ALLOWED_ORIGINS`; an empty value rejects all cross-origin requests. Configure the SMTP password encryption key separately before enabling SMTP authentication. SMTP credentials are never returned by the API.

See [`docs/api.md`](docs/api.md), the [PRD](docs/prd/iam-management-platform.md), [domain design](docs/design/iam-domain.md), and approved [specifications and plans](docs/sdd/breakdown.md) for the API contract and behavior.

## Verify

```sh
./mvnw test
./mvnw verify
docker compose config
docker compose -f compose.full.yaml config
```

## Database backup and restore

Back up the local database with `docker compose exec -T postgres pg_dump -U roseiam roseiam > roseiam.sql`. Restore into an empty database with `docker compose exec -T postgres psql -U roseiam -d roseiam < roseiam.sql`. For production, use the deployment database's managed backup and point-in-time recovery facilities; keep backup files encrypted and test restores before relying on them.
