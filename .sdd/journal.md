# Journal SDD

<!-- Bitácora append-only: añade entradas AL FINAL, no reescribas las anteriores.
     Formato:
     ## AAAA-MM-DD · <feature o "general"> · <evento>
     - detalle (qué, por qué, archivos, verificación)
     Las horas exactas de cada edición/checkpoint quedan en .sdd/activity.log. -->

## 2026-10-11 · general · Arnés SDD instalado
- La copia del arnés llegó vacía (`.sdd/`, `.claude/`); se copió desde `urban-alert-geoespacial` (comandos, hooks, protocolo, plantillas) con STATE/journal nuevos.
- Los hooks usan `powershell`, no disponible en este Mac: el protocolo se sigue a mano.
- Constitución 1.0.0: P1–P10 adaptados de la plataforma (1.0.1) para un microservicio (P2 añade: no gestionar recursos de otros servicios).

## 2026-10-11 · 001-migracion-aws · Creada spec
- Desde la petición del usuario + README de `UrbanAlert/infrastructure` y README/guía de `urban-alert-plataforma` (copiados a `fuentes/`). Referencia: geoespacial spec 002.
- ECS Express en la red privada compartida, RDS PostgreSQL propio, solo por el gateway (`/api/v1/usuarios/*` → `/api/v1/users/*`), Provision en push a `main`, Deprovision manual.
- 2 `[NEEDS CLARIFICATION]`: datos iniciales en la nube (RF-009) y autorización de las rutas (RF-019). Indicaciones técnicas en `notes.md`.

## 2026-10-11 · 001-migracion-aws · Clarificaciones
- RF-009: la nube lleva los 7 usuarios de la semilla V2 (como en local).
- RF-019: GET públicos; POST/PUT/PATCH role/DELETE con AWS_IAM (6 rutas exactas). El frontend no puede escribir sin firmante (aceptado, fuera de alcance).
- Trabajo en la rama `aws-provisioning`, PR a `main`. Spec en estado `aclarada`.

## 2026-10-11 · 001-migracion-aws · Semilla anonimizada (cambio fuera del flujo, pedido por el usuario)
- `V2__seed_users.sql` y `DataInitializer` (perfil local): 6 usuarios reales → `Usuario Dos..Siete` / `usuarioNN@example.com`; nuevo `test-user` / `urbanalerttest@proton.me` (id ...0008, USER). Admin sin cambios. RF-009 actualizado (8 usuarios, sin datos reales).
- Se editó V2 en sitio (no V3): la base de la nube será nueva; una base local antigua falla el checksum de Flyway → `docker compose ... down -v` (documentado en docs/funcionamiento.md).
- Verificado: V1+V2+V2 en postgres:16 → 8 filas (idempotente); compileJava + UserTest (16) + UserServiceTest (19) en verde en temurin:21 por Podman. UserControllerIT (Testcontainers) no se ejecutó.
- Los datos reales siguen en el historial de git (commits anteriores).

## 2026-10-11 · general · Historial de git sin reescribir
- El usuario decidió no reescribir el historial: los datos reales de la semilla anterior siguen en `603bb86` (origin/main) y en `f5c314f` (local). Solo se anonimizó el árbol actual (`ebd5bf2`).

## 2026-10-11 · 001-migracion-aws · Spec aprobada
- El usuario aprobó la spec («Go ahead»). Fase plan.

## 2026-10-11 · 001-migracion-aws · Plan redactado
- 3 stacks (base/service/routes) como Geoespacial; estado en `usuarios/`.
- D-002/D-003: Flyway en tarea ECS puntual (perfil `aws,migrate`, usuario maestro) + callback `afterMigrate` que crea/sincroniza `users_app` (CRUD, sin DDL); servicio con Flyway desactivado.
- D-004: roles propios `urban-alert-usuarios-ecs-*` (no los de Geoespacial). D-005: Spring Boot 3.3.4 → 3.5.16 para logs ECS nativos. D-009: pool 8 × 5 tareas = 40.
- D-010: 6 rutas explícitas (2 GET NONE, 4 escrituras AWS_IAM). D-011: humo sin escrituras; `signed-request.sh` (curl --aws-sigv4) para firmadas.

## 2026-10-11 · 001-migracion-aws · Plan aprobado
- El usuario aprobó el plan («Go ahead»). Fase tasks.

