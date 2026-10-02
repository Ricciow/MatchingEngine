package dev.ricciow.matchingengine.book;

import java.util.UUID;

public record Order(
        UUID id,
        long priority,
        Side side,
        double price,
        long quantity
) {
    public Order {
        if (priority <= 0) {
            throw new IllegalArgumentException("Priority must be positive");
        }
        if (!Double.isFinite(price) || price <= 0) {
            throw new IllegalArgumentException("Price must be finite and positive");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
}
