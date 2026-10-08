package cl.reservas.admin;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import cl.reservas.admin.AdminController.*;

/** Operaciones de infraestructura; el controlador solo conoce solicitudes y respuestas. */
@Service
public class RabbitAdminService {
    public static final String NAME_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,254}";
    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);
    private static final Set<String> NUMERIC_ARGS = Set.of("x-message-ttl", "x-expires", "x-max-length", "x-max-length-bytes");
    private static final Set<String> TEXT_ARGS = Set.of("x-dead-letter-exchange", "x-dead-letter-routing-key");
    private final RabbitAdmin admin;
    public RabbitAdminService(RabbitAdmin admin) { this.admin = admin; }

    public ColaRespuesta crearCola(ColaSolicitud solicitud) {
        validarNombre(solicitud.name());
        var args = solicitud.args() == null ? Map.<String, Object>of() : solicitud.args();
        validarArgumentos(args);
        boolean durable = !Boolean.FALSE.equals(solicitud.durable());
        admin.declareQueue(new Queue(solicitud.name(), durable, false, false, args));
        log.info("COLA_CREADA nombre={} durable={}", solicitud.name(), durable);
        return new ColaRespuesta(solicitud.name(), durable);
    }
    public void eliminarCola(String name) {
        validarNombre(name);
        if (!admin.deleteQueue(name)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La cola no existe");
        log.info("COLA_ELIMINADA nombre={}", name);
    }
    public ExchangeRespuesta crearExchange(ExchangeSolicitud solicitud) {
        validarNombre(solicitud.name());
        boolean durable = !Boolean.FALSE.equals(solicitud.durable());
        Exchange exchange = switch (solicitud.type() == null ? "" : solicitud.type()) {
            case "direct" -> new DirectExchange(solicitud.name(), durable, false);
            case "fanout" -> new FanoutExchange(solicitud.name(), durable, false);
            case "topic" -> new TopicExchange(solicitud.name(), durable, false);
            default -> throw invalido("type debe ser direct, fanout o topic");
        };
        admin.declareExchange(exchange);
        log.info("EXCHANGE_CREADO nombre={} tipo={}", solicitud.name(), solicitud.type());
        return new ExchangeRespuesta(solicitud.name(), solicitud.type(), durable);
    }
    public void eliminarExchange(String name) {
        validarNombre(name);
        if (!admin.deleteExchange(name)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El exchange no existe");
        log.info("EXCHANGE_ELIMINADO nombre={}", name);
    }
    public BindingSolicitud crearBinding(BindingSolicitud solicitud) {
        var binding = binding(solicitud);
        admin.declareBinding(binding);
        log.info("BINDING_CREADO queue={} exchange={} routingKey={}", solicitud.queue(), solicitud.exchange(), binding.getRoutingKey());
        return new BindingSolicitud(solicitud.queue(), solicitud.exchange(), binding.getRoutingKey());
    }
    public void eliminarBinding(BindingSolicitud solicitud) {
        var binding = binding(solicitud);
        admin.removeBinding(binding);
        log.info("BINDING_ELIMINADO queue={} exchange={} routingKey={}", solicitud.queue(), solicitud.exchange(), binding.getRoutingKey());
    }
    private Binding binding(BindingSolicitud solicitud) {
        validarNombre(solicitud.queue());
        validarNombre(solicitud.exchange());
        String key = solicitud.routingKey() == null ? "" : solicitud.routingKey();
        if (key.length() > 255 || key.chars().anyMatch(Character::isISOControl)) throw invalido("routingKey inválida");
        return new Binding(solicitud.queue(), Binding.DestinationType.QUEUE, solicitud.exchange(), key, Map.of());
    }
    private void validarNombre(String name) {
        if (name == null || !name.matches(NAME_PATTERN) || name.startsWith("amq."))
            throw invalido("Nombre inválido: usa hasta 255 letras, números, puntos, guiones o guiones bajos; amq. está reservado");
    }
    private void validarArgumentos(Map<String, Object> args) {
        args.forEach((key, value) -> {
            if (NUMERIC_ARGS.contains(key)) {
                if (!(value instanceof Number number)) throw invalido(key + " debe ser un entero");
                var decimal = new BigDecimal(number.toString());
                try {
                    long n = decimal.longValueExact();
                    if (n < 0 || (key.equals("x-expires") && n == 0)) throw invalido(key + " fuera de rango");
                } catch (ArithmeticException ex) { throw invalido(key + " debe ser un entero no negativo de 64 bits"); }
            } else if (TEXT_ARGS.contains(key)) {
                if (!(value instanceof String text) || text.length() > 255 || text.chars().anyMatch(Character::isISOControl))
                    throw invalido(key + " debe ser texto de hasta 255 caracteres");
                if (key.equals("x-dead-letter-exchange")) validarNombre(text);
            } else throw invalido("Argumento de cola no soportado: " + key);
        });
    }
    private ResponseStatusException invalido(String detalle) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detalle); }
}
