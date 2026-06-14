# FCRefSystem

Минимальная версия внутренней системы отбора кандидатов в закрытое сообщество.

## MVP scope

MVP реализует сквозной каркас для ключевых UC из SRS и figma-прототипа:

- UC-01: создание реферального приглашения;
- UC-02: создание регламента отбора;
- UC-03: голосование за кандидата;
- UC-04: блокировка кандидата;
- UC-05: прохождение этапа отбора.

Первая реализация использует in-memory хранилище, чтобы зафиксировать бизнес-правила и интерфейс без зависимости от локальной PostgreSQL. Целевая БД по SRS - PostgreSQL 16.

## Stack

- Java 17;
- Spring Boot;
- Maven;
- REST API over JSON;
- статический HTML/CSS/JS интерфейс, отдаваемый приложением.

## Run

```bash
mvn spring-boot:run
```

После запуска:

- UI: `http://localhost:8080/`
- health: `http://localhost:8080/actuator/health`
- API snapshot: `http://localhost:8080/api/snapshot`
- OpenAPI: `http://localhost:8080/openapi.yaml`

Порт можно изменить переменной окружения:

```bash
PORT=8090 mvn spring-boot:run
```

## Test

```bash
mvn test
```

## Demo users

- `member-1` - участник, создает приглашения;
- `admin-1` - администратор, управляет регламентом и голосованием;
- `privileged-1` - участник с привилегиями, голосует и подает жалобы;
- `interviewer-1` - интервьюер, блокирует кандидата и фиксирует вердикт;
- `candidate-user-1` - кандидат, отправляет результат этапа.
