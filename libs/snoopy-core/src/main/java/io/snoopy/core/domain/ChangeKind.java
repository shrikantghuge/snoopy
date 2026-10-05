package io.snoopy.core.domain;

/**
 * How a piece of content relates to the secret lifecycle.
 *
 * <ul>
 *   <li>{@link #PRESENT} - a full snapshot (current file, Confluence page version, Jira field value).</li>
 *   <li>{@link #ADDED} - content introduced by a change (Git added lines, Jira changelog "to" value).</li>
 *   <li>{@link #REMOVED} - content removed by a change (Git removed lines, Jira changelog "from" value).</li>
 * </ul>
 */
public enum ChangeKind {
    PRESENT,
    ADDED,
    REMOVED
}
