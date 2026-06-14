# TASKS

Документ фиксирует backlog работ, необходимых для доведения FCRefSystem от текущего MVP до финальной защиты и будущего релиза. Основания: локальные PDF SRS, SDP, Business Case, Risk List, Glossary, figma-прототип и текущее состояние репозитория.

## Current State

- MVP уже содержит backend на Java/Spring Boot, frontend на HTML/CSS/JS, тесты, линтеры и GitHub Actions CI.
- Реализована in-memory версия ключевых UC-01..UC-05: приглашения, регламент, голосование, блокировка, прохождение этапа.
- Структура разделена на `backend/`, `frontend/`, `tests/backend/`, `figma/`, `config/`.
- Исходные PDF-документы не версионируются: они должны оставаться локально в рабочем каталоге и игнорироваться Git.
- Главные пробелы перед реальным релизом: нет PostgreSQL, миграций, настоящей аутентификации, сессий, серверного RBAC на уровне полноценной модели, production deployment, полноценной отчетности, E2E тестов, документации архитектуры и приемочного пакета.

## Definition of Done

- Требование имеет трассировку к SRS/UC/NFR или явно помечено как инфраструктурная задача.
- Реализация покрыта тестами на уровне риска: unit, integration, API, E2E или manual acceptance checklist.
- Изменение проходит backend lint, frontend lint, tests и CI.
- Пользовательский сценарий проверен в UI и через API.
- Документация обновлена: README, OpenAPI, архитектурные заметки, эксплуатационные инструкции или TASKS.
- Для критичных операций есть аудит и корректная обработка ошибок.

## 1. Requirements, Scope And Traceability

1.1. Build SRS Traceability Matrix - связать FR-001..FR-018, NFR-001..NFR-018, IR, DC, LR с кодом, тестами и UI-экранами.

1.2. Build UC Traceability Matrix - связать UC-01..UC-05 из figma/SRS с API endpoints, service methods, UI forms and tests.

1.3. Restore Missing Vision/Use Case Knowledge - найти локальные Vision и Use Case источники, извлечь из них требования в версионируемые markdown summaries, исходные PDF не добавлять в Git.

1.4. Clarify MVP Acceptance Boundary - зафиксировать, какие части обязательны для защиты, а какие являются post-defense roadmap.

1.5. Define Production Release Boundary - описать минимальный релиз после защиты: persistence, auth, deployment, monitoring, backup.

1.6. Normalize Domain Terminology - проверить единообразие ролей, статусов, вердиктов, этапов и действий между Glossary, UI, API, tests and docs.

1.7. Formalize Candidate State Machine - описать допустимые статусы и переходы кандидата в отдельном документе.

1.8. Formalize Invitation State Machine - описать ACTIVE, ACTIVATED, CANCELLED, EXPIRED и правила переходов.

1.9. Formalize Voting Rules - описать пороги, сроки, ручное/автоматическое закрытие и пересчет результата.

1.10. Formalize Retry Rules - описать лимиты попыток, повторные назначения этапа и запреты на обход лимитов.

1.11. Formalize Blocking Rules - описать основания, роли, снятие блокировки и последствия для процесса отбора.

1.12. Define Report Requirements - описать, какие отчеты нужны на защите и в будущей эксплуатации.

1.13. Define Audit Acceptance Criteria - определить обязательный набор событий журнала для FR-018.

1.14. Define Data Privacy Criteria - определить чувствительные данные и роли, которым они доступны.

1.15. Define Non-Functional Acceptance Criteria - перевести NFR в проверяемые acceptance checks.

1.16. Add Requirements Change Log - вести изменения требований в одном месте, чтобы снизить риск scope creep.

1.17. Maintain Local Source Document Policy - держать исходные PDF вне истории Git, обновлять только derived summaries, matrices and markdown docs.

## 2. Project Structure And Build System

2.1. Validate Repository Layout - зафиксировать стандарт структуры `backend/`, `frontend/`, `tests/`, `config/`, `.github/`.

