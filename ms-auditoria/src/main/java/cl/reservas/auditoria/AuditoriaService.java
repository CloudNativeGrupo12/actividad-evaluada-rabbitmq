package cl.reservas.auditoria;
import cl.reservas.contratos.*;
import org.springframework.stereotype.Service;
@Service
public class AuditoriaService {
    private final RegistroRepository repository;
    public AuditoriaService(RegistroRepository repository) { this.repository=repository; }
    public synchronized void procesar(ReservaConfirmada evento,String payload) {
        if (!repository.existe(evento.eventoId())) repository.guardar(evento,payload,"RESERVA_CONFIRMADA_REGISTRADA");
    }
}
