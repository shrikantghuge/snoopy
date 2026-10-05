# Credential Exposure Discovery Platform — AI Implementation Specification

## 1. Purpose

Build an enterprise-grade platform that connects to developer and collaboration systems and discovers credentials/secrets that are:

1. Present in current content.
2. Present in reachable historical content.
3. Present in version history.
4. Present in comments.
5. Present in attachments and supported documents.
6. Repeated across multiple systems.
7. Potentially still active.
8. Associated with a meaningful owner, project, environment, and exposure timeline.

Initial connectors:

- GitHub
- Jira Cloud
- Confluence Cloud

The architecture MUST make additional connectors possible without modifying the core scan/detection/risk pipeline.

The product is not just a regex scanner. It is a credential exposure discovery, correlation, verification, risk, and remediation platform.

---

## 2. Product principles

### 2.1 Security first

Never persist raw secrets by default.

Store:

- secret type
- detector/rule
- source metadata
- masked representation
- HMAC fingerprint
- first seen / last seen
- evidence coordinates
- confidence
- risk
- verification state

If raw evidence is required for forensic mode, encrypt it with envelope encryption backed by KMS/HSM, restrict access, audit every read, and support short retention.

### 2.2 Source-agnostic core

Git, Jira and Confluence must become normalized content streams consumed by the same pipeline.

Do NOT implement separate detection logic for each source.

### 2.3 Historical awareness

A finding must distinguish current and historical exposure.

Examples:

- CURRENT
- HISTORICAL
- CURRENT_AND_HISTORICAL
- REMEDIATED
- REAPPEARED

### 2.4 High precision

Use multiple signals:

- provider-specific patterns
- regular expressions
- keyword/context detection
- entropy
- file type/path context
- surrounding text
- verification
- allowlists/exclusions

High entropy alone must never automatically produce a critical finding.

### 2.5 Extensibility

Every major subsystem must use interfaces/SPI:

- source connectors
- content extractors
- detectors
- false-positive filters
- credential verifiers
- risk rules
- notification channels

### 2.6 Idempotency

Repeated retrieval of the same object/version/content must not repeatedly create findings or duplicate scan work.

---

# 3. Recommended technology stack

Use a stack aligned with an enterprise Java/Kubernetes environment.

## Backend

- Java 21+
- Spring Boot 3.x
- Spring Web
- Spring Validation
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL
- Redis only where it adds value for cache/locks
- Kafka or Google Pub/Sub for asynchronous jobs
- Jackson
- Micrometer
- OpenTelemetry

## Frontend

- React
- TypeScript
- Vite
- React Router
- TanStack Query
- component library such as MUI
- secure HTTP-only cookie/session or OAuth/OIDC-compatible authentication

## Search

Start with PostgreSQL full-text/search capabilities where sufficient.

Add OpenSearch/Elasticsearch only when finding volume or cross-field search justifies it.

## Object/document processing

- Apache Tika
- PDF parser
- Office document parser
- archive extraction
- plain text / JSON / YAML / XML / CSV
- binary file type detection

## Deployment

- Docker
- Kubernetes
- Helm
- Horizontal Pod Autoscaler
- OpenTelemetry
- Prometheus
- Grafana

Cloud-neutral design. Primary examples may use GCP/Kubernetes.

---

# 4. High-level architecture

```text
                         +----------------------+
                         |       React UI       |
                         | Dashboard / Findings |
                         | Search / Reports     |
                         +----------+-----------+
                                    |
                                    v
                         +----------------------+
                         | API Gateway / BFF     |
                         | Auth / RBAC / Tenant  |
                         +----------+-----------+
                                    |
          +-------------------------+--------------------------+
          |                         |                          |
          v                         v                          v
 +----------------+       +----------------+         +----------------+
 | Source Manager |       | Scan Scheduler |         | Policy Manager |
 +-------+--------+       +-------+--------+         +----------------+
         |                        |
         +------------+-----------+
                      |
                      v
               +-------------+
               | Job Queue   |
               | Kafka/PubSub|
               +------+------+
                      |
       +--------------+--------------+
       |              |              |
       v              v              v
 +-----------+  +-----------+  +-------------+
 | Git Worker|  | JiraWorker |  | Confluence  |
 |           |  |           |  | Worker      |
 +-----+-----+  +-----+-----+  +------+------+ 
       |              |               |
       +--------------+---------------+
                      |
                      v
              +---------------+
              | Content Model |
              | Normalization  |
              +-------+-------+
                      |
                      v
              +---------------+
              | Extractors    |
              | Files/Docs     |
              +-------+-------+
                      |
                      v
              +---------------+
              | Detection     |
              | Engine        |
              +-------+-------+
                      |
                      v
              +---------------+
              | Correlation   |
              | Fingerprint   |
              +-------+-------+
                      |
                      v
              +---------------+
              | Risk Engine   |
              +-------+-------+
                      |
                      v
              +---------------+
              | Verification  |
              | Engine        |
              +-------+-------+
                      |
                      v
              +---------------+
              | Findings DB   |
              | Exposure Graph|
              +---------------+
```

---

# 5. Monorepo structure

Create a monorepo like:

```text
credential-exposure-platform/
|
+-- services/
|   +-- api-service/
|   +-- scan-orchestrator/
|   +-- connector-github/
|   +-- connector-jira/
|   +-- connector-confluence/
|   +-- content-service/
|   +-- detection-service/
|   +-- correlation-service/
|   +-- risk-service/
|   +-- verification-service/
|   +-- notification-service/
|
+-- libs/
|   +-- domain-model/
|   +-- connector-spi/
|   +-- detection-spi/
|   +-- extractor-spi/
|   +-- verifier-spi/
|   +-- security/
|   +-- observability/
|   +-- test-fixtures/
|
+-- frontend/
|
+-- deployment/
|   +-- docker/
|   +-- helm/
|   +-- k8s/
|
+-- database/
|   +-- migrations/
|
+-- docs/
|   +-- architecture/
|   +-- api/
|   +-- threat-model/
|   +-- connector-guides/
|
+-- scripts/
+-- .github/
+-- README.md
```

A modular monolith is acceptable for the first milestone if deployment simplicity is more important than independent scaling. The internal package boundaries MUST remain equivalent to the service boundaries above.

---

# 6. Core domain model

Create these domain concepts.

## Tenant

```text
Tenant
- id
- name
- status
- createdAt
- updatedAt
```

## SourceConnection

```text
SourceConnection
- id
- tenantId
- type
- displayName
- baseUrl
- authType
- credentialReference
- status
- lastHealthCheck
- createdAt
- updatedAt
```

Supported types:

```text
GITHUB
JIRA_CLOUD
CONFLUENCE_CLOUD
```

## SourceScope

Defines what to scan.

