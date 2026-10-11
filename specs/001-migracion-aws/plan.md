# Plan técnico: Migración del Servicio de Usuarios a AWS

- **Spec**: ./spec.md
- **Estado**: borrador <!-- borrador | aprobado -->
- **Fecha**: 2026-10-11
- **Referencia**: `Unal-Team-C/urban-alert-geoespacial`, spec y plan 002-migracion-aws (aplicado y verificado en la cuenta). Lo que aquí no se justifica de otra forma se copia de ese patrón.

## 1. Resumen técnico

Tres stacks de Terraform con estado en `s3://urban-alert-tfstate-348364742283/usuarios/`: **base** (ECR, roles propios, grupos de seguridad, RDS PostgreSQL 17 propio, secreto de la aplicación, logs, clúster y la task definition de migración), **service** (`aws_ecs_express_gateway_service` en las subredes privadas de la plataforma) y **routes** (6 rutas en el API Gateway compartido: 2 `GET` públicas y 4 de escritura con `AWS_IAM`). El esquema y la semilla los aplica Flyway en una **tarea ECS puntual** con la misma imagen y el perfil `aws,migrate`, como administrador, que además crea y sincroniza el rol de la aplicación con un callback `afterMigrate`; el servicio arranca con el perfil `aws`, Flyway desactivado y un usuario sin DDL. En el código: Spring Boot 3.3.4 → 3.5.16 para tener logs estructurados nativos (ECS JSON), un filtro con una línea de log por petición, apagado ordenado, imagen sin root. `provision.yml` (push a `main`) y `deprovision.yml` (manual) siguen el orden de Geoespacial.

## 2. Contexto técnico

| Aspecto | Valor |
|---|---|
| Lenguaje / versión | Java 21, Spring Boot **3.5.16** (desde 3.3.4), Gradle 8.10 (wrapper, sin cambios), HCL (Terraform ≥ 1.10), YAML de Actions, bash 3.2-compatible |
| Dependencias | Sin dependencias nuevas de aplicación. `springdoc-openapi-starter-webmvc-ui` 2.6.0 → **2.8.17** (compatible con Boot 3.5); plugin `io.spring.dependency-management` 1.1.6 → **1.1.7**. Terraform: `hashicorp/aws ~> 6.0`, `hashicorp/random ~> 3.6`. Actions: `actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4`, `aws-actions/configure-aws-credentials@v4`, `aws-actions/amazon-ecr-login@v2`, `hashicorp/setup-terraform@v3` |
| Almacenamiento | RDS for PostgreSQL 17, `db.t4g.micro`, gp3 20 GiB (máx. 50), Single-AZ, cifrado, backups 7 días, `deletion_protection`, base `urbanalert_users` |
| Pruebas | `./gradlew test` (JUnit 5 + Testcontainers con `postgres:16-alpine`; en CI hay Docker), `terraform fmt/validate/test` con `mock_provider`, `actionlint`, `shellcheck`, pruebas de scripts con stubs de `aws` |
| Plataforma objetivo | AWS us-east-2, VPC/subredes privadas de la plataforma (SSM), ECS Express (Fargate, X86_64), 512 CPU / 1024 MiB por tarea, 1–5 tareas |
| Restricciones | Rol OIDC solo desde `main`; políticas existentes del rol (`github-actions-urban-alertPolicy`, `-terraform`, `-platform`) acotadas a `urban-alert-*`; ≤ 50 conexiones; sin `aws`, `terraform` ni JDK instalados en el Mac (todo por Podman o en CI) |

**Comandos locales**

```bash
podman run --rm -v "$PWD":/w -w /w -v ~/.cache/gradle-podman:/root/.gradle docker.io/library/eclipse-temurin:21-jdk ./gradlew --no-daemon test   # unitarias; las IT necesitan socket Docker
podman run --rm -v "$PWD":/w -w /w docker.io/hashicorp/terraform:1.16.5 -chdir=infra/<stack> init -backend=false   # luego validate / test
podman run --rm -v "$PWD":/w -w /w docker.io/hashicorp/terraform:1.16.5 fmt -check -recursive infra
podman run --rm -v "$PWD":/repo -w /repo docker.io/rhysd/actionlint:1.7.7
podman run --rm -v "$PWD":/mnt -w /mnt docker.io/koalaman/shellcheck:v0.10.0 scripts/*.sh scripts/tests/*.sh
```

## 3. Verificación contra la constitución

