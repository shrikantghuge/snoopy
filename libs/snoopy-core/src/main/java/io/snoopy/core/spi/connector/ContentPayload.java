package io.snoopy.core.spi.connector;

import io.snoopy.core.domain.Actor;
import io.snoopy.core.domain.ChangeKind;
import io.snoopy.core.domain.ObjectType;
import io.snoopy.core.domain.SourceType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical, normalized unit of content emitted by every connector. The core pipeline only ever sees this
 * type, which keeps detection independent from Git/Jira/Confluence specifics (spec 2.2).
 *
 * <p>The raw {@link #content()} is transient: it is never persisted by the platform.
 *
 * <p>Identity rules:
 * <ul>
 *   <li>{@code externalObjectId} - unique per object <b>and version</b>
 *       (e.g. {@code acme/api@3f2a..:config/app.yml}, {@code PAY-12:comment:10023}, {@code page:123:v7}).</li>
 *   <li>{@code seriesId} - the logical object across versions (e.g. {@code acme/api:config/app.yml},
 *       {@code PAY-12:description}, {@code page:123}). Used to detect REMOVED / REAPPEARED.</li>
 *   <li>{@code sequence} - monotonically increasing order within a series (version number or epoch millis).</li>
 * </ul>
 */
public final class ContentPayload {

    private final SourceType sourceType;
    private final String scopeKey;
    private final String externalObjectId;
    private final String seriesId;
    private final String parentObjectId;
    private final ObjectType objectType;
    private final String version;
    private final long sequence;
    private final ChangeKind changeKind;
    private final boolean current;
    private final String title;
    private final String path;
    private final String sourceUrl;
    private final Actor author;
    private final Instant timestamp;
    private final Map<String, Actor> relatedActors;
    private final String mimeType;
    private final String filename;
    private final byte[] content;
    private final List<Integer> lineNumbers;
    private final Map<String, String> metadata;

    private ContentPayload(Builder b) {
        this.sourceType = Objects.requireNonNull(b.sourceType, "sourceType");
        this.scopeKey = Objects.requireNonNull(b.scopeKey, "scopeKey");
        this.externalObjectId = Objects.requireNonNull(b.externalObjectId, "externalObjectId");
        this.seriesId = b.seriesId != null ? b.seriesId : b.externalObjectId;
        this.parentObjectId = b.parentObjectId;
        this.objectType = Objects.requireNonNull(b.objectType, "objectType");
        this.version = b.version;
        this.sequence = b.sequence;
        this.changeKind = b.changeKind != null ? b.changeKind : ChangeKind.PRESENT;
        this.current = b.current;
        this.title = b.title;
        this.path = b.path;
        this.sourceUrl = b.sourceUrl;
        this.author = b.author;
        this.timestamp = b.timestamp;
        this.relatedActors = Collections.unmodifiableMap(new LinkedHashMap<>(b.relatedActors));
        this.mimeType = b.mimeType;
        this.filename = b.filename;
        this.content = b.content != null ? b.content : new byte[0];
        this.lineNumbers = b.lineNumbers == null ? null : List.copyOf(b.lineNumbers);
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(b.metadata));
    }

    public static Builder builder() {
        return new Builder();
    }

    public SourceType sourceType() { return sourceType; }
    public String scopeKey() { return scopeKey; }
    public String externalObjectId() { return externalObjectId; }
    public String seriesId() { return seriesId; }
    public String parentObjectId() { return parentObjectId; }
    public ObjectType objectType() { return objectType; }
    public String version() { return version; }
    public long sequence() { return sequence; }
    public ChangeKind changeKind() { return changeKind; }
    public boolean current() { return current; }
    public String title() { return title; }
    public String path() { return path; }
    public String sourceUrl() { return sourceUrl; }
    public Actor author() { return author; }
    public Instant timestamp() { return timestamp; }
    public Map<String, Actor> relatedActors() { return relatedActors; }
    public String mimeType() { return mimeType; }
    public String filename() { return filename; }
    /** Raw bytes. Transient - never persist. */
    public byte[] content() { return content; }
    /** Optional mapping: line i (0-based) of the text content maps to original line {@code lineNumbers.get(i)}. */
    public List<Integer> lineNumbers() { return lineNumbers; }
    public Map<String, String> metadata() { return metadata; }

    public String meta(String key) { return metadata.get(key); }

    @Override
    public String toString() {
        // Never include content in toString (log safety).
        return "ContentPayload{" + sourceType + ", " + objectType + ", id=" + externalObjectId
                + ", version=" + version + ", bytes=" + content.length + "}";
    }

    public static final class Builder {
        private SourceType sourceType;
        private String scopeKey;
        private String externalObjectId;
        private String seriesId;
        private String parentObjectId;
        private ObjectType objectType;
        private String version;
        private long sequence;
        private ChangeKind changeKind;
        private boolean current;
        private String title;
        private String path;
        private String sourceUrl;
        private Actor author;
        private Instant timestamp;
        private final Map<String, Actor> relatedActors = new LinkedHashMap<>();
        private String mimeType;
        private String filename;
        private byte[] content;
        private List<Integer> lineNumbers;
        private final Map<String, String> metadata = new LinkedHashMap<>();

        public Builder sourceType(SourceType v) { this.sourceType = v; return this; }
        public Builder scopeKey(String v) { this.scopeKey = v; return this; }
        public Builder externalObjectId(String v) { this.externalObjectId = v; return this; }
        public Builder seriesId(String v) { this.seriesId = v; return this; }
        public Builder parentObjectId(String v) { this.parentObjectId = v; return this; }
        public Builder objectType(ObjectType v) { this.objectType = v; return this; }
        public Builder version(String v) { this.version = v; return this; }
        public Builder sequence(long v) { this.sequence = v; return this; }
        public Builder changeKind(ChangeKind v) { this.changeKind = v; return this; }
        public Builder current(boolean v) { this.current = v; return this; }
        public Builder title(String v) { this.title = v; return this; }
        public Builder path(String v) { this.path = v; return this; }
        public Builder sourceUrl(String v) { this.sourceUrl = v; return this; }
        public Builder author(Actor v) { this.author = v; return this; }
        public Builder timestamp(Instant v) { this.timestamp = v; return this; }
        public Builder relatedActor(String role, Actor actor) {
            if (actor != null) this.relatedActors.put(role, actor);
            return this;
        }
        public Builder mimeType(String v) { this.mimeType = v; return this; }
        public Builder filename(String v) { this.filename = v; return this; }
        public Builder content(byte[] v) { this.content = v; return this; }
        public Builder text(String v) {
            this.content = v == null ? new byte[0] : v.getBytes(StandardCharsets.UTF_8);
            if (this.mimeType == null) this.mimeType = "text/plain";
            return this;
        }
        public Builder lineNumbers(List<Integer> v) { this.lineNumbers = v; return this; }
        public Builder meta(String key, String value) {
            if (value != null) this.metadata.put(key, value);
            return this;
        }

        public ContentPayload build() {
            return new ContentPayload(this);
        }
    }
}
