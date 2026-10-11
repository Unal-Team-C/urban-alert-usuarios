# CLAUDE.md

Microservicio de Usuarios de UrbanAlert (Java 21 + Spring Boot 3, Gradle Kotlin DSL, PostgreSQL + Flyway), extraído
del monorepo UrbanAlert para migrarlo a AWS con CI/CD en GitHub Actions.

- Local: `./gradlew bootRun --args='--spring.profiles.active=local'` (H2) o `docker compose -f deploy/docker/docker-compose.yml up -d --build` (API en :8081).
- Pruebas: `./gradlew test` (integración con Testcontainers; requiere Docker/Podman).

AWS (spec `specs/001-migracion-aws/`): cuenta 348364742283, us-east-2. Terraform se aplica solo desde GitHub Actions.

- Se despliega sobre la plataforma compartida `Unal-Team-C/urban-alert-plataforma` (red privada + API Gateway; contrato en SSM `/urban-alert/plataforma/`, guía `docs/integracion.md` de ese repo), siguiendo el patrón de `Unal-Team-C/urban-alert-geoespacial` (spec 002).
- No reutilizar ni gestionar recursos de otros servicios (p. ej. los roles `urban-alert-ecs-*` de Geoespacial).
- `infra/base` (ECR, roles propios, SG, RDS, secreto, logs, clúster, task definition de migración), `infra/service` (Express) e `infra/routes` (6 rutas: GET públicos, escrituras con AWS_IAM); estado en `s3://urban-alert-tfstate-348364742283/usuarios/`. Inventario: `infra/INVENTARIO.md`.
- Perfiles `aws` (servicio) y `aws,migrate` (tarea de migración: Flyway + `db/cloud/afterMigrate.sql`).
- Workflows: `provision.yml` (push a `main`) y `deprovision.yml` (manual, confirmación `urban-alert-usuarios`).

## Validación local (Podman, sin JDK ni Terraform instalados)

- Gradle: `podman run --rm -v "$PWD":/w -w /w -v ~/.cache/gradle-podman:/root/.gradle docker.io/library/eclipse-temurin:21-jdk ./gradlew --no-daemon test`. Para las pruebas con Testcontainers añadir `--network host --security-opt label=disable -v /run/user/501/podman/podman.sock:/var/run/docker.sock -e TESTCONTAINERS_RYUK_DISABLED=true -e TESTCONTAINERS_HOST_OVERRIDE=localhost`.
- Terraform, actionlint y shellcheck: comandos en el README («Validar en local»).

## Gotchas

- No borrar `build/test-results` mientras Gradle corre en el contenedor: la escritura de informes en la carpeta compartida falla de forma intermitente.
- Flyway con callbacks usa 3 conexiones: el pool del perfil `migrate` es 4.
- macOS trae bash 3.2: los scripts no usan funciones de bash 4.

## Desarrollo guiado por especificaciones (SDD)

Este proyecto usa un arnés SDD con persistencia en disco para sobrevivir a interrupciones de sesión.
El protocolo es obligatorio en todas las sesiones:

@.sdd/PROTOCOL.md
