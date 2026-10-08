package cl.reservas.auditoria;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class ConsumerRetryConfig {
    @Bean public RetryTemplate consumerRetry() {
        return RetryTemplate.builder().maxAttempts(3).fixedBackoff(250)
            .retryOn(TransientDataAccessException.class).build();
    }
}