## 2026-10-11 · 001-migracion-aws · tasks.md generado
- 28 tareas: preparación 4, pruebas 3, núcleo 11, integración 5, pulido 5. Todo RF/RNF cubierto (RNF-004/005 se miden en verify).

## 2026-10-11 · 001-migracion-aws · T001 hecha
- build.gradle.kts: Spring Boot 3.3.4→3.5.16, dependency-management 1.1.7, springdoc 2.8.17.
- Verificado: UserTest 16, UserServiceTest 19, UserControllerIT 24 en verde con Boot 3.5.16 (Testcontainers por el socket de Podman). Un primer intento falló al escribir el XML del informe en la carpeta compartida (transitorio); la repetición pasó.

## 2026-10-11 · 001-migracion-aws · tasks.md aprobado
- El usuario aprobó las tareas («begin implementing the tasks»).

## 2026-10-11 · 001-migracion-aws · T002 hecha
- .dockerignore: el contexto queda en build.gradle.kts, settings, gradle/, gradlew, deploy/ y src/.
- Verificado: imagen de prueba con COPY . lista solo esos archivos; `podman build --platform linux/amd64 -f deploy/docker/Dockerfile` termina bien.

## 2026-10-11 · 001-migracion-aws · T003 hecha
- infra/bootstrap/README.md: arranque reutilizado (OIDC, rol, 3 políticas, bucket) con su repo dueño; AWS_ROLE_ARN/AWS_REGION con ejemplo y comandos gh; requisito: ECR urban-alert-usuarios no debe existir.
- Verificado: revisión del contenido; sin valores secretos.

## 2026-10-11 · 001-migracion-aws · T004 hecha
- infra/INVENTARIO.md: recursos por stack (nada importado), arranque externo y recursos de otros repos; roles urban-alert-ecs-* de Geoespacial marcados como no usados. Nombre de la política en línea fijado: `read-usuarios-secrets`.
- Verificado: todos los recursos de §4 del plan aparecen.

## 2026-10-11 · 001-migracion-aws · T005 hecha
- RequestLoggingFilterTest: una entrada con method/path/status/duration_ms (pares clave-valor SLF4J), sin query string; status 500 si la cadena lanza.
- Verificado: compileTestJava falla solo por `cannot find symbol RequestLoggingFilter` (rojo esperado; como en Geoespacial, el commit queda en rojo hasta T008).

## 2026-10-11 · 001-migracion-aws · T006 hecha
- AwsProfileConfigTest: perfil aws (datasource y pool desde el entorno, Flyway off, validate, formato ecs, graceful 20 s, springdoc off), perfil migrate (web none, Flyway on con db/migration+db/cloud, placeholders) y application.yml sin db/cloud.
- Verificado: 5 pruebas, 4 fallan porque faltan application-aws.yml/application-migrate.yml (rojo esperado); la de application.yml pasa.

## 2026-10-11 · 001-migracion-aws · T007 hecha
- CloudMigrationIT (postgres:17-alpine, API de Flyway, métodos ordenados): 1ª migración (2 versiones, 8 usuarios anonimizados, 1 ADMIN, sin gmail), 2ª sin cambios, users_app con CRUD y sin CREATE/DROP, nueva contraseña sincronizada.
- Verificado (socket de Podman): 1 y 2 pasan; 3 y 4 fallan con `password authentication failed for user users_app` (rojo esperado: falta afterMigrate.sql).

## 2026-10-11 · 001-migracion-aws · T008 hecha
- RequestLoggingFilter (@Component, HIGHEST_PRECEDENCE): log.atInfo() con method/path/status/duration_ms; 500 si la cadena lanza; sin query string.
- Verificado: RequestLoggingFilterTest 2/2, UserTest 16/16, UserServiceTest en verde.

## 2026-10-11 · 001-migracion-aws · T009 hecha
- application-aws.yml (datasource/pool por entorno, Flyway off, validate, ECS JSON, graceful 20 s, springdoc off) y application-migrate.yml (web none, Flyway on con db/migration+db/cloud, placeholders desde DB_APP_USER/DB_APP_PASSWORD).
- Desviación menor del plan §7: la tarea de migración no recibe DB_POOL_*; su pool queda fijo en 2 en el perfil (Flyway puede necesitar una conexión aparte). Plan actualizado.
- Verificado: AwsProfileConfigTest 5/5; RequestLoggingFilterTest, UserTest, UserServiceTest en verde.

