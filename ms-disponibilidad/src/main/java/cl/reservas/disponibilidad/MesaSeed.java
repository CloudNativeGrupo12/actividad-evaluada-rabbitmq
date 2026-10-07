package cl.reservas.disponibilidad;
import java.util.List;
import org.slf4j.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
@Component
public class MesaSeed implements ApplicationRunner {
    private static final Logger log=LoggerFactory.getLogger(MesaSeed.class);
    private final MesaJpaRepository mesas;
    public MesaSeed(MesaJpaRepository mesas) { this.mesas=mesas; }
    @Override
    public void run(ApplicationArguments args) {
        if (mesas.count()>0) return;
        mesas.saveAll(List.of(new Mesa("mesa-01",2),new Mesa("mesa-02",4),
            new Mesa("mesa-03",4),new Mesa("mesa-04",6),new Mesa("mesa-05",8)));
        log.info("MESAS_SEMBRADAS cantidad=5");
    }
}
