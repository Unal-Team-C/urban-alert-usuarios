# Technical Specification — UrbanAlert Users Service

**Version:** 1.0.0
**Date:** 2026-10-03
**Author:** UrbanAlert Team C
**Status:** Approved

---

## Table of Contents

1. [Service Overview](#1-service-overview)
2. [Technology Stack](#2-technology-stack)
3. [Quality Attribute Scenarios](#3-quality-attribute-scenarios)
4. [Architecture](#4-architecture)
5. [Domain Model](#5-domain-model)
6. [Database Schema](#6-database-schema)
7. [Project Structure](#7-project-structure)
8. [Dependencies](#8-dependencies)
9. [Configuration](#9-configuration)
10. [Integration Points](#10-integration-points)
11. [Testing Strategy](#11-testing-strategy)
12. [Deployment](#12-deployment)
13. [Architectural Decision Records](#13-architectural-decision-records)

---

## 1. Service Overview

The Users Service is a stateless HTTP microservice deployed as a Serverless Container (scale 1–20 replicas). It is the **single source of truth** for user identity data in the UrbanAlert platform.

Other microservices reference users by `userId` (UUID) and may call this service to validate that a user exists or to retrieve their role. The service exposes a REST API over HTTPS, protected at the perimeter by the API Gateway and WAF (QAS-01.1 / QAS-01.2), which handle authentication and rate limiting before requests reach this service.

The service does **not** emit domain events to the Event Bus in its current scope. If future requirements demand observability of user lifecycle events (e.g., role changes triggering notifications), a `UserRoleChanged` event can be added following the same MassTransit pattern used by the Reports Service.

---

## 2. Technology Stack

| Concern | Technology | Version | Rationale |
|---|---|---|---|
| Language | Java | 21 (LTS) | Team's primary language; LTS support until 2029 |
| Framework | Spring Boot | 3.3.x | Industry standard for Spring ecosystem; production-tested |
| Persistence | Spring Data JPA + Hibernate | 6.x (via Boot) | ORM consistent with Clean Architecture repository pattern |
| Database | PostgreSQL | 16 | Shared ACID core with Reports and Works (ADR-05) |
| Migrations | Flyway | 10.x | Explicit, versioned schema migrations; equivalent to EF Core migrations |
| Build tool | Gradle (Kotlin DSL) | 8.x | Standard Java build tool; reproducible builds; wrapper committed for CI parity |
| Validation | Jakarta Bean Validation | 3.x (via Boot) | Declarative validation at the API boundary |
| Test framework | JUnit 5 + Mockito | 5.x / 5.x | Unit tests for domain and application layers |
| Integration tests | Testcontainers (PostgreSQL) | 1.20.x | Real database in CI; no mocking of persistence |
| UUID v7 | java-uuid-generator | 5.x | Consistent with `Guid.CreateVersion7()` used in Reports (C#) |
| Boilerplate reduction | Lombok | 1.18.x | Reduces getter/setter/constructor boilerplate in JPA entities and DTOs |
| API documentation | SpringDoc OpenAPI | 2.x | Equivalent to `Scalar.AspNetCore`; provides `/swagger-ui.html` |
| Containerization | Docker | — | Multi-stage build; base image `eclipse-temurin:21-jre-alpine` |

---

## 3. Quality Attribute Scenarios

These scenarios are scoped to the Users Service and complement the system-level QAS defined in the architecture document.

---

### QAS-US-01 — Input Validation Against Injection (Security)

**Source:** Authenticated client or actor with stolen credentials sending malicious payloads.
**Stimulus:** An injection attempt in the `email` or `name` fields (SQL injection, XSS payload, oversized string).
**Artifact:** `UserController`, `UserService`, and the JPA persistence layer.
**Environment:** Normal production operation, after the API Gateway perimeter has been passed.
**Response:** Every request field is validated against a strict schema (type, length, format whitelist) using Jakarta Bean Validation before entering the application layer. The JPA layer uses parameterized queries exclusively (Hibernate prepared statements); no string concatenation toward SQL is permitted.
**Measure:** 100% of requests with non-conforming payloads rejected with HTTP 400 before touching the persistence layer. 0 SQL queries constructed via string concatenation (enforced by not using `@Query` with string interpolation; all queries use Spring Data method derivation or JPQL with named parameters).
**Tactics applied:** Validate Input, Parameterize Queries, Limit Exposure (DB user has no DDL privileges).

---

### QAS-US-02 — Email Uniqueness Under Concurrent Writes (Data Integrity)

**Source:** Two concurrent HTTP clients attempting to register the same email address.
**Stimulus:** Two simultaneous POST /api/v1/users requests with the same email, arriving within the same millisecond.
**Artifact:** `UserService.createUser()` and the PostgreSQL `users` table.
**Environment:** Production under normal or peak load (up to 20 concurrent replicas per ADR-03).
**Response:** A UNIQUE constraint on the `email` column acts as the last line of defense. A `DataIntegrityViolationException` thrown by Hibernate is caught by the `GlobalExceptionHandler` and translated to HTTP 409. The application-level check in `UserService` (`existsByEmail`) catches the conflict early under normal concurrency, reducing unnecessary constraint violations.
**Measure:** 0 duplicate email registrations persisted. 100% of duplicate attempts result in HTTP 409. No data corruption under concurrent writes.
**Tactics applied:** Detect Faults (DB constraint), Handle Exceptions (translate to 409), Limit Exposure (single write transaction per request).

---

### QAS-US-03 — Last Administrator Protection (Business Integrity)

**Source:** Administrator performing a DELETE or role demotion operation.
**Stimulus:** Attempt to delete the last remaining ADMIN user or demote them to USER.
**Artifact:** `UserService.deleteUser()` and `UserService.updateUserRole()`.
**Environment:** Any time during normal operation.
**Response:** Before executing the deletion or role update, the service counts the number of ADMIN users. If the result would leave the system with zero administrators, the operation is rejected with a `LastAdminException`, translated to HTTP 409.
**Measure:** 100% of operations that would leave 0 admins are rejected. The check and the write occur within the same transaction to avoid a TOCTOU race condition.
**Tactics applied:** Validate Business Invariant, Transactional Consistency (single `@Transactional` span).

---

### QAS-US-04 — Response Latency Under Normal Load (Performance)

**Source:** Internal services and admin portal querying user data.
**Stimulus:** Sustained read load of GET /api/v1/users and GET /api/v1/users/{id}.
**Artifact:** `UserController`, `UserService`, `UserRepositoryAdapter`, PostgreSQL.
**Environment:** Normal production operation.
**Response:** Queries use indexed columns (`id` as PK, `email` with UNIQUE index). List queries use `findAll()` with projection to `UserResponse` to avoid loading unnecessary fields. No N+1 queries.
**Measure:** p95 response time < 100 ms for GET /api/v1/users/{id} under normal load. p95 < 200 ms for GET /api/v1/users (full list).
**Tactics applied:** Index Usage, Projection Queries, Connection Pooling (HikariCP default via Spring Boot).

---

### QAS-US-05 — Startup Idempotency of Seed Data (Reliability)

**Source:** Platform operator restarting or redeploying the service.
**Stimulus:** Service restarts when the seed admin already exists in the database.
**Artifact:** Flyway migration `V2__seed_admin_user.sql`.
**Environment:** Any restart in any environment.
**Response:** Flyway's migration versioning ensures `V2` runs exactly once. The SQL uses `INSERT ... ON CONFLICT DO NOTHING` as an additional safety net.
**Measure:** 0 duplicate seed records after any number of restarts. 0 startup failures caused by seed data conflicts.
**Tactics applied:** Idempotent Migration, Conflict-Safe INSERT.

---

## 4. Architecture

The service follows **Clean Architecture** (also known as Hexagonal / Ports & Adapters), decomposed into four layers. Dependencies flow inward only — outer layers depend on inner layers, never the reverse.

```
┌─────────────────────────────────────────────────────┐
│  API Layer (Adapter — Driving)                      │
│  UserController, DTOs, GlobalExceptionHandler       │
├─────────────────────────────────────────────────────┤
│  Application Layer (Use Cases)                      │
│  Use case interfaces (input ports)                  │
│  UserService (implements all input ports)           │
├─────────────────────────────────────────────────────┤
│  Domain Layer (Core)                                │
│  User, Role, domain exceptions                      │
│  UserRepository interface (output port)             │
├─────────────────────────────────────────────────────┤
│  Infrastructure Layer (Adapter — Driven)            │
│  UserJpaEntity, UserJpaRepository (Spring Data)     │
│  UserRepositoryAdapter, UserPersistenceMapper       │
└─────────────────────────────────────────────────────┘
                          │
                     PostgreSQL 16
```

### Layer responsibilities

| Layer | Responsibility | May depend on |
|---|---|---|
| Domain | Business entities, invariants, domain exceptions, output port interfaces | Nothing (pure Java) |
| Application | Use case orchestration, business rule enforcement | Domain only |
| Infrastructure | JPA persistence, Spring Data, Flyway | Domain + Application (implements output ports) |
| API | HTTP request/response mapping, validation, error translation | Application (calls input ports) |

---

## 5. Domain Model

### `User` (entity)

```
User
  id:    UUID           — assigned by system (UUID v7), never null
  name:  String         — 1–200 chars, non-blank
  email: String         — 1–300 chars, valid email format, unique
  role:  Role           — enum, defaults to USER
```

**Constructor invariants (enforced in `User(String name, String email, Role role)`):**
- `name` must not be blank and must not exceed 200 characters.
- `email` must not be blank and must not exceed 300 characters.
- `email` must match a valid email pattern (`^[^@\s]+@[^@\s]+\.[^@\s]+$`).
- `role` must not be null; defaults to `Role.USER` when null is passed.
- `id` is generated by the constructor using `UUIDs.timeBased()` (UUID v7); clients cannot set it.

**Mutating methods:**
- `updateProfile(String name, String email, Role role)` — replaces all mutable fields; same invariants as constructor.
- `updateRole(Role role)` — replaces only the role; role must not be null.

### `Role` (enum)

```java
public enum Role {
    USER,
    ADMIN
}
```

### Domain Exceptions

| Class | Extends | When thrown |
|---|---|---|
| `DuplicateEmailException` | `RuntimeException` | Email already in use by another user |
| `UserNotFoundException` | `RuntimeException` | No user found for the given ID |
| `LastAdminException` | `RuntimeException` | Operation would leave system with 0 admins |

### Output Port

```java
public interface UserRepository {
    User save(User user);
    Optional<User> findById(UUID id);
    List<User> findAll();
    void delete(User user);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, UUID excludedId);
    long countByRole(Role role);
}
```

---

## 6. Database Schema

### Table: `users`

```sql
CREATE TABLE users (
    id      UUID        PRIMARY KEY,
    name    VARCHAR(200) NOT NULL,
    email   VARCHAR(300) NOT NULL,
    role    VARCHAR(20)  NOT NULL
);

CREATE UNIQUE INDEX uix_users_email ON users (email);
```

### Migration files

| File | Description |
|---|---|
| `V1__create_users_table.sql` | Creates the `users` table with PK and unique index on email |
| `V2__seed_admin_user.sql` | Inserts the default administrator; uses `ON CONFLICT DO NOTHING` |

### Seed data (V2)

```sql
INSERT INTO users (id, name, email, role)
VALUES (
    '018f4c2a-0000-7000-8000-000000000001',
    'Administrator',
    'admin@urbanalert.com',
    'ADMIN'
)
ON CONFLICT DO NOTHING;
```

---

## 7. Project Structure

```
backend/UrbanAlert.Usuarios/
├── specs/
│   ├── functional-spec.md          ← this document's companion
│   └── technical-spec.md           ← this document
│
├── src/
│   ├── main/
│   │   ├── java/com/urbanalert/users/
│   │   │   │
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   │   ├── User.java                       ← domain entity
│   │   │   │   │   └── Role.java                       ← enum: USER, ADMIN
│   │   │   │   ├── exception/
│   │   │   │   │   ├── DuplicateEmailException.java
│   │   │   │   │   ├── UserNotFoundException.java
│   │   │   │   │   └── LastAdminException.java
│   │   │   │   └── port/
│   │   │   │       └── out/
│   │   │   │           └── UserRepository.java         ← output port
│   │   │   │
│   │   │   ├── application/
│   │   │   │   ├── port/
│   │   │   │   │   └── in/
│   │   │   │   │       ├── CreateUserUseCase.java
│   │   │   │   │       ├── GetUsersUseCase.java
│   │   │   │   │       ├── GetUserByIdUseCase.java
│   │   │   │   │       ├── UpdateUserUseCase.java
│   │   │   │   │       ├── UpdateUserRoleUseCase.java
│   │   │   │   │       └── DeleteUserUseCase.java
│   │   │   │   └── service/
│   │   │   │       └── UserService.java                ← implements all use cases
│   │   │   │
│   │   │   ├── infrastructure/
│   │   │   │   └── persistence/
│   │   │   │       ├── entity/
│   │   │   │       │   └── UserJpaEntity.java          ← JPA entity (separate from domain)
│   │   │   │       ├── repository/
│   │   │   │       │   ├── UserJpaRepository.java      ← Spring Data JPA interface
│   │   │   │       │   └── UserRepositoryAdapter.java  ← implements domain UserRepository
│   │   │   │       └── mapper/
│   │   │   │           └── UserPersistenceMapper.java  ← UserJpaEntity ↔ User
│   │   │   │
│   │   │   └── api/
│   │   │       ├── controller/
│   │   │       │   └── UserController.java
│   │   │       ├── dto/
│   │   │       │   ├── CreateUserRequest.java
│   │   │       │   ├── UpdateUserRequest.java
│   │   │       │   ├── UpdateUserRoleRequest.java
│   │   │       │   ├── UserResponse.java
│   │   │       │   └── ErrorResponse.java
│   │   │       ├── mapper/
│   │   │       │   └── UserApiMapper.java              ← User ↔ UserResponse / Request → User
│   │   │       └── exception/
│   │   │           └── GlobalExceptionHandler.java     ← @ControllerAdvice
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   │           ├── V1__create_users_table.sql
│   │           └── V2__seed_admin_user.sql
│   │
│   └── test/
│       └── java/com/urbanalert/users/
│           ├── domain/
│           │   └── UserTest.java                       ← unit tests: constructor invariants + methods
│           ├── application/
│           │   └── UserServiceTest.java                ← unit tests: use cases with Mockito
│           └── api/
│               └── UserControllerIT.java               ← integration tests: Testcontainers + MockMvc
│
├── deploy/
│   └── docker/
│       ├── Dockerfile
│       └── docker-compose.yml
│
├── gradle/wrapper/
├── gradlew / gradlew.bat
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 8. Dependencies

### `build.gradle.kts` — key dependencies

```kotlin
// Web & REST
implementation("org.springframework.boot:spring-boot-starter-web")

// Persistence
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
runtimeOnly("org.postgresql:postgresql")

// Migrations
implementation("org.flywaydb:flyway-core")
implementation("org.flywaydb:flyway-database-postgresql")

// Validation
implementation("org.springframework.boot:spring-boot-starter-validation")

// Health check
implementation("org.springframework.boot:spring-boot-starter-actuator")

// API Documentation
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

// UUID v7
implementation("com.fasterxml.uuid:java-uuid-generator:5.1.0")

// Boilerplate reduction
compileOnly("org.projectlombok:lombok")
annotationProcessor("org.projectlombok:lombok")

// Local development (H2 in-memory database)
runtimeOnly("com.h2database:h2")

// Testing
testImplementation("org.springframework.boot:spring-boot-starter-test")     // JUnit 5 + Mockito
testImplementation("org.springframework.boot:spring-boot-testcontainers")
testImplementation("org.testcontainers:junit-jupiter")
testImplementation("org.testcontainers:postgresql")
testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.1"))
```

---

## 9. Configuration

### `application.yml`

```yaml
server:
  port: 8080

spring:
  application:
    name: urbanalert-users
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate          # Flyway owns schema; Hibernate only validates
    show-sql: false
    properties:
      hibernate:
        format_sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

### Environment variables

| Variable | Description | Example |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/urbanalert_users` |
| `DB_USERNAME` | Database user | `urbanalert` |
| `DB_PASSWORD` | Database password | `urbanalert` |

---

## 10. Integration Points

### Consumed by

| Consumer | How | Data used |
|---|---|---|
| Reports Service | REST GET /api/v1/users/{id} | Validates `userId` existence before creating a report |
| API Gateway | JWT claims + role lookup | Enforces RBAC using the role stored in this service |
| Admin Portal (frontend) | REST (all endpoints) | Manages the user list |

### Produces

The Users Service does **not** publish events to the Event Bus in the current scope. No MassTransit / RabbitMQ dependency is included.

### Health check

```
GET /actuator/health  →  { "status": "UP" }
```

Spring Boot Actuator is included. The health check verifies database connectivity via the default DataSource health indicator.

---

## 11. Testing Strategy

### Unit Tests — Domain Layer (`UserTest.java`)

Tests are pure Java, no Spring context, no mocking.

| Test case | Description |
|---|---|
| `createUser_withValidData_succeeds` | Happy path: constructor assigns UUID, default role USER |
| `createUser_withNullName_throwsArgumentException` | BR-004 |
| `createUser_withBlankName_throwsArgumentException` | BR-004 |
| `createUser_withNameExceedingMaxLength_throwsArgumentException` | BR-004 |
| `createUser_withNullEmail_throwsArgumentException` | BR-005 |
| `createUser_withInvalidEmailFormat_throwsArgumentException` | BR-005 |
| `createUser_withEmailExceedingMaxLength_throwsArgumentException` | BR-005 |
| `createUser_withNullRole_defaultsToUSER` | BR-002 |
| `updateProfile_withValidData_updatesFields` | All mutable fields replaced |
| `updateProfile_withInvalidEmail_throwsArgumentException` | Invariant re-enforced on update |
| `updateRole_withValidRole_updatesRole` | Role changes, other fields unchanged |
| `id_isAssignedByConstructor_andIsVersion7` | UUID v7 format verification |

### Unit Tests — Application Layer (`UserServiceTest.java`)

Spring context is NOT loaded. All dependencies are mocked with Mockito.

| Test case | Description |
|---|---|
| `createUser_withUniqueEmail_savesAndReturnsId` | Happy path |
| `createUser_withDuplicateEmail_throwsDuplicateEmailException` | QAS-US-02 |
| `createUser_withNullRole_defaultsToUSER` | BR-002 via service |
| `getUserById_withExistingId_returnsUser` | Happy path |
| `getUserById_withUnknownId_throwsUserNotFoundException` | US-003 Scenario 2 |
| `updateUser_withSameEmail_doesNotConflict` | BR-008 |
| `updateUser_withEmailOfAnotherUser_throwsDuplicateEmailException` | US-004 Scenario 2 |
| `updateUser_withUnknownId_throwsUserNotFoundException` | US-004 Scenario 4 |
| `updateUserRole_lastAdmin_throwsLastAdminException` | QAS-US-03 |
| `updateUserRole_oneOfMultipleAdmins_succeeds` | US-005 Scenario 2 |
| `deleteUser_lastAdmin_throwsLastAdminException` | QAS-US-03 / US-006 Scenario 3 |
| `deleteUser_regularUser_succeeds` | US-006 Scenario 1 |
| `deleteUser_unknownId_throwsUserNotFoundException` | US-006 Scenario 2 |
| `getUsers_returnsAllUsers` | US-002 Scenario 1 |

### Integration Tests — API Layer (`UserControllerIT.java`)

Full Spring context with a real PostgreSQL instance via Testcontainers. Uses MockMvc.

| Test case | Description |
|---|---|
| `POST_createUser_validPayload_returns201` | US-001 Scenario 1 |
| `POST_createUser_duplicateEmail_returns409` | US-001 Scenario 3 |
| `POST_createUser_invalidEmail_returns400` | US-001 Scenario 4 |
| `POST_createUser_missingName_returns400` | US-001 Scenario 5 |
| `POST_createUser_noRole_defaultsToUSER` | US-001 Scenario 2 |
| `GET_listUsers_returns200WithArray` | US-002 Scenario 1 |
| `GET_listUsers_emptyDb_returnsEmptyArray` | US-002 Scenario 2 |
| `GET_getUserById_existingUser_returns200` | US-003 Scenario 1 |
| `GET_getUserById_unknownId_returns404` | US-003 Scenario 2 |
| `PUT_updateUser_validPayload_returns200` | US-004 Scenario 1 |
| `PUT_updateUser_emailCollision_returns409` | US-004 Scenario 2 |
| `PUT_updateUser_sameEmail_returns200` | US-004 Scenario 3 |
| `PUT_updateUser_unknownId_returns404` | US-004 Scenario 4 |
| `PATCH_updateRole_validRole_returns200` | US-005 Scenario 1 |
| `PATCH_updateRole_invalidRoleValue_returns400` | US-005 Scenario 3 |
| `DELETE_deleteUser_regularUser_returns204` | US-006 Scenario 1 |
| `DELETE_deleteUser_unknownId_returns404` | US-006 Scenario 2 |
| `DELETE_deleteUser_lastAdmin_returns409` | US-006 Scenario 3 |
| `DELETE_deleteUser_oneOfMultipleAdmins_returns204` | US-006 Scenario 4 |
| `GET_health_returns200` | Health check availability |

---

## 12. Deployment

### Dockerfile (multi-stage)

```dockerfile
# Stage 1 — Build
FROM eclipse-temurin:21-alpine AS build
WORKDIR /app
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon -q
COPY src ./src
RUN ./gradlew bootJar --no-daemon -q

# Stage 2 — Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
EXPOSE 8080
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### `docker-compose.yml`

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: urbanalert-users-postgres
    environment:
      POSTGRES_DB: urbanalert_users
      POSTGRES_USER: urbanalert
      POSTGRES_PASSWORD: urbanalert
    ports:
      - "5433:5432"               # port 5433 to avoid collision with Reports (5432)
    volumes:
      - urbanalert-users-postgres-data:/var/lib/postgresql/data

  users-service:
    build:
      context: ../..
      dockerfile: deploy/docker/Dockerfile
    container_name: urbanalert-users
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/urbanalert_users
      DB_USERNAME: urbanalert
      DB_PASSWORD: urbanalert
    ports:
      - "8081:8080"               # port 8081 to avoid collision with Reports (8080)
    depends_on:
      - postgres

volumes:
  urbanalert-users-postgres-data:
```

---

## 13. Architectural Decision Records

### ADR-US-01 — Java 21 + Spring Boot 3.3 over .NET 10

**Status:** Accepted
**Context:** The existing Reports and Audit services are implemented in .NET 10 (C#). The team member responsible for this service has stronger expertise in Java.
**Decision:** Implement the Users Service in Java 21 with Spring Boot 3.3. The microservice architecture allows polyglot implementations (the Geospatial service already uses Python), and all services communicate exclusively over REST/HTTP.
**Consequences:** Team must maintain two JVM-based + two .NET-based contexts. The deployment artifact is a JVM JAR in a Docker container, identical in shape to the other services' containers. No shared code dependencies across languages.

---

### ADR-US-02 — Separate JPA Entity from Domain Entity

**Status:** Accepted
**Context:** A single class annotated with both `@Entity` (JPA) and domain logic would violate the Clean Architecture principle of keeping the domain free of framework dependencies.
**Decision:** Maintain two separate classes: `User` (pure domain entity in the domain layer) and `UserJpaEntity` (JPA-annotated class in the infrastructure layer), with a mapper between them.
**Consequences:** Slightly more code. The domain entity is fully testable without a Spring context. Persistence concerns (column names, JPA annotations) are isolated to the infrastructure layer.

---

### ADR-US-03 — Flyway over `hibernate.ddl-auto`

**Status:** Accepted
**Context:** `hibernate.ddl-auto=create` or `update` generates schema at startup without version control, making it unsafe for production.
**Decision:** Use Flyway with versioned SQL migration scripts. `hibernate.ddl-auto` is set to `validate` — Hibernate verifies the schema matches the entity model but does not modify it.
**Consequences:** Schema changes require a new numbered migration file. This is consistent with the EF Core migrations approach in the .NET services. Flyway runs automatically on startup; no manual intervention needed in CI/CD.

---

### ADR-US-04 — Manual Mappers over MapStruct

**Status:** Accepted
**Context:** MapStruct would generate mapping code at compile time and reduce boilerplate. However, the Users domain model has only four fields, making the mapping trivial.
**Decision:** Use manual mapper classes (`UserPersistenceMapper`, `UserApiMapper`) with explicit field-by-field mapping.
**Consequences:** More verbose code. Zero additional build-time annotation processing dependency. Mappings are fully visible and debuggable without generated source inspection.

---

### ADR-US-05 — UUID v7 for User Identifiers

**Status:** Accepted
**Context:** The Reports Service (C#) uses `Guid.CreateVersion7()`, which generates time-ordered UUIDs. For cross-service consistency and to allow natural chronological ordering in the database index, the same UUID version should be used.
**Decision:** Use `com.fasterxml.uuid.Generators.timeBasedEpochGenerator()` from the `java-uuid-generator` library to generate UUID v7 values.
**Consequences:** UUIDs are monotonically increasing within the same millisecond bucket, improving B-tree index performance on the `id` column. The library adds one dependency to `build.gradle.kts`.
