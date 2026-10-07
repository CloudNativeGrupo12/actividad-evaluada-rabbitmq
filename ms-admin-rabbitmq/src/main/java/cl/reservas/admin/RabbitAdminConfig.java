package cl.reservas.admin;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class RabbitAdminConfig {
    @Bean
    public ConnectionFactory connectionFactory(RabbitProperties properties) {
        var factory = new CachingConnectionFactory(properties.determineHost(), properties.determinePort());
        factory.setUsername(properties.determineUsername());
        factory.setPassword(properties.determinePassword());
        return factory;
    }
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
}