| Principio | ¿Cumple? | Nota |
|---|---|---|
| P1 IaC solo desde Actions | Sí | Todo en `infra/`; `apply`/`destroy` solo en los workflows sobre `main`. En local, solo `init -backend=false`/`validate`/`test`/`fmt` |
| P2 Arranque y recursos ajenos fuera | Sí | Roles propios `urban-alert-usuarios-ecs-*` (D-004); SSM y gateway solo leídos o con rutas propias; ningún `import` de recursos de Geoespacial o de la plataforma |
| P3 Sin secretos | Sí | OIDC; contraseñas en Secrets Manager (`random_password`, maestro gestionado por RDS); salidas sin secretos; la tarea de migración no imprime credenciales |
| P4 Destructivo solo explícito | Sí | `deletion_protection` + `allow_destroy` solo desde `deprovision.yml` con confirmación `urban-alert-usuarios`; copia final |
| P5 Coste mínimo | Sí | D-013: tamaños mínimos y tabla de costes en el README. Recursos con coste nuevos: RDS micro, Fargate 1 tarea, CloudWatch; el ALB de Express ya existe (lo comparte con Geoespacial) |
| P6 Validación previa | Sí | Job `test` antes del `provision`; mismas comprobaciones antes de cada commit de tarea |
| P7 Pruebas de Terraform | Sí | `.tftest.hcl` en los 3 stacks (§9) |
| P8 Versiones fijadas | Sí | Terraform 1.16.5, `.terraform.lock.hcl` con 3 plataformas, actions por versión mayor fijada (como Geoespacial y la plataforma), imágenes de herramientas por versión |
| P9 Contrato y nombres | Sí | Solo SSM `/urban-alert/plataforma/*`; prefijo `urban-alert-usuarios`; etiquetas `Project`, `Service=usuarios`, `ManagedBy`, `Stack`; rutas solo bajo `/api/v1/usuarios` |
| P10 Serializado e idempotente | Sí | `concurrency: urban-alert-usuarios-aws` sin cancelación en ambos workflows; ningún recurso cambia en cada ejecución salvo la imagen/tag (D-003) |

## 4. Arquitectura y componentes

```text
GitHub push a main ── provision.yml ── OIDC ──┐                 workflow_dispatch ── deprovision.yml
                                              ▼
┌──── AWS us-east-2 · VPC de la plataforma (SSM /urban-alert/plataforma/*) ───────────────────────────┐
│ stack base                                                                                          │
│   ECR urban-alert-usuarios ◄── docker push :<sha>                                                   │
│   IAM urban-alert-usuarios-ecs-execution (+ leer secretos propios) · urban-alert-usuarios-ecs-infrastructure │
│   ECS cluster urban-alert-usuarios                                                                  │
│   SG urban-alert-usuarios-tasks ──5432──► SG urban-alert-usuarios-rds                               │
│   RDS urban-alert-usuarios-db (PG17, privada, TLS) ── secreto rds!… (postgres, gestionado por RDS)  │
│   Secreto urban-alert-usuarios-db {username,password,host,port,dbname,jdbc_url}                     │
│   Logs /ecs/urban-alert-usuarios · /ecs/urban-alert-usuarios-migrate (30 días)                      │
│   Task definition urban-alert-usuarios-migrate (imagen :<sha>, perfil aws,migrate)                  │
│                                                                                                     │
│ paso: scripts/run-schema-task.sh → Flyway V1, V2 + afterMigrate (rol users_app) → exit 0            │
│                                                                                                     │
│ stack service: Express urban-alert-usuarios (:8080, /actuator/health, 1–5 tareas, perfil aws)        │
│   └ regla Host en el ALB interno compartido de Express                                              │
│ stack routes: 6 integraciones HTTP_PROXY por el VPC Link → listener 443 del ALB                      │
└─────────────────────────────────────────────────────────────────────────────────────────────────────┘
Cliente ─► API Gateway (plataforma) ─► GET /api/v1/usuarios[/{id}] (NONE) · POST/PUT/PATCH/DELETE (AWS_IAM)
```

**Componentes**

