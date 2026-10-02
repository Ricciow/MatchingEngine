package dev.ricciow.matchingengine.strategy;

import dev.ricciow.matchingengine.book.Order;
import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;

import java.util.Optional;

/**
 * Adds limit orders at their specified price without executing trades.
 */
public class LimitStrategy implements OrderStrategy {
    private final long quantity;
    private final double price;

    public LimitStrategy(long quantity, double price) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (!Double.isFinite(price) || price <= 0) {
            throw new IllegalArgumentException("Price must be finite and positive");
        }
        this.quantity = quantity;
        this.price = price;
    }

    @Override
    public Optional<Order> execute(OrderBook book, Side side) {
        return Optional.of(book.addOrder(side, price, quantity));
    }
}