# Especificación: Migración del Servicio de Usuarios a AWS

- **ID**: 001-migracion-aws
- **Estado**: aclarada <!-- borrador | aclarada | aprobada | implementada -->
- **Creada**: 2026-10-11
- **Entrada original**: "Ok now we must migrate the service to aws, follow the README in urban-alert/infrastructure. This service must be migrated to an ECS express mode and database must have its own postgres instance. Multimedia, geoespacial and the network itself (check the urban-alert-plataforma repo in the aws migration folder) to understand how it's deployed." Fuentes en `fuentes/`: README de `UrbanAlert/infrastructure` (rutas y aislamiento locales), README y guía de integración de `urban-alert-plataforma`. Referencia de práctica: `urban-alert-geoespacial`, spec 002-migracion-aws.

## 1. Resumen

Llevar el Servicio de Usuarios, que hoy solo corre en contenedores locales, a AWS: servicio de contenedores en modo Express con escalado automático y una base PostgreSQL gestionada **propia del servicio**, ambos en la red privada compartida de UrbanAlert y publicados **solo** a través del API Gateway compartido, con las mismas rutas que el gateway local. Toda la infraestructura queda como código y se aprovisiona con un flujo de GitHub Actions en cada commit a `main`; otro flujo, manual, la elimina. El contrato de la API no cambia.

## 2. Problema y contexto

- El servicio solo se puede usar en la máquina de cada integrante (`docker compose`); los demás servicios (Reportes valida `userId` contra él) y el frontend no tienen una instancia compartida.
- En local, el gateway Nginx publica `/api/v1/usuarios/*` → `/api/v1/users/*` y la red `data-usuarios` aísla su base (`fuentes/UrbanAlert-infrastructure-README.md`). En AWS, ese papel lo cumplen el API Gateway y la red privada de la plataforma compartida `Unal-Team-C/urban-alert-plataforma` (ya aprovisionada), que publica su contrato en SSM (`/urban-alert/plataforma/`) y una guía de integración (`fuentes/plataforma-integracion.md`).
- Geoespacial ya se desplegó así (spec 002 de su repo), y su patrón está probado en la cuenta: es la referencia.
- La configuración actual es de desarrollo: credenciales por defecto en el compose, conexión sin cifrar, logs en texto y una migración (`V2__seed_users.sql`) que siembra nombres y correos reales de personas en cualquier entorno con base PostgreSQL.

## 3. Usuarios y actores

- **Equipo de desarrollo**: publicar cambios del servicio y de su infraestructura con un commit a `main` y eliminar el entorno a mano cuando no se usa.
- **Frontend / portal de administración**: gestionar usuarios a través de la URL del API Gateway.
- **Servicio de Reportes**: consultar un usuario por id a través del API Gateway (lectura pública).
- **Identidad de AWS autorizada** (otro microservicio o un administrador con credenciales): crear, actualizar, cambiar el rol y borrar usuarios con peticiones firmadas.
- **Revisor / docente**: entender desde el repositorio qué infraestructura existe y cómo se despliega.

## 4. Historias de usuario

### HU-1: Aprovisionar y desplegar con un commit a `main` (Prioridad: P1)

Como integrante del equipo quiero que un commit a `main` aprovisione la infraestructura y deje desplegada la versión de ese commit, sin pasos manuales.

**Criterios de aceptación**
1. Dado un commit a `main`, cuando termina el flujo de aprovisionamiento, entonces el servicio responde con la versión de ese commit a través del API Gateway y su comprobación de salud interna es saludable.
2. Dado un commit a `main` que hace fallar las pruebas, cuando se ejecuta el flujo, entonces no se modifica la infraestructura ni se despliega nada, y el flujo queda en rojo.
3. Dado un commit sin cambios de infraestructura, cuando se ejecuta el flujo, entonces el aprovisionamiento informa de que no hay cambios.
4. Dados dos commits seguidos, cuando sus flujos coinciden, entonces se ejecutan uno detrás de otro.

### HU-2: Desaprovisionar el entorno a mano (Prioridad: P1)

Como integrante del equipo quiero lanzar a mano un flujo que elimine el entorno del servicio, para no pagar cuando no se usa y poder recrearlo con un commit.

