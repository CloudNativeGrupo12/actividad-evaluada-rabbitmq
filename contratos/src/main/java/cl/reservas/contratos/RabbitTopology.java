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

    @Bean
    public Declarables reservasTopology() {
        var exchange = new DirectExchange(EXCHANGE, true, false);
        var notificaciones = QueueBuilder.durable(NOTIFICACIONES)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY).build();
        var auditoria = QueueBuilder.durable(AUDITORIA)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY).build();
        var dlx = new FanoutExchange(DLX, true, false);
        var dlq = QueueBuilder.durable(DLQ).build();
        return new Declarables(exchange, notificaciones, auditoria, dlx, dlq,
                BindingBuilder.bind(notificaciones).to(exchange).with(ROUTING_KEY),
                BindingBuilder.bind(auditoria).to(exchange).with(ROUTING_KEY),
                BindingBuilder.bind(dlq).to(dlx));
    }
}
