# risk-prediction-backend

AI-LLM Early Warning System for Agile Sprint Risk Prediction — Spring Boot 3 backend that orchestrates two external ML/LLM services (Over-Budget Risk + Requirement-Change Risk) into a unified sprint-risk profile.

## Stack

- Java 17, Spring Boot 3.2 (Web, WebFlux/WebClient, Data JPA, Validation, Cache, Security, Actuator)
- PostgreSQL 14+ (JSONB) with Flyway migrations
- Resilience4j (retry, circuit-breaker, timeout) on AI calls
- Caffeine cache (swap to Redis via `spring.cache.type=redis`)
- springdoc-openapi (Swagger UI at `/swagger-ui.html`)
- Lombok

## Module / package layout

```
com.agilerisk
├── config            // properties, WebClient, security, OpenAPI
├── controller        // REST endpoints
├── service           // orchestration, aggregation, ingestion, sprint, reporting
│   └── ai            // OverBudgetRiskClient, RequirementChangeRiskClient
├── repository        // Spring Data JPA
├── domain            // JPA entities + enums
├── dto               // request / response / ai
├── mapper            // entity → DTO
└── exception         // GlobalExceptionHandler + typed exceptions
```

## Configuration (`application.yml`)

| Env var | Purpose |
|---|---|
| `DB_URL` `DB_USER` `DB_PASSWORD` | PostgreSQL connection |
| `OVERBUDGET_API_URL` / `_PATH` / `_KEY` | Over-budget ML endpoint |
| `REQCHANGE_API_URL` / `_PATH` / `_KEY` | Requirement-change LLM endpoint |
| `JWT_ENABLED` / `JWT_SECRET` | Toggle bearer auth |

Aggregation weights and thresholds live under `aggregation.*`. Resilience4j retry/CB live under `resilience4j.*`.

## REST API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/sprints` | Create sprint |
| `GET` | `/api/v1/sprints` | List sprints |
| `GET` | `/api/v1/sprints/{id}` | Get sprint |
| `PATCH` | `/api/v1/sprints/{id}` | Update sprint |
| `DELETE` | `/api/v1/sprints/{id}` | Delete sprint |
| `POST` | `/api/v1/sprints/{id}/metrics` | Add sprint metric snapshot |
| `POST` | `/api/v1/sprints/{id}/stories` | Add user story |
| `POST` | `/api/v1/stories/{id}/tasks` | Add task |
| `POST` | `/api/v1/stories/{id}/comments` | Add comment |
| `POST` | `/api/v1/sprints/{id}/requirement-changes` | Log requirement change |
| `POST` | `/api/v1/sprints/{id}/evaluate-risk` | Run both AI models + aggregate |
| `GET` | `/api/v1/sprints/{id}/risk-summary` | Latest aggregated + per-model results |
| `GET` | `/api/v1/sprints/{id}/history` | Per-model prediction history |
| `GET` | `/api/v1/sprints/{id}/trend` | Aggregated trend points |
| `GET` | `/api/v1/sprints/compare?ids=1,2,3` | Compare latest aggregates |

Swagger UI: `http://localhost:8080/swagger-ui.html`.

## AI integration contract

Each external model must accept a JSON POST and return:

```json
{ "riskScore": 72.5, "riskLevel": "HIGH", "probability": 0.81, "explanation": "..." }
```

`riskLevel` is optional — if missing, the backend derives it from `riskScore` against `aggregation.thresholds.{medium,high}`. On retry exhaustion the client returns a `degraded` fallback (score=50, level=UNKNOWN), the audit row is still persisted, and the aggregate is bumped one severity step.

## Aggregation logic

```
overall = (w_ob * obScore + w_rc * rcScore) / (w_ob + w_rc)
level   = HIGH   if overall >= 70
        | MEDIUM if overall >= 35
        | LOW    otherwise
if either model degraded -> level.bumpUp()
```

Defaults: `w_ob = 0.55`, `w_rc = 0.45`. All values configurable in `application.yml`.

## Running locally

```bash
# 1. start postgres
docker run -d --name agilerisk-pg -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:16
# 2. create DB
docker exec -it agilerisk-pg psql -U postgres -c "CREATE DATABASE agilerisk;"
# 3. point env vars at your AI services
export OVERBUDGET_API_URL=http://localhost:9001
export REQCHANGE_API_URL=http://localhost:9002
# 4. run
./mvnw spring-boot:run
```

Flyway runs `V1__init_schema.sql` on first boot.

## Notes

- `open-in-view: false` — DTO mapping must happen inside `@Transactional` boundaries.
- Entity-level Jackson is avoided; DTOs are returned everywhere.
- Both AI calls are issued in parallel via `CompletableFuture`; orchestration time ≈ max(latencies).
- Audit row in `ai_model_responses` stores raw request/response JSONB for every call (success or failure) — full audit trail.
