package dev.ricciow.matchingengine.book;

import dev.ricciow.matchingengine.strategy.OrderStrategy;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Stores orders for one asset, ordered by price and arrival priority.
 */
public final class OrderBook {
    private record OrderKey(double price, long priority) {
        static OrderKey from(Order order) {
            return new OrderKey(order.price(), order.priority());
        }
    }

    // Orders by descending price, then ascending priority.
    private static final Comparator<OrderKey> BUY_PRIORITY =
            Comparator.comparingDouble(OrderKey::price)
                    .reversed()
                    .thenComparingLong(OrderKey::priority);
    // Orders by ascending price, then ascending priority.
    private static final Comparator<OrderKey> SELL_PRIORITY =
            Comparator.comparingDouble(OrderKey::price)
                    .thenComparingLong(OrderKey::priority);

    private final NavigableMap<OrderKey, Order> buys = new TreeMap<>(BUY_PRIORITY);
    private final NavigableMap<OrderKey, Order> sells = new TreeMap<>(SELL_PRIORITY);
    private final Map<UUID, Order> ordersById = new HashMap<>();

    private long nextPriority = 1;

    /** Executes an order using its strategy. */
    public Optional<Order> executeOrder(Side side, OrderStrategy strategy) {
        return strategy.execute(this, side);
    }

    /** Adds a resting order without matching it against the opposite side. */
    public Order addOrder(Side side, double price, long quantity) {
        UUID id = UUID.randomUUID();
        Order order = new Order(id, nextPriority, side, price, quantity);

        ordersFor(side).put(OrderKey.from(order), order);
        ordersById.put(id, order);
        nextPriority++;

        return order;
    }

    public Optional<Order> bestOrder(Side side) {
        Map.Entry<OrderKey, Order> entry = ordersFor(side).firstEntry();
        return entry == null ? Optional.empty() : Optional.of(entry.getValue());
    }

    public OptionalDouble bestPrice(Side side) {
        Optional<Order> order = bestOrder(side);
        return order.map(value -> OptionalDouble.of(value.price())).orElseGet(OptionalDouble::empty);
    }

    public List<Order> getOrders(Side side) {
        return List.copyOf(ordersFor(side).values());
    }

    public Optional<Order> getOrderById(UUID id) {
        return Optional.ofNullable(ordersById.get(id));
    }

    public Optional<Order> removeOrder(UUID id) {
        Order order = ordersById.remove(id);
        if (order == null) {
            return Optional.empty();
        }

        ordersFor(order.side()).remove(OrderKey.from(order));
        return Optional.of(order);
    }

    private NavigableMap<OrderKey, Order> ordersFor(Side side) {
        return switch (side) {
            case BUY -> buys;
            case SELL -> sells;
        };
    }
}