```text
SourceScope
- id
- sourceConnectionId
- externalId
- scopeType
- includePattern
- excludePattern
- scanHistory
- scanAttachments
- scanComments
- enabled
```

Examples:

```text
GitHub organization
GitHub repository
Jira project
Confluence space
```

## ScanJob

```text
ScanJob
- id
- tenantId
- sourceConnectionId
- scanType
- status
- cursor
- startedAt
- completedAt
- itemsDiscovered
- itemsScanned
- findingsCreated
- errorCount
```

Scan types:

```text
FULL
HISTORICAL
INCREMENTAL
MANUAL_RESUMPTION
TARGETED
```

## ContentItem

Canonical normalized object.

```text
ContentItem
- id
- tenantId
- sourceConnectionId
- externalObjectId
- parentObjectId
- objectType
- version
- path
- title
- authorExternalId
- authorDisplayName
- createdAt
- modifiedAt
- mimeType
- sizeBytes
- contentHash
- sourceUrl
- current
- historical
- metadataJson
```

Do not store full raw content by default.

## ContentChunk

Large objects are chunked.

```text
ContentChunk
- id
- contentItemId
- sequence
- textHash
- byteStart
- byteEnd
- extractedText
```

For privacy-first deployments, `extractedText` can be transient only.

## DetectionRule

```text
DetectionRule
- id
- ruleId
- name
- secretType
- detectorType
- provider
- regex
- keywordSet
- entropyThreshold
- severity
- enabled
- verificationType
- ruleVersion
```

Detector types:

```text
REGEX
KEYWORD
ENTROPY
COMPOSITE
PRIVATE_KEY
URL_CREDENTIAL
CUSTOM
```

## SecretCandidate

Ephemeral detection output.

```text
SecretCandidate
- detectorId
- secretType
- startOffset
- endOffset
- matchedValue
- context
- confidence
- metadata
```

Never persist `matchedValue` unless encrypted forensic mode is explicitly enabled.

## SecretFingerprint

```text
SecretFingerprint
- id
- tenantId
- fingerprint
- secretType
- provider
- createdAt
```

Fingerprint:

```text
HMAC-SHA256(normalizedSecret, tenantScopedHmacKey)
```

Never use plain SHA-256 for a low-entropy password because offline guessing would remain possible.

## Finding

```text
Finding
- id
- tenantId
- fingerprintId
- ruleId
- sourceConnectionId
- contentItemId
- status
- exposureState
- confidence
- severity
- riskScore
- firstSeenAt
- lastSeenAt
- firstDetectedAt
- locationJson
- maskedSecret
- verificationStatus
- verificationCheckedAt
- ownerJson
- createdAt
- updatedAt
```

Statuses:

```text
OPEN
ACKNOWLEDGED
IN_REMEDIATION
RESOLVED
FALSE_POSITIVE
ACCEPTED_RISK
REOPENED
```

Exposure state:

```text
CURRENT
HISTORICAL
CURRENT_AND_HISTORICAL
REMOVED
REAPPEARED
```

Verification:

```text
NOT_ATTEMPTED
ACTIVE
INVALID
REVOKED
EXPIRED
RATE_LIMITED
UNKNOWN
NOT_SUPPORTED
```

---

# 7. Database tables

At minimum:

```text
tenants
users
roles
user_roles

source_connections
source_scopes
source_sync_state

scan_jobs
scan_job_items
scan_failures

content_items
content_chunks
content_relationships

detection_rules
detection_rule_versions

secret_fingerprints
findings
finding_occurrences
finding_status_history

verification_attempts
risk_assessments
risk_policies

allow_lists
false_positive_rules

notifications
audit_events
```

Important indexes:

```text
findings(tenant_id, status)
findings(tenant_id, severity)
findings(tenant_id, exposure_state)
findings(tenant_id, verification_status)
findings(fingerprint_id)
finding_occurrences(content_item_id)
content_items(tenant_id, source_connection_id, external_object_id)
content_items(content_hash)
scan_jobs(source_connection_id, status)
```

Use tenant_id in every tenant-owned table.

---

# 8. Source connector SPI

Create:

```java
public interface SourceConnector {

    SourceType getType();

    SourceCapabilities getCapabilities();

    ConnectionHealth checkConnection(SourceConnectionConfig config);

    Stream<SourceScopeItem> discoverScopes(
        SourceConnectionConfig config
    );

    ScanPage<ContentReference> discoverCurrent(
        SourceConnectionConfig config,
        ScanScope scope,
        Cursor cursor
    );

    ScanPage<ContentReference> discoverHistorical(
        SourceConnectionConfig config,
        ScanScope scope,
        Cursor cursor
    );

    ContentPayload fetchContent(
        SourceConnectionConfig config,
        ContentReference reference
    );

    Optional<ChangeCursor> getIncrementalCursor(
        SourceConnectionConfig config,
        ScanScope scope
    );
}
```

Capabilities:

```text
CURRENT_CONTENT
HISTORY
COMMENTS
ATTACHMENTS
VERSIONS
INCREMENTAL
DELETED_OBJECTS
AUTHOR_METADATA
```

Never assume every connector supports every capability.

---

# 9. GitHub connector

## Authentication

Prefer GitHub App authentication for enterprise integrations.

Support PAT only as a fallback for development/smaller deployments.

## Discovery

1. Fetch installation/organization.
2. Enumerate repositories.
3. Apply configured scope filters.
4. Discover default and all relevant branches/refs.
5. Create repository scan tasks.

## Current scan

Retrieve current repository files or mirror the repository.

## Historical scan

For deep history, create a local mirror:

```bash
git clone --mirror <repository>
```

Then inspect:

```text
all refs
branches
tags
commits
trees
blobs
renames
deletions
commit diffs
```

Preferred scan model:

```text
repository
 -> refs
 -> commits
 -> diff/content
 -> canonical ContentItem
```

A commit finding should record:

```text
repository
commitSha
branch/ref if known
path
lineStart
lineEnd
author
commitTimestamp
commitMessageHash or sanitized message
```

Do not retain commit messages if they can themselves contain secrets unless policy explicitly requires it.

## Important semantics

The product must state:

"Historical coverage includes reachable history available through the configured source."

Do NOT claim that permanently garbage-collected remote Git objects are necessarily recoverable.

## Incremental scan

Maintain:

```text
lastScannedCommit
lastScannedTimestamp
scanCursor
```

Scan only changed commits/content after the cursor.

---

# 10. Jira Cloud connector

Jira is not Git. Its history is modeled around issue fields, comments, changelog, attachments and related content.

## Current scan

For each configured project:

1. Search issues using paginated JQL.
2. Retrieve required issue fields.
3. Retrieve comments.
4. Identify attachment metadata.
5. Download eligible textual/document attachments.
6. Retrieve changelog/history where policy allows.
7. Normalize each content source.

