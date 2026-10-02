package dev.ricciow.matchingengine.strategy;

import dev.ricciow.matchingengine.book.Order;
import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;

import java.util.Optional;

public interface OrderStrategy {
    Optional<Order> execute(OrderBook book, Side side);
}