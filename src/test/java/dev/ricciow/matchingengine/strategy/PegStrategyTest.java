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

@DisplayName("Estratégia Peg")
class PegStrategyTest {
    private final OrderBook book = new OrderBook();

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Peg segue a melhor Limit do próprio lado sem executar trades")
    void PegFollowsBestLimitOnSameSide(Side side) {
        double bestPrice = side == BUY ? 11.00 : 10.00;
        double worstPrice = side == BUY ? 10.00 : 11.00;
        Order worst = book.addOrder(side, worstPrice, 100);
        Order best = book.addOrder(side, bestPrice, 200);
        Side opposite = side == BUY ? SELL : BUY;
        Order otherSide = book.addOrder(opposite, side == BUY ? 9.00 : 12.00, 300);

        ExecutionResult result = book.executeOrder(side, new PegStrategy(150));
        Order peg = result.order().orElseThrow();

        assertEquals(side, peg.side());
        assertEquals(bestPrice, peg.price());
        assertEquals(150, peg.quantity());
        assertTrue(result.trades().isEmpty());
        assertEquals(peg, book.getOrderById(peg.id()).orElseThrow());
        assertEquals(List.of(best, peg, worst), book.getOrders(side));
        assertEquals(List.of(otherSide), book.getOrders(opposite));
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Pegs sem Limits usam 0.01 e acompanham a primeira referência")
    void PegUsesMinimumPriceWithoutLimits(Side side) {
        PegStrategy strategy = new PegStrategy(150);
        Order first = book.executeOrder(side, strategy).order().orElseThrow();
        Order second = book.executeOrder(side, strategy).order().orElseThrow();

        assertEquals(0.01, first.price());
        assertEquals(0.01, second.price());
        assertTrue(book.bestLimitPrice(side).isEmpty());

        Order limit = book.addOrder(side, 10.00, 300);
        Order updatedFirst = book.getOrderById(first.id()).orElseThrow();
        Order updatedSecond = book.getOrderById(second.id()).orElseThrow();

        assertEquals(10.00, updatedFirst.price());
        assertEquals(10.00, updatedSecond.price());
        assertEquals(List.of(updatedFirst, updatedSecond, limit), book.getOrders(side));
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Peg mantém sua chegada original quando uma nova Limit melhora a referência")
    void PegKeepsArrivalPriorityWhenReferenceImproves(Side side) {
        double initialPrice = side == BUY ? 10.00 : 10.50;
        double improvedPrice = side == BUY ? 10.10 : 10.40;
        double worsePrice = side == BUY ? 9.99 : 10.60;
        Order originalLimit = book.addOrder(side, initialPrice, 200);
        Order worse = book.addOrder(side, worsePrice, 100);
        Order peg = book.executeOrder(side, new PegStrategy(150)).order().orElseThrow();

        Order improved = book.executeOrder(side, new LimitStrategy(300, improvedPrice)).order().orElseThrow();
        Order updated = book.getOrderById(peg.id()).orElseThrow();

        assertEquals(new Order(peg.id(), peg.priority(), side, improvedPrice, 150,
                peg.priceUpdatePolicy()), updated);
        assertEquals(List.of(updated, improved, originalLimit, worse), book.getOrders(side));
        assertEquals(initialPrice, peg.price());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Cancelar a melhor Limit muda a referência sem deixar a Peg sustentar seu preço")
    void PegFollowsNextLimitAfterCancellation(Side side) {
        double nextPrice = side == BUY ? 9.00 : 11.00;
        Order best = book.addOrder(side, 10.00, 150);
        Order next = book.addOrder(side, nextPrice, 30);
        Order peg = book.executeOrder(side, new PegStrategy(50)).order().orElseThrow();

        book.executeOrder(side, new CancelStrategy(best.id()));

        Order updated = book.getOrderById(peg.id()).orElseThrow();
        assertEquals(nextPrice, updated.price());
        assertEquals(List.of(next, updated), book.getOrders(side));

        book.removeOrder(next.id());

        assertEquals(0.01, book.getOrderById(peg.id()).orElseThrow().price());
        assertTrue(book.bestLimitPrice(side).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Alterar o preço da melhor Limit reposiciona as Pegs")
    void PegFollowsReferenceAfterLimitUpdate(Side side) {
        double nextPrice = side == BUY ? 9.00 : 11.00;
        double worsePrice = side == BUY ? 8.00 : 12.00;
        Order best = book.addOrder(side, 10.00, 100);
        Order next = book.addOrder(side, nextPrice, 200);
        Order peg = book.executeOrder(side, new PegStrategy(150)).order().orElseThrow();

        Order updatedLimit = book.executeOrder(side, new UpdateStrategy(best.id(), worsePrice, 300))
                .order().orElseThrow();
        Order updatedPeg = book.getOrderById(peg.id()).orElseThrow();

        assertEquals(nextPrice, updatedPeg.price());
        assertEquals(List.of(next, updatedPeg, updatedLimit), book.getOrders(side));
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Atualizar a Peg ignora o preço informado e preserva o acompanhamento da referência")
    void PegIgnoresManualPriceAndKeepsFollowingAfterUpdate(Side side) {
        Order limit = book.addOrder(side, 10.00, 100);
        Order peg = book.executeOrder(side, new PegStrategy(150)).order().orElseThrow();

        ExecutionResult result = book.executeOrder(side, new UpdateStrategy(peg.id(), 12.00, 200));
        Order updated = result.order().orElseThrow();

        assertEquals(new Order(peg.id(), peg.priority(), side, 10.00, 200,
                peg.priceUpdatePolicy()), updated);
        assertEquals(updated, book.getOrderById(peg.id()).orElseThrow());
        assertEquals(List.of(limit, updated), book.getOrders(side));
        assertTrue(result.trades().isEmpty());

        double improvedPrice = side == BUY ? 11.00 : 9.00;
        book.addOrder(side, improvedPrice, 100);
        Order repriced = book.getOrderById(peg.id()).orElseThrow();

        assertEquals(improvedPrice, repriced.price());
        assertEquals(200, repriced.quantity());
        assertEquals(peg.priority(), repriced.priority());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Cancelar uma Peg remove seu registro e permite ajustar as Pegs restantes")
    void PegStopsFollowingAfterCancellation(Side side) {
        book.addOrder(side, 10.00, 100);
        Order cancelled = book.executeOrder(side, new PegStrategy(50)).order().orElseThrow();
        Order remaining = book.executeOrder(side, new PegStrategy(150)).order().orElseThrow();

        book.executeOrder(side, new CancelStrategy(cancelled.id()));
        double improvedPrice = side == BUY ? 11.00 : 9.00;
        Order improved = book.addOrder(side, improvedPrice, 100);
        Order updated = book.getOrderById(remaining.id()).orElseThrow();

        assertTrue(book.getOrderById(cancelled.id()).isEmpty());
        assertEquals(improvedPrice, updated.price());
        assertEquals(List.of(updated, improved), book.getOrders(side).subList(0, 2));
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Market usa o novo preço das Pegs e respeita a chegada após cada fill")
    void PegFollowsReferenceDuringMarket(Side side) {
        double nextPrice = side == BUY ? 9.00 : 11.00;
        Order best = book.addOrder(side, 10.00, 150);
        Order next = book.addOrder(side, nextPrice, 30);
        Order peg = book.executeOrder(side, new PegStrategy(50)).order().orElseThrow();
        Side marketSide = side == BUY ? SELL : BUY;

        ExecutionResult result = book.executeOrder(marketSide, new MarketStrategy(160));
        Order remaining = book.getOrderById(peg.id()).orElseThrow();
        Order remainingLimit = book.getOrderById(next.id()).orElseThrow();

        assertEquals(List.of(new Trade(10.00, 150), new Trade(nextPrice, 10)), result.trades());
        assertTrue(book.getOrderById(best.id()).isEmpty());
        assertEquals(new Order(peg.id(), peg.priority(), side, nextPrice, 50,
                peg.priceUpdatePolicy()), remaining);
        assertEquals(20, remainingLimit.quantity());
        assertEquals(List.of(remainingLimit, remaining), book.getOrders(side));

        double improvedPrice = side == BUY ? 11.00 : 9.00;
        book.addOrder(side, improvedPrice, 100);
        assertEquals(improvedPrice, book.getOrderById(peg.id()).orElseThrow().price());
        assertEquals(50, book.getOrderById(peg.id()).orElseThrow().quantity());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Peg usa 0.01 assim que a última Limit é consumida e fill total remove seu registro")
    void PegUsesMinimumPriceDuringMarketAndStopsFollowingAfterFullFill(Side side) {
        book.addOrder(side, 10.00, 100);
        Order peg = book.executeOrder(side, new PegStrategy(200)).order().orElseThrow();
        Side marketSide = side == BUY ? SELL : BUY;

        ExecutionResult first = book.executeOrder(marketSide, new MarketStrategy(150));
        Order remaining = book.getOrderById(peg.id()).orElseThrow();

        assertEquals(List.of(new Trade(10.00, 100), new Trade(0.01, 50)), first.trades());
        assertEquals(0.01, remaining.price());
        assertEquals(150, remaining.quantity());

        ExecutionResult second = book.executeOrder(marketSide, new MarketStrategy(200));
        Order limit = book.addOrder(side, 12.00, 100);

        assertEquals(List.of(new Trade(0.01, 150)), second.trades());
        assertTrue(book.getOrderById(peg.id()).isEmpty());
        assertEquals(List.of(limit), book.getOrders(side));
    }

    @Test
    @DisplayName("Market mantém a sequência quando um preço reaparece após a Peg perder sua referência")
    void PegKeepsTradeSequenceWhenPriceReturnsDuringMarket() {
        book.addOrder(SELL, 0.01, 100);
        book.addOrder(SELL, 10.00, 100);
        Order peg = book.executeOrder(SELL, new PegStrategy(100)).order().orElseThrow();

        ExecutionResult result = book.executeOrder(BUY, new MarketStrategy(250));

        assertEquals(List.of(new Trade(0.01, 100), new Trade(10.00, 100), new Trade(0.01, 50)),
                result.trades());
        Order remaining = book.getOrderById(peg.id()).orElseThrow();
        assertEquals(0.01, remaining.price());
        assertEquals(50, remaining.quantity());
        assertEquals(peg.priority(), remaining.priority());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("Peg rejeita quantidades não positivas")
    void PegRejectsNonPositiveQuantities(long quantity) {
        assertThrows(IllegalArgumentException.class, () -> new PegStrategy(quantity));
    }
}
