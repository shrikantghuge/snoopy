package io.snoopy.app.db.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "secret_fingerprints")
public class SecretFingerprintEntity {
    @Id
    private String id;
    private String tenantId;
    private String fingerprint;
    private String secretType;
    private String provider;
    private Instant createdAt = Instant.now();

    public SecretFingerprintEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getSecretType() { return secretType; }
    public void setSecretType(String secretType) { this.secretType = secretType; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public Instant getCreatedAt() { return createdAt; }
}
