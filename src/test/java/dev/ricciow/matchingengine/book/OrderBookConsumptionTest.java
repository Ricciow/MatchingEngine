package dev.ricciow.matchingengine.book;

import dev.ricciow.matchingengine.trade.Trade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static dev.ricciow.matchingengine.book.Side.BUY;
import static dev.ricciow.matchingengine.book.Side.SELL;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Consumo de ordens do livro")
class OrderBookConsumptionTest {
    private final OrderBook book = new OrderBook();

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Consumir um lado vazio lança erro sem alterar o livro")
    void BookRejectsConsumptionWithoutLiquidity(Side side) {
        assertFalse(book.canConsume(side, 100));
        assertThrows(IllegalStateException.class, () -> book.ConsumeOrders(side, 100));
        assertTrue(book.getOrders(BUY).isEmpty());
        assertTrue(book.getOrders(SELL).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("O livro consome por preço e prioridade e gera um trade por ordem preenchida")
    void BookConsumesOrdersByPriceAndPriority(Side side) {
        double bestPrice = side == BUY ? 11.00 : 10.00;
        double worstPrice = side == BUY ? 10.00 : 11.00;
        Order worst = book.addOrder(side, worstPrice, 100);
        Order first = book.addOrder(side, bestPrice, 50);
        Order second = book.addOrder(side, bestPrice, 100);

        List<Trade> trades = book.ConsumeOrders(side, 175);

        assertEquals(List.of(new Trade(bestPrice, 50), new Trade(bestPrice, 100), new Trade(worstPrice, 25)), trades);
        assertTrue(book.getOrderById(first.id()).isEmpty());
        assertTrue(book.getOrderById(second.id()).isEmpty());
        Order remaining = new Order(worst.id(), worst.priority(), side, worstPrice, 75);
        assertEquals(remaining, book.getOrderById(worst.id()).orElseThrow());
        assertEquals(List.of(remaining), book.getOrders(side));
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Consumo parcial preserva a ordem de chegada no mesmo preço")
    void BookKeepsPriorityAfterPartialConsumption(Side side) {
        Order first = book.addOrder(side, 10.50, 200);
        Order second = book.addOrder(side, 10.50, 100);

        assertEquals(List.of(new Trade(10.50, 50)), book.ConsumeOrders(side, 50));

        Order remaining = new Order(first.id(), first.priority(), side, 10.50, 150);
        assertEquals(List.of(remaining, second), book.getOrders(side));
        assertEquals(remaining, book.bestOrder(side).orElseThrow());
        assertEquals(remaining, book.getOrderById(first.id()).orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Liquidez insuficiente lança erro antes de consumir qualquer ordem")
    void BookRejectsInsufficientLiquidityWithoutConsumingOrders(Side side) {
        Order first = book.addOrder(side, 10.50, 100);
        Order second = book.addOrder(side, 11.00, 200);
        Side opposite = side == BUY ? SELL : BUY;
        Order otherSide = book.addOrder(opposite, 10.50, 1_000);
        List<Order> before = book.getOrders(side);

        assertThrows(IllegalStateException.class, () -> book.ConsumeOrders(side, 301));

        assertEquals(before, book.getOrders(side));
        assertEquals(List.of(otherSide), book.getOrders(opposite));
        assertEquals(first, book.getOrderById(first.id()).orElseThrow());
        assertEquals(second, book.getOrderById(second.id()).orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Consumir toda a liquidez remove as ordens e preserva o outro lado")
    void BookConsumesExactlyTheAvailableQuantity(Side side) {
        Order first = book.addOrder(side, 10.50, 100);
        Order second = book.addOrder(side, 10.50, 200);
        Side opposite = side == BUY ? SELL : BUY;
        Order otherSide = book.addOrder(opposite, 10.50, 50);

        assertEquals(List.of(new Trade(10.50, 100), new Trade(10.50, 200)), book.ConsumeOrders(side, 300));

        assertTrue(book.getOrders(side).isEmpty());
        assertTrue(book.bestPrice(side).isEmpty());
        assertTrue(book.getOrderById(first.id()).isEmpty());
        assertTrue(book.getOrderById(second.id()).isEmpty());
        assertEquals(List.of(otherSide), book.getOrders(opposite));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("Quantidades de consumo não positivas são rejeitadas sem alterar o livro")
    void BookRejectsNonPositiveConsumption(long quantity) {
        Order order = book.addOrder(SELL, 10.50, 100);

        assertFalse(book.canConsume(SELL, quantity));
        assertThrows(IllegalArgumentException.class, () -> book.ConsumeOrders(SELL, quantity));

        assertEquals(List.of(order), book.getOrders(SELL));
        assertEquals(order, book.getOrderById(order.id()).orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("A validação de liquidez suporta totais maiores que Long.MAX_VALUE")
    void BookValidatesLargeQuantitiesWithoutOverflow(Side side) {
        Order first = book.addOrder(side, 10.50, Long.MAX_VALUE);
        Order second = book.addOrder(side, 10.50, Long.MAX_VALUE);

        assertTrue(book.canConsume(side, Long.MAX_VALUE));
        assertEquals(List.of(new Trade(10.50, Long.MAX_VALUE)), book.ConsumeOrders(side, Long.MAX_VALUE));

        assertTrue(book.getOrderById(first.id()).isEmpty());
        assertEquals(List.of(second), book.getOrders(side));
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("canConsume verifica apenas o lado informado sem alterar as ordens")
    void BookChecksConsumptionWithoutChangingOrders(Side side) {
        Order first = book.addOrder(side, 10.50, 100);
        Order second = book.addOrder(side, 11.00, 200);
        Side opposite = side == BUY ? SELL : BUY;
        Order otherSide = book.addOrder(opposite, 10.50, 1_000);
        List<Order> before = book.getOrders(side);

        assertTrue(book.canConsume(side, 1));
        assertTrue(book.canConsume(side, 300));
        assertFalse(book.canConsume(side, 301));

        assertEquals(before, book.getOrders(side));
        assertEquals(List.of(otherSide), book.getOrders(opposite));
        assertEquals(first, book.getOrderById(first.id()).orElseThrow());
        assertEquals(second, book.getOrderById(second.id()).orElseThrow());
    }
}
