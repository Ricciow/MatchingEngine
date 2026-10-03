package dev.ricciow.matchingengine.book;

public enum Side {
    BUY,
    SELL;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
