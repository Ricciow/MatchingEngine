package dev.ricciow.matchingengine.strategy;

import dev.ricciow.matchingengine.book.ExecutionResult;
import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;
import dev.ricciow.matchingengine.policy.FollowPricePolicy;
import dev.ricciow.matchingengine.policy.PriceUpdatePolicy;

import java.util.List;
import java.util.Optional;

/** Adds a passive order that follows the reference price on its side. */
public final class PegStrategy implements OrderStrategy {
    private final long quantity;
    private final PriceUpdatePolicy priceUpdatePolicy = new FollowPricePolicy();

    public PegStrategy(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.quantity = quantity;
    }

    @Override
    public ExecutionResult execute(OrderBook book, Side side) {
        double price = priceUpdatePolicy.resolvePrice(book, side);
        return new ExecutionResult(Optional.of(book.addOrder(side, price, quantity, priceUpdatePolicy)), List.of());
    }
}
