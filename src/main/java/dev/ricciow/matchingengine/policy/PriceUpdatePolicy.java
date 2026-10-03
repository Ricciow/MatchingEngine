package dev.ricciow.matchingengine.policy;

import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;

public interface PriceUpdatePolicy {
    double resolvePrice(OrderBook book, Side side);

    /** Side whose price changes trigger repricing; defaults to the order's side. */
    default Side referenceSide(Side orderSide) {
        return orderSide;
    }
}
