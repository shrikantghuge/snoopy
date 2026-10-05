package io.snoopy.app.db.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tenants")
public class TenantEntity {
    @Id
    private String id;
    private String name;
    private String status;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public TenantEntity() {}
    public TenantEntity(String id, String name, String status) {
        this.id = id;
        this.name = name;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