## 2026-10-11 · 001-migracion-aws · T010 hecha
- db/cloud/afterMigrate.sql: DO con format(%I/%L): crea users_app si falta, ALTER ROLE PASSWORD, CONNECT/USAGE, CRUD en todas las tablas + privilegios por defecto; revoca escritura sobre flyway_schema_history (añadido respecto a D-007: la app no toca el historial).
- Verificado: suite completa en verde (70 pruebas: AwsProfileConfigTest 5, UserControllerIT 24, RequestLoggingFilterTest 2, UserTest 16, CloudMigrationIT 4, UserServiceTest 19). Un primer intento falló de nuevo al escribir informes tras borrar build/test-results (transitorio de la carpeta compartida).

## 2026-10-11 · 001-migracion-aws · T011 hecha
- Dockerfile: usuario `app` (uid 100), JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75, COPY --chown, ENTRYPOINT exec.
- Verificado: build linux/amd64 OK; `id` = uid=100(app); docker compose (perfil docker): health UP, 8 usuarios, línea de petición en texto.
- Simulación local de la nube (postgres:17 + imagen): la tarea `aws,migrate` FALLÓ con pool 2 (Hikari agotado: Flyway usa 3 conexiones con callbacks) → pool del perfil migrate subido a 4 (application-migrate.yml); después exit 0, repetida exit 0, sin la contraseña en logs. Servicio `aws` como users_app: health UP, 8 usuarios, logs JSON ECS con method/path/status/duration_ms; SIGTERM → «Graceful shutdown complete» y cierre del pool (exit 143 = SIGTERM de la JVM, normal).
- Hallazgo previo (fuera de alcance): GlobalExceptionHandler convierte NoResourceFoundException/405 en 500 (p. ej. /v3/api-docs desactivado → 500). Por el gateway no se alcanza (rutas no registradas → 404 del gateway). Se comunica al usuario.

## 2026-10-11 · 001-migracion-aws · T012 hecha
- infra/base: versions, backend (usuarios/base.tfstate), providers (Service=usuarios), variables (name con prefijo urban-alert-usuarios, backups ≥ 7, container_port 8080), tfvars, network.tf (SSM vpc-id/vpc-cidr/subredes-privadas, SG tareas con entrada 8080 desde el CIDR, SG RDS solo desde tareas). .gitignore: .terraform/.
- Verificado: init -backend=false + validate «Success»; fmt -check sin cambios.

## 2026-10-11 · 001-migracion-aws · T013 hecha
- ecr.tf (inmutable, scan, ciclo de vida, force_delete = allow_destroy) e iam.tf (urban-alert-usuarios-ecs-execution con AmazonECSTaskExecutionRolePolicy + read-usuarios-secrets acotada a secret:urban-alert-usuarios-* y rds!* etiquetado con esta base; urban-alert-usuarios-ecs-infrastructure con la política de Express).
- Observación: la política read-project-secrets del rol de Geoespacial cubre secret:urban-alert-*, que incluye el secreto de Usuarios (repo ajeno; se comunica al usuario).
- Verificado: validate «Success», fmt sin cambios.

## 2026-10-11 · 001-migracion-aws · T014 hecha
- rds.tf (PG 17 propio, db.t4g.micro, gp3 20→50, Single-AZ, privada, cifrada, backups 7, maestro gestionado por RDS, protección según allow_destroy, copia final por sufijo) y secrets.tf (random_password alfanumérico, secreto urban-alert-usuarios-db con jdbc_url ?sslmode=require, recovery 0).
- Verificado: validate «Success», fmt sin cambios.

## 2026-10-11 · 001-migracion-aws · T015 hecha
- ecs.tf (clúster urban-alert-usuarios), logs.tf (/ecs/urban-alert-usuarios y -migrate, 30 días), migrate_task.tf (Fargate X86_64 512/1024, SPRING_PROFILES_ACTIVE=aws,migrate, DB_URL/DB_APP_PASSWORD del secreto de la app, DB_USERNAME/DB_PASSWORD del secreto maestro rds!), outputs.tf (salidas de §7 del plan).
- Verificado: validate «Success», fmt sin cambios.

