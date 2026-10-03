package dev.ricciow.matchingengine.strategy;

import dev.ricciow.matchingengine.book.ExecutionResult;
import dev.ricciow.matchingengine.book.Order;
import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.ricciow.matchingengine.book.Side.BUY;
import static dev.ricciow.matchingengine.book.Side.SELL;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Estratégia de cancelamento")
class CancelStrategyTest {
    private final OrderBook book = new OrderBook();

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("Cancel retorna a ordem removida, remove apenas o UUID indicado e não gera trades")
    void CancelRemovesOnlyRequestedOrder(Side side) {
        Side opposite = side == BUY ? SELL : BUY;
        Order target = book.addOrder(side, 10.00, 100);
        Order remaining = book.addOrder(side, 10.00, 200);
        Order otherSide = book.addOrder(opposite, 11.00, 300);

        ExecutionResult result = book.executeOrder(side, new CancelStrategy(target.id()));

        assertEquals(Optional.of(target), result.order());
        assertTrue(result.trades().isEmpty());
        assertTrue(book.getOrderById(target.id()).isEmpty());
        assertEquals(List.of(remaining), book.getOrders(side));
        assertEquals(Optional.of(remaining), book.getOrderById(remaining.id()));
        assertEquals(List.of(otherSide), book.getOrders(opposite));
        assertEquals(Optional.of(otherSide), book.getOrderById(otherSide.id()));
    }

    @Test
    @DisplayName("Cancelar um UUID inexistente ou já cancelado não altera o livro")
    void CancelIgnoresMissingOrders() {
        Order target = book.addOrder(BUY, 10.00, 100);
        Order remaining = book.addOrder(SELL, 11.00, 200);
        CancelStrategy strategy = new CancelStrategy(target.id());
        ExecutionResult cancelled = book.executeOrder(BUY, strategy);

        ExecutionResult repeated = book.executeOrder(BUY, strategy);
        ExecutionResult missing = book.executeOrder(SELL, new CancelStrategy(UUID.randomUUID()));

        assertEquals(Optional.of(target), cancelled.order());
        assertTrue(cancelled.trades().isEmpty());
        assertTrue(repeated.order().isEmpty());
        assertTrue(repeated.trades().isEmpty());
        assertEquals(repeated, missing);
        assertTrue(book.getOrders(BUY).isEmpty());
        assertEquals(List.of(remaining), book.getOrders(SELL));
        assertEquals(Optional.of(remaining), book.getOrderById(remaining.id()));
    }
}
