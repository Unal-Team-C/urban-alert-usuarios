# Tareas: Migración del Servicio de Usuarios a AWS

- **Plan**: ./plan.md
- **Estado**: aprobado <!-- borrador | aprobado -->

Leyenda: `[ ]` pendiente · `[~]` en progreso · `[x]` hecha · `[!]` bloqueada · `[-]` descartada · `[P]` paralelizable

Formato de cada tarea (una línea):
`- [ ] T001 [P] Descripción imperativa — archivos: ruta/a, ruta/b — cubre: RF-001 — verifica: <comando o criterio observable>`

Dependencias: cada fase depende de la anterior; dentro de una fase se ejecuta en orden salvo `[P]`.
Dependencias explícitas: añadir `— depende: T00X`.

**Herramientas de verificación local** (sin JDK, `terraform`, `aws` ni `actionlint` instalados; Podman con `podman machine start`):
- `$GRADLE` = `podman run --rm -v "$PWD":/w -w /w -v ~/.cache/gradle-podman:/root/.gradle docker.io/library/eclipse-temurin:21-jdk ./gradlew --no-daemon`
- `$TF` = `podman run --rm -v "$PWD":/w -w /w docker.io/hashicorp/terraform:1.16.5` (`-chdir=infra/<stack> init -backend=false`, `validate`, `test`; `fmt -check -recursive infra`)
- `actionlint`: `podman run --rm -v "$PWD":/repo -w /repo docker.io/rhysd/actionlint:1.7.7`
- `shellcheck`: `podman run --rm -v "$PWD":/mnt -w /mnt docker.io/koalaman/shellcheck:v0.10.0 scripts/*.sh scripts/tests/*.sh`
- Las pruebas con Testcontainers (`*IT`) necesitan un socket Docker: en local, intentar con el de Podman (`DOCKER_HOST`); si no es posible, se verifican en el job `test` de CI y se anota en el journal.

## Fase 1: Preparación

- [x] T001 Subir Spring Boot a 3.5.16, `io.spring.dependency-management` a 1.1.7 y springdoc a 2.8.17 sin otros cambios — archivos: build.gradle.kts — cubre: RF-004, RF-022 — verifica: `$GRADLE compileJava test --tests '*UserTest' --tests '*UserServiceTest'` en verde y `UserControllerIT` en verde (local con socket de Podman o en CI)
- [x] T002 [P] Excluir del contexto de build `infra/`, `.github/`, `.sdd/`, `.claude/`, `specs/`, `scripts/`, `build/` y `.gradle/` — archivos: .dockerignore — cubre: RNF-001 — verifica: `podman build -f deploy/docker/Dockerfile -t usuarios:t002 .` termina bien y el contexto enviado no incluye esas carpetas
- [x] T003 [P] Documentar el arranque que se reutiliza (OIDC, rol de GitHub y sus 3 políticas, bucket; dueños en geoespacial/plataforma) y lo que este repo necesita configurar (`AWS_ROLE_ARN`, `AWS_REGION`) — archivos: infra/bootstrap/README.md — cubre: RF-021 — verifica: el README lista cada recurso de arranque con su dueño y los 2 valores de GitHub con ejemplo, sin valores secretos
- [x] T004 [P] Crear el inventario: recursos que crea cada stack, recursos externos de arranque, recursos de la plataforma y de Geoespacial que NO se gestionan — archivos: infra/INVENTARIO.md — cubre: RF-012, RF-021 — verifica: cada recurso de §4 del plan aparece en su stack; los roles `urban-alert-ecs-*` figuran como ajenos

## Fase 2: Pruebas

