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
