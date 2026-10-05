package cl.reservas.auditoria;
import cl.reservas.contratos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.slf4j.*;
import java.nio.charset.StandardCharsets;
@Component
public class ReservaConsumer {
    private final ObjectMapper mapper;
    private final AuditoriaService service;
    private static final Logger log=LoggerFactory.getLogger(ReservaConsumer.class);
    public ReservaConsumer(ObjectMapper mapper,AuditoriaService service) { this.mapper=mapper; this.service=service; }
    @RabbitListener(queues=RabbitTopology.AUDITORIA)
    public void recibir(Message mensaje,Channel canal) throws java.io.IOException {
        long tag=mensaje.getMessageProperties().getDeliveryTag();
        try {
            String payload=new String(mensaje.getBody(),StandardCharsets.UTF_8);
            var evento=mapper.readValue(payload,ReservaConfirmada.class);
            if (!"ReservaConfirmada".equals(evento.tipoEvento()) || evento.eventoId()==null || evento.reservaId()==null)
                throw new IllegalArgumentException("Evento inválido");
            service.procesar(evento,payload);
            canal.basicAck(tag,false);
            log.info("AUDITORIA_PROCESADA eventoId={} reservaId={} queue={}",evento.eventoId(),evento.reservaId(),RabbitTopology.AUDITORIA);
        } catch (Exception ex) {
            log.error("MENSAJE_RECHAZADO queue={} mensajeId={} error={}",RabbitTopology.AUDITORIA,mensaje.getMessageProperties().getMessageId(),ex.toString());
            // Sin DLQ ni retries complejos: el error se registra y el mensaje se descarta.
            canal.basicNack(tag,false,false);
        }
    }
}
