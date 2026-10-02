package dev.ricciow.matchingengine.trade;

public record Trade(double price, long quantity) {
    @Override
    public String toString() {
        return "Trade, price: " + price + ", qty: " + quantity;
    }
}
