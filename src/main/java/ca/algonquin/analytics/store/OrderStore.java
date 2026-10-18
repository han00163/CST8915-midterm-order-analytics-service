package ca.algonquin.analytics.store;

import ca.algonquin.analytics.model.OrderMessage;
import java.util.List;

/** Persistence boundary: a database implementation can replace the memory store. */
public interface OrderStore {
    void append(OrderMessage order);
    List<OrderMessage> snapshot();
}
