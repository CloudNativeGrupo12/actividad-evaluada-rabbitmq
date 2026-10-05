package cl.reservas.contratos;
import jakarta.validation.constraints.*;
import java.time.*;
public record ReservaSolicitud(@NotBlank String clienteId, @NotBlank @Email String emailCliente,
    @NotNull @FutureOrPresent LocalDate fechaReserva, @NotNull LocalTime horaInicio,
    @NotNull LocalTime horaFin, @Min(1) @Max(8) int cantidadPersonas) {
    @AssertTrue(message="horaFin debe ser posterior a horaInicio")
    public boolean isHorarioValido() { return horaInicio == null || horaFin == null || horaFin.isAfter(horaInicio); }
}
