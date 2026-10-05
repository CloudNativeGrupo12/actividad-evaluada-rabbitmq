# Capturas de la implementación

Estas capturas complementan los JSON y logs generados por `scripts/verificar.py`. Utiliza una misma reserva y muestra su `reservaId` o `eventoId` para conectar las evidencias.

## 1 Exchange y bindings

Abre http://localhost:15672 e ingresa con `reservas` / `reservas-demo`. En **Exchanges**, abre `reservas.exchange`. Captura el tipo `direct`, su durabilidad y los dos bindings con `reserva.confirmada` dirigidos a las colas de auditoría y notificaciones.

## 2 Mensajes pendientes sin consumers

En la terminal del proyecto:

```sh
docker compose stop ms-notificaciones ms-auditoria
```

Crea una reserva con una fecha y un horario libres usando el ejemplo de PowerShell del README. En Management, abre **Queues and Streams**. Espera a que las estadísticas muestren al menos un mensaje **Ready** por cola y **Consumers = 0**. Captura ambas colas. Guarda también la respuesta de la creación: debe ser `201`, `CONFIRMADA` y `PUBLICADA` aun cuando los consumidores están detenidos.

## 3 Mensajes consumidos

```sh
docker compose start ms-notificaciones ms-auditoria
```

Espera a que los consumidores arranquen. Vuelve a la vista de colas y captura los dos consumers activos y las colas sin mensajes pendientes. Las cifras de mensajes pueden tardar unos segundos en actualizarse.

## 4 Correo y auditoría

Abre http://localhost:8025. Selecciona el correo cuya reserva coincide con la respuesta HTTP y captura su asunto y contenido. Luego abre http://localhost:8083/auditoria y comprueba que aparece el mismo `reservaId` y `eventoId`.

## 5 Logs

```sh
docker compose logs --no-color ms-reservas ms-disponibilidad ms-notificaciones ms-auditoria
```

Busca `VALIDACION_SINCRONA`, `MESA_ASIGNADA`, `EVENTO_PUBLICADO`, `NOTIFICACION_PROCESADA` y `AUDITORIA_PROCESADA`. El orden exacto entre notificaciones y auditoría puede variar porque son tareas independientes. Captura líneas con los identificadores de la misma reserva.

## Entrega

Incorpora las capturas junto con el diseño de la Parte 1, fragmentos de `RabbitTopology`, `ReservaPublisher` y ambos `ReservaConsumer`, y la explicación de resultados de `INFORME_IMPLEMENTACION.md`. No uses una cola vacía por sí sola como prueba: acompáñala con el mensaje pendiente anterior, logs y registros del procesamiento.
