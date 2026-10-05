package io.snoopy.core.spi.connector;

/**
 * A scope selected for scanning plus its policy flags.
 *
 * @param externalId      scope id (repo full name, Jira project key, Confluence space key, directory path)
 * @param scopeType       scope type
 * @param includePattern  optional glob/regex of paths/objects to include
 * @param excludePattern  optional glob/regex of paths/objects to exclude
 * @param scanHistory     scan history (git commits, Jira changelog, Confluence versions)
 * @param scanComments    scan comments
 * @param scanAttachments scan attachments
 */
public record ScopeSelection(
        String externalId,
        String scopeType,
        String includePattern,
        String excludePattern,
        boolean scanHistory,
        boolean scanComments,
        boolean scanAttachments) {

    public static ScopeSelection all(String externalId, String scopeType) {
        return new ScopeSelection(externalId, scopeType, null, null, true, true, true);
    }
}
