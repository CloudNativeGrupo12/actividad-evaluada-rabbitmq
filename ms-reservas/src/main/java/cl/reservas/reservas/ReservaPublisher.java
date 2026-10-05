package cl.reservas.reservas;
import cl.reservas.contratos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.slf4j.*;
@Component
public class ReservaPublisher {
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;
    private static final Logger log=LoggerFactory.getLogger(ReservaPublisher.class);
    public ReservaPublisher(RabbitTemplate rabbit,ObjectMapper mapper) { this.rabbit=rabbit; this.mapper=mapper; }
    public void publicar(ReservaConfirmada evento) throws Exception {
        var properties=new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(evento.eventoId());
        var correlacion=new CorrelationData(evento.eventoId());
        rabbit.send(RabbitTopology.EXCHANGE,RabbitTopology.ROUTING_KEY,new Message(mapper.writeValueAsBytes(evento),properties),correlacion);
        var confirmacion=correlacion.getFuture().get(5,TimeUnit.SECONDS);
        if (!confirmacion.isAck() || correlacion.getReturned()!=null) throw new IllegalStateException("Publicación no confirmada o sin destino");
        log.info("EVENTO_PUBLICADO eventoId={} reservaId={} exchange={} routingKey={}",evento.eventoId(),evento.reservaId(),RabbitTopology.EXCHANGE,RabbitTopology.ROUTING_KEY);
    }
}
