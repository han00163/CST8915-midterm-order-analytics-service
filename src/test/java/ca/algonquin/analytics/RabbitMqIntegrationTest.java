package ca.algonquin.analytics;

import ca.algonquin.analytics.store.OrderStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@EnabledIfEnvironmentVariable(named = "RABBITMQ_TEST_ADDRESS", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RabbitMqIntegrationTest {
    private static final String QUEUE = "analytics-test-" + UUID.randomUUID();
    @Autowired RabbitTemplate rabbit;
    @Autowired OrderStore store;
    @Autowired TestRestTemplate http;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry properties) {
        properties.add("spring.rabbitmq.addresses", () -> System.getenv("RABBITMQ_TEST_ADDRESS"));
        properties.add("orders.queue", () -> QUEUE);
    }

    @Test
    void consumesRawMessagesFromRealBrokerAndServesAnalyticsOverHttp() {
        try {
            await().atMost(Duration.ofSeconds(30)).ignoreExceptions().untilAsserted(() ->
                    assertThat(http.getForEntity("/health", JsonNode.class).getStatusCode().value())
                            .isEqualTo(200));
            rabbit.send("", QUEUE, raw("not JSON"));
            String order = """
                    {"product":{"id":1,"name":"Cat Food","price":9.99},"quantity":2,"totalPrice":19.98}
                    """;
            rabbit.send("", QUEUE, raw(order));
            rabbit.send("", QUEUE, raw(order));
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                    assertThat(store.snapshot()).hasSize(2));
            JsonNode summary = http.getForObject("/analytics/summary", JsonNode.class);
            assertThat(summary.path("totalRevenue").decimalValue()).isEqualByComparingTo("39.96");
            assertThat(summary.path("totalOrders").asInt()).isEqualTo(2);
            assertThat(http.getForObject("/orders", JsonNode.class).size()).isEqualTo(2);
            assertThat(http.getForObject("/analytics/products", JsonNode.class)
                    .get(0).path("orderCount").asInt()).isEqualTo(2);
            assertThat(http.getForObject("/analytics/top-products", JsonNode.class)
                    .get(0).path("totalQuantity").asInt()).isEqualTo(4);
            // The valid orders were acknowledged and the poison message did not block them.
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                Integer count = rabbit.execute(channel -> channel.queueDeclarePassive(QUEUE).getMessageCount());
                assertThat(count).isZero();
            });
        } finally {
            rabbit.execute(channel -> { channel.queueDelete(QUEUE); return null; });
        }
    }

    private Message raw(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8));
    }
}
