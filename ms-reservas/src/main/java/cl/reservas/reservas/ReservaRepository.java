package cl.reservas.reservas;
import cl.reservas.contratos.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
@Repository
public class ReservaRepository {
    private final ReservaJpaRepository jpa;
    private final ObjectMapper mapper;
    public ReservaRepository(ReservaJpaRepository jpa,ObjectMapper mapper) { this.jpa=jpa; this.mapper=mapper; }
    public void guardar(ReservaConfirmada e) {
        try { jpa.save(new Reserva(e.reservaId(),e.eventoId(),mapper.writeValueAsString(e),"PENDIENTE")); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    public void publicada(String id) {
        jpa.findById(id).ifPresent(r -> { r.setPublicacion("PUBLICADA"); jpa.save(r); });
    }
    public ReservaConfirmada evento(String id) { return parsear(buscar(id)); }
    public Map<String,Object> obtener(String id) {
        var r=buscar(id);
        Map<String,Object> respuesta=mapper.convertValue(parsear(r),new TypeReference<>() {});
        respuesta.put("publicacion",r.getPublicacion());
        return respuesta;
    }
    public List<Map<String,Object>> listar() {
        return jpa.findAllByOrderByReservaIdAsc().stream().map(r -> {
            Map<String,Object> respuesta=mapper.convertValue(parsear(r),new TypeReference<>() {});
            respuesta.put("publicacion",r.getPublicacion());
            return respuesta;
        }).toList();
    }
    private Reserva buscar(String id) {
        return jpa.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Reserva no encontrada"));
    }
    private ReservaConfirmada parsear(Reserva r) {
        try { return mapper.readValue(r.getPayload(),ReservaConfirmada.class); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
