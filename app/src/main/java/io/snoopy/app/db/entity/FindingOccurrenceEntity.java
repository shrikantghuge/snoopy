package io.snoopy.app.db.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "finding_occurrences")
public class FindingOccurrenceEntity {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finding_id")
    private FindingEntity finding;

    private String occurrenceFingerprint;
    private String sourceType;
    private String scopeKey;
    private String externalObjectId;
    private String seriesId;
    private String version;
    private boolean isCurrent = true;
    private int lineNumber = 1;

    @Column(columnDefinition = "TEXT")
    private String authorJson;

    private Instant timestamp;
    private String sourceUrl;

    @Column(columnDefinition = "TEXT")
    private String contextSnippet;

    private Instant createdAt = Instant.now();

    public FindingOccurrenceEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public FindingEntity getFinding() { return finding; }
    public void setFinding(FindingEntity finding) { this.finding = finding; }
    public String getOccurrenceFingerprint() { return occurrenceFingerprint; }
    public void setOccurrenceFingerprint(String occurrenceFingerprint) { this.occurrenceFingerprint = occurrenceFingerprint; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public String getExternalObjectId() { return externalObjectId; }
    public void setExternalObjectId(String externalObjectId) { this.externalObjectId = externalObjectId; }
    public String getSeriesId() { return seriesId; }
    public void setSeriesId(String seriesId) { this.seriesId = seriesId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public boolean isCurrent() { return isCurrent; }
    public void setCurrent(boolean current) { isCurrent = current; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
    public String getAuthorJson() { return authorJson; }
    public void setAuthorJson(String authorJson) { this.authorJson = authorJson; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getContextSnippet() { return contextSnippet; }
    public void setContextSnippet(String contextSnippet) { this.contextSnippet = contextSnippet; }
}
