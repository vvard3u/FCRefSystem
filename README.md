# FCRefSystem

Минимальная demo-ready версия внутренней системы отбора кандидатов в закрытое сообщество.

## Stack

- Java 17;
- Spring Boot, Spring Security, JDBC;
- PostgreSQL 16 for demo data;
- H2 in-memory database for automated tests;
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

Start PostgreSQL 16 first. The repository includes a local demo compose file:

```bash
docker compose up -d postgres
```

Default database settings:

- URL: `jdbc:postgresql://localhost:5432/fcref`
- user: `fcref`
- password: `fcref`

```bash
./mvnw package
java -jar backend/target/fc-ref-system-0.1.0-SNAPSHOT.jar
```

On Windows PowerShell use `.\mvnw.cmd` instead of `./mvnw`.

After startup:

- UI: `http://localhost:8080/`
- candidate invitation activation: `http://localhost:8080/activate.html`
- health: `http://localhost:8080/actuator/health`
- OpenAPI: `http://localhost:8080/openapi.yaml`
- protected API example: `curl -u member:member http://localhost:8080/api/snapshot`

The port can be changed with a startup argument:

```bash
java -jar backend/target/fc-ref-system-0.1.0-SNAPSHOT.jar --server.port=8090
```

Database settings can be changed with environment variables:

- `DATABASE_URL`
- `DATABASE_DRIVER`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`

## Database Inspection

The application mirrors the demo selection state to PostgreSQL tables after every business event.

Useful tables for defense/demo:

- `app_users`, `app_user_roles` - accounts and roles.
- `invitations` - referral invitations and activation status.
- `candidates` - candidate status and current stage.
- `candidate_stage_progress` - stage attempts, submitted results, assigned interviewer and decisions.
- `voting_sessions`, `votes` - opened votes, thresholds, decisions and member votes.
- `block_records`, `complaints` - blocking and complaint flow.
- `selection_events` - chronological audit log.

Example:

```bash
psql postgresql://fcref:fcref@localhost:5432/fcref
select id, full_name, status, current_stage_id from candidates;
select event_type, actor_user_id, candidate_id, occurred_at from selection_events order by occurred_at desc;
```

## Demo Users

All passwords are development-only and stored for MVP demonstration.

| Login | Password | Roles | Main flow |
| --- | --- | --- | --- |
| `member` | `member` | MEMBER | create referral invitation |
| `admin` | `admin` | MEMBER, ADMIN | manage regulation, open/close voting |
| `privileged` | `privileged` | MEMBER, PRIVILEGED_MEMBER | vote, create complaint, create invitation, review assigned stages |
| `privileged2` | `privileged2` | MEMBER, PRIVILEGED_MEMBER | vote, create complaint, create invitation, review assigned stages |

The demo starts without invitations, candidates, voting sessions or candidate accounts.
Create an invitation as `member`, open the generated activation link, and activate it as a candidate.
Public invitation activation is available without login only on `/activate.html`.
Activation creates a candidate account and returns a generated login/password in the UI.

Every accepted community participant has the `MEMBER` role. Voting is a separate earned privilege:
when an invited candidate passes every selection stage, the inviter receives `PRIVILEGED_MEMBER`.
Interviewers are assigned per candidate stage from active privileged members. There is no separate
interviewer login; use the privileged user shown in the candidate stage details.

## MVP Scope

Implemented Use Cases:

- UC-01: member creates referral invitation; candidate activates invitation publicly.
- UC-02: admin creates active selection regulation.
- UC-03: privileged member casts a vote; admin opens and closes voting.
- UC-04: assigned interviewer/admin blocks a candidate; admin unblocks.
- UC-05: candidate submits current stage result; assigned interviewer/admin records verdict.

Other screens are intentionally minimal and use interface placeholders where the future release scope is not implemented yet.

## Test

```bash
./mvnw test
```

Tests cover domain rules, Basic Auth API access, clean MVP state and public invitation activation.

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
