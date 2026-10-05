package io.snoopy.engine.content;

import io.snoopy.core.spi.connector.ConnectorException;

public final class FileSafetyGuard {

    private final long maxFileSizeBytes;
    private final long maxDecompressedBytes;

    public FileSafetyGuard(long maxFileSizeBytes, long maxDecompressedBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxDecompressedBytes = maxDecompressedBytes;
    }

    public static FileSafetyGuard defaultGuard() {
        return new FileSafetyGuard(50L * 1024 * 1024, 200L * 1024 * 1024);
    }

    public void validateSize(long actualSizeBytes) {
        if (actualSizeBytes > maxFileSizeBytes) {
            throw new ConnectorException(ConnectorException.Kind.BLOCKED,
                    "File size " + actualSizeBytes + " bytes exceeds limit of " + maxFileSizeBytes + " bytes");
        }
    }

    public boolean isSafeMimeType(String mimeType) {
        if (mimeType == null) return true;
        String lower = mimeType.toLowerCase();
        // Reject executables and binaries
        if (lower.contains("x-executable") || lower.contains("x-dosexec") || lower.contains("x-msdownload") || lower.contains("x-elf")) {
            return false;
        }
        return true;
    }
}
