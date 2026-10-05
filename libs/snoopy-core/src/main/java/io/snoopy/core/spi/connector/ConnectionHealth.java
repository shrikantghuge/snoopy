package io.snoopy.core.spi.connector;

import java.util.Map;

public record ConnectionHealth(boolean ok, String message, Map<String, String> details) {

    public static ConnectionHealth ok(String message, Map<String, String> details) {
        return new ConnectionHealth(true, message, details == null ? Map.of() : Map.copyOf(details));
    }

    public static ConnectionHealth failed(String message) {
        return new ConnectionHealth(false, message, Map.of());
    }
}
