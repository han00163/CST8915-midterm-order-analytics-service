package ca.algonquin.analytics;

import ca.algonquin.analytics.messaging.OrderListener;
import ca.algonquin.analytics.store.InMemoryOrderStore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderListenerTest {
    @Test
    void acceptsNodeJsonWithoutAmqpJsonHeadersAndRejectsMalformedOrInvalidOrders() {
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var store = new InMemoryOrderStore();
            var mapper = JsonMapper.builder()
                    .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
            var listener = new OrderListener(mapper, validators.getValidator(), store);
            String valid = """
                    {"product":{"id":1,"name":"Cat Food","price":9.99},"quantity":2,"totalPrice":19.98}
                    """;
            listener.consume(message(valid));
            assertThat(store.snapshot()).hasSize(1);
            for (String invalid : new String[] {"not JSON", "null", "{}",
                    valid.replace("\"quantity\":2", "\"quantity\":0"),
                    valid.replace("\"quantity\":2", "\"quantity\":1.5"),
                    valid.replace("\"totalPrice\":19.98", "\"totalPrice\":-1"),
                    valid.replace("\"name\":\"Cat Food\"", "\"name\":\" \""),
                    valid + " {}"}) {
                assertThatThrownBy(() -> listener.consume(message(invalid)))
                        .isInstanceOf(AmqpRejectAndDontRequeueException.class);
            }
            assertThat(store.snapshot()).hasSize(1);
        }
    }

    private Message message(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8));
    }
}
