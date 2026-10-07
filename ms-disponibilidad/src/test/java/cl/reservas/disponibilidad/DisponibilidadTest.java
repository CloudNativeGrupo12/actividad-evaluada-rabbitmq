package cl.reservas.disponibilidad;

import cl.reservas.contratos.AsignacionSolicitud;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:disponibilidad-test;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
@WithMockUser
class DisponibilidadTest {
    @Autowired DisponibilidadService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @BeforeEach void limpiar() { jdbc.update("DELETE FROM asignaciones"); }
    AsignacionSolicitud solicitud(String id,int inicio,int fin) {
        return new AsignacionSolicitud(id,LocalDate.now().plusDays(7),LocalTime.of(inicio,0),LocalTime.of(fin,0),8);
    }
    @Test void rechazaSolapamientosPeroPermiteHorariosConsecutivos() {
        var primera=service.asignar(solicitud("uno",18,20));
        assertThrows(ResponseStatusException.class,()->service.asignar(solicitud("dos",19,21)));
        var consecutiva=service.asignar(solicitud("tres",20,21));
        assertEquals(primera.mesaId(),consecutiva.mesaId());
    }
    @Test void soloUnaSolicitudConcurrenteObtieneLaMesa() throws Exception {
        try (var pool=Executors.newFixedThreadPool(8)) {
            var barrera=new CyclicBarrier(8);
            List<Callable<Boolean>> tareas=new ArrayList<>();
            for (int i=0;i<8;i++) {
                String id="concurrente-"+i;
                tareas.add(()->{ barrera.await(); try { service.asignar(solicitud(id,18,20)); return true; }
                    catch (ResponseStatusException ex) { assertEquals(409,ex.getStatusCode().value()); return false; } });
            }
            int exitos=0;
            for (var resultado:pool.invokeAll(tareas)) if (resultado.get()) exitos++;
            assertEquals(1,exitos);
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM asignaciones",Integer.class));
        }
    }
    @Test void validaHorarioAntesDeAsignar() throws Exception {
        mvc.perform(post("/asignaciones").contentType("application/json").content("""
            {"reservaId":"invalida","fecha":"%s","horaInicio":"20:00","horaFin":"19:00","cantidadPersonas":4}
            """.formatted(LocalDate.now().plusDays(7)))).andExpect(status().isBadRequest());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM asignaciones",Integer.class));
    }
    @Test void liberarPermiteVolverAReservar() {
        service.asignar(solicitud("uno",18,20));
        service.liberar("uno");
        assertNotNull(service.asignar(solicitud("dos",18,20)));
    }
}
