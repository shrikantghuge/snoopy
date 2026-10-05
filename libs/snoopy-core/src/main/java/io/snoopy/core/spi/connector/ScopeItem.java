package io.snoopy.core.spi.connector;

/**
 * A scannable scope discovered in a source.
 *
 * @param externalId  e.g. {@code acme/payments-api}, {@code PAY}, {@code ENG}
 * @param scopeType   e.g. {@code REPOSITORY}, {@code JIRA_PROJECT}, {@code CONFLUENCE_SPACE}, {@code DIRECTORY}
 * @param displayName human readable name
 * @param url         link to the scope in the source system
 */
public record ScopeItem(String externalId, String scopeType, String displayName, String url) {
}
