'use client';
import { Settings } from 'lucide-react';

export default function SettingsPage() {
  return (
    <>
      <div className="topbar">
        <div className="topbar-title"><Settings size={18} /> Settings</div>
      </div>
      <div className="page-body section-gap" style={{ maxWidth: 640 }}>
        <div className="card">
          <div className="card-header"><span className="card-title">API Configuration</span></div>
          <div className="card-body section-gap" style={{ gap: 14 }}>
            <div className="form-group">
              <label className="form-label">Backend API URL</label>
              <input className="form-input" defaultValue={process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'} readOnly />
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
                Set <code style={{ background: 'var(--bg-surface)', padding: '1px 5px', borderRadius: 3 }}>NEXT_PUBLIC_API_URL</code> in your <code>.env.local</code> to change this.
              </div>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-header"><span className="card-title">About</span></div>
          <div className="card-body">
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {[
                { label: 'Application', value: 'AgileRisk Frontend' },
                { label: 'Version', value: '1.0.0' },
                { label: 'Framework', value: 'Next.js 15 (App Router)' },
                { label: 'Backend', value: 'Spring Boot 4 + AI/ML Risk Engine' },
                { label: 'Risk Models', value: 'Over-Budget · Requirement Change · Communication/Collaboration' },
              ].map(({ label, value }) => (
                <div key={label} style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border)', paddingBottom: 8 }}>
                  <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{label}</span>
                  <span style={{ fontSize: 13, color: 'var(--text-primary)', fontWeight: 500 }}>{value}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-header"><span className="card-title">API Endpoints Reference</span></div>
          <div className="card-body">
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {[
                { method: 'GET', path: '/api/v1/sprints', desc: 'List all sprints' },
                { method: 'POST', path: '/api/v1/sprints', desc: 'Create sprint' },
                { method: 'PATCH', path: '/api/v1/sprints/{id}', desc: 'Update sprint' },
                { method: 'POST', path: '/api/v1/sprints/{id}/metrics', desc: 'Add sprint metric' },
                { method: 'POST', path: '/api/v1/sprints/{id}/stories', desc: 'Add user story' },
                { method: 'POST', path: '/api/v1/sprints/{id}/evaluate-risk', desc: 'Run AI risk evaluation' },
                { method: 'GET', path: '/api/v1/sprints/{id}/risk-summary', desc: 'Get risk summary' },
                { method: 'GET', path: '/api/v1/sprints/{id}/trend', desc: 'Get risk trend data' },
                { method: 'GET', path: '/api/v1/sprints/compare?ids=1,2', desc: 'Compare sprints' },
              ].map(({ method, path, desc }) => (
                <div key={path} style={{ display: 'flex', gap: 10, alignItems: 'center', padding: '6px 0', borderBottom: '1px solid var(--border)' }}>
                  <span style={{ fontSize: 10, fontWeight: 700, background: method === 'GET' ? '#1e3a5f' : method === 'POST' ? '#14532d30' : '#3b0764', color: method === 'GET' ? '#60a5fa' : method === 'POST' ? '#4ade80' : '#c084fc', padding: '2px 6px', borderRadius: 4, width: 44, textAlign: 'center' }}>{method}</span>
                  <code style={{ fontSize: 12, color: 'var(--text-secondary)', flex: 1 }}>{path}</code>
                  <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{desc}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
