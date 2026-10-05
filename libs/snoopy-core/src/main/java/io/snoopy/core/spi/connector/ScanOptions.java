package io.snoopy.core.spi.connector;

/**
 * Connector limits.
 *
 * @param maxContentBytes     max bytes for a single text object (file, page body)
 * @param maxAttachmentBytes  max bytes to download for an attachment
 * @param maxCommitsPerRepo   safety limit for history walks (0 = unlimited)
 * @param maxVersionsPerPage  safety limit for Confluence page versions (0 = unlimited)
 * @param pageSize            API page size
 */
public record ScanOptions(
        long maxContentBytes,
        long maxAttachmentBytes,
        int maxCommitsPerRepo,
        int maxVersionsPerPage,
        int pageSize) {

    public static ScanOptions defaults() {
        return new ScanOptions(10L * 1024 * 1024, 50L * 1024 * 1024, 0, 0, 50);
    }
}
