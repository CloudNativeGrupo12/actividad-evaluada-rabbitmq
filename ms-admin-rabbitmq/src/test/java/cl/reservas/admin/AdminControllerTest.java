package cl.reservas.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import({RabbitAdminService.class, SecurityConfig.class, AdminExceptionHandler.class})
@WithMockUser
class AdminControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean RabbitAdmin rabbitAdmin;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test void creaColaConContratoParaAngular() throws Exception {
        mvc.perform(post("/api/admin/queues").contentType("application/json").content("""
            {"name":"prueba.cola","durable":true,"args":{"x-message-ttl":5000}}
            """))
            .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/admin/queues/prueba.cola"))
            .andExpect(jsonPath("$.name").value("prueba.cola")).andExpect(jsonPath("$.durable").value(true));
        verify(rabbitAdmin).declareQueue(argThat(q -> q.isDurable() && q.getArguments().get("x-message-ttl").equals(5000)));
    }
    @ParameterizedTest @ValueSource(strings={"{}", "{\"name\":\"   \"}", "{\"name\":\"amq.reservada\"}",
        "{\"name\":\"a/b\"}", "{\"name\":\"demo\",\"args\":{\"x-message-ttl\":-1}}",
        "{\"name\":\"demo\",\"args\":{\"x-message-ttl\":1.5}}",
        "{\"name\":\"demo\",\"args\":{\"x-expires\":0}}",
        "{\"name\":\"demo\",\"args\":{\"x-message-ttl\":\"500\"}}",
        "{\"name\":\"demo\",\"args\":{\"desconocido\":true}}"})
    void rechazaColasInvalidasSinContactarBroker(String body) throws Exception {
        mvc.perform(post("/api/admin/queues").contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").exists());
        verifyNoInteractions(rabbitAdmin);
    }
    @Test void eliminaCola() throws Exception {
        when(rabbitAdmin.deleteQueue("prueba.cola")).thenReturn(true);
        mvc.perform(delete("/api/admin/queues/prueba.cola")).andExpect(status().isNoContent());
        verify(rabbitAdmin).deleteQueue("prueba.cola");
    }
    @Test void colaInexistenteEs404() throws Exception {
        mvc.perform(delete("/api/admin/queues/no.existe")).andExpect(status().isNotFound());
    }
    @Test void creaYEliminaExchange() throws Exception {
        mvc.perform(post("/api/admin/exchanges").contentType("application/json")
            .content("{\"name\":\"prueba.exchange\",\"type\":\"fanout\",\"durable\":true}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("fanout"));
        verify(rabbitAdmin).declareExchange(any());
        when(rabbitAdmin.deleteExchange("prueba.exchange")).thenReturn(true);
        mvc.perform(delete("/api/admin/exchanges/prueba.exchange")).andExpect(status().isNoContent());
    }
    @Test void rechazaTipoDeExchangeInvalido() throws Exception {
        mvc.perform(post("/api/admin/exchanges").contentType("application/json")
            .content("{\"name\":\"demo\",\"type\":\"otro\"}" )).andExpect(status().isBadRequest());
        verifyNoInteractions(rabbitAdmin);
    }
    @Test void creaYEliminaBindingConClaveVaciaParaFanout() throws Exception {
        String body="{\"queue\":\"demo.cola\",\"exchange\":\"demo.exchange\",\"routingKey\":\"\"}";
        mvc.perform(post("/api/admin/bindings").contentType("application/json").content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.routingKey").value(""));
        mvc.perform(delete("/api/admin/bindings").contentType("application/json").content(body)).andExpect(status().isNoContent());
        verify(rabbitAdmin).declareBinding(any(Binding.class));
        verify(rabbitAdmin).removeBinding(any(Binding.class));
    }
    @Test void rechazaBindingInvalido() throws Exception {
        mvc.perform(post("/api/admin/bindings").contentType("application/json").content("{\"queue\":\"demo\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(rabbitAdmin);
    }
    @Test void brokerCaidoEs503() throws Exception {
        when(rabbitAdmin.declareQueue(any(Queue.class))).thenThrow(new AmqpConnectException(new java.net.ConnectException()));
        mvc.perform(post("/api/admin/queues").contentType("application/json").content("{\"name\":\"demo\"}"))
            .andExpect(status().isServiceUnavailable());
    }
    @Test @WithAnonymousUser void exigeAutenticacion() throws Exception {
        mvc.perform(post("/api/admin/queues").contentType("application/json").content("{\"name\":\"demo\"}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(rabbitAdmin);
    }
}