## 2026-10-11 · 001-migracion-aws · T016 hecha
- infra/base/tests/base.tftest.hcl: 11 runs (protección y copia final, RDS propia/privada/cifrada/backups, SG RDS solo desde tareas, red de SSM, entrada 8080 solo desde la VPC, roles y secretos propios, jdbc_url con TLS y users_app, tarea de migración con imagen del commit/perfil aws,migrate/maestro, allow_destroy, validaciones de name y backups). Lock: aws 6.68.0, random 3.9.1 con hashes linux_amd64/linux_arm64/darwin_arm64.
- Verificado: `terraform test` 11 passed; `init -backend=false -lockfile=readonly` OK.

## 2026-10-11 · 001-migracion-aws · T017 hecha
- infra/service: backend usuarios/service.tfstate, remote_state de base, Express (todas las subredes privadas, SG de tareas, roles propios, 8080, /actuator/health, SPRING_PROFILES_ACTIVE=aws, DB_URL/USERNAME/PASSWORD del secreto, pool 2/8, CPU 60 % 1–5 tareas, 512/1024), precondición pool×tareas ≤ 50, salida service_endpoint. Lock con 3 plataformas.
- Verificado: init -lockfile=readonly OK, validate OK, `terraform test` 6 passed (incl. 11×5 rechazado), fmt sin cambios.

## 2026-10-11 · 001-migracion-aws · T018 hecha
- infra/routes: backend usuarios/routes.tfstate, SSM api-id/vpc-link-id/api-endpoint, 6 integraciones HTTP_PROXY por VPC Link (Host y TLS = endpoint, payload 1.0, overwrite:path a /api/v1/users...) y 6 rutas (GET NONE; POST/PUT/PATCH role/DELETE AWS_IAM); salidas gateway_url y route_keys. Lock con 3 plataformas.
- Verificado: `terraform test` 7 passed; validate OK; fmt -check sin cambios.

## 2026-10-11 · 001-migracion-aws · T019 hecha
- scripts/: check-platform.sh, buscar-listener-express.sh (versión corregida con búsqueda por regla host-header), run-schema-task.sh (comentarios: tarea de migración Flyway), diagnose-service.sh y pruebas test-check-platform/test-buscar-listener, copiados de Geoespacial.
- Verificado: 6 casos de las pruebas de scripts en verde; shellcheck v0.10.0 sin avisos.

## 2026-10-11 · 001-migracion-aws · T020 hecha
- smoke-gateway-usuarios.sh (solo lectura): GET lista 200 con admin (reintentos), GET admin 200, GET id desconocido 404 del servicio, POST y DELETE sin firma 403, /actuator/health, GET …/{id}/role y /api/v1/users → 404. test-smoke.sh con curl simulado (gateway correcto, POST abierto, servicio caído, argumento inválido).
- Verificado: 4/4 casos de test-smoke; shellcheck sin avisos.

## 2026-10-11 · 001-migracion-aws · T021 hecha
- signed-request.sh: curl --aws-sigv4 aws:amz:<región>:execute-api; credenciales del entorno pasadas a curl por --config en stdin (no en la línea de comandos); token de sesión como cabecera; cuerpo JSON opcional.
- Verificado: sin argumentos o sin credenciales → mensaje de uso y exit 2; shellcheck sin avisos; con credenciales falsas contra un eco HTTP público (httpbin.org) curl envía Authorization AWS4-HMAC-SHA256 con x-amz-security-token firmado.

## 2026-10-11 · 001-migracion-aws · T022 hecha
- provision.yml: push a main, concurrency urban-alert-usuarios-aws sin cancelación. Job test: setup-java 21 + setup-gradle, ./gradlew test (Testcontainers con el Docker del runner), pruebas de scripts, Terraform fmt/validate/test de los 3 stacks con -lockfile=readonly, actionlint, shellcheck v0.10.0. Job provision: OIDC → check-platform → base → ECR (omite si el tag existe; build -f deploy/docker/Dockerfile) → run-schema-task (migración) → service → diagnose si falla → listener → routes → humo → resumen.
- test-smoke.sh marcado ejecutable.
- Verificado: actionlint sin errores.

## 2026-10-11 · 001-migracion-aws · T023 hecha
- deprovision.yml (adaptado del de Geoespacial): workflow_dispatch con confirmación urban-alert-usuarios y solo main; misma concurrency; routes destroy (valores de relleno) → service destroy → base apply allow_destroy + sufijo run_id → base destroy; cada paso tolera estado vacío; resumen con la copia final.
- Verificado: actionlint sin errores.
