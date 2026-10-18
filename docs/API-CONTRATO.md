# Contrato de API, eventos y datos

Este documento es la **fuente de verdad** entre backend y frontend. Si algo cambia, se cambia aquí primero y se avisa al equipo.

Base URL: `http://localhost:8083` (courier-service directo, Fase A) o `http://localhost:8762` (API Gateway, Fase C).
Autenticación: cabecera `Authorization: Bearer <token>` en todo salvo `POST /auth/login` y `POST /users`.

## 1. Endpoints

### courier-service (puerto 8083)

| Método | Ruta | Rol | Cuerpo / Notas | Respuestas |
|---|---|---|---|---|
| POST | `/auth/login` | público | `{ username, password }` | 200 `LoginResponse` · 400 validación · 401 credenciales |
| POST | `/users` | público | Registro de cliente: `{ username, password, nombre, email }`. Crea siempre rol `ROLE_CLIENTE` | 201 `UserResponse` · 400 · 409 usuario repetido |
| GET | `/envios` | OPERADOR: todos · CLIENTE: solo los suyos | Filtro opcional `?estado=EN_RUTA` | 200 `[EnvioResponse]` |
| GET | `/envios/{id}` | ambos | CLIENTE solo si es suyo | 200 · 403 · 404 |
| GET | `/envios/tracking/{codigo}` | ambos | CLIENTE solo si es suyo | 200 · 403 · 404 |
| POST | `/envios` | OPERADOR | `EnvioRequest`. Genera `codigoTracking`, calcula `costoEnvio`, estado inicial `RECEPCIONADO` | 201 · 400 |
| PUT | `/envios/{id}` | OPERADOR | `EnvioRequest` (edita datos, **no** el estado) | 200 · 400 · 404 · 409 si ya no está `RECEPCIONADO` |
| PATCH | `/envios/{id}/estado` | OPERADOR | `{ "estado": "EN_RUTA" }`. Publica eventos (ver §2) | 200 · 404 · 409 transición inválida |
| DELETE | `/envios/{id}` | OPERADOR | Anula (elimina) el envío; solo si está `RECEPCIONADO` | 204 · 404 · 409 |

### tracking-service (puerto 8084)

| Método | Ruta | Rol | Notas | Respuestas |
|---|---|---|---|---|
| GET | `/tracking/{codigo}/historial` | ambos | Devuelve datos del envío (vía Feign) + lista del historial ordenada por fecha | 200 · 404 |
| GET | `/notificaciones` | CLIENTE | Las del usuario del token, más recientes primero | 200 `[NotificacionResponse]` |
| PATCH | `/notificaciones/{id}/leida` | CLIENTE | Marca como leída (solo si es suya) | 200 · 403 · 404 |

## 2. Eventos de mensajería

| Canal | Nombre | Quién publica | Cuándo | Consumidores |
|---|---|---|---|---|
| **Kafka** | tópico `tracking-status` (clave = `codigoTracking`) | courier-service | Al registrar el envío (`RECEPCIONADO`) y cuando pasa a `EN_RUTA` o `ENTREGADO` (y `DEVUELTO`) | tracking-service: `historial-cg` (guarda historial) y `audit-cg` (log de auditoría) |
| **RabbitMQ** | cola `notificaciones.envio` | courier-service | Cuando pasa a `ENTREGADO` o `DEVUELTO` | tracking-service: crea `Notificacion` para el remitente |

```java
// Kafka
record TrackingStatusEvent(String codigoTracking, String estado, Long remitenteId, String usuario, Instant timestamp)
// RabbitMQ
record EnvioNotificacionEvent(String codigoTracking, Long remitenteId, String estado, String mensaje)
```

Los records ya existen en ambos servicios (paquetes `kafka` y `rabbitmq`). **No cambiar campos sin avisar.**

## 3. Formas JSON

```jsonc
// LoginResponse  (la forma que ya espera el AuthService de Angular)
{
  "token": "eyJhbGciOi...",
  "user": {
    "id": 1, "username": "operador", "name": "Operador Demo", "email": "op@courier.pe",
    "roles": [ { "name": "OPERADOR", "priority": 1 } ],     // OPERADOR | CLIENTE (sin el prefijo ROLE_)
    "permissions": ["canAdd", "canEdit", "canDelete", "canRead"]   // CLIENTE: ["canRead"]
  }
}

// EnvioRequest
{ "destinatarioNombre": "Ana Torres", "direccionDestino": "Av. Lima 123, Miraflores", "pesoKg": 2.5 }
//   Validaciones: destinatarioNombre y direccionDestino obligatorios; pesoKg > 0
//   remitenteId: lo asigna el servidor (CLIENTE: él mismo). Si el OPERADOR registra, envía además "remitenteId".

// EnvioResponse
{
  "id": 10, "codigoTracking": "EN-2026-000010", "remitenteId": 2, "remitenteNombre": "Cliente Uno",
  "destinatarioNombre": "Ana Torres", "direccionDestino": "Av. Lima 123, Miraflores",
  "pesoKg": 2.5, "costoEnvio": 17.50, "estado": "RECEPCIONADO", "fechaRegistro": "2026-10-18T10:30:00"
}

// Historial (GET /tracking/{codigo}/historial)
{
  "envio": { /* EnvioResponse */ },
  "historial": [ { "estado": "RECEPCIONADO", "fecha": "2026-10-18T10:30:00", "usuario": "operador" } ]
}

// NotificacionResponse
{ "id": 5, "codigoTracking": "EN-2026-000010", "mensaje": "Tu envío fue entregado", "fecha": "2026-10-18T15:00:00", "leida": false }

// Error uniforme (todos los 4xx)
{ "status": 409, "message": "Transición de estado no permitida", "errors": [] }
```

## 4. Reglas de negocio

- `costoEnvio = 8.00 + (pesoKg × 3.80)` (constantes en `application.yml` o en el servicio; redondeo a 2 decimales).
- `codigoTracking = "EN-" + año + "-" + id con 6 dígitos` (ej. `EN-2026-000010`).
- Transiciones válidas: `RECEPCIONADO → EN_RUTA → ENTREGADO` y `EN_RUTA → DEVUELTO`. Cualquier otra → 409.
- Un CLIENTE solo ve y consulta envíos cuyo `remitenteId` es su `id`.
- Usuarios de prueba (`DataInitializer`): `operador / operador123` (ROLE_OPERADOR), `cliente1 / cliente123` y `cliente2 / cliente123` (ROLE_CLIENTE).

## 5. Modelo de datos

| Servicio | Tabla | Campos |
|---|---|---|
| courier-service (`courier_db`) | `app_user` | `id, username (único), password (BCrypt), nombre, email, rol` |
| courier-service | `envio` | `id, codigo_tracking (único), remitente_id → app_user, destinatario_nombre, direccion_destino, peso_kg, costo_envio, estado, fecha_registro` |
| tracking-service (`tracking_db`) | `historial_tracking` | `id, codigo_tracking, estado, fecha, usuario` |
| tracking-service | `notificacion` | `id, codigo_tracking, destinatario_id, mensaje, fecha, leida` |

## 6. Puertos

| Componente | Puerto |
|---|---|
| Angular (`ng serve`) | 4200 |
| API Gateway | 8762 |
| Eureka | 8761 |
| courier-service | 8083 |
| tracking-service | 8084 |
| MySQL / phpMyAdmin | 5510 / 3410 |
| Kafka / Kafka UI | 9092 / 8090 |
| RabbitMQ / consola | 5672 / 15672 |