**Criterios de aceptación**
1. Dado el repositorio en GitHub, cuando un integrante lanza el flujo y confirma escribiendo el nombre del entorno, entonces se eliminan primero las rutas del servicio en el gateway y después todos los recursos del servicio gestionados como código.
2. Dado que se lanza sin la confirmación exacta, entonces no elimina nada y lo indica.
3. Dada la base con datos, cuando se desaprovisiona, entonces antes de eliminarla queda una copia final restaurable.
4. Dado el entorno desaprovisionado, cuando se hace un commit a `main`, entonces se recrea completo y el servicio vuelve a estar saludable.
5. Desaprovisionar este servicio no toca la plataforma compartida, los recursos de arranque ni los recursos de otros microservicios (Geoespacial sigue respondiendo).
6. El flujo nunca se ejecuta por un commit.

### HU-3: Base PostgreSQL propia del servicio (Prioridad: P1)

Como equipo queremos que Usuarios tenga su propia instancia de PostgreSQL gestionada, separada de las de otros servicios, para aislar sus datos como en local.

**Criterios de aceptación**
1. Dado el entorno aprovisionado, cuando se listan las bases de datos de la cuenta, entonces Usuarios tiene una instancia propia que no comparte con ningún otro servicio.
2. Dado el entorno recién aprovisionado, cuando se consulta la base, entonces existe la tabla de usuarios con su índice único de correo, y los datos iniciales son exactamente los acordados en la Clarificación 1.
3. Dado el entorno aprovisionado, cuando se intenta conectar a la base desde internet o desde tareas de otro servicio, entonces no es posible; solo llegan las tareas de Usuarios.
4. Dado el servicio desplegado, cuando se conecta a la base, entonces la conexión va cifrada y usa un usuario distinto del administrador, sin permisos para alterar el esquema.

### HU-4: Servicio apto para la nube (Prioridad: P1)

Como equipo queremos que el servicio se configure, registre su actividad y se apague como espera la plataforma de contenedores.

**Criterios de aceptación**
1. Dado el servicio desplegado, cuando recibe peticiones, entonces cada petición y cada error quedan en sus logs en la consola de AWS, una entrada estructurada por evento con nivel y marca de tiempo.
2. Dado que la plataforma detiene una tarea, cuando la tarea recibe la señal de parada, entonces termina las peticiones en curso y cierra sus conexiones antes de salir.
3. Dado un nuevo despliegue, cuando se reemplazan las tareas, entonces las rutas del servicio siguen respondiendo durante el cambio.

### HU-5: Exponer el servicio solo a través del API Gateway compartido (Prioridad: P1)

Como equipo queremos que Usuarios sea accesible únicamente por el gateway de la plataforma, con las mismas rutas que el gateway local.

**Criterios de aceptación**
1. Dado el servicio desplegado, cuando un cliente sin firma llama a `GET <gateway>/api/v1/usuarios` o `GET <gateway>/api/v1/usuarios/{id}`, entonces recibe la misma respuesta (código y cuerpo) que daría `GET /api/v1/users` o `GET /api/v1/users/{id}` del servicio.
1b. Dado un cliente sin firma de AWS (o con una identidad sin permiso), cuando llama a `POST`, `PUT`, `PATCH …/role` o `DELETE` bajo `<gateway>/api/v1/usuarios`, entonces el gateway responde 403 y la petición no llega al servicio.
1c. Dada una petición firmada por una identidad con permiso de invocación, cuando llama a una ruta de escritura, entonces recibe la misma respuesta que daría el servicio (201/200/204/400/404/409/422).
2. Dado el servicio desplegado, cuando se intenta llegar a él desde internet sin pasar por el gateway, entonces no es posible: no tiene URL pública propia ni sus tareas tienen IP pública.
3. Dada una ruta fuera de `/api/v1/usuarios` (p. ej. `/api/v1/usuarios-x`, `/actuator/health`, `/swagger-ui.html`), cuando se llama al gateway, entonces responde 404 sin llegar al servicio.
4. Dado que el gateway gestiona CORS, cuando el frontend llama desde su origen permitido, entonces el servicio no añade cabeceras CORS propias que entren en conflicto.

### HU-6: Entender y reproducir el despliegue (Prioridad: P2)

Como revisor o nuevo integrante quiero encontrar en el repositorio cómo se despliega el servicio y qué hay que configurar.

**Criterios de aceptación**
1. Dada una cuenta con solo los recursos de arranque y la plataforma, cuando se siguen los pasos documentados y se hace un commit a `main`, entonces se obtiene el entorno funcionando.
2. Dado el README, cuando se busca la lista de variables y secretos de GitHub, las rutas publicadas y los costes, entonces aparecen con su propósito y un ejemplo (sin valores secretos).

### Casos borde

