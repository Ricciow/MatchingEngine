package dev.ricciow.matchingengine.strategy;

import dev.ricciow.matchingengine.book.ExecutionResult;
import dev.ricciow.matchingengine.book.Order;
import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.ricciow.matchingengine.book.Side.BUY;
import static dev.ricciow.matchingengine.book.Side.SELL;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Estratégia de atualização")
class UpdateStrategyTest {
    private final OrderBook book = new OrderBook();

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Update retorna a ordem atualizada sem trades e preserva as demais ordens")
    void UpdateReturnsUpdatedOrderWithoutTrades(Side side) {
        Side opposite = side == BUY ? SELL : BUY;
        Order original = book.addOrder(side, 10.00, 100);
        Order otherSide = book.addOrder(opposite, 11.00, 300);

        ExecutionResult result = book.executeOrder(side, new UpdateStrategy(original.id(), 12.00, 200));

        Order updated = new Order(original.id(), original.priority(), side, 12.00, 200);
        assertEquals(Optional.of(updated), result.order());
        assertTrue(result.trades().isEmpty());
        assertEquals(Optional.of(updated), book.getOrderById(original.id()));
        assertEquals(List.of(updated), book.getOrders(side));
        assertEquals(List.of(otherSide), book.getOrders(opposite));
    }

    @Test
    @DisplayName("Atualizar um UUID inexistente retorna um resultado vazio sem alterar o livro")
    void UpdateIgnoresMissingOrders() {
        Order existing = book.addOrder(BUY, 10.00, 100);

        ExecutionResult result = book.executeOrder(BUY, new UpdateStrategy(UUID.randomUUID(), 12.00, 200));

        assertTrue(result.order().isEmpty());
        assertTrue(result.trades().isEmpty());
        assertEquals(List.of(existing), book.getOrders(BUY));
        assertTrue(book.getOrders(SELL).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"0.00, 200", "12.00, 0"})
    @DisplayName("Update propaga a rejeição de preço ou quantidade inválidos sem alterar a ordem")
    void UpdateRejectsInvalidValues(double price, long quantity) {
        Order original = book.addOrder(SELL, 10.00, 100);
        UpdateStrategy strategy = new UpdateStrategy(original.id(), price, quantity);

        assertThrows(IllegalArgumentException.class, () -> book.executeOrder(SELL, strategy));

        assertEquals(List.of(original), book.getOrders(SELL));
        assertEquals(Optional.of(original), book.getOrderById(original.id()));
    }
}
