package dev.ricciow.matchingengine.strategy;

import dev.ricciow.matchingengine.book.Order;
import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;
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
        Order existing = book.executeOrder(side, new LimitStrategy(100, 10.50)).orElseThrow();

        assertThrows(IllegalStateException.class, () -> book.executeOrder(side, new MarketStrategy(100)));

        assertEquals(List.of(existing), book.getOrders(side));
        assertEquals(existing, book.getOrderById(existing.id()).orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Market executa no lado oposto, preserva o próprio lado e retorna vazio")
    void MarketExecutesOppositeOrdersWithoutResting(Side side) {
        Side opposite = opposite(side);
        Order sameSide = book.executeOrder(side, new LimitStrategy(100, 10.50)).orElseThrow();
        Order existing = book.executeOrder(opposite, new LimitStrategy(100, 10.50)).orElseThrow();

        assertTrue(book.executeOrder(side, new MarketStrategy(100)).isEmpty());

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
        Order sameSide = book.executeOrder(side, new LimitStrategy(100, 10.50)).orElseThrow();
        Order first = book.executeOrder(opposite, new LimitStrategy(50, 10.50)).orElseThrow();
        Order second = book.executeOrder(opposite, new LimitStrategy(75, 10.50)).orElseThrow();

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

        book.executeOrder(BUY, strategy);
        assertEquals(200, book.bestOrder(SELL).orElseThrow().quantity());
        book.executeOrder(BUY, strategy);

        assertEquals(100, book.bestOrder(SELL).orElseThrow().quantity());
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