Minimum content candidates:

```text
summary
description
environment fields
custom fields
comments
worklog text where configured
attachment filenames/contents
changelog values
```

Do not scan only `description`.

## Jira changelog

Normalize a changelog item like:

```json
{
  "objectType": "JIRA_CHANGE",
  "issueKey": "PAY-1234",
  "field": "description",
  "fromValue": "...",
  "toValue": "...",
  "changedAt": "...",
  "authorId": "..."
}
```

Both the old and new values can be relevant.

## Attachments

Download only supported/safe content types.

Process:

```text
attachment metadata
 -> content download
 -> size limit check
 -> file type detection
 -> extractor
 -> chunks
 -> detection
```

Use configurable maximum:

```text
maxAttachmentBytes
```

Use a dead-letter queue for extraction failures.

---

# 11. Confluence Cloud connector

## Current content

Scan configured spaces and:

```text
pages
blog posts if enabled
inline comments
footer comments
attachments
```

## Historical content

For each page:

```text
page
 -> version list
 -> version content
 -> detection
```

For each relevant attachment:

```text
attachment
 -> attachment versions if configured
 -> content
 -> detection
```

Store:

```text
pageId
pageTitle
spaceKey/spaceId
versionNumber
author
versionTimestamp
```

Use version number plus content hash as the idempotency key.

## Comments

Support:

```text
inline comments
footer comments
attachment comments where supported
```

Comments are independent content items but preserve their parent page/attachment relationship.

---

# 12. Pagination, rate limits and retries

All connectors MUST implement:

- pagination
- exponential backoff
- provider-specific retry-after handling
- 429 handling
- 5xx retry
- connection timeout
- read timeout
- circuit breaker
- dead-letter handling

Do not use unbounded retries.

Recommended:

```text
attempt 1: 1s
attempt 2: 2s
attempt 3: 4s
attempt 4: 8s
attempt 5: dead-letter
```

Add jitter.

Persist connector cursor after durable processing, not before.

---

# 13. Canonical content processing pipeline

Every source must enter:

```text
SourceConnector
    |
    v
ContentPayload
    |
    v
File/content type detection
    |
    v
Text extraction
    |
    v
Normalization
    |
    v
Chunking
    |
    v
Candidate detection
    |
    v
Candidate enrichment
    |
    v
False-positive filtering
    |
    v
Fingerprint
    |
    v
Correlation
    |
    v
Risk scoring
    |
    v
Optional verification
    |
    v
Finding persistence
```

---

# 14. Content extractor SPI

```java
public interface ContentExtractor {

    boolean supports(MediaType mediaType, String filename);

    ExtractionResult extract(InputStream input);
}
```

Implement:

```text
PlainTextExtractor
JsonExtractor
YamlExtractor
XmlExtractor
PropertiesExtractor
CsvExtractor
MarkdownExtractor
HtmlExtractor
PdfExtractor
WordExtractor
ExcelExtractor
ArchiveExtractor
ImageMetadataExtractor
```

For images, OCR is optional and disabled by default.

Do not run OCR on every attachment because of cost/performance/privacy. Make it a policy-controlled advanced feature.

---

# 15. File safety

Before processing an attachment:

1. Verify declared size.
2. Verify actual byte size.
3. Detect MIME from file signature.
4. Reject executable files by default.
5. Limit archive recursion.
6. Detect zip bombs.
7. Limit decompressed size.
8. Limit maximum nesting depth.
9. Scan only allowed extensions/content types.

Config:

```yaml
content:
  maxFileSizeBytes: 52428800
  maxDecompressedBytes: 209715200
  maxArchiveDepth: 3
  allowedTypes:
    - text/*
    - application/json
    - application/xml
    - application/pdf
    - application/zip
    - application/vnd.openxmlformats-officedocument.*
```

---

# 16. Detection architecture

Create:

```java
public interface SecretDetector {

    String getId();

    DetectorMetadata getMetadata();

    List<SecretCandidate> detect(ContentChunk chunk);
}
```

And:

```java
public interface SecretVerifier {

    boolean supports(SecretType type);

    VerificationResult verify(
        VerificationRequest request
    );
}
```

Detector categories:

```text
PROVIDER_SIGNATURE
REGEX
KEYWORD
ENTROPY
PRIVATE_KEY
AUTH_URL
COMPOSITE
CUSTOM
```

---

# 17. Initial detectors

Implement at least:

```text
AWS Access Key
AWS Secret Key
Azure Client Secret
Azure Storage Key
GitHub Token
GitLab Token
Google API Key
Google Service Account Private Key
Slack Token
Stripe API Key
SendGrid API Key
Mailchimp API Key
OpenAI API Key
npm Token
PyPI Token
SSH Private Key
PEM Private Key
JWT
Basic Authentication
Generic OAuth Client Secret
Generic Database Connection String
Generic Password
Generic API Key
Generic Bearer Token
Generic High Entropy String
```

Support detector metadata:

```text
secretType
provider
severity
confidence
keywords
regex
entropyThreshold
verifier
safeToVerify
```

Do not copy detector implementations blindly from third-party projects into proprietary code. Re-implement or use compatible libraries according to their licenses and version requirements.

---

# 18. Detection strategy

Use three main signals.

## Signal A: structured provider patterns

Example:

```text
AWS key prefixes
GitHub token prefixes
private-key delimiters
known token formats
```

## Signal B: context

Examples:

```text
password:
passwd:
secret:
api_key:
apikey:
access_token:
client_secret:
authorization:
bearer:
jdbc:
mongodb://
postgresql://
```

Use configurable context windows.

## Signal C: entropy

Calculate Shannon entropy.

Example formula:

```text
H(X) = -sum(p(x) * log2(p(x)))
```

Apply only to candidate strings above minimum length.

Never treat entropy as sufficient proof of a secret.

---

# 19. Composite confidence model

Example:

```text
confidence =
  0.45 * detectorConfidence
+ 0.20 * contextScore
+ 0.15 * entropyScore
+ 0.10 * sourceRiskScore
+ 0.10 * verificationConfidence
```

Normalize to 0-100.

Suggested classification:

```text
90-100 CRITICAL
75-89  HIGH
50-74  MEDIUM
25-49  LOW
0-24   INFO
```

These weights MUST be configurable.

Do not hard-code business risk decisions into detectors.

---

# 20. False-positive framework

Implement allowlists for:

- exact fingerprint
- detector/rule
- file path
- repository
- Jira project
- Confluence space
- regex
- value pattern
- environment
- content label

Examples:

```text
<YOUR_API_KEY>
YOUR_PASSWORD
example.com
example-token
changeme
xxxxxxxx
********
${TOKEN}
${PASSWORD}
```

