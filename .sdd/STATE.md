# Estado SDD

<!-- Puntero de reanudación. Claude lo actualiza en CADA transición (inicio/fin de tarea, cambio de fase, decisión, bloqueo).
     Editar este archivo = checkpoint (lo registra el hook). Mantenlo corto: el detalle va en specs/ y journal.md.
     fase: constitution | specify | clarify | plan | tasks | implement | verify | done
     estado_tarea: - | en_progreso | hecha | bloqueada -->

feature_activa: 001-migracion-aws
fase: clarify
tarea_actual: -
estado_tarea: -

## Siguiente paso

Spec aclarada: el usuario debe revisarla y aprobarla (Estado: aprobada) antes de /sdd-plan.

## Contexto de trabajo

- Referencias: `../Plataforma compartida AWS` (README, docs/integracion.md, infra/ejemplo-consumidor) y `../Microservicio geoespacial AWS` (specs/002-migracion-aws, infra/{base,service,routes}, workflows, scripts).
- Los hooks del arnés usan `powershell`, que no está instalado en este Mac: no se ejecutan; el protocolo se sigue a mano.
- Geoespacial gestiona en su estado los roles compartidos `urban-alert-ecs-execution-role` / `-infrastructure-role`: Usuarios NO debe reutilizarlos ni gestionarlos (P2); crea los suyos.
- Rama `aws-provisioning` (PR a main al terminar). Semilla V2 completa en la nube; lecturas públicas y escrituras con AWS_IAM.
- V2__seed_users.sql siembra nombres/emails reales y corre con cualquier perfil con Flyway (docs/funcionamiento.md).

## Bloqueos / preguntas abiertas

- Esperando aprobación de la spec por el usuario.
