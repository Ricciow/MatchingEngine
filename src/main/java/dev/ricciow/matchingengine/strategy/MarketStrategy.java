package dev.ricciow.matchingengine.strategy;

import dev.ricciow.matchingengine.book.ExecutionResult;
import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;

import java.util.Optional;

/** Executes against the available liquidity on the opposite side. */
public final class MarketStrategy implements OrderStrategy {
    private final long quantity;

    public MarketStrategy(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.quantity = quantity;
    }

    @Override
    public ExecutionResult execute(OrderBook book, Side side) {
        Side opposite = switch (side) {
            case BUY -> Side.SELL;
            case SELL -> Side.BUY;
        };
        return new ExecutionResult(Optional.empty(), book.ConsumeOrders(opposite, quantity));
    }
}
