# Preparación del sistema de reservas para la Evaluación Parcial 3

La Evaluación Parcial 3 de DSY1107 exige incorporar RabbitMQ al sistema existente y entregar backend Java/Spring Boot y frontend Angular mediante GitHub. El encargo tiene ponderación del 12%, modalidad en parejas y dos semanas de elaboración y presentación. El documento entregado tiene edición 2025; la fecha concreta de entrega debe confirmarse en AVA.

La fuente es `EP3_DSY1107_Estudiante_encargo.pdf`, instrucciones específicas en su página numerada 2 y pauta en las páginas numeradas 3 a 6. Los informes anteriores corresponden a una actividad previa con RabbitMQ y se conservan como antecedentes.

La pauta indica entrega en parejas y los informes anteriores registran cuatro integrantes. Confirmar con el docente los integrantes de esta entrega; no se modificó la autoría histórica.

El [cierre de entrega y guion de presentación](ENTREGA_EP3.md) reúne los pendientes formales, el recorrido sugerido y las comprobaciones previas a la clase.

## Indicadores de la pauta y su implementación

| Indicador | Peso | Código o evidencia |
|---|---:|---|
| Centralizar nombres de colas, exchanges y bindings | 12% | Constantes de `contratos/RabbitTopology`; ningún nombre de la topología se define en los servicios de negocio. |
| Declarar beans Queue, Exchange y Binding | 13% | Ocho beans explícitos en `RabbitTopology`: tres colas, dos exchanges y tres bindings. `RabbitTopologyTest` comprueba las rutas y DLQ. |
| Separar mensajería de la lógica de negocio | 10% | Topología en configuración, publicación en `ReservaPublisher`, política de retry en `ConsumerRetryConfig`, casos de uso en sus servicios. |
| Consumidores con RabbitListener y organización por dominio | 15% | Un `ReservaConsumer` por módulo de notificaciones y auditoría. |
| ACK y manejo explícito de errores | 20% | ACK después del procesamiento. JSON o contrato inválido: NACK sin requeue hacia DLQ. Errores transitorios: hasta tres intentos, separados por 250 ms, luego DLQ. Fallos de ACK se propagan sin intentar un segundo settlement. |
| Endpoints REST del administrador | 13% | `/api/admin/queues`, `/exchanges` y `/bindings`: POST 201 y DELETE 204. Errores 400, 404, 409 o 503 según causa. |
| Administración encapsulada en un servicio | 10% | `RabbitAdminService`; el controlador recibe DTO y delega. |
| Validación de parámetros | 7% | Jakarta Validation y validación de argumentos: nombres, prefijo reservado, tipos de exchange, claves, TTL y límites. Errores con `ProblemDetail`. |
| **Total** | **100%** | Estos pesos no incluyen por separado los requisitos generales, que también son obligatorios. |

## Requisitos generales

- Java 21 y Spring Boot 3.5.7: `mvn clean package` compila y ejecuta las pruebas.
- Angular 22: `npm ci`, `npm run build` y `npm test -- --watch=false` desde `frontend-reservas/frontend-reservas`.
- Cada dominio tiene entidades JPA, repositorios y una base propia. El perfil `cloud` recibe PostgreSQL por variables de entorno.
- Microsoft Entra ID gestiona login; MSAL solicita `access_as_user`, procesa el redirect y adjunta el access token. Cada API valida firma, issuer, expiración y audience del JWT con Spring Security.
- `.gitignore` excluye datos locales, dependencias, build y secretos. El `.env` no se entrega en GitHub.
- Código, documentación y evidencias publicados en la rama `feature/ep3-rubrica` de ambos repositorios; enlaces directos en [cierre de entrega](ENTREGA_EP3.md). Falta entregar esos enlaces en AVA y enviar copia al correo del docente.

## Configuración Azure mediante CLI

```powershell
.\scripts\configurar-azure.ps1
```

El script crea o reutiliza `ReservasApp-API` y `ReservasApp-SPA`, configura tokens v2, publica `access_as_user`, preautoriza la SPA y registra `http://localhost:4200` como redirect SPA. No crea client secrets. Aplica los identificadores al frontend y al `.env` local. El 8 de octubre de 2026 se crearon los registros y se verificó el login real con Microsoft y las llamadas de Angular a las cinco APIs mediante JWT.

