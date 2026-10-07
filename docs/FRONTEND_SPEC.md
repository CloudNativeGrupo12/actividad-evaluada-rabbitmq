# Especificación del Frontend Angular — Sistema de Reservas

Documento de referencia para construir el frontend Angular del sistema de reservas.
Contiene los endpoints del backend, puertos, flujo de autenticación y estructura de componentes.

---

## Stack tecnológico

| Tecnología | Versión | Propósito |
| :--- | :--- | :--- |
| Angular / CLI | 22.0.7 | Framework SPA y CLI de generación |
| Node.js | 24.18.0 | Entorno de ejecución JavaScript |
| npm | 11.16.0 | Package Manager |
| @azure/msal-browser | 4.x / compatible | Autenticación PKCE con Azure Entra ID |
| @azure/msal-angular | 4.x / compatible | Integración MSAL con Angular (guards, interceptor) |
| Angular Material | 22.x | Componentes UI (opcional pero recomendado) |
| RxJS | 7.x / compatible | Manejo de flujos reactivos |

### Inicialización recomendada del proyecto
```bash
ng new frontend-reservas --routing --style=css --ssr=false
```
> En Angular v22 las aplicaciones son standalone por defecto. La opción `--ssr=false` asegura que sea una SPA pura para consumo de APIs y compatibilidad directa con MSAL en navegador.

---

## Flujo de autenticación (Authorization Code + PKCE)

```
┌──────────────┐    1. loginRedirect()     ┌──────────────────┐
│  Angular SPA │ ─────────────────────────▷ │ Microsoft Entra  │
│  (MSAL)      │                            │ ID (login.ms...) │
│              │ ◁───────────────────────── │                  │
│              │    2. Authorization Code   │  Tenant: {ID}    │
│              │                            └──────────────────┘
│              │    3. MSAL intercambia code
│              │       por Access Token (JWT)
│              │       usando PKCE (sin secret)
│              │
│              │    4. HttpInterceptor adjunta
│              │       Authorization: Bearer <JWT>
│              │
│              │ ─────────────────────────▷ ┌──────────────────┐
│              │    5. GET /api/reservas    │ Spring Boot API  │
│              │       + Bearer Token      │ (Resource Server)│
│              │                            │                  │
│              │ ◁───────────────────────── │ Valida JWT con   │
│              │    6. 200 OK + JSON        │ JWKS de Azure    │
└──────────────┘                            └──────────────────┘
```

### Configuración MSAL en Angular

```typescript
// environment.ts
export const environment = {
  production: false,
  msalConfig: {
    auth: {
      clientId: '<AZURE_SPA_CLIENT_ID>',        // App Registration: SPA (público)
      authority: 'https://login.microsoftonline.com/<TENANT_ID>',
      redirectUri: 'http://localhost:4200',
    },
    cache: {
      cacheLocation: 'localStorage',
    },
  },
  apiConfig: {
    scopes: ['api://<AZURE_API_CLIENT_ID>/access_as_user'],
    uri: 'http://localhost:8080',  // o URL del API Gateway
  },
};
```

### App Registrations en Azure Entra ID

| Registro | Tipo | Propósito |
| :--- | :--- | :--- |
| **ReservasApp-SPA** | SPA (público) | Frontend Angular. Sin client secret. Redirect URI: `http://localhost:4200`. |
| **ReservasApp-API** | Web | Backend Spring Boot. Expone scope `access_as_user`. Define `appRoles` si se necesitan (ADMIN, USER). |

---

## Endpoints del Backend

### ms-reservas (Puerto 8080)