1. **Aplicación** (`src/main`):
   - `application-aws.yml` (nuevo): datasource desde `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`, Hikari `maximum-pool-size: ${DB_POOL_MAX}` / `minimum-idle: ${DB_POOL_MIN}`, `spring.flyway.enabled: false`, `ddl-auto: validate`, `logging.structured.format.console: ecs`, `server.shutdown: graceful`, `spring.lifecycle.timeout-per-shutdown-phase: 20s`, `springdoc.api-docs.enabled: false` y `swagger-ui.enabled: false`.
   - `application-migrate.yml` (nuevo): `spring.main.web-application-type: none`, Flyway activado con `locations: classpath:db/migration,classpath:db/cloud`, `placeholders.app_user`/`app_password` desde el entorno. La aplicación arranca, migra, valida con Hibernate y termina (sin hilos no-daemon) con código 0; cualquier error → código ≠ 0.
   - `db/cloud/afterMigrate.sql` (nuevo): crea el rol `${app_user}` si no existe (`DO $$ … $$`), `ALTER ROLE … PASSWORD`, `GRANT CONNECT`, `USAGE`, `SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public` y `ALTER DEFAULT PRIVILEGES` para tablas futuras. Solo se carga con el perfil `migrate` (fuera de `db/migration`, para que local y las pruebas no cambien).
   - `api/logging/RequestLoggingFilter.java` (nuevo): `OncePerRequestFilter` que registra por petición `method`, `path`, `status`, `duration_ms` como pares clave-valor (SLF4J fluent API, que el formato ECS de Boot 3.5 incluye en el JSON).
   - `build.gradle.kts`: versiones de §2.
   - `deploy/docker/Dockerfile`: usuario sin privilegios, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`, `ENTRYPOINT` en forma exec (ya lo está: Java es PID 1 y recibe `SIGTERM`).
2. **`infra/base`**, **`infra/service`**, **`infra/routes`**: §5 y §7.
3. **Scripts** (`scripts/`): `check-platform.sh`, `buscar-listener-express.sh` (versión corregida de Geoespacial), `run-schema-task.sh`, `diagnose-service.sh` copiados; `smoke-gateway-usuarios.sh` y `signed-request.sh` nuevos; pruebas en `scripts/tests/`.
4. **Workflows**: `provision.yml`, `deprovision.yml` (§7).
5. **Documentación**: `README.md` (despliegue, rutas, cómo firmar, variables, costes), `infra/INVENTARIO.md`, `infra/bootstrap/README.md` (qué se reutiliza del arranque y qué configurar en este repo).

## 5. Estructura de archivos

```text
build.gradle.kts                                  (mod.)  Boot 3.5.16, springdoc 2.8.17, dep-mgmt 1.1.7
deploy/docker/Dockerfile                          (mod.)  usuario no root, MaxRAMPercentage
.dockerignore                                     (nuevo) excluir infra/, .github/, .sdd/, specs/, build/
src/main/resources/application-aws.yml            (nuevo)
src/main/resources/application-migrate.yml        (nuevo)
src/main/resources/db/cloud/afterMigrate.sql      (nuevo)
src/main/java/com/urbanalert/users/api/logging/RequestLoggingFilter.java   (nuevo)
src/test/java/com/urbanalert/users/api/logging/RequestLoggingFilterTest.java (nuevo)
src/test/java/com/urbanalert/users/infrastructure/CloudMigrationIT.java    (nuevo)
src/test/java/com/urbanalert/users/AwsProfileConfigTest.java               (nuevo)
infra/
  INVENTARIO.md  bootstrap/README.md
  base/    versions.tf backend.tf providers.tf variables.tf terraform.tfvars
           network.tf ecr.tf iam.tf ecs.tf rds.tf secrets.tf logs.tf migrate_task.tf outputs.tf
           tests/base.tftest.hcl  .terraform.lock.hcl
  service/ versions.tf backend.tf providers.tf variables.tf terraform.tfvars main.tf outputs.tf
           tests/service.tftest.hcl  .terraform.lock.hcl
  routes/  versions.tf backend.tf providers.tf variables.tf terraform.tfvars main.tf outputs.tf
           tests/routes.tftest.hcl  .terraform.lock.hcl
scripts/
  check-platform.sh buscar-listener-express.sh run-schema-task.sh diagnose-service.sh   (copiados)
  smoke-gateway-usuarios.sh signed-request.sh                                          (nuevos)
  tests/test-check-platform.sh tests/test-buscar-listener.sh tests/test-smoke.sh