False-positive filters should run after candidate detection and before finding creation.

Use a reason code:

```text
TEMPLATE_VALUE
PLACEHOLDER
ALLOWLIST
KNOWN_TEST_VALUE
LOW_CONFIDENCE
NON_SECRET_CONTEXT
```

---

# 21. Fingerprinting

Do NOT use plain SHA-256(secret) as the only identity mechanism.

Use tenant-scoped HMAC:

```text
fingerprint = HMAC-SHA256(
    tenantSecret,
    canonicalize(secret)
)
```

Canonicalization should be deterministic for supported credential formats.

Store:

```text
fingerprint
secretType
provider
```

The fingerprint enables cross-system correlation without storing plaintext.

---

# 22. Occurrence model

One credential may have many occurrences.

Example:

```text
Fingerprint F1
|
+-- GitHub / repo-A / commit-123
+-- GitHub / repo-A / commit-456
+-- Jira / PAY-123
+-- Confluence / page-77 / v19
+-- Confluence / attachment-991
```

Model:

```text
SecretFingerprint
    |
    +-- Finding/Occurrence 1
    +-- Finding/Occurrence 2
    +-- Finding/Occurrence N
```

Avoid treating every occurrence as a separate independent incident.

---

# 23. First seen / last seen calculation

For each fingerprint:

```text
firstSeenAt = minimum(occurrence.timestamp)
lastSeenAt  = maximum(occurrence.timestamp)
```

Current exposure:

```text
exists at least one occurrence marked current
```

Historical exposure:

```text
exists at least one historical occurrence
```

Current-and-historical:

```text
both conditions true
```

If a secret disappears from current content after previously being found, mark the historical finding as REMOVED/HISTORICAL, but do not automatically resolve the security incident.

---

# 24. Risk engine

Risk must combine:

```text
secret confidence
+
verification status
+
environment
+
privilege
+
source visibility
+
recency
+
exposure duration
+
number of locations
+
external/public exposure
```

Example score:

```text
risk = weighted(
    confidence,
    verifiedActive,
    production,
    privileged,
    publicExposure,
    exposureDuration,
    propagationCount
)
```

Make these factors policy-driven.

Example rules:

```yaml
risk:
  critical:
    when:
      - verifiedStatus: ACTIVE
      - environment: PRODUCTION
      - privilege: ADMIN

  high:
    when:
      - verifiedStatus: ACTIVE
      - sourceVisibility: INTERNAL

  medium:
    when:
      - confidence: HIGH
      - verification: UNKNOWN
```

---

# 25. Verification architecture

Verification is OPTIONAL and heavily controlled.

Create provider-specific implementations:

```text
AwsCredentialVerifier
GithubTokenVerifier
GitlabTokenVerifier
AzureCredentialVerifier
StripeKeyVerifier
SlackTokenVerifier
GenericOAuthVerifier
```

Return:

```text
ACTIVE
INVALID
REVOKED
EXPIRED
RATE_LIMITED
UNKNOWN
NOT_SUPPORTED
```

Verification MUST:

1. Use least-impact APIs.
2. Never perform write operations.
3. Never enumerate sensitive data unnecessarily.
4. Have strict timeout limits.
5. Have rate limits.
6. Be disabled by default for unknown/custom credentials.
7. Record an audit event.
8. Never log the raw secret.

Do not verify credentials automatically merely because they were detected.

Make policy configurable:

```text
MANUAL_ONLY
AUTO_HIGH_CONFIDENCE
AUTO_PROVIDER_SUPPORTED
DISABLED
```

---

# 26. Connector credential storage

Never store provider credentials directly in the application database.

Use:

```text
Vault / Cloud KMS / Secret Manager
```

Database stores only:

```text
credentialReference
```

Example:

```text
secretmanager://tenant/123/source/github-prod
```

Use encrypted-at-rest storage and key rotation.

---

# 27. Source permissions

Request minimum read permissions.

GitHub:

```text
read repositories
read metadata
security/secret scanning API only if integrating with native findings
```

Jira:

```text
read projects/issues/comments/attachments/changelog as necessary
```

Confluence:

```text
read content
read spaces
read attachments
read comments
read versions
```

Do not request write/delete/admin unless future remediation explicitly needs them.

---

# 28. GitHub native secret scanning integration

Do NOT blindly duplicate GitHub native results.

Provide an optional connector capability:

```text
nativeSecretScanning = true
```

Retrieve GitHub secret scanning alerts and use them as:

```text
externalFindingSource
```

Cross-correlate them with your own scanner.

Fields:

```text
providerFindingId
providerAlertType
providerSecretType
providerState
providerLocation
```

This provides a useful comparison:

```text
Provider detected it
Our engine detected it
Both detected it
Only our engine detected it
```

GitHub exposes repository and organization secret-scanning alert APIs and scan history for Advanced Security repositories.

---

# 29. Scan orchestration

Create:

```java
public interface ScanOrchestrator {

    UUID startFullScan(UUID sourceId);

    UUID startHistoricalScan(UUID sourceId);

    UUID startIncrementalScan(UUID sourceId);

    UUID startTargetedScan(TargetedScanRequest request);
}
```

Each scan creates partitioned work:

```text
ScanJob
 |
 +-- ScanTask
 |     |
 |     +-- repository-A
 |     +-- repository-B
 |
 +-- ScanTask
       |
       +-- jira-project-A
       +-- jira-project-B
```

Every ScanTask must be resumable.

---

# 30. Event model

Use events similar to:

```json
{
  "eventType": "CONTENT_DISCOVERED",
  "eventVersion": 1,
  "tenantId": "...",
  "sourceId": "...",
  "contentItemId": "...",
  "scanJobId": "...",
  "occurredAt": "..."
}
```

Events:

```text
SCAN_STARTED
SCAN_PROGRESS
CONTENT_DISCOVERED
CONTENT_EXTRACTED
CONTENT_SCANNED
CANDIDATE_DETECTED
FINDING_CREATED
FINDING_UPDATED
VERIFICATION_COMPLETED
SCAN_COMPLETED
SCAN_FAILED
```

Consumers should be idempotent.

---

# 31. REST APIs

## Authentication

```text
POST /api/v1/auth/login
POST /api/v1/auth/logout
GET  /api/v1/auth/me
```

Use OIDC/SSO for enterprise deployment.

## Sources

```text
GET    /api/v1/sources
POST   /api/v1/sources
GET    /api/v1/sources/{id}
PUT    /api/v1/sources/{id}
DELETE /api/v1/sources/{id}
POST   /api/v1/sources/{id}/test
POST   /api/v1/sources/{id}/scan
POST   /api/v1/sources/{id}/historical-scan
POST   /api/v1/sources/{id}/incremental-scan
```