2.2. Add Architecture Decision Records - создать `docs/adr/` для ключевых решений: Spring Boot, PostgreSQL, static frontend, auth approach.

2.3. Add Contributor Guide - описать workflow разработки, ветки, коммиты, CI checks and review rules.

2.4. Add Root Makefile Or Task Runner - добавить короткие команды `lint`, `test`, `build`, `run`.

2.5. Add Maven Wrapper - добавить `mvnw`/`mvnw.cmd`, чтобы CI и разработчики не зависели от локального Maven.

2.6. Pin Java Version - добавить `.java-version`, Maven Enforcer или toolchains для Java 17.

2.7. Pin Node Version - добавить `.nvmrc` or Volta config for frontend tooling.

2.8. Separate Test Reports - настроить отчеты Maven Surefire/Failsafe and frontend lint artifacts.

2.9. Add Dependency Update Policy - определить порядок обновления Maven/npm dependencies.

2.10. Add License Inventory - составить список сторонних компонентов и лицензий для LR-002/LR-003.

2.11. Add Conventional Branch Rules - описать формат веток и коммитов `MPE#BranchName`.

2.12. Add PR Template - чеклист требований, тестов, документации и безопасности.

## 3. Backend Architecture

3.1. Split Backend Packages By Layer - отделить api, application service, domain, repository, config, security.

3.2. Introduce DTO Boundary - не отдавать domain objects напрямую из REST API.

3.3. Add Mapper Layer - добавить явное преобразование domain <-> DTO.

3.4. Add Command Objects - оформить действия UC как commands: CreateInvitationCommand, CastVoteCommand, etc.

3.5. Add Query Services - отделить read-model карточек, списков, журнала и отчетов.

3.6. Add Domain Validation Module - вынести бизнес-инварианты из одного большого SelectionService.

3.7. Split SelectionService - разделить invitation, candidate, regulation, voting, blocking, role and audit services.

3.8. Add Transaction Boundary - определить, какие операции должны выполняться атомарно.

3.9. Add Error Code Catalog - документировать коды ошибок API и бизнес-правил.

3.10. Add Global API Response Policy - унифицировать формат ошибок, validation errors and conflict responses.

3.11. Add Time Provider Abstraction - оставить Clock injection во всех time-dependent сервисах.

3.12. Add Idempotency Service - вынести idempotency ключи из SelectionService в отдельный компонент.

3.13. Add Domain Event Model - отделить event records from audit persistence.

3.14. Add Service-Level Authorization Guards - централизовать проверку ролей на уровне backend.

3.15. Add Backend Module Tests For Layers - покрыть package boundaries архитектурными тестами.

## 4. Database And Persistence

4.1. Add PostgreSQL 16 Dependency - подключить JDBC/JPA stack, совместимый с MIT license policy.

4.2. Add Flyway Or Liquibase - внедрить миграции схемы БД.

4.3. Model Users Table - хранить пользователей, display names, credentials/session identities.

4.4. Model Roles Tables - хранить роли, назначения ролей, историю изменений ролей.

4.5. Model Invitations Table - хранить token hash, author, status, createdAt, expiresAt, activatedAt.

4.6. Model Candidates Table - хранить карточку кандидата, приглашение, статус, текущий этап, даты.

4.7. Model Regulations Table - хранить версии регламента, active flag, author, createdAt.

4.8. Model Regulation Stages Table - хранить этапы, порядок, тип, лимиты, сроки, пороги, критерии.

4.9. Model Stage Progress Table - хранить назначенные этапы, попытки, состояние, результаты.

4.10. Model Verdicts Table - хранить вердикт, отчет, автора и дату.

4.11. Model Voting Tables - хранить голосования, сроки, статусы, голоса, расчет результата.

4.12. Model Complaints Table - хранить жалобы с причиной и автором.

4.13. Model Blocks Table - хранить блокировки, снятие блокировки and reasons.

