package dev.ricciow.matchingengine.book;

import dev.ricciow.matchingengine.policy.FollowPricePolicy;
import dev.ricciow.matchingengine.policy.PriceUpdatePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static dev.ricciow.matchingengine.book.Side.BUY;
import static dev.ricciow.matchingengine.book.Side.SELL;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Atualização de preços por policy no livro")
class OrderBookPricePolicyTest {
    @Test
    void CrossedPegsFollowWinningBuyLimitWhenOfferLimitIsAbsent() {
        OrderBook book = new OrderBook();
        Order limit = book.addOrder(BUY, 10, 100);
        Order buyPeg = book.executeOrder(BUY,
                new dev.ricciow.matchingengine.strategy.PegStrategy(50, SELL)).order().orElseThrow();
        assertEquals(0.01, buyPeg.price());
        Order sellPeg = book.executeOrder(SELL,
                new dev.ricciow.matchingengine.strategy.PegStrategy(50, BUY)).order().orElseThrow();
        assertEquals(10, sellPeg.price());
        assertEquals(10, book.getOrderById(buyPeg.id()).orElseThrow().price());
        assertEquals(java.util.List.of(new dev.ricciow.matchingengine.trade.Trade(10, 50)),
                book.ConsumeOrders(SELL, 50));
        assertEquals(0.01, book.getOrderById(buyPeg.id()).orElseThrow().price());
        assertEquals(limit, book.getOrderById(limit.id()).orElseThrow());
    }

    @Test
    void SameSidePegFollowsCrossSidePegWithoutSustainingStalePrice() {
        OrderBook book = new OrderBook();
        book.addOrder(BUY, 9, 200);
        Order sellLimit = book.addOrder(SELL, 12, 200);
        Order bidPeg = book.executeOrder(BUY,
                new dev.ricciow.matchingengine.strategy.PegStrategy(50, BUY)).order().orElseThrow();
        Order offerPeg = book.executeOrder(BUY,
                new dev.ricciow.matchingengine.strategy.PegStrategy(50, SELL)).order().orElseThrow();
        assertEquals(12, book.getOrderById(bidPeg.id()).orElseThrow().price());
        assertEquals(12, offerPeg.price());
        book.addOrder(BUY, 8, 10);
        assertEquals(12, book.getOrderById(bidPeg.id()).orElseThrow().price());
        book.updateOrder(sellLimit.id(), 11, 200);
        assertEquals(11, book.getOrderById(bidPeg.id()).orElseThrow().price());
        assertEquals(11, book.getOrderById(offerPeg.id()).orElseThrow().price());
        book.ConsumeOrders(SELL, 200);
        assertEquals(9, book.getOrderById(bidPeg.id()).orElseThrow().price());
        assertEquals(0.01, book.getOrderById(offerPeg.id()).orElseThrow().price());
        book.addOrder(BUY, 7, 10);
        assertEquals(9, book.getOrderById(bidPeg.id()).orElseThrow().price());
        book.removeOrder(offerPeg.id());
        assertEquals(9, book.getOrderById(bidPeg.id()).orElseThrow().price());
    }

