package cl.reservas.reservas;
import cl.reservas.contratos.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.*;
import java.time.Instant;
import java.util.*;
@Service
public class ReservaService {
    private final DisponibilidadClient disponibilidad;
    private final ReservaRepository repository;
    private final ReservaPublisher publisher;
    private static final Logger log=LoggerFactory.getLogger(ReservaService.class);
    public ReservaService(DisponibilidadClient disponibilidad,ReservaRepository repository,ReservaPublisher publisher) {
        this.disponibilidad=disponibilidad; this.repository=repository; this.publisher=publisher;
    }
    public Map<String,Object> crear(ReservaSolicitud solicitud) {
        String id=UUID.randomUUID().toString();
        AsignacionRespuesta asignacion;
        try {
            log.info("VALIDACION_SINCRONA reservaId={} destino=ms-disponibilidad",id);
            asignacion=disponibilidad.asignar(id,solicitud);
        } catch (HttpClientErrorException.Conflict ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"No hay mesas disponibles");
        } catch (RestClientException ex) {
            // Si el resultado HTTP es incierto, se intenta liberar usando el id conocido.
            compensar(id);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo confirmar la asignación de mesa");
        }
        if (asignacion==null) { compensar(id); throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Respuesta de disponibilidad vacía"); }
        var evento=new ReservaConfirmada(UUID.randomUUID().toString(),"ReservaConfirmada",Instant.now(),id,
            solicitud.clienteId(),solicitud.emailCliente(),asignacion.mesaId(),solicitud.fechaReserva(),
            solicitud.horaInicio(),solicitud.horaFin(),solicitud.cantidadPersonas(),"CONFIRMADA");
        try { repository.guardar(evento); }
        catch (RuntimeException ex) { compensar(id); throw ex; }
        publicarSiEsPosible(evento);
        return repository.obtener(id);
    }
    private void compensar(String id) {
        try { disponibilidad.liberar(id); }
        catch (Exception ex) { log.error("LIBERACION_PENDIENTE reservaId={} error={}",id,ex.toString()); }
    }
    private void publicarSiEsPosible(ReservaConfirmada evento) {
        try { publisher.publicar(evento); repository.publicada(evento.reservaId()); }
        catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("PUBLICACION_PENDIENTE reservaId={} eventoId={} error={}",evento.reservaId(),evento.eventoId(),ex.toString());
        }
    }
    public synchronized Map<String,Object> reintentar(String id) {
        var actual=repository.obtener(id);
        if (!"PUBLICADA".equals(actual.get("publicacion"))) publicarSiEsPosible(repository.evento(id));
        return repository.obtener(id);
    }
}
