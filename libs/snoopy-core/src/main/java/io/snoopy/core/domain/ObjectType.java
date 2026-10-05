package io.snoopy.core.domain;

/**
 * Normalized kind of content object. Every connector maps its native objects to one of these so the
 * detection/correlation pipeline stays source-agnostic.
 */
public enum ObjectType {
    /** A file in a filesystem or the current tree of a Git repository. */
    FILE,
    /** Lines added/removed by a Git commit for a single path. */
    COMMIT_DIFF,
    /** A Git commit message. */
    COMMIT_MESSAGE,

    JIRA_ISSUE_FIELD,
    JIRA_COMMENT,
    JIRA_CHANGELOG,
    JIRA_ATTACHMENT,
    JIRA_WORKLOG,

    CONFLUENCE_PAGE,
    CONFLUENCE_PAGE_VERSION,
    CONFLUENCE_BLOGPOST,
    CONFLUENCE_COMMENT,
    CONFLUENCE_ATTACHMENT
}
