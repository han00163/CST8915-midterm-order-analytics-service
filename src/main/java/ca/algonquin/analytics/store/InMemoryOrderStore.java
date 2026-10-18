package ca.algonquin.analytics.store;

import ca.algonquin.analytics.model.OrderMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryOrderStore implements OrderStore {
    private final List<OrderMessage> orders = new ArrayList<>();

    @Override
    public synchronized void append(OrderMessage order) {
        orders.add(Objects.requireNonNull(order));
    }

    @Override
    public synchronized List<OrderMessage> snapshot() {
        // Readers get one immutable view while the listener can keep appending.
        return List.copyOf(orders);
    }
}
