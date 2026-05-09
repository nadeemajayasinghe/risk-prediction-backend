'use client';
import { useEffect, useState } from 'react';
import { sprintsApi, reportingApi } from '@/lib/api';
import type { SprintResponse, RiskSummaryResponse, RiskPredictionResponse } from '@/lib/types';
import { RiskBadge } from '@/components/ui/Badges';
import { LoadingOverlay } from '@/components/ui/Spinner';
import { formatDateTime, riskScoreColor } from '@/lib/utils';
import { BarChart2 } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts';

export default function ReportsPage() {
  const [sprints, setSprints] = useState<SprintResponse[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [summary, setSummary] = useState<RiskSummaryResponse | null>(null);
  const [history, setHistory] = useState<RiskPredictionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);

  useEffect(() => {
    sprintsApi.list().then(data => {
      setSprints(data);
      const active = data.find(s => s.status === 'ACTIVE');
      if (active) setSelected(active.id);
    }).finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!selected) return;
    setLoadingDetail(true);
    Promise.allSettled([
      reportingApi.summary(selected),
      reportingApi.history(selected),
    ]).then(([s, h]) => {
      if (s.status === 'fulfilled') setSummary(s.value);
      if (h.status === 'fulfilled') setHistory(h.value);
    }).finally(() => setLoadingDetail(false));
  }, [selected]);

  if (loading) return <LoadingOverlay />;

  const barData = summary?.latestAggregated ? [
    { name: 'Over-Budget', score: Math.round(summary.latestAggregated.overBudgetScore ?? 0) },
    { name: 'Req. Changes', score: Math.round(summary.latestAggregated.requirementChangeScore ?? 0) },
    { name: 'Collaboration', score: Math.round(summary.latestAggregated.communicationCollaborationScore ?? 0) },
  ] : [];

  return (
    <>
      <div className="topbar">
        <div className="topbar-title"><BarChart2 size={18} /> Reports</div>
        <div className="topbar-actions">
          <select className="form-select" style={{ minWidth: 220 }} value={selected ?? ''} onChange={e => setSelected(Number(e.target.value))}>
            <option value="">— Select Sprint —</option>
            {sprints.map(s => <option key={s.id} value={s.id}>{s.name} ({s.status})</option>)}
          </select>
        </div>
      </div>

      <div className="page-body section-gap">
        {loadingDetail ? <LoadingOverlay /> : !selected ? (
          <div className="empty-state card"><div className="empty-state-icon">📊</div><div className="empty-state-title">Select a sprint to view its report</div></div>
        ) : !summary?.latestAggregated ? (
          <div className="empty-state card"><div className="empty-state-icon">🔍</div><div className="empty-state-title">No risk evaluation found for this sprint</div><div className="empty-state-desc">Run a risk evaluation first from the Risk Analysis page.</div></div>
        ) : (
          <>
            {/* Risk score bar chart */}
            <div className="card">
              <div className="card-header">
                <span className="card-title">Risk Score Breakdown</span>
                <RiskBadge level={summary.latestAggregated.overallLevel} />
              </div>
              <div className="card-body">
                <ResponsiveContainer width="100%" height={220}>
                  <BarChart data={barData} margin={{ top: 4, right: 10, left: 0, bottom: 4 }}>
                    <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
                    <XAxis dataKey="name" tick={{ fill: 'var(--text-muted)', fontSize: 12 }} />
                    <YAxis domain={[0, 100]} tick={{ fill: 'var(--text-muted)', fontSize: 12 }} />
                    <Tooltip contentStyle={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 8 }} />
                    <Bar dataKey="score" radius={[4, 4, 0, 0]}>
                      {barData.map((entry, i) => (
                        <Cell key={i} fill={riskScoreColor(entry.score)} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>

            {/* Prediction history */}
            <div className="card">
              <div className="card-header"><span className="card-title">Prediction History ({history.length})</span></div>
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr><th>Model</th><th>Score</th><th>Level</th><th>Probability</th><th>Explanation</th><th>Evaluated At</th></tr>
                  </thead>
                  <tbody>
                    {history.map(p => (
                      <tr key={p.id}>
                        <td className="primary" style={{ whiteSpace: 'nowrap' }}>{p.modelType.replace(/_/g, ' ')}</td>
                        <td><span style={{ fontWeight: 700, color: riskScoreColor(p.riskScore ?? 0) }}>{Math.round(p.riskScore ?? 0)}</span></td>
                        <td><RiskBadge level={p.riskLevel} /></td>
                        <td>{((p.probability ?? 0) * 100).toFixed(1)}%</td>
                        <td style={{ maxWidth: 300 }} className="truncate">{p.explanation ?? '—'}</td>
                        <td style={{ whiteSpace: 'nowrap' }}>{formatDateTime(p.createdAt)}</td>
                      </tr>
                    ))}
                    {history.length === 0 && <tr><td colSpan={6} className="text-center" style={{ padding: 30, color: 'var(--text-muted)' }}>No history</td></tr>}
                  </tbody>
                </table>
              </div>
            </div>
          </>
        )}
      </div>
    </>
  );
}