El script actualiza también identificadores ya rellenados, para permitir configurar un clon en otro tenant. Si Azure CLI solicita autenticación adicional mediante `InteractionRequired`, completar el login interactivo antes de administrar los registros.

Para tokens v2 de esta API, el audience esperado por el backend es el Application ID de la API, mientras que el scope solicitado es `api://<Application ID>/access_as_user`. No usar un ID token como bearer para la API.

## Configuración PostgreSQL en AWS mediante CLI

Las credenciales deben superar `aws sts get-caller-identity`. En AWS Academy, renovar también `aws_session_token` cuando se inicia o reinicia el laboratorio. No guardar claves AWS en el repositorio.

```powershell
.\scripts\configurar-aws.ps1 -SoloConsultar
.\scripts\configurar-aws.ps1
```

El segundo comando crea una instancia RDS PostgreSQL `ep3-reservas-postgres`, `db.t3.micro`, con 20 GiB gp3 cifrados, una zona y retención de backup de un día. **RDS genera cargos mientras exista**; no se presupone que sea gratuito. El acceso de demostración se restringe al puerto 5432 desde la IP pública actual `/32`, dentro de la VPC por defecto. Para un backend desplegado en AWS, usar acceso privado desde su security group.

El script puede recibir `-Region`, `-Profile` y `-ClientCidr`. Si encuentra una instancia existente, la consulta sin cambiar su contraseña. Después de que su estado sea `available`, ejecutar nuevamente para escribir las cuatro URLs JDBC en `.env` y luego:

```powershell
uv run --with 'psycopg[binary]' python scripts/inicializar-postgres.py
docker compose up -d --build
```

El inicializador crea `reservas`, `disponibilidad`, `notificaciones` y `auditoria`, verifica las cuatro conexiones TLS y solo entonces activa `SPRING_PROFILES_ACTIVE=cloud`.

Después de crear una reserva desde Angular, comprobar su persistencia y la correlación del evento entre las cuatro bases:

```powershell
uv run --with 'psycopg[binary]' python scripts/verificar-postgres.py --reserva-id <UUID_RESERVA>
docker compose restart ms-reservas ms-disponibilidad ms-notificaciones ms-auditoria
uv run --with 'psycopg[binary]' python scripts/verificar-postgres.py --reserva-id <UUID_RESERVA> --despues-reinicio
```

El verificador consulta las tablas reales, exige TLS, compara el `eventoId` de reserva/correo/auditoría y guarda `postgres.json` y `postgres-persistencia.json` sin contraseñas. Las reservas de las demostraciones H2 se conservan en los volúmenes locales; la demostración cloud usa bases nuevas en RDS.

## Ejecución y demostración

1. Iniciar Docker Desktop y levantar `docker compose up -d --build` en el backend. Se ejecutan cinco APIs, RabbitMQ y Mailpit. Comprobar `/actuator/health` de los puertos 8080 a 8084.
2. Ejecutar `npm start` en el frontend, abrir `http://localhost:4200`, iniciar sesión con Microsoft y crear una reserva.
3. Mostrar su mesa, estado de publicación, correo en `http://localhost:8025` y registro de auditoría. Correlacionar la operación con `reservaId` y `eventoId`.
4. Detener solo los consumidores, crear otra reserva y mostrar un mensaje Ready en cada cola. Reiniciarlos y mostrar procesamiento y ACK. La reserva no espera al correo ni a la auditoría.
5. En `/admin`, crear una cola y un exchange de prueba, vincularlos y eliminarlos. Usar nombres `ep3.demo.*`; conservar los recursos de negocio.
6. Ejecutar `python scripts/verificar-dlq.py`: JSON inválido, dos rechazos, dos mensajes en DLQ y logs `DLQ_REDIRECT`.
7. Mostrar 401 sin bearer y una petición autorizada desde Angular. Mostrar PostgreSQL cloud y sus tablas con `verificar-postgres.py`; reiniciar los microservicios y comprobar que la reserva persiste.