- El aprovisionamiento falla a mitad → el siguiente commit retoma desde el estado registrado sin duplicar recursos.
- Un recurso gestionado se cambia a mano en la consola → el siguiente aprovisionamiento lo devuelve a lo definido en el repositorio.
- Primer despliegue: la base tarda varios minutos en estar disponible → el servicio no se da por desplegado hasta que su comprobación de salud responde saludable.
- La base no está disponible tras el despliegue → la comprobación de salud falla y la plataforma no envía tráfico a esas tareas.
- El escalado llega al máximo de tareas → el total de conexiones a la base no supera el presupuesto definido en RNF-002.
- Se intenta borrar la base desde el flujo de aprovisionamiento → la protección contra borrado lo impide y el flujo falla.
- Se desaprovisiona con el entorno ya eliminado → termina sin error indicando que no hay nada que eliminar.
- Se lanza el desaprovisionamiento mientras corre un aprovisionamiento → espera a que termine.
- La plataforma compartida no está aprovisionada → el aprovisionamiento falla antes de cambiar nada, indicándolo.
- Ya existe en el gateway una ruta con la misma clave → el aprovisionamiento falla con conflicto y la ruta existente no cambia.
- Geoespacial (u otro servicio) se desaprovisiona o se recrea → Usuarios sigue funcionando, porque no comparte con él roles, base ni grupos de seguridad.
- Un cambio de esquema (nueva migración) llega con varias tareas arrancando a la vez → se aplica una sola vez y ninguna tarea arranca con un esquema a medias.
- El flujo se lanza desde una rama distinta de `main` o desde un fork → no obtiene credenciales de AWS.

## 5. Requisitos funcionales

- **RF-001**: El servicio DEBE leer su configuración del entorno; en la nube, las credenciales de la base DEBEN venir del almacén de secretos de AWS y no aparecer en el repositorio, en la imagen ni en los logs.
- **RF-002**: En la nube, la conexión a la base DEBE ir cifrada; si no se puede cifrar, el servicio DEBE fallar en lugar de conectarse sin cifrar.
- **RF-003**: El tamaño del pool de conexiones DEBE cumplir `conexiones por tarea × tareas máximas ≤ presupuesto` (RNF-002), comprobado antes de desplegar.
- **RF-004**: El servicio DEBE escribir sus logs en la salida estándar, una entrada estructurada por línea (nivel, marca de tiempo, mensaje), con una entrada por petición que incluya método, ruta, código de respuesta y duración.
- **RF-005**: Al recibir la señal de parada, el servicio DEBE dejar de aceptar peticiones, terminar las que están en curso y cerrar el pool antes de salir, dentro del margen de parada de la plataforma.
- **RF-006**: La imagen DEBE escuchar en un puerto fijo documentado, exponer una comprobación de salud que refleje la disponibilidad de la base, ejecutarse sin privilegios de administrador y estar construida para la arquitectura de la plataforma.
- **RF-007**: Cada imagen publicada DEBE etiquetarse con el identificador del commit, de forma inmutable.
- **RF-008**: El esquema en la nube DEBE salir únicamente de las migraciones versionadas del repositorio, aplicarse una sola vez por versión antes de que la nueva versión reciba tráfico, y ser idempotente al repetir el despliegue.
- **RF-009**: La base de la nube DEBE empezar con los mismos datos iniciales que en local: los 7 usuarios de la migración de semilla versionada (administrador por defecto incluido, US-007), insertados una sola vez y sin duplicarse en despliegues posteriores.
- **RF-010**: El servicio DEBE conectarse con un usuario propio con permisos de lectura y escritura de datos sobre sus tablas y sin permisos de DDL, distinto del usuario administrador; las migraciones las aplica una identidad con permisos de esquema.
- **RF-011**: Toda la infraestructura del servicio DEBE estar definida como código en este repositorio: instancia PostgreSQL propia, reglas de red de acceso a ella, secretos, registro de imágenes, roles propios de ejecución e infraestructura, servicio Express con escalado, comprobación de salud, logs y rutas en el gateway.
- **RF-012**: El servicio NO DEBE gestionar ni reutilizar recursos de otros microservicios (roles, clúster, base, grupos de seguridad, registro de imágenes), ni recursos de la plataforma o de arranque; solo los lee cuando los necesita.
- **RF-013**: El estado de la infraestructura DEBE guardarse de forma remota, cifrada y con bloqueo, bajo una clave propia del servicio.
- **RF-014**: El flujo de aprovisionamiento DEBE ejecutarse en cada push a `main` y, en este orden: comprobar que la plataforma existe; ejecutar las pruebas unitarias y de integración y las comprobaciones estáticas; aprovisionar los recursos base; construir y publicar la imagen; aplicar el esquema (RF-008); desplegar la versión; registrar o actualizar las rutas en el gateway (RF-019); comprobar a través del gateway que responde. Un paso fallido detiene los siguientes.
- **RF-015**: Debe existir un flujo de desaprovisionamiento solo manual, con confirmación explícita (escribir el nombre del entorno), que elimine primero las rutas, después el servicio y después los recursos base, dejando copia final de la base. La protección contra borrado solo la levanta este flujo.
- **RF-016**: Los flujos DEBEN autenticarse con credenciales temporales (federación de identidad de GitHub), sin claves de larga duración.
- **RF-017**: Los flujos DEBEN impedir ejecuciones simultáneas sobre la misma infraestructura.
- **RF-018**: El servicio, la base y cualquier tarea puntual DEBEN desplegarse en la red privada compartida, tomando VPC y subredes del contrato SSM de la plataforma en cada aprovisionamiento (todas las subredes privadas). Sin URL pública propia ni IP pública en las tareas.
- **RF-019**: El servicio DEBE registrar en el gateway compartido exactamente estas rutas, que reproducen la convención local (`/api/v1/usuarios/*` → `/api/v1/users/*`):
  - Públicas (sin autenticación de clientes): `GET /api/v1/usuarios` → `GET /api/v1/users`; `GET /api/v1/usuarios/{id}` → `GET /api/v1/users/{id}`.
  - Internas (exigen firma de AWS de una identidad con permiso de invocación): `POST /api/v1/usuarios` → `POST /api/v1/users`; `PUT /api/v1/usuarios/{id}` → `PUT /api/v1/users/{id}`; `PATCH /api/v1/usuarios/{id}/role` → `PATCH /api/v1/users/{id}/role`; `DELETE /api/v1/usuarios/{id}` → `DELETE /api/v1/users/{id}`.
  Ninguna otra ruta (salud, documentación, raíz) queda publicada.
