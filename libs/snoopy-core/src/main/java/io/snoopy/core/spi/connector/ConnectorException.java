package io.snoopy.core.spi.connector;

/** Typed connector failure. Messages must never contain secrets or credentials. */
public class ConnectorException extends RuntimeException {

    public enum Kind { AUTHENTICATION, PERMISSION, NOT_FOUND, RATE_LIMITED, TRANSIENT, CONFIGURATION, BLOCKED, UNKNOWN }

    private final Kind kind;
    private final int httpStatus;

    public ConnectorException(Kind kind, String message) {
        this(kind, message, -1, null);
    }

    public ConnectorException(Kind kind, String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.httpStatus = httpStatus;
    }

    public Kind kind() {
        return kind;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
