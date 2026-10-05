package cl.reservas.auditoria;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class RegistroController {
    private final RegistroRepository repository;
    public RegistroController(RegistroRepository repository) { this.repository=repository; }
    @GetMapping("/auditoria") public List<Map<String,Object>> listar() { return repository.listar(); }
}
