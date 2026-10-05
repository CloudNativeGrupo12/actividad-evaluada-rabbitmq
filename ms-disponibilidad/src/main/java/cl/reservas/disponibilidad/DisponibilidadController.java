package cl.reservas.disponibilidad;
import cl.reservas.contratos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.slf4j.*;
import java.util.*;
@RestController
public class DisponibilidadController {
    private final DisponibilidadService service;
    private static final Logger log=LoggerFactory.getLogger(DisponibilidadController.class);
    public DisponibilidadController(DisponibilidadService service) { this.service=service; }
    @PostMapping("/asignaciones") @ResponseStatus(HttpStatus.CREATED)
    public AsignacionRespuesta asignar(@Valid @RequestBody AsignacionSolicitud solicitud) {
        var respuesta=service.asignar(solicitud);
        log.info("MESA_ASIGNADA reservaId={} mesaId={}",solicitud.reservaId(),respuesta.mesaId());
        return respuesta;
    }
    @DeleteMapping("/asignaciones/{reservaId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void liberar(@PathVariable String reservaId) { service.liberar(reservaId); }
    @GetMapping("/mesas") public List<Map<String,Object>> mesas() { return service.mesas(); }
    @GetMapping("/asignaciones") public List<Map<String,Object>> asignaciones() { return service.asignaciones(); }
}
