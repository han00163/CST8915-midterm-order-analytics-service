package ca.algonquin.analytics;

import ca.algonquin.analytics.service.BrokerHealth;
import ca.algonquin.analytics.store.OrderStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.dynamic=false"})
class AnalyticsHttpTest {
    @Autowired TestRestTemplate http;
    @MockitoBean OrderStore store;
    @MockitoBean BrokerHealth health;

    @BeforeEach
    void seed() {
        when(store.snapshot()).thenReturn(List.of(
                AnalyticsServiceTest.order("Cat Food", 2, "9.99", "19.98"),
                AnalyticsServiceTest.order("Cat Food", 1, "9.99", "9.99")));
    }

    @Test
    void servesAllFiveGetEndpointsWithExpectedJsonShapes() {
        when(health.isAvailable()).thenReturn(true);
        var response = http.getForEntity("/health", JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
        assertThat(http.getForObject("/orders", JsonNode.class).size()).isEqualTo(2);
        JsonNode summary = http.getForObject("/analytics/summary", JsonNode.class);
        assertThat(summary.path("totalOrders").asInt()).isEqualTo(2);
        assertThat(summary.path("totalRevenue").decimalValue()).isEqualByComparingTo("29.97");
        assertThat(summary.path("averageOrderValue").decimalValue()).isEqualByComparingTo("14.99");
        JsonNode product = http.getForObject("/analytics/products", JsonNode.class).get(0);
        assertThat(product.path("productName").asText()).isEqualTo("Cat Food");
        assertThat(product.path("orderCount").asInt()).isEqualTo(2);
        assertThat(product.path("totalRevenue").decimalValue()).isEqualByComparingTo("29.97");
        JsonNode top = http.getForObject("/analytics/top-products", JsonNode.class);
        assertThat(top.size()).isEqualTo(1);
        assertThat(top.get(0).path("totalQuantity").asInt()).isEqualTo(3);
    }

    @Test
    void brokerOutageReturns503ButAlreadyCollectedAnalyticsRemainReadable() {
        when(health.isAvailable()).thenReturn(false);
        var response = http.getForEntity("/health", JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().path("rabbitmq").asText()).isEqualTo("DOWN");
        assertThat(http.getForEntity("/orders", JsonNode.class).getStatusCode().value()).isEqualTo(200);
    }
}
