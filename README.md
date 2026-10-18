# Plan de trabajo — Sistema de Encomiendas y Envíos Express

Curso: Desarrollo de Aplicaciones Web II (4697) · Equipo de 5 · Stack: Spring Boot 3 + MySQL + Angular 21

> Este documento sale del análisis de: `base del proyecto grupal.md`, `plan de proyecto.pdf`, `ficha tecnica del proyecto.pdf`, los proyectos `DAWII-T1`, `DAWII-T2`, `DESARROLLO-DE-APLICACIONES-WEB-II-04697-` y el frontend `hospital_web`.

---

## 1. Qué exige el curso (lo que se califica)

| Evaluación | Qué piden | Puntos |
|---|---|---|
| **AP1 (semana 11, ≥50 %)** | Servicio REST **login** (usuario y password en BD, password con **BCrypt**) **con validaciones** | 6 |
| | **Pruebas de la capa de datos**: insertar, actualizar, eliminar **y listar** (las 4 para el puntaje máximo) | 6 |
| | Parte formal: SEPTE (≥3 variables), objetivos SMART, justificación + beneficiarios, conclusiones | 8 |
| **SP1 (semana 14, 100 %)** | Servicios REST con **GET, POST, PUT y DELETE** | 6 |
| | Angular que **consume todos** los servicios REST | 6 |
| | Informe (60 %) + sustentación (40 %) | — |

**Entregables finales** (en un zip): informe `.docx` (A4, Arial 11, interlineado simple, márgenes 3 cm sup/inf y 2.5 cm izq/der), presentación `.ppt`, carpeta del aplicativo y **video demo de 3–5 min**.

### Puntos de atención
1. **Las pruebas deben cubrir las 4 operaciones**, no solo "buscar por tracking y actualizar estado" como dice la base. Hay que añadir insertar, eliminar y listar a `EnvioRepositoryTest`.
2. **DELETE tiene que existir de verdad** (la rúbrica pide los 4 verbos). "Anular envío" = `DELETE /envios/{id}`, permitido solo si el estado es `RECEPCIONADO`.
3. La **ficha técnica** menciona como tema "Ecommerce (tienda virtual)". La propuesta de encomiendas es distinta: conviene confirmar con el docente que el tema está aceptado, o encuadrarlo como "seguimiento de las ventas de un ecommerce" en la justificación.
4. El **login del frontend es simulado**: `core/service/login.service.ts` valida contra usuarios en memoria y genera un JWT falso. Hay que reemplazarlo por un `POST /auth/login` real.
5. "Tiempo real" no está en la rúbrica. Con RabbitMQ el historial se registra de forma asíncrona y el cliente lo ve al consultar/refrescar. **No usar WebSockets/SignalR.**

---

## 2. Qué se reutiliza de cada proyecto

| Necesidad | Fuente | Qué se toma |
|---|---|---|
| Login, JWT, BCrypt, roles | `backend/jwt-sales-services` | `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`, `AuthController`, `AuthService`, `AppUser`, `AppUserRepository`, DTOs de login, `application.yml` (MySQL, puerto, secret) |
| Capas y CRUD | mismo proyecto (`entidades / repositorio / negocio / rest / dto / config`) | Patrón completo de `Customer` y `Sale` → molde para `Envio` |
| Datos iniciales | `config/SaleDataInitializer.java` | Molde para crear usuarios y envíos de prueba |
| Mensajería | `DAWII-T1/ms-recargas/rabbitmq/*` (más simple que Kafka) y `products-services/rabbitmq` | `RabbitMQConfig`, `Producer`, `Event` → `TrackingStatusEvent` |
| Consumidor / auditoría | `products-services/kafka/StockAuditConsumer` | Idea del consumidor que registra historial |
| Feign | `DAWII-T1/ms-recargas/client/TarjetaClient`, `DAWII-T2/ms-resolucion/client/ReclamoClient` | Cliente Feign de tracking → courier |
| Gateway + Eureka | `spring_cloud/api-gateway`, `spring_cloud/eureka-server` | Copiar y cambiar rutas |
| Docker (MySQL, RabbitMQ) | `DAWII-T1/docker`, `queue/docker-compose-rabbitmq.yml` | Un único `docker-compose.yml` |
| Colección Postman | carpetas `postman/` | Molde |
| Frontend | `frontend/hospital_web` (Angular 21 + Material + Bootstrap, standalone, zoneless, HashLocationStrategy) | Plantilla "cliniva": layout, sidebar, auth guard, interceptor |

