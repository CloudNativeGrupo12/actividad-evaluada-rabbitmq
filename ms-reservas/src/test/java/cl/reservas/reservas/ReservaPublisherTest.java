package cl.reservas.reservas;

import cl.reservas.contratos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ReservaPublisherTest {
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final ReservaPublisher publisher = new ReservaPublisher(rabbit, new ObjectMapper().findAndRegisterModules());
    private final ReservaConfirmada event = new ReservaConfirmada("evt-1", "ReservaConfirmada", Instant.now(), "res-1", "cli-1",
        "cliente@example.com", "mesa-02", LocalDate.now().plusDays(7), LocalTime.of(18,0), LocalTime.of(20,0), 4, "CONFIRMADA");
    private void broker(boolean ack, boolean returned) {
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            CorrelationData correlation = invocation.getArgument(3);
            assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
            assertEquals("evt-1", message.getMessageProperties().getMessageId());
            if (returned) correlation.setReturned(new ReturnedMessage(message, 312, "NO_ROUTE", RabbitTopology.EXCHANGE, RabbitTopology.ROUTING_KEY));
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "broker nack"));
            return null;
        }).when(rabbit).send(eq(RabbitTopology.EXCHANGE), eq(RabbitTopology.ROUTING_KEY), any(Message.class), any(CorrelationData.class));
    }
    @Test void publicaJsonPersistenteYEsperaConfirmacion() throws Exception {
        broker(true, false); publisher.publicar(event);
    }
    @Test void nackNoSeInformaComoPublicacionExitosa() {
        broker(false, false); assertThrows(IllegalStateException.class, () -> publisher.publicar(event));
    }
    @Test void mensajeSinDestinoNoSeInformaComoPublicacionExitosa() {
        broker(true, true); assertThrows(IllegalStateException.class, () -> publisher.publicar(event));
    }
}
