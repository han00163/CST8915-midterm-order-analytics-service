package ca.algonquin.analytics.messaging;

import ca.algonquin.analytics.model.OrderMessage;
import ca.algonquin.analytics.store.OrderStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderListener {
    private static final Logger log = LoggerFactory.getLogger(OrderListener.class);
    private final ObjectMapper mapper;
    private final Validator validator;
    private final OrderStore store;

    public OrderListener(ObjectMapper mapper, Validator validator, OrderStore store) {
        this.mapper = mapper;
        this.validator = validator;
        this.store = store;
    }

    @RabbitListener(queues = "${orders.queue}")
    public void consume(Message message) {
        final OrderMessage order;
        try {
            // Read raw JSON bytes: Node.js publishers need not set content_type
            // or Spring-specific Java type headers.
            order = mapper.readValue(message.getBody(), OrderMessage.class);
        } catch (JsonProcessingException error) {
            throw invalid("Malformed order JSON", error);
        } catch (IOException error) {
            throw invalid("Cannot read order JSON", error);
        }
        if (order == null || !validator.validate(order).isEmpty()) {
            throw invalid("Order is missing valid product, quantity, or totalPrice", null);
        }
        store.append(order);
        // Spring sends the acknowledgement only after this method succeeds.
        log.debug("Stored order for product {}", order.product().name());
    }

    private AmqpRejectAndDontRequeueException invalid(String reason, Throwable error) {
        log.warn("Rejecting RabbitMQ message: {}", reason);
        return new AmqpRejectAndDontRequeueException(reason, error);
    }
}
