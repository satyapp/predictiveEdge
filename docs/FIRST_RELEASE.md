# First Release Scope

## Included

- Java 21 multi-module build
- Platform Core executable application
- Public API and actuator health checks
- Default-deny HTTP security outside health checks
- PostgreSQL migration bootstrap
- Redis connectivity
- React web shell
- Docker images and a local Compose deployment
- CI for backend tests, frontend build/tests, and container builds

## Not included

- User registration, login, sessions, or token issuance
- Broker integrations or stored broker credentials
- AI prediction services
- Trade execution and portfolio lifecycle services
- Kubernetes manifests or a production hosting target

Those capabilities require domain implementation, security review, integration tests, and an explicit deployment environment before they can be called deployable.

## Verification gates

1. `mvn clean verify`
2. `npm ci && npm test && npm run build` from the web application directory
3. `docker compose config --quiet`
4. `docker compose build platform-core web`
5. `docker compose up -d` followed by successful web and health requests
