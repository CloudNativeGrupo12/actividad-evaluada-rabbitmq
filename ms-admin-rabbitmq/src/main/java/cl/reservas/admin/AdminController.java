package cl.reservas.admin;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Binding.DestinationType;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private final RabbitAdmin admin;
    public AdminController(RabbitAdmin admin) { this.admin = admin; }
    public record ColaSolicitud(String name, Boolean durable, Map<String, Object> args) {}
    public record ExchangeSolicitud(String name, String type, Boolean durable) {}
    public record BindingSolicitud(String queue, String exchange, String routingKey) {}
    public record Respuesta(String status, String mensaje) {}

    @PostMapping("/queues")
    public ResponseEntity<Respuesta> crearCola(@RequestBody ColaSolicitud solicitud) {
        if (solicitud == null || solicitud.name() == null || solicitud.name().isBlank())
            return ResponseEntity.badRequest().body(new Respuesta("ERROR", "name es obligatorio"));
        var builder = Boolean.FALSE.equals(solicitud.durable())
            ? QueueBuilder.nonDurable(solicitud.name()) : QueueBuilder.durable(solicitud.name());
        Queue cola = (solicitud.args() == null ? builder : builder.withArguments(solicitud.args())).build();
        admin.declareQueue(cola);
        log.info("COLA_CREADA nombre={} durable={}", cola.getName(), cola.isDurable());
        return ResponseEntity.ok(new Respuesta("OK", "cola creada: " + cola.getName()));
    }

    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Respuesta> eliminarCola(@PathVariable String name) {
        boolean eliminada = admin.deleteQueue(name);
        log.info("COLA_ELIMINADA nombre={} eliminada={}", name, eliminada);
        return ResponseEntity.ok(new Respuesta("OK", "cola eliminada: " + name));
    }

    @PostMapping("/exchanges")
    public ResponseEntity<Respuesta> crearExchange(@RequestBody ExchangeSolicitud solicitud) {
        if (solicitud == null || solicitud.name() == null || solicitud.name().isBlank())
            return ResponseEntity.badRequest().body(new Respuesta("ERROR", "name es obligatorio"));
        boolean durable = !Boolean.FALSE.equals(solicitud.durable());
        String tipo = solicitud.type() == null ? "direct" : solicitud.type().toLowerCase(Locale.ROOT);
        Exchange exchange = switch (tipo) {
            case "fanout" -> new FanoutExchange(solicitud.name(), durable, false);
            case "topic" -> new TopicExchange(solicitud.name(), durable, false);
            case "headers" -> new HeadersExchange(solicitud.name(), durable, false);
            case "direct" -> new DirectExchange(solicitud.name(), durable, false);
            default -> null;
        };
        if (exchange == null)
            return ResponseEntity.badRequest().body(new Respuesta("ERROR", "type no soportado: " + tipo));
        admin.declareExchange(exchange);
        log.info("EXCHANGE_CREADO nombre={} tipo={}", exchange.getName(), tipo);
        return ResponseEntity.ok(new Respuesta("OK", "exchange creado: " + exchange.getName()));
    }

    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Respuesta> eliminarExchange(@PathVariable String name) {
        boolean eliminado = admin.deleteExchange(name);
        log.info("EXCHANGE_ELIMINADO nombre={} eliminado={}", name, eliminado);
        return ResponseEntity.ok(new Respuesta("OK", "exchange eliminado: " + name));
    }

    @PostMapping("/bindings")
    public ResponseEntity<Respuesta> crearBinding(@RequestBody BindingSolicitud solicitud) {
        return declararBinding(solicitud, true);
    }

    @DeleteMapping("/bindings")
    public ResponseEntity<Respuesta> eliminarBinding(@RequestBody BindingSolicitud solicitud) {
        return declararBinding(solicitud, false);
    }

    private ResponseEntity<Respuesta> declararBinding(BindingSolicitud solicitud, boolean crear) {
        if (solicitud == null || solicitud.queue() == null || solicitud.queue().isBlank()
                || solicitud.exchange() == null || solicitud.exchange().isBlank())
            return ResponseEntity.badRequest().body(new Respuesta("ERROR", "queue y exchange son obligatorios"));
        String routingKey = solicitud.routingKey() == null ? "" : solicitud.routingKey();
        var binding = new Binding(solicitud.queue(), DestinationType.QUEUE, solicitud.exchange(), routingKey, Map.of());
        if (crear) admin.declareBinding(binding);
        else admin.removeBinding(binding);
        String accion = crear ? "BINDING_CREADO" : "BINDING_ELIMINADO";
        log.info("{} queue={} exchange={} routingKey={}", accion, solicitud.queue(), solicitud.exchange(), routingKey);
        return ResponseEntity.ok(new Respuesta("OK", (crear ? "binding creado: " : "binding eliminado: ") + solicitud.queue()));
    }
}
