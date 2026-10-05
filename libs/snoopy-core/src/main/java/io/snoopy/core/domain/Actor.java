package io.snoopy.core.domain;

/**
 * A person or system account in a source system (Git author, Jira user, Confluence user).
 *
 * @param externalId  stable id in the source (Atlassian accountId, GitHub login, git email)
 * @param displayName human readable name
 * @param email       email if known (may be null; Atlassian often hides it)
 */
public record Actor(String externalId, String displayName, String email) {

    public static Actor of(String externalId, String displayName, String email) {
        return new Actor(externalId, displayName, email);
    }

    public String label() {
        if (displayName != null && !displayName.isBlank()) {
            return email != null && !email.isBlank() ? displayName + " <" + email + ">" : displayName;
        }
        if (email != null) return email;
        return externalId == null ? "unknown" : externalId;
    }
}
