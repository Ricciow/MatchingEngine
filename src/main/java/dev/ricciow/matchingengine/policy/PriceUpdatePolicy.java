package dev.ricciow.matchingengine.policy;

import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;

public interface PriceUpdatePolicy {
    double resolvePrice(OrderBook book, Side side);
}
