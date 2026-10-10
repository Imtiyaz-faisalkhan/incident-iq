# IncidentIQ

![CI](https://github.com/Imtiyaz-faisalkhan/incident-iq/actions/workflows/ci.yml/badge.svg)

AI-assisted incident investigation platform for microservices. See [PROJECT_PLAN.md](PROJECT_PLAN.md) for the roadmap.

## Run locally

Requires Java 21 and Docker.

```bash
docker compose up -d        # PostgreSQL (pgvector) on 127.0.0.1:5434
./mvnw spring-boot:run      # API on http://localhost:8080
```

## Test

```bash
./mvnw verify               # unit, MVC and Testcontainers integration tests
```
