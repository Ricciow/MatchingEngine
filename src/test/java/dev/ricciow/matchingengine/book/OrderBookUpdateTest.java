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

@DisplayName("Atualização de ordens")
class OrderBookUpdateTest {
    private final OrderBook book = new OrderBook();

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Atualizar reposiciona a ordem mantendo o UUID, lado e prioridade")
    void BookReordersUpdatedPrices(Side side) {
        Order original = book.addOrder(side, 10.00, 100);
        List<Order> snapshot = book.getOrders(side);
        Order other = book.addOrder(side, 11.00, 200);
        double bestPrice = side == BUY ? 12.00 : 9.00;

        Order updated = book.updateOrder(original.id(), bestPrice, 300).orElseThrow();

        assertEquals(new Order(original.id(), original.priority(), side, bestPrice, 300), updated);
        assertEquals(Optional.of(updated), book.getOrderById(original.id()));
        assertEquals(List.of(updated, other), book.getOrders(side));
        assertEquals(Optional.of(updated), book.bestOrder(side));
        assertEquals(List.of(original), snapshot);
        assertEquals(Optional.of(updated), book.removeOrder(updated.id()));
        assertEquals(List.of(other), book.getOrders(side));
        assertTrue(book.getOrderById(updated.id()).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Atualizar a melhor ordem para um preço pior promove a próxima")
    void BookPromotesNextOrderWhenUpdatedPriceIsWorse(Side side) {
        double bestPrice = side == BUY ? 11.00 : 9.00;
        double worsePrice = side == BUY ? 9.00 : 11.00;
        Order original = book.addOrder(side, bestPrice, 100);
        Order other = book.addOrder(side, 10.00, 200);

        Order updated = book.updateOrder(original.id(), worsePrice, 100).orElseThrow();

        assertEquals(List.of(other, updated), book.getOrders(side));
        assertEquals(Optional.of(other), book.bestOrder(side));
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Atualizar para o mesmo preço de outra ordem mantém a prioridade original")
    void BookKeepsPriorityWhenUpdatedPricesMatch(Side side) {
        Order first = book.addOrder(side, 9.00, 100);
        Order second = book.addOrder(side, 10.00, 200);

        Order updated = book.updateOrder(first.id(), 10.00, 300).orElseThrow();
        Order third = book.addOrder(side, 10.00, 400);

        assertEquals(List.of(updated, second, third), book.getOrders(side));
        assertEquals(second.priority() + 1, third.priority());
    }

    @Test
    @DisplayName("Atualizar apenas a quantidade mantém a posição e preserva o outro lado")
    void BookKeepsPositionWhenOnlyQuantityChanges() {
        Order first = book.addOrder(BUY, 10.00, 100);
        Order second = book.addOrder(BUY, 10.00, 200);
        Order sell = book.addOrder(SELL, 11.00, 300);

        Order updated = book.updateOrder(first.id(), 10.00, 50).orElseThrow();

        assertEquals(List.of(updated, second), book.getOrders(BUY));
        assertEquals(List.of(sell), book.getOrders(SELL));
        assertEquals(Optional.of(sell), book.getOrderById(sell.id()));
    }

    @Test
    @DisplayName("Atualizar uma ordem inexistente ou removida não altera o livro")
    void BookIgnoresUpdateOfMissingOrders() {
        Order remaining = book.addOrder(BUY, 10.00, 100);
        Order removed = book.addOrder(SELL, 11.00, 200);
        book.removeOrder(removed.id());

        assertTrue(book.updateOrder(UUID.randomUUID(), 12.00, 300).isEmpty());
        assertTrue(book.updateOrder(removed.id(), 12.00, 300).isEmpty());
        assertEquals(List.of(remaining), book.getOrders(BUY));
        assertEquals(Optional.of(remaining), book.getOrderById(remaining.id()));
        assertTrue(book.getOrders(SELL).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("Atualizar com preço inválido mantém a ordem original no livro")
    void BookRejectsInvalidUpdatePrices(double price) {
        Order original = book.addOrder(BUY, 10.00, 100);

        assertThrows(IllegalArgumentException.class, () -> book.updateOrder(original.id(), price, 200));

        assertEquals(List.of(original), book.getOrders(BUY));
        assertEquals(Optional.of(original), book.getOrderById(original.id()));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("Atualizar com quantidade inválida mantém a ordem original no livro")
    void BookRejectsInvalidUpdateQuantities(long quantity) {
        Order original = book.addOrder(SELL, 10.00, 100);

        assertThrows(IllegalArgumentException.class, () -> book.updateOrder(original.id(), 11.00, quantity));

        assertEquals(List.of(original), book.getOrders(SELL));
        assertEquals(Optional.of(original), book.getOrderById(original.id()));
    }
}
