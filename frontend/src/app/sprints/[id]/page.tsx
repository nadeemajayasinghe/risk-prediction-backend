'use client';
import { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { sprintsApi, reportingApi, riskApi, ingestionApi } from '@/lib/api';
import type { SprintResponse, RiskSummaryResponse, RiskTrendPoint, SprintMetricRequest, UserStoryRequest, RequirementChangeRequest, ChangeType } from '@/lib/types';
import { SprintStatusBadge, RiskBadge } from '@/components/ui/Badges';
import { RiskGauge } from '@/components/ui/RiskGauge';
import { LoadingOverlay, Spinner } from '@/components/ui/Spinner';
import { formatDate, formatDateTime, riskScoreColor, severity } from '@/lib/utils';
import { useToast } from '@/context/ToastContext';
import { Edit, Zap, Plus, ChevronRight, BarChart2, AlertTriangle } from 'lucide-react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';

type Tab = 'overview' | 'metrics' | 'stories' | 'risk' | 'trends';

export default function SprintDetailPage() {
  const { id } = useParams<{ id: string }>();
  const sprintId = Number(id);
  const router = useRouter();
  const { toast } = useToast();

  const [sprint, setSprint] = useState<SprintResponse | null>(null);
  const [summary, setSummary] = useState<RiskSummaryResponse | null>(null);
  const [trend, setTrend] = useState<RiskTrendPoint[]>([]);
  const [tab, setTab] = useState<Tab>('overview');
  const [loading, setLoading] = useState(true);
  const [evaluating, setEvaluating] = useState(false);

  // Ingestion modals
  const [showMetricModal, setShowMetricModal] = useState(false);
  const [showStoryModal, setShowStoryModal] = useState(false);
  const [showChangeModal, setShowChangeModal] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [s, sum, tr] = await Promise.allSettled([
        sprintsApi.get(sprintId),
        reportingApi.summary(sprintId),
        reportingApi.trend(sprintId),
      ]);
      if (s.status === 'fulfilled') setSprint(s.value);
      if (sum.status === 'fulfilled') setSummary(sum.value);
      if (tr.status === 'fulfilled') setTrend(tr.value);
    } finally { setLoading(false); }
  }, [sprintId]);

  useEffect(() => { load(); }, [load]);

  const evaluate = async () => {
    setEvaluating(true);
    try {
      await riskApi.evaluate(sprintId);
      toast('Risk evaluation complete', 'success');
      await load();
    } catch { toast('Evaluation failed', 'error'); }
    finally { setEvaluating(false); }
  };

  if (loading) return <LoadingOverlay label="Loading sprint…" />;
  if (!sprint) return <div className="page-body" style={{ color: 'var(--text-muted)' }}>Sprint not found.</div>;

  const risk = summary?.latestAggregated;
  const trendData = trend.map(t => ({
    time: new Date(t.timestamp).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' }),
    Overall: Math.round(t.overallScore ?? 0),
    OverBudget: Math.round(t.overBudgetScore ?? 0),
    ReqChange: Math.round(t.requirementChangeScore ?? 0),
  }));

  return (
    <>
      <div className="topbar">
        <div className="topbar-title">
          <ChevronRight size={14} style={{ color: 'var(--text-muted)' }} />
          <Link href="/sprints" style={{ color: 'var(--text-muted)', textDecoration: 'none', fontSize: 13 }}>Sprints</Link>
          <ChevronRight size={14} style={{ color: 'var(--text-muted)' }} />
          {sprint.name}
        </div>
        <div className="topbar-actions">
          <button className="btn btn-primary btn-sm" onClick={evaluate} disabled={evaluating}>
            {evaluating ? <><Spinner size={12} /> Evaluating…</> : <><Zap size={14} /> Evaluate Risk</>}
          </button>
          <Link href={`/sprints/${id}/edit`} className="btn btn-secondary btn-sm"><Edit size={14} /> Edit</Link>
        </div>
      </div>

      <div className="page-body section-gap">
        {/* Header row */}
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', alignItems: 'flex-start' }}>
          <div className="card" style={{ flex: 1, minWidth: 300 }}>
            <div className="card-body">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
                <div>
                  <h1 style={{ fontSize: '1.2rem', marginBottom: 4 }}>{sprint.name}</h1>
                  {sprint.goal && <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>{sprint.goal}</p>}
                </div>
                <SprintStatusBadge status={sprint.status} />
              </div>
              <div className="form-grid-3" style={{ gap: 10 }}>
                {[
                  { label: 'Team', value: sprint.teamId ?? '—' },
                  { label: 'Start', value: formatDate(sprint.startDate) },
                  { label: 'End', value: formatDate(sprint.endDate) },
                  { label: 'Capacity', value: sprint.capacityPoints ? `${sprint.capacityPoints} pts` : '—' },
                  { label: 'Team Size', value: sprint.teamSize ?? '—' },
                  { label: 'Velocity', value: sprint.baseVelocity ?? '—' },
                ].map(({ label, value }) => (
                  <div key={label}>
                    <div className="stat-label">{label}</div>
                    <div style={{ fontWeight: 500, color: 'var(--text-primary)', fontSize: 13 }}>{String(value)}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {risk && (
            <div className="card" style={{ minWidth: 260 }}>
              <div className="card-header"><span className="card-title">Risk Overview</span><RiskBadge level={risk.overallLevel} /></div>
              <div className="card-body" style={{ display: 'flex', gap: 20, alignItems: 'center' }}>
                <RiskGauge score={risk.overallScore ?? 0} size={90} />
                <div style={{ flex: 1 }}>
                  {[
                    { label: 'Over-Budget', score: risk.overBudgetScore },
                    { label: 'Req. Changes', score: risk.requirementChangeScore },
                    { label: 'Collaboration', score: risk.communicationCollaborationScore },
                  ].map(({ label, score }) => {
                    const s = score ?? 0;
                    return (
                      <div key={label} style={{ marginBottom: 8 }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--text-muted)', marginBottom: 3 }}>
                          <span>{label}</span><span>{Math.round(s)}</span>
                        </div>
                        <div className="progress-bar"><div className="progress-fill" style={{ width: `${s}%`, background: riskScoreColor(s) }} /></div>
                      </div>
                    );
                  })}
                </div>
              </div>
              {risk.degraded && <div style={{ padding: '8px 16px', fontSize: 11, color: '#f87171', borderTop: '1px solid var(--border)' }}>⚠ Degraded mode – some models unavailable</div>}
            </div>
          )}
        </div>

        {/* Tabs */}
        <div className="card">
          <div className="tabs">
            {(['overview', 'metrics', 'stories', 'risk', 'trends'] as Tab[]).map(t => (
              <button key={t} className={`tab-btn ${tab === t ? 'active' : ''}`} onClick={() => setTab(t)}>
                {t.charAt(0).toUpperCase() + t.slice(1)}
              </button>
            ))}
          </div>

          {tab === 'overview' && (
            <div className="card-body section-gap">
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                <button className="btn btn-secondary btn-sm" onClick={() => setShowMetricModal(true)}><Plus size={13} /> Add Metric</button>
                <button className="btn btn-secondary btn-sm" onClick={() => setShowStoryModal(true)}><Plus size={13} /> Add Story</button>
                <button className="btn btn-secondary btn-sm" onClick={() => setShowChangeModal(true)}><Plus size={13} /> Add Req. Change</button>
              </div>
              <div className="stat-grid">
                {[
                  { label: 'Sprint ID', value: sprint.id },
                  { label: 'Team Type', value: sprint.teamType ?? '—' },
                  { label: 'Complexity', value: sprint.complexity ?? '—' },
                  { label: 'Capacity Hrs', value: sprint.sprintCapacityHours ?? '—' },
                  { label: 'Created', value: formatDateTime(sprint.createdAt) },
                  { label: 'Updated', value: formatDateTime(sprint.updatedAt) },
                ].map(({ label, value }) => (
                  <div key={label} className="stat-card">
                    <div className="stat-label">{label}</div>
                    <div style={{ fontWeight: 600, color: 'var(--text-primary)', marginTop: 4 }}>{String(value)}</div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {tab === 'risk' && risk && (
            <div className="card-body section-gap">
              {/* Findings */}
              {risk.findings?.length > 0 && (
                <div>
                  <h3 style={{ marginBottom: 10, display: 'flex', gap: 8, alignItems: 'center' }}>
                    <AlertTriangle size={15} /> Risk Findings ({risk.findings.length})
                  </h3>
                  <div className="section-gap" style={{ gap: 8 }}>
                    {risk.findings.map((f, i) => {
                      const { color, icon } = severity(f.severity);
                      return (
                        <div key={i} className="finding-card" style={{ borderLeftColor: color, borderLeftWidth: 3 }}>
                          <div className="finding-icon">{icon}</div>
                          <div className="finding-body">
                            <div className="finding-code">{f.code} · {f.severity}</div>
                            <div className="finding-reason">{f.reason}</div>
                            <div className="finding-suggestion">→ {f.suggestion}</div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* LLM Explanation */}
              {risk.communicationCollaborationLlmExplanation && (
                <div>
                  <h3 style={{ marginBottom: 8 }}>AI Collaboration Analysis</h3>
                  <div className="card" style={{ background: 'var(--bg-surface)', padding: 14 }}>
                    <p style={{ color: 'var(--text-secondary)', fontSize: 13, lineHeight: 1.7 }}>
                      {risk.communicationCollaborationLlmExplanation}
                    </p>
                  </div>
                </div>
              )}

              {/* Recommendations */}
              {risk.communicationCollaborationRecommendations?.length > 0 && (
                <div>
                  <h3 style={{ marginBottom: 8 }}>Recommendations</h3>
                  <ul style={{ paddingLeft: 20, display: 'flex', flexDirection: 'column', gap: 6 }}>
                    {risk.communicationCollaborationRecommendations.map((r, i) => (
                      <li key={i} style={{ color: 'var(--text-secondary)', fontSize: 13 }}>{r}</li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Feature Impacts */}
              {risk.overBudgetFeatureImpacts?.length > 0 && (
                <div>
                  <h3 style={{ marginBottom: 10 }}>Over-Budget Feature Impacts (SHAP)</h3>
                  {risk.overBudgetFeatureImpacts.slice(0, 8).map((f, i) => {
                    const pct = Math.min(Math.abs(f.riskContribution ?? 0), 100);
                    const color = (f.riskContribution ?? 0) > 0 ? '#ef4444' : '#22c55e';
                    return (
                      <div key={i} className="feature-bar-row">
                        <span className="feature-name" title={f.feature}>{f.feature}</span>
                        <div className="feature-bar-track"><div className="feature-bar-fill" style={{ width: `${pct}%`, background: color }} /></div>
                        <span className="feature-value">{f.riskContribution?.toFixed(1)}</span>
                      </div>
                    );
                  })}
                </div>
              )}

              {/* Combined explanation */}
              {risk.combinedExplanation && (
                <div>
                  <h3 style={{ marginBottom: 8 }}>Combined Explanation</h3>
                  <p style={{ color: 'var(--text-secondary)', fontSize: 13, lineHeight: 1.7 }}>{risk.combinedExplanation}</p>
                </div>
              )}
            </div>
          )}

          {tab === 'risk' && !risk && (
            <div className="empty-state">
              <div className="empty-state-icon"><BarChart2 size={40} /></div>
              <div className="empty-state-title">No Risk Evaluation Yet</div>
              <div className="empty-state-desc">Click "Evaluate Risk" above to run AI risk analysis on this sprint.</div>
            </div>
          )}

          {tab === 'metrics' && (
            <div className="card-body section-gap">
              <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                <button className="btn btn-primary btn-sm" onClick={() => setShowMetricModal(true)}><Plus size={13} /> Add Metric</button>
              </div>
              <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>
                Sprint metrics are used as inputs for AI risk models. Add metrics to enable accurate risk prediction.
              </div>
            </div>
          )}

          {tab === 'stories' && (
            <div className="card-body section-gap">
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                <button className="btn btn-primary btn-sm" onClick={() => setShowStoryModal(true)}><Plus size={13} /> Add Story</button>
                <button className="btn btn-secondary btn-sm" onClick={() => setShowChangeModal(true)}><Plus size={13} /> Add Req. Change</button>
              </div>
              <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>
                User stories and requirement changes feed into the AI risk prediction models.
              </div>
            </div>
          )}

          {tab === 'trends' && (
            <div className="card-body">
              {trendData.length === 0 ? (
                <div className="empty-state">
                  <div className="empty-state-icon">📈</div>
                  <div className="empty-state-title">No trend data</div>
                  <div className="empty-state-desc">Run multiple risk evaluations to build a trend chart.</div>
                </div>
              ) : (
                <div>
                  <h3 style={{ marginBottom: 16 }}>Risk Score Trend</h3>
                  <ResponsiveContainer width="100%" height={300}>
                    <LineChart data={trendData}>
                      <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
                      <XAxis dataKey="time" tick={{ fill: 'var(--text-muted)', fontSize: 11 }} />
                      <YAxis domain={[0, 100]} tick={{ fill: 'var(--text-muted)', fontSize: 11 }} />
                      <Tooltip contentStyle={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 8 }} labelStyle={{ color: 'var(--text-primary)' }} />
                      <Legend />
                      <Line type="monotone" dataKey="Overall" stroke="#6366f1" strokeWidth={2} dot={false} />
                      <Line type="monotone" dataKey="OverBudget" stroke="#ef4444" strokeWidth={2} dot={false} />
                      <Line type="monotone" dataKey="ReqChange" stroke="#f59e0b" strokeWidth={2} dot={false} />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              )}
            </div>
          )}
        </div>

        {/* History */}
        {summary?.latestPerModel && summary.latestPerModel.length > 0 && (
          <div className="card">
            <div className="card-header"><span className="card-title">Latest Model Predictions</span></div>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr><th>Model</th><th>Risk Score</th><th>Level</th><th>Probability</th><th>Explanation</th><th>Time</th></tr>
                </thead>
                <tbody>
                  {summary.latestPerModel.map(p => (
                    <tr key={p.id}>
                      <td className="primary">{p.modelType.replace(/_/g, ' ')}</td>
                      <td><span style={{ fontWeight: 600, color: riskScoreColor(p.riskScore ?? 0) }}>{Math.round(p.riskScore ?? 0)}</span></td>
                      <td><RiskBadge level={p.riskLevel} /></td>
                      <td>{((p.probability ?? 0) * 100).toFixed(1)}%</td>
                      <td style={{ maxWidth: 300 }} className="truncate">{p.explanation ?? '—'}</td>
                      <td>{formatDateTime(p.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      {showMetricModal && <MetricModal sprintId={sprintId} onClose={() => setShowMetricModal(false)} onSaved={() => { setShowMetricModal(false); load(); toast('Metric added', 'success'); }} />}
      {showStoryModal && <StoryModal sprintId={sprintId} onClose={() => setShowStoryModal(false)} onSaved={() => { setShowStoryModal(false); load(); toast('Story added', 'success'); }} />}
      {showChangeModal && <ChangeModal sprintId={sprintId} onClose={() => setShowChangeModal(false)} onSaved={() => { setShowChangeModal(false); load(); toast('Requirement change added', 'success'); }} />}
    </>
  );
}

/* ── Ingestion Modals ─────────────────────────────────────────────────────── */

function MetricModal({ sprintId, onClose, onSaved }: { sprintId: number; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<Partial<SprintMetricRequest>>({});
  const [saving, setSaving] = useState(false);
  const set = (k: keyof SprintMetricRequest, v: unknown) => setForm(p => ({ ...p, [k]: v }));
  const submit = async (e: React.FormEvent) => {
    e.preventDefault(); setSaving(true);
    try { await ingestionApi.addMetric(sprintId, form as SprintMetricRequest); onSaved(); }
    catch { setSaving(false); }
  };
  const num = (k: keyof SprintMetricRequest, label: string, step = '1') => (
    <div className="form-group">
      <label className="form-label">{label}</label>
      <input type="number" step={step} min={0} className="form-input" value={(form as Record<string, unknown>)[k] as number ?? ''} onChange={e => set(k, Number(e.target.value))} />
    </div>
  );
  return (
    <div className="modal-overlay">
      <div className="modal">
        <div className="modal-header"><h2>Add Sprint Metric</h2><button className="btn btn-ghost btn-icon" onClick={onClose}>✕</button></div>
        <form onSubmit={submit}>
          <div className="modal-body">
            <div className="form-grid-2">
              {num('plannedPoints', 'Planned Points')}
              {num('completedPoints', 'Completed Points')}
              {num('effortDeviation', 'Effort Deviation', '0.01')}
              {num('bugsCount', 'Bugs Count')}
              {num('scopeChangesCount', 'Scope Changes')}
              {num('velocity', 'Velocity', '0.1')}
              {num('reworkScore', 'Rework Score', '0.1')}
              {num('blockedTasks', 'Blocked Tasks')}
              {num('reopenedTasks', 'Reopened Tasks')}
              {num('fatigue', 'Fatigue', '0.1')}
              {num('avgResponseTimeHours', 'Avg Response Time (hrs)')}
              {num('inactiveDays', 'Inactive Days')}
            </div>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Saving…' : 'Add Metric'}</button>
          </div>
        </form>
      </div>
    </div>
  );
}

function StoryModal({ sprintId, onClose, onSaved }: { sprintId: number; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<Partial<UserStoryRequest>>({});
  const [saving, setSaving] = useState(false);
  const set = (k: keyof UserStoryRequest, v: unknown) => setForm(p => ({ ...p, [k]: v }));
  const submit = async (e: React.FormEvent) => {
    e.preventDefault(); setSaving(true);
    try { await ingestionApi.addStory(sprintId, form as UserStoryRequest); onSaved(); }
    catch { setSaving(false); }
  };
  return (
    <div className="modal-overlay">
      <div className="modal">
        <div className="modal-header"><h2>Add User Story</h2><button className="btn btn-ghost btn-icon" onClick={onClose}>✕</button></div>
        <form onSubmit={submit}>
          <div className="modal-body section-gap" style={{ gap: 12 }}>
            <div className="form-group"><label className="form-label">External Key</label><input className="form-input" placeholder="PROJ-123" value={form.externalKey ?? ''} onChange={e => set('externalKey', e.target.value)} /></div>
            <div className="form-group"><label className="form-label">Title *</label><input required className="form-input" value={form.title ?? ''} onChange={e => set('title', e.target.value)} /></div>
            <div className="form-group"><label className="form-label">Description</label><textarea className="form-textarea" value={form.description ?? ''} onChange={e => set('description', e.target.value)} /></div>
            <div className="form-grid-3">
              <div className="form-group"><label className="form-label">Story Points</label><input type="number" min={0} className="form-input" value={form.storyPoints ?? ''} onChange={e => set('storyPoints', Number(e.target.value))} /></div>
              <div className="form-group"><label className="form-label">Priority</label><select className="form-select" value={form.priority ?? ''} onChange={e => set('priority', e.target.value)}><option value="">—</option>{['HIGH', 'MEDIUM', 'LOW'].map(p => <option key={p} value={p}>{p}</option>)}</select></div>
              <div className="form-group"><label className="form-label">Status</label><select className="form-select" value={form.status ?? ''} onChange={e => set('status', e.target.value)}><option value="">—</option>{['TODO', 'IN_PROGRESS', 'DONE'].map(s => <option key={s} value={s}>{s}</option>)}</select></div>
            </div>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Saving…' : 'Add Story'}</button>
          </div>
        </form>
      </div>
    </div>
  );
}

const CHANGE_TYPES: ChangeType[] = ['SCOPE_ADDED', 'SCOPE_REMOVED', 'ACCEPTANCE_CRITERIA_CHANGED', 'PRIORITY_CHANGED', 'DESCRIPTION_UPDATED', 'OTHER'];

function ChangeModal({ sprintId, onClose, onSaved }: { sprintId: number; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<Partial<RequirementChangeRequest>>({ changeType: 'OTHER' });
  const [saving, setSaving] = useState(false);
  const set = (k: keyof RequirementChangeRequest, v: unknown) => setForm(p => ({ ...p, [k]: v }));
  const submit = async (e: React.FormEvent) => {
    e.preventDefault(); setSaving(true);
    try { await ingestionApi.addRequirementChange(sprintId, form as RequirementChangeRequest); onSaved(); }
    catch { setSaving(false); }
  };
  return (
    <div className="modal-overlay">
      <div className="modal">
        <div className="modal-header"><h2>Add Requirement Change</h2><button className="btn btn-ghost btn-icon" onClick={onClose}>✕</button></div>
        <form onSubmit={submit}>
          <div className="modal-body section-gap" style={{ gap: 12 }}>
            <div className="form-group"><label className="form-label">Change Type *</label><select required className="form-select" value={form.changeType} onChange={e => set('changeType', e.target.value)}>{CHANGE_TYPES.map(c => <option key={c} value={c}>{c.replace(/_/g, ' ')}</option>)}</select></div>
            <div className="form-group"><label className="form-label">Story ID (optional)</label><input type="number" className="form-input" value={form.storyId ?? ''} onChange={e => set('storyId', Number(e.target.value))} /></div>
            <div className="form-group"><label className="form-label">Description</label><textarea className="form-textarea" value={form.description ?? ''} onChange={e => set('description', e.target.value)} /></div>
            <div className="form-group"><label className="form-label">Requested By</label><input className="form-input" value={form.requestedBy ?? ''} onChange={e => set('requestedBy', e.target.value)} /></div>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Saving…' : 'Add Change'}</button>
          </div>
        </form>
      </div>
    </div>
  );
}
