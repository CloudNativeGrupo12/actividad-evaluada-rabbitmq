package cl.reservas.admin;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class AdminControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean RabbitAdmin rabbitAdmin;

    @Test
    void creaColaViaRest() throws Exception {
        mvc.perform(post("/api/admin/queues").contentType("application/json")
                .content("""
                    {"name":"prueba.cola","durable":true}
                    """))
            .andExpect(status().isOk());
        verify(rabbitAdmin).declareQueue(any(Queue.class));
    }

    @Test
    void rechazaColaSinNombre() throws Exception {
        mvc.perform(post("/api/admin/queues").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        verify(rabbitAdmin, never()).declareQueue(any(Queue.class));
    }

    @Test
    void creaYEliminaExchangeViaRest() throws Exception {
        mvc.perform(post("/api/admin/exchanges").contentType("application/json")
                .content("""
                    {"name":"prueba.exchange","type":"fanout","durable":true}
                    """))
            .andExpect(status().isOk());
        verify(rabbitAdmin).declareExchange(any());
        mvc.perform(delete("/api/admin/exchanges/prueba.exchange"))
            .andExpect(status().isOk());
        verify(rabbitAdmin).deleteExchange("prueba.exchange");
    }
}
