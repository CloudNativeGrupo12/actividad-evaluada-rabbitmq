package cl.reservas.notificaciones;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.mail.MailSendException;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class ConsumerRetryConfig {
    // Tres intentos dentro de la misma entrega; nunca requeue infinito.
    @Bean public RetryTemplate consumerRetry() {
        return RetryTemplate.builder().maxAttempts(3).fixedBackoff(250)
            .retryOn(TransientDataAccessException.class).retryOn(MailSendException.class).build();
    }
}
