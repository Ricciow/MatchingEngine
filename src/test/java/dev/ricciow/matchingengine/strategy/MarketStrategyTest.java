package dev.ricciow.matchingengine.strategy;

import dev.ricciow.matchingengine.book.ExecutionResult;
import dev.ricciow.matchingengine.book.Order;
import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;
import dev.ricciow.matchingengine.trade.Trade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static dev.ricciow.matchingengine.book.Side.BUY;
import static dev.ricciow.matchingengine.book.Side.SELL;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Estratégia Market")
class MarketStrategyTest {
    private final OrderBook book = new OrderBook();

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Market em livro vazio lança erro sem criar ordens")
    void MarketRejectsEmptyBook(Side side) {
        assertThrows(IllegalStateException.class, () -> book.executeOrder(side, new MarketStrategy(100)));

        assertTrue(book.getOrders(BUY).isEmpty());
        assertTrue(book.getOrders(SELL).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Market não consome ordens do próprio lado")
    void MarketOnlyConsumesOppositeOrders(Side side) {
        Order existing = book.executeOrder(side, new LimitStrategy(100, 10.50)).order().orElseThrow();

        assertThrows(IllegalStateException.class, () -> book.executeOrder(side, new MarketStrategy(100)));

        assertEquals(List.of(existing), book.getOrders(side));
        assertEquals(existing, book.getOrderById(existing.id()).orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Market retorna os trades do lado oposto sem deixar uma ordem no livro")
    void MarketExecutesOppositeOrdersWithoutResting(Side side) {
        Side opposite = opposite(side);
        Order sameSide = book.executeOrder(side, new LimitStrategy(100, 10.50)).order().orElseThrow();
        Order existing = book.executeOrder(opposite, new LimitStrategy(100, 10.50)).order().orElseThrow();

        ExecutionResult result = book.executeOrder(side, new MarketStrategy(100));

        assertTrue(result.order().isEmpty());
        assertEquals(List.of(new Trade(10.50, 100)), result.trades());
        assertTrue(book.getOrderById(existing.id()).isEmpty());
        assertTrue(book.getOrders(opposite).isEmpty());
        assertEquals(List.of(sameSide), book.getOrders(side));
        assertEquals(sameSide, book.getOrderById(sameSide.id()).orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Market com liquidez insuficiente lança erro sem consumir nenhuma ordem")
    void MarketRejectsInsufficientLiquidity(Side side) {
        Side opposite = opposite(side);
        Order sameSide = book.executeOrder(side, new LimitStrategy(100, 10.50)).order().orElseThrow();
        Order first = book.executeOrder(opposite, new LimitStrategy(50, 10.50)).order().orElseThrow();
        Order second = book.executeOrder(opposite, new LimitStrategy(75, 10.50)).order().orElseThrow();

        assertThrows(IllegalStateException.class, () -> book.executeOrder(side, new MarketStrategy(200)));

        assertEquals(first, book.getOrderById(first.id()).orElseThrow());
        assertEquals(second, book.getOrderById(second.id()).orElseThrow());
        assertEquals(List.of(first, second), book.getOrders(opposite));
        assertEquals(List.of(sameSide), book.getOrders(side));
    }

    @Test
    @DisplayName("Reutilizar Market executa novamente a quantidade original")
    void MarketCanReuseStrategy() {
        book.executeOrder(SELL, new LimitStrategy(300, 10.50));
        MarketStrategy strategy = new MarketStrategy(100);

        ExecutionResult first = book.executeOrder(BUY, strategy);
        assertEquals(200, book.bestOrder(SELL).orElseThrow().quantity());
        ExecutionResult second = book.executeOrder(BUY, strategy);

        assertEquals(List.of(new Trade(10.50, 100)), first.trades());
        assertEquals(first.trades(), second.trades());
        assertEquals(100, book.bestOrder(SELL).orElseThrow().quantity());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Market retorna um trade por fill, incluindo o último fill parcial")
    void MarketReturnsTradesForEachFill(Side side) {
        Side opposite = opposite(side);
        book.executeOrder(opposite, new LimitStrategy(100, 10.50));
        Order second = book.executeOrder(opposite, new LimitStrategy(200, 10.50)).order().orElseThrow();

        ExecutionResult result = book.executeOrder(side, new MarketStrategy(150));

        assertTrue(result.order().isEmpty());
        assertEquals(List.of(new Trade(10.50, 100), new Trade(10.50, 50)), result.trades());
        assertEquals(150, book.getOrderById(second.id()).orElseThrow().quantity());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("Market rejeita quantidades não positivas")
    void MarketRejectsNonPositiveQuantities(long quantity) {
        assertThrows(IllegalArgumentException.class, () -> new MarketStrategy(quantity));
    }

    private Side opposite(Side side) {
        return side == BUY ? SELL : BUY;
    }
}
