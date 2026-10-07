package cl.reservas.disponibilidad;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface AsignacionJpaRepository extends JpaRepository<Asignacion,String> {
    Optional<Asignacion> findByReservaId(String reservaId);
    List<Asignacion> findAllByOrderByFechaAscHoraInicioAsc();
    @Query("""
        SELECT a.mesaId FROM Asignacion a
        WHERE a.fecha=:fecha AND a.horaInicio<:horaFin AND a.horaFin>:horaInicio
        """)
    List<String> mesasOcupadas(@Param("fecha") LocalDate fecha,
                               @Param("horaInicio") LocalTime horaInicio,
                               @Param("horaFin") LocalTime horaFin);
}
