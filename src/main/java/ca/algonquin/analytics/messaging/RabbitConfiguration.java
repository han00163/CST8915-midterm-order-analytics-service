package ca.algonquin.analytics.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    @Bean
    Queue orderQueue(@Value("${orders.queue}") String name) {
        // Match the existing order-service: durable, non-exclusive, not auto-delete.
        return QueueBuilder.durable(name).build();
    }
}