4.14. Model Audit Events Table - хранить immutable event log для FR-018/NFR-006.

4.15. Add Unique Constraints - token uniqueness, one vote per user per voting, active role uniqueness.

4.16. Add Referential Integrity - внешние ключи между candidate, invitation, stages, votes, blocks.

4.17. Add Indexes For Read Paths - карточка кандидата, список кандидатов, список приглашений, журнал.

4.18. Add Repository Layer - заменить in-memory maps на repositories.

4.19. Add Testcontainers PostgreSQL - интеграционные тесты с настоящей PostgreSQL 16.

4.20. Add Seed Data For Demo - подготовить controlled demo fixtures for defense.

4.21. Add Backup And Restore Scripts - минимальный сценарий backup/restore for production.

## 5. Authentication, Sessions And Authorization

5.1. Choose Auth Approach - определить форму входа: local accounts, LDAP/OAuth, or temporary internal auth.

5.2. Add Login Page - создать UI для входа пользователя.

5.3. Add Password Hashing - если local accounts, использовать BCrypt/Argon2.

5.4. Add Session Management - реализовать session expiry: 30 days default, 7 days inactive.

5.5. Add Logout - корректно завершать session and invalidate protected operations.

5.6. Add CSRF Protection - включить защиту для state-changing requests.

5.7. Add CORS Policy - ограничить источники для production.

5.8. Add Server-Side RBAC - проверять права на каждом API action and read path.

5.9. Add Candidate Data Isolation - кандидат видит только собственный процесс and allowed status.

5.10. Add Privileged Data Isolation - ограничить отчеты, причины блокировки, жалобы, голоса and audit.

5.11. Add Admin Role Management UI - назначение и отзыв ролей через интерфейс.

5.12. Add Role Change Audit - каждое изменение ролей сохранять в журнал.

5.13. Add Access Denied UX - понятные ошибки доступа в UI.

5.14. Add Security Tests - unit/API tests для ролей and forbidden paths.

5.15. Add Session Expiry Tests - проверить expired/inactive sessions.

5.16. Add Security Headers - CSP, X-Frame-Options, Referrer-Policy, HSTS for HTTPS.

5.17. Add Token Protection - хранить invitation tokens безопасно, не логировать секреты.

5.18. Add Threat Model - описать угрозы и меры по NFR-008..NFR-011.

## 6. Core Functional UC Completion

6.1. UC-01 Complete Invitation Quota Rules - квоты должны храниться в БД and apply per member.

6.2. UC-01 Add Invitation Cancellation - участник/admin отменяет приглашение с причиной.

6.3. UC-01 Add Invitation Expiration Job - истекшие приглашения переводятся в EXPIRED автоматически.

6.4. UC-01 Add Invitation List Filters - фильтры по статусу, автору, дате, кандидату.

6.5. UC-01 Add Invitation Copy UX - безопасное копирование ссылки без раскрытия лишних данных.

6.6. UC-01 Add Duplicate Request Handling - idempotency keys persist in DB.

6.7. UC-02 Add Regulation Draft Mode - создать черновик без немедленной активации.

6.8. UC-02 Add Regulation Activation Confirmation - критическое действие с явным подтверждением.

6.9. UC-02 Add Regulation Completeness Rules - проверить этапы, завершения, переходы, thresholds, limits.

6.10. UC-02 Add Regulation Versioning - старые кандидаты должны сохранять связь с версией регламента.

6.11. UC-02 Add Stage Reorder UI - изменять порядок этапов.

6.12. UC-02 Add Transition Rules UI - задавать Passed/Failed/Retry/Next transitions.

6.13. UC-02 Add Criteria Editor - критерии оценки по этапам.

6.14. UC-02 Add Regulation Audit Diff - журналировать, что именно изменилось в регламенте.

6.15. UC-03 Add Voting Eligibility Query - показать, кто может голосовать and кто уже голосовал.