.github/workflows/provision.yml  deprovision.yml
README.md  docs/funcionamiento.md  CLAUDE.md      (mod.)
```

## 6. Modelo de datos

Sin cambios en la tabla `users` ni en V1/V2. Solo en la nube:

- **Rol `users_app`**: `LOGIN`; `CONNECT` sobre `urbanalert_users`, `USAGE` sobre `public`, `SELECT, INSERT, UPDATE, DELETE` sobre todas las tablas de `public` (D-007) y privilegios por defecto para las tablas que creen migraciones futuras. Sin `CREATE`, `ALTER` ni `DROP`.
- **Secreto `urban-alert-usuarios-db`** (JSON): `username`, `password` (`random_password`, 32 alfanuméricos), `host`, `port`, `dbname`, `jdbc_url = jdbc:postgresql://<host>:5432/urbanalert_users?sslmode=require`.
- **Secreto `rds!…`**: usuario maestro `postgres`, lo crea y rota RDS; solo lo lee la tarea de migración.

## 7. Contratos / interfaces

**API HTTP**: sin cambios en el servicio. En el gateway (RF-019):

| Ruta pública | Ruta del servicio (`overwrite:path`) | `authorization_type` |
|---|---|---|
| `GET /api/v1/usuarios` | `/api/v1/users` | `NONE` |
| `GET /api/v1/usuarios/{id}` | `/api/v1/users/$request.path.id` | `NONE` |
| `POST /api/v1/usuarios` | `/api/v1/users` | `AWS_IAM` |
| `PUT /api/v1/usuarios/{id}` | `/api/v1/users/$request.path.id` | `AWS_IAM` |
| `PATCH /api/v1/usuarios/{id}/role` | `/api/v1/users/$request.path.id/role` | `AWS_IAM` |
| `DELETE /api/v1/usuarios/{id}` | `/api/v1/users/$request.path.id` | `AWS_IAM` |

Cada integración como en Geoespacial: `HTTP_PROXY`, `VPC_LINK`, listener 443, payload 1.0, `tls_config.server_name_to_verify` y `overwrite:header.host` = endpoint de Express.

**Variables de entorno**

| Variable | Servicio (perfil `aws`) | Tarea de migración (perfil `aws,migrate`) |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `aws` | `aws,migrate` |
| `DB_URL` | secreto `urban-alert-usuarios-db:jdbc_url::` | igual |
| `DB_USERNAME` / `DB_PASSWORD` | secreto app `username` / `password` | secreto `rds!…` `username` / `password` |
| `DB_APP_USER` / `DB_APP_PASSWORD` | — | `users_app` / secreto app `password` (placeholders de Flyway) |
| `DB_POOL_MIN` / `DB_POOL_MAX` | `2` / `8` | `1` / `1` |

**Logs**: una línea JSON ECS por evento en stdout (`@timestamp`, `log.level`, `log.logger`, `message`, `service.name`, más `method`, `path`, `status`, `duration_ms` en la línea por petición).

**Terraform → workflows**: base → `name`, `ecr_repository_url`, `cluster_name`, `migrate_task_definition_arn`, `migrate_container_name`, `migrate_log_group`, `subnet_ids`, `tasks_security_group_id`; service → `service_endpoint`, `cluster_name`, `service_name`; routes → `gateway_url`, `route_keys`.

**`provision.yml`** (push a `main`; `concurrency: urban-alert-usuarios-aws`, sin cancelar)

| Job | Pasos |
|---|---|
| `test` | checkout → setup-java 21 + setup-gradle → `./gradlew test` → pruebas de scripts → Terraform `fmt -check`, `init -backend=false -lockfile=readonly`, `validate`, `test` de los 3 stacks → actionlint → shellcheck |
| `provision` (needs `test`) | OIDC → `check-platform.sh` → apply base (`image_tag=$SHA`) → login ECR → build `-f deploy/docker/Dockerfile --platform linux/amd64` + push (omitido si el tag existe) → `run-schema-task.sh` → apply service → (si falla) `diagnose-service.sh` → `buscar-listener-express.sh` → apply routes → `smoke-gateway-usuarios.sh` → resumen |

**`deprovision.yml`** (`workflow_dispatch`, input `confirm` = `urban-alert-usuarios`, solo `main`, misma `concurrency`): routes destroy (con valores de relleno) → service destroy → base apply `allow_destroy=true final_snapshot_suffix=<run_id>` → base destroy. Cada paso comprueba si su estado está vacío.

