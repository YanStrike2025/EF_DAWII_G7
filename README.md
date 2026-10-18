# Sistema de Encomiendas y Envíos Express (Logística & Courier)

Proyecto final de **Desarrollo de Aplicaciones Web II (4697)** · Grupo 7 · 5 integrantes
Stack: **Spring Boot 3.2 (Java 17, Gradle) · MySQL · Kafka · RabbitMQ · Spring Cloud (Eureka, Gateway, Feign) · Angular 21**

Sistema para una empresa de paquetería: un **operador** registra y despacha encomiendas y un **cliente** consulta el seguimiento de sus envíos.

> 📄 Contrato completo de endpoints, JSON, eventos y reglas de negocio: **[`docs/API-CONTRATO.md`](docs/API-CONTRATO.md)**. Léelo antes de empezar.

---

## 1. Qué se evalúa (parte técnica)

| Evaluación | Qué piden | Puntos |
|---|---|---|
| **AP1** (semana 11, ≥50 %) | Servicio REST **login** con usuario/clave en BD y password con **BCrypt**, **con validaciones** | 6 |
| | **Pruebas de la capa de datos**: insertar, actualizar, eliminar **y listar** (las 4) | 6 |
| **Final** (semana 14, 100 %) | Servicios REST con **GET, POST, PUT y DELETE** | 6 |
| | Angular que **consume todos** los servicios y persiste en BD | 6 |

Kafka, RabbitMQ, Gateway, Eureka y Feign son el valor añadido de la propuesta (se priorizan después del núcleo).

---

## 2. Arquitectura

```
Angular (courier-web :4200) ──► API Gateway :8762 ──┬─► courier-service :8083  (auth + usuarios + envíos)  ── MySQL courier_db
                                                    └─► tracking-service :8084 (historial + notificaciones) ── MySQL tracking_db
                                  Eureka :8761 (registro de servicios)
courier-service publica al cambiar el estado de un envío:
   ├─ Kafka    tópico  tracking-status       → tracking-service: historial-cg (guarda historial) + audit-cg (auditoría)
   └─ RabbitMQ cola    notificaciones.envio  → tracking-service: crea la Notificacion del cliente
```

**Fases** (si falta tiempo se recorta de abajo hacia arriba sin perder puntos de rúbrica):

| Fase | Contenido | Rúbrica |
|---|---|---|
| **A — Núcleo** | `courier-service` (login + CRUD envíos + tests) + Angular consumiendo todo, sin pasar por el gateway | ✅ cubre el 100 % técnico |
| **B — Eventos** | Kafka + RabbitMQ + `tracking-service` + pantallas de seguimiento y notificaciones | valor extra |
| **C — Infra** | Eureka + API Gateway + Feign | valor extra |

### Estructura del repositorio

```
EF-DAWII-G7/
├─ backend/
│  ├─ courier-service/     Spring Boot (Gradle) · puerto 8083 · auth, usuarios, envíos
│  ├─ tracking-service/    Spring Boot (Gradle) · puerto 8084 · historial y notificaciones
│  ├─ eureka-server/       Spring Cloud (Maven) · puerto 8761
│  └─ api-gateway/         Spring Cloud (Maven) · puerto 8762
├─ frontend/
│  └─ courier-web/         Angular 21 (copia de la plantilla hospital_web, por adaptar)
├─ docker/docker-compose.yml   MySQL + phpMyAdmin + Kafka + Kafka UI + RabbitMQ
├─ postman/                    colecciones por rol
├─ docs/API-CONTRATO.md        contrato de API, eventos, reglas y datos
└─ .github/pull_request_template.md
```

Dentro de cada servicio: `entidades / repositorio / negocio / rest / dto / security / config` (+ `kafka`, `rabbitmq`, `client` donde aplica), igual que los proyectos del curso.