6.16. UC-03 Add Vote Deadline Handling - автоматическое закрытие по сроку.

6.17. UC-03 Add Vote Result Calculation - поддержать thresholds from regulation.

6.18. UC-03 Add Vote Revocation Policy - решить, можно ли менять голос до закрытия.

6.19. UC-03 Add Vote Visibility Policy - скрывать или показывать голоса согласно требованиям доступа.

6.20. UC-03 Add Complaint Workflow - жалобы должны иметь status, handling result and audit trail.

6.21. UC-04 Add Block Confirmation Modal - критическое действие с подтверждением.

6.22. UC-04 Add Block Categories Catalog - справочник оснований блокировки.

6.23. UC-04 Add Block Report For Admin - отчет по активным/снятым блокировкам.

6.24. UC-04 Add Unblock Workflow - admin снимает блокировку and candidate resumes correctly.

6.25. UC-04 Add Blocked Candidate Read Model - UI clearly shows process stopped.

6.26. UC-05 Add Stage Assignment Engine - назначать этапы строго по active regulation version.

6.27. UC-05 Add Stage Submission Attachments - поддержать файлы/ссылки для задания.

6.28. UC-05 Add Attempt History - показывать все попытки кандидата.

6.29. UC-05 Add Retry Assignment - при Retry создавать новую попытку в пределах лимита.

6.30. UC-05 Add Verdict Permissions - разные роли для interview/task/vote stages.

6.31. UC-05 Add Candidate Stage Deadlines - сроки выполнения и overdue states.

6.32. UC-05 Add Candidate View - кандидат видит доступное действие без внутренних данных.

6.33. FR-004 Add Full Candidate Card - статус, этап, приглашение, история, попытки, вердикты, отчеты, голосования, жалобы, блокировки.

6.34. FR-017 Complete Role Management - assign/revoke roles with UI/API/test coverage.

6.35. FR-018 Complete Audit Coverage - проверить все перечисленные действия and missing audit events.

## 7. Frontend Productization

7.1. Replace Demo Text Mojibake - проверить, что все русские строки в frontend stored and rendered as UTF-8.

7.2. Add Real Navigation Routes - routing by URL for invitations/regulation/voting/blocking/stage/journal.

7.3. Add API Client Module - вынести fetch/error handling из `app.js`.

7.4. Split UI Components - разнести views, forms, tables, badges, dialogs.

7.5. Add Design Tokens - централизовать colors, spacing, typography.

7.6. Add Form Validation UX - inline errors, disabled submit, required fields.

7.7. Add Loading States - state for initial load and actions.

7.8. Add Empty States - lists without data should render predictable views.

7.9. Add Error States - API conflicts and forbidden errors should be actionable.

7.10. Add Confirmation Dialogs - block, unblock, activate regulation, close voting, role changes.

7.11. Add Candidate Card Screen - full FR-004 view.

7.12. Add Invitations Screen - list, create, cancel, filters.

7.13. Add Regulation Builder Screen - draft, stages, validation, activation.

7.14. Add Voting Screen - materials, choice, reason, vote status, close action for admin.

7.15. Add Blocking Screen - candidate status, reason, consequences, unblock.

7.16. Add Stage Passing Screen - candidate-facing stage action and result submission.

7.17. Add Admin Role Screen - user list and role management.

7.18. Add Journal Screen - filters by event type, candidate, actor, date.

7.19. Add Report Screen - metrics and candidate process report.

7.20. Add Responsive QA - verify mobile, tablet and desktop layouts.

7.21. Add Accessibility Pass - keyboard navigation, labels, contrast, focus states.

7.22. Add Frontend Unit Tests - if frontend remains complex, introduce Vitest or similar.

7.23. Add Playwright E2E - cover UC-01..UC-05 in browser.

7.24. Add Frontend Build Step - if frontend grows, introduce bundler with production assets.

## 8. API And OpenAPI

8.1. Move OpenAPI Source - keep OpenAPI under `docs/api/` or generate from backend annotations.