**`scripts/signed-request.sh <MÉTODO> <url> [cuerpo]`**: `curl --aws-sigv4 "aws:amz:us-east-2:execute-api"` con las credenciales del entorno (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`); para administradores y para la verificación (las escrituras no se pueden hacer desde el navegador).

**Variables y secretos de GitHub en `urban-alert-usuarios`**: secreto `AWS_ROLE_ARN` (mismo rol que Geoespacial) y variable `AWS_REGION=us-east-2`. Hoy no existen: los crea el usuario (T de arranque).

## 8. Decisiones

### D-001: Tres stacks (base, service, routes), como Geoespacial
- **Decisión**: estados `usuarios/base.tfstate`, `usuarios/service.tfstate`, `usuarios/routes.tfstate`; service lee base por `terraform_remote_state`; routes recibe endpoint y listener por variables.
- **Alternativas**: un stack con `-target`; rutas en service en dos fases.
- **Motivo**: la migración debe ir entre base y service (RF-014) y el listener solo existe tras desplegar el servicio (guía §3). Patrón ya probado en la cuenta.

### D-002: Flyway en una tarea ECS puntual con perfil `migrate`, no al arrancar el servicio
- **Decisión**: la task definition `urban-alert-usuarios-migrate` ejecuta la misma imagen con `SPRING_PROFILES_ACTIVE=aws,migrate` como usuario maestro; el servicio arranca con `spring.flyway.enabled=false` y `users_app`.
- **Alternativas**: Flyway al arrancar cada tarea (necesita DDL en el usuario de la app; varias tareas compiten por el bloqueo); imagen de Flyway CLI (otra imagen y otra copia de las migraciones).
- **Motivo**: RF-008 (una vez por versión, antes del tráfico) y RF-010 (app sin DDL). Reutiliza las migraciones de `db/migration` sin copiarlas y no añade imagen ni dependencias.

### D-003: Rol de la aplicación con un callback `afterMigrate` de Flyway
- **Decisión**: `db/cloud/afterMigrate.sql` con placeholders `${app_user}`/`${app_password}`, cargado solo por el perfil `migrate`. Idempotente: crea si falta, resincroniza la contraseña y vuelve a conceder en cada ejecución.
- **Alternativas**: un `ApplicationRunner` Java; un `V3` con el rol (las migraciones versionadas no se repiten y la contraseña cambiaría el checksum).
- **Motivo**: SQL explícito junto a las migraciones, sin código nuevo; los callbacks se ejecutan en cada `migrate`. La contraseña es alfanumérica (sin escapes) y Flyway no registra el contenido de los scripts.

### D-004: Roles IAM propios del servicio
- **Decisión**: `urban-alert-usuarios-ecs-execution` (con `AmazonECSTaskExecutionRolePolicy` + política en línea que lee solo `secret:urban-alert-usuarios-*` y el `rds!*` etiquetado con el ARN de `urban-alert-usuarios-db`) y `urban-alert-usuarios-ecs-infrastructure` (con `AmazonECSInfrastructureRoleforExpressGatewayServices`). Sin rol de tarea (la app no llama a AWS).
- **Alternativas**: reutilizar `urban-alert-ecs-execution-role`/`-infrastructure-role`.
- **Motivo**: esos roles los gestiona y destruye el estado de Geoespacial (P2, RF-012); su política de secretos es `urban-alert-*`, más amplia. Los nombres caben en las políticas del rol de GitHub (`role/urban-alert-*`, `iam:AttachRolePolicy` limitado a esas dos políticas gestionadas).

### D-005: Spring Boot 3.5.16 para logs estructurados nativos
- **Decisión**: subir de 3.3.4 a 3.5.16 (última 3.5.x en Maven Central el 2026-10-11) y usar `logging.structured.format.console: ecs` solo en el perfil `aws`; springdoc 2.8.17.
- **Alternativas**: `logstash-logback-encoder` (dependencia nueva) o un layout propio en `logback-spring.xml`.
- **Motivo**: RF-004 sin dependencias nuevas; dentro de Boot 3.x (sin cambio mayor); local mantiene logs en texto (RF-022). Riesgo de regresión cubierto por la suite existente (§9).

### D-006: Una línea por petición con un filtro propio
- **Decisión**: `RequestLoggingFilter` (`OncePerRequestFilter`, orden alto) con pares clave-valor; no se activa el access log de Tomcat.
- **Alternativas**: access log de Tomcat a stdout (texto, no estructurado); Micrometer/observability.
- **Motivo**: RF-004 con el formato ECS ya configurado; ~30 líneas.

### D-007: Permisos del rol de la aplicación
- **Decisión**: `SELECT, INSERT, UPDATE, DELETE` sobre las tablas de `public` y privilegios por defecto; nada de `CREATE` en el esquema (se revoca `CREATE ON SCHEMA public FROM PUBLIC`, ya por defecto en PG ≥ 15).
- **Motivo**: el CRUD necesita las cuatro operaciones (a diferencia de Geoespacial, que solo lee e inserta); sin DDL (RF-010). Hibernate `validate` solo lee el catálogo.

### D-008: TLS con `sslmode=require`
- **Decisión**: `jdbc_url` con `?sslmode=require`; RDS PG 17 tiene `rds.force_ssl=1` por defecto.
- **Alternativas**: `verify-full` con el bundle de CA de RDS en la imagen.
- **Motivo**: RF-002 sin certificados en la imagen (igual que Geoespacial D-008).

### D-009: Presupuesto de conexiones
- **Decisión**: `DB_POOL_MAX=8`, `DB_POOL_MIN=2`, `max_tasks=5` → 40; `precondition` en service `db_pool_max × max_tasks ≤ 50`. La tarea de migración usa 1.
- **Motivo**: RNF-002; `db.t4g.micro` admite ~80 conexiones, quedan ~40 para migración y administración.

### D-010: Rutas por método, sin comodín `{proxy+}`
- **Decisión**: las 6 rutas explícitas de §7.
- **Alternativas**: `GET /api/v1/usuarios/{proxy+}` + `ANY` con IAM.
- **Motivo**: RF-019 pide exactamente esas rutas y autorización por método; un comodín publicaría también rutas futuras sin revisarlas.

### D-011: Prueba de humo por el gateway y firma manual
- **Decisión**: `smoke-gateway-usuarios.sh <gateway>`: `GET /api/v1/usuarios` → 200 con un array que contiene `admin@urbanalert.com` (reintentos 5 min); `GET /api/v1/usuarios/018f4c2a-0000-7000-8000-000000000001` → 200; `GET /api/v1/usuarios/<uuid aleatorio>` → 404; `POST /api/v1/usuarios` y `DELETE /api/v1/usuarios/<uuid>` sin firma → 403; `GET /actuator/health` → 404. Las escrituras firmadas (MS-002 completo) se prueban en `/sdd-verify` con `signed-request.sh`.
- **Alternativas**: dar `execute-api:Invoke` al rol de GitHub para probar escrituras en cada despliegue.
- **Motivo**: el rol de los workflows no debe poder escribir datos de negocio; la prueba no modifica datos.

### D-012: Arquitectura, tamaño y JVM
- **Decisión**: imagen `linux/amd64` (Express usa X86_64); tareas 512 CPU / 1024 MiB; `-XX:MaxRAMPercentage=75`.
- **Motivo**: coste mínimo (RNF-006). Si el arranque de Spring en 0,5 vCPU no pasa la comprobación de salud, subir a 1024 CPU (riesgo §10).

### D-013: Tamaños y coste
- **Decisión**: RDS `db.t4g.micro` gp3 20 GiB Single-AZ (~15 USD/mes con almacenamiento), Fargate 1 tarea 0,5 vCPU/1 GiB (~18 USD/mes), logs 30 días, ECR 20 imágenes. El ALB interno de Express y el NAT ya existen y se comparten.
- **Motivo**: RNF-006, P5; tabla en el README.

### D-014: Clúster ECS propio
- **Decisión**: `aws_ecs_cluster "urban-alert-usuarios"`.
- **Motivo**: la tarea de migración corre antes que el servicio; no depender del clúster de Geoespacial (P2).

### D-015: Sin cambios de arranque; ECR nuevo
- **Decisión**: no se importa nada: ECR `urban-alert-usuarios` lo crea Terraform. T de arranque: el usuario comprueba que no existe y configura `AWS_ROLE_ARN`/`AWS_REGION` en el repo. Si un permiso falta en el primer apply, se corrige el JSON versionado en `urban-alert-geoespacial/infra/bootstrap/` (dueño de las políticas) y lo aplica un administrador.
- **Motivo**: P2; los permisos actuales cubren `urban-alert-*` (ECR, roles, logs, secretos, RDS, ECS, SSM, API Gateway, ELB describe).

### D-016: Desaprovisionamiento en orden inverso
- **Decisión**: routes → service → base (allow_destroy) → base destroy; copia final `urban-alert-usuarios-final-<run_id>`; ECR con `force_delete` solo con `allow_destroy`; secretos con `recovery_window_in_days = 0`.
- **Motivo**: RF-015; guía §9 de la plataforma.

### D-017: Swagger desactivado en la nube
- **Decisión**: `springdoc.api-docs.enabled=false` y `swagger-ui.enabled=false` en el perfil `aws`; `HomeController` queda sin efecto útil (no se publica `/`).
- **Motivo**: fuera de alcance publicarla y el ALB interno es alcanzable desde la VPC; menos superficie.

## 9. Estrategia de pruebas

| Nivel | Qué | Cómo |
|---|---|---|
| Regresión | Suite existente (dominio, servicio, `UserControllerIT`) tras subir a Boot 3.5 | `./gradlew test` (CI; local con Podman y socket Docker) |
| Unitario | `RequestLoggingFilter`: una entrada con `method`, `path`, `status`, `duration_ms`, también si la petición falla | `RequestLoggingFilterTest` con `MockHttpServletRequest` y un appender en memoria |
| Unitario | Perfil `aws`: `server.shutdown=graceful`, timeout 20 s, Flyway desactivado, formato `ecs`, springdoc desactivado | `AwsProfileConfigTest` (lee los YAML con `YamlPropertySourceLoader`) |
| Integración | Migración en la nube: Flyway con `db/migration`+`db/cloud` sobre PostgreSQL (Testcontainers) crea tabla, 8 usuarios y `users_app`; segunda ejecución sin errores ni cambios; `users_app` puede SELECT/INSERT/UPDATE/DELETE y no `CREATE TABLE`; cambio de contraseña sincronizado | `CloudMigrationIT` (API de Flyway + JDBC) |
| Terraform base | RDS no pública, cifrada, backups ≥ 7, `deletion_protection` true por defecto y false con `allow_destroy`; SG de RDS solo desde el SG de tareas; subredes y VPC de SSM; política de secretos acotada a `urban-alert-usuarios-*`; nombres de roles | `infra/base/tests/base.tftest.hcl` (`mock_provider`, `override_data`) |
| Terraform service | Precondición 8×5 pasa, 11×5 falla; subredes = todas las de base; perfil `aws`; puerto 8080 y `/actuator/health` | `infra/service/tests/service.tftest.hcl` |
| Terraform routes | Exactamente las 6 claves; `NONE` solo en los `GET`; ninguna `$default`; `VPC_LINK`, payload 1.0, Host y TLS = endpoint, `overwrite:path` correcto; validaciones de variables | `infra/routes/tests/routes.tftest.hcl` |
| Scripts | `check-platform` (faltan parámetros → falla), `buscar-listener` (copiado con sus pruebas), humo (stub de `curl`) | `scripts/tests/*.sh` |
| Workflows | Sintaxis y expresiones | `actionlint` |
| e2e | Humo por el gateway (D-011) | Último paso de `provision.yml` |
| Manual (`/sdd-verify`) | MS-002 con `signed-request.sh`; MS-003 (segundo Provision 0 cambios); MS-004; HU-2 y MS-005 (desaprovisionar Usuarios, Geoespacial sigue respondiendo; reaprovisionar); logs en CloudWatch; conexión a RDS desde fuera rechazada | `verify.md` |

## 10. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Subir a Boot 3.5 rompe algo (Hibernate 6.6, springdoc) | Pruebas en rojo | Tarea propia al principio, con la suite completa; si falla sin arreglo sencillo, plan B: quedarse en 3.3.4 con `logstash-logback-encoder` (revisar D-005) |
| Spring arranca lento con 0,5 vCPU y Express marca las tareas no saludables | Despliegue fallido | `diagnose-service.sh`; subir `cpu` a 1024 en `terraform.tfvars` (≈ +18 USD/mes) |
| Primer `apply` real solo en `main` (OIDC) | Errores de IAM tras el merge | Todo lo verificable en el job `test`; correcciones con commits pequeños; el PR se mergea cuando el usuario lo decida |
| Falta un permiso del rol de GitHub (p. ej. `iam:TagRole` en roles nuevos con etiquetas) | `AccessDenied` | La política de Terraform ya incluye `iam:TagRole`/`CreateRole` sobre `role/urban-alert-*`; si falta algo, se anota el JSON en Geoespacial y lo aplica un administrador |
| El ALB de Express no se comparte entre clústeres distintos | Express crea otro ALB (~16–20 USD/mes) | Comprobar en el primer despliegue (`describe-load-balancers`); documentar el coste real |
| Conflicto de prioridades en las reglas del listener compartido | Despliegue de Express falla | Lo gestiona Express; `diagnose-service.sh` muestra el evento |
| Placeholders de Flyway activos en V1/V2 | Sustitución inesperada | V1/V2 no contienen `${`; `CloudMigrationIT` lo cubre |
| El rol de los workflows o alguien sin permiso hace escrituras | 403 | Comportamiento esperado (RF-019); escrituras con `signed-request.sh` y credenciales con `execute-api:Invoke` |
| Desaprovisionar Geoespacial borra el ALB compartido | Express lo recrea en el siguiente despliegue de Usuarios | Cada servicio despliega sus reglas; documentar que tras desaprovisionar otro servicio puede hacer falta un Provision |
| Contraseña de la app en el estado de Terraform | Quien lea el bucket la ve | Bucket privado, cifrado, solo TLS (arranque) |
| Las IT con Testcontainers no corren en el Mac sin socket Docker | Solo se ven en CI | Ejecutar unitarias en local por Podman; `podman machine` expone socket compatible si hace falta (`DOCKER_HOST`) |

## 11. Trazabilidad

| Requisito | Componentes | Pruebas |
|---|---|---|
| RF-001 Config por entorno, secretos | `application-aws.yml`, `service/main.tf` (`secret`), `secrets.tf` | `service.tftest.hcl`; MS-004 |
| RF-002 TLS | `jdbc_url` `sslmode=require` (D-008) | `base.tftest.hcl` (jdbc_url); verify |
| RF-003 Pool | `DB_POOL_*`, precondición (D-009) | `service.tftest.hcl` |
| RF-004 Logs | Boot 3.5 `ecs` (D-005), `RequestLoggingFilter` (D-006) | `RequestLoggingFilterTest`, `AwsProfileConfigTest`; verify en CloudWatch |
| RF-005 Apagado | `server.shutdown=graceful`, 20 s, `ENTRYPOINT` exec | `AwsProfileConfigTest`; verify (despliegue sin errores 5xx) |
| RF-006 Imagen | `Dockerfile` (no root, 8080), `/actuator/health`, amd64 | Despliegue Express saludable; revisión |
| RF-007 Tag = commit | `provision.yml`, ECR `IMMUTABLE` | Verify: imagen del servicio = SHA |
| RF-008 Esquema | Tarea `migrate` (D-002), `run-schema-task.sh` | `CloudMigrationIT` (dos ejecuciones) |
| RF-009 Semilla anonimizada | `V2__seed_users.sql` (ya hecho) | `CloudMigrationIT` (8 usuarios, 0 duplicados) |
| RF-010 Usuario sin DDL | `afterMigrate.sql` (D-003, D-007) | `CloudMigrationIT` (privilegios) |
| RF-011 IaC | `infra/{base,service,routes}` | `terraform validate/test`; MS-003 |
| RF-012 Sin recursos ajenos | Roles propios (D-004), clúster propio (D-014) | `base.tftest.hcl` (nombres); revisión de `INVENTARIO.md` |
| RF-013 Estado remoto | `backend.tf` x3 | Verify: objetos en `usuarios/` |
| RF-014 Orden de Provision | `provision.yml` | Ejecución en `main` |
| RF-015 Deprovision | `deprovision.yml`, `allow_destroy` (D-016) | `base.tftest.hcl`; verify HU-2 |
| RF-016 OIDC | `configure-aws-credentials` | Revisión: sin claves |
| RF-017 Concurrencia | `concurrency` | Revisión |
| RF-018 Red privada | `base/network.tf` (SSM), `run-schema-task.sh` (`assignPublicIp=DISABLED`) | `base.tftest.hcl`; verify: ALB interno, sin IP pública |
| RF-019 Rutas | `infra/routes` (D-010) | `routes.tftest.hcl`; humo (D-011); verify firmado |
| RF-020 Sin plataforma | `check-platform.sh` | `test-check-platform.sh` |
| RF-021 Documentación | `README.md`, `INVENTARIO.md`, `bootstrap/README.md` | Verify HU-6 |
| RF-022 Local sin cambios | Perfiles `aws`/`migrate` y `db/cloud` aislados; `local`/`docker` intactos | Suite completa; `docker compose up` |
| RNF-001 Seguridad | D-004, D-007, D-017, SG | `base.tftest.hcl`; MS-004 |
| RNF-002 Conexiones | D-009 | `service.tftest.hcl` |
| RNF-003 Idempotencia | D-001, D-003 | MS-003 |
| RNF-004 Sin caída | Express rolling, apagado ordenado | Verify: `curl` en bucle durante un despliegue |
| RNF-005 < 20 min | Caché de Gradle y de capas, omitir push si el tag existe | Duración en verify |
| RNF-006 Coste | D-012, D-013 | Revisión de `terraform.tfvars` y README |
| RNF-007 Backups | `rds.tf` | `base.tftest.hcl` |
