package ca.algonquin.analytics.service;

import ca.algonquin.analytics.model.OrderMessage;
import ca.algonquin.analytics.store.OrderStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {
    private final OrderStore store;

    public AnalyticsService(OrderStore store) {
        this.store = store;
    }

    public List<OrderMessage> orders() {
        return store.snapshot();
    }

    public Summary summary() {
        List<OrderMessage> snapshot = store.snapshot();
        BigDecimal total = snapshot.stream().map(OrderMessage::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = snapshot.isEmpty() ? money(BigDecimal.ZERO)
                : total.divide(BigDecimal.valueOf(snapshot.size()), 2, RoundingMode.HALF_UP);
        return new Summary(snapshot.size(), money(total), average);
    }

    public List<ProductAnalytics> products() {
        return grouped().entrySet().stream()
                .map(entry -> new ProductAnalytics(entry.getKey(), entry.getValue().count,
                        money(entry.getValue().revenue)))
                .toList();
    }

    public List<TopProduct> topProducts() {
        return grouped().entrySet().stream()
                .map(entry -> new TopProduct(entry.getKey(), entry.getValue().quantity))
                .sorted(Comparator.comparingLong(TopProduct::totalQuantity).reversed()
                        .thenComparing(TopProduct::productName))
                .limit(3)
                .toList();
    }

    private Map<String, Totals> grouped() {
        Map<String, Totals> result = new TreeMap<>();
        for (OrderMessage order : store.snapshot()) {
            Totals totals = result.computeIfAbsent(order.product().name(), ignored -> new Totals());
            totals.count++;
            totals.quantity += order.quantity();
            totals.revenue = totals.revenue.add(order.totalPrice());
        }
        return result;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class Totals {
        long count;
        long quantity;
        BigDecimal revenue = BigDecimal.ZERO;
    }

    public record Summary(long totalOrders, BigDecimal totalRevenue, BigDecimal averageOrderValue) { }
    public record ProductAnalytics(String productName, long orderCount, BigDecimal totalRevenue) { }
    public record TopProduct(String productName, long totalQuantity) { }
}
