# FCRefSystem

Минимальная версия внутренней системы отбора кандидатов в закрытое сообщество.

## Stack

- Java 17;
- Spring Boot;
- Maven;
- REST API over JSON;
- статический HTML/CSS/JS интерфейс, отдаваемый приложением.

## Structure

- `backend/` - Java/Spring Boot application and backend Maven module.
- `frontend/` - static HTML/CSS/JS interface and UI assets.
- `tests/backend/` - backend unit and API tests.
- `figma/` - Figma prototype plugin sources.
- source PDF documents are local-only and ignored by Git.

## Run

```bash
mvn package
java -jar backend/target/fc-ref-system-0.1.0-SNAPSHOT.jar
```

После запуска:

- UI: `http://localhost:8080/`
- health: `http://localhost:8080/actuator/health`
- API snapshot: `http://localhost:8080/api/snapshot`
- OpenAPI: `http://localhost:8080/openapi.yaml`

Порт можно изменить аргументом запуска:

```bash
java -jar backend/target/fc-ref-system-0.1.0-SNAPSHOT.jar --server.port=8090
```

## Test

```bash
mvn test
```

Тесты проверяют доменные правила UC и базовый HTTP-контур `/api`.

## Lint

```bash
mvn -pl backend checkstyle:check
cd frontend
npm ci
npm run lint
```

Backend lint uses Checkstyle and checks Java sources plus backend tests. Frontend lint uses ESLint, Stylelint and HTMLHint.

## CI

GitHub Actions workflow `.github/workflows/ci.yml` runs backend Checkstyle, backend tests, backend package build and frontend lint.

## Demo users

- `member-1` - участник, создает приглашения;
- `admin-1` - администратор, управляет регламентом и голосованием;
- `privileged-1` - участник с привилегиями, голосует и подает жалобы;
- `interviewer-1` - интервьюер, блокирует кандидата и фиксирует вердикт;
- `candidate-user-1` - кандидат, отправляет результат этапа.
