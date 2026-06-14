# FCRefSystem

Минимальная demo-ready версия внутренней системы отбора кандидатов в закрытое сообщество.

## Stack

- Java 17;
- Spring Boot, Spring Security, JDBC;
- H2 in-memory database for MVP users and roles;
- Maven;
- REST API over JSON;
- static HTML/CSS/JS interface served by the backend.

## Structure

- `backend/` - Java/Spring Boot application and backend Maven module.
- `frontend/` - static HTML/CSS/JS interface and UI assets.
- `tests/backend/` - backend unit and API tests.
- `figma/` - Figma prototype plugin sources.
- source PDF documents are local-only and ignored by Git.

## Run

```bash
./mvnw package
java -jar backend/target/fc-ref-system-0.1.0-SNAPSHOT.jar
```

On Windows PowerShell use `.\mvnw.cmd` instead of `./mvnw`.

After startup:

- UI: `http://localhost:8080/`
- health: `http://localhost:8080/actuator/health`
- OpenAPI: `http://localhost:8080/openapi.yaml`
- protected API example: `curl -u member:member http://localhost:8080/api/snapshot`

The port can be changed with a startup argument:

```bash
java -jar backend/target/fc-ref-system-0.1.0-SNAPSHOT.jar --server.port=8090
```

## Demo Users

All passwords are development-only and stored for MVP demonstration.

| Login | Password | Role | Main UC |
| --- | --- | --- | --- |
| `member` | `member` | MEMBER | UC-01 create invitation |
| `admin` | `admin` | ADMIN | UC-02 manage regulation, open/close voting |
| `privileged` | `privileged` | PRIVILEGED_MEMBER | UC-03 vote, create complaint |
| `interviewer` | `interviewer` | INTERVIEWER | UC-04 block candidate, record verdict |
| `candidate` | `candidate` | CANDIDATE | UC-05 submit stage result |

Public invitation activation is available without login. Seed token: `bk-seed-active`.
Activation creates a candidate account and returns a generated login/password in the UI.

## MVP Scope

Implemented Use Cases:

- UC-01: member creates referral invitation; candidate activates invitation publicly.
- UC-02: admin creates active selection regulation.
- UC-03: privileged member casts a vote; admin opens and closes voting.
- UC-04: interviewer/admin blocks a candidate; admin unblocks.
- UC-05: candidate submits current stage result; interviewer/admin records verdict.

Other screens are intentionally minimal and use interface placeholders where the future release scope is not implemented yet.

## Test

```bash
./mvnw test
```

Tests cover domain rules, Basic Auth API access, seeded MVP state and public invitation activation.

## Lint

```bash
./mvnw -pl backend checkstyle:check
cd frontend
npm ci
npm run lint
```

On Windows PowerShell with script execution disabled, use `npm.cmd run lint`.
Backend lint uses Checkstyle and checks Java sources plus backend tests. Frontend lint uses ESLint, Stylelint and HTMLHint.

## CI

GitHub Actions workflow `.github/workflows/ci.yml` runs backend Checkstyle, backend tests, backend package build and frontend lint.