## Findings

```text
GET    /api/v1/findings
GET    /api/v1/findings/{id}
PATCH  /api/v1/findings/{id}
POST   /api/v1/findings/{id}/verify
POST   /api/v1/findings/{id}/resolve
POST   /api/v1/findings/{id}/false-positive
POST   /api/v1/findings/{id}/accept-risk
GET    /api/v1/findings/{id}/occurrences
```

Filters:

```text
source
provider
secretType
severity
status
verificationStatus
current/historical
repository
jiraProject
confluenceSpace
owner
date range
```

## Scans

```text
GET /api/v1/scans
GET /api/v1/scans/{id}
GET /api/v1/scans/{id}/tasks
POST /api/v1/scans/{id}/pause
POST /api/v1/scans/{id}/resume
POST /api/v1/scans/{id}/cancel
```

## Detectors

```text
GET /api/v1/detectors
GET /api/v1/detectors/{id}
POST /api/v1/detectors/custom
PUT /api/v1/detectors/custom/{id}
```

## Reports

```text
GET /api/v1/reports/summary
GET /api/v1/reports/findings.csv
GET /api/v1/reports/findings.json
GET /api/v1/reports/findings.sarif
```

---

# 32. Finding API response

Never return a raw secret.

Example:

```json
{
  "id": "f-123",
  "secretType": "AWS_ACCESS_KEY",
  "provider": "AWS",
  "maskedValue": "AKIA••••••••9X7P",
  "severity": "CRITICAL",
  "confidence": 98,
  "riskScore": 95,
  "status": "OPEN",
  "verificationStatus": "ACTIVE",
  "exposureState": "CURRENT_AND_HISTORICAL",
  "firstSeenAt": "2024-03-11T10:21:00Z",
  "lastSeenAt": "2026-10-05T01:12:00Z",
  "occurrenceCount": 17,
  "sources": [
    "GitHub",
    "Jira",
    "Confluence"
  ]
}
```

---

# 33. UI

Implement these screens.

## 33.1 Executive dashboard

Cards:

```text
Total findings
Critical
High
Verified active
Current exposures
Historical exposures
Open findings
Resolved findings
```

Charts:

```text
findings over time
findings by source
findings by secret type
findings by severity
findings by project/team
verification distribution
```

## 33.2 Findings table

Columns:

```text
Severity
Secret type
Provider
Source
Location
Current/Historical
Verified status
First seen
Last seen
Occurrences
Status
```

## 33.3 Finding details

Show:

```text
secret type
masked value
risk score
confidence
verification
timeline
all occurrences
source hierarchy
author
first seen
last seen
remediation guidance
audit history
```

Never show plaintext secret in the normal UI.

## 33.4 Source management

Allow:

```text
connect source
test connection
select scopes
configure scan policy
run scan
view scan progress
```

## 33.5 Rules

Support:

```text
built-in detectors
custom regex rules
allowlists
ignore paths
ignore projects/spaces
```

---

# 34. Timeline view

For each credential fingerprint:

```text
2024-03-11  GitHub commit        FIRST SEEN
2024-03-18  GitHub commit
2024-04-03  Jira PAY-412
2024-05-09  Confluence v12
2024-05-12  Confluence v13     REMOVED
2025-01-07  GitHub commit      REAPPEARED
2026-10-05  Jira comment       CURRENT
```

This is a core product feature.

---

# 35. Exposure graph

Start with relational links in PostgreSQL.

Entities:

```text
Credential
Occurrence
Repository
Commit
Issue
Comment
Page
PageVersion
Attachment
User
Project
Space
```

Relationships:

```text
FOUND_IN
INTRODUCED_BY
MODIFIED_BY
SAME_SECRET_AS
REAPPEARED_IN
REMOVED_FROM
BELONGS_TO
```

Do not introduce Neo4j initially unless actual graph queries prove the need.

---

# 36. Search

Implement a unified search:

```text
"AKIA"
"PAY-1234"
"aws"
"production"
"developer@example.com"
```

Search metadata, not raw secrets.

For secret lookup use fingerprint or masked representation.

---

# 37. Incremental scanning

Each source has its own cursor.

Store:

```text
sourceId
scopeId
cursor
lastSuccessfulPoll
lastSuccessfulScan
```

Use source-specific change semantics.

GitHub:

```text
new commits / ref changes
```

Jira:

```text
updated issue timestamp / changelog / incremental query
```

Confluence:

```text
updated content / versions
```

Always support a periodic reconciliation full scan because incremental mechanisms can miss changes after connector errors or provider-side retention behavior.

Recommended:

```text
incremental: every 15-60 minutes
reconciliation: daily/weekly depending on scale
full historical: on demand
```

---

# 38. Scheduling

The scan scheduler should support:

```text
ON_DEMAND
HOURLY
EVERY_6_HOURS
DAILY
WEEKLY
```

Use Quartz or Kubernetes scheduling.

Do not couple scheduling directly to connector code.

---

# 39. Concurrency

Use bounded worker pools.

Examples:

```text
connector discovery workers
content download workers
extraction workers
detection workers
verification workers
```

Each has its own concurrency limit.

Provider rate limits should dynamically constrain worker concurrency.

---

# 40. Caching

Cache:

```text
provider metadata
repository metadata
space/project metadata
detector configuration
rule versions
```

Do NOT cache raw secrets.

Short-lived caching of raw source content should be disabled by default.

---

# 41. Audit logging

Every sensitive operation:

```text
source connected
source credential changed
scan started
scan cancelled
finding viewed
finding exported
verification executed
finding status changed
allowlist changed
custom detector changed
```

Audit event:

```text
actor
tenant
action
resourceType
resourceId
timestamp
result
IP/device metadata if enterprise policy allows
```

Never log raw secrets.

---

# 42. Security controls

Implement:

```text
tenant isolation
RBAC
OIDC/SAML
CSRF protection where applicable
rate limiting
input validation
SSRF protection
secure outbound HTTP client
certificate validation
TLS
encryption at rest
KMS-backed key management
secret redaction in logs
audit logs
secure file extraction
zip-bomb protection
maximum payload sizes
request timeouts
```

SSRF protection is particularly important because source connectors and attachment downloads accept URLs from external systems.

Only allow outbound requests to explicitly configured source domains.

---

# 43. Threat model

Threats:

## T1: Attacker accesses findings

Mitigation:

- strict RBAC
- tenant isolation
- encryption
- no plaintext secrets
- audit log

## T2: Scanner logs a secret

Mitigation:

- log scrubbing
- structured logging
- secret-aware serializers

## T3: Compromised source credential

Mitigation:

- least privilege
- secret manager
- credential rotation
- source-specific scopes

## T4: Malicious attachment

Mitigation:

- file type detection
- size limits
- sandboxing where necessary
- archive limits
- disable executable processing

## T5: SSRF

Mitigation:

- allowlisted domains
- no arbitrary URL fetching
- DNS/IP validation
- block localhost/private IP ranges unless explicitly required
- re-validate after DNS resolution

## T6: Denial of service

Mitigation:

- quotas
- concurrency limits
- file size limits
- chunk limits
- timeouts
- backpressure

## T7: Cross-tenant data leakage

Mitigation:

- tenant_id everywhere
- repository-level authorization checks
- row-level security as defense in depth where practical
- security integration tests

---

# 44. Third-party scanner integration strategy

Study and selectively reuse ideas from:

- Gitleaks
- TruffleHog
- detect-secrets
- Secretlint

Use them as references or optional engines, not as the domain architecture.

Important design lessons:

### detect-secrets

Its design separates:

- plugins
- filters
- transformers
- baseline
- audit

Adopt those concepts in the new engine.

### TruffleHog

Its detector model separates:

- candidate extraction
- validation/verification
- structured credential parts

Adopt the same conceptual separation.

### Gitleaks

Adopt:

- rule IDs
- rule metadata
- line/path context
- fingerprints
- SARIF/JSON reporting

Do not make the product dependent on Gitleaks internals.

---

# 45. Finding fingerprint vs credential fingerprint

Use two different identities.

## Credential fingerprint

Identifies the credential itself:

```text
HMAC(secret)
```

## Occurrence fingerprint

Identifies a specific occurrence:

```text
HMAC(
  credentialFingerprint
  + sourceType
  + objectId
  + version
  + location
)
```

This allows:

```text
same credential across systems
```

while keeping occurrences distinct.

---

# 46. Deduplication

A detector may produce duplicate candidates.

Deduplicate by:

```text
same contentItem
same detector
same normalized secret
same logical location
```

But do not merge:

```text
same credential in two different source objects
```

because those are separate occurrences.

---

# 47. Content hashing

For every ContentItem:

```text
contentHash = SHA-256(normalizedContent)
```

Idempotency key:

```text
sourceType
sourceConnectionId
externalObjectId
version
contentHash
```

If unchanged, skip detection.

---

# 48. Handling deleted historical content

A current scan cannot always reconstruct content that has been permanently deleted.

Represent scan coverage honestly:

```text
coverage:
  reachableHistory: COMPLETE
  providerHistory: PARTIAL
  attachments: PARTIAL
```

Expose connector coverage in the UI.

Never claim absolute historical completeness unless guaranteed by the provider and the connector.

---

# 49. Report formats

Support:

## JSON

For APIs/integrations.

## CSV

For auditors/security teams.

## SARIF

For DevSecOps tool integration.

## PDF/HTML executive report

Optional later.

Report fields:

```text
findingId
severity
secretType
provider
source
location
current/historical
verification
firstSeen
lastSeen
occurrences
riskScore
status
```

Never export plaintext secret by default.

---

# 50. Notifications

Support:

```text
email
Slack
Microsoft Teams
webhook
Jira ticket creation
```

Notification triggers:

```text
new critical finding
new verified-active credential
credential reappeared
scan failure
source connection unhealthy
high finding volume anomaly
```

Do not notify for every occurrence of an already-known credential.

Notify on lifecycle changes.

---

# 51. Remediation

MVP:

```text
mark resolved
mark false positive
accept risk
assign owner
add remediation comment
```

Later phases can support:

```text
credential rotation workflows
Git secret-removal guidance
Jira/Confluence cleanup
ticket creation
automated remediation
```

Auto-deletion from source systems MUST NOT be included in MVP.

---

# 52. Ownership inference

Infer ownership from:

```text
Git commit author
Git repository owner
Jira assignee
Jira project lead
Confluence page owner/creator
team metadata
configured mapping
```

Store:

```text
ownerType
ownerId
ownerConfidence
```

Do not assume source author is always the responsible owner.

---

# 53. Environment inference

Infer:

```text
PRODUCTION
STAGING
UAT
DEV
TEST
UNKNOWN
```

Signals:

```text
file path
branch name
repository name
Jira fields
project name
Confluence space
context keywords
configuration content
```

Make inference explainable:

```json
{
  "environment": "PRODUCTION",
  "confidence": 91,
  "signals": [
    "repository contains prod configuration",
    "Confluence page tagged production"
  ]
}
```

---

# 54. Custom enterprise rules

Allow:

```yaml
id: internal-api-key
name: Internal API Key
secretType: INTERNAL_API_KEY
detectorType: COMPOSITE
regex: 'IAPI-[A-Z0-9]{32}'
keywords:
  - api_key
severity: HIGH
verification: NONE
```

Version every custom rule.

Findings must store the rule version used for detection so future rule changes do not alter historical evidence retroactively.

---

# 55. Configuration

Example:

```yaml
platform:
  tenantIsolation: true

scan:
  maxConcurrency: 20
  maxContentBytes: 10485760
  maxAttachmentBytes: 52428800
  enableHistorical: true

detection:
  entropy:
    enabled: true
    base64Threshold: 4.5
    hexThreshold: 3.0

verification:
  mode: MANUAL_ONLY
  timeoutSeconds: 5

security:
  storeRawEvidence: false
  forensicRetentionHours: 24

providers:
  github:
    rateLimitSafetyFactor: 0.8
  jira:
    rateLimitSafetyFactor: 0.8
  confluence:
    rateLimitSafetyFactor: 0.8
```

No credentials in YAML checked into source control.

---

# 56. API error model

All APIs use:

```json
{
  "timestamp": "2026-10-05T01:00:00Z",
  "status": 400,
  "code": "INVALID_REQUEST",
  "message": "Invalid scan scope",
  "traceId": "..."
}
```

Never include secrets in exception messages.

---

# 57. Observability

Metrics:

```text
scan_jobs_started_total
scan_jobs_completed_total
scan_jobs_failed_total
content_items_discovered_total
content_items_scanned_total
detection_candidates_total
findings_created_total
findings_suppressed_total
verification_attempts_total
verification_active_total
connector_request_total
connector_rate_limit_total
attachment_extraction_failure_total
scan_duration_seconds
queue_depth
worker_utilization
```

Tracing:

```text
scan request
 -> connector
 -> content extraction
 -> detector
 -> correlation
 -> risk
 -> persistence
```

Every scan/job gets:

```text
traceId
scanJobId
tenantId
```

Do not put secrets in trace attributes.

---

# 58. Performance targets for MVP

Target, not hard SLA:

```text
API p95 read latency: < 500 ms for normal dashboard queries
API p95 write latency: < 1 s
source connector HTTP timeout: 10 s
verification timeout: 5 s
content extraction timeout: 10 s/object
```

Historical scanning should be asynchronous.

