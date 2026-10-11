# Estado SDD

<!-- Puntero de reanudación. Claude lo actualiza en CADA transición (inicio/fin de tarea, cambio de fase, decisión, bloqueo).
     Editar este archivo = checkpoint (lo registra el hook). Mantenlo corto: el detalle va en specs/ y journal.md.
     fase: constitution | specify | clarify | plan | tasks | implement | verify | done
     estado_tarea: - | en_progreso | hecha | bloqueada -->

feature_activa: 001-migracion-aws
fase: implement
tarea_actual: T018
estado_tarea: hecha

## Siguiente paso

T018: stack infra/routes (6 rutas: 2 GET NONE, 4 escrituras AWS_IAM) con pruebas y lock.

## Contexto de trabajo

- Referencias: `../Plataforma compartida AWS` (README, docs/integracion.md, infra/ejemplo-consumidor) y `../Microservicio geoespacial AWS` (specs/002-migracion-aws, infra/{base,service,routes}, workflows, scripts).
- Los hooks del arnés usan `powershell`, que no está instalado en este Mac: no se ejecutan; el protocolo se sigue a mano.
- Geoespacial gestiona en su estado los roles compartidos `urban-alert-ecs-execution-role` / `-infrastructure-role`: Usuarios NO debe reutilizarlos ni gestionarlos (P2); crea los suyos.
- Rama `aws-provisioning` (PR a main al terminar). Semilla V2 anonimizada (8 usuarios, incl. test-user) en la nube; lecturas públicas y escrituras con AWS_IAM.
- Sin JDK local: Gradle por Podman con `eclipse-temurin:21-jdk` y caché en `~/.cache/gradle-podman`. Para las IT (Testcontainers): `podman run --rm --network host --security-opt label=disable -v /run/user/501/podman/podman.sock:/var/run/docker.sock -e TESTCONTAINERS_RYUK_DISABLED=true -e TESTCONTAINERS_HOST_OVERRIDE=localhost ...`; no borrar build/test-results durante la ejecución.

- Versiones (Maven Central 2026-10-11): Spring Boot 3.5.16, springdoc 2.8.17, dependency-management 1.1.7.
- Repo GitHub sin secretos/variables de Actions: falta AWS_ROLE_ARN y AWS_REGION (acción del usuario).

- Pendiente de comunicar: GlobalExceptionHandler devuelve 500 para rutas inexistentes/405 (bug previo, fuera de alcance). Y: el rol de ejecución de Geoespacial lee secret:urban-alert-*, incluido el de Usuarios (acotarlo en su repo).

- Terraform local: wrapper `<scratchpad>/tf` (Podman, hashicorp/terraform:1.16.5, caché de plugins en ~/.cache/tf-plugins).

## Bloqueos / preguntas abiertas

- ninguno
