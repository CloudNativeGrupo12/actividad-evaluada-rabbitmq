package cl.reservas.notificaciones;

import cl.reservas.contratos.ReservaConfirmada;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificacionServiceTest {
    JavaMailSender mail=mock(JavaMailSender.class);
    RegistroRepository repository=mock(RegistroRepository.class);
    NotificacionService service=new NotificacionService(mail,repository,"reservas@restaurante.local");
    ReservaConfirmada evento=new ReservaConfirmada("evt-1","ReservaConfirmada",Instant.now(),"res-1",
        "cli-1","cliente@example.com","mesa-02",LocalDate.now().plusDays(7),LocalTime.of(18,0),
        LocalTime.of(20,0),4,"CONFIRMADA");

    @Test void enviaCorreoYRegistraProcesado() {
        when(repository.existe("evt-1")).thenReturn(false);
        service.procesar(evento,"{\"eventoId\":\"evt-1\"}");
        ArgumentCaptor<SimpleMailMessage> captor=ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(captor.capture());
        assertEquals("cliente@example.com",captor.getValue().getTo()[0]);
        assertEquals("reservas@restaurante.local",captor.getValue().getFrom());
        verify(repository).guardar(evento,"{\"eventoId\":\"evt-1\"}","CORREO_ENVIADO");
    }

    @Test void ignoraEventoYaProcesado() {
        when(repository.existe("evt-1")).thenReturn(true);
        service.procesar(evento,"{}");
        verifyNoInteractions(mail);
        verify(repository,never()).guardar(any(),anyString(),anyString());
    }
}