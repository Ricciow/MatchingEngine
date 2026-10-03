package dev.ricciow.matchingengine.strategy;

import dev.ricciow.matchingengine.book.ExecutionResult;
import dev.ricciow.matchingengine.book.Order;
import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cancels a resting order by UUID. */
public final class CancelStrategy implements OrderStrategy {
    private final UUID id;

    public CancelStrategy(UUID id) {
        this.id = id;
    }

    @Override
    public ExecutionResult execute(OrderBook book, Side side) {
        Optional<Order> removedOrder = book.removeOrder(id);
        return new ExecutionResult(removedOrder, List.of());
    }
}
