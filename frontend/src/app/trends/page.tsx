'use client';
import { useEffect, useState } from 'react';
import { sprintsApi, reportingApi } from '@/lib/api';
import type { SprintResponse, RiskTrendPoint } from '@/lib/types';
import { LoadingOverlay } from '@/components/ui/Spinner';
import { Activity } from 'lucide-react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend, ReferenceLine } from 'recharts';

export default function TrendsPage() {
  const [sprints, setSprints] = useState<SprintResponse[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [trend, setTrend] = useState<RiskTrendPoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingTrend, setLoadingTrend] = useState(false);

  useEffect(() => {
    sprintsApi.list().then(data => {
      setSprints(data);
      const active = data.find(s => s.status === 'ACTIVE');
      if (active) setSelected(active.id);
    }).finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!selected) return;
    setLoadingTrend(true);
    reportingApi.trend(selected).then(setTrend).catch(() => setTrend([])).finally(() => setLoadingTrend(false));
  }, [selected]);

  if (loading) return <LoadingOverlay />;

  const chartData = trend.map(t => ({
    time: new Date(t.timestamp).toLocaleString('en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }),
    Overall: Math.round(t.overallScore ?? 0),
    OverBudget: Math.round(t.overBudgetScore ?? 0),
    ReqChange: Math.round(t.requirementChangeScore ?? 0),
    level: t.overallLevel,
  }));

  return (
    <>
      <div className="topbar">
        <div className="topbar-title"><Activity size={18} /> Risk Trends</div>
        <div className="topbar-actions">
          <select className="form-select" style={{ minWidth: 220 }} value={selected ?? ''} onChange={e => setSelected(Number(e.target.value))}>
            <option value="">— Select Sprint —</option>
            {sprints.map(s => <option key={s.id} value={s.id}>{s.name} ({s.status})</option>)}
          </select>
        </div>
      </div>

      <div className="page-body section-gap">
        {loadingTrend ? <LoadingOverlay label="Loading trend data…" /> : !selected ? (
          <div className="empty-state card"><div className="empty-state-icon">📈</div><div className="empty-state-title">Select a sprint to view trends</div></div>
        ) : chartData.length === 0 ? (
          <div className="empty-state card"><div className="empty-state-icon">📈</div><div className="empty-state-title">No trend data</div><div className="empty-state-desc">Run at least two risk evaluations on this sprint to see a trend.</div></div>
        ) : (
          <>
            <div className="card">
              <div className="card-header"><span className="card-title">Overall Risk Trend</span><span className="chip">{chartData.length} evaluations</span></div>
              <div className="card-body">
                <ResponsiveContainer width="100%" height={300}>
                  <LineChart data={chartData}>
                    <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
                    <XAxis dataKey="time" tick={{ fill: 'var(--text-muted)', fontSize: 10 }} />
                    <YAxis domain={[0, 100]} tick={{ fill: 'var(--text-muted)', fontSize: 11 }} />
                    <Tooltip contentStyle={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 8 }} labelStyle={{ color: 'var(--text-primary)', fontSize: 12 }} />
                    <Legend />
                    <ReferenceLine y={33} stroke="#22c55e" strokeDasharray="4 4" label={{ value: 'Low', fill: '#22c55e', fontSize: 10 }} />
                    <ReferenceLine y={66} stroke="#f59e0b" strokeDasharray="4 4" label={{ value: 'Medium', fill: '#f59e0b', fontSize: 10 }} />
                    <Line type="monotone" dataKey="Overall" stroke="#6366f1" strokeWidth={2.5} dot={{ r: 4, fill: '#6366f1' }} />
                    <Line type="monotone" dataKey="OverBudget" stroke="#ef4444" strokeWidth={2} dot={false} strokeDasharray="4 4" />
                    <Line type="monotone" dataKey="ReqChange" stroke="#f59e0b" strokeWidth={2} dot={false} strokeDasharray="4 4" />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </div>

            <div className="card">
              <div className="card-header"><span className="card-title">Trend Data Table</span></div>
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr><th>Time</th><th>Overall</th><th>Over-Budget</th><th>Req. Change</th><th>Level</th></tr>
                  </thead>
                  <tbody>
                    {chartData.map((row, i) => (
                      <tr key={i}>
                        <td>{row.time}</td>
                        <td className="primary">{row.Overall}</td>
                        <td>{row.OverBudget}</td>
                        <td>{row.ReqChange}</td>
                        <td><span className={`badge badge-${row.level?.toLowerCase()}`}>{row.level}</span></td>
                      </tr>
                    ))}
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