### Qué ya viene hecho (esqueleto)
- Los 4 proyectos backend **compilan** y tienen `build.gradle`/`pom.xml` con **todas las dependencias ya declaradas** (nadie debe editarlos), `application.yml` con MySQL, Kafka, RabbitMQ, JWT y Eureka configurados, y la clase `Application`.
- `courier-service` y `tracking-service` tienen los **records de eventos** (`TrackingStatusEvent`, `EnvioNotificacionEvent`) y los **stubs** `TrackingProducer` y `NotificacionProducer`: el Rol 2 ya puede llamarlos y los Roles 3 y 4 solo rellenan el cuerpo.
- `api-gateway` ya tiene las rutas y CORS; `eureka-server` está listo.
- `docker-compose.yml` completo y `frontend/courier-web` con la plantilla copiada.
- Los directorios vacíos tienen un `.gitkeep` (bórralo al crear tu primer archivo ahí).

---

## 3. Cómo levantar el entorno

Requisitos: **JDK 17+**, **Docker Desktop**, **Node 20+** y Angular CLI.

```bash
# 1) Infraestructura (MySQL, Kafka, RabbitMQ)
cd docker && docker compose up -d

# 2) Backend (una terminal por servicio, en este orden si usas Fase C)
cd backend/eureka-server   && ./mvnw spring-boot:run      # opcional hasta la Fase C
cd backend/courier-service && ./gradlew bootRun
cd backend/tracking-service && ./gradlew bootRun
cd backend/api-gateway     && ./mvnw spring-boot:run      # opcional hasta la Fase C

# 3) Frontend
cd frontend/courier-web && npm install && npm start       # http://localhost:4200
```

- Sin Eureka corriendo, los servicios arrancan igual (solo muestran avisos); el gateway sí lo necesita.
- Las bases `courier_db` y `tracking_db` se crean solas al arrancar cada servicio.
- Los tests de repositorio usan **H2 en memoria**: se ejecutan con `./gradlew test` sin Docker.
- Si Maven falla por espacios en la ruta del proyecto, mueve el repo a una ruta sin espacios.

URLs útiles: phpMyAdmin <http://localhost:3410> · Kafka UI <http://localhost:8090> · RabbitMQ <http://localhost:15672> (guest/guest) · Eureka <http://localhost:8761>.

---

## 4. Flujo de trabajo en Git

1. `main` solo recibe código por **Pull Request**; el coordinador revisa y fusiona. Nadie hace push directo a `main`.
2. Ramas: `feature/rolN-descripcion` (ej. `feature/rol2-envio-crud`). Parte siempre de `main` actualizada (`git pull`).
3. **Un PR = una tarea** de tu lista (PRs pequeños se revisan rápido). Usa la plantilla del PR.
4. Si el coordinador pide cambios, corrige **en la misma rama** y haz push; el PR se actualiza solo.
5. Commits cortos y descriptivos en presente: `agrega EnvioRepositoryTest`, no `cambios`.
6. No subir `build/`, `.gradle/`, `node_modules/`, claves ni `.env` (el `.gitignore` ya los cubre).
7. Marca tus casillas `[x]` de este README en el mismo PR en que completas la tarea.

### Reglas para no pisarse
- **Cada rol edita solo sus archivos** (ver "Archivos" en cada rol). Si necesitas tocar algo de otro rol, pídeselo o avisa en el PR.
- Archivos compartidos que **nadie edita sin avisar**: `build.gradle`, `pom.xml`, `application.yml`, `docs/API-CONTRATO.md`, `docker-compose.yml` (los dueños de cada uno están indicados abajo).
- Los records de eventos son un contrato: no cambiar campos sin avisar.
- El **frontend** es exclusivo del coordinador.

---

## 5. Roles del equipo

Hay **5 roles de backend/infra**. Cada integrante elige **uno**. Además, el **coordinador hace todo el frontend** (sección 6) encima de su rol.

| Rol | Responsabilidad | Integrante (completar) |
|---|---|---|
| **Rol 1** | Seguridad: login BCrypt/JWT, usuarios | Yan Carlos |
| **Rol 2** | Envíos: CRUD, reglas y **pruebas AP1** | |
| **Rol 3** | Kafka: productor, historial, auditoría, Feign | |
| **Rol 4** | RabbitMQ: notificaciones y seguridad en tracking-service | |
| **Rol 5** | Integración: Gateway/Eureka, datos de demo, Postman global, QA | |

