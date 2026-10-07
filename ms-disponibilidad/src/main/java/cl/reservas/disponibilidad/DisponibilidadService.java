package cl.reservas.disponibilidad;
import cl.reservas.contratos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.format.DateTimeFormatter;
import java.util.*;
@Service
public class DisponibilidadService {
    private static final DateTimeFormatter HORA=DateTimeFormatter.ofPattern("HH:mm:ss");
    private final MesaJpaRepository mesasJpa;
    private final AsignacionJpaRepository asignacionesJpa;
    private final TransactionTemplate tx;
    public DisponibilidadService(MesaJpaRepository mesasJpa,AsignacionJpaRepository asignacionesJpa,TransactionTemplate tx) {
        this.mesasJpa=mesasJpa; this.asignacionesJpa=asignacionesJpa; this.tx=tx;
    }
    // El bloqueo permanece hasta el commit. Una sola instancia de este servicio en la demo.
    public synchronized AsignacionRespuesta asignar(AsignacionSolicitud solicitud) {
        return tx.execute(status -> {
            var existente=asignacionesJpa.findByReservaId(solicitud.reservaId());
            if (existente.isPresent()) {
                var a=existente.get();
                return new AsignacionRespuesta(a.getAsignacionId(),a.getMesaId(),"ASIGNADA");
            }
            var ocupadas=new HashSet<>(asignacionesJpa.mesasOcupadas(solicitud.fecha(),solicitud.horaInicio(),solicitud.horaFin()));
            var mesaLibre=mesasJpa.findAllByOrderByCapacidadAscMesaIdAsc().stream()
                .filter(m -> m.getCapacidad()>=solicitud.cantidadPersonas())
                .filter(m -> !ocupadas.contains(m.getMesaId()))
                .findFirst();
            if (mesaLibre.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"No hay mesas disponibles para ese horario y capacidad");
            var asignacion=new Asignacion(UUID.randomUUID().toString(),solicitud.reservaId(),mesaLibre.get().getMesaId(),
                solicitud.fecha(),solicitud.horaInicio(),solicitud.horaFin());
            asignacionesJpa.save(asignacion);
            return new AsignacionRespuesta(asignacion.getAsignacionId(),asignacion.getMesaId(),"ASIGNADA");
        });
    }
    public synchronized void liberar(String reservaId) {
        tx.executeWithoutResult(status -> asignacionesJpa.findByReservaId(reservaId).ifPresent(asignacionesJpa::delete));
    }
    public List<Map<String,Object>> mesas() {
        return mesasJpa.findAllByOrderByMesaIdAsc().stream().map(m -> {
            Map<String,Object> fila=new LinkedHashMap<>();
            fila.put("MESA_ID",m.getMesaId());
            fila.put("CAPACIDAD",m.getCapacidad());
            return fila;
        }).toList();
    }
    public List<Map<String,Object>> asignaciones() {
        return asignacionesJpa.findAllByOrderByFechaAscHoraInicioAsc().stream().map(a -> {
            Map<String,Object> fila=new LinkedHashMap<>();
            fila.put("ASIGNACION_ID",a.getAsignacionId());
            fila.put("RESERVA_ID",a.getReservaId());
            fila.put("MESA_ID",a.getMesaId());
            fila.put("FECHA",a.getFecha().toString());
            fila.put("HORA_INICIO",a.getHoraInicio().format(HORA));
            fila.put("HORA_FIN",a.getHoraFin().format(HORA));
            return fila;
        }).toList();
    }
}