**Decisión: se usan Kafka y RabbitMQ, cada uno con un rol distinto** (igual que `products-services`, que ya combina ambos):
- **Kafka** → *stream de eventos de tracking*: tópico `tracking-status` con `TrackingStatusEvent`. Dos consumidores con grupos distintos (`historial-cg` guarda el historial, `audit-cg` registra auditoría). Molde: `products-services/kafka/*`.
- **RabbitMQ** → *mensajes de trabajo puntuales*: cola de notificaciones al cliente cuando su envío pasa a `ENTREGADO` o `DEVUELTO`. Molde: `ms-recargas/rabbitmq/*` y `products-services/rabbitmq/*`.

Ambos se levantan con Docker, así que el `docker-compose.yml` incluye MySQL + Kafka + RabbitMQ.

---

## 3. Arquitectura propuesta (simple)

```
Angular (courier-web) ──► API Gateway :8762 ──┬─► courier-service :8083  (auth + usuarios + envíos)  ── MySQL
                                              └─► tracking-service :8084 (historial)                 ── MySQL
                              Eureka :8761         ▲
                       courier-service publica:    │
                         ├─ Kafka  (tópico tracking-status)  → tracking-service: historial-cg (historial) + audit-cg (auditoría)
                         └─ RabbitMQ (cola notificaciones)   → tracking-service: guarda Notificacion al cliente
```

### Prioridad por fases (si falta tiempo, se recorta de abajo hacia arriba sin perder puntos de rúbrica)

| Fase | Contenido | ¿Cubre la rúbrica? |
|---|---|---|
| **A — Núcleo** | `courier-service` (login BCrypt/JWT + CRUD Envíos) + Angular consumiendo todo | **Sí, 100 % de la parte técnica** |
| **B — Eventos** | `tracking-service` + Kafka (historial/auditoría) + RabbitMQ (notificaciones) + pantalla de seguimiento | Valor extra |
| **C — Infra** | Eureka + API Gateway + Feign | Valor extra |

### Modelo de datos

- **AppUser**: `id, username, password (BCrypt), nombre, email, rol (ROLE_OPERADOR | ROLE_CLIENTE)`
- **Envio**: `id, codigoTracking (EN-AAAA-000001), remitenteId (→ AppUser), destinatarioNombre, direccionDestino, pesoKg, costoEnvio, estado (RECEPCIONADO | EN_RUTA | ENTREGADO | DEVUELTO), fechaRegistro`
- **HistorialTracking** (tracking-service, vía Kafka): `id, codigoTracking, estado, fecha, usuario`
- **Notificacion** (tracking-service, vía RabbitMQ): `id, codigoTracking, destinatarioId, mensaje, fecha, leida`

Reglas: `costoEnvio` = tarifa base + pesoKg × tarifa por kg (calculado en el servicio). Transiciones válidas: `RECEPCIONADO → EN_RUTA → ENTREGADO | DEVUELTO`. Un cliente solo ve envíos donde `remitenteId` es él.

### Contrato de API (acordar ya para trabajar en paralelo)

| Método | Ruta | Rol | Notas |
|---|---|---|---|
| POST | `/auth/login` | público | Respuesta con la forma que ya espera el front: `{ token, user:{ id, username, name, email, roles:[{name,priority}], permissions:[] } }` |
| POST | `/users` | público | Registro de cliente |
| GET | `/envios` | OPERADOR todos / CLIENTE los suyos | Filtro opcional `?estado=` |
| GET | `/envios/{id}` | ambos | |
| GET | `/envios/tracking/{codigo}` | ambos | |
| POST | `/envios` | OPERADOR | Validado con `@Valid` |
| PUT | `/envios/{id}` | OPERADOR | Edita datos |
| PATCH | `/envios/{id}/estado` | OPERADOR | Publica evento si pasa a EN_RUTA / ENTREGADO |
| DELETE | `/envios/{id}` | OPERADOR | Anular; solo si `RECEPCIONADO` |
| GET | `/tracking/{codigo}/historial` | ambos | tracking-service |
| GET | `/notificaciones` | CLIENTE | tracking-service; las del usuario del JWT |
| PATCH | `/notificaciones/{id}/leida` | CLIENTE | |

