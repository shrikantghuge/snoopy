package io.snoopy.core.spi.connector;

/** Well-known keys for {@link ContentPayload#metadata()} and {@link ContentPayload#relatedActors()}. */
public final class Meta {
    private Meta() {}

    // ---- metadata keys ----
    public static final String REPOSITORY = "repository";
    public static final String COMMIT_SHA = "commitSha";
    public static final String BRANCH = "branch";
    public static final String COMMITTER = "committer";
    public static final String ISSUE_KEY = "issueKey";
    public static final String PROJECT_KEY = "projectKey";
    public static final String FIELD = "field";
    public static final String COMMENT_ID = "commentId";
    public static final String CHANGELOG_ID = "changelogId";
    public static final String ATTACHMENT_ID = "attachmentId";
    public static final String SPACE_KEY = "spaceKey";
    public static final String SPACE_ID = "spaceId";
    public static final String PAGE_ID = "pageId";
    public static final String VERSION_NUMBER = "versionNumber";
    public static final String LABELS = "labels";
    public static final String VISIBILITY = "visibility"; // PUBLIC | INTERNAL | PRIVATE

    // ---- related actor roles ----
    public static final String ROLE_COMMITTER = "committer";
    public static final String ROLE_REPO_OWNER = "repoOwner";
    public static final String ROLE_REPORTER = "reporter";
    public static final String ROLE_ASSIGNEE = "assignee";
    public static final String ROLE_PROJECT_LEAD = "projectLead";
    public static final String ROLE_PAGE_OWNER = "pageOwner";
    public static final String ROLE_PAGE_CREATOR = "pageCreator";
    public static final String ROLE_SPACE_ADMIN = "spaceAdmin";
}
