# Recursos de arranque

Terraform no gestiona estos recursos: los necesita para poder ejecutarse, y el desaprovisionamiento de este repo no debe tocarlos (constitución P2). Usuarios **no crea ninguno**: reutiliza los que ya existen y que otros repos documentan y versionan.

Cuenta `348364742283`, región `us-east-2`.

## 1. Lo que se reutiliza

| Recurso | ID en AWS | Dónde está documentado y versionado |
|---|---|---|
| Proveedor OIDC de GitHub | `arn:aws:iam::348364742283:oidc-provider/token.actions.githubusercontent.com` | [urban-alert-geoespacial/infra/bootstrap](https://github.com/Unal-Team-C/urban-alert-geoespacial/tree/main/infra/bootstrap) |
| Rol de los workflows (`github-actions-…`) | ARN en el secreto `AWS_ROLE_ARN` | Confianza: `github-oidc-trust.json` de Geoespacial. Admite `repo:Unal-Team-C@335834608/*:ref:refs/heads/main`, así que cubre este repo sin cambios |
| Política `github-actions-urban-alertPolicy` | `arn:aws:iam::348364742283:policy/github-actions-urban-alertPolicy` | `github-deploy-policy.json` de Geoespacial: ECR, ECS Express, `iam:PassRole` sobre `role/urban-alert-*`, RDS, red, secretos `urban-alert-*` |
| Política `github-actions-urban-alert-terraform` | `arn:aws:iam::348364742283:policy/github-actions-urban-alert-terraform` | `github-terraform-policy.json` de Geoespacial: roles `urban-alert-*`, logs `/ecs/urban-alert-*`, secretos `rds!*`, tareas ECS, KMS vía RDS |
| Política `github-actions-urban-alert-platform` | `arn:aws:iam::348364742283:policy/github-actions-urban-alert-platform` | [urban-alert-plataforma/infra/bootstrap](https://github.com/Unal-Team-C/urban-alert-plataforma/tree/main/infra/bootstrap): API Gateway `/apis/*`, SSM `/urban-alert/*`, `elasticloadbalancing:Describe*` |
| Bucket del estado | `urban-alert-tfstate-348364742283` | Geoespacial. Este repo usa las claves `usuarios/base.tfstate`, `usuarios/service.tfstate` y `usuarios/routes.tfstate` |

Todos los recursos de este servicio llevan el prefijo `urban-alert-usuarios`, que cae dentro de los permisos `urban-alert-*` de esas políticas. Si el primer aprovisionamiento falla con `AccessDenied`, el mensaje indica la acción: se añade al JSON correspondiente **en el repo que lo versiona** y lo aplica un administrador de la cuenta (`aws iam create-policy-version --set-as-default …`). Este repo no modifica políticas.

## 2. Lo que hay que configurar en este repo (una vez)

Settings → Secrets and variables → Actions de `Unal-Team-C/urban-alert-usuarios`:

| Tipo | Nombre | Valor de ejemplo |
|---|---|---|
| Secreto | `AWS_ROLE_ARN` | `arn:aws:iam::348364742283:role/github-actions-<nombre>` (el mismo que usa Geoespacial) |
| Variable | `AWS_REGION` | `us-east-2` |

```bash
gh secret set AWS_ROLE_ARN --repo Unal-Team-C/urban-alert-usuarios      # pega el ARN cuando lo pida
gh variable set AWS_REGION --repo Unal-Team-C/urban-alert-usuarios --body us-east-2
```

El resto de la configuración (nombres, tamaños, bucket) está en `infra/*/terraform.tfvars` y `backend.tf`.

## 3. Requisitos previos en la cuenta

- La plataforma compartida aprovisionada (workflow Provision de `urban-alert-plataforma`). Si no, Provision falla al principio con «La plataforma compartida no está aprovisionada».
- No debe existir un repositorio ECR `urban-alert-usuarios` creado a mano: lo crea Terraform. Si existiera, hay que importarlo (anotarlo en [../INVENTARIO.md](../INVENTARIO.md)) o borrarlo antes del primer Provision.

```bash
aws ecr describe-repositories --repository-names urban-alert-usuarios --region us-east-2   # debe responder RepositoryNotFoundException
```