- **RF-020**: Si la plataforma compartida no está aprovisionada, el aprovisionamiento DEBE fallar antes de modificar ningún recurso, con un mensaje que lo indique.
- **RF-021**: El repositorio DEBE documentar: los recursos de arranque que usa y cómo comprobarlos; variables y secretos de GitHub; cómo funciona cada flujo; las rutas publicadas; los costes; y el inventario de recursos que crea.
- **RF-022**: El entorno local (`./gradlew bootRun` con perfil `local`, `docker compose` de `deploy/docker`) y la suite de pruebas DEBEN seguir funcionando sin cambios en su forma de uso.

## 6. Requisitos no funcionales

- **RNF-001 (seguridad)**: Ningún secreto en el repositorio, la imagen, las variables de GitHub ni los logs; la base no es pública y solo admite conexiones de las tareas de Usuarios; el servicio solo es alcanzable desde internet por el gateway.
- **RNF-002 (conexiones)**: Con el escalado al máximo, las conexiones simultáneas del servicio a su base no superan 50 (la instancia más pequeña admite ~80; el resto queda para migraciones y administración).
- **RNF-003 (idempotencia)**: Dos aprovisionamientos seguidos sobre el mismo commit dan 0 cambios de infraestructura en el segundo.
- **RNF-004 (disponibilidad en despliegue)**: Durante un despliegue las rutas del servicio siguen respondiendo (sin ventana de caída).
- **RNF-005 (duración)**: Un aprovisionamiento sin cambios de base de datos termina en menos de 20 minutos.
- **RNF-006 (coste)**: Tamaños mínimos (una zona para la base, clase de instancia más pequeña adecuada, mínimo una tarea), con su coste mensual documentado.
- **RNF-007 (protección de datos)**: Copias automáticas con al menos 7 días de retención y protección contra borrado accidental.

## 7. Entidades clave

Sin entidades nuevas: el modelo `User` (id, nombre, correo único, rol `USER`/`ADMIN`) no cambia. Recursos relevantes:

- **Entorno del servicio**: base PostgreSQL propia, secretos de conexión, registro de imágenes, roles propios, servicio de contenedores con balanceador interno compartido por Express, rutas en el gateway, reglas de red y logs.
- **Plataforma compartida** (externa): red privada, salida a internet y API Gateway; solo se consume.
- **Recursos de otros servicios** (externos): p. ej. roles y base de Geoespacial; ni se usan ni se gestionan.

