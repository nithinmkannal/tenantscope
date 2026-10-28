# TriageLens

[![CI](https://github.com/nithinmkannal/triagelens/actions/workflows/ci.yml/badge.svg)](https://github.com/nithinmkannal/triagelens/actions/workflows/ci.yml)

Incident triage and tenant diagnostics service for multi-tenant SaaS support teams.

When a customer reports a problem, the support engineer usually has to dig through several backend
systems to answer: *is this tenant configured correctly, and are their documents actually processing?*
TriageLens answers that in one API call and records the answer on the incident timeline. It also exposes
the aggregated numbers a support lead needs (open/urgent/SLA-breached incidents, MTTA, MTTR, trends).

**Stack:** Java 17, Spring Boot 3.4, Spring Data JPA (Hibernate), PostgreSQL 16, Flyway, OpenAPI/Swagger,
JUnit 5, Testcontainers, Docker, Kubernetes, GitHub Actions.

## Tech stack

| Layer | Technology | How it is used here |
|---|---|---|
| Language | Java 17 | Whole codebase; Maven build targets Java 17 |
| Framework | Spring Boot 3.4.5 | Embedded Tomcat, auto-configuration, externalised config via `application.yml` and env vars |
| REST API | Spring Web (MVC), Bean Validation | Versioned `/api/v1` endpoints, DTO request validation, central `@RestControllerAdvice` error handling with one error shape |
| Persistence | Spring Data JPA, Hibernate, HikariCP | Entities, repositories, custom `@Query` methods (including a native query) for aggregation, `@Transactional` service methods |
| Database | PostgreSQL 16 | Relational store for tenants, tenant config, documents, incidents and timeline notes |
| Schema migrations | Flyway | Versioned SQL in [`db/migration`](src/main/resources/db/migration), applied on startup |
| Security | Servlet filter | API-key authentication (`X-API-Key`) with constant-time comparison; Actuator health left open for probes |
| API docs | springdoc-openapi (Swagger UI) | Auto-generated OpenAPI spec and interactive docs |
| Observability | Spring Boot Actuator | Health, liveness and readiness endpoints used by Kubernetes probes |
| Testing | JUnit 5, AssertJ, Spring Boot Test, Testcontainers | Unit tests for the state machine and finding rules; integration tests against a real PostgreSQL container |
| Build | Maven | Single module, Spring Boot Maven Plugin produces the executable jar |
| Containers | Docker (multi-stage), Docker Compose | Maven build stage plus a slim JRE Alpine runtime image running as a non-root user; Compose runs app and database |
| Orchestration | Kubernetes | Deployment, StatefulSet, Service, HPA, PodDisruptionBudget, Ingress, ConfigMap/Secret, CronJob |
| CI | GitHub Actions | Runs `mvn verify` (including Testcontainers tests) and builds the Docker image on every push and pull request |

### Engineering concepts demonstrated
- **Layered architecture:** controller, service, repository and domain layers, with DTOs separating the API from entities.
- **Domain modelling:** an incident state machine with enforced transitions, and rule-based priority, routing and SLA logic.
- **Multi-tenancy:** per-tenant configuration and document data, and diagnostics scoped to one tenant.
- **Data protection:** sensitive tenant config values are masked in diagnostics output.
- **Operability:** health probes, resource limits, autoscaling and a CronJob that fails when SLAs are breached.
- **Testing strategy:** fast unit tests plus integration tests against real infrastructure instead of an in-memory substitute.

## What it does

| Area | Behaviour |
|---|---|
| Incident lifecycle | `NEW -> ACKNOWLEDGED -> IN_PROGRESS -> RESOLVED -> CLOSED` state machine; illegal moves return `409`; reopening a resolved incident is allowed |
| Routing and priority | Incident is auto-assigned to the owning team of the affected service; **PLATINUM** tenants get priority raised one level, with a system note explaining why |
| SLA | Resolve target derived from priority (P1 4h, P2 8h, P3 24h, P4 72h); each incident reports `slaBreached` |
| Tenant diagnostics | Config export grouped by service, **sensitive values masked**, document stats per type, latest failures, and rule-based findings (missing/empty required settings, inactive tenant, elevated/critical failure rate with a minimum sample size) |
| Incident diagnostics | `POST /diagnostics/incidents/{number}` runs the report for the incident's tenant and appends a summary note to the timeline |
| Dashboard | Open / urgent / breached / aging counts, MTTA, MTTR, breakdowns by priority, service, team, category, daily created-vs-resolved trend, latest P1s |
| Security | `X-API-Key` required on `/api/**` (constant-time comparison); actuator health is open for probes |

## Run it

### Docker Compose (app + PostgreSQL, seeded demo data)
```bash
docker compose up --build
# API:     http://localhost:8085   (host port 8085 -> container 8080)
# Swagger: http://localhost:8085/swagger-ui.html
curl -H "X-API-Key: dev-key-change-me" http://localhost:8085/api/v1/diagnostics/tenants/CONTOSO
curl -H "X-API-Key: dev-key-change-me" http://localhost:8085/api/v1/dashboard/summary
```
Seeded tenants: `NORTHWIND` (healthy, PLATINUM), `CONTOSO` (missing config + 35% failures), `FABRIKAM`
(elevated invoice failures), `LEGACYCO` (inactive).

### Local development
```bash
docker compose up -d db            # PostgreSQL on localhost:5433
DB_URL=jdbc:postgresql://localhost:5433/triagelens TRIAGELENS_SEED=true mvn spring-boot:run
```

### Kubernetes
Manifests in [`k8s/`](k8s): namespace, PostgreSQL StatefulSet (PVC), app Deployment (2 replicas,
startup/liveness/readiness probes on Actuator, non-root, resource limits), Service, PodDisruptionBudget,
HPA, Ingress, and an `sla-watchdog` CronJob that calls the dashboard API every 15 minutes and fails
when SLA breaches exist (so the failure surfaces in `kubectl get jobs` or any alerting on job status).
```bash
docker build -t triagelens:1.0.0 .
# kind/minikube: load the image first, e.g.  kind load docker-image triagelens:1.0.0
kubectl apply -f k8s/
kubectl -n triagelens port-forward svc/triagelens 8085:80
```
The Secrets in `k8s/` hold placeholder values. Replace them before any real use.

## API summary

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/incidents` | Create incident (routing + priority rules applied) |
| GET | `/api/v1/incidents` | Search: `state`, `priority`, `serviceArea`, `tenant`, `page`, `size` |
| GET | `/api/v1/incidents/{number}` | Incident with timeline |
| POST | `/api/v1/incidents/{number}/transition` | Change state (`409` if not allowed) |
| POST | `/api/v1/incidents/{number}/notes` | Add comment / work note |
| GET | `/api/v1/diagnostics/tenants/{code}?windowDays=7` | Config export + health findings |
| POST | `/api/v1/diagnostics/incidents/{number}` | Same, resolved from an incident, recorded on the timeline |
| GET | `/api/v1/dashboard/summary?days=14` | Aggregated support metrics |

Errors share one shape: `{"error": "<CODE>", "message": "..."}`.

## Tests
```bash
mvn test
```
Unit tests cover the state machine, SLA maths and finding rules. The integration suite runs the whole
application against a real PostgreSQL container via Testcontainers (Docker required). The surefire
config pins `api.version=1.44` so Testcontainers works with Docker Engine 29+.

## Configuration

| Env var | Default | Meaning |
|---|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | local PostgreSQL | datasource |
| `TRIAGELENS_API_KEYS` | `dev-key-change-me` | comma-separated accepted API keys |
| `TRIAGELENS_SEED` | `false` | load synthetic demo data when the DB is empty |
