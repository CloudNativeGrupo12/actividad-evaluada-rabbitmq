package cl.reservas.contratos;
import java.time.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
public record ReservaConfirmada(@NotBlank String eventoId, @Pattern(regexp="ReservaConfirmada") @NotBlank String tipoEvento,
    @NotNull Instant fechaEvento, @NotBlank String reservaId, @NotBlank String clienteId,
    @NotBlank @Email String emailCliente, @NotBlank String mesaId, @NotNull LocalDate fechaReserva,
    @NotNull LocalTime horaInicio, @NotNull LocalTime horaFin, @Min(1) @Max(8) int cantidadPersonas,
    @NotBlank @Pattern(regexp="CONFIRMADA") String estado) {
    @AssertTrue(message="horaFin debe ser posterior a horaInicio")
    @JsonIgnore
    public boolean isHorarioValido() { return horaInicio == null || horaFin == null || horaFin.isAfter(horaInicio); }
}