Target horizontal scalability:

```text
1 million content items/day
```

as an architectural test target, not an assumption about initial deployment.

---

# 59. Testing strategy

## Unit tests

Every:

- detector
- filter
- extractor
- risk policy
- fingerprint function
- connector parser
- cursor strategy

## Integration tests

Test against mocked providers.

For Jira:

```text
issue
comment
attachment
changelog
pagination
429
5xx
permission denied
```

For Confluence:

```text
page
page version
comments
attachment
attachment version
pagination
429
404
```

For GitHub:

```text
repository
branch
commit
deleted file
rename
historical commit
pagination
permission issue
rate limit
```

## Security tests

At minimum:

```text
cross-tenant access
IDOR
SSRF
file upload
zip bomb
oversized file
JWT/session security
log secret leakage
export authorization
verification authorization
```

## End-to-end tests

Fixture dataset:

```text
GitHub repo with current secret
Git history with deleted secret
Jira issue current secret
Jira historical description secret
Jira comment secret
Jira attachment secret
Confluence page secret
Confluence historical page version secret
Confluence comment secret
Confluence attachment secret
same secret in three systems
known false positives
```

Expected output must be deterministic.

---

# 60. Golden test corpus

Create:

```text
test-fixtures/
  github/
  jira/
  confluence/
  attachments/
  secrets/
  false-positives/
  custom-rules/
```

Include synthetic secrets only.

Never commit real production credentials.

Each detector needs:

```text
positive samples
negative samples
near-miss samples
false-positive samples
```

Maintain a detector precision/recall report.

---

# 61. AI coding instructions

Use the following engineering rules while implementing:

1. Do not create a giant monolithic class.
2. Keep connector code independent from detection code.
3. Prefer interfaces/SPI boundaries.
4. Write tests before or together with each major implementation.
5. Use constructor injection.
6. Use immutable DTOs where practical.
7. Validate all API input.
8. Never log secrets.
9. Never return raw secrets from normal APIs.
10. Never place source credentials in application properties committed to Git.
11. Use Flyway migrations.
12. Use optimistic locking for mutable findings.
13. Make all worker operations idempotent.
14. Make scans resumable.
15. Implement pagination everywhere.
16. Handle provider rate limits explicitly.
17. Add retry with jitter.
18. Do not perform credential verification by default.
19. Use feature flags for experimental detection/verification.
20. Keep detector rule versions.
21. Add correlation tests.
22. Add tenant-isolation tests.
23. Add SSRF and malicious-file tests.
24. Write OpenAPI documentation for every public API.
25. Do not silently swallow connector failures; persist scan failures.

---

# 62. Recommended implementation sequence

## Phase 0 — bootstrap

Implement:

```text
repo
CI/CD
Docker
Spring Boot
React
PostgreSQL
Flyway
authentication skeleton
tenant model
audit model
OpenAPI
observability
```

Deliverable:

```text
running application + login + empty dashboard
```

## Phase 1 — core scanning engine

Implement:

```text
ContentItem
ContentChunk
Detector SPI
Regex detector
Keyword detector
Entropy detector
Fingerprinting
Deduplication
Finding model
Risk engine
```

Deliverable:

```text
local filesystem scanner
 -> findings
```

This validates the scanning engine independently from providers.

## Phase 2 — GitHub

Implement:

```text
GitHub authentication
organization/repository discovery
current scan
historical git mirror
incremental scan
```

Deliverable:

```text
GitHub repository -> findings
```

## Phase 3 — Jira

Implement:

```text
project discovery
issue search
comments
changelog
attachments
incremental scan
```

## Phase 4 — Confluence

Implement:

```text
space discovery
page discovery
versions
comments
attachments
attachment versions
incremental scan
```

## Phase 5 — correlation

Implement:

```text
credential fingerprint
cross-source grouping
first/last seen
timeline
```

## Phase 6 — verification

Implement only provider-safe verifiers.

Start with:

```text
AWS
GitHub
GitLab
```

or another small supported set.

## Phase 7 — enterprise features

Add:

```text
SSO
RBAC
custom rules
allowlists
notifications
SARIF
advanced reports
multi-tenancy hardening
Kubernetes
Helm
```

---

# 63. MVP definition

MVP is complete when the platform can:

1. Connect to GitHub.
2. Scan current repository content.
3. Scan reachable Git history.
4. Identify at least 20 common credential classes.
5. Connect to Jira Cloud.
6. Scan issues, comments, changelog and attachments.
7. Connect to Confluence Cloud.
8. Scan pages, versions, comments and attachments.
9. Normalize all content into one model.
10. Deduplicate candidates.
11. Fingerprint credentials securely.
12. Correlate the same credential across systems.
13. Calculate first-seen/last-seen.
14. Assign risk.
15. Show findings without plaintext secrets.
16. Allow false-positive handling.
17. Support incremental scanning.
18. Produce JSON/CSV/SARIF reports.
19. Persist audit logs.
20. Pass tenant-isolation/security tests.

---

# 64. Definition of done for every connector

A connector is NOT complete merely because the API can be called.

It must provide:

```text
authentication
connection test
scope discovery
current discovery
historical discovery
pagination
rate limiting
retry
cursor persistence
idempotency
permission errors
not-found handling
structured metadata
content fetching
attachment handling where supported
metrics
tracing
unit tests
integration tests
mock fixtures
documentation
```

---

# 65. Example processing flow

Example:

```text
Confluence Page 123 Version 41
        |
        v
ContentItem
        |
        v
HTML -> normalized text
        |
        v
Detector engine
        |
        +---- AWS detector -> no
        +---- GitHub detector -> no
        +---- keyword detector -> candidate
        +---- entropy detector -> candidate
        |
        v
candidate merge
        |
        v
confidence = 87
        |
        v
fingerprint HMAC
        |
        v
existing fingerprint found
        |
        v
same credential already exists in GitHub + Jira
        |
        v
occurrence added
        |
        v
risk recalculated
        |
        v
finding updated
```

---

# 66. Example finding lifecycle

```text
DETECTED
   |
   v
OPEN
   |
   +--> FALSE_POSITIVE
   |
   +--> ACCEPTED_RISK
   |
   +--> IN_REMEDIATION
              |
              v
           VERIFIED
              |
              v
           RESOLVED
```

A finding may move backwards to:

```text
REOPENED
```

when the credential reappears.

---

# 67. Future connectors

The SPI should make these possible:

```text
GitLab
Bitbucket
Azure DevOps
Jira Data Center
Confluence Data Center
Slack
Microsoft Teams
SharePoint
OneDrive
S3
GCS
Docker Registry
Nexus
Artifactory
Jenkins
GitHub Actions
GitLab CI
```

Do not add them during MVP unless the first three connectors are stable.