Errores uniformes: `{ status, message, errors[] }` con `@RestControllerAdvice` (400 validación, 401, 403, 404, 409 transición inválida).

Evento de tracking (Kafka, `record`): `TrackingStatusEvent(codigoTracking, estado, remitenteId, usuario, timestamp)`. Evento de notificación (RabbitMQ): `EnvioNotificacionEvent(codigoTracking, remitenteId, estado, mensaje)`.

Convenciones: paquete `com.cibertec.courierservice`, carpetas `entidades / repositorio / negocio / rest / dto / security / config` (igual que el curso), Java 17, Gradle, MySQL en el puerto 5510.

---

## 4. Estructura del repositorio

```
courier-express/
├─ backend/
│  ├─ courier-service/
│  ├─ tracking-service/
│  ├─ eureka-server/
│  └─ api-gateway/
├─ frontend/courier-web/        ← copia de hospital_web adaptada
├─ docker/docker-compose.yml    ← MySQL + RabbitMQ (+ phpMyAdmin)
├─ postman/
├─ docs/                        ← informe, ppt, diagramas, video
├─ .gitignore                   ← build/, .gradle/, node_modules/, .idea/, bin/, *.env
└─ README.md                    ← cómo levantar todo
```

### Flujo de Git
- `main` protegida: nadie hace push directo, todo entra por **Pull Request** que revisa el coordinador.
- Ramas: `feature/<area>-<tarea>` (ej. `feature/envios-crud`, `feature/front-login`).
- Commits cortos y en presente: `agrega EnvioRepositoryTest`, no "cambios".
- Un PR = una tarea de la lista de abajo. Se marca la casilla del checklist al fusionar.
- El coordinador responde con *Request changes* en el PR y el autor corrige en la misma rama.
- No subir `build/`, `.gradle/`, `node_modules/`, claves ni `.env` reales.

---

## 5. Reparto de tareas

**Coordinador (tú):** todo el **frontend** (`courier-web`), la revisión de PRs, el armado final del informe y la integración. Los 4 integrantes restantes se reparten el **backend** y las secciones del informe. Ellos no tocan `frontend/`.

> Como el front depende del back, el contrato de API (sección 3) es lo primero que se congela, y cada integrante entrega a tu equipo una colección Postman de lo suyo para que puedas conectar el front sin esperar.

### Coordinador — Frontend completo + revisión
**Técnico**
- [ ] Copiar `hospital_web` → `frontend/courier-web`; renombrar la app (título, logo, textos); limpiar sidebar (Dashboard, Envíos, Seguimiento, Notificaciones, Cerrar sesión).
- [ ] `environment.ts` con `apiUrl` hacia el gateway (o `:8083` en Fase A).
- [ ] **Reemplazar `LoginService` simulado** por `POST /auth/login` respetando la forma de respuesta; interceptor con `Authorization: Bearer`.
- [ ] `Role`: `ADMIN/DOCTOR/PATIENT` → `OPERADOR/CLIENTE`; rutas `/operador` y `/cliente`; `AuthGuard` y redirección post-login por rol.
- [ ] Registro de cliente (`signup` → `POST /users`).
- [ ] **Operador:** `EnvioService` Angular con los 4 verbos, formulario reactivo de encomienda, tabla `mat-table` con **badges de colores** por estado, cambio de estado y **modal de confirmación al anular**.
- [ ] **Cliente:** "Mis envíos", consulta de seguimiento por código con línea de tiempo (historial) y campana de notificaciones.
- [ ] Snackbars de éxito/error y pruebas manuales end-to-end.
- [ ] Revisar PRs (checklist de la sección 7) y dar feedback.