> 💡 **Recomendación para el coordinador:** tomar el **Rol 5**. El esqueleto de Gateway/Eureka/Docker ya viene hecho, así que es el rol más liviano y el que mejor conecta con el trabajo del frontend.

### Dependencias entre roles (quién desbloquea a quién)

```
Rol 1 (login + base)  ──►  Rol 2 (envíos usan el usuario del token)  ──►  Rol 5 (datos demo, QA)
                                │
                                ├─ llama a TrackingProducer  (Rol 3 rellena el cuerpo)
                                └─ llama a NotificacionProducer (Rol 4 rellena el cuerpo)
Rol 3 (tracking-service: historial) ──► Rol 4 (suma notificaciones en el mismo servicio)
```
Nadie queda bloqueado: los stubs permiten compilar desde el día 1. Mientras el login no esté listo, los demás pueden probar con la seguridad abierta en su rama local (sin subirlo).

---

### Rol 1 — Seguridad: login BCrypt / JWT (AP1: 6 pts)

**Archivos:** `courier-service` → `security/`, `entidades/AppUser`, `repositorio/AppUserRepository`, `negocio/AuthService`, `negocio/UserService`, `rest/AuthController`, `rest/UserController`, `dto/Login*`, `dto/User*`, `config/DataInitializer` (usuarios), manejador global de errores.
**Molde:** `backend/jwt-sales-services` del repo del curso (`SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`, `AuthController`, `AuthService`, `AppUser`).

- [ ] `AppUser` (id, username único, password, nombre, email, rol `ROLE_OPERADOR` | `ROLE_CLIENTE`) y `AppUserRepository` (`findByUsername`, `existsByUsername`).
- [ ] `SecurityConfig`: stateless, `BCryptPasswordEncoder`, públicos solo `POST /auth/login` y `POST /users`; el resto autenticado. Autorización por rol para que el Rol 2 use `hasRole`/`@PreAuthorize`.
- [ ] `JwtService` + `JwtAuthenticationFilter` (el token lleva `userId`, `username` y rol; la expiración sale de `app.jwt.expiration-minutes`).
- [ ] `POST /auth/login` con `LoginRequest` validado (`@NotBlank`, `@Size`) y `LoginResponse` **con la forma exacta** de `docs/API-CONTRATO.md` (`token` + `user.roles[{name,priority}]` + `permissions`) — el front depende de ella.
- [ ] `POST /users`: registro de cliente (rol siempre `ROLE_CLIENTE`), contraseña guardada con BCrypt, 409 si el usuario existe.
- [ ] `@RestControllerAdvice` global con el formato de error `{status,message,errors[]}` (400 validación, 401, 403, 404, 409). Lo usan todos los roles.
- [ ] **CORS** para `http://localhost:4200` (valor en `app.cors.allowed-origin`).
- [ ] `DataInitializer` con `operador/operador123`, `cliente1/cliente123`, `cliente2/cliente123` (passwords con BCrypt).
- [ ] **Tests** (`src/test`): `AppUserRepositoryTest` con `@DataJpaTest` (guardar, buscar por username, eliminar, listar) y `AuthServiceTest` (login correcto, clave incorrecta, usuario inexistente, BCrypt no guarda texto plano).
- [ ] Colección Postman `postman/rol1-auth.json`.
- [ ] ⏱️ **Entregar primero**: subir a `main` en las primeras 48 h el login funcionando (aunque falten tests) para destrabar al resto.

### Rol 2 — Envíos: CRUD, reglas de negocio y pruebas (AP1: 6 pts + final: 6 pts)

**Archivos:** `courier-service` → `entidades/Envio`, `entidades/EstadoEnvio`, `repositorio/EnvioRepository`, `negocio/EnvioService`, `rest/EnvioController`, `dto/Envio*`.
**Molde:** `jwt-sales-services` (`Customer`, `Sale`, `CustomerService`, `CustomerController`).