    @Test
    void CrossedPegsUseOldestAnchorAndReelectAfterRemoval() {
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
            OrderBook book = new OrderBook();
            Order buyLimit = book.addOrder(BUY, 10, 100);
            book.addOrder(SELL, 20, 100);
            Order oldest = book.addOrder(SELL, 10, 50, new FollowPricePolicy(BUY));
            Order buyPeg = book.addOrder(BUY, 10, 50, new FollowPricePolicy(SELL));
            Order nextSellPeg = book.addOrder(SELL, 10, 50, new FollowPricePolicy(BUY));

            assertEquals(10, book.getOrderById(buyPeg.id()).orElseThrow().price());
            book.updateOrder(buyLimit.id(), 12, 100);
            assertEquals(12, book.getOrderById(buyPeg.id()).orElseThrow().price());
            assertEquals(12, book.getOrderById(nextSellPeg.id()).orElseThrow().price());

            book.removeOrder(buyLimit.id());
            assertEquals(0.01, book.getOrderById(buyPeg.id()).orElseThrow().price());
            book.removeOrder(oldest.id());
            // The BUY Peg is now oldest; its SELL Limit reference anchors the cycle.
            assertEquals(20, book.getOrderById(buyPeg.id()).orElseThrow().price());
            assertEquals(20, book.getOrderById(nextSellPeg.id()).orElseThrow().price());
            assertEquals(buyPeg.priority(), book.getOrderById(buyPeg.id()).orElseThrow().priority());
        });
    }

    @Test
    void CrossedPegsWithoutLimitsTerminateAtMinimumPrice() {
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
            OrderBook book = new OrderBook();
            Order buyPeg = book.addOrder(BUY, 0.01, 50, new FollowPricePolicy(SELL));
            Order sellPeg = book.addOrder(SELL, 0.01, 50, new FollowPricePolicy(BUY));
            Order limit = book.addOrder(SELL, 10, 100);
            assertEquals(10, book.getOrderById(buyPeg.id()).orElseThrow().price());
            assertEquals(10, book.getOrderById(sellPeg.id()).orElseThrow().price());
            book.removeOrder(limit.id());
            assertEquals(0.01, book.getOrderById(buyPeg.id()).orElseThrow().price());
            assertEquals(0.01, book.getOrderById(sellPeg.id()).orElseThrow().price());
        });
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
    @DisplayName("Ordens com policy ignoram até preços inválidos informados na atualização")
    void BookIgnoresUpdatePriceForPolicyOrders(double price) {
        OrderBook book = new OrderBook();
        PriceUpdatePolicy policy = (currentBook, side) -> 10.00;
        Order order = book.addOrder(BUY, 10.00, 100, policy);

        Order updated = book.updateOrder(order.id(), price, 200).orElseThrow();

        assertEquals(new Order(order.id(), order.priority(), BUY, 10.00, 200, policy), updated);
        assertEquals(updated, book.bestOrder(BUY).orElseThrow());
        assertEquals(updated, book.getOrderById(order.id()).orElseThrow());
    }

    @Test
    @DisplayName("Fill parcial e alteração de quantidade não recalculam os preços das Pegs")
    void BookUpdatesPolicyPricesAfterPriceChanges() {
        OrderBook book = new OrderBook();
        int[] updates = {0, 0};
        FollowPricePolicy buyFollow = new FollowPricePolicy(BUY);
        FollowPricePolicy sellFollow = new FollowPricePolicy(SELL);
        PriceUpdatePolicy buyPolicy = (currentBook, side) -> {
            updates[0]++;
            return buyFollow.resolvePrice(currentBook, side);
        };
        PriceUpdatePolicy sellPolicy = (currentBook, side) -> {
            updates[1]++;
            return sellFollow.resolvePrice(currentBook, side);
        };
        Order buyLimit = book.addOrder(BUY, 10.00, 100);
        Order sellLimit = book.addOrder(SELL, 20.00, 100);
        Order buyPolicyOrder = book.addOrder(BUY, 10.00, 50, buyPolicy);
        Order sellPolicyOrder = book.addOrder(SELL, 20.00, 50, sellPolicy);
        int initialBuyUpdates = updates[0];
        int initialSellUpdates = updates[1];

        book.ConsumeOrders(BUY, 50);
        book.updateOrder(buyLimit.id(), 10.00, 200);
        book.updateOrder(buyPolicyOrder.id(), 12.00, 100);

        assertArrayEquals(new int[]{initialBuyUpdates, initialSellUpdates}, updates);

        book.updateOrder(buyLimit.id(), 11.00, 200);

        assertTrue(updates[0] > initialBuyUpdates);
        assertEquals(initialSellUpdates, updates[1]);
        assertEquals(11.00, book.getOrderById(buyPolicyOrder.id()).orElseThrow().price());
        int buyUpdates = updates[0];
        assertEquals(sellPolicyOrder, book.getOrderById(sellPolicyOrder.id()).orElseThrow());
        Order unchangedBuyOrder = book.getOrderById(buyPolicyOrder.id()).orElseThrow();

        book.updateOrder(sellLimit.id(), 19.00, 100);

        assertEquals(buyUpdates, updates[0]);
        assertTrue(updates[1] > initialSellUpdates);
        assertEquals(19.00, book.getOrderById(sellPolicyOrder.id()).orElseThrow().price());
        assertEquals(unchangedBuyOrder, book.getOrderById(buyPolicyOrder.id()).orElseThrow());

        book.ConsumeOrders(BUY, 200);

        assertEquals(0.01, book.getOrderById(buyPolicyOrder.id()).orElseThrow().price());
        assertEquals(100, book.getOrderById(buyPolicyOrder.id()).orElseThrow().quantity());
    }
}
