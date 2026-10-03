package dev.ricciow.matchingengine.book;

import dev.ricciow.matchingengine.strategy.OrderStrategy;
import dev.ricciow.matchingengine.policy.PriceUpdatePolicy;
import dev.ricciow.matchingengine.policy.FollowPricePolicy;
import dev.ricciow.matchingengine.trade.Trade;
import java.util.ArrayList;
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
    private final NavigableMap<Long, UUID> pricePolicyOrderIds = new TreeMap<>();

    private long nextPriority = 1;

    /** Executes an order using its strategy. */
    public ExecutionResult executeOrder(Side side, OrderStrategy strategy) {
        return strategy.execute(this, side);
    }

    /** Adds a resting order without matching it against the opposite side. */
    public Order addOrder(Side side, double price, long quantity) {
        return addOrder(side, price, quantity, null);
    }

    public Order addOrder(Side side, double price, long quantity, PriceUpdatePolicy priceUpdatePolicy) {
        UUID id = UUID.randomUUID();
        Order order = new Order(id, nextPriority, side, price, quantity, priceUpdatePolicy);
        storeOrder(order);

        if (priceUpdatePolicy != null) {
            pricePolicyOrderIds.put(order.priority(), id);
        }

        nextPriority++;
        updatePolicyPrices(side);
        return ordersById.get(id);
    }

    public Optional<Order> bestOrder(Side side) {
        Map.Entry<OrderKey, Order> entry = ordersFor(side).firstEntry();
        return entry == null ? Optional.empty() : Optional.of(entry.getValue());
    }

    /** Returns the best price on this side, excluding orders with a price policy. */
    public OptionalDouble bestLimitPrice(Side side) {
        for (Order order : ordersFor(side).values()) {
            if (order.priceUpdatePolicy() == null) {
                return OptionalDouble.of(order.price());
            }
        }
        return OptionalDouble.empty();
    }

    public List<Order> getOrders(Side side) {
        return List.copyOf(ordersFor(side).values());
    }

    public Optional<Order> getOrderById(UUID id) {
        return Optional.ofNullable(ordersById.get(id));
    }

    public NavigableMap<Long, UUID> getPricePolicyOrderIds() {
        return new TreeMap<>(pricePolicyOrderIds);
    }

    /** Updates price and quantity while preserving identity, side and arrival priority. */
    public Optional<Order> updateOrder(UUID id, double price, long quantity) {
        Order order = ordersById.get(id);
        if (order == null) return Optional.empty();

        if (order.priceUpdatePolicy() != null) {
            price = order.price();
        }
        Order updated = new Order(id, order.priority(), order.side(), price, quantity,
                order.priceUpdatePolicy());
        storeOrder(updated);

        if (price != order.price()) {
            updatePolicyPrices(order.side());
        }
        return getOrderById(id);
    }

    public Optional<Order> removeOrder(UUID id) {
        Order order = ordersById.remove(id);
        if (order == null) return Optional.empty();

        ordersFor(order.side()).remove(OrderKey.from(order));

        if (order.priceUpdatePolicy() != null) {
            pricePolicyOrderIds.remove(order.priority());
        }
        updatePolicyPrices(order.side());
        return Optional.of(order);
    }

    /** Consumes up to the requested quantity, preserving any unfilled remainder. */
    public Trade ConsumeOrder(UUID id, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        Order order = getOrderById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        long filled = Math.min(quantity, order.quantity());
        if (filled == order.quantity()) {
            removeOrder(id);
        } else {
            updateOrder(id, order.price(), order.quantity() - filled);
        }
        return new Trade(order.price(), filled);
    }

    /** Consumes the current best orders, refreshing prices after fully consumed orders. */
    public List<Trade> ConsumeOrders(Side side, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        List<Trade> trades = new ArrayList<>();
        while (quantity > 0 && !ordersFor(side).isEmpty()) {
            Order order = bestOrder(side).orElseThrow();
            Trade trade = ConsumeOrder(order.id(), quantity);
            trades.add(trade);
            quantity -= trade.quantity();
        }
        return consolidateTrades(trades);
    }

    /** Merges multiple trades of the same price into one */
    private List<Trade> consolidateTrades(List<Trade> trades) {
        List<Trade> consolidated = new ArrayList<>();
        double lastPrice = 0;
        long lastQty = 0;
        for (Trade trade : trades) {
            if (lastQty > 0 && trade.price() != lastPrice) {
                consolidated.add(new Trade(lastPrice, lastQty));
                lastQty = 0;
            }
            lastPrice = trade.price();
            lastQty += trade.quantity();
        }
        if (lastQty > 0) {
            consolidated.add(new Trade(lastPrice, lastQty));
        }
        return consolidated;
    }

    /** Reprices orders that follow the changed side. */
    private void updatePolicyPrices(Side side) {
        Map<UUID, Double> prices = new HashMap<>();
        List<Order> policyOrders = pricePolicyOrderIds.values().stream().map(ordersById::get).toList();
        for (Order order : policyOrders) {
            if (order.priceUpdatePolicy() instanceof FollowPricePolicy
                    || order.priceUpdatePolicy().referenceSide(order.side()) == side) {
                prices.put(order.id(), order.priceUpdatePolicy().resolvePrice(this, order.side()));
            }
        }
        for (UUID id : pricePolicyOrderIds.values()) {
            Order order = ordersById.get(id);
            double price = prices.getOrDefault(id, order.price());
            if (price != order.price()) {
                storeOrder(new Order(id, order.priority(), order.side(), price, order.quantity(), order.priceUpdatePolicy()));
            }
        }
    }

    /** Internal update method for orders */
    private void storeOrder(Order order) {
        Order previous = ordersById.put(order.id(), order);
        if (previous != null) {
            ordersFor(previous.side()).remove(OrderKey.from(previous));
        }
        ordersFor(order.side()).put(OrderKey.from(order), order);
    }

    private NavigableMap<OrderKey, Order> ordersFor(Side side) {
        return switch (side) {
            case BUY -> buys;
            case SELL -> sells;
        };
    }
}
