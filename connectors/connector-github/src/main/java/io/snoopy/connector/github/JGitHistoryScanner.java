package io.snoopy.connector.github;

import io.snoopy.core.domain.Actor;
import io.snoopy.core.domain.ChangeKind;
import io.snoopy.core.domain.ObjectType;
import io.snoopy.core.domain.SourceType;
import io.snoopy.core.spi.connector.ContentPayload;
import io.snoopy.core.spi.connector.ContentSink;
import io.snoopy.core.spi.connector.Meta;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

public class JGitHistoryScanner {

    private static final Logger log = LoggerFactory.getLogger(JGitHistoryScanner.class);

    public void scanRepository(File repoDir, String repoName, boolean scanHistory, ContentSink sink) {
        try (Git git = Git.open(repoDir)) {
            Repository repo = git.getRepository();

            if (scanHistory) {
                scanCommitsAndDiffs(repo, repoName, sink);
            } else {
                scanHeadTree(repo, repoName, sink);
            }
        } catch (Exception e) {
            log.error("Failed to scan Git repository {}: {}", repoName, e.getMessage(), e);
            sink.failure(repoName, repoDir.getAbsolutePath(), "Failed to scan Git repository: " + e.getMessage(), e);
        }
    }

    private void scanCommitsAndDiffs(Repository repo, String repoName, ContentSink sink) throws Exception {
        try (RevWalk revWalk = new RevWalk(repo)) {
            // Find all ref heads (using getLeaf().getObjectId() to resolve symbolic refs like HEAD)
            repo.getRefDatabase().getRefs().forEach(ref -> {
                try {
                    ObjectId id = ref.getLeaf().getObjectId();
                    if (id != null) revWalk.markStart(revWalk.parseCommit(id));
                } catch (Exception ignored) {}
            });

            for (RevCommit commit : revWalk) {
                if (sink.isCancelled()) return;

                String commitSha = commit.getName();
                Actor author = Actor.of(commit.getAuthorIdent().getName(), commit.getAuthorIdent().getName(), commit.getAuthorIdent().getEmailAddress());
                Actor committer = Actor.of(commit.getCommitterIdent().getName(), commit.getCommitterIdent().getName(), commit.getCommitterIdent().getEmailAddress());
                Instant commitTime = Instant.ofEpochSecond(commit.getCommitTime());

                RevCommit parent = commit.getParentCount() > 0 ? revWalk.parseCommit(commit.getParent(0).getId()) : null;
                diffCommit(repo, repoName, parent, commit, commitSha, author, committer, commitTime, sink);
            }
        }
    }

    private void diffCommit(Repository repo, String repoName, RevCommit parent, RevCommit commit,
                            String commitSha, Actor author, Actor committer, Instant commitTime, ContentSink sink) throws Exception {
        try (ObjectReader reader = repo.newObjectReader();
             ByteArrayOutputStream out = new ByteArrayOutputStream();
             DiffFormatter diffFormatter = new DiffFormatter(out)) {
            diffFormatter.setReader(reader, repo.getConfig());

            AbstractTreeIterator parentTree = parent != null ?
                    new CanonicalTreeParser(null, reader, parent.getTree().getId()) : new EmptyTreeIterator();
            AbstractTreeIterator commitTree = new CanonicalTreeParser(null, reader, commit.getTree().getId());

            List<DiffEntry> diffs = diffFormatter.scan(parentTree, commitTree);
            for (DiffEntry diff : diffs) {
                String path = diff.getNewPath();
                if (path == null || path.equals("/dev/null")) continue;

                FileHeader fileHeader = diffFormatter.toFileHeader(diff);

                // Extract added content lines for this commit
                out.reset();
                diffFormatter.format(diff);
                String diffText = out.toString(StandardCharsets.UTF_8);

                ContentPayload payload = ContentPayload.builder()
                        .sourceType(SourceType.GITHUB)
                        .scopeKey(repoName)
                        .externalObjectId(repoName + ":" + commitSha + ":" + path)
                        .seriesId(repoName + ":" + path)
                        .objectType(ObjectType.COMMIT_DIFF)
                        .version(commitSha)
                        .sequence(commitTime.toEpochMilli())
                        .changeKind(ChangeKind.ADDED)
                        .current(parent == null)
                        .title(path)
                        .path(path)
                        .author(author)
                        .relatedActor(Meta.ROLE_COMMITTER, committer)
                        .timestamp(commitTime)
                        .text(diffText)
                        .meta(Meta.REPOSITORY, repoName)
                        .meta(Meta.COMMIT_SHA, commitSha)
                        .build();

                sink.accept(payload);
            }
        }
    }

    private void scanHeadTree(Repository repo, String repoName, ContentSink sink) throws Exception {
        ObjectId head = repo.resolve("HEAD");
        if (head == null) return;

        try (RevWalk revWalk = new RevWalk(repo);
             TreeWalk treeWalk = new TreeWalk(repo)) {
            RevCommit commit = revWalk.parseCommit(head);
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);

            Actor author = Actor.of(commit.getAuthorIdent().getName(), commit.getAuthorIdent().getName(), commit.getAuthorIdent().getEmailAddress());
            Instant commitTime = Instant.ofEpochSecond(commit.getCommitTime());

            while (treeWalk.next()) {
                String path = treeWalk.getPathString();
                ObjectId blobId = treeWalk.getObjectId(0);
                byte[] bytes = repo.open(blobId).getBytes();

                ContentPayload payload = ContentPayload.builder()
                        .sourceType(SourceType.GITHUB)
                        .scopeKey(repoName)
                        .externalObjectId(repoName + ":HEAD:" + path)
                        .seriesId(repoName + ":" + path)
                        .objectType(ObjectType.FILE)
                        .version(commit.getName())
                        .sequence(commitTime.toEpochMilli())
                        .changeKind(ChangeKind.PRESENT)
                        .current(true)
                        .title(path)
                        .path(path)
                        .author(author)
                        .timestamp(commitTime)
                        .content(bytes)
                        .meta(Meta.REPOSITORY, repoName)
                        .meta(Meta.COMMIT_SHA, commit.getName())
                        .build();

                sink.accept(payload);
            }
        }
    }
}
