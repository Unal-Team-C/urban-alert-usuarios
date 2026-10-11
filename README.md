# UrbanAlert.Usuarios

Microservicio Java/Spring Boot, fuente de verdad de la identidad de usuarios de UrbanAlert (perfil, email y rol). No maneja autenticación ni sesiones — eso lo resuelve el Identity Provider externo; este servicio expone el CRUD de cuentas que consumen los demás microservicios (p. ej. Reportes para atribuir `userId`, el API Gateway para decisiones de autorización por rol).

## Estructura del repositorio

```
src/
  main/java/com/urbanalert/users/
    domain/          Modelo y reglas de negocio puras (User, Role, excepciones de dominio).
    application/     Casos de uso (puertos de entrada) y su implementación (UserService).
    infrastructure/  Adaptadores: persistencia JPA, seed de datos local.
    api/             Controladores REST, DTOs, mapeo y manejo global de excepciones.
  main/resources/     application*.yml por perfil y migraciones Flyway (db/migration).
  test/java/...       Tests unitarios (dominio, aplicación) e integración (Testcontainers + MockMvc).
deploy/docker/        Dockerfile multi-stage y docker-compose (base + override local).
docs/                 Notas de implementación y estado actual (ver docs/funcionamiento.md).
specs/                Especificación funcional y técnica (SDD) del servicio.
```

Sigue el layout estándar de Gradle/Java (`src/main`, `src/test`) en vez de carpetas `src`/`test` separadas en la raíz — es la convención del ecosistema, equivalente a cómo los servicios .NET de esta plataforma usan `src/` y `tests/` como hermanos.

## Arquitectura

Hexagonal/Clean Architecture: `domain` no depende de nada externo; `application` define los casos de uso contra puertos (`port.in`, `port.out`) implementados por `infrastructure` (JPA) y consumidos por `api` (REST). El detalle de cada capa, el modelo de datos y las decisiones de arquitectura (ADRs) están en [specs/technical-spec.md](specs/technical-spec.md); el contrato de API, reglas de negocio e historias de usuario en [specs/functional-spec.md](specs/functional-spec.md).

## Ejecutar

Requiere JDK 21. El wrapper de Gradle está versionado (`./gradlew`), no se necesita instalar Gradle.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'   # H2 en memoria, sin Docker
./gradlew test                                               # unitarios + integración (Testcontainers; requiere Docker)
```

Con el perfil `local`, `DataInitializer` siembra usuarios de ejemplo al arrancar. La API queda en `http://localhost:8080`, con Swagger UI en `/swagger-ui.html` y health check en `/actuator/health`.

## Docker Compose

```bash
docker compose -f deploy/docker/docker-compose.yml up -d --build
```

Levanta la API (puerto `8081`, perfil `docker`) y su propio PostgreSQL (puerto `5433`, para no chocar con el de Reportes). Para desarrollo en red de host en vez de mapeo de puertos, se puede sumar el override:

```bash
docker compose -f deploy/docker/docker-compose.yml -f deploy/docker/docker-compose.local.yml up -d --build
```

Las credenciales de `docker-compose.yml` son solo para desarrollo local.
