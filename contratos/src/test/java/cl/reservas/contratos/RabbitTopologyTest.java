package cl.reservas.contratos;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class RabbitTopologyTest {
    @Test void declaraBeansYLasDosRutasConDlq() {
        try (var context = new AnnotationConfigApplicationContext(RabbitTopology.class)) {
            var queues = context.getBeansOfType(Queue.class);
            assertEquals(3, queues.size());
            assertEquals(2, context.getBeansOfType(Exchange.class).size());
            var bindings = context.getBeansOfType(Binding.class);
            assertEquals(3, bindings.size());
            for (String name : new String[]{"notificacionesQueue", "auditoriaQueue"}) {
                var queue = queues.get(name);
                assertTrue(queue.isDurable());
                assertEquals(RabbitTopology.DLX, queue.getArguments().get("x-dead-letter-exchange"));
                assertEquals(RabbitTopology.DLQ_ROUTING_KEY, queue.getArguments().get("x-dead-letter-routing-key"));
            }
            assertEquals(2, bindings.values().stream().filter(b -> b.getExchange().equals(RabbitTopology.EXCHANGE)
                && b.getRoutingKey().equals(RabbitTopology.ROUTING_KEY)).count());
            var deadLetter = bindings.get("deadLetterBinding");
            assertEquals(RabbitTopology.DLQ, deadLetter.getDestination());
            assertEquals(RabbitTopology.DLX, deadLetter.getExchange());
        }
    }
}
