package cl.reservas.auditoria;
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
    private final AuditoriaService service;
    private final RetryTemplate retry;
    private final Validator validator;
    private static final Logger log=LoggerFactory.getLogger(ReservaConsumer.class);
    public ReservaConsumer(ObjectMapper mapper,AuditoriaService service,RetryTemplate retry,Validator validator) {
        this.mapper=mapper; this.service=service; this.retry=retry; this.validator=validator;
    }
    @RabbitListener(queues=RabbitTopology.AUDITORIA)
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
                if (context.getRetryCount()>0) log.warn("MENSAJE_REINTENTO queue={} eventoId={} intento={}",RabbitTopology.AUDITORIA,confirmado.eventoId(),context.getRetryCount()+1);
                service.procesar(confirmado,payload);
                return null;
            });
        } catch (Exception ex) {
            log.error("MENSAJE_RECHAZADO queue={} mensajeId={} error={}",RabbitTopology.AUDITORIA,mensaje.getMessageProperties().getMessageId(),ex.toString());
            log.error("DLQ_REDIRECT queue={} dlx={} dlq={} mensajeId={}",RabbitTopology.AUDITORIA,RabbitTopology.DLX,RabbitTopology.DLQ,mensaje.getMessageProperties().getMessageId());
            canal.basicNack(tag,false,false);
            return;
        }
        canal.basicAck(tag,false);
        log.info("AUDITORIA_PROCESADA eventoId={} reservaId={} queue={}",evento.eventoId(),evento.reservaId(),RabbitTopology.AUDITORIA);
    }
}
