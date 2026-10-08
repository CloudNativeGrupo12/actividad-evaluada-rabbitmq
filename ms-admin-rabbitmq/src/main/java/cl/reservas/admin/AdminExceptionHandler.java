package cl.reservas.admin;

import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.AMQP;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class AdminExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validar(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage()).sorted().reduce((a, b) -> a + "; " + b).orElse("Entrada inválida");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
    }
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail estado(ResponseStatusException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
    }
    @ExceptionHandler(AmqpException.class)
    ProblemDetail broker(AmqpException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ShutdownSignalException signal && signal.getReason() instanceof AMQP.Channel.Close close) {
                if (close.getReplyCode() == 406) return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "El recurso ya existe con una configuración diferente");
                if (close.getReplyCode() == 404) return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "La cola o el exchange no existe");
            }
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "RabbitMQ no está disponible para esta operación");
    }
}
