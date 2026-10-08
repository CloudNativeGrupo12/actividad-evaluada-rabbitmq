# Cierre de entrega EP3

La implementación y sus evidencias se detallan en [EP3](EP3.md). Esta lista distingue la demostración técnica del envío formal. Estado revisado el 8 de octubre de 2026.

## Comprobado

- [x] Cinco microservicios Java/Spring Boot, compilación y 50 pruebas Java aprobadas.
- [x] Angular modular, build optimizado con URLs reales de la demo y seis pruebas aprobadas.
- [x] Login Microsoft y llamadas autenticadas a las cinco APIs; 401 sin JWT válido.
- [x] Cuatro bases PostgreSQL en RDS, con entidades y repositorios JPA; reserva y registros conservados tras reiniciar los microservicios.
- [x] Productor, dos consumidores por dominio, topología centralizada, beans explícitos y DLQ.
- [x] ACK posterior al procesamiento, reintentos transitorios acotados y NACK hacia DLQ ante mensajes inválidos.
- [x] Creación y eliminación de colas, exchanges y bindings desde Angular y servicio administrador separado.
- [x] Validación de parámetros y errores claros; pruebas del administrador.
- [x] `.env` y solicitudes AWS con secretos excluidos de Git y del contexto Docker.
- [x] `.env.example` usa H2 en perfil local; ejemplos HTTP reciben bearer desde una variable de entorno.
- [x] Revisar y publicar los cambios de ambos repositorios en `feature/ep3-rubrica`, incluyendo código, documentación y evidencias.

## Falta cerrar

- [ ] Verificar que el docente tenga acceso al código actualizado en GitHub.
- [ ] Confirmar los integrantes de la pareja: la pauta indica parejas y los informes históricos incluyen cuatro integrantes.
- [ ] Confirmar en AVA la fecha y hora vigentes; el PDF tiene edición 2025 y no proporciona un plazo concreto para esta entrega.
- [ ] Ensayar el recorrido siguiente y las explicaciones técnicas entre los dos integrantes.
- [ ] Entregar los enlaces actualizados en AVA y enviar copia al correo del docente.

La versión EP3 está publicada en [backend — rama EP3](https://github.com/CloudNativeGrupo12/actividad-evaluada-rabbitmq/tree/feature/ep3-rubrica) y [frontend — rama EP3](https://github.com/CloudNativeGrupo12/frontend-reservas/tree/feature/ep3-rubrica). Para entregar esta versión, usar esos enlaces directos a la rama. La integración a `main` se puede realizar posteriormente mediante pull requests.

## Guion para defender el proyecto

1. Explicar el caso de uso: se confirma una reserva tras asignar una mesa por HTTP; el correo y la auditoría se procesan mediante RabbitMQ. Mostrar los cinco dominios y las cuatro bases.
2. Iniciar sesión con Microsoft, crear una reserva desde Angular y mostrar mesa, CONFIRMADA y PUBLICADA. Explicar que el frontend envía un access token y las APIs validan firma, issuer, expiración y audience.
3. Mostrar el correo en Mailpit y el mismo `reservaId`/`eventoId` en auditoría. Mostrar las tablas cloud y la evidencia de persistencia tras reiniciar los servicios.
4. Detener únicamente notificaciones y auditoría; crear otra reserva y mostrar Ready=1 y consumers=0 en cada cola. Reiniciar los consumidores y mostrar los dos procesamientos. Tener los comandos de reinicio preparados antes de detenerlos.
5. Mostrar `RabbitTopology`: exchange direct `reservas.exchange`, routing key `reserva.confirmada`, una cola por dominio y exchange fanout `reservas.dlx` vinculado a `reservas.dlq`. Explicar por qué cada dominio recibe su propia copia.
6. Mostrar un mensaje inválido que termina en DLQ y el log `DLQ_REDIRECT`. Explicar ACK después del éxito, hasta tres intentos para errores transitorios y NACK sin requeue para errores permanentes o intentos agotados.
7. Crear y eliminar cola, exchange y binding con nombres `ep3.demo.*` desde el administrador. Mostrar un nombre inválido y el error de validación. Evitar usar los recursos de negocio para esta parte de la demo.
8. Explicar los límites documentados: no se promete exactamente una vez; un fallo de SMTP o ACK puede producir duplicados; el reintento de publicación y el reproceso de DLQ son manuales.

Las capturas y resultados están en `evidencias/ep3`. La secuencia de consumidores detenidos se comprobó inicialmente con H2; las evidencias `postgres*.json` y `*-rds-*.png` corresponden a RDS. Distinguir esas demostraciones al presentarlas.

## Preparación el día de la presentación

Iniciar el laboratorio AWS y comprobar que RDS esté disponible. La regla de acceso a PostgreSQL permite la IPv4 pública de la demostración; si la red cambia, revisar esa regla antes de la clase. Comprobar salud de las cinco APIs, login y lectura de reservas antes de comenzar. RDS permanece encendida y consume recursos/créditos del laboratorio.

Al repetir una operación administrativa con Azure CLI, Microsoft devolvió `InteractionRequired` por evaluación continua de acceso. Si aparece ese resultado, completar otra autenticación interactiva con `az login`; no modificar ni evitar la política de la institución. La configuración de la aplicación y las evidencias ya verificadas están conservadas.

El entorno de la evaluación usa Angular y APIs en localhost, con Azure Entra ID y RDS cloud. La pauta suministrada exige base de datos cloud y entrega del código por GitHub; no especifica hosting público de Angular ni de los microservicios.
