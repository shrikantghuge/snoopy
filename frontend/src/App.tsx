import React, { useState, useEffect } from 'react';

interface Finding {
  id: string;
  secretType: string;
  provider: string;
  maskedSecret: string;
  severity: string;
  riskScore: number;
  status: string;
  exposureState: string;
  firstSeenAt: string;
  lastSeenAt: string;
  occurrenceCount: number;
  introducedBy?: { displayName: string; email: string };
  likelyOwner?: { displayName: string; email: string };
}

interface Occurrence {
  id: string;
  sourceType: string;
  scopeKey: string;
  externalObjectId: string;
  version: string;
  isCurrent: boolean;
  lineNumber: number;
  timestamp: string;
  sourceUrl: string;
  contextSnippet: string;
  author?: { displayName: string; email: string };
}

interface PlaybookStep {
  stepNumber: number;
  category: string;
  title: string;
  description: string;
  commandOrUrl: string;
}

interface Playbook {
  secretType: string;
  sourceType: string;
  summary: string;
  steps: PlaybookStep[];
}

export default function App() {
  const [activeTab, setActiveTab] = useState<'dashboard' | 'findings' | 'sources'>('dashboard');
  const [findings, setFindings] = useState<Finding[]>([]);
  const [selectedFinding, setSelectedFinding] = useState<any | null>(null);
  const [playbook, setPlaybook] = useState<Playbook | null>(null);
  const [sources, setSources] = useState<any[]>([]);

  useEffect(() => {
    fetchFindings();
    fetchSources();
  }, []);

  const fetchFindings = async () => {
    try {
      const res = await fetch('/api/v1/findings');
      if (res.ok) {
        const data = await res.json();
        setFindings(data);
      }
    } catch (e) {
      // Fallback demo data if backend is offline during preview
      setFindings([
        {
          id: 'f-8a12b3c4',
          secretType: 'AWS_ACCESS_KEY',
          provider: 'AWS',
          maskedSecret: 'AKIA••••EXAMPLE',
          severity: 'CRITICAL',
          riskScore: 95,
          status: 'OPEN',
          exposureState: 'CURRENT_AND_HISTORICAL',
          firstSeenAt: '2024-03-11T10:21:00Z',
          lastSeenAt: '2026-10-05T07:40:00Z',
          occurrenceCount: 3,
          introducedBy: { displayName: 'Alice Developer', email: 'alice@enterprise.com' },
          likelyOwner: { displayName: 'Alice Developer', email: 'alice@enterprise.com' }
        },
        {
          id: 'f-1b98c7d6',
          secretType: 'GITHUB_TOKEN',
          provider: 'GitHub',
          maskedSecret: 'ghp_••••3456',
          severity: 'HIGH',
          riskScore: 82,
          status: 'OPEN',
          exposureState: 'CURRENT',
          firstSeenAt: '2026-10-01T14:10:00Z',
          lastSeenAt: '2026-10-05T06:12:00Z',
          occurrenceCount: 1,
          introducedBy: { displayName: 'Bob Security', email: 'bob@enterprise.com' }
        }
      ]);
    }
  };

  const fetchSources = async () => {
    try {
      const res = await fetch('/api/v1/sources');
      if (res.ok) {
        setSources(await res.json());
      }
    } catch (e) {}
  };

  const selectFindingDetails = async (id: string) => {
    try {
      const res = await fetch(`/api/v1/findings/${id}`);
      if (res.ok) {
        const details = await res.json();
        setSelectedFinding(details);

        const pbRes = await fetch(`/api/v1/findings/${id}/remediation`);
        if (pbRes.ok) {
          setPlaybook(await pbRes.json());
        }
      }
    } catch (e) {}
  };

  const criticalCount = findings.filter(f => f.severity === 'CRITICAL').length;
  const highCount = findings.filter(f => f.severity === 'HIGH').length;

  return (
    <div style={{ padding: '24px', maxWidth: '1400px', margin: '0 auto' }}>
      {/* Top Header */}
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{ width: '40px', height: '40px', background: 'linear-gradient(135deg, #3b82f6, #8b5cf6)', borderRadius: '10px', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold', fontSize: '20px' }}>S</div>
          <div>
            <h1 style={{ fontSize: '20px', fontWeight: 700 }}>Snoopy Platform</h1>
            <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>Credential Exposure Discovery &amp; Attribution</p>
          </div>
        </div>

        {/* Navigation Tabs */}
        <div className="glass-panel" style={{ display: 'flex', padding: '4px', gap: '4px' }}>
          <button className={`btn ${activeTab === 'dashboard' ? 'btn-primary' : ''}`} onClick={() => setActiveTab('dashboard')}>Dashboard</button>
          <button className={`btn ${activeTab === 'findings' ? 'btn-primary' : ''}`} onClick={() => setActiveTab('findings')}>Exposed Findings ({findings.length})</button>
          <button className={`btn ${activeTab === 'sources' ? 'btn-primary' : ''}`} onClick={() => setActiveTab('sources')}>Sources</button>
        </div>

        <div>
          <a href="/api/v1/reports/findings.sarif" download className="btn btn-primary" style={{ textDecoration: 'none' }}>Export SARIF</a>
        </div>
      </header>

      {/* DASHBOARD TAB */}
      {activeTab === 'dashboard' && (
        <div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '20px', marginBottom: '32px' }}>
            <div className="glass-panel" style={{ padding: '20px' }}>
              <div style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>Total Exposed Credentials</div>
              <div style={{ fontSize: '32px', fontWeight: 700, marginTop: '8px' }}>{findings.length}</div>
            </div>
            <div className="glass-panel" style={{ padding: '20px', borderLeft: '4px solid var(--severity-critical)' }}>
              <div style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>Critical Risk</div>
              <div style={{ fontSize: '32px', fontWeight: 700, color: '#fca5a5', marginTop: '8px' }}>{criticalCount}</div>
            </div>
            <div className="glass-panel" style={{ padding: '20px', borderLeft: '4px solid var(--severity-high)' }}>
              <div style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>High Risk</div>
              <div style={{ fontSize: '32px', fontWeight: 700, color: '#fdba74', marginTop: '8px' }}>{highCount}</div>
            </div>
            <div className="glass-panel" style={{ padding: '20px' }}>
              <div style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>Cross-System Correlated</div>
              <div style={{ fontSize: '32px', fontWeight: 700, color: '#93c5fd', marginTop: '8px' }}>
                {findings.filter(f => f.occurrenceCount > 1).length}
              </div>
            </div>
          </div>

          <h2 style={{ fontSize: '18px', marginBottom: '16px' }}>Recent Critical Findings</h2>
          <div className="glass-panel" style={{ padding: '20px' }}>
            {findings.map(f => (
              <div key={f.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 0', borderBottom: '1px solid var(--border-color)' }}>
                <div>
                  <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                    <span className={`badge badge-${f.severity.toLowerCase()}`}>{f.severity}</span>
                    <strong style={{ fontSize: '15px' }}>{f.secretType}</strong>
                    <code style={{ background: 'rgba(255,255,255,0.06)', padding: '2px 8px', borderRadius: '4px' }}>{f.maskedSecret}</code>
                  </div>
                  <div style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '4px' }}>
                    Introduced by: <strong>{f.introducedBy ? f.introducedBy.displayName : 'Unknown'}</strong> &bull; Occurrences: {f.occurrenceCount}
                  </div>
                </div>
                <button className="btn btn-primary" onClick={() => { setSelectedFinding(f); selectFindingDetails(f.id); setActiveTab('findings'); }}>View Details &amp; Fix</button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* FINDINGS TAB */}
      {activeTab === 'findings' && (
        <div style={{ display: 'grid', gridTemplateColumns: selectedFinding ? '1fr 1.2fr' : '1fr', gap: '24px' }}>
          {/* List */}
          <div className="glass-panel" style={{ padding: '20px' }}>
            <h2 style={{ fontSize: '18px', marginBottom: '16px' }}>All Exposed Credentials</h2>
            {findings.map(f => (
              <div
                key={f.id}
                onClick={() => { setSelectedFinding(f); selectFindingDetails(f.id); }}
                style={{
                  padding: '16px',
                  borderRadius: '8px',
                  marginBottom: '12px',
                  cursor: 'pointer',
                  border: selectedFinding?.id === f.id ? '1px solid var(--accent-blue)' : '1px solid transparent',
                  background: selectedFinding?.id === f.id ? 'rgba(59, 130, 246, 0.1)' : 'rgba(255,255,255,0.02)'
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                  <span className={`badge badge-${f.severity.toLowerCase()}`}>{f.severity}</span>
                  <span className={`badge badge-${f.exposureState.includes('CURRENT') ? 'current' : 'historical'}`}>{f.exposureState}</span>
                </div>
                <div style={{ fontSize: '16px', fontWeight: 600 }}>{f.secretType}</div>
                <code style={{ display: 'inline-block', margin: '4px 0', color: '#93c5fd' }}>{f.maskedSecret}</code>
                <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '6px' }}>
                  Introduced by: <strong>{f.introducedBy ? f.introducedBy.displayName : 'Unknown'}</strong>
                </div>
              </div>
            ))}
          </div>

          {/* Details & Remediation */}
          {selectedFinding && (
            <div className="glass-panel" style={{ padding: '24px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '20px' }}>
                <div>
                  <span className={`badge badge-${selectedFinding.severity?.toLowerCase()}`}>{selectedFinding.severity}</span>
                  <h2 style={{ fontSize: '22px', margin: '8px 0 4px 0' }}>{selectedFinding.secretType}</h2>
                  <code style={{ fontSize: '16px', background: 'rgba(255,255,255,0.08)', padding: '4px 10px', borderRadius: '6px' }}>{selectedFinding.maskedSecret}</code>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>Risk Score</div>
                  <div style={{ fontSize: '28px', fontWeight: 800, color: '#fca5a5' }}>{selectedFinding.riskScore} / 100</div>
                </div>
              </div>

              {/* Who Exposed It Box */}
              <div style={{ background: 'rgba(59, 130, 246, 0.08)', border: '1px solid rgba(59, 130, 246, 0.2)', padding: '16px', borderRadius: '8px', marginBottom: '20px' }}>
                <h3 style={{ fontSize: '14px', textTransform: 'uppercase', letterSpacing: '0.05em', color: '#93c5fd', marginBottom: '8px' }}>Attribution &amp; Ownership</h3>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                  <div>
                    <span style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>Originally Introduced By:</span>
                    <div style={{ fontWeight: 600 }}>{selectedFinding.introducedBy ? selectedFinding.introducedBy.displayName : 'Unknown Author'}</div>
                    <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>{selectedFinding.introducedBy?.email}</div>
                  </div>
                  <div>
                    <span style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>Likely Responsible Owner:</span>
                    <div style={{ fontWeight: 600 }}>{selectedFinding.likelyOwner ? selectedFinding.likelyOwner.displayName : 'Unassigned'}</div>
                  </div>
                </div>
              </div>

              {/* Timeline of Occurrences */}
              <h3 style={{ fontSize: '16px', marginBottom: '12px' }}>Exposure Timeline Across Systems</h3>
              <div style={{ borderLeft: '2px solid var(--border-color)', paddingLeft: '16px', marginBottom: '24px' }}>
                {selectedFinding.occurrences?.map((occ: Occurrence) => (
                  <div key={occ.id} style={{ marginBottom: '16px', position: 'relative' }}>
                    <div style={{ fontSize: '12px', color: '#93c5fd' }}>{new Date(occ.timestamp).toLocaleString()} &bull; {occ.sourceType}</div>
                    <div style={{ fontWeight: 600, fontSize: '14px' }}>{occ.scopeKey} - {occ.externalObjectId}</div>
                    {occ.contextSnippet && (
                      <pre style={{ fontSize: '11px', background: 'rgba(0,0,0,0.4)', padding: '8px', borderRadius: '4px', marginTop: '4px', overflowX: 'auto' }}>{occ.contextSnippet}</pre>
                    )}
                  </div>
                ))}
              </div>

              {/* Quick Remediation Instructions */}
              {playbook && (
                <div>
                  <h3 style={{ fontSize: '16px', marginBottom: '12px', color: '#6ee7b7' }}>Quick Remediation Instructions (Phase 1)</h3>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                    {playbook.steps.map(step => (
                      <div key={step.stepNumber} style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid var(--border-color)', padding: '12px', borderRadius: '8px' }}>
                        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', marginBottom: '4px' }}>
                          <span style={{ background: 'var(--accent-blue)', color: 'white', borderRadius: '50%', width: '20px', height: '20px', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '11px', fontWeight: 'bold' }}>{step.stepNumber}</span>
                          <strong style={{ fontSize: '14px' }}>{step.title}</strong>
                        </div>
                        <p style={{ fontSize: '13px', color: 'var(--text-secondary)', margin: '4px 0 8px 0' }}>{step.description}</p>
                        {step.commandOrUrl !== 'N/A' && (
                          <code style={{ display: 'block', background: 'black', padding: '8px', borderRadius: '4px', fontSize: '12px', color: '#a7f3d0' }}>{step.commandOrUrl}</code>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* SOURCES TAB */}
      {activeTab === 'sources' && (
        <div className="glass-panel" style={{ padding: '24px' }}>
          <h2 style={{ fontSize: '18px', marginBottom: '16px' }}>Connected Systems (GitHub, Jira, Confluence)</h2>
          <p style={{ color: 'var(--text-secondary)', marginBottom: '20px' }}>Connect enterprise developer repositories and collaboration workspaces to perform content and reachable history scans.</p>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '16px' }}>
            <div style={{ border: '1px solid var(--border-color)', padding: '16px', borderRadius: '8px' }}>
              <h3>GitHub Organization</h3>
              <p style={{ fontSize: '12px', color: 'var(--text-secondary)', margin: '8px 0' }}>Scans repos, commit diffs, reachable git history &amp; author metadata.</p>
              <button className="btn btn-primary">Connect GitHub</button>
            </div>
            <div style={{ border: '1px solid var(--border-color)', padding: '16px', borderRadius: '8px' }}>
              <h3>Jira Cloud Space</h3>
              <p style={{ fontSize: '12px', color: 'var(--text-secondary)', margin: '8px 0' }}>Scans issue summary, description, changelog history, comments &amp; attachments.</p>
              <button className="btn btn-primary">Connect Jira</button>
            </div>
            <div style={{ border: '1px solid var(--border-color)', padding: '16px', borderRadius: '8px' }}>
              <h3>Confluence Cloud Space</h3>
              <p style={{ fontSize: '12px', color: 'var(--text-secondary)', margin: '8px 0' }}>Scans pages, page versions history, comments &amp; attachments.</p>
              <button className="btn btn-primary">Connect Confluence</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