- [ ] `Envio` + enum `EstadoEnvio` (`RECEPCIONADO, EN_RUTA, ENTREGADO, DEVUELTO`) según el modelo de datos del contrato; `fechaRegistro` automática.
- [ ] `EnvioRepository`: `findByCodigoTracking`, `findByRemitenteId`, `findByEstado`, `existsByCodigoTracking`.
- [ ] `EnvioService`: generar `codigoTracking` (`EN-AAAA-000001`), calcular `costoEnvio`, validar transiciones (409 si no es válida), restringir al CLIENTE a sus envíos (id del usuario autenticado).
- [ ] `EnvioController` con los **4 verbos**: `GET` (lista con `?estado=`, por id, por tracking), `POST`, `PUT`, `PATCH /{id}/estado`, `DELETE /{id}` (solo `RECEPCIONADO`; 204). Permisos por rol según el contrato.
- [ ] DTOs con validaciones: `destinatarioNombre` y `direccionDestino` obligatorios, `pesoKg > 0`.
- [ ] Al registrar y al cambiar de estado: llamar a `TrackingProducer.publicar(...)` (Kafka) y, si el estado es `ENTREGADO` o `DEVUELTO`, a `NotificacionProducer.publicar(...)` (RabbitMQ). Ver la tabla de eventos en el contrato.
- [ ] **`EnvioRepositoryTest`** con `@DataJpaTest` + H2 — **imprescindible para AP1, deben estar las 4 operaciones**:
  - [ ] insertar (guardar y verificar id y datos)
  - [ ] actualizar (cambiar estado/datos y verificar persistencia)
  - [ ] eliminar (borrar y verificar que ya no existe)
  - [ ] listar (`findAll`, `findByEstado`, `findByRemitenteId`)
  - [ ] buscar por `codigoTracking` (existente e inexistente)
- [ ] `EnvioServiceTest` (Mockito): costo calculado, transición válida, transición inválida, DELETE rechazado si no está `RECEPCIONADO`.
- [ ] Colección Postman `postman/rol2-envios.json`.

### Rol 3 — Kafka: eventos de tracking, historial y auditoría (final)

**Archivos:** `courier-service` → `kafka/TrackingProducer` (rellenar el stub). `tracking-service` → `kafka/`, `entidades/HistorialTracking`, `repositorio/HistorialTrackingRepository`, `negocio/HistorialService`, `rest/TrackingController`, `client/` (Feign), `dto/Historial*`, `TrackingServiceApplication` (solo para `@EnableFeignClients`).
**Molde:** `products-services/kafka` (`StockMovementEvent`, `StockAuditConsumer`, `StockUpdateConsumer`) y `DAWII-T1/ms-recargas/client/TarjetaClient` (Feign).

- [ ] Verificar que Kafka y MySQL levantan con `docker compose up -d` (ver el compose; corregir si hace falta y avisar).
- [ ] `TrackingProducer`: reemplazar el stub por `KafkaTemplate<String, TrackingStatusEvent>` enviando al tópico `app.kafka.topic-tracking` con clave = `codigoTracking`. Mantener la firma `publicar(TrackingStatusEvent)`.
- [ ] `tracking-service`: `HistorialTracking` + repositorio (`findByCodigoTrackingOrderByFechaAsc`).
- [ ] `HistorialConsumer` con `@KafkaListener(topics = "tracking-status", groupId = "historial-cg")` que guarda el historial.
- [ ] `AuditConsumer` con `groupId = "audit-cg"` que registra el evento en el log (como `StockAuditConsumer`).
- [ ] `GET /tracking/{codigo}/historial`: devuelve el envío (consultado por **Feign** a `courier-service` `GET /envios/tracking/{codigo}`, reenviando el token `Authorization`) + el historial.
- [ ] Cliente Feign con `@FeignClient(name = "courier-service")` y fallback/mensaje claro si courier no responde.
- [ ] **Tests:** `HistorialTrackingRepositoryTest` (`@DataJpaTest`: guardar, listar por código ordenado, eliminar) y prueba del `HistorialService`.
- [ ] Probar el flujo completo: cambiar estado de un envío → mensaje visible en Kafka UI → fila en `historial_tracking`.
- [ ] Colección Postman `postman/rol3-tracking.json`.