---

# 68. Future product capabilities

Potential later products/features:

```text
secret blast-radius analysis
credential ownership graph
automated ticketing
credential rotation orchestration
developer IDE extension
pre-commit checks
CI/CD gates
public-internet monitoring
AI-assisted triage
natural-language security queries
compliance reporting
policy-as-code
```

AI triage must never replace deterministic detection for security-critical decisions.

---

# 69. Recommended AI implementation prompt

Use the following as the master prompt when giving the specification to an implementation AI:

---

You are a senior staff engineer and security platform architect.

Build the Credential Exposure Discovery Platform described in this specification.

Your objectives are:

1. Implement a production-quality, modular, secure application.
2. Use Java 21 + Spring Boot 3.x for backend services.
3. Use React + TypeScript for frontend.
4. Use PostgreSQL + Flyway.
5. Use Kafka/Google Pub/Sub abstraction for async processing.
6. Use Docker/Kubernetes-compatible deployment.
7. Implement the platform as modular components with clear interfaces.
8. Implement GitHub, Jira Cloud and Confluence Cloud as the first connectors.
9. Implement historical scanning, current scanning and incremental scanning.
10. Implement attachment/document extraction.
11. Implement a pluggable detection engine.
12. Never store raw secrets by default.
13. Implement tenant isolation and RBAC.
14. Implement secure HMAC fingerprinting.
15. Implement cross-source correlation.
16. Implement risk scoring.
17. Implement optional provider-safe verification.
18. Implement audit logs and observability.
19. Write automated tests for every significant component.
20. Produce an OpenAPI definition and developer documentation.

### Implementation constraints

- Do not create a giant monolith.
- Keep connector logic separate from the core scanning engine.
- Keep detection independent from source systems.
- Use interfaces for connectors, detectors, extractors, verifiers and risk policies.
- Make asynchronous jobs idempotent and resumable.
- Implement pagination and rate-limit handling.
- Never log secrets.
- Never expose plaintext secrets in normal API responses.
- Do not commit provider credentials.
- Never use arbitrary URLs for downloads without SSRF protections.
- Never execute downloaded attachments.
- Enforce file and archive size limits.
- Use migrations, not auto-generated production schema changes.
- Use structured logs and trace IDs.
- Add security tests for tenant isolation and authorization.

### Implementation methodology

Work in this order:

1. Analyze the specification and produce a final implementation plan.
2. Generate the repository structure.
3. Implement domain model and database migrations.
4. Implement authentication, tenant isolation and RBAC.
5. Implement core scanning/content/detection abstractions.
6. Implement local filesystem scanner as an end-to-end reference source.
7. Implement GitHub connector.
8. Implement Jira connector.
9. Implement Confluence connector.
10. Implement fingerprint/correlation.
11. Implement risk engine.
12. Implement verification framework.
13. Implement frontend dashboard and finding screens.
14. Implement reports.
15. Implement observability.
16. Implement Docker/Kubernetes/Helm deployment.
17. Execute all tests and fix failures.
18. Produce architecture and runbook documentation.

After each major phase:
- compile
- run unit tests
- run integration tests
- inspect failures
- fix issues
- document what changed

Do not claim a feature is implemented unless the corresponding code and tests exist.

### Required deliverables

Produce:

```text
backend source
frontend source
database migrations
OpenAPI specification
Dockerfiles
docker-compose for local development
Helm chart
Kubernetes manifests as appropriate
unit tests
integration tests
security tests
test fixtures
detector configuration
sample custom rules
sample environment configuration
README
architecture documentation
deployment documentation
API documentation
threat model
```

### Acceptance standard

The implementation is successful only when an evaluator can:

1. Run the platform locally.
2. Create a tenant.
3. Authenticate.
4. Add GitHub/Jira/Confluence connections.
5. Test source connectivity.
6. Select scopes.
7. Start a full scan.
8. Monitor scan progress.
9. Find current and historical synthetic credentials.
10. See where the same credential appeared in multiple systems.
11. See first/last seen.
12. See risk score.
13. See masked credential only.
14. Mark false positives.
15. Resolve findings.
16. Run incremental scan.
17. Export JSON/CSV/SARIF.
18. Review audit trail.
19. Confirm no secret appears in logs.
20. Confirm one tenant cannot read another tenant's findings.

Do not substitute a simplified regex demo for the requested architecture.

---

# 70. External implementation references

Use these official/public implementations as architectural references:

- Gitleaks: https://github.com/gitleaks/gitleaks
- TruffleHog: https://github.com/trufflesecurity/trufflehog
- detect-secrets: https://github.com/Yelp/detect-secrets
- Secretlint: https://github.com/secretlint/secretlint
- GitHub Secret Scanning REST API: https://docs.github.com/en/rest/secret-scanning
- Jira Cloud REST API: https://developer.atlassian.com/cloud/jira/platform/rest/v3/
- Confluence Cloud REST API v2: https://developer.atlassian.com/cloud/confluence/rest/v2/

Important implementation observations:

- detect-secrets separates plugins, filters, transformers and baselines.
- TruffleHog separates candidate extraction from credential validation.
- Gitleaks uses rule identifiers, context and fingerprints and supports machine-readable formats such as JSON and SARIF.
- GitHub exposes secret scanning alerts and scan history through APIs.
- Confluence exposes page, attachment and comment version APIs.
- Jira exposes issue comments and attachment APIs and provides historical issue changelog information.

Use these projects to learn patterns and API behavior, but maintain our own platform-level domain model and interfaces.

---

# 71. Final engineering direction

The strategic architecture is:

```text
                Enterprise Credential Exposure Platform

                          +---------------+
                          |   Connectors   |
                          +-------+-------+
                                  |
                         Normalized Content
                                  |
                          +-------v-------+
                          |   Extractors  |
                          +-------+-------+
                                  |
                          +-------v-------+
                          |   Detectors   |
                          +-------+-------+
                                  |
                          +-------v-------+
                          | Fingerprints  |
                          +-------+-------+
                                  |
                          +-------v-------+
                          | Correlation   |
                          +-------+-------+
                                  |
                          +-------v-------+
                          |  Risk Engine  |
                          +-------+-------+
                                  |
                          +-------v-------+
                          |  Verification |
                          +-------+-------+
                                  |
                          +-------v-------+
                          |   Findings    |
                          +-------+-------+
                                  |
                   +--------------+--------------+
                   |                             |
                   v                             v
             Investigation                  Remediation
```

The long-term differentiator is not the regex catalogue.

The differentiator is:

```text
cross-system historical visibility
+
credential identity/correlation
+
safe verification
+
exposure timeline
+
risk/blast-radius
+
enterprise connector framework
+
privacy-first evidence handling
```

This architecture is the foundation the implementation AI must follow.