| Método | Ruta | Body | Respuesta | Descripción |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/reservas` | `ReservaSolicitud` | `201` + Reserva creada | Crear una reserva |
| `GET` | `/reservas` | — | `200` + Lista de reservas | Listar todas las reservas |
| `GET` | `/reservas/{id}` | — | `200` + Reserva | Obtener reserva por ID |
| `POST` | `/reservas/{id}/publicar` | — | `200` + Reserva actualizada | Reintentar publicación a RabbitMQ |

**ReservaSolicitud (body del POST):**
```json
{
  "clienteId": "cli-010",
  "emailCliente": "cliente@example.com",
  "fechaReserva": "2026-10-15",
  "horaInicio": "18:00",
  "horaFin": "20:00",
  "cantidadPersonas": 4
}
```

**Respuesta exitosa (201):**
```json
{
  "reservaId": "uuid",
  "eventoId": "uuid",
  "clienteId": "cli-010",
  "emailCliente": "cliente@example.com",
  "mesaId": "mesa-05",
  "fechaReserva": "2026-10-15",
  "horaInicio": "18:00:00",
  "horaFin": "20:00:00",
  "cantidadPersonas": 4,
  "estado": "CONFIRMADA",
  "publicacion": "PUBLICADA"
}
```

### ms-disponibilidad (Puerto 8081)

| Método | Ruta | Body | Respuesta | Descripción |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/mesas` | — | `200` + Lista de mesas | Listar mesas del restaurante |
| `GET` | `/asignaciones` | — | `200` + Lista de asignaciones | Ver ocupación actual |
| `POST` | `/asignaciones` | `AsignacionSolicitud` | `200` + Asignación | Asignar mesa (uso interno) |
| `DELETE` | `/asignaciones/{reservaId}` | — | `200` | Liberar mesa (uso interno) |

**Mesas disponibles (respuesta GET /mesas):**
```json
[
  { "MESA_ID": "mesa-01", "CAPACIDAD": 2 },
  { "MESA_ID": "mesa-02", "CAPACIDAD": 4 },
  { "MESA_ID": "mesa-03", "CAPACIDAD": 4 },
  { "MESA_ID": "mesa-04", "CAPACIDAD": 6 },
  { "MESA_ID": "mesa-05", "CAPACIDAD": 8 }
]
```

### ms-notificaciones (Puerto 8082)

| Método | Ruta | Body | Respuesta | Descripción |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/notificaciones` | — | `200` + Lista | Notificaciones procesadas |

### ms-auditoria (Puerto 8083)

| Método | Ruta | Body | Respuesta | Descripción |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/auditoria` | — | `200` + Lista | Registros de auditoría |

### ms-admin-rabbitmq (Puerto 8084)

