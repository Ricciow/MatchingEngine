package dev.ricciow.matchingengine.book;

import dev.ricciow.matchingengine.trade.Trade;

import java.util.List;
import java.util.Optional;

/** Contains the resting order, if any, and the trades produced by an execution. */
public record ExecutionResult(Optional<Order> order, List<Trade> trades) {}