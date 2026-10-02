package dev.ricciow.matchingengine.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.ricciow.matchingengine.book.Side.BUY;
import static dev.ricciow.matchingengine.book.Side.SELL;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Livro de ofertas")
class OrderBookTest {
    private final OrderBook book = new OrderBook();

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("O livro começa sem ordens ou melhor preço")
    void BookStartsEmpty(Side side) {
        assertTrue(book.getOrders(side).isEmpty());
        assertTrue(book.bestOrder(side).isEmpty());
        assertTrue(book.bestPrice(side).isEmpty());
    }

    @Test
    @DisplayName("Compras são ordenadas por maior preço e prioridade de chegada")
    void BookKeepsBuysSortedByPriceAndPriority() {
        Order lowest = book.addOrder(BUY, 9.99, 100);
        Order firstHighest = book.addOrder(BUY, 10.10, 200);
        Order middle = book.addOrder(BUY, 10.00, 300);
        Order secondHighest = book.addOrder(BUY, 10.10, 400);

        assertEquals(List.of(firstHighest, secondHighest, middle, lowest), book.getOrders(BUY));
        assertEquals(Optional.of(firstHighest), book.bestOrder(BUY));
        assertEquals(10.10, book.bestPrice(BUY).orElseThrow());
    }

    @Test
    @DisplayName("Vendas são ordenadas por menor preço e prioridade de chegada")
    void BookKeepsSellsSortedByPriceAndPriority() {
        Order highest = book.addOrder(SELL, 10.10, 100);
        Order firstLowest = book.addOrder(SELL, 9.99, 200);
        Order middle = book.addOrder(SELL, 10.00, 300);
        Order secondLowest = book.addOrder(SELL, 9.99, 400);

        assertEquals(List.of(firstLowest, secondLowest, middle, highest), book.getOrders(SELL));
        assertEquals(Optional.of(firstLowest), book.bestOrder(SELL));
        assertEquals(9.99, book.bestPrice(SELL).orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Ordens no mesmo preço mantêm a ordem de chegada")
    void BookKeepsSamePriceOrdersInArrivalOrder(Side side) {
        Order first = book.addOrder(side, 10.00, 100);
        Order second = book.addOrder(side, 10.00, 100);
        Order third = book.addOrder(side, 10.00, 100);

        assertEquals(List.of(first, second, third), book.getOrders(side));
        assertNotEquals(first.id(), second.id());
        assertNotEquals(second.id(), third.id());
    }

    @Test
    @DisplayName("A prioridade de chegada é compartilhada pelos dois lados")
    void BookAssignsPriorityAcrossBothSides() {
        Order buy = book.addOrder(BUY, 10.00, 100);
        Order sell = book.addOrder(SELL, 10.50, 200);
        Order nextBuy = book.addOrder(BUY, 10.00, 300);

        assertTrue(buy.priority() < sell.priority());
        assertTrue(sell.priority() < nextBuy.priority());
        assertEquals(List.of(buy, nextBuy), book.getOrders(BUY));
        assertEquals(List.of(sell), book.getOrders(SELL));
    }

    @Test
    @DisplayName("A consulta por ID retorna a ordem com seus dados originais")
    void BookFindsOrdersById() {
        Order order = book.addOrder(SELL, 10.50, 200);

        assertNotNull(order.id());
        assertEquals(SELL, order.side());
        assertEquals(10.50, order.price());
        assertEquals(200, order.quantity());
        assertEquals(Optional.of(order), book.getOrderById(order.id()));
        assertTrue(book.getOrderById(UUID.randomUUID()).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Remover a melhor ordem promove a próxima por preço e chegada")
    void BookPromotesNextOrderWhenBestIsRemoved(Side side) {
        double bestPrice = side == BUY ? 10.10 : 9.99;
        Order first = book.addOrder(side, bestPrice, 100);
        Order second = book.addOrder(side, bestPrice, 200);
        Order nextPrice = book.addOrder(side, 10.00, 300);

        assertEquals(Optional.of(first), book.removeOrder(first.id()));
        assertTrue(book.getOrderById(first.id()).isEmpty());
        assertEquals(Optional.of(second), book.bestOrder(side));
        assertEquals(List.of(second, nextPrice), book.getOrders(side));

        book.removeOrder(second.id());

        assertEquals(Optional.of(nextPrice), book.bestOrder(side));
        assertEquals(10.00, book.bestPrice(side).orElseThrow());
    }

    @Test
    @DisplayName("Remover uma ordem intermediária preserva as demais prioridades")
    void BookKeepsPriorityWhenMiddleOrderIsRemoved() {
        Order first = book.addOrder(BUY, 10.00, 100);
        Order removed = book.addOrder(BUY, 10.00, 200);
        Order third = book.addOrder(BUY, 10.00, 300);
        Order sell = book.addOrder(SELL, 10.50, 400);

        assertEquals(Optional.of(removed), book.removeOrder(removed.id()));

        assertEquals(List.of(first, third), book.getOrders(BUY));
        assertEquals(List.of(sell), book.getOrders(SELL));
        assertEquals(Optional.of(first), book.bestOrder(BUY));
        assertTrue(book.getOrderById(removed.id()).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Remover a última ordem deixa o lado vazio e permite nova inserção")
    void BookBecomesEmptyWhenLastOrderIsRemoved(Side side) {
        Order order = book.addOrder(side, 10.00, 100);

        book.removeOrder(order.id());

        assertTrue(book.getOrders(side).isEmpty());
        assertTrue(book.bestOrder(side).isEmpty());
        assertTrue(book.bestPrice(side).isEmpty());
        assertTrue(book.getOrderById(order.id()).isEmpty());

        Order replacement = book.addOrder(side, 10.00, 200);
        assertEquals(Optional.of(replacement), book.bestOrder(side));
        assertNotEquals(order.id(), replacement.id());
        assertTrue(replacement.priority() > order.priority());
    }

    @Test
    @DisplayName("Remover ordens inexistentes ou já removidas não altera o livro")
    void BookIgnoresRemovalOfMissingOrders() {
        Order removed = book.addOrder(BUY, 10.00, 100);
        Order remaining = book.addOrder(SELL, 10.50, 200);
        book.removeOrder(removed.id());

        assertTrue(book.removeOrder(removed.id()).isEmpty());
        assertTrue(book.removeOrder(UUID.randomUUID()).isEmpty());
        assertTrue(book.getOrders(BUY).isEmpty());
        assertEquals(List.of(remaining), book.getOrders(SELL));
    }

    @Test
    @DisplayName("A consulta retorna um snapshot imutável e independente do livro")
    void BookReturnsImmutableSnapshots() {
        Order first = book.addOrder(BUY, 10.00, 100);
        List<Order> snapshot = book.getOrders(BUY);

        assertThrows(UnsupportedOperationException.class, snapshot::clear);

        Order second = book.addOrder(BUY, 10.10, 200);
        book.removeOrder(first.id());

        assertEquals(List.of(first), snapshot);
        assertEquals(List.of(second), book.getOrders(BUY));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.0, -1.0})
    @DisplayName("Preços não positivos são rejeitados sem alterar o livro")
    void BookRejectsNonPositivePrices(double price) {
        assertThrows(IllegalArgumentException.class, () -> book.addOrder(BUY, price, 100));
        assertTrue(book.getOrders(BUY).isEmpty());
        assertTrue(book.getOrders(SELL).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("Preços NaN e infinitos são rejeitados sem alterar o livro")
    void BookRejectsNonFinitePrices(double price) {
        assertThrows(IllegalArgumentException.class, () -> book.addOrder(BUY, price, 100));
        assertTrue(book.getOrders(BUY).isEmpty());
        assertTrue(book.getOrders(SELL).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("Quantidades não positivas são rejeitadas sem alterar o livro")
    void BookRejectsNonPositiveQuantities(long quantity) {
        assertThrows(IllegalArgumentException.class, () -> book.addOrder(SELL, 10.00, quantity));
        assertTrue(book.getOrders(BUY).isEmpty());
        assertTrue(book.getOrders(SELL).isEmpty());
    }

    @Test
    @DisplayName("O livro gera UUIDs distintos para ordens dos dois lados")
    void BookGeneratesUniqueIdsAcrossBothSides() {
        Order buy = book.addOrder(BUY, 10.00, 100);
        Order sell = book.addOrder(SELL, 10.50, 200);

        assertNotNull(buy.id());
        assertNotNull(sell.id());
        assertNotEquals(buy.id(), sell.id());
        assertEquals(Optional.of(buy), book.getOrderById(buy.id()));
        assertEquals(Optional.of(sell), book.getOrderById(sell.id()));
    }

    @Test
    @DisplayName("Um lado nulo é rejeitado sem alterar o livro")
    void BookRejectsNullSide() {
        assertThrows(NullPointerException.class, () -> book.addOrder(null, 10.00, 100));
        assertTrue(book.getOrders(BUY).isEmpty());
        assertTrue(book.getOrders(SELL).isEmpty());
    }
}
