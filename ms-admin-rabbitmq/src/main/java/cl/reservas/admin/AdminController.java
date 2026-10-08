package cl.reservas.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final RabbitAdminService service;
    public AdminController(RabbitAdminService service) { this.service = service; }

    public record ColaSolicitud(
            @NotBlank @Pattern(regexp = RabbitAdminService.NAME_PATTERN) String name,
            Boolean durable, Map<String, Object> args) {}
    public record ExchangeSolicitud(
            @NotBlank @Pattern(regexp = RabbitAdminService.NAME_PATTERN) String name,
            @NotBlank @Pattern(regexp = "direct|fanout|topic") String type, Boolean durable) {}
    public record BindingSolicitud(
            @NotBlank @Pattern(regexp = RabbitAdminService.NAME_PATTERN) String queue,
            @NotBlank @Pattern(regexp = RabbitAdminService.NAME_PATTERN) String exchange,
            @Size(max = 255) String routingKey) {}
    public record ColaRespuesta(String name, boolean durable) {}
    public record ExchangeRespuesta(String name, String type, boolean durable) {}

    @PostMapping("/queues")
    public ResponseEntity<ColaRespuesta> crearCola(@Valid @RequestBody ColaSolicitud solicitud) {
        var cola = service.crearCola(solicitud);
        return ResponseEntity.created(URI.create("/api/admin/queues/" + cola.name())).body(cola);
    }
    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Void> eliminarCola(@PathVariable String name) {
        service.eliminarCola(name);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/exchanges")
    public ResponseEntity<ExchangeRespuesta> crearExchange(@Valid @RequestBody ExchangeSolicitud solicitud) {
        var exchange = service.crearExchange(solicitud);
        return ResponseEntity.created(URI.create("/api/admin/exchanges/" + exchange.name())).body(exchange);
    }
    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Void> eliminarExchange(@PathVariable String name) {
        service.eliminarExchange(name);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/bindings")
    public ResponseEntity<BindingSolicitud> crearBinding(@Valid @RequestBody BindingSolicitud solicitud) {
        return ResponseEntity.status(201).body(service.crearBinding(solicitud));
    }
    @DeleteMapping("/bindings")
    public ResponseEntity<Void> eliminarBinding(@Valid @RequestBody BindingSolicitud solicitud) {
        service.eliminarBinding(solicitud);
        return ResponseEntity.noContent().build();
    }
}
