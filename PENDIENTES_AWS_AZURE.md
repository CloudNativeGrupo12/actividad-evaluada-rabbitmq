# PENDIENTES PARA CONFIGURAR AWS Y AZURE (Fase 6)

## Estado
La aplicación ya está desplegada en [AWS por HTTPS](https://6yyp6d2s6j.execute-api.us-east-1.amazonaws.com), con Angular, los cinco microservicios, RabbitMQ y Mailpit en EC2 y las cuatro bases en RDS. Login y reserva pública verificados. Consultar [DESPLIEGUE_AWS](docs/DESPLIEGUE_AWS.md); la lista histórica que sigue se conserva como antecedente.

Al 8 de octubre de 2026, Azure Entra ID tiene los registros API y SPA creados y aplicados al frontend y `.env` local; login, llamadas JWT desde Angular y sesión Azure CLI verificados. RDS `ep3-reservas-postgres` está disponible en `us-east-1`, con cuatro bases PostgreSQL 18.3 y los microservicios en perfil cloud. Se verificaron reserva, asignación, correo y auditoría, incluyendo persistencia después de reiniciar los cuatro microservicios. Usar los scripts, evidencias y configuración actual de [EP3](docs/EP3.md); los ejemplos de fases siguientes son antecedentes. Renovar las credenciales temporales al reiniciar el laboratorio y controlar el consumo de RDS.

## 1. Base de Datos PostgreSQL (AWS RDS)
Para activar el perfil `cloud` con PostgreSQL:

- Crear instancia RDS PostgreSQL (ej. `db.t3.micro`, PostgreSQL 15+)
- Configurar VPC, security groups y permitir acceso desde los servicios ECS/EC2 (o local para pruebas)
- Obtener endpoint: ej. `reserva-db.xxxxxx.us-east-1.rds.amazonaws.com`

Variables a completar en `.env` (o en variables de entorno del despliegue):
```env
SPRING_PROFILES_ACTIVE=cloud
DB_URL=jdbc:postgresql://<RDS_ENDPOINT>:5432/<DB_NAME>
DB_USER=<DB_USER>
DB_PASSWORD=<DB_PASSWORD>
```

Notas:
- Todos los microservicios ya tienen `application-cloud.properties` con `spring.jpa.hibernate.ddl-auto=update` y dialecto PostgreSQL.
- En `compose.yaml` se pasa `SPRING_PROFILES_ACTIVE` desde `.env` (por defecto `local`).

## 2. Autenticación OAuth2 (Azure Entra ID)
Requisitos para validar JWT en todos los microservicios:

- Crear App Registration en Azure Entra ID (Azure AD)
- Obtener Tenant ID y Client ID (Application ID)
- Configurar Allowed Token Audiences (aud) y Issuer según corresponde
- Para frontend (Angular + MSAL) se necesitará Client ID del SPA y redirect URIs

Variables a completar:
```env
AZURE_TENANT_ID=<AZURE_TENANT_ID>
AZURE_CLIENT_ID=<AZURE_CLIENT_ID>
```

Configuración ya aplicada:
- `spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.microsoftonline.com/${AZURE_TENANT_ID}/v2.0`
- `spring.security.oauth2.resourceserver.jwt.audiences=${AZURE_CLIENT_ID}`
- CORS global con `app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:4200}`

## 3. Despliegue en AWS
Pendientes para despliegue cloud:
- Docker images en ECR (o otro registry)
- ECS/Fargate o EC2 para correr contenedores
- Load Balancer para exponer servicios
- Configurar secrets en AWS Secrets Manager/Parameter Store para DB y Azure
- Actualizar `SPRING_PROFILES_ACTIVE=cloud` y variables en entorno AWS

## 4. Frontend (Angular + MSAL)
Frontend implementado en el repositorio hermano `frontend-reservas`; login preparado para los registros Entra ID actuales. Ver `docs/FRONTEND_SPEC.md` y `docs/EP3.md`.

## Notas actuales
- `compose.yaml` ya incluye `ms-admin-rabbitmq` (8084), `env_file: .env`, y `SPRING_PROFILES_ACTIVE` configurable.
- DLQ configurado en RabbitTopology (Fase 1) y consumidores con `DLQ_REDIRECT` + `basicNack` sin requeue.
- Build y tests pasan en local (perfil por defecto). Los servicios levantan con H2 cuando no hay credenciales cloud.
