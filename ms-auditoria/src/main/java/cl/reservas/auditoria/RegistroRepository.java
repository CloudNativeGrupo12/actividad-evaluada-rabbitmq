package cl.reservas.auditoria;
import cl.reservas.contratos.*;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.*;
@Repository
public class RegistroRepository {
    private final ProcesadoRepository jpa;
    public RegistroRepository(ProcesadoRepository jpa) { this.jpa=jpa; }
    public boolean existe(String eventoId) { return jpa.existsById(eventoId); }
    public void guardar(ReservaConfirmada e,String payload,String resultado) {
        jpa.save(new Procesado(e.eventoId(),e.reservaId(),payload,resultado,OffsetDateTime.now()));
    }
    public List<Map<String,Object>> listar() {
        return jpa.findAllByOrderByProcesadoEnDesc().stream().map(p -> {
            Map<String,Object> fila=new LinkedHashMap<>();
            fila.put("EVENTO_ID",p.getEventoId());
            fila.put("RESERVA_ID",p.getReservaId());
            fila.put("PAYLOAD",p.getPayload());
            fila.put("RESULTADO",p.getResultado());
            fila.put("PROCESADO_EN",p.getProcesadoEn());
            return fila;
        }).toList();
    }
}
