package cl.reservas.disponibilidad;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MesaJpaRepository extends JpaRepository<Mesa,String> {
    List<Mesa> findAllByOrderByMesaIdAsc();
    List<Mesa> findAllByOrderByCapacidadAscMesaIdAsc();
}
