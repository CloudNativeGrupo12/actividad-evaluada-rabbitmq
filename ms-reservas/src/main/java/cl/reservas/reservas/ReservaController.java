package cl.reservas.reservas;
import cl.reservas.contratos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import java.util.*;
@RestController @RequestMapping("/reservas")
public class ReservaController {
    private final ReservaService service;
    private final ReservaRepository repository;
    public ReservaController(ReservaService service,ReservaRepository repository) { this.service=service; this.repository=repository; }
    @PostMapping public ResponseEntity<Map<String,Object>> crear(@Valid @RequestBody ReservaSolicitud solicitud) {
        var r=service.crear(solicitud);
        return ResponseEntity.created(URI.create("/reservas/"+r.get("reservaId"))).body(r);
    }
    @GetMapping public List<Map<String,Object>> listar() { return repository.listar(); }
    @GetMapping("/{id}") public Map<String,Object> obtener(@PathVariable String id) { return repository.obtener(id); }
    @PostMapping("/{id}/publicar") public Map<String,Object> publicar(@PathVariable String id) { return service.reintentar(id); }
}
