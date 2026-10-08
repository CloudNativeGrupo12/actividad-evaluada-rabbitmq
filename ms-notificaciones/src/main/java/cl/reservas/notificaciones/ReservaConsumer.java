package cl.reservas.notificaciones;
import cl.reservas.contratos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.slf4j.*;
import java.nio.charset.StandardCharsets;
import jakarta.validation.Validator;
import org.springframework.retry.support.RetryTemplate;
@Component
public class ReservaConsumer {
    private final ObjectMapper mapper;
    private final NotificacionService service;
    private final RetryTemplate retry;
    private final Validator validator;
    private static final Logger log=LoggerFactory.getLogger(ReservaConsumer.class);
    public ReservaConsumer(ObjectMapper mapper,NotificacionService service,RetryTemplate retry,Validator validator) {
        this.mapper=mapper; this.service=service; this.retry=retry; this.validator=validator;
    }
    @RabbitListener(queues=RabbitTopology.NOTIFICACIONES)
    public void recibir(Message mensaje,Channel canal) throws java.io.IOException {
        long tag=mensaje.getMessageProperties().getDeliveryTag();
        ReservaConfirmada evento;
        try {
            String payload=new String(mensaje.getBody(),StandardCharsets.UTF_8);
            evento=mapper.readValue(payload,ReservaConfirmada.class);
            if (evento==null || !validator.validate(evento).isEmpty())
                throw new IllegalArgumentException("Evento inválido");
            var confirmado=evento;
            retry.execute(context -> {
                if (context.getRetryCount()>0) log.warn("MENSAJE_REINTENTO queue={} eventoId={} intento={}",RabbitTopology.NOTIFICACIONES,confirmado.eventoId(),context.getRetryCount()+1);
                service.procesar(confirmado,payload);
                return null;
            });
        } catch (Exception ex) {
            log.error("MENSAJE_RECHAZADO queue={} mensajeId={} error={}",RabbitTopology.NOTIFICACIONES,mensaje.getMessageProperties().getMessageId(),ex.toString());
            log.error("DLQ_REDIRECT queue={} dlx={} dlq={} mensajeId={}",RabbitTopology.NOTIFICACIONES,RabbitTopology.DLX,RabbitTopology.DLQ,mensaje.getMessageProperties().getMessageId());
            canal.basicNack(tag,false,false);
            return;
        }
        // Un fallo de ACK se propaga: no intentamos NACK sobre un canal roto o un tag ya confirmado.
        canal.basicAck(tag,false);
        log.info("NOTIFICACION_PROCESADA eventoId={} reservaId={} queue={}",evento.eventoId(),evento.reservaId(),RabbitTopology.NOTIFICACIONES);
    }
}
