package cl.reservas.contratos;
import java.time.*;
public record ReservaConfirmada(String eventoId, String tipoEvento, Instant fechaEvento, String reservaId,
    String clienteId, String emailCliente, String mesaId, LocalDate fechaReserva, LocalTime horaInicio,
    LocalTime horaFin, int cantidadPersonas, String estado) {}
