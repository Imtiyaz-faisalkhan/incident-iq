# IncidentIQ — Project Plan

**Purpose:** Portfolio project and GenAI learning project  
**Current stack:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL, Docker Compose  
**Architecture:** Modular monolith first  
**Current stage:** Planning and local setup; feature development has not started

## 1. IncidentIQ in plain English

IncidentIQ is a **technical detective for software problems**.

Imagine a company's payment service suddenly starts returning errors. A developer needs to figure out why. They may have to search application logs, check recent deployments, and read troubleshooting documents. The clues are often in different places, so investigating can take time.

IncidentIQ brings those clues together. A developer describes the incident, and IncidentIQ finds relevant records, arranges them into a timeline, and helps explain possible causes with links to the evidence.

**Example:** A new version was deployed at 14:17. Database timeout errors started at 14:19. HTTP 500 errors increased at 14:20. IncidentIQ can show these events together and explain why the deployment is worth investigating. It must not claim the deployment definitely caused the issue unless the evidence supports that conclusion.

IncidentIQ is an assistant, not magic and not an auto-repair system. It should identify missing information, separate facts from guesses, and recommend next steps for a developer to review. It will not automatically change or restart real systems.

## 2. The problem we want to solve

When a service fails, developers need to answer:

1. What failed, and when did it start?
2. What changed shortly before the failure?
3. Which logs, deployments, or documents support a possible explanation?
4. What should the developer investigate next?

IncidentIQ turns this information into one investigation report.

## 3. First version (MVP)

We will begin with **synthetic/sample data**, not real company systems. This lets us demonstrate the complete workflow safely without integrations or credentials from an employer.

The first version will:

- Register services and create/view incidents.
- Store and search sample application logs within a bounded time window.
- Record deployments and show deployment history.
- Correlate incidents with nearby logs and deployments using normal deterministic code—no AI yet.
- Produce an ordered timeline that references the original records.
- Include automated tests and documented local setup.

Later, we will add document search (RAG), an LLM-generated investigation summary, and constrained AI tools. We will not build a fleet of microservices or add Kafka, Redis, Kubernetes, or a separate vector database until there is a clear reason.

## 4. How the system works

```text
Developer / REST client
          |
          v
    Spring Boot API
          |
          +--> Services and Incidents
          +--> Logs and Deployments
          +--> Investigation and Evidence
          +--> (later) Documents and AI
          |
          v
      PostgreSQL
          |
          +--> operational and investigation records
          +--> pgvector embeddings (later)

Later AI flow:
Investigation -> application-owned tools -> evidence retrieval -> LLM -> structured report
```

We will start with one Spring Boot application organised into modules. This is a **modular monolith**: one application with clear internal boundaries. It is simpler to build and operate than multiple microservices and can be split later if real needs emerge.

The LLM will not access the database directly. It will use limited application tools that validate requests and retrieve relevant records. Logs and documents are untrusted input and must not be allowed to override the AI's instructions.

## 5. Main data concepts

- **Service:** An application being monitored, such as `payment-service`.
- **Incident:** A problem affecting a service, with severity, status, description, and timestamps.
- **LogEntry:** A timestamped record, such as a database timeout or HTTP error.
- **Deployment:** A record of a service version released at a particular time.
- **Investigation:** One attempt to analyse an incident. An incident may have multiple investigations.
- **Evidence:** A reference to a log, deployment, or document passage supporting a finding.
- **Document / DocumentChunk (later):** A runbook or troubleshooting guide and the passages used for semantic search.

Use UUIDs for persisted domain records and `java.time.Instant` for timestamps, stored consistently in UTC, unless a reviewed design decision calls for something different.

## 6. API direction

Public APIs will use `/api/v1/`, JSON, validated request/response DTOs, and appropriate HTTP status codes. Implement them gradually:

| Area | Planned endpoints | Purpose |
|---|---|---|
| Services | `POST /api/v1/services`, `GET /api/v1/services/{id}` | Register and view services |
| Incidents | `POST /api/v1/incidents`, `GET /api/v1/incidents/{id}` | Create and view incidents |
| Logs | `POST /api/v1/logs`, `GET /api/v1/logs` | Add and search logs |
| Deployments | `POST /api/v1/deployments`, `GET /api/v1/services/{id}/deployments` | Record and list releases |
| Investigations | `POST /api/v1/incidents/{id}/investigations` | Start an investigation |
| Results | `GET /api/v1/investigations/{id}` plus report/evidence endpoints | Check progress and read findings |
| Documents (later) | Document ingestion/search endpoints | Prepare for RAG |

Long-running investigations should run in the background. The API should return an investigation ID so the client can check progress instead of waiting on one long HTTP request.

## 7. Delivery roadmap

### Phase 0 — Align with the existing repository
Inspect `pom.xml`, source structure, database configuration, Docker Compose, and Git status. Compare the actual project to `CLAUDE.md` and this plan. Resolve differences before implementation.

**Done when:** We understand the current baseline and agree on the first feature.

### Phase 1 — Services and Incidents
Build entities, repositories, DTOs, validation, services, controllers, consistent errors, and tests.

**Done when:** A user can create and retrieve services/incidents through tested APIs.

### Phase 2 — Logs and Deployments
Add log ingestion, bounded filtering, deployment records, history, and useful database indexes.

**Done when:** Sample logs and deployments can be queried by service and time.

### Phase 3 — Deterministic investigation
Gather records around an incident, build a timeline, and preserve references to source evidence—without an LLM.

**Done when:** A sample incident produces a traceable investigation timeline.

### Phase 4 — Investigation lifecycle and report
Track `PENDING`, `RUNNING`, `COMPLETED`, and `FAILED`; persist report/evidence records; handle missing data and partial failures.

**Done when:** An investigation can be started, checked, and retrieved through API calls.

### Phase 5 — RAG knowledge base
Ingest sample runbooks, split them into chunks, generate embeddings, store them using PostgreSQL + pgvector, and retrieve relevant passages with citations. Evaluate retrieval using known questions.

**Done when:** Relevant document passages can be found and cited for a question.

### Phase 6 — Grounded LLM analysis and agent tools
Check Spring AI compatibility with Spring Boot 4.1.1 before adding dependencies. Generate structured reports based on collected evidence. Add only a few constrained tools, such as `search_logs`, `get_deployment_history`, and `search_documentation`. Test provider failures, missing evidence, and prompt-injection attempts in logs/documents.

**Done when:** The report separates facts from hypotheses and references supporting evidence.

### Phase 7 — Production-minded hardening
Add Flyway migrations, authentication/authorisation, redaction, auditability, rate limits, observability, CI checks, integration tests, and deployment documentation. Introduce other infrastructure only when needed.

**Done when:** Another developer can follow the documentation, run and test the project, and understand its limitations.

## 8. Definition of done for each feature

A feature is complete only when it matches the agreed API/domain behavior, validates inputs, handles expected errors, has relevant tests that were actually run, does not add secrets or unrelated files, and is documented where necessary. The developer should be able to explain the design and trade-offs.

## 9. Core principles

1. **Evidence before AI:** make deterministic retrieval and correlation work first.
2. **AI advises; people decide:** no automatic remediation in the initial product.
3. **Show the source:** findings should point to supporting records.
4. **State uncertainty:** missing data and hypotheses must be visible.
5. **Start simple:** modular monolith first, extra infrastructure only when justified.
6. **Build in small steps:** inspect, implement, test, review, then continue.
7. **Protect data:** begin with synthetic data, keep secrets out of Git, and minimise/redact information sent to an external LLM.
