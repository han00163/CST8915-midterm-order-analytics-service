package ca.algonquin.analytics.web;

import ca.algonquin.analytics.model.OrderMessage;
import ca.algonquin.analytics.service.AnalyticsService;
import ca.algonquin.analytics.service.BrokerHealth;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsController {
    private final AnalyticsService analytics;
    private final BrokerHealth brokerHealth;

    public AnalyticsController(AnalyticsService analytics, BrokerHealth brokerHealth) {
        this.analytics = analytics;
        this.brokerHealth = brokerHealth;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        boolean available = brokerHealth.isAvailable();
        return ResponseEntity.status(available ? 200 : 503).body(Map.of(
                "status", available ? "UP" : "DOWN",
                "rabbitmq", available ? "UP" : "DOWN",
                "storage", "in-memory"));
    }

    @GetMapping("/orders")
    public List<OrderMessage> orders() { return analytics.orders(); }

    @GetMapping("/analytics/summary")
    public AnalyticsService.Summary summary() { return analytics.summary(); }

    @GetMapping("/analytics/products")
    public List<AnalyticsService.ProductAnalytics> products() { return analytics.products(); }

    @GetMapping("/analytics/top-products")
    public List<AnalyticsService.TopProduct> topProducts() { return analytics.topProducts(); }
}
