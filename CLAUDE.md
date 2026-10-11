# CLAUDE.md

Microservicio de Usuarios de UrbanAlert (Java 21 + Spring Boot 3, Gradle Kotlin DSL, PostgreSQL + Flyway), extraído
del monorepo UrbanAlert para migrarlo a AWS con CI/CD en GitHub Actions.

- Local: `./gradlew bootRun --args='--spring.profiles.active=local'` (H2) o `docker compose -f deploy/docker/docker-compose.yml up -d --build` (API en :8081).
- Pruebas: `./gradlew test` (integración con Testcontainers; requiere Docker/Podman).

AWS (spec `specs/001-migracion-aws/`): cuenta 348364742283, us-east-2. Terraform se aplica solo desde GitHub Actions.

- Se despliega sobre la plataforma compartida `Unal-Team-C/urban-alert-plataforma` (red privada + API Gateway; contrato en SSM `/urban-alert/plataforma/`, guía `docs/integracion.md` de ese repo), siguiendo el patrón de `Unal-Team-C/urban-alert-geoespacial` (spec 002).
- No reutilizar ni gestionar recursos de otros servicios (p. ej. los roles `urban-alert-ecs-*` de Geoespacial).

## Desarrollo guiado por especificaciones (SDD)

Este proyecto usa un arnés SDD con persistencia en disco para sobrevivir a interrupciones de sesión.
El protocolo es obligatorio en todas las sesiones:

@.sdd/PROTOCOL.md
