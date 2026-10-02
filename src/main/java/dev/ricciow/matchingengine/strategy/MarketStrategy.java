package dev.ricciow.matchingengine.strategy;

import dev.ricciow.matchingengine.book.Order;
import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;

import java.util.Optional;

/** Executes against the opposite side only when the full quantity is available. */
public final class MarketStrategy implements OrderStrategy {
    private final long quantity;

    public MarketStrategy(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.quantity = quantity;
    }

    @Override
    public Optional<Order> execute(OrderBook book, Side side) {
        Side opposite = switch (side) {
            case BUY -> Side.SELL;
            case SELL -> Side.BUY;
        };
        book.ConsumeOrders(opposite, quantity);
        return Optional.empty();
    }
}
