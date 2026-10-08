# Roadmap — Evolución del Sistema de Reservas

Plan de desarrollo para cumplir los requisitos de la evaluación parcial.
Cada fase indica los archivos a crear o modificar y el criterio de aceptación.

> **Despliegue entregable:** [Angular y las cinco APIs en AWS por HTTPS](https://6yyp6d2s6j.execute-api.us-east-1.amazonaws.com), con login Microsoft, RabbitMQ y RDS comprobados. Ver [DESPLIEGUE_AWS](DESPLIEGUE_AWS.md) para arquitectura, scripts y evidencias públicas.

> **Estado al 8 de octubre de 2026:** Backend con cinco microservicios, JPA, JWT y DLQ; frontend Angular implementado; login real con Entra ID y Azure CLI verificados. Maven package y 50 pruebas Java pasan, Angular compila y sus seis pruebas pasan. Compose y DLQ verificados. RDS PostgreSQL tiene cuatro bases activas; una reserva real desde Angular se verificó en todas ellas y persistió después de reiniciar los cuatro microservicios. La [matriz EP3](EP3.md) es la referencia para la pauta oficial. Las listas originales que siguen son antecedentes y no representan por sí solas el estado de implementación.

---

## Fase 1 · RabbitMQ: DLQ + Microservicio Administrador

**Objetivo:** Cumplir los requisitos de mensajería avanzada de la rúbrica.

### 1.1 Dead Letter Queue (DLQ) en colas existentes

- [ ] **Modificar** `contratos/src/.../RabbitTopology.java`:
  - Crear `reservas.dlx` (Dead Letter Exchange, tipo `fanout`).
  - Crear `reservas.dlq` (cola única para mensajes fallidos).
  - Binding: `reservas.dlq` → `reservas.dlx`.
  - Agregar argumentos DLX a las colas principales:
    - `reservas.notificaciones.queue` → `x-dead-letter-exchange: reservas.dlx`
    - `reservas.auditoria.queue` → `x-dead-letter-exchange: reservas.dlx`
  - Agregar constantes: `DLX`, `DLQ`, `DLQ_ROUTING_KEY`.
- [ ] **Modificar** `ms-notificaciones/src/.../ReservaConsumer.java`:
  - Eliminar el comentario `// Sin DLQ ni retries complejos`.
  - En el `catch`: `log.error("DLQ_REDIRECT ...")` antes de `basicNack(tag, false, false)`.
  - El `basicNack` sin requeue + el argumento DLX en la cola hace que RabbitMQ envíe automáticamente a la DLQ.
- [ ] **Modificar** `ms-auditoria/src/.../ReservaConsumer.java`:
  - Mismo cambio que en notificaciones.
- [ ] **Verificar** que al enviar un mensaje con formato inválido, aparezca en la DLQ y en los logs.

### 1.2 Microservicio Administrador de RabbitMQ

- [ ] **Crear** módulo `ms-admin-rabbitmq/` con su propio `pom.xml`.
- [ ] **Agregar** `<module>ms-admin-rabbitmq</module>` al `pom.xml` raíz.
- [ ] **Crear** archivos del microservicio:
  - `src/main/java/cl/reservas/admin/Application.java`
  - `src/main/java/cl/reservas/admin/RabbitAdminConfig.java` — Bean `RabbitAdmin`.
  - `src/main/java/cl/reservas/admin/AdminController.java` — Endpoints REST:
    - `POST /api/admin/queues` — Crear cola (body: `{name, durable, args}`).
    - `DELETE /api/admin/queues/{name}` — Eliminar cola.
    - `POST /api/admin/exchanges` — Crear exchange (body: `{name, type, durable}`).
    - `DELETE /api/admin/exchanges/{name}` — Eliminar exchange.
    - `POST /api/admin/bindings` — Crear binding (body: `{queue, exchange, routingKey}`).
    - `DELETE /api/admin/bindings` — Eliminar binding (body: `{queue, exchange, routingKey}`).
  - `src/main/resources/application.properties` — Puerto `8084`, conexión RabbitMQ.
- [ ] **Agregar** servicio al `compose.yaml` en puerto `8084`.
- [ ] **Crear** tests básicos (`AdminControllerTest.java`).

**Criterio:** `mvn clean test` pasa. Se puede crear y eliminar una cola via REST.

---

## Fase 2 · Persistencia: Migración a JPA + PostgreSQL Cloud

**Objetivo:** Cumplir la integración con base de datos cloud mediante entidades y repositorios.

### 2.1 Dependencias y configuración

- [x] **Agregar** a cada `pom.xml` de microservicio:
  - `spring-boot-starter-data-jpa`
  - `org.postgresql:postgresql` (scope `runtime`)
  - `org.projectlombok:lombok` (scope `provided`, opcional)
  - Mantener `com.h2database:h2` (scope `runtime`) para perfil local.
- [x] **Crear** perfil `application-cloud.properties` en cada microservicio:
  ```properties
  spring.datasource.url=${DB_URL}
  spring.datasource.username=${DB_USER}
  spring.datasource.password=${DB_PASSWORD}
  spring.jpa.hibernate.ddl-auto=update
  spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
  ```
- [x] **Crear** perfil `application-local.properties` en cada microservicio (H2 embebido para dev).

### 2.2 Entidades JPA y Repositorios

- [x] **ms-reservas:** Crear `Reserva.java` (`@Entity`), `ReservaJpaRepository.java`.
  - Refactorizar `ReservaRepository.java` (actualmente JDBC) para usar JPA.
- [x] **ms-disponibilidad:** Crear `Mesa.java`, `Asignacion.java` (`@Entity`), repositorios JPA.
  - Refactorizar `DisponibilidadService.java` de JDBC a JPA.
- [x] **ms-notificaciones:** Crear `Procesado.java` (`@Entity`), `ProcesadoRepository.java`.
  - Refactorizar `RegistroRepository.java` de JDBC a JPA.
- [x] **ms-auditoria:** Crear `Procesado.java` (`@Entity`), `ProcesadoRepository.java`.
  - Refactorizar `RegistroRepository.java` de JDBC a JPA.

**Criterio:** `mvn clean test` pasa con perfil local (H2). Conexión a PostgreSQL RDS funciona con perfil cloud.

---

## Fase 3 · Seguridad: OAuth2 Resource Server (Azure Entra ID)

**Objetivo:** Validar JWT en cada microservicio para autorizar peticiones.

### 3.1 Dependencias

- [x] **Agregar** a cada `pom.xml` de microservicio:
  - `spring-boot-starter-security`
  - `spring-boot-starter-oauth2-resource-server`
  - `spring-security-test` (scope `test`)

### 3.2 Configuración de seguridad

- [x] **Crear** en cada microservicio `SecurityConfig.java`:
  ```java
  @Configuration @EnableWebSecurity
  public class SecurityConfig {
      @Bean
      public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
          return http
              .csrf(csrf -> csrf.disable())
              .cors(Customizer.withDefaults())
              .authorizeHttpRequests(auth -> auth
                  .requestMatchers("/actuator/**").permitAll()
                  .anyRequest().authenticated()
              )
              .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
              .build();
      }
  }
  ```
- [x] **Agregar** propiedades JWT a `application.properties` de cada microservicio:
  ```properties
  spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.microsoftonline.com/${AZURE_TENANT_ID}/v2.0
  spring.security.oauth2.resourceserver.jwt.audiences=${AZURE_CLIENT_ID}
  ```
- [x] **Configurar** CORS global permitiendo el origen del frontend Angular.
- [x] **Actualizar** tests existentes para inyectar contexto de seguridad (`@WithMockUser` o JWT mock).

**Criterio:** Peticiones sin token reciben `401`. Peticiones con JWT válido de Azure reciben respuesta normal.

---

## Fase 4 · Pruebas y .gitignore

**Objetivo:** Código compilable, testeable y limpio para entrega.

- [x] **Crear** tests unitarios faltantes:
  - `ms-notificaciones/src/test/.../NotificacionServiceTest.java`
  - `ms-auditoria/src/test/.../AuditoriaServiceTest.java`
  - `ms-admin-rabbitmq/src/test/.../AdminControllerTest.java`
- [x] **Verificar** que los tests existentes (`DisponibilidadTest`, `ReservaServiceTest`) sigan pasando.
- [x] **Ampliar** `.gitignore` del backend:
  ```gitignore
  **/target/
  **/data/
  .env
  *.iml
  .idea/
  .vscode/
  .settings/
  .project
  .classpath
  **/*.log
  ```
- [x] **Ejecutar** `mvn clean test` — debe ser `BUILD SUCCESS` sin errores.

**Criterio:** 0 fallos, 0 errores. `.gitignore` excluye artefactos de compilación e IDE.

---

## Fase 5 · Frontend Angular + MSAL

**Objetivo:** Aplicación Angular con autenticación Azure Entra ID y vistas funcionales.

Ver especificación completa en [FRONTEND_SPEC.md](FRONTEND_SPEC.md).

- [ ] **Inicializar** proyecto Angular (Angular 22+, CLI 22.0.7, Node 24.18.0, npm 11.16.0).
- [ ] **Configurar** MSAL (`@azure/msal-browser`, `@azure/msal-angular`):
  - Flujo Authorization Code + PKCE.
  - `MsalGuard` en rutas protegidas.
  - `MsalInterceptor` para adjuntar `Bearer <token>` en peticiones HTTP.
- [ ] **Crear** vistas funcionales:
  - Login/Logout y estado del usuario.
  - Formulario para crear reserva y listado de reservas.
  - Panel de disponibilidad de mesas.
  - Panel de administración de RabbitMQ (crear/eliminar colas, exchanges, bindings).
  - Vista de auditoría y notificaciones procesadas.
- [ ] **Configurar** `.gitignore` para Angular:
  ```gitignore
  node_modules/
  .angular/
  dist/
  .env
  ```
- [ ] **Crear** repositorio en GitHub para el frontend.

**Criterio:** `ng build` sin errores. Login con Azure Entra ID funciona. Vistas muestran datos del backend.

---

## Fase 6 · Contenedorización y Entrega

**Objetivo:** Todo listo para Docker Compose, despliegue en AWS y entrega al docente.

- [ ] **Actualizar** `compose.yaml` con:
  - Servicio `ms-admin-rabbitmq` (puerto 8084).
  - Variables de entorno para PostgreSQL y Azure.
  - Perfil `cloud` activable con `SPRING_PROFILES_ACTIVE=cloud`.
- [ ] **Actualizar** `Dockerfile` si es necesario para el nuevo módulo.
- [ ] **Verificar** `docker compose up -d --build` — todos los servicios `UP`.
- [ ] **Preparar** enlaces de GitHub:
  - Repo 1 (Backend): `https://github.com/CloudNativeGrupo12/actividad-evaluada-rabbitmq`
  - Repo 2 (Frontend): `https://github.com/CloudNativeGrupo12/<nombre-frontend>`
- [ ] **Enviar** enlaces a AVA y correo del docente.

**Criterio:** Los dos repositorios están públicos, compilables y funcionales.
