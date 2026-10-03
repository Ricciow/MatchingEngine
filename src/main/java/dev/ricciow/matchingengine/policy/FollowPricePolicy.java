package dev.ricciow.matchingengine.policy;

import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;
import dev.ricciow.matchingengine.book.Order;

/** Resolves Pegs into effective Limit prices, breaking cycles by arrival priority. */
public final class FollowPricePolicy implements PriceUpdatePolicy {
    private static final double baseReference = 0.01;
    private final Side referenceSide;

    public FollowPricePolicy(Side referenceSide) {
        this.referenceSide = referenceSide;
    }

    @Override
    public double resolvePrice(OrderBook book, Side side) {
        var bidLimit = book.bestLimitPrice(Side.BUY);
        var offerLimit = book.bestLimitPrice(Side.SELL);
        double bid = bidLimit.orElse(baseReference);
        double offer = offerLimit.orElse(baseReference);
        Order buyPeg = oldestCrossPeg(book, Side.BUY);
        Order sellPeg = oldestCrossPeg(book, Side.SELL);

        if (buyPeg != null && sellPeg != null) {
            if (!(bidLimit.isPresent() && offerLimit.isPresent() && bid > offer)) {
                Order oldest = buyPeg.priority() < sellPeg.priority() ? buyPeg : sellPeg;
                Side effectiveSide = oldest.priceUpdatePolicy().referenceSide(oldest.side());
                double anchor = book.bestLimitPrice(effectiveSide).orElse(baseReference);
                if (bidLimit.isPresent()) {
                    anchor = Math.max(anchor, bid);
                }
                if (offerLimit.isPresent()) {
                    anchor = Math.min(anchor, offer);
                }
                bid = offer = anchor;
            }
        } else if (buyPeg != null) {
            bid = bidLimit.isPresent() ? Math.max(bid, offer) : offer;
        } else if (sellPeg != null) {
            offer = offerLimit.isPresent() ? Math.min(offer, bid) : bid;
        }
        return referenceSide == Side.BUY ? bid : offer;
    }

    private static Order oldestCrossPeg(OrderBook book, Side side) {
        for (var id : book.getPricePolicyOrderIds().values()) {
            Order order = book.getOrderById(id).orElseThrow();
            if (order.side() == side
                    && order.priceUpdatePolicy() instanceof FollowPricePolicy policy
                    && policy.referenceSide != side) {
                return order;
            }
        }
        return null;
    }

    @Override
    public Side referenceSide(Side orderSide) {
        return referenceSide;
    }
}
