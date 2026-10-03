package dev.ricciow.matchingengine.cli;

import dev.ricciow.matchingengine.book.ExecutionResult;
import dev.ricciow.matchingengine.book.OrderBook;
import dev.ricciow.matchingengine.book.Side;
import dev.ricciow.matchingengine.strategy.LimitStrategy;
import dev.ricciow.matchingengine.strategy.MarketStrategy;

import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;

public class Console {
    Scanner scanner = new Scanner(System.in);
    OrderBook book = new OrderBook();

    public Console() {
        captureMessages();
    }

    private void captureMessages() {
        while (true) {
            System.out.print(">>> ");
            String message = scanner.nextLine();
            if (Objects.equals(message, "quit")) break;
            executeMessage(message);
        }
    }

    private void executeMessage(String message) {
        message = message.trim().toLowerCase(Locale.ROOT);
        String[] parts = message.split("\\s+");

        switch (parts[0]) {
            case "limit":
                limitCommand(parts);
                break;
            case "market":
                marketCommand(parts);
                break;
            case "peg":
                break;
            case "book":
                break;
            case "cancel":
                break;
            case "edit":
                break;
            default:
                System.out.println("Invalid command");
                break;
        }
    }

    private void limitCommand(String[] parts) {
        if (parts.length != 4) {
            System.out.println("Usage: limit <buy|sell> <qty> <price>");
            return;
        }

        try {
            Side side = switch (parts[1]) {
                case "buy" -> Side.BUY;
                case "sell" -> Side.SELL;
                default -> throw new IllegalArgumentException("Side must be buy or sell");
            };
            double price = Double.parseDouble(parts[2]);
            long quantity = Long.parseLong(parts[3]);
            ExecutionResult result = book.executeOrder(side, new LimitStrategy(quantity, price));
            result.order().ifPresent(order -> { System.out.println("Order created: " + order); });
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid limit command: " + e.getMessage());
        }
    }

    private void marketCommand(String[] parts) {
        if (parts.length != 3) {
            System.out.println("Usage: market <buy|sell> <qty>");
            return;
        }

        try {
            Side side = switch (parts[1]) {
                case "buy" -> Side.BUY;
                case "sell" -> Side.SELL;
                default -> throw new IllegalArgumentException("Side must be buy or sell");
            };
            long quantity = Long.parseLong(parts[2]);
            ExecutionResult result = book.executeOrder(side, new MarketStrategy(quantity));
            result.trades().forEach(System.out::println);
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid market command: " + e.getMessage());
        }
    }
}