8.2. Complete OpenAPI Schemas - define all request/response schemas.

8.3. Document Error Responses - add standard error schema and status codes.

8.4. Add API Versioning Policy - decide `/api/v1` before production.

8.5. Add Pagination - candidate, invitation, journal, reports lists.

8.6. Add Filtering And Sorting - list endpoints need predictable query params.

8.7. Add OpenAPI Validation In CI - fail CI if OpenAPI invalid.

8.8. Add Postman/HTTP Collection - defense demo and regression checks.

8.9. Add Contract Tests - verify backend responses match OpenAPI.

8.10. Add API Examples - examples for all UC requests.

8.11. Add Idempotency Header - standardize request id handling for critical operations.

8.12. Add API Deprecation Policy - future release compatibility.

## 9. Testing And QA

9.1. Expand Unit Tests For Domain Rules - cover all business invariants.

9.2. Add Integration Tests With PostgreSQL - repositories and transactions.

9.3. Add API Tests For UC-01 - invitation create, quota, activation, duplicate activation, expiration.

9.4. Add API Tests For UC-02 - regulation draft, invalid regulation, activation, versioning.

9.5. Add API Tests For UC-03 - open vote, duplicate vote, close vote, threshold result.

9.6. Add API Tests For UC-04 - block, duplicate block, unblock, blocked process stop.

9.7. Add API Tests For UC-05 - submit result, retry, limit reached, verdict effects.

9.8. Add Role-Based API Tests - each role should have allowed and forbidden paths.

9.9. Add Audit Transaction Tests - operation and audit record must commit together.

9.10. Add Idempotency Tests - no duplicate invitations, attempts, votes, verdicts, blocks.

9.11. Add Database Migration Tests - migrations apply cleanly to empty DB.

9.12. Add Regression Test Suite - protection against core scenario regressions.

9.13. Add E2E Smoke Tests - browser tests for the main happy path.

9.14. Add E2E Negative Tests - invalid role, missing reason, blocked candidate.

9.15. Add Accessibility Tests - automated axe checks for main screens.

9.16. Add Performance Smoke Tests - basic latency checks for read/write operations.

9.17. Add Security Tests - CSRF, session expiration, access control.

9.18. Add Manual Test Plan - checklist for defense and release candidate validation.

9.19. Add Test Data Builders - reduce fixture duplication.

9.20. Add Coverage Reporting - JaCoCo for backend and coverage thresholds.

9.21. Add Mutation Testing Candidate - optional PIT for domain rules.

9.22. Add Test Reports To CI Artifacts - upload reports for failed CI runs.

9.23. Add Release Regression Checklist - exact manual steps before tagging release.

9.24. Add Bug Bash Checklist - edge cases from Risk List.

## 10. CI/CD And Developer Experience

10.1. Add Maven Wrapper To CI - use repo-local Maven wrapper.

10.2. Add Frontend Dependency Cache - already configured, verify on GitHub Actions.

10.3. Add CI Status Badge - show workflow status in README.

10.4. Add Branch Protection Rules - require CI pass before merge.

10.5. Add Pull Request Checks - lint, tests, package, OpenAPI validation.

10.6. Add Build Artifact Upload - upload backend jar for CI build.

10.7. Add Dependency Audit - `npm audit` policy and Maven dependency check policy.

10.8. Add Secret Scanning Policy - avoid credentials in repository.

10.9. Add Release Workflow - tag builds and attach jar.

10.10. Add Deploy Workflow For Helios - after credentials and server path are available.

10.11. Add Environment Matrix - local, test, staging, production configs.

10.12. Add CI Failure Playbook - common failures and fixes.

10.13. Add Automated Changelog - generate from commits or PR labels.

10.14. Add Repository Labels - requirements, backend, frontend, tests, docs, security, release.

## 11. Deployment And Infrastructure

11.1. Define Target Deployment Topology - FreeBSD/Helios, Java 17, PostgreSQL 16, Nginx.

