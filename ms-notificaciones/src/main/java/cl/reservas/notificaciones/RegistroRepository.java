package cl.reservas.notificaciones;
import cl.reservas.contratos.*;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.OffsetDateTime;
import java.util.*;
@Repository
public class RegistroRepository {
    private final JdbcTemplate jdbc;
    public RegistroRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean existe(String eventoId) { return jdbc.queryForObject("SELECT COUNT(*) FROM procesados WHERE evento_id=?",Integer.class,eventoId)>0; }
    public void guardar(ReservaConfirmada e,String payload,String resultado) {
        jdbc.update("INSERT INTO procesados VALUES (?,?,?,?,?)",e.eventoId(),e.reservaId(),payload,resultado,OffsetDateTime.now());
    }
    public List<Map<String,Object>> listar() { return jdbc.queryForList("SELECT * FROM procesados ORDER BY procesado_en DESC"); }
}
