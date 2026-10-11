# Notas: 001-migracion-aws

## Indicaciones técnicas de la fuente

Para `/sdd-plan` (no son requisitos):

- Patrón a copiar de `urban-alert-geoespacial` (spec 002): stacks `infra/base` (ECR, roles, SG, RDS, secretos, logs, clúster, task def puntual), `infra/service` (`aws_ecs_express_gateway_service`) e `infra/routes` (integraciones HTTP_PROXY por VPC Link, `overwrite:header.host` + `server_name_to_verify` = endpoint, payload 1.0, `overwrite:path`); estado en `s3://urban-alert-tfstate-348364742283/usuarios/{base,service,routes}.tfstate`.
- Scripts reutilizables: `check-platform.sh`, `buscar-listener-express.sh` (versión corregida de geoespacial, con búsqueda por regla host-header), `run-schema-task.sh`, `diagnose-service.sh`, humo por el gateway.
- Roles: NO reutilizar `urban-alert-ecs-execution-role` / `urban-alert-ecs-infrastructure-role` (los gestiona y destruye el estado de Geoespacial). Crear `urban-alert-usuarios-ecs-execution` / `urban-alert-usuarios-ecs-infrastructure` (prefijo `urban-alert-` para que la política de Terraform y `iam:PassRole` los cubran). Política de secretos acotada a `urban-alert-usuarios-*` (Geoespacial usa `urban-alert-*`, más amplio).
- Grupo de seguridad de tareas con entrada al puerto del contenedor desde el CIDR de la VPC (Express usa el SG pasado en lugar del suyo; lección de Geoespacial T037).
- Spring Boot: puerto 8080, salud `/actuator/health` (incluye la BD por defecto). `server.shutdown=graceful` + `spring.lifecycle.timeout-per-shutdown-phase` < 30 s. Logs estructurados: Spring Boot ≥ 3.4 trae `logging.structured.format.console` (ecs/logstash); el proyecto está en 3.3.4. Hikari `maximum-pool-size`. JDBC `?sslmode=require`.
- Migraciones: Flyway en el arranque con varias tareas vs. tarea puntual con usuario administrador (Flyway usa bloqueo consultivo en PostgreSQL, pero RF-010 pide que la app no tenga DDL → tarea puntual con `spring.flyway` como admin, o `flyway` CLI). `V2__seed_users.sql` está en `db/migration` y corre con cualquier perfil.
- Imagen: el Dockerfile actual (`deploy/docker/Dockerfile`, temurin 21 alpine) corre como root; añadir usuario sin privilegios. Construir `linux/amd64`.
- Rutas gateway: `ANY /api/v1/usuarios` y `ANY /api/v1/usuarios/{proxy+}` (dentro del prefijo, guía §4) o rutas por método. `HomeController` redirige `/` a Swagger: no se publica.
- CORS lo gestiona el gateway; el servicio no lo configura hoy.