11.2. Add Production Configuration Template - environment variables and sample config.

11.3. Add PostgreSQL Setup Guide - database, user, schema, permissions.

11.4. Add Nginx Reverse Proxy Config - HTTPS, static compression, proxy headers.

11.5. Add Systemd/Rc Script - process management for FreeBSD/Linux target.

11.6. Add Deployment Script - repeatable build/copy/restart steps.

11.7. Add Rollback Script - restore previous jar and schema backup path.

11.8. Add Health Check Endpoint Policy - readiness/liveness checks.

11.9. Add HTTPS Certificate Plan - certbot/manual certificate depending on server.

11.10. Add Log Directory And Rotation - avoid unbounded production logs.

11.11. Add Backup Schedule - PostgreSQL dumps and retention.

11.12. Add Restore Drill - test restore from backup.

11.13. Add Staging Environment - separate DB and deployment for acceptance.

11.14. Add Production Secrets Handling - no secrets in repo or CI logs.

11.15. Add Deployment Verification Checklist - post-deploy smoke checks.

11.16. Add Disaster Recovery Notes - what to do on DB/app/server failure.

## 12. Observability, Audit And Reporting

12.1. Add Structured Logging - JSON or consistent text logs with correlation id.

12.2. Add Request Correlation Id - trace user action through logs and audit.

12.3. Add Metrics Endpoint Review - expose safe actuator metrics.

12.4. Add Audit Event Persistence - immutable storage in DB.

12.5. Add Audit Filters UI - event type, actor, candidate, date.

12.6. Add Audit Export - CSV/JSON export for defense/reporting.

12.7. Add Candidate Report - full candidate history and current decision state.

12.8. Add Process Metrics Report - invitations, pass/fail, blocks, average stage time.

12.9. Add Voting Report - votes count, result, threshold, deadline.

12.10. Add Regulation Change Report - versions and changes over time.

12.11. Add Security Event Logging - failed login, forbidden action, session expiration.

12.12. Add Admin Dashboard - operational state and key counters.

12.13. Add Log Redaction - do not leak tokens, reports or sensitive reasons.

12.14. Add Monitoring Checklist - health, DB connectivity, disk, memory, latency.

## 13. Performance And Reliability

13.1. Define Performance Baseline - 100 users, 500 candidates, 20 active users per SRS.

13.2. Add Query Performance Tests - candidate card, lists, journal under expected data volume.

13.3. Add Database Index Review - verify query plans for common reads.

13.4. Add Transaction Atomicity Tests - critical operations all-or-nothing.

13.5. Add Optimistic Locking - prevent lost updates on candidate/voting/regulation.

13.6. Add Duplicate Submit Protection - backend idempotency for double-click/network retry.

13.7. Add Scheduled Jobs Safety - expiration and voting close jobs should be idempotent.

13.8. Add Graceful Shutdown - complete in-flight requests or fail predictably.

13.9. Add Startup Validation - app should fail fast if DB/config invalid.

13.10. Add Data Consistency Checks - maintenance checks for impossible states.

13.11. Add Load Smoke Script - simple k6/JMeter scenario for defense evidence.

13.12. Add Reliability Test Plan - DB outage, app restart, repeated request tests.

## 14. Security And Compliance

14.1. Classify Sensitive Data - reports, complaints, votes, block reasons, audit records.

14.2. Add Privacy Notice For Users - what is stored and who can see it.

14.3. Add Data Retention Policy - how long candidates, audit and reports are stored.

14.4. Add Admin Data Export Policy - who can export reports and audit.

14.5. Add Input Sanitization Review - user-entered comments, reports, reasons.

14.6. Add XSS Protection Tests - frontend rendering of untrusted text.

14.7. Add SQL Injection Protection Review - repository parameter binding.

14.8. Add Dependency Vulnerability Scanning - Maven and npm.

14.9. Add Least Privilege DB User - application DB user without superuser privileges.

