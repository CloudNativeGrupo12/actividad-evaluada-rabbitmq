package cl.reservas.disponibilidad;
import cl.reservas.contratos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service
public class DisponibilidadService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public DisponibilidadService(JdbcTemplate jdbc, TransactionTemplate tx) { this.jdbc=jdbc; this.tx=tx; }
    // El bloqueo permanece hasta el commit. Una sola instancia de este servicio en la demo.
    public synchronized AsignacionRespuesta asignar(AsignacionSolicitud solicitud) {
        return tx.execute(status -> {
            var existente=jdbc.query("SELECT * FROM asignaciones WHERE reserva_id=?",
                (rs,n) -> new AsignacionRespuesta(rs.getString("asignacion_id"),rs.getString("mesa_id"),"ASIGNADA"), solicitud.reservaId());
            if (!existente.isEmpty()) return existente.getFirst();
            var mesas=jdbc.queryForList("""
                SELECT m.mesa_id FROM mesas m WHERE m.capacidad>=?
                AND NOT EXISTS (SELECT 1 FROM asignaciones a WHERE a.mesa_id=m.mesa_id
                  AND a.fecha=? AND a.hora_inicio<? AND a.hora_fin>?)
                ORDER BY m.capacidad, m.mesa_id
                """, String.class, solicitud.cantidadPersonas(), solicitud.fecha(), solicitud.horaFin(), solicitud.horaInicio());
            if (mesas.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"No hay mesas disponibles para ese horario y capacidad");
            String id=UUID.randomUUID().toString(), mesa=mesas.getFirst();
            jdbc.update("INSERT INTO asignaciones VALUES (?,?,?,?,?,?)",id,solicitud.reservaId(),mesa,solicitud.fecha(),solicitud.horaInicio(),solicitud.horaFin());
            return new AsignacionRespuesta(id,mesa,"ASIGNADA");
        });
    }
    public synchronized void liberar(String reservaId) { jdbc.update("DELETE FROM asignaciones WHERE reserva_id=?",reservaId); }
    public List<Map<String,Object>> mesas() { return jdbc.queryForList("SELECT * FROM mesas ORDER BY mesa_id"); }
    public List<Map<String,Object>> asignaciones() { return jdbc.queryForList("SELECT * FROM asignaciones ORDER BY fecha,hora_inicio"); }
}
