package dev.ricciow.matchingengine.strategy;

import dev.ricciow.matchingengine.book.ExecutionResult;
import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;

public interface OrderStrategy {
    ExecutionResult execute(OrderBook book, Side side);
}