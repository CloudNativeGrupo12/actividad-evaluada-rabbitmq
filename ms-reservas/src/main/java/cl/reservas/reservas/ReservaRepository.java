package cl.reservas.reservas;
import cl.reservas.contratos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
@Repository
public class ReservaRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public ReservaRepository(JdbcTemplate jdbc,ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }
    public void guardar(ReservaConfirmada e) {
        try { jdbc.update("INSERT INTO reservas VALUES (?,?,?,?)",e.reservaId(),e.eventoId(),mapper.writeValueAsString(e),"PENDIENTE"); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    public void publicada(String id) { jdbc.update("UPDATE reservas SET publicacion='PUBLICADA' WHERE reserva_id=?",id); }
    public ReservaConfirmada evento(String id) {
        var rows=jdbc.queryForList("SELECT payload FROM reservas WHERE reserva_id=?",String.class,id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Reserva no encontrada");
        try { return mapper.readValue(rows.getFirst(),ReservaConfirmada.class); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    public Map<String,Object> obtener(String id) {
        var e=evento(id);
        Map<String,Object> respuesta=mapper.convertValue(e,new com.fasterxml.jackson.core.type.TypeReference<>() {});
        respuesta.put("publicacion",jdbc.queryForObject("SELECT publicacion FROM reservas WHERE reserva_id=?",String.class,id));
        return respuesta;
    }
    public List<Map<String,Object>> listar() {
        return jdbc.queryForList("SELECT reserva_id FROM reservas ORDER BY reserva_id",String.class).stream().map(this::obtener).toList();
    }
}