### Rol 4 — RabbitMQ: notificaciones y seguridad de tracking-service (final)

**Archivos:** `courier-service` → `rabbitmq/` (rellenar `NotificacionProducer`, crear `RabbitMQConfig`). `tracking-service` → `rabbitmq/` (`RabbitMQConfig`, `NotificacionConsumer`), `entidades/Notificacion`, `repositorio/NotificacionRepository`, `negocio/NotificacionService`, `rest/NotificacionController`, `dto/Notificacion*`, `security/` y `config/SecurityConfig`.
**Molde:** `DAWII-T1/ms-recargas/rabbitmq` (`RabbitMQConfig`, `RecargaProducer`) y `products-services/rabbitmq`.

- [ ] `RabbitMQConfig` en ambos servicios: cola `notificaciones.envio` durable y `Jackson2JsonMessageConverter`.
- [ ] `NotificacionProducer`: reemplazar el stub por `RabbitTemplate.convertAndSend` del `EnvioNotificacionEvent` (mantener la firma `publicar(...)`).
- [ ] `tracking-service`: `Notificacion` (+ repositorio), `NotificacionConsumer` con `@RabbitListener` que guarda la notificación para el `remitenteId`.
- [ ] `GET /notificaciones` (solo las del usuario del token, más recientes primero) y `PATCH /notificaciones/{id}/leida` (403 si no es suya).
- [ ] **Seguridad en tracking-service:** copiar `JwtService` y `JwtAuthenticationFilter` de `courier-service` **solo para validar** el token (mismo `app.jwt.secret`); reemplazar el `SecurityConfig` provisional exigiendo autenticación en `/tracking/**` y `/notificaciones/**`. Coordinar con el Rol 3: ambos trabajan en el mismo servicio, así que el Rol 3 sube primero su estructura y el Rol 4 hace `git pull` antes de crear la suya.
- [ ] **Tests:** `NotificacionRepositoryTest` (`@DataJpaTest`: guardar, listar por destinatario, marcar leída/actualizar, eliminar) y prueba del `NotificacionService`.
- [ ] Probar el flujo: pasar un envío a `ENTREGADO` → mensaje en la consola de RabbitMQ → fila en `notificacion`.
- [ ] Colección Postman `postman/rol4-notificaciones.json`.

### Rol 5 — Integración: Gateway, Eureka, datos de demo y QA (final)

**Archivos:** `backend/api-gateway`, `backend/eureka-server`, `docker/`, `postman/` (colección global), `config/EnvioDataInitializer` en courier-service, `README`/`docs`.
**Molde:** `spring_cloud/api-gateway` y `spring_cloud/eureka-server` del repo del curso.

- [ ] Arrancar `eureka-server` y `api-gateway` (ya traen configuración) y verificar que `courier-service` y `tracking-service` aparecen registrados en <http://localhost:8761>.
- [ ] Verificar y ajustar las rutas del gateway: `/auth/**`, `/users/**`, `/envios/**` → courier · `/tracking/**`, `/notificaciones/**` → tracking. Comprobar que **CORS no se duplica** (service + gateway) y que el header `Authorization` pasa.
- [ ] Probar que un `POST /auth/login` por el gateway (`:8762`) devuelve el mismo resultado que directo a `:8083`.
- [ ] `EnvioDataInitializer` (cuando el Rol 2 tenga `Envio`): ~8 envíos de ejemplo en distintos estados para las demos y pruebas del front.
- [ ] **Colección Postman global** `postman/courier-express.postman_collection.json` con variables (`{{baseUrl}}`, `{{token}}`) y un script que guarde el token tras el login; juntar las colecciones de los roles 1–4.
- [ ] **QA end-to-end**: recorrer el flujo completo (registro → login → crear envío → EN_RUTA → ENTREGADO → historial → notificación → anular un RECEPCIONADO) y abrir un **Issue** en GitHub por cada fallo, indicando el rol responsable.
- [ ] Verificar el arranque desde cero siguiendo la sección 3 de este README en una máquina limpia y corregir lo que falte.
- [ ] Revisar que no haya secretos ni archivos de build en el repo.