- [x] T005 [P] Escribir la prueba del filtro de peticiones: una entrada con `method`, `path`, `status`, `duration_ms`, también cuando el controlador lanza excepción — archivos: src/test/java/com/urbanalert/users/api/logging/RequestLoggingFilterTest.java — cubre: RF-004 — verifica: `$GRADLE test --tests '*RequestLoggingFilterTest'` falla solo por compilación (`RequestLoggingFilter` no existe)
- [x] T006 [P] Escribir la prueba de configuración de los perfiles `aws` y `migrate` (graceful, 20 s, Flyway desactivado en `aws`, formato `ecs`, springdoc desactivado, pool desde `DB_POOL_*`; `migrate` sin web y con `db/cloud`) — archivos: src/test/java/com/urbanalert/users/AwsProfileConfigTest.java — cubre: RF-004, RF-005, RF-008 — verifica: `$GRADLE test --tests '*AwsProfileConfigTest'` falla porque faltan `application-aws.yml` / `application-migrate.yml`
- [ ] T007 [P] Escribir la prueba de la migración en la nube con Testcontainers (PostgreSQL 17): Flyway con `db/migration`+`db/cloud` y placeholders crea la tabla, 8 usuarios y `users_app`; segunda ejecución sin cambios ni duplicados; `users_app` puede SELECT/INSERT/UPDATE/DELETE y no `CREATE TABLE`; nueva contraseña sincronizada — archivos: src/test/java/com/urbanalert/users/infrastructure/CloudMigrationIT.java — cubre: RF-008, RF-009, RF-010 — verifica: la prueba falla porque falta `db/cloud/afterMigrate.sql` (local con socket o en CI)

## Fase 3: Núcleo

