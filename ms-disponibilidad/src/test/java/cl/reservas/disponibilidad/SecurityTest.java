package cl.reservas.disponibilidad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:seguridad-test;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
class SecurityTest {
    @MockitoBean JwtDecoder jwtDecoder;
    @Autowired MockMvc mvc;

    @Test void peticionSinTokenEs401() throws Exception {
        mvc.perform(post("/asignaciones").contentType("application/json")
                .content("""
                    {"reservaId":"sin-token","fecha":"2026-12-01","horaInicio":"18:00","horaFin":"20:00","cantidadPersonas":4}
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test void actuatorEsPublico() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