| Método | Ruta | Body | Respuesta | Descripción |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/admin/queues` | `{name, durable}` | `201` | Crear cola |
| `DELETE` | `/api/admin/queues/{name}` | — | `204` | Eliminar cola |
| `POST` | `/api/admin/exchanges` | `{name, type, durable}` | `201` | Crear exchange |
| `DELETE` | `/api/admin/exchanges/{name}` | — | `204` | Eliminar exchange |
| `POST` | `/api/admin/bindings` | `{queue, exchange, routingKey}` | `201` | Crear binding |
| `DELETE` | `/api/admin/bindings` | `{queue, exchange, routingKey}` | `204` | Eliminar binding |

### Endpoints públicos (sin JWT)

Todos los microservicios exponen:
- `GET /actuator/health` — Health check (`200` si el servicio está activo).

---

## Estructura de componentes Angular

```
src/
├── app/
│   ├── app.config.ts                  # Configuración standalone (providers, MSAL, HttpClient)
│   ├── app.routes.ts                  # Rutas con MsalGuard
│   ├── app.component.ts               # Layout principal (navbar + router-outlet)
│   │
│   ├── auth/                          # Módulo de autenticación
│   │   ├── msal.config.ts             # Configuración de MSAL (PublicClientApplication)
│   │   └── auth.interceptor.ts        # HttpInterceptorFn: adjunta Bearer token
│   │
│   ├── services/                      # Servicios HTTP
│   │   ├── reservas.service.ts        # CRUD contra ms-reservas (:8080)
│   │   ├── disponibilidad.service.ts  # GET mesas y asignaciones (:8081)
│   │   ├── notificaciones.service.ts  # GET notificaciones (:8082)
│   │   ├── auditoria.service.ts       # GET auditoría (:8083)
│   │   └── admin-rabbitmq.service.ts  # CRUD colas/exchanges/bindings (:8084)
│   │
│   ├── models/                        # Interfaces TypeScript (equivalentes a los DTOs Java)
│   │   ├── reserva.model.ts
│   │   ├── mesa.model.ts
│   │   ├── asignacion.model.ts
│   │   ├── notificacion.model.ts
│   │   ├── auditoria.model.ts
│   │   └── admin.model.ts
│   │
│   ├── pages/                         # Componentes de página (una por ruta)
│   │   ├── home/                      # Landing / Dashboard
│   │   ├── reservas/                  # Formulario + listado de reservas
│   │   ├── disponibilidad/            # Mesas y su ocupación
│   │   ├── notificaciones/            # Listado de correos procesados
│   │   ├── auditoria/                 # Registros de auditoría
│   │   └── admin-rabbitmq/            # Panel de administración de colas
│   │
│   └── shared/                        # Componentes reutilizables
│       ├── navbar/                    # Barra de navegación con login/logout
│       └── loading/                   # Spinner de carga
│
├── environments/
│   ├── environment.ts                 # Dev: localhost + IDs de Azure
│   └── environment.prod.ts            # Prod: URLs de API Gateway + IDs de Azure
│
├── index.html
├── main.ts
└── styles.css
```

---

## Rutas de la aplicación

| Ruta | Componente | Guard | Descripción |
| :--- | :--- | :--- | :--- |
| `/` | `HomeComponent` | — | Dashboard / landing |
| `/reservas` | `ReservasComponent` | `MsalGuard` | Crear y ver reservas |
| `/disponibilidad` | `DisponibilidadComponent` | `MsalGuard` | Ver mesas y ocupación |
| `/notificaciones` | `NotificacionesComponent` | `MsalGuard` | Correos procesados |
| `/auditoria` | `AuditoriaComponent` | `MsalGuard` | Registros de auditoría |
| `/admin` | `AdminRabbitmqComponent` | `MsalGuard` | Gestión de colas/exchanges |

---

## Interfaces TypeScript (Models)

```typescript
// reserva.model.ts
export interface ReservaSolicitud {
  clienteId: string;
  emailCliente: string;
  fechaReserva: string;       // formato: YYYY-MM-DD
  horaInicio: string;         // formato: HH:mm
  horaFin: string;            // formato: HH:mm
  cantidadPersonas: number;   // 1-8
}

export interface Reserva {
  reservaId: string;
  eventoId: string;
  clienteId: string;
  emailCliente: string;
  mesaId: string;
  fechaReserva: string;
  horaInicio: string;
  horaFin: string;
  cantidadPersonas: number;
  estado: string;
  publicacion: string;
}

// mesa.model.ts
export interface Mesa {
  MESA_ID: string;
  CAPACIDAD: number;
}

// admin.model.ts
export interface QueueRequest {
  name: string;
  durable: boolean;
}

export interface ExchangeRequest {
  name: string;
  type: 'direct' | 'fanout' | 'topic';
  durable: boolean;
}

export interface BindingRequest {
  queue: string;
  exchange: string;
  routingKey: string;
}
```

---

## Notas de implementación

1. **CORS:** El backend debe permitir `http://localhost:4200` (dev) y el dominio de producción. Esto se configura en `SecurityConfig.java` de cada microservicio con `CorsConfigurationSource`.
2. **Proxy en desarrollo:** Opcionalmente, configurar `proxy.conf.json` en Angular para redirigir `/api/*` al backend local y evitar problemas de CORS durante el desarrollo.
3. **API Gateway (producción):** En AWS, el API Gateway rutea todas las peticiones a los microservicios. El frontend solo necesita la URL del Gateway, no los puertos individuales.
4. **Manejo de errores:** El frontend debe manejar `401 Unauthorized` (token expirado → re-login), `409 Conflict` (mesa no disponible) y `503 Service Unavailable`.
