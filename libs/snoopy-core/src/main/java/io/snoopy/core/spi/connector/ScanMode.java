package io.snoopy.core.spi.connector;

public enum ScanMode {
    /** Current content plus (if scope allows) full reachable history. */
    FULL,
    /** Only content changed after the persisted cursor. */
    INCREMENTAL
}
