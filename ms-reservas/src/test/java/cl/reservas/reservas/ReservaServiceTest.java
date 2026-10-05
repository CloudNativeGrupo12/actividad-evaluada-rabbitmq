package cl.reservas.reservas;

import cl.reservas.contratos.*;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ReservaServiceTest {
    DisponibilidadClient disponibilidad=mock(DisponibilidadClient.class);
    ReservaRepository repository=mock(ReservaRepository.class);
    ReservaPublisher publisher=mock(ReservaPublisher.class);
    ReservaService service=new ReservaService(disponibilidad,repository,publisher);
    ReservaSolicitud solicitud=new ReservaSolicitud("cli-1","cliente@example.com",LocalDate.now().plusDays(7),LocalTime.of(18,0),LocalTime.of(20,0),4);
    @Test void conflictoNoGuardaNiPublica() {
        when(disponibilidad.asignar(anyString(),any())).thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT,"Sin mesas",HttpHeaders.EMPTY,new byte[0],null));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.crear(solicitud)).getStatusCode().value());
        verifyNoInteractions(repository,publisher);
    }
    @Test void caidaDeDisponibilidadNoConfirma() {
        when(disponibilidad.asignar(anyString(),any())).thenThrow(new ResourceAccessException("Timeout"));
        assertEquals(503,assertThrows(ResponseStatusException.class,()->service.crear(solicitud)).getStatusCode().value());
        verifyNoInteractions(repository,publisher);
        verify(disponibilidad).liberar(anyString());
    }
    @Test void falloAlGuardarLiberaMesaYNoPublica() {
        when(disponibilidad.asignar(anyString(),any())).thenReturn(new AsignacionRespuesta("asg","mesa-02","ASIGNADA"));
        doThrow(new IllegalStateException("Fallo DB")).when(repository).guardar(any());
        assertThrows(IllegalStateException.class,()->service.crear(solicitud));
        verify(disponibilidad).liberar(anyString());
        verifyNoInteractions(publisher);
    }
    @Test void falloDelBrokerConservaReservaPendiente() throws Exception {
        when(disponibilidad.asignar(anyString(),any())).thenReturn(new AsignacionRespuesta("asg","mesa-02","ASIGNADA"));
        when(repository.obtener(anyString())).thenReturn(Map.of("estado","CONFIRMADA","publicacion","PENDIENTE"));
        doThrow(new IllegalStateException("Broker no disponible")).when(publisher).publicar(any());
        var respuesta=service.crear(solicitud);
        assertEquals("CONFIRMADA",respuesta.get("estado"));
        assertEquals("PENDIENTE",respuesta.get("publicacion"));
        verify(repository).guardar(any());
        verify(repository,never()).publicada(anyString());
        verify(disponibilidad,never()).liberar(anyString());
    }
}
