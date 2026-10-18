package ca.algonquin.analytics.service;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BrokerHealth {
    private final RabbitTemplate rabbit;
    private final String queue;

    public BrokerHealth(RabbitTemplate rabbit, @Value("${orders.queue}") String queue) {
        this.rabbit = rabbit;
        this.queue = queue;
    }

    public boolean isAvailable() {
        try {
            return Boolean.TRUE.equals(rabbit.execute(channel -> {
                channel.queueDeclarePassive(queue);
                return true;
            }));
        } catch (AmqpException error) {
            return false;
        }
    }
}
