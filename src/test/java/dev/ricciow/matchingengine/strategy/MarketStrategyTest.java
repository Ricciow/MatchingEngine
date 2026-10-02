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
    @DisplayName("Market em livro vazio retorna um resultado sem ordem e sem trades")
    void MarketReturnsNoTradesOnEmptyBook(Side side) {
        ExecutionResult result = book.executeOrder(side, new MarketStrategy(100));

        assertTrue(result.order().isEmpty());
        assertTrue(result.trades().isEmpty());
        assertTrue(book.getOrders(BUY).isEmpty());
        assertTrue(book.getOrders(SELL).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Market não consome ordens do próprio lado")
    void MarketOnlyConsumesOppositeOrders(Side side) {
        Order existing = book.executeOrder(side, new LimitStrategy(100, 10.50)).order().orElseThrow();

        ExecutionResult result = book.executeOrder(side, new MarketStrategy(100));

        assertTrue(result.order().isEmpty());
        assertTrue(result.trades().isEmpty());
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
    @DisplayName("Market consome a liquidez disponível e descarta a quantidade não preenchida")
    void MarketConsumesAvailableLiquidity(Side side) {
        Side opposite = opposite(side);
        Order sameSide = book.executeOrder(side, new LimitStrategy(100, 10.50)).order().orElseThrow();
        Order first = book.executeOrder(opposite, new LimitStrategy(50, 10.50)).order().orElseThrow();
        Order second = book.executeOrder(opposite, new LimitStrategy(75, 10.50)).order().orElseThrow();

        ExecutionResult result = book.executeOrder(side, new MarketStrategy(200));

        assertTrue(result.order().isEmpty());
        assertEquals(List.of(new Trade(10.50, 125)), result.trades());
        assertTrue(book.getOrderById(first.id()).isEmpty());
        assertTrue(book.getOrderById(second.id()).isEmpty());
        assertTrue(book.getOrders(opposite).isEmpty());
        assertEquals(List.of(sameSide), book.getOrders(side));
    }

    @Test
    @DisplayName("Markets sucessivas executam até onde houver liquidez, conforme o exemplo do desafio")
    void MarketConsumesAvailableLiquidityAcrossExecutions() {
        book.executeOrder(BUY, new LimitStrategy(100, 10.00));
        Order firstSell = book.executeOrder(SELL, new LimitStrategy(100, 20.00)).order().orElseThrow();
        Order secondSell = book.executeOrder(SELL, new LimitStrategy(200, 20.00)).order().orElseThrow();

        ExecutionResult first = book.executeOrder(BUY, new MarketStrategy(150));

        assertEquals(List.of(new Trade(20.00, 150)), first.trades());
        assertTrue(book.getOrderById(firstSell.id()).isEmpty());
        assertEquals(150, book.getOrderById(secondSell.id()).orElseThrow().quantity());

        ExecutionResult second = book.executeOrder(BUY, new MarketStrategy(200));
        ExecutionResult third = book.executeOrder(SELL, new MarketStrategy(200));

        assertEquals(List.of(new Trade(20.00, 150)), second.trades());
        assertEquals(List.of(new Trade(10.00, 100)), third.trades());
        assertTrue(book.getOrders(BUY).isEmpty());
        assertTrue(book.getOrders(SELL).isEmpty());
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
    @DisplayName("Market soma os fills no mesmo preço em um único trade")
    void MarketReturnsTradesByPrice(Side side) {
        Side opposite = opposite(side);
        book.executeOrder(opposite, new LimitStrategy(100, 20.00));
        Order second = book.executeOrder(opposite, new LimitStrategy(200, 20.00)).order().orElseThrow();

        ExecutionResult result = book.executeOrder(side, new MarketStrategy(150));

        assertTrue(result.order().isEmpty());
        assertEquals(List.of(new Trade(20.00, 150)), result.trades());
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
