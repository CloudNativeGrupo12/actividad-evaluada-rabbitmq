package cl.reservas.auditoria;

import cl.reservas.contratos.ReservaConfirmada;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class AuditoriaServiceTest {
    RegistroRepository repository=mock(RegistroRepository.class);
    AuditoriaService service=new AuditoriaService(repository);
    ReservaConfirmada evento=new ReservaConfirmada("evt-1","ReservaConfirmada",Instant.now(),"res-1",
        "cli-1","cliente@example.com","mesa-02",LocalDate.now().plusDays(7),LocalTime.of(18,0),
        LocalTime.of(20,0),4,"CONFIRMADA");

    @Test void registraEventoNuevo() {
        when(repository.existe("evt-1")).thenReturn(false);
        service.procesar(evento,"{\"eventoId\":\"evt-1\"}");
        verify(repository).guardar(evento,"{\"eventoId\":\"evt-1\"}","RESERVA_CONFIRMADA_REGISTRADA");
    }

    @Test void ignoraEventoDuplicado() {
        when(repository.existe("evt-1")).thenReturn(true);
        service.procesar(evento,"{}");
        verify(repository,never()).guardar(any(),anyString(),anyString());
    }
}