CREATE TABLE tenants (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE source_connections (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    type VARCHAR(32) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    base_url VARCHAR(512),
    auth_type VARCHAR(32),
    credentials_json TEXT,
    settings_json TEXT,
    status VARCHAR(32) NOT NULL,
    last_health_check TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE source_scopes (
    id VARCHAR(64) PRIMARY KEY,
    source_connection_id VARCHAR(64) NOT NULL REFERENCES source_connections(id) ON DELETE CASCADE,
    external_id VARCHAR(255) NOT NULL,
    scope_type VARCHAR(64) NOT NULL,
    include_pattern VARCHAR(512),
    exclude_pattern VARCHAR(512),
    scan_history BOOLEAN DEFAULT TRUE,
    scan_comments BOOLEAN DEFAULT TRUE,
    scan_attachments BOOLEAN DEFAULT TRUE,
    enabled BOOLEAN DEFAULT TRUE
);

CREATE TABLE scan_jobs (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    source_connection_id VARCHAR(64) NOT NULL REFERENCES source_connections(id) ON DELETE CASCADE,
    scan_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    items_discovered INT DEFAULT 0,
    items_scanned INT DEFAULT 0,
    findings_created INT DEFAULT 0,
    error_count INT DEFAULT 0,
    error_message TEXT
);

CREATE TABLE secret_fingerprints (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    fingerprint VARCHAR(128) NOT NULL,
    secret_type VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, fingerprint)
);

CREATE TABLE findings (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    fingerprint_id VARCHAR(64) NOT NULL REFERENCES secret_fingerprints(id) ON DELETE CASCADE,
    rule_id VARCHAR(64),
    source_type VARCHAR(32) NOT NULL,
    secret_type VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    masked_secret VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    exposure_state VARCHAR(32) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    confidence INT NOT NULL,
    risk_score INT NOT NULL,
    verification_status VARCHAR(32) NOT NULL,
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    introduced_by_json TEXT,
    likely_owner_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE finding_occurrences (
    id VARCHAR(64) PRIMARY KEY,
    finding_id VARCHAR(64) NOT NULL REFERENCES findings(id) ON DELETE CASCADE,
    occurrence_fingerprint VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    scope_key VARCHAR(255) NOT NULL,
    external_object_id VARCHAR(255) NOT NULL,
    series_id VARCHAR(255) NOT NULL,
    version VARCHAR(255),
    is_current BOOLEAN DEFAULT TRUE,
    line_number INT DEFAULT 1,
    author_json TEXT,
    timestamp TIMESTAMP WITH TIME ZONE,
    source_url VARCHAR(1024),
    context_snippet TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_events (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    actor_username VARCHAR(100) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(255),
    details_json TEXT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_findings_tenant_status ON findings(tenant_id, status);
CREATE INDEX idx_findings_tenant_severity ON findings(tenant_id, severity);
CREATE INDEX idx_findings_tenant_exposure ON findings(tenant_id, exposure_state);
CREATE INDEX idx_occurrences_finding ON finding_occurrences(finding_id);
