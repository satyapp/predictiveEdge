# PredictiveEdge

PredictiveEdge is an early-stage trading platform built with Java 21, Spring Boot, React, PostgreSQL, Redis, and Apache Kafka. The first deployable release is **Platform Core**: the identity boundary, platform health API, database migration baseline, event backbone bootstrap, and web shell.

The AI and trade-lifecycle engines remain roadmap items. They are intentionally excluded from the first release until executable modules and tests exist.

## First release

The deployable stack contains:

- `platform-core`: Spring Boot application on port `8080`
- `web`: React application served by Nginx on port `3000`
- `postgres`: authoritative relational store
- `redis`: cache and short-lived state store
- `kafka`: local KRaft event broker with versioned PredictiveEdge topic families

Public checks:

- Web: <http://localhost:3000>
- API health: <http://localhost:8080/api/health>
- Actuator health: <http://localhost:8080/actuator/health>

All application routes other than health checks are denied until the identity flows are implemented.

## Build and test

Prerequisites: Java 21, Maven 3.9+, Node.js 22+, and Docker.

```powershell
mvn clean verify

cd frontend/apps/web/predictiveedge-web
npm ci
npm test
npm run build
```

## Run the release stack

For local development, the Compose file supplies non-production fallback passwords. To choose your own values:

```powershell
Copy-Item .env.example .env
# Edit .env before starting the stack.
```

Build and start everything:

```powershell
docker compose up --build -d
docker compose ps
```

Stop the stack without deleting database volumes:

```powershell
docker compose down
```

Production deployments must inject PostgreSQL, Redis, and Kafka connection/security
settings through the deployment environment or secret store. The Compose Kafka
listener is plaintext and intended only for local development. Never commit
`.env`, broker credentials, signing keys, or API keys.

When running Platform Core directly, Kafka defaults to `localhost:9092`. Topic
provisioning is disabled by default and is enabled explicitly by local Compose.
Production topic partition and replication settings must be managed by the
deployment platform rather than application startup.

## Repository layout

```text
backend/predictiveedge-parent/   Java modules and Platform Core application
frontend/apps/web/               React web application
docker/                          Release container definitions
docs/                            Architecture and design material
.github/workflows/ci.yml         Verified first-release CI pipeline
docker-compose.yml               Local release stack
```

## Release boundary

This release proves reproducible builds, database migrations, health reporting, secure route defaults, and container deployment. Login, user persistence, broker integration, AI predictions, and trade execution are not yet claimed as complete.