**Informe:** armado y formato final del `.docx` (carátula, Arial 11, márgenes), Resumen y Productos/entregables.

### Integrante 1 — Base del repo + Seguridad (login)
- [ ] Crear `courier-service` copiando `jwt-sales-services`: `AppUser`, `AppUserRepository`, `JwtService`, `JwtAuthenticationFilter`, `SecurityConfig` (**BCrypt**), `AuthController`, `AuthService`.
- [ ] `LoginResponse` con la forma `{ token, user:{ id, username, name, email, roles:[{name,priority}], permissions } }` (la que ya espera el front).
- [ ] Validaciones del login (`@NotBlank`, `@Size`), 401 con mensaje claro, `@RestControllerAdvice` global (400/401/403/404/409).
- [ ] `POST /users` (registro con rol `ROLE_CLIENTE`) y `DataInitializer` con 1 operador y 2 clientes.
- [ ] CORS para `http://localhost:4200`.
- [ ] `AppUserRepositoryTest` (guardar, buscar por username, eliminar, listar) + test de `AuthService` (BCrypt, credenciales inválidas).
- [ ] Subir el esqueleto a `main` en las primeras 48 h para que los demás ramifiquen.
- [ ] Colección Postman de auth/usuarios.

**Informe:** Introducción y Definición y alcance.

### Integrante 2 — Backend Envíos (CRUD) + pruebas AP1
- [ ] `Envio` + `EstadoEnvio`, `EnvioRepository` (`findByCodigoTracking`, `findByRemitenteId`, `findByEstado`).
- [ ] `EnvioService`: generar `codigoTracking`, calcular `costoEnvio`, validar transiciones (`RECEPCIONADO → EN_RUTA → ENTREGADO | DEVUELTO`), limitar al cliente a sus envíos (usuario del JWT).
- [ ] `EnvioController`: GET (lista, por id, por tracking), POST, PUT, PATCH estado, DELETE (solo `RECEPCIONADO`).
- [ ] DTOs con validaciones (`pesoKg > 0`, destinatario y dirección obligatorios).
- [ ] **`EnvioRepositoryTest` con `@DataJpaTest` + H2**: **insertar, actualizar, eliminar, listar** y búsqueda por tracking (6/12 de la parte técnica de AP1).
- [ ] `EnvioServiceTest` (Mockito): costo y transiciones inválidas.
- [ ] Colección Postman de envíos.

**Informe:** Diagnóstico **SEPTE** (Social, Económica y Tecnológica como mínimo) con estadísticas y fuentes citadas en pie de página.

### Integrante 3 — Kafka + tracking-service (historial y auditoría)
- [ ] `docker/docker-compose.yml`: MySQL + Kafka (tomar de `queue/docker-compose-kafka.yml`).
- [ ] En `courier-service`: `TrackingStatusEvent` (record), `TrackingProducer` con `KafkaTemplate`, tópico `tracking-status`; se publica cuando el estado pasa a `EN_RUTA` o `ENTREGADO` (coordinar el punto de llamada con el integrante 2).
- [ ] Crear `tracking-service`: entidad `HistorialTracking`, repositorio, `HistorialConsumer` (`@KafkaListener`, grupo `historial-cg`) y `AuditConsumer` (grupo `audit-cg`, log), basados en `products-services/kafka`.
- [ ] `GET /tracking/{codigo}/historial`.
- [ ] **Feign** hacia courier-service para devolver historial + datos del envío (molde: `ms-recargas/client/TarjetaClient`).
- [ ] `HistorialTrackingRepositoryTest`.
- [ ] Colección Postman de tracking.

**Informe:** Objetivos (≥2, **SMART**) y Justificación (impacto y beneficiarios directos e indirectos).

