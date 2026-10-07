package cl.reservas.disponibilidad;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;
@Entity
@Table(name="asignaciones")
@Getter @Setter @NoArgsConstructor
public class Asignacion {
    @Id
    @Column(name="asignacion_id",length=40)
    private String asignacionId;
    @Column(name="reserva_id",length=40,nullable=false,unique=true)
    private String reservaId;
    @Column(name="mesa_id",length=40,nullable=false)
    private String mesaId;
    @Column(name="fecha",nullable=false)
    private LocalDate fecha;
    @Column(name="hora_inicio",nullable=false)
    private LocalTime horaInicio;
    @Column(name="hora_fin",nullable=false)
    private LocalTime horaFin;
    public Asignacion(String asignacionId,String reservaId,String mesaId,LocalDate fecha,LocalTime horaInicio,LocalTime horaFin) {
        this.asignacionId=asignacionId;
        this.reservaId=reservaId;
        this.mesaId=mesaId;
        this.fecha=fecha;
        this.horaInicio=horaInicio;
        this.horaFin=horaFin;
    }
}