---

## 6. Frontend — Coordinador

Todo el trabajo de `frontend/courier-web` es del coordinador. Plantilla base: `hospital_web` (Angular 21 + Material + Bootstrap, standalone, zoneless, `HashLocationStrategy`).

**Adaptación de la plantilla**
- [ ] Renombrar la app (`package.json` `name`, título en `index.html`, logo y textos "Cliniva"/hospital).
- [ ] `environments/environment*.ts`: `apiUrl` apuntando a `http://localhost:8083` (Fase A) y luego al gateway `http://localhost:8762`.
- [ ] Sidebar: dejar Dashboard, Envíos, Seguimiento, Notificaciones y Cerrar sesión; quitar menús y módulos de hospital que no se usen (`multilevel`, `extra-pages`, dashboards de doctor/enfermera).

**Autenticación y roles**
- [ ] **Reemplazar `core/service/login.service.ts` simulado** (usuarios en memoria + JWT falso) por `HttpClient.post(apiUrl + '/auth/login')`; `AuthService` ya espera `token` y `user.roles[{name,priority}]`.
- [ ] Interceptor que añade `Authorization: Bearer <token>`; conservar el manejo del 401.
- [ ] `core/models/role.ts`: `ADMIN/DOCTOR/PATIENT` → `OPERADOR/CLIENTE`; en `app.routes.ts` renombrar `/admin` → `/operador` y `/doctor`+`/patient` → `/cliente`; actualizar `AuthGuard` y la redirección post-login según el rol.
- [ ] Pantalla de **registro** (`authentication/signup`) conectada a `POST /users`.

**Operador** (consume los 4 verbos: es lo que da los 6 pts de "consume todos")
- [ ] `EnvioService` de Angular: `GET` lista/uno, `POST`, `PUT`, `PATCH` estado, `DELETE`.
- [ ] **Formulario reactivo** de registro/edición de encomienda con validaciones y mensajes de error.
- [ ] **Tabla de envíos** (`mat-table`) con **badges de colores**: RECEPCIONADO gris · EN_RUTA azul · ENTREGADO verde · DEVUELTO naranja; filtro por estado y búsqueda por código.
- [ ] Acción "Cambiar estado" y **modal de confirmación al anular** (`MatDialog`, solo habilitado en `RECEPCIONADO`).

**Cliente**
- [ ] "Mis envíos" (tabla con badges) y consulta de seguimiento por código con **línea de tiempo** del historial.
- [ ] Campana/listado de **notificaciones** con marcar como leída.

**Transversal**
- [ ] Snackbars de éxito/error, estados de carga, manejo de errores del contrato.
- [ ] Probar con datos de prueba (`of([...])`) mientras el backend no esté listo; conectar al API real cuando cada rol suba su parte.
- [ ] Revisar los PRs de los demás (checklist abajo) y dar feedback.

---

## 7. Checklists

### Checklist de Pull Request (revisión del coordinador)
- [ ] Compila y `./gradlew test` pasa (o `ng build` en el front).
- [ ] Solo toca archivos del rol del autor.
- [ ] Respeta la estructura de paquetes, los nombres y el contrato de `docs/API-CONTRATO.md`.
- [ ] Validaciones y códigos HTTP correctos (400/401/403/404/409).
- [ ] Sin secretos, `build/`, `.gradle/` ni `node_modules/`.
- [ ] El autor puede explicar su código (se pregunta en la sustentación).

### Checklist técnico final (rúbrica)
- [ ] Login REST con usuario/clave en BD, **BCrypt** y validaciones
- [ ] Pruebas de acceso a datos: **insertar, actualizar, eliminar, listar**
- [ ] REST con **GET, POST, PUT y DELETE**
- [ ] Angular consume **todos** los servicios y persiste en BD
- [ ] Kafka: historial y auditoría funcionando
- [ ] RabbitMQ: notificaciones funcionando
- [ ] Gateway + Eureka + Feign funcionando
- [ ] Arranque desde cero siguiendo este README
