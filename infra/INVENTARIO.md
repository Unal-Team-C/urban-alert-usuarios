# Inventario de recursos de AWS

Cuenta `348364742283`, región `us-east-2`. Spec 001-migracion-aws (RF-011, RF-012, RF-021).

## 1. Recursos creados por Terraform

Ninguno se importó: no existían antes del código. Los crea y destruye Terraform desde los workflows. Todos llevan el prefijo `urban-alert-usuarios` y las etiquetas `Project=urban-alert`, `Service=usuarios`, `ManagedBy=terraform`, `Stack=<stack>`.

| Stack (estado) | Recursos |
|---|---|
| `base` (`usuarios/base.tfstate`) | Repositorio ECR `urban-alert-usuarios` y su regla de ciclo de vida; roles IAM `urban-alert-usuarios-ecs-execution` (con `AmazonECSTaskExecutionRolePolicy` y la política en línea `read-usuarios-secrets`) y `urban-alert-usuarios-ecs-infrastructure` (con `AmazonECSInfrastructureRoleforExpressGatewayServices`); en la VPC compartida, grupos de seguridad `urban-alert-usuarios-tasks` y `urban-alert-usuarios-rds`; subnet group y RDS `urban-alert-usuarios-db` (con su secreto `rds!…` gestionado por RDS); secreto `urban-alert-usuarios-db`; clúster ECS `urban-alert-usuarios`; grupos de logs `/ecs/urban-alert-usuarios` y `/ecs/urban-alert-usuarios-migrate`; task definition `urban-alert-usuarios-migrate` |
| `service` (`usuarios/service.tfstate`) | Servicio ECS Express `urban-alert-usuarios` (con su regla en el balanceador **interno** de Express, certificado y escalado, que crea ECS con el rol de infraestructura) |
| `routes` (`usuarios/routes.tfstate`) | En el API Gateway compartido: 6 integraciones HTTP_PROXY por el VPC Link de la plataforma y 6 rutas: `GET /api/v1/usuarios`, `GET /api/v1/usuarios/{id}` (públicas); `POST /api/v1/usuarios`, `PUT /api/v1/usuarios/{id}`, `PATCH /api/v1/usuarios/{id}/role`, `DELETE /api/v1/usuarios/{id}` (`AWS_IAM`) |

Fecha del primer aprovisionamiento: _pendiente_ (se anota en la tarea T027).

Al desaprovisionar queda la copia final de la base `urban-alert-usuarios-final-<run_id>` (no la gestiona Terraform; borrarla a mano cuando ya no se necesite).

## 2. Recursos externos de arranque (no gestionados aquí)

Los usa este repo; no aparecen en su estado y el desaprovisionamiento no los toca. Detalle: [bootstrap/README.md](bootstrap/README.md).

| Recurso | ID en AWS |
|---|---|
| Proveedor OIDC de GitHub | `arn:aws:iam::348364742283:oidc-provider/token.actions.githubusercontent.com` |
| Rol de los workflows | ARN en el secreto `AWS_ROLE_ARN` de GitHub (`github-actions-…`) |
| Políticas del rol | `github-actions-urban-alertPolicy`, `github-actions-urban-alert-terraform`, `github-actions-urban-alert-platform` |
| Bucket del estado | `urban-alert-tfstate-348364742283` |

## 3. Recursos de otros repos (no gestionados aquí)

| Recurso | Dueño | Cómo lo usa Usuarios |
|---|---|---|
| VPC, subredes privadas, NAT, endpoint S3 | `urban-alert-plataforma` | Lee `vpc-id`, `vpc-cidr` y `subredes-privadas` de SSM `/urban-alert/plataforma/` |
| API Gateway (HTTP API), stage, VPC Link | `urban-alert-plataforma` | Lee `api-id`, `api-endpoint`, `vpc-link-id`; registra solo sus rutas bajo `/api/v1/usuarios` |
| Balanceador interno de ECS Express | ECS Express (compartido por los servicios Express de la VPC) | Express añade la regla por cabecera `Host` de Usuarios |
| Roles `urban-alert-ecs-execution-role` y `urban-alert-ecs-infrastructure-role` | `urban-alert-geoespacial` | **No los usa** (tiene roles propios) |
| RDS, clúster, ECR y grupos de seguridad de Geoespacial | `urban-alert-geoespacial` | No los usa |
