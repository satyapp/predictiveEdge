# PredictiveEdge

PredictiveEdge is a production-ready enterprise starter built with Java 21 and Spring Boot 3.5.x using a Maven multi-module architecture.

## Repository structure

- `backend/predictiveedge-parent`
  - `predictiveedge-common`
  - `predictiveedge-domain`
  - `predictiveedge-api`
- `docker`
- `docs`
- `infrastructure`
- `frontend`
- `sample-data`
- `postman`
- `tools`

## Technology stack

- Java 21
- Spring Boot 3.5.x
- Spring Data JPA
- Spring Boot Actuator
- Flyway
- PostgreSQL
- Lombok
- Docker Compose

## Backend modules

- `predictiveedge-common`: shared utilities and cross-cutting contracts
- `predictiveedge-domain`: clean domain model and business abstractions
- `predictiveedge-api`: Spring Boot application, REST API, persistence, Flyway migration, and actuator configuration

## Run locally

### 1. Start infrastructure

```powershell
docker compose up -d
```

### 2. Build the backend

```powershell
cd backend/predictiveedge-parent
mvn clean test
```

If Maven is not installed globally, use the Maven Wrapper once it is added to the project.

### 3. Run the API

```powershell
cd backend/predictiveedge-parent/predictiveedge-api
mvn spring-boot:run
```

### 4. Check health

- Application health: `http://localhost:8080/api/health`
- Actuator health: `http://localhost:8080/actuator/health`
- Adminer: `http://localhost:8081`

## Configuration

The default `application.yml` expects PostgreSQL on `localhost:5432` with:

- database: `predictiveedge`
- username: `predictiveedge`
- password: `predictiveedge`

## Notes

- The project follows Clean Architecture by keeping the domain and common modules independent of Spring Boot runtime concerns.
- Flyway migration files live under `backend/predictiveedge-parent/predictiveedge-api/src/main/resources/db/migration`.
- A Maven Wrapper should be generated or copied into `backend/predictiveedge-parent` for fully self-contained builds.