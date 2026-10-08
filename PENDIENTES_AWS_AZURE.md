# PENDIENTES PARA CONFIGURAR AWS Y AZURE (Fase 6)

## Estado
Servicios de AWS y Azure NO disponibles en este momento. Por ello, se han preparado los placeholders y configuraciones necesarias para completarlos posteriormente.

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
Ver `docs/FRONTEND_SPEC.md`. Repositorio frontend pendiente de creación según roadmap Fase 5.

## Notas actuales
- `compose.yaml` ya incluye `ms-admin-rabbitmq` (8084), `env_file: .env`, y `SPRING_PROFILES_ACTIVE` configurable.
- DLQ configurado en RabbitTopology (Fase 1) y consumidores con `DLQ_REDIRECT` + `basicNack` sin requeue.
- Build y tests pasan en local (perfil por defecto). Los servicios levantan con H2 cuando no hay credenciales cloud.
