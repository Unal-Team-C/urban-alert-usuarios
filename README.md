# UrbanAlert.Usuarios

Microservicio Java/Spring Boot, fuente de verdad de la identidad de usuarios de UrbanAlert (perfil, email y rol). No maneja autenticación ni sesiones — eso lo resuelve el Identity Provider externo; este servicio expone el CRUD de cuentas que consumen los demás microservicios (p. ej. Reportes para atribuir `userId`, el API Gateway para decisiones de autorización por rol).

Se ejecuta en local (H2 o Docker Compose) y se despliega en AWS (ECS Express Mode + RDS PostgreSQL propio, expuesto solo por el API Gateway compartido) con GitHub Actions y Terraform: ver [Despliegue en AWS](#despliegue-en-aws). Spec, plan y tareas de la migración (desarrollada con SDD): [specs/001-migracion-aws/](specs/001-migracion-aws/).

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
specs/                Especificación funcional y técnica del servicio; specs/001-migracion-aws (SDD).
infra/                Terraform de AWS: base, service, routes; bootstrap/ e INVENTARIO.md.
scripts/              Scripts de los workflows (migración, listener, humo, firma SigV4) y sus pruebas.
.github/workflows/    Provision (push a main) y Deprovision (manual).
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

## Despliegue en AWS

Cuenta `348364742283`, región `us-east-2`. Toda la infraestructura es Terraform y solo la aplican los workflows de GitHub Actions desde `main` (constitución P1). El servicio vive en la red privada de la plataforma compartida [`urban-alert-plataforma`](https://github.com/Unal-Team-C/urban-alert-plataforma) y sigue el mismo patrón que [`urban-alert-geoespacial`](https://github.com/Unal-Team-C/urban-alert-geoespacial).

```text
Cliente ─HTTPS─► API Gateway compartido ─VPC Link─► ALB interno de ECS Express (regla Host) ─► tareas Fargate (perfil aws)
                 /api/v1/usuarios/*                                                            │ TLS, usuario users_app
                                                                                               ▼
                                                        RDS PostgreSQL 17 urban-alert-usuarios-db (propia, privada)
```

| Pieza | Dónde |
|---|---|
| ECR, roles propios (`urban-alert-usuarios-ecs-*`), grupos de seguridad, RDS, secreto, logs, clúster, tarea de migración | [`infra/base`](infra/base) (estado `usuarios/base.tfstate`) |
| Servicio ECS Express (1–5 tareas, 0,5 vCPU / 1 GiB, `/actuator/health`) | [`infra/service`](infra/service) |
| Rutas en el API Gateway compartido | [`infra/routes`](infra/routes) |
| Inventario de recursos (propios, de arranque y ajenos) | [`infra/INVENTARIO.md`](infra/INVENTARIO.md) |

### Perfiles de Spring en la nube

- **`aws`** (servicio): credenciales de Secrets Manager (`DB_URL` con `sslmode=require`, `DB_USERNAME`, `DB_PASSWORD`), pool `DB_POOL_MIN`/`DB_POOL_MAX` (2/8; 8 × 5 tareas = 40 ≤ 50 conexiones), Flyway desactivado, logs JSON (formato ECS) con una línea por petición (`method`, `path`, `status`, `duration_ms`), apagado ordenado de 20 s, Swagger desactivado.
- **`aws,migrate`** (tarea puntual antes de cada despliegue): sin servidor web; como usuario maestro de RDS aplica las migraciones de Flyway (`db/migration`, incluida la semilla anonimizada) y el callback [`db/cloud/afterMigrate.sql`](src/main/resources/db/cloud/afterMigrate.sql), que crea o actualiza el rol `users_app` (lectura y escritura de datos, sin DDL). Termina con código 0 o falla el despliegue.

### Rutas en el API Gateway

URL base: la salida `gateway_url` (también en el resumen de cada ejecución de Provision), p. ej. `https://<api-id>.execute-api.us-east-2.amazonaws.com`.

| Ruta pública | Ruta del servicio | Acceso |
|---|---|---|
| `GET /api/v1/usuarios` | `GET /api/v1/users` | Público |
| `GET /api/v1/usuarios/{id}` | `GET /api/v1/users/{id}` | Público |
| `POST /api/v1/usuarios` | `POST /api/v1/users` | Firma IAM (SigV4) |
| `PUT /api/v1/usuarios/{id}` | `PUT /api/v1/users/{id}` | Firma IAM (SigV4) |
| `PATCH /api/v1/usuarios/{id}/role` | `PATCH /api/v1/users/{id}/role` | Firma IAM (SigV4) |
| `DELETE /api/v1/usuarios/{id}` | `DELETE /api/v1/users/{id}` | Firma IAM (SigV4) |

Cualquier otra ruta (salud, Swagger, `/api/v1/users`) responde 404 en el gateway. Sin firma, o con una identidad sin `execute-api:Invoke`, las escrituras responden **403** y no llegan al servicio. CORS lo gestiona el gateway.

**Escrituras firmadas.** Con credenciales de AWS que permitan `execute-api:Invoke` (los administradores de la cuenta las tienen):

```bash
eval "$(aws configure export-credentials --format env)"
GATEWAY=https://<api-id>.execute-api.us-east-2.amazonaws.com
scripts/signed-request.sh POST "$GATEWAY/api/v1/usuarios" '{"name":"Ana Torres","email":"ana@example.com","role":"USER"}'
scripts/signed-request.sh PATCH "$GATEWAY/api/v1/usuarios/<id>/role" '{"role":"ADMIN"}'
scripts/signed-request.sh DELETE "$GATEWAY/api/v1/usuarios/<id>"
```

Un microservicio que deba escribir usuarios necesita, en su rol de tarea, `execute-api:Invoke` sobre `<api-execution-arn>/$default/<MÉTODO>/api/v1/usuarios[...]` (parámetro SSM `/urban-alert/plataforma/api-execution-arn`) y firmar con SigV4 (servicio `execute-api`, `us-east-2`). El frontend, desde el navegador, solo puede leer.

### Antes del primer despliegue (una sola vez)

Detalle en [infra/bootstrap/README.md](infra/bootstrap/README.md). En **Settings → Secrets and variables → Actions** de este repo:

| Tipo | Nombre | Ejemplo |
|---|---|---|
| Secreto | `AWS_ROLE_ARN` | `arn:aws:iam::348364742283:role/github-actions-<nombre>` (el mismo rol que usa Geoespacial) |
| Variable | `AWS_REGION` | `us-east-2` |

La plataforma compartida debe estar aprovisionada. No hace falta crear nada más a mano.

### Workflows

| Workflow | Cuándo | Qué hace |
|---|---|---|
| [Provision](.github/workflows/provision.yml) | Cada push a `main` | Pruebas (Gradle, scripts, Terraform, actionlint, shellcheck) → comprobar la plataforma → `base` → imagen `:<sha>` → tarea de migración → `service` → listener del ALB → `routes` → [prueba de humo](scripts/smoke-gateway-usuarios.sh) por el gateway (solo lectura) |
| [Deprovision](.github/workflows/deprovision.yml) | Solo a mano | Exige escribir `urban-alert-usuarios` → `routes` → `service` → `base` (con copia final `urban-alert-usuarios-final-<run_id>`) |

Comparten el grupo de `concurrency` `urban-alert-usuarios-aws`: nunca se ejecutan a la vez. Desaprovisionar Usuarios no toca la plataforma ni otros servicios; para desaprovisionar la plataforma, antes hay que desaprovisionar cada microservicio.

### Costes

Estimación mensual en `us-east-2` con el servicio encendido y sin tráfico (730 h). No incluye la plataforma compartida (NAT ~36,6 USD) ni el ALB interno de Express (~16–20 USD), que comparten todos los servicios.

| Recurso | Mes |
|---|---|
| RDS `db.t4g.micro` Single-AZ + 20 GiB gp3 | ~14 USD |
| Fargate, 1 tarea de 0,5 vCPU / 1 GiB (+ la tarea de migración, segundos por despliegue) | ~18 USD |
| Secrets Manager (1 secreto propio; el de RDS lo gestiona RDS) | ~0,4 USD |
| ECR (≤ 20 imágenes), CloudWatch Logs (30 días) | < 1 USD |
| **Total** | **~33 USD** |

Para no pagar cuando no se usa: workflow Deprovision (queda la copia final de la base, que cobra almacenamiento de snapshot hasta que se borre).

### Validar en local (sin credenciales)

Con Podman (`podman machine start`), desde la raíz:

```bash
podman run --rm -v "$PWD":/w -w /w docker.io/hashicorp/terraform:1.16.5 -chdir=infra/base init -backend=false   # y validate / test; igual con service y routes
podman run --rm -v "$PWD":/w -w /w docker.io/hashicorp/terraform:1.16.5 fmt -check -recursive infra
bash scripts/tests/test-check-platform.sh && bash scripts/tests/test-buscar-listener.sh && bash scripts/tests/test-smoke.sh
podman run --rm -v "$PWD":/repo -w /repo docker.io/rhysd/actionlint:1.7.7
podman run --rm -v "$PWD":/mnt -w /mnt docker.io/koalaman/shellcheck:v0.10.0 scripts/*.sh scripts/tests/*.sh
```

## Desarrollo con SDD

Se desarrolla con un arnés de desarrollo guiado por especificaciones con persistencia en disco: protocolo en [.sdd/PROTOCOL.md](.sdd/PROTOCOL.md), principios en [.sdd/constitution.md](.sdd/constitution.md), features en `specs/NNN-nombre/`.
