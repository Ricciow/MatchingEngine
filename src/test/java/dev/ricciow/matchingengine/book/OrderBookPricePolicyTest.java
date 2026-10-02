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
        FollowPricePolicy follow = new FollowPricePolicy();
        PriceUpdatePolicy buyPolicy = (currentBook, side) -> {
            updates[0]++;
            return follow.resolvePrice(currentBook, side);
        };
        PriceUpdatePolicy sellPolicy = (currentBook, side) -> {
            updates[1]++;
            return follow.resolvePrice(currentBook, side);
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
