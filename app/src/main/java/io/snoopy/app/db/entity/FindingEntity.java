package io.snoopy.app.db.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "findings")
public class FindingEntity {
    @Id
    private String id;
    private String tenantId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fingerprint_id")
    private SecretFingerprintEntity fingerprint;

    private String ruleId;
    private String sourceType;
    private String secretType;
    private String provider;
    private String maskedSecret;
    private String status;
    private String exposureState;
    private String severity;
    private int confidence;
    private int riskScore;
    private String verificationStatus;
    private Instant firstSeenAt;
    private Instant lastSeenAt;

    @Column(columnDefinition = "TEXT")
    private String introducedByJson;

    @Column(columnDefinition = "TEXT")
    private String likelyOwnerJson;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "finding", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<FindingOccurrenceEntity> occurrences = new ArrayList<>();

    public FindingEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public SecretFingerprintEntity getFingerprint() { return fingerprint; }
    public void setFingerprint(SecretFingerprintEntity fingerprint) { this.fingerprint = fingerprint; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSecretType() { return secretType; }
    public void setSecretType(String secretType) { this.secretType = secretType; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getMaskedSecret() { return maskedSecret; }
    public void setMaskedSecret(String maskedSecret) { this.maskedSecret = maskedSecret; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExposureState() { return exposureState; }
    public void setExposureState(String exposureState) { this.exposureState = exposureState; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }
    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public String getIntroducedByJson() { return introducedByJson; }
    public void setIntroducedByJson(String introducedByJson) { this.introducedByJson = introducedByJson; }
    public String getLikelyOwnerJson() { return likelyOwnerJson; }
    public void setLikelyOwnerJson(String likelyOwnerJson) { this.likelyOwnerJson = likelyOwnerJson; }
    public List<FindingOccurrenceEntity> getOccurrences() { return occurrences; }
    public void setOccurrences(List<FindingOccurrenceEntity> occurrences) { this.occurrences = occurrences; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
