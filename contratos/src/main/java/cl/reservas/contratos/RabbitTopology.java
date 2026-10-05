package cl.reservas.contratos;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;
@Configuration
public class RabbitTopology {
    public static final String EXCHANGE = "reservas.exchange";
    public static final String ROUTING_KEY = "reserva.confirmada";
    public static final String NOTIFICACIONES = "reservas.notificaciones.queue";
    public static final String AUDITORIA = "reservas.auditoria.queue";
    @Bean public Declarables reservasTopology() {
        var exchange = new DirectExchange(EXCHANGE, true, false);
        var notificaciones = new Queue(NOTIFICACIONES, true);
        var auditoria = new Queue(AUDITORIA, true);
        return new Declarables(exchange, notificaciones, auditoria,
            BindingBuilder.bind(notificaciones).to(exchange).with(ROUTING_KEY),
            BindingBuilder.bind(auditoria).to(exchange).with(ROUTING_KEY));
    }
}