## 8. Fuera de alcance

- Varios entornos; dominio o certificado propios.
- Crear o modificar la plataforma compartida.
- Autenticación de clientes finales en el gateway (no existe en la plataforma) y autorización por rol en el servicio.
- Publicar la documentación interactiva (Swagger) por el gateway (en local es `/docs/usuarios`).
- Mensajería entre servicios, eventos de dominio y cambios del contrato de la API.
- Configurar Reportes o el frontend para que usen la nueva URL, y dar a cualquier consumidor permiso de invocación sobre las rutas de escritura o firmar sus peticiones (consecuencia aceptada: el frontend no puede escribir usuarios por el gateway sin un intermediario firmante).
- Migrar datos existentes desde entornos locales.
- RDS Proxy, multi-zona, réplicas; paneles y alarmas adicionales.
- Validación del pipeline en pull requests.

## 9. Supuestos

- Cuenta `348364742283`, región `us-east-2`; la plataforma compartida está aprovisionada y su contrato SSM es el de su README.
- El rol de los workflows y sus políticas (`github-actions-urban-alertPolicy`, `-terraform`, `-platform`) ya permiten gestionar recursos con prefijo `urban-alert-*`, de modo que Usuarios no necesita cambios de arranque; si un permiso falta, se añade al JSON versionado correspondiente y lo aplica un administrador.
- Los secretos `AWS_ROLE_ARN` y la variable `AWS_REGION` se configuran en el repo `urban-alert-usuarios` (la confianza OIDC ya cubre `Unal-Team-C/*` en `main`).
- El registro de imágenes del servicio no existe todavía y lo crea el código (si existiera, se importaría y se anotaría en el inventario).
- Express comparte un balanceador interno por VPC (lo creó Geoespacial); Usuarios añade su regla por cabecera `Host`.
- La base es PostgreSQL 17 (en local se usa 16; el esquema es compatible) con la contraseña del administrador gestionada por AWS.
- Escalado por defecto de 1 a 5 tareas.
- Las pruebas de integración en CI usan una base en contenedor dentro del runner, no la de la nube.
- Las rutas de lectura quedan sin autenticación de clientes; las de escritura exigen firma IAM. Ningún consumidor tiene hoy permiso de invocación: concederlo (en el repo del consumidor) queda fuera de alcance.

## 10. Métricas de éxito

- **MS-001**: Desde un commit a `main` hasta la nueva versión respondiendo por el gateway pasan menos de 20 minutos sin acción manual.
- **MS-002**: Los escenarios de la spec funcional pasan contra la URL del gateway: lecturas sin firma; escrituras (crear, actualizar, cambiar rol, borrar, errores 400/404/409/422) con petición firmada; escrituras sin firma reciben 403.
- **MS-003**: Un segundo aprovisionamiento sin cambios reporta 0 recursos modificados.
- **MS-004**: Ningún secreto aparece en el repositorio, la imagen publicada ni los logs de los flujos.
- **MS-005**: Tras desaprovisionar Usuarios, las rutas de Geoespacial siguen respondiendo.

## 11. Clarificaciones

### Sesión 2026-10-11
- P: ¿Qué datos iniciales lleva la base de la nube? → R: **Los 7 usuarios de la semilla**, igual que en local (RF-009). El usuario aceptó que nombres y correos reales queden en la base de la nube.
- P: ¿Cómo se autorizan las rutas? → R: **Lecturas públicas, escrituras con firma IAM** (RF-019, HU-5 1–1c). Consecuencia aceptada: sin un consumidor firmante, las escrituras solo se pueden hacer con credenciales de AWS.
- P: ¿Dónde se trabaja? → R: Rama `aws-provisioning` y PR a `main`, como Geoespacial (nada se despliega hasta el merge).

---

### Checklist de calidad (autorrevisión antes de entregar)

- [x] Sin detalles de implementación — AWS, GitHub Actions, Terraform, ECS Express y PostgreSQL son restricciones fijadas por el usuario y la plataforma, no decisiones de diseño
- [x] Cada requisito es testeable y no ambiguo
- [x] Criterios de aceptación con resultado observable
- [x] Casos borde identificados
- [x] Alcance acotado (fuera de alcance explícito)
- [x] Como máximo 3 `[NEEDS CLARIFICATION]` pendientes (0 tras la sesión del 2026-10-11)
- [x] Coherente con `.sdd/constitution.md` 1.0.0
