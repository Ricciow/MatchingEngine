package dev.ricciow.matchingengine.strategy;

import dev.ricciow.matchingengine.book.Order;
import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static dev.ricciow.matchingengine.book.Side.BUY;
import static dev.ricciow.matchingengine.book.Side.SELL;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Estratégia Limit")
class LimitStrategyTest {
    private final OrderBook book = new OrderBook();

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Limit insere uma ordem no livro vazio com o preço informado")
    void LimitAddsOrderToEmptyBook(Side side) {
        Order order = book.executeOrder(side, new LimitStrategy(100, 10.50)).orElseThrow();

        assertNotNull(order.id());
        assertEquals(side, order.side());
        assertEquals(10.50, order.price());
        assertEquals(100, order.quantity());
        assertEquals(Optional.of(order), book.getOrderById(order.id()));
        assertEquals(List.of(order), book.getOrders(side));
        assertTrue(book.getOrders(side == BUY ? SELL : BUY).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Limit preserva ordens do lado oposto quando os preços não cruzam")
    void LimitKeepsOppositeOrdersUnchanged(Side side) {
        Side opposite = side == BUY ? SELL : BUY;
        double oppositePrice = side == BUY ? 10.50 : 10.00;
        double price = side == BUY ? 10.49 : 10.01;
        Order existing = book.executeOrder(opposite, new LimitStrategy(200, oppositePrice)).orElseThrow();

        Order added = book.executeOrder(side, new LimitStrategy(100, price)).orElseThrow();

        assertEquals(List.of(existing), book.getOrders(opposite));
        assertEquals(List.of(added), book.getOrders(side));
        assertEquals(Optional.of(existing), book.getOrderById(existing.id()));
    }

    @ParameterizedTest
    @CsvSource({
        "BUY, 10.50, 10.50",
        "BUY, 10.50, 10.51",
        "SELL, 10.00, 10.00",
        "SELL, 10.00, 9.99"
    })
    @DisplayName("Limit insere ordens com preços cruzados sem executar fill")
    void LimitAddsCrossingOrdersWithoutFilling(
            Side side, double oppositePrice, double crossingPrice
    ) {
        Side opposite = side == BUY ? SELL : BUY;
        Order existing = book.executeOrder(opposite, new LimitStrategy(200, oppositePrice)).orElseThrow();

        Order added = book.executeOrder(side, new LimitStrategy(100, crossingPrice)).orElseThrow();

        assertEquals(crossingPrice, added.price());
        assertEquals(100, added.quantity());
        assertEquals(List.of(added), book.getOrders(side));
        assertEquals(Optional.of(added), book.getOrderById(added.id()));
        assertEquals(List.of(existing), book.getOrders(opposite));
        assertEquals(Optional.of(existing), book.getOrderById(existing.id()));
        assertEquals(existing.priority() + 1, added.priority());
    }

    @ParameterizedTest
    @CsvSource({"BUY, 100", "BUY, 400", "SELL, 100", "SELL, 400"})
    @DisplayName("Limit preserva todas as quantidades mesmo quando os preços cruzam")
    void LimitPreservesQuantitiesWhenPricesCross(Side side, long quantity) {
        Side opposite = side == BUY ? SELL : BUY;
        double firstPrice = side == BUY ? 10.50 : 10.00;
        double secondPrice = side == BUY ? 11.00 : 9.99;
        Order first = book.executeOrder(opposite, new LimitStrategy(100, firstPrice)).orElseThrow();
        Order second = book.executeOrder(opposite, new LimitStrategy(200, secondPrice)).orElseThrow();
        double restingPrice = side == BUY ? 9.00 : 11.50;
        Order resting = book.executeOrder(side, new LimitStrategy(50, restingPrice)).orElseThrow();
        List<Order> oppositeBefore = book.getOrders(opposite);
        double crossingPrice = side == BUY ? 11.00 : 9.99;

        Order added = book.executeOrder(side, new LimitStrategy(quantity, crossingPrice)).orElseThrow();

        assertEquals(crossingPrice, added.price());
        assertEquals(quantity, added.quantity());
        assertEquals(oppositeBefore, book.getOrders(opposite));
        assertEquals(List.of(added, resting), book.getOrders(side));
        assertEquals(Optional.of(added), book.getOrderById(added.id()));
        assertEquals(Optional.of(first), book.getOrderById(first.id()));
        assertEquals(Optional.of(second), book.getOrderById(second.id()));
        assertEquals(Optional.of(resting), book.getOrderById(resting.id()));
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Limits no mesmo preço mantem a prioridade de chegada")
    void LimitKeepsSamePriceOrdersInArrivalOrder(Side side) {
        Order first = book.executeOrder(side, new LimitStrategy(100, 10.50)).orElseThrow();
        Order second = book.executeOrder(side, new LimitStrategy(200, 10.50)).orElseThrow();
        Order third = book.executeOrder(side, new LimitStrategy(300, 10.50)).orElseThrow();

        assertEquals(List.of(first, second, third), book.getOrders(side));
        assertTrue(first.priority() < second.priority());
        assertTrue(second.priority() < third.priority());
    }

    @Test
    @DisplayName("Limits usam a sequência de prioridade compartilhada pelos dois lados")
    void LimitUsesPriorityAcrossBothSides() {
        Order buy = book.executeOrder(BUY, new LimitStrategy(100, 10.00)).orElseThrow();
        Order sell = book.executeOrder(SELL, new LimitStrategy(200, 10.50)).orElseThrow();
        Order nextBuy = book.executeOrder(BUY, new LimitStrategy(300, 10.00)).orElseThrow();

        assertTrue(buy.priority() < sell.priority());
        assertTrue(sell.priority() < nextBuy.priority());
        assertEquals(List.of(buy, nextBuy), book.getOrders(BUY));
        assertEquals(List.of(sell), book.getOrders(SELL));
    }

    @Test
    @DisplayName("Reutilizar a estratégia cria ordens com IDs e prioridades distintos")
    void LimitCreatesDistinctOrdersWhenStrategyIsReused() {
        OrderStrategy strategy = new LimitStrategy(100, 10.50);

        Order first = book.executeOrder(BUY, strategy).orElseThrow();
        Order second = book.executeOrder(BUY, strategy).orElseThrow();

        assertNotEquals(first.id(), second.id());
        assertTrue(first.priority() < second.priority());
        assertEquals(List.of(first, second), book.getOrders(BUY));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("Limit rejeita preços não positivos, NaN e infinitos")
    void LimitRejectsInvalidPrices(double price) {
        assertThrows(IllegalArgumentException.class, () -> new LimitStrategy(100, price));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("Limit rejeita quantidades não positivas")
    void LimitRejectsNonPositiveQuantities(long quantity) {
        assertThrows(IllegalArgumentException.class, () -> new LimitStrategy(quantity, 10.50));
    }

    @Test
    @DisplayName("Executar uma Limit com lado nulo não altera o livro")
    void LimitRejectsNullSide() {
        assertThrows(NullPointerException.class,
                () -> book.executeOrder(null, new LimitStrategy(100, 10.50)));
        assertTrue(book.getOrders(BUY).isEmpty());
        assertTrue(book.getOrders(SELL).isEmpty());
    }
}
