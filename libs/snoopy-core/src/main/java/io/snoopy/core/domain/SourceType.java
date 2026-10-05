package io.snoopy.core.domain;

/** Supported source systems. New connectors add a value here and register a {@code SourceConnector}. */
public enum SourceType {
    FILESYSTEM,
    GITHUB,
    JIRA_CLOUD,
    CONFLUENCE_CLOUD
}
