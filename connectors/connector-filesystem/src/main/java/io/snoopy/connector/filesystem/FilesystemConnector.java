package io.snoopy.connector.filesystem;

import io.snoopy.core.domain.Actor;
import io.snoopy.core.domain.ChangeKind;
import io.snoopy.core.domain.ObjectType;
import io.snoopy.core.domain.SourceType;
import io.snoopy.core.spi.connector.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

public class FilesystemConnector implements SourceConnector {

    @Override
    public SourceType type() {
        return SourceType.FILESYSTEM;
    }

    @Override
    public Set<Capability> capabilities() {
        return Set.of(Capability.CURRENT_CONTENT, Capability.AUTHOR_METADATA);
    }

    @Override
    public Map<String, String> coverage() {
        return Map.of("reachableHistory", "NONE", "currentContent", "COMPLETE");
    }

    @Override
    public ConnectionHealth checkConnection(ConnectionConfig config) {
        String pathStr = config.setting("directoryPath", ".");
        File dir = new File(pathStr);
        if (dir.exists() && dir.canRead()) {
            return ConnectionHealth.ok("Directory accessible", Map.of("path", dir.getAbsolutePath()));
        }
        return ConnectionHealth.failed("Directory does not exist or is not readable: " + pathStr);
    }

    @Override
    public List<ScopeItem> discoverScopes(ConnectionConfig config) {
        String pathStr = config.setting("directoryPath", ".");
        File dir = new File(pathStr);
        return List.of(new ScopeItem(dir.getAbsolutePath(), "DIRECTORY", dir.getName(), dir.toURI().toString()));
    }

    @Override
    public void scan(ScanContext context, ContentSink sink) {
        String pathStr = context.config().setting("directoryPath", ".");
        Path root = Path.of(pathStr);
        if (!Files.exists(root)) return;

        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("/.git/"))
                    .forEach(path -> {
                        if (sink.isCancelled()) return;
                        try {
                            byte[] bytes = Files.readAllBytes(path);
                            String relPath = root.relativize(path).toString();
                            Instant lastMod = Files.getLastModifiedTime(path).toInstant();
                            String owner = Files.getOwner(path).getName();
                            String fname = path.getFileName().toString();

                            ContentPayload payload = ContentPayload.builder()
                                    .sourceType(SourceType.FILESYSTEM)
                                    .scopeKey(root.toString())
                                    .externalObjectId(path.toAbsolutePath().toString())
                                    .seriesId(relPath)
                                    .objectType(ObjectType.FILE)
                                    .version(String.valueOf(lastMod.toEpochMilli()))
                                    .sequence(lastMod.toEpochMilli())
                                    .changeKind(ChangeKind.PRESENT)
                                    .current(true)
                                    .title(fname)
                                    .filename(fname)
                                    .mimeType("text/plain")
                                    .path(relPath)
                                    .sourceUrl(path.toUri().toString())
                                    .author(Actor.of(owner, owner, null))
                                    .timestamp(lastMod)
                                    .content(bytes)
                                    .build();

                            sink.accept(payload);
                        } catch (Exception e) {
                            sink.failure(root.toString(), path.toString(), "Failed to read file", e);
                        }
                    });
            sink.checkpoint(root.toString(), String.valueOf(System.currentTimeMillis()));
        } catch (IOException e) {
            sink.failure(root.toString(), root.toString(), "Failed to walk directory", e);
        }
    }
}
