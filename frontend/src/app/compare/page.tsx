'use client';
import { useEffect, useState } from 'react';
import { sprintsApi, reportingApi } from '@/lib/api';
import type { SprintResponse, AggregatedRiskResponse } from '@/lib/types';
import { RiskBadge, SprintStatusBadge } from '@/components/ui/Badges';
import { RiskGauge } from '@/components/ui/RiskGauge';
import { LoadingOverlay } from '@/components/ui/Spinner';
import { formatDate } from '@/lib/utils';
import { GitBranch, Plus, X } from 'lucide-react';

export default function ComparePage() {
  const [sprints, setSprints] = useState<SprintResponse[]>([]);
  const [selected, setSelected] = useState<number[]>([]);
  const [results, setResults] = useState<AggregatedRiskResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [comparing, setComparing] = useState(false);

  useEffect(() => {
    sprintsApi.list().then(setSprints).finally(() => setLoading(false));
  }, []);

  const addSprint = (id: number) => {
    if (selected.includes(id) || selected.length >= 5) return;
    setSelected(p => [...p, id]);
  };

  const removeSprint = (id: number) => {
    setSelected(p => p.filter(x => x !== id));
    setResults([]);
  };

  const compare = async () => {
    if (selected.length < 2) return;
    setComparing(true);
    try {
      const data = await reportingApi.compare(selected);
      setResults(data);
    } finally { setComparing(false); }
  };

  if (loading) return <LoadingOverlay />;

  return (
    <>
      <div className="topbar">
        <div className="topbar-title"><GitBranch size={18} /> Compare Sprints</div>
        <div className="topbar-actions">
          <button className="btn btn-primary btn-sm" onClick={compare} disabled={selected.length < 2 || comparing}>
            {comparing ? 'Comparing…' : 'Compare'}
          </button>
        </div>
      </div>

      <div className="page-body section-gap">
        {/* Sprint picker */}
        <div className="card">
          <div className="card-header"><span className="card-title">Select Sprints to Compare (up to 5)</span></div>
          <div className="card-body section-gap" style={{ gap: 12 }}>
            <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
              <select className="form-select" style={{ flex: 1 }} defaultValue="" onChange={e => { if (e.target.value) addSprint(Number(e.target.value)); e.target.value = ''; }}>
                <option value="">+ Add sprint to compare</option>
                {sprints.filter(s => !selected.includes(s.id)).map(s => <option key={s.id} value={s.id}>{s.name} ({s.status})</option>)}
              </select>
            </div>
            {selected.length > 0 && (
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {selected.map(id => {
                  const s = sprints.find(x => x.id === id);
                  return s ? (
                    <div key={id} style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 6, padding: '4px 10px' }}>
                      <span style={{ fontSize: 13 }}>{s.name}</span>
                      <SprintStatusBadge status={s.status} />
                      <button className="btn btn-ghost btn-icon" style={{ padding: 2 }} onClick={() => removeSprint(id)}><X size={12} /></button>
                    </div>
                  ) : null;
                })}
              </div>
            )}
            {selected.length < 2 && <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Add at least 2 sprints to compare</div>}
          </div>
        </div>

        {/* Comparison results */}
        {results.length > 0 && (
          <>
            <div style={{ display: 'grid', gridTemplateColumns: `repeat(${results.length}, 1fr)`, gap: 14 }}>
              {results.map(r => {
                const sprint = sprints.find(s => s.id === r.sprintId);
                return (
                  <div key={r.id} className="card">
                    <div className="card-header">
                      <div>
                        <div className="card-title">{sprint?.name ?? `Sprint #${r.sprintId}`}</div>
                        <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>{sprint ? `${formatDate(sprint.startDate)} → ${formatDate(sprint.endDate)}` : ''}</div>
                      </div>
                      <RiskBadge level={r.overallLevel} />
                    </div>
                    <div className="card-body" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12 }}>
                      <RiskGauge score={r.overallScore ?? 0} size={100} />
                      <div style={{ width: '100%' }}>
                        {[
                          { label: 'Over-Budget', score: r.overBudgetScore },
                          { label: 'Req. Changes', score: r.requirementChangeScore },
                          { label: 'Collaboration', score: r.communicationCollaborationScore },
                        ].map(({ label, score }) => {
                          const s = score ?? 0;
                          const color = s < 33 ? '#22c55e' : s < 66 ? '#f59e0b' : '#ef4444';
                          return (
                            <div key={label} style={{ marginBottom: 8 }}>
                              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--text-muted)', marginBottom: 3 }}>
                                <span>{label}</span><span>{Math.round(s)}</span>
                              </div>
                              <div className="progress-bar"><div className="progress-fill" style={{ width: `${s}%`, background: color }} /></div>
                            </div>
                          );
                        })}
                      </div>
                      <div style={{ fontSize: 12, color: 'var(--text-muted)', textAlign: 'center' }}>
                        {r.findings?.length ?? 0} findings · {r.degraded ? '⚠ Degraded' : '✓ Healthy'}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Summary table */}
            <div className="card">
              <div className="card-header"><span className="card-title">Comparison Summary</span></div>
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Sprint</th><th>Overall Score</th><th>Level</th><th>Over-Budget</th><th>Req. Change</th><th>Collaboration</th><th>Findings</th>
                    </tr>
                  </thead>
                  <tbody>
                    {results.map(r => (
                      <tr key={r.id}>
                        <td className="primary">{sprints.find(s => s.id === r.sprintId)?.name ?? `#${r.sprintId}`}</td>
                        <td style={{ fontWeight: 700, fontSize: 15 }}>{Math.round(r.overallScore ?? 0)}</td>
                        <td><RiskBadge level={r.overallLevel} /></td>
                        <td>{Math.round(r.overBudgetScore ?? 0)}</td>
                        <td>{Math.round(r.requirementChangeScore ?? 0)}</td>
                        <td>{Math.round(r.communicationCollaborationScore ?? 0)}</td>
                        <td>{r.findings?.length ?? 0}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </>
        )}

        {selected.length >= 2 && results.length === 0 && !comparing && (
          <div className="empty-state card">
            <div className="empty-state-icon"><GitBranch size={36} /></div>
            <div className="empty-state-title">Ready to compare</div>
            <div className="empty-state-desc">Click "Compare" to see a side-by-side risk analysis of the selected sprints.</div>
          </div>
        )}
      </div>
    </>
  );
}