- [ ] T008 Implementar `RequestLoggingFilter` (pares clave-valor SLF4J, orden alto) — archivos: src/main/java/com/urbanalert/users/api/logging/RequestLoggingFilter.java — cubre: RF-004 — verifica: `$GRADLE test --tests '*RequestLoggingFilterTest'` en verde — depende: T001, T005
- [ ] T009 Crear los perfiles `aws` y `migrate` — archivos: src/main/resources/application-aws.yml, src/main/resources/application-migrate.yml — cubre: RF-001, RF-002, RF-003, RF-004, RF-005, RF-008, RF-022 — verifica: `$GRADLE test --tests '*AwsProfileConfigTest'` en verde y el resto de unitarias sigue en verde — depende: T001, T006
- [ ] T010 Crear el callback `afterMigrate.sql` (rol `${app_user}` idempotente, contraseña, CONNECT/USAGE/CRUD, privilegios por defecto) — archivos: src/main/resources/db/cloud/afterMigrate.sql — cubre: RF-010 — verifica: `CloudMigrationIT` en verde (local con socket o en CI) — depende: T007, T009
- [ ] T011 Ajustar la imagen: usuario sin privilegios, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`, `ENTRYPOINT` exec — archivos: deploy/docker/Dockerfile — cubre: RF-005, RF-006 — verifica: `podman build --platform linux/amd64 -f deploy/docker/Dockerfile -t usuarios:t011 .` y `podman run --rm --entrypoint id usuarios:t011` no es `uid=0`; `docker compose -f deploy/docker/docker-compose.yml up -d --build` deja `GET :8081/actuator/health` en `UP` y lista los 8 usuarios — depende: T002, T008, T009
- [ ] T012 Crear el stack base, parte 1: versiones, backend `usuarios/base.tfstate`, proveedor con etiquetas, variables con validaciones (`name` empieza por `urban-alert-usuarios`, backups ≥ 7), `terraform.tfvars`; red: SSM `vpc-id`/`vpc-cidr`/`subredes-privadas`, SG de tareas (salida total, entrada 8080 desde el CIDR de la VPC), SG de RDS (5432 solo desde tareas) — archivos: infra/base/versions.tf, backend.tf, providers.tf, variables.tf, terraform.tfvars, network.tf — cubre: RF-011, RF-013, RF-018, RNF-001 — verifica: `$TF -chdir=infra/base init -backend=false && $TF -chdir=infra/base validate` → Success
- [ ] T013 Base, parte 2: ECR (inmutable, scan, 20 imágenes, `force_delete = allow_destroy`), roles propios `urban-alert-usuarios-ecs-execution` / `-infrastructure` con sus políticas gestionadas y la política en línea de secretos acotada — archivos: infra/base/ecr.tf, infra/base/iam.tf — cubre: RF-007, RF-011, RF-012, RNF-001 — verifica: `validate` en verde — depende: T012
- [ ] T014 Base, parte 3: RDS PG 17 (`db.t4g.micro`, gp3 20→50, Single-AZ, privada, cifrada, backups 7, `manage_master_user_password`, `deletion_protection = !allow_destroy`, copia final por sufijo) y secreto de la app (`random_password`, JSON con `jdbc_url …?sslmode=require`, `recovery_window_in_days = 0`) — archivos: infra/base/rds.tf, infra/base/secrets.tf — cubre: RF-001, RF-002, RF-015, RNF-006, RNF-007, HU-3 — verifica: `validate` en verde — depende: T012
- [ ] T015 Base, parte 4: clúster, grupos de logs (30 días), task definition `urban-alert-usuarios-migrate` (perfil `aws,migrate`, secretos del maestro y de la app, X86_64) y salidas — archivos: infra/base/ecs.tf, infra/base/logs.tf, infra/base/migrate_task.tf, infra/base/outputs.tf — cubre: RF-008, RF-011 — verifica: `validate` en verde; `outputs.tf` expone las salidas de §7 del plan — depende: T013, T014
- [ ] T016 Pruebas del stack base con `mock_provider` y `override_data` de SSM (RDS no pública/cifrada/backups, protección según `allow_destroy`, SG de RDS solo desde tareas, red de SSM, política de secretos acotada, nombres de roles, `jdbc_url` con `sslmode=require`) y lock con 3 plataformas — archivos: infra/base/tests/base.tftest.hcl, infra/base/.terraform.lock.hcl — cubre: RF-002, RF-012, RF-015, RF-018, RNF-001, RNF-007 — verifica: `$TF -chdir=infra/base test` todo en verde — depende: T015
- [ ] T017 Crear el stack service (`terraform_remote_state` de base, Express con todas las subredes, SG de tareas, puerto 8080, `/actuator/health`, `SPRING_PROFILES_ACTIVE=aws`, secretos, pool, escalado CPU 1–5, precondición ≤ 50, salida `service_endpoint`) con pruebas y lock — archivos: infra/service/*.tf, infra/service/terraform.tfvars, infra/service/tests/service.tftest.hcl, infra/service/.terraform.lock.hcl — cubre: RF-001, RF-003, RF-006, RF-011, RF-018, RNF-002 — verifica: `$TF -chdir=infra/service init -backend=false && validate && test` en verde (8×5 pasa, 11×5 falla)
- [ ] T018 Crear el stack routes (SSM `api-id`/`vpc-link-id`/`api-endpoint`, 6 integraciones y rutas de §7 del plan, salidas) con pruebas y lock — archivos: infra/routes/*.tf, infra/routes/terraform.tfvars, infra/routes/tests/routes.tftest.hcl, infra/routes/.terraform.lock.hcl — cubre: RF-019, HU-5 — verifica: `$TF -chdir=infra/routes init -backend=false && validate && test` en verde; `$TF fmt -check -recursive infra` sin diferencias

## Fase 4: Integración

- [ ] T019 [P] Copiar de Geoespacial `check-platform.sh`, `buscar-listener-express.sh`, `run-schema-task.sh`, `diagnose-service.sh` y sus pruebas, ajustando solo comentarios y referencias a la spec — archivos: scripts/check-platform.sh, scripts/buscar-listener-express.sh, scripts/run-schema-task.sh, scripts/diagnose-service.sh, scripts/tests/test-check-platform.sh, scripts/tests/test-buscar-listener.sh — cubre: RF-008, RF-014, RF-018, RF-020 — verifica: `bash scripts/tests/test-check-platform.sh && bash scripts/tests/test-buscar-listener.sh` en verde; shellcheck sin avisos
- [ ] T020 [P] Escribir la prueba de humo por el gateway (D-011) y su prueba con un `curl` simulado — archivos: scripts/smoke-gateway-usuarios.sh, scripts/tests/test-smoke.sh — cubre: RF-014, RF-019, HU-5 — verifica: `bash scripts/tests/test-smoke.sh` en verde (pasa con respuestas correctas y falla si un `POST` sin firma devuelve 201); shellcheck sin avisos
- [ ] T021 [P] Escribir `signed-request.sh` (curl `--aws-sigv4`, credenciales del entorno, token de sesión opcional) — archivos: scripts/signed-request.sh — cubre: RF-019, RF-021 — verifica: shellcheck sin avisos; sin credenciales termina con un mensaje de uso y código ≠ 0
- [ ] T022 Escribir `provision.yml` (push a `main`, `concurrency: urban-alert-usuarios-aws`; job `test`: Gradle, scripts, Terraform x3, actionlint, shellcheck; job `provision`: orden de §7 del plan) — archivos: .github/workflows/provision.yml — cubre: RF-007, RF-014, RF-016, RF-017, RNF-003, RNF-005 — verifica: actionlint sin errores — depende: T016, T017, T018, T019, T020
- [ ] T023 Escribir `deprovision.yml` (manual, confirmación `urban-alert-usuarios`, solo `main`, misma `concurrency`; routes → service → base `allow_destroy` → destroy, cada paso tolera estado vacío) — archivos: .github/workflows/deprovision.yml — cubre: RF-015, RF-016, RF-017, HU-2 — verifica: actionlint sin errores — depende: T022

## Fase 5: Pulido

- [ ] T024 Documentar el despliegue en el README (arquitectura, rutas públicas e internas, cómo firmar con `signed-request.sh`, workflows, variables y secretos, costes, desaprovisionar) y actualizar `docs/funcionamiento.md` (perfiles `aws`/`migrate`) y `CLAUDE.md` — archivos: README.md, docs/funcionamiento.md, CLAUDE.md — cubre: RF-021, RNF-006, HU-6 — verifica: el README contiene la tabla de 6 rutas, `AWS_ROLE_ARN`/`AWS_REGION` con ejemplo y la tabla de costes; sin secretos
- [ ] T025 Comprobación completa antes del PR: suite Gradle, Terraform x3 (`fmt`, `validate`, `test`), pruebas de scripts, actionlint, shellcheck; búsqueda de secretos en el árbol — archivos: (ninguno) — cubre: RF-022, RNF-001, P6 — verifica: todo en verde; `git grep -n -i -E "password|secret" -- ':!*.md'` solo muestra referencias a variables/secretos, no valores
- [ ] T026 Pedir al usuario que configure `AWS_ROLE_ARN` y `AWS_REGION` en `Unal-Team-C/urban-alert-usuarios` y que confirme que no existe el ECR `urban-alert-usuarios`; con su permiso, publicar la rama y abrir el PR a `main` — archivos: (ninguno) — cubre: RF-016, D-015 — verifica: `gh api repos/Unal-Team-C/urban-alert-usuarios/actions/variables` muestra `AWS_REGION`; PR abierto
- [ ] T027 Tras el merge (decisión del usuario): seguir el primer Provision, corregir fallos con commits pequeños y registrar en el inventario la fecha del primer aprovisionamiento — archivos: según fallos, infra/INVENTARIO.md — cubre: HU-1, RF-014 — verifica: Provision en verde; humo por el gateway en verde
- [ ] T028 Segundo Provision sin cambios para comprobar idempotencia — archivos: (ninguno) — cubre: RNF-003, MS-003 — verifica: los 3 `apply` informan «No changes» (salvo la task definition si cambia el tag)

## Correcciones de verificación

<!-- /sdd-verify añade aquí tareas si detecta fallos -->

## Cobertura

| Requisito | Tareas |
|---|---|
| RF-001 | T009, T014, T017 |
| RF-002 | T009, T014, T016 |
| RF-003 | T009, T017 |
| RF-004 | T001, T005, T006, T008, T009 |
| RF-005 | T006, T009, T011 |
| RF-006 | T011, T017 |
| RF-007 | T013, T022 |
| RF-008 | T006, T007, T009, T015, T019 |
| RF-009 | T007 (semilla ya anonimizada en `ebd5bf2`) |
| RF-010 | T007, T010 |
| RF-011 | T012–T015, T017 |
| RF-012 | T004, T013, T016 |
| RF-013 | T012 |
| RF-014 | T019, T020, T022, T027 |
| RF-015 | T014, T016, T023 |
| RF-016 | T022, T023, T026 |
| RF-017 | T022, T023 |
| RF-018 | T012, T016, T017, T019 |
| RF-019 | T018, T020, T021 |
| RF-020 | T019 |
| RF-021 | T003, T004, T021, T024 |
| RF-022 | T001, T009, T025 |
| RNF-001 | T002, T012, T013, T016, T025 |
| RNF-002 | T017 |
| RNF-003 | T022, T028 |
| RNF-004 | Express rolling + T009 (graceful); se mide en `/sdd-verify` |
| RNF-005 | T022 (caché, omitir push); se mide en `/sdd-verify` |
| RNF-006 | T014, T024 |
| RNF-007 | T014, T016 |
