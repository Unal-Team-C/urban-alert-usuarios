# Funcionamiento del servicio de Usuarios

## Alcance actual

CRUD completo de usuarios (`POST`, `GET` lista, `GET` por id, `PUT`, `PATCH /role`, `DELETE`) bajo `/api/v1/users`, respaldado por PostgreSQL vía Spring Data JPA, con esquema versionado por Flyway. No hay autenticación ni autorización en el propio servicio: se asume que el API Gateway ya validó la identidad antes de reenviar la petición.

Reglas de negocio implementadas (`UserService` + `User`, ver `specs/functional-spec.md` §4 para el detalle):

- Email único (verificado antes de insertar/actualizar; además hay índice único `uix_users_email` como segunda línea de defensa — `GlobalExceptionHandler` traduce la violación de esa constraint a `409`).
- Protección del último administrador: ni `PATCH /role` ni `DELETE` pueden dejar el sistema sin ningún `ADMIN`.
- Validación de formato de nombre/email tanto en el DTO de entrada (`jakarta.validation`) como en el constructor del dominio (`User`), para que la invariante se cumpla también si algo construye un `User` sin pasar por la API.

## Perfiles de Spring

| Perfil | Base de datos | Uso |
|---|---|---|
| (ninguno / default) | — | Config común: puerto, Flyway, Actuator, SpringDoc. No es ejecutable solo. |
| `local` | H2 en memoria | `./gradlew bootRun` para desarrollo sin Docker. `DataInitializer` siembra usuarios de ejemplo al arrancar (solo con este perfil). |
| `docker` | PostgreSQL (`DB_URL`/`DB_USERNAME`/`DB_PASSWORD` por variable de entorno) | Usado por `deploy/docker/docker-compose.yml`. |

`V2__seed_users.sql` (Flyway) inserta el mismo conjunto de usuarios de ejemplo que `DataInitializer`, pero esa migración corre en **cualquier** perfil con Flyway habilitado (default y `docker`), no solo en `local`. Son datos sintéticos: el administrador por defecto, seis usuarios anónimos (`usuarioNN@example.com`) y `test-user` (`urbanalerttest@proton.me`) para pruebas. Una base local creada con la versión anterior de `V2` (con datos reales) falla la validación de Flyway por cambio de checksum: recrearla con `docker compose -f deploy/docker/docker-compose.yml down -v`.

## UUID v7

Los IDs se generan con `com.fasterxml.uuid.Generators.timeBasedEpochGenerator()` (ADR-US-05), para quedar ordenados cronológicamente igual que `Guid.CreateVersion7()` en los servicios .NET (Reportes, Auditoría). Esto mejora la localidad del índice B-tree de `id` frente a UUID v4 aleatorio.

## Brechas conocidas / fuera de alcance

- No emite eventos de dominio al bus de mensajería (MassTransit/RabbitMQ) — si en el futuro algo necesita reaccionar a cambios de rol, habría que agregar un evento `UserRoleChanged` siguiendo el mismo patrón que usa Reportes.
- No valida que el `role` enviado en `CreateUserRequest`/`UpdateUserRequest` sea estrictamente `USER`/`ADMIN` más allá de lo que ya hace la deserialización del enum (un valor no reconocido cae en `HttpMessageNotReadableException`, mapeado a `400`).
- `specs/technical-spec.md` documentaba originalmente Maven (`pom.xml`); el proyecto real usa Gradle Kotlin DSL (`build.gradle.kts`) — el spec ya se actualizó para reflejarlo.