14.10. Add Production Secrets Checklist - DB password, session secret, deploy keys.

14.11. Add Security Review Before Defense - checklist of NFR-008..NFR-011.

14.12. Add Security Review Before Release - deeper threat model and manual test.

## 15. Documentation For Defense

15.1. Add Architecture Document - components, modules, data flow, deployment diagram.

15.2. Add Database Schema Document - ERD and table descriptions.

15.3. Add API Documentation - OpenAPI plus endpoint examples.

15.4. Add User Manual - role-based walkthrough for UC-01..UC-05.

15.5. Add Admin Manual - regulation, roles, blocks, audit, reports.

15.6. Add Developer Setup Guide - Java, Maven, Node, PostgreSQL, run, test, lint.

15.7. Add Testing Report - what is covered, how to run, latest results.

15.8. Add CI/CD Report - workflow explanation and screenshots/results.

15.9. Add Deployment Guide - Helios/FreeBSD or staging server instructions.

15.10. Add Risk Mitigation Report - map Risk List to implemented safeguards.

15.11. Add Requirements Coverage Report - FR/NFR status table.

15.12. Add Demo Scenario Script - exact click path for defense.

15.13. Add Demo Data Guide - users, candidates, tokens, roles and expected outcomes.

15.14. Add Known Limitations - honest list of remaining constraints and future work.

15.15. Add Presentation Slides - problem, requirements, architecture, demo, tests, risks, roadmap.

15.16. Add Defense Checklist - final day steps and fallback options.

## 16. Release Management

16.1. Define Versioning Scheme - semantic versions for future releases.

16.2. Add Release Candidate Checklist - build, tests, migrations, docs, backup, deploy.

16.3. Add Changelog - maintain user-visible changes.

16.4. Add Release Notes Template - scope, risks, migration notes, rollback.

16.5. Add Production Readiness Review - security, performance, observability, backup.

16.6. Add Tagging Workflow - CI builds tagged releases.

16.7. Add Artifact Signing Or Checksums - integrity for release jar.

16.8. Add Rollback Acceptance Test - verify previous version can be restored.

16.9. Add Post-Release Monitoring Plan - observe logs, health, error rates.

16.10. Add Support Process - triage bugs and operational incidents.

## 17. Product And Operations

17.1. Define Owner Responsibilities - administrator, process owner, interviewer, moderator.

17.2. Define Operational Policies - who changes regulation, blocks candidates, closes voting.

17.3. Define Support Channels - where users report issues.

17.4. Define Audit Review Cadence - how often admins review events.

17.5. Define Role Review Cadence - periodic review of privileged roles.

17.6. Define Metrics For Product Value - reduced manual tracking, process transparency, decision reproducibility.

17.7. Define Feedback Loop - collect feedback from admins/interviewers/candidates after demo.

17.8. Define Future Feature Backlog - tasks catalog, interviews, moderation workflows, analytics, notifications.

17.9. Define Notification Requirements - email/Telegram/internal notifications for invitations, stages, votes.

17.10. Define Data Migration Strategy - how to import existing spreadsheet/chat process data if needed.

## 18. Immediate Next Milestones

18.1. Milestone A: Defense-Ready Documentation - architecture, requirements coverage, demo script, testing report.

18.2. Milestone B: Persistent Backend - PostgreSQL, migrations, repositories, integration tests.

18.3. Milestone C: Auth And RBAC - login, sessions, server-side role checks, security tests.

18.4. Milestone D: Complete UC Coverage - fill missing FR-004, FR-016, FR-017 and productionize UC-01..UC-05.

18.5. Milestone E: Quality Gate - expanded tests, Playwright smoke, coverage, CI artifacts.

18.6. Milestone F: Deployment - Helios/staging deployment, Nginx, PostgreSQL, backup, smoke checks.

18.7. Milestone G: Final Defense Run - scripted demo, fallback video/screenshots, final docs and release tag.
