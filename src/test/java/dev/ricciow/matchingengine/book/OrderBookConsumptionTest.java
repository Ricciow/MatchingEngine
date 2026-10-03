package dev.ricciow.matchingengine.book;

import dev.ricciow.matchingengine.trade.Trade;
import dev.ricciow.matchingengine.strategy.PegStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static dev.ricciow.matchingengine.book.Side.BUY;
import static dev.ricciow.matchingengine.book.Side.SELL;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Consumo de ordens do livro")
class OrderBookConsumptionTest {
    private final OrderBook book = new OrderBook();

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Consumir uma ordem inteira retorna seu trade e atualiza o preço da Peg")
    void BookConsumesOneOrderAndUpdatesPegPrice(Side side) {
        Order first = book.addOrder(side, 10.00, 100);
        double nextPrice = side == BUY ? 9.00 : 11.00;
        Order next = book.addOrder(side, nextPrice, 200);
        Order peg = book.executeOrder(side, new PegStrategy(50, side)).order().orElseThrow();

        Trade trade = book.ConsumeOrder(first.id(), first.quantity());

        assertEquals(new Trade(10.00, 100), trade);
        assertTrue(book.getOrderById(first.id()).isEmpty());
        Order updatedPeg = book.getOrderById(peg.id()).orElseThrow();
        assertEquals(nextPrice, updatedPeg.price());
        assertEquals(peg.priority(), updatedPeg.priority());
        assertEquals(50, updatedPeg.quantity());
        assertEquals(List.of(next, updatedPeg), book.getOrders(side));
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Consumir parte de uma ordem preserva sua prioridade e o preço da Peg")
    void BookConsumesPartOfOneOrder(Side side) {
        Order order = book.addOrder(side, 10.00, 100);
        Order peg = book.executeOrder(side, new PegStrategy(50, side)).order().orElseThrow();

        assertEquals(new Trade(10.00, 40), book.ConsumeOrder(order.id(), 40));

        Order remaining = new Order(order.id(), order.priority(), side, 10.00, 60);
        assertEquals(remaining, book.getOrderById(order.id()).orElseThrow());
        assertEquals(List.of(remaining, peg), book.getOrders(side));
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Consumir por UUID remove a ordem indicada mesmo quando ela não é a melhor do lado")
    void BookConsumesSpecifiedOrderInsteadOfBestOrder(Side side) {
        Order best = book.addOrder(side, 10.00, 200);
        double worsePrice = side == BUY ? 9.00 : 11.00;
        Order target = book.addOrder(side, worsePrice, 100);

        assertEquals(new Trade(worsePrice, 100), book.ConsumeOrder(target.id(), 100));

        assertTrue(book.getOrderById(target.id()).isEmpty());
        assertEquals(best, book.getOrderById(best.id()).orElseThrow());
        assertEquals(List.of(best), book.getOrders(side));
    }

    @Test
    @DisplayName("Consumir uma ordem limita o trade à quantidade disponível")
    void BookConsumesAtMostOneOrderQuantity() {
        Order order = book.addOrder(SELL, 10.00, 100);

        assertEquals(new Trade(10.00, 100), book.ConsumeOrder(order.id(), 101));

        assertTrue(book.getOrderById(order.id()).isEmpty());
        assertTrue(book.getOrders(SELL).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("Consumir uma ordem rejeita quantidades não positivas sem alterar o livro")
    void BookRejectsNonPositiveSingleOrderConsumption(long quantity) {
        Order order = book.addOrder(SELL, 10.00, 100);

        assertThrows(IllegalArgumentException.class, () -> book.ConsumeOrder(order.id(), quantity));

        assertEquals(List.of(order), book.getOrders(SELL));
        assertEquals(order, book.getOrderById(order.id()).orElseThrow());
    }

    @Test
    @DisplayName("Consumir uma ordem inexistente ou já consumida rejeita a operação sem alterar o livro")
    void BookRejectsConsumptionOfMissingOrders() {
        Order buy = book.addOrder(BUY, 10.00, 200);
        Order sell = book.addOrder(SELL, 11.00, 100);
        Order consumed = book.addOrder(SELL, 12.00, 50);
        book.ConsumeOrder(consumed.id(), 50);

        assertThrows(IllegalArgumentException.class, () -> book.ConsumeOrder(UUID.randomUUID(), 10));
        assertThrows(IllegalArgumentException.class, () -> book.ConsumeOrder(consumed.id(), 10));

        assertEquals(List.of(buy), book.getOrders(BUY));
        assertEquals(List.of(sell), book.getOrders(SELL));
        assertEquals(buy, book.getOrderById(buy.id()).orElseThrow());
        assertEquals(sell, book.getOrderById(sell.id()).orElseThrow());
        assertTrue(book.getOrderById(consumed.id()).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Consumir um lado vazio retorna uma lista vazia sem alterar o livro")
    void BookReturnsNoTradesWithoutLiquidity(Side side) {
        assertTrue(book.ConsumeOrders(side, 100).isEmpty());
        assertTrue(book.getOrders(BUY).isEmpty());
        assertTrue(book.getOrders(SELL).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("O livro consome por preço e prioridade e agrupa os trades por preço")
    void BookConsumesOrdersByPriceAndPriority(Side side) {
        double bestPrice = side == BUY ? 11.00 : 10.00;
        double worstPrice = side == BUY ? 10.00 : 11.00;
        Order worst = book.addOrder(side, worstPrice, 100);
        Order first = book.addOrder(side, bestPrice, 50);
        Order second = book.addOrder(side, bestPrice, 100);

        List<Trade> trades = book.ConsumeOrders(side, 175);

        assertEquals(List.of(new Trade(bestPrice, 150), new Trade(worstPrice, 25)), trades);
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
    @DisplayName("O livro consome apenas a liquidez disponível no lado informado")
    void BookConsumesAvailableLiquidity(Side side) {
        Order first = book.addOrder(side, 10.50, 100);
        Order second = book.addOrder(side, 11.00, 200);
        Side opposite = side == BUY ? SELL : BUY;
        Order otherSide = book.addOrder(opposite, 10.50, 1_000);
        List<Trade> expected = side == BUY
                ? List.of(new Trade(11.00, 200), new Trade(10.50, 100))
                : List.of(new Trade(10.50, 100), new Trade(11.00, 200));

        assertEquals(expected, book.ConsumeOrders(side, 301));

        assertTrue(book.getOrders(side).isEmpty());
        assertEquals(List.of(otherSide), book.getOrders(opposite));
        assertTrue(book.getOrderById(first.id()).isEmpty());
        assertTrue(book.getOrderById(second.id()).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Consumir toda a liquidez remove as ordens e preserva o outro lado")
    void BookConsumesExactlyTheAvailableQuantity(Side side) {
        Order first = book.addOrder(side, 10.50, 100);
        Order second = book.addOrder(side, 10.50, 200);
        Side opposite = side == BUY ? SELL : BUY;
        Order otherSide = book.addOrder(opposite, 10.50, 50);

        assertEquals(List.of(new Trade(10.50, 300)), book.ConsumeOrders(side, 300));
        assertTrue(book.getOrders(side).isEmpty());
        assertTrue(book.bestOrder(side).isEmpty());
        assertTrue(book.getOrderById(first.id()).isEmpty());
        assertTrue(book.getOrderById(second.id()).isEmpty());
        assertEquals(List.of(otherSide), book.getOrders(opposite));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("Quantidades de consumo não positivas são rejeitadas sem alterar o livro")
    void BookRejectsNonPositiveConsumption(long quantity) {
        Order order = book.addOrder(SELL, 10.50, 100);

        assertThrows(IllegalArgumentException.class, () -> book.ConsumeOrders(SELL, quantity));

        assertEquals(List.of(order), book.getOrders(SELL));
        assertEquals(order, book.getOrderById(order.id()).orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("O consumo suporta quantidades grandes sem overflow")
    void BookConsumesLargeQuantitiesWithoutOverflow(Side side) {
        Order first = book.addOrder(side, 10.50, Long.MAX_VALUE);
        Order second = book.addOrder(side, 10.50, Long.MAX_VALUE);

        assertEquals(List.of(new Trade(10.50, Long.MAX_VALUE)), book.ConsumeOrders(side, Long.MAX_VALUE));

        assertTrue(book.getOrderById(first.id()).isEmpty());
        assertEquals(List.of(second), book.getOrders(side));
    }

}
