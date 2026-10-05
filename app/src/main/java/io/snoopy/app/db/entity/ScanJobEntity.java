package io.snoopy.app.db.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "scan_jobs")
public class ScanJobEntity {
    @Id
    private String id;
    private String tenantId;
    private String sourceConnectionId;
    private String scanType;
    private String status;
    private Instant startedAt = Instant.now();
    private Instant completedAt;
    private int itemsDiscovered;
    private int itemsScanned;
    private int findingsCreated;
    private int errorCount;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public ScanJobEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getSourceConnectionId() { return sourceConnectionId; }
    public void setSourceConnectionId(String sourceConnectionId) { this.sourceConnectionId = sourceConnectionId; }
    public String getScanType() { return scanType; }
    public void setScanType(String scanType) { this.scanType = scanType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public int getItemsDiscovered() { return itemsDiscovered; }
    public void setItemsDiscovered(int itemsDiscovered) { this.itemsDiscovered = itemsDiscovered; }
    public int getItemsScanned() { return itemsScanned; }
    public void setItemsScanned(int itemsScanned) { this.itemsScanned = itemsScanned; }
    public int getFindingsCreated() { return findingsCreated; }
    public void setFindingsCreated(int findingsCreated) { this.findingsCreated = findingsCreated; }
    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
