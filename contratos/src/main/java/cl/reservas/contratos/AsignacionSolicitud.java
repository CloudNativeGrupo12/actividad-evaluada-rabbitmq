package cl.reservas.contratos;
import jakarta.validation.constraints.*;
import java.time.*;
public record AsignacionSolicitud(@NotBlank String reservaId, @NotNull @FutureOrPresent LocalDate fecha,
    @NotNull LocalTime horaInicio, @NotNull LocalTime horaFin, @Min(1) @Max(8) int cantidadPersonas) {
    @AssertTrue(message="horaFin debe ser posterior a horaInicio")
    public boolean isHorarioValido() { return horaInicio == null || horaFin == null || horaFin.isAfter(horaInicio); }
}