Para usar el verificador HTTP, definir `RESERVAS_ACCESS_TOKEN` localmente con un access token de la SPA; no compartirlo ni guardarlo en Git. Ejecutar `python scripts/verificar.py`. Las nuevas evidencias se escriben en `evidencias/ep3`, sin reemplazar las evidencias históricas. El script de DLQ usa las credenciales de RabbitMQ de demostración o las variables `RABBIT_USER` y `RABBIT_PASSWORD`.

## Límites que se deben poder explicar

La demo usa una instancia de cada servicio; el bloqueo de asignación no garantiza coordinación entre múltiples instancias. El evento pendiente se reintenta con `POST /reservas/{id}/publicar`; no hay despachador automático de outbox ni transacción conjunta DB/broker. Un fallo SMTP o de ACK puede provocar duplicados; no se promete procesamiento exactamente una vez. La DLQ conserva eventos fallidos y su revisión/reproceso es manual.

La compilación, las pruebas y una configuración cloud preparada son evidencias diferentes de un login real, un recorrido completo por RabbitMQ y una conexión real a RDS. Conservar esa distinción al presentar.

## Verificación del 8 de octubre de 2026

`mvn package` generó los JAR de los cinco microservicios: 50 pruebas Java, cero errores y cero fallos. Angular compiló y pasó seis pruebas, incluidas las regresiones para los contratos de asignaciones, notificaciones y auditoría. Compose construyó y levantó los siete contenedores; las cinco APIs respondieron UP.

Se verificó login con Microsoft, lectura de mesas, reserva confirmada y publicada desde Angular, correo en Mailpit, auditoría del mismo evento, creación y eliminación de cola/exchange/binding desde la SPA, 401 sin token y con token inválido, y dos copias de un JSON inválido en DLQ con sus logs. Una segunda reserva se confirmó con ambos consumidores detenidos: cada cola conservó un mensaje Ready y cero consumers antes de reiniciarlos.

Después de renovar las credenciales temporales del laboratorio, AWS STS validó la sesión y se creó `ep3-reservas-postgres` en `us-east-1`: PostgreSQL 18.3, `db.t3.micro`, 20 GiB gp3 cifrados. El security group permite exclusivamente TCP 5432 desde una IPv4 `/32`. También se renovó y verificó el login de Azure CLI en Azure for Students.

Las cuatro bases están activas y los microservicios usan el perfil `cloud`. La reserva `53c82380-1215-4002-9a78-0725af0cdf6f`, creada desde Angular, quedó CONFIRMADA y PUBLICADA, con asignación `mesa-02`, correo en Mailpit y auditoría. Las consultas SQL verificaron el evento `d6ef5135-493b-4573-a369-3267d589d38f` en reserva, notificaciones y auditoría, y conexiones TLS 1.3. Después de reiniciar los cuatro microservicios se verificaron nuevamente todos los registros sin duplicados.

Las capturas, topología y logs están en `evidencias/ep3`. La demostración inicial usa H2 persistente; `reserva-rds-angular.png`, `postgres.json`, `postgres-persistencia.json`, `aws-rds.json`, `salud-cloud.json`, `correo-rds.json` y `cloud-logs.txt` corresponden a la demostración real sobre RDS. La instancia queda encendida y consume recursos/créditos del laboratorio mientras exista.

La revisión de entrega corrigió las URLs de ejemplo del build optimizado de Angular, los valores PostgreSQL activos en `.env.example` para perfil local y los headers bearer de `scripts/reservas.http`. El build optimizado volvió a compilar correctamente y `docker compose --env-file .env.example config` confirmó H2 para los cuatro dominios en perfil local.

## Referencias técnicas

- [Configuración de aplicaciones con Azure CLI](https://learn.microsoft.com/en-us/cli/azure/ad/app?view=azure-cli-latest).
- [Creación de RDS mediante AWS CLI](https://docs.aws.amazon.com/cli/latest/reference/rds/create-db-instance.html).
- [Manejo de fallos con Spring AMQP](https://docs.spring.io/spring-amqp/reference/amqp/resilience-recovering-from-errors-and-broker-failures.html).
