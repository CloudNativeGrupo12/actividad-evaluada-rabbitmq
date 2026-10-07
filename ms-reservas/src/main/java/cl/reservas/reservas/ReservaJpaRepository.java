package cl.reservas.reservas;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReservaJpaRepository extends JpaRepository<Reserva,String> {
    Optional<Reserva> findByEventoId(String eventoId);
    List<Reserva> findAllByOrderByReservaIdAsc();
}
