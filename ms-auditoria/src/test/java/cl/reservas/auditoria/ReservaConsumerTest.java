package cl.reservas.auditoria;

import cl.reservas.contratos.ReservaConfirmada;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.retry.support.RetryTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ReservaConsumerTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AuditoriaService service = mock(AuditoriaService.class);
    private final Channel channel = mock(Channel.class);
    private ValidatorFactory validation;
    private ReservaConsumer consumer;
    @BeforeEach void preparar() {
        validation = Validation.buildDefaultValidatorFactory();
        var retry = RetryTemplate.builder().maxAttempts(3).noBackoff().retryOn(TransientDataAccessResourceException.class).build();
        consumer = new ReservaConsumer(mapper, service, retry, validation.getValidator());
    }
    @AfterEach void cerrar() { validation.close(); }
    private Message mensaje(String payload) {
        var properties = new MessageProperties(); properties.setDeliveryTag(7);
        return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    }
    private Message valido() throws Exception {
        return mensaje(mapper.writeValueAsString(new ReservaConfirmada("evt-1", "ReservaConfirmada", Instant.now(), "res-1", "cli-1",
            "cliente@example.com", "mesa-02", LocalDate.now().plusDays(7), LocalTime.of(18,0), LocalTime.of(20,0), 4, "CONFIRMADA")));
    }
    @Test void confirmaDespuesDeGuardar() throws Exception {
        consumer.recibir(valido(), channel);
        var order = inOrder(service, channel); order.verify(service).procesar(any(), anyString()); order.verify(channel).basicAck(7,false);
    }
    @Test void jsonInvalidoVaADlq() throws Exception {
        consumer.recibir(mensaje("{mal"), channel); verifyNoInteractions(service); verify(channel).basicNack(7,false,false);
    }
    @Test void eventoIncompletoVaADlq() throws Exception {
        consumer.recibir(mensaje("null"), channel); verifyNoInteractions(service); verify(channel).basicNack(7,false,false);
    }
    @Test void errorTransitorioReintenta() throws Exception {
        doThrow(new TransientDataAccessResourceException("DB temporal")).doNothing().when(service).procesar(any(),anyString());
        consumer.recibir(valido(), channel); verify(service,times(2)).procesar(any(),anyString()); verify(channel).basicAck(7,false);
    }
    @Test void tresFallosTerminanEnDlq() throws Exception {
        doThrow(new TransientDataAccessResourceException("DB caída")).when(service).procesar(any(),anyString());
        consumer.recibir(valido(),channel); verify(service,times(3)).procesar(any(),anyString()); verify(channel).basicNack(7,false,false);
        verify(channel,never()).basicAck(anyLong(),anyBoolean());
    }
    @Test void errorPermanenteNoSeReintenta() throws Exception {
        doThrow(new IllegalArgumentException("permanente")).when(service).procesar(any(),anyString());
        consumer.recibir(valido(),channel); verify(service).procesar(any(),anyString()); verify(channel).basicNack(7,false,false);
    }
    @Test void falloDeAckNoIntentaOtroSettlement() throws Exception {
        doThrow(new IOException("canal cerrado")).when(channel).basicAck(7,false);
        assertThrows(IOException.class,()->consumer.recibir(valido(),channel));
        verify(channel,never()).basicNack(anyLong(),anyBoolean(),anyBoolean());
    }
}