### Integrante 4 — RabbitMQ + notificaciones + infraestructura (Gateway/Eureka)
- [ ] `docker-compose.yml`: añadir RabbitMQ (de `queue/docker-compose-rabbitmq.yml`).
- [ ] En `courier-service`: `RabbitMQConfig`, `EnvioNotificacionEvent` y `NotificacionProducer`; se envía cuando el estado pasa a `ENTREGADO` o `DEVUELTO` (molde: `ms-recargas/rabbitmq`).
- [ ] En `tracking-service`: entidad `Notificacion`, repositorio, `NotificacionConsumer` (`@RabbitListener`), `GET /notificaciones` (filtrado por usuario del JWT) y `PATCH /notificaciones/{id}/leida`. Coordinar con el integrante 3 porque ambos tocan `tracking-service`: el 3 crea el proyecto y el 4 añade su paquete `rabbitmq/`.
- [ ] `eureka-server` y `api-gateway` (copiar de `spring_cloud`): rutas `/auth/**`, `/users/**`, `/envios/**` → courier; `/tracking/**`, `/notificaciones/**` → tracking. CORS en el gateway.
- [ ] `NotificacionRepositoryTest`.
- [ ] README de cómo levantar todo con Docker y orden de arranque.

**Informe:** Conclusiones (máx. 3), Recomendaciones (máx. 3), Glosario, Bibliografía; además la **presentación `.ppt`** y el **video demo (3–5 min)** (grabando la demo con el front del coordinador).

---

## 6. Cronograma por sprints

### Sprint 1 — Hasta AP1 (semana 11)
| Quién | Entrega |
|---|---|
| Coordinador | Front renombrado + login real + guard por roles; revisar PRs; unir la parte formal |
| 1 | `courier-service` con login BCrypt/JWT y tests de usuario |
| 2 | `Envio` + repositorio + **tests de las 4 operaciones** + CRUD básico |
| 3 | docker-compose con Kafka + borrador de objetivos y justificación |
| 4 | Conclusiones preliminares, glosario, bibliografía base |
| Todos | SEPTE, objetivos, justificación y conclusiones **redactados** (8 pts de AP1) |

### Sprint 2 — Hasta SP1 (semana 14)
| Quién | Entrega |
|---|---|
| Coordinador | Pantallas de Operador y Cliente conectadas a los 4 verbos; seguimiento y notificaciones |
| 1 | Pulir validaciones y manejo de errores |
| 2 | Reglas de negocio completas + tests de servicio |
| 3 | Kafka productor/consumidores + `tracking-service` + Feign |
| 4 | RabbitMQ + notificaciones + Gateway/Eureka + PPT + video |
| Todos | Ensayo de sustentación (cada uno explica su parte) |

**Hitos de control:** (1) esqueleto en `main` a las 48 h; (2) contrato de API congelado al final de la primera semana; (3) congelar funcionalidades 3 días antes de la entrega.

---

## 7. Cómo trabajar en paralelo sin bloquearse

- El **contrato de API** (sección 3) ya está definido: el front puede usar datos de prueba (`of([...])`) hasta que el back esté listo.
- El integrante 1 publica pronto el login; hasta entonces el front puede seguir con el simulado.
- Los tests de `@DataJpaTest` usan H2 y no necesitan Docker ni a los demás servicios.
- Cada PR debe indicar: qué hace, cómo probarlo y captura/log del resultado.

### Checklist del PR (para tu revisión)
- [ ] Compila y los tests pasan (`./gradlew test` / `ng build`).
- [ ] Sigue la estructura de paquetes y nombres acordados.
- [ ] Sin secretos ni archivos de build en el commit.
- [ ] Validaciones y códigos HTTP correctos.
- [ ] El autor puede explicar el código (importante para la sustentación).

---

## 8. Checklist final contra la rúbrica

**Parte técnica**
- [ ] Login REST con usuario/clave en BD, **BCrypt** y validaciones
- [ ] Pruebas de acceso a datos: **insertar, actualizar, eliminar, listar**
- [ ] REST con **GET, POST, PUT y DELETE**
- [ ] Angular consume **todos** esos servicios y persiste en BD

**Parte formal**
- [ ] SEPTE (≥3 variables) con evidencias y pie de página
- [ ] ≥2 objetivos SMART
- [ ] Justificación con beneficiarios directos e indirectos
- [ ] ≤3 conclusiones y ≤3 recomendaciones
- [ ] Glosario y bibliografía
- [ ] Informe `.docx` (A4, Arial 11), `.ppt`, aplicativo y video 3–5 min en un zip
