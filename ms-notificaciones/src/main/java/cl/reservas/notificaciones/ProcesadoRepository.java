package cl.reservas.notificaciones;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProcesadoRepository extends JpaRepository<Procesado,String> {
    List<Procesado> findAllByOrderByProcesadoEnDesc();
}
