package cl.reservas.contratos;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;

@Configuration
public class RabbitTopology {
    public static final String EXCHANGE = "reservas.exchange";
    public static final String ROUTING_KEY = "reserva.confirmada";
    public static final String NOTIFICACIONES = "reservas.notificaciones.queue";
    public static final String AUDITORIA = "reservas.auditoria.queue";
    public static final String DLX = "reservas.dlx";
    public static final String DLQ = "reservas.dlq";
    public static final String DLQ_ROUTING_KEY = "reservas.dlq";

    @Bean public DirectExchange reservasExchange() { return new DirectExchange(EXCHANGE, true, false); }
    @Bean public FanoutExchange reservasDeadLetterExchange() { return new FanoutExchange(DLX, true, false); }

    // Cada dominio recibe su propia copia; los mensajes rechazados terminan en la DLQ.
    @Bean public Queue notificacionesQueue() { return colaConDlq(NOTIFICACIONES); }
    @Bean public Queue auditoriaQueue() { return colaConDlq(AUDITORIA); }
    @Bean public Queue deadLetterQueue() { return QueueBuilder.durable(DLQ).build(); }

    @Bean public Binding notificacionesBinding() {
        return BindingBuilder.bind(notificacionesQueue()).to(reservasExchange()).with(ROUTING_KEY);
    }
    @Bean public Binding auditoriaBinding() {
        return BindingBuilder.bind(auditoriaQueue()).to(reservasExchange()).with(ROUTING_KEY);
    }
    @Bean public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(reservasDeadLetterExchange());
    }
    private Queue colaConDlq(String nombre) {
        return QueueBuilder.durable(nombre).deadLetterExchange(DLX).deadLetterRoutingKey(DLQ_ROUTING_KEY).build();
    }
}
