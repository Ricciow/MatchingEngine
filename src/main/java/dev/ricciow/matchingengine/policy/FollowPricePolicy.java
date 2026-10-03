package dev.ricciow.matchingengine.policy;

import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;

/** Follows the best Limit price on the reference side, or 0.01 without a reference. */
public final class FollowPricePolicy implements PriceUpdatePolicy {
    private static final double baseReference = 0.01;
    private final Side referenceSide;

    public FollowPricePolicy(Side referenceSide) {
        this.referenceSide = referenceSide;
    }

    @Override
    public double resolvePrice(OrderBook book, Side side) {
        return book.bestLimitPrice(referenceSide).orElse(baseReference);
    }

    @Override
    public Side referenceSide(Side orderSide) {
        return referenceSide;
    }
}
