package ca.algonquin.analytics;

import ca.algonquin.analytics.model.OrderMessage;
import ca.algonquin.analytics.model.OrderMessage.Product;
import ca.algonquin.analytics.service.AnalyticsService;
import ca.algonquin.analytics.store.InMemoryOrderStore;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsServiceTest {
    static OrderMessage order(String name, int quantity, String price, String total) {
        return new OrderMessage(new Product(1L, name, new BigDecimal(price)),
                quantity, new BigDecimal(total));
    }

    @Test
    void emptyResultsHaveZeroRevenueAndNoDivisionByZero() {
        var analytics = new AnalyticsService(new InMemoryOrderStore());
        assertThat(analytics.orders()).isEmpty();
        assertThat(analytics.summary()).isEqualTo(new AnalyticsService.Summary(
                0, new BigDecimal("0.00"), new BigDecimal("0.00")));
        assertThat(analytics.products()).isEmpty();
        assertThat(analytics.topProducts()).isEmpty();
    }

    @Test
    void repeatedOrdersAreRetainedAndMoneyUsesDecimalArithmetic() {
        var store = new InMemoryOrderStore();
        store.append(order("Cat Food", 2, "9.99", "19.98"));
        store.append(order("Cat Food", 1, "9.99", "9.99"));
        store.append(order("Dog Leash", 3, "24.99", "74.97"));
        var analytics = new AnalyticsService(store);
        assertThat(analytics.orders()).hasSize(3);
        assertThat(analytics.summary()).isEqualTo(new AnalyticsService.Summary(
                3, new BigDecimal("104.94"), new BigDecimal("34.98")));
        assertThat(analytics.products()).containsExactly(
                new AnalyticsService.ProductAnalytics("Cat Food", 2, new BigDecimal("29.97")),
                new AnalyticsService.ProductAnalytics("Dog Leash", 1, new BigDecimal("74.97")));
    }

    @Test
    void topThreeRanksQuantityRatherThanOrderCountOrRevenueAndBreaksTiesByName() {
        var store = new InMemoryOrderStore();
        store.append(order("Expensive", 1, "900", "900"));
        store.append(order("Frequent", 1, "1", "1"));
        store.append(order("Frequent", 1, "1", "1"));
        store.append(order("Bulk", 10, "0.10", "1.00"));
        store.append(order("Alpha", 3, "1", "3"));
        store.append(order("Beta", 3, "1", "3"));
        assertThat(new AnalyticsService(store).topProducts()).containsExactly(
                new AnalyticsService.TopProduct("Bulk", 10),
                new AnalyticsService.TopProduct("Alpha", 3),
                new AnalyticsService.TopProduct("Beta", 3));
    }

    @Test
    void averagesRoundHalfUpAndASecondInstanceStartsEmpty() {
        var store = new InMemoryOrderStore();
        store.append(order("A", 1, "0.10", "0.10"));
        store.append(order("B", 1, "0.20", "0.20"));
        store.append(order("C", 1, "0.20", "0.20"));
        assertThat(new AnalyticsService(store).summary().averageOrderValue())
                .isEqualByComparingTo("0.17");
        assertThat(new InMemoryOrderStore().snapshot()).isEmpty();
    }

    @Test
    void snapshotsAreImmutableAndConcurrentAppendsDoNotLoseOrders() throws Exception {
        var store = new InMemoryOrderStore();
        var executor = Executors.newFixedThreadPool(4);
        try {
            for (int i = 0; i < 1000; i++) {
                executor.submit(() -> store.append(order("A", 1, "0.10", "0.10")));
            }
        } finally {
            executor.shutdown();
        }
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        List<OrderMessage> snapshot = store.snapshot();
        assertThat(snapshot).hasSize(1000);
        assertThatThrownBy(() -> snapshot.add(order("B", 1, "1", "1")))
                .isInstanceOf(UnsupportedOperationException.class);
        store.append(order("B", 1, "1", "1"));
        assertThat(snapshot).hasSize(1000);
        assertThat(new AnalyticsService(store).summary().totalRevenue()).isEqualByComparingTo("101.00");
    }
}
