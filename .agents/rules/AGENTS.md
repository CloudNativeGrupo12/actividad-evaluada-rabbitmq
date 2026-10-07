# Reglas del repositorio — Sistema de Reservas de Mesas

## Contexto del proyecto

Sistema de reservas para un restaurante, construido con microservicios Java 21 + Spring Boot 3.5.7.
Monorepo Maven multi-módulo. Cada microservicio produce su propio `.jar`, corre en su propio puerto
y tiene su propia base de datos. La comunicación síncrona es HTTP REST; la asíncrona es RabbitMQ.

**Equipo:** Franco Garay, Deymon Gonzalez, Fernando Camus y Juan Carlos Tapia. **Sección:** 002D.

**Documentación existente (léela antes de modificar):**
- `docs/INFORME_DISENO.md` — Diseño original: microservicios, flujo, topología RabbitMQ.
- `docs/INFORME_IMPLEMENTACION.md` — Implementación actual, evidencias, limitaciones conocidas.
- `docs/ROADMAP.md` — Plan de desarrollo con fases y checklist.
- `docs/FRONTEND_SPEC.md` — Especificación del frontend Angular (endpoints, puertos, flujo MSAL).

---

## Estructura del monorepo backend

```
actividad-evaluada-rabbitmq/
├── pom.xml                          # POM padre (modules, Spring Boot parent)
├── contratos/                       # Módulo compartido: DTOs (records) y topología RabbitMQ
│   └── src/main/java/cl/reservas/contratos/
├── ms-reservas/                     # Productor RabbitMQ + coordinador (puerto 8080)
│   └── src/main/java/cl/reservas/reservas/
├── ms-disponibilidad/               # Gestión de mesas y asignaciones (puerto 8081)
│   └── src/main/java/cl/reservas/disponibilidad/
├── ms-notificaciones/               # Consumidor: envía correos por SMTP (puerto 8082)
│   └── src/main/java/cl/reservas/notificaciones/
├── ms-auditoria/                    # Consumidor: registra auditoría (puerto 8083)
│   └── src/main/java/cl/reservas/auditoria/
├── ms-admin-rabbitmq/               # (POR CREAR) Admin de colas/exchanges/bindings (puerto 8084)
├── compose.yaml                     # Docker Compose para desarrollo local
├── Dockerfile                       # Multi-stage build compartido por todos los módulos
└── docs/                            # Documentación del proyecto
```

---

## Convenciones de código

### Java
- **Java 21**, usar records para DTOs, text blocks para SQL multi-línea.
- **Package base:** `cl.reservas.<nombre-modulo>` (ej. `cl.reservas.reservas`, `cl.reservas.auditoria`).
- Cada microservicio tiene su clase `Application.java` con `@SpringBootApplication`.
- Los DTOs compartidos entre microservicios van en el módulo `contratos`.
- Los DTOs internos de un microservicio van dentro de su propio paquete.
- Usar `record` en lugar de clases con getters/setters para DTOs inmutables.
- Validación con anotaciones de Jakarta Validation (`@NotNull`, `@NotBlank`, `@Email`, etc.).
- Logging con SLF4J (`LoggerFactory.getLogger(...)`) — NO usar `System.out.println`.
- Formato de logs: `log.info("ACCION_EN_MAYUSCULAS campo={} campo={}", val1, val2)`.

### Spring Boot
- Configuración en `application.properties` (NO `.yml`).
- Variables de entorno para valores sensibles o de ambiente: `${VAR:default}`.
- Perfiles: `application.properties` (base), `application-local.properties` (H2), `application-cloud.properties` (PostgreSQL RDS).
- CORS debe configurarse en `SecurityConfig`, no en `@CrossOrigin` por controlador.

### RabbitMQ
- Topología declarada en `contratos/RabbitTopology.java` usando `Declarables`.
- Constantes de nombres de colas, exchanges y routing keys en `RabbitTopology`.
- Consumers con `basicAck` manual, `prefetch=1`, `default-requeue-rejected=false`.
- Mensajes JSON persistentes con `MessageDeliveryMode.PERSISTENT`.
- DLQ: Colas principales deben tener `x-dead-letter-exchange` y `x-dead-letter-routing-key`.

### Seguridad (Azure Entra ID)
- Usar `spring-boot-starter-oauth2-resource-server` para validar JWT.
- La validación se hace con JWKS de Azure (NO se programa manualmente la verificación de firma).
- `SecurityFilterChain` en cada microservicio: `/actuator/**` público, resto autenticado.
- NO crear un microservicio de autenticación; la autenticación la gestiona Azure Entra ID.

### Tests
- Tests unitarios con JUnit 5 + Mockito.
- Nombre de clase de test: `<Clase>Test.java` (ej. `ReservaServiceTest.java`).
- Cada microservicio debe tener al menos tests básicos que validen la lógica de negocio.

---

## Lo que NO debes hacer

- **NO eliminar** los informes de diseño e implementación existentes en `docs/`.
- **NO cambiar** el `groupId` (`cl.reservas`) ni la estructura de packages.
- **NO mezclar** lógica de diferentes microservicios en un mismo módulo.
- **NO subir** `target/`, `data/`, `.env`, `node_modules/`, `.angular/` ni `dist/` al repositorio.
- **NO usar** `WebClient` reactivo; este proyecto usa `RestClient` (Spring Boot 3 síncrono).
- **NO crear** un microservicio para autenticación; Azure Entra ID es el IDaaS.
- **NO eliminar** la topología existente de RabbitMQ; solo extenderla con DLQ y el admin.

---

## Puertos y servicios

| Microservicio       | Puerto | Rol                                     |
| :------------------ | :----- | :-------------------------------------- |
| ms-reservas         | 8080   | Productor RabbitMQ, coordinador         |
| ms-disponibilidad   | 8081   | Gestión de mesas (HTTP síncrono)        |
| ms-notificaciones   | 8082   | Consumidor: correos                     |
| ms-auditoria        | 8083   | Consumidor: auditoría                   |
| ms-admin-rabbitmq   | 8084   | Admin de colas/exchanges/bindings       |
| RabbitMQ            | 5672   | Broker AMQP                             |
| RabbitMQ Management | 15672  | UI de administración                    |
| Mailpit SMTP        | 1025   | Servidor SMTP de prueba                 |
| Mailpit UI          | 8025   | Buzón de correos de prueba              |
| Frontend Angular    | 4200   | Aplicación SPA (dev server)             |
