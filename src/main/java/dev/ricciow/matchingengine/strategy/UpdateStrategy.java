package dev.ricciow.matchingengine.strategy;

import dev.ricciow.matchingengine.book.ExecutionResult;
import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;

import java.util.List;
import java.util.UUID;

/** Updates a resting order's price and quantity by UUID. */
public final class UpdateStrategy implements OrderStrategy {
    private final UUID id;
    private final double price;
    private final long quantity;

    public UpdateStrategy(UUID id, double price, long quantity) {
        this.id = id;
        this.price = price;
        this.quantity = quantity;
    }

    @Override
    public ExecutionResult execute(OrderBook book, Side side) {
        return new ExecutionResult(book.updateOrder(id, price, quantity), List.of());
    }
}
