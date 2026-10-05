package io.snoopy.app.db.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "source_scopes")
public class SourceScopeEntity {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_connection_id")
    private SourceConnectionEntity sourceConnection;

    private String externalId;
    private String scopeType;
    private String includePattern;
    private String excludePattern;
    private boolean scanHistory = true;
    private boolean scanComments = true;
    private boolean scanAttachments = true;
    private boolean enabled = true;

    public SourceScopeEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public SourceConnectionEntity getSourceConnection() { return sourceConnection; }
    public void setSourceConnection(SourceConnectionEntity sourceConnection) { this.sourceConnection = sourceConnection; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public String getIncludePattern() { return includePattern; }
    public void setIncludePattern(String includePattern) { this.includePattern = includePattern; }
    public String getExcludePattern() { return excludePattern; }
    public void setExcludePattern(String excludePattern) { this.excludePattern = excludePattern; }
    public boolean isScanHistory() { return scanHistory; }
    public void setScanHistory(boolean scanHistory) { this.scanHistory = scanHistory; }
    public boolean isScanComments() { return scanComments; }
    public void setScanComments(boolean scanComments) { this.scanComments = scanComments; }
    public boolean isScanAttachments() { return scanAttachments; }
    public void setScanAttachments(boolean scanAttachments) { this.scanAttachments = scanAttachments; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
