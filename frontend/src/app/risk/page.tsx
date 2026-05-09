'use client';
import { useEffect, useState } from 'react';
import { sprintsApi, reportingApi, riskApi } from '@/lib/api';
import type { SprintResponse, AggregatedRiskResponse } from '@/lib/types';
import { RiskBadge, SprintStatusBadge } from '@/components/ui/Badges';
import { RiskGauge } from '@/components/ui/RiskGauge';
import { LoadingOverlay, Spinner } from '@/components/ui/Spinner';
import { riskScoreColor, formatDate } from '@/lib/utils';
import { useToast } from '@/context/ToastContext';
import { Zap } from 'lucide-react';

export default function RiskPage() {
  const [sprints, setSprints] = useState<SprintResponse[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [result, setResult] = useState<AggregatedRiskResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [evaluating, setEvaluating] = useState(false);
  const { toast } = useToast();

  useEffect(() => {
    sprintsApi.list().then(data => {
      setSprints(data);
      const active = data.find(s => s.status === 'ACTIVE');
      if (active) setSelected(active.id);
    }).finally(() => setLoading(false));
  }, []);

  const evaluate = async () => {
    if (!selected) return;
    setEvaluating(true);
    try {
      const r = await riskApi.evaluate(selected);
      setResult(r);
      toast('Risk evaluation complete', 'success');
    } catch { toast('Evaluation failed', 'error'); }
    finally { setEvaluating(false); }
  };

  useEffect(() => {
    if (!selected) return;
    reportingApi.summary(selected).then(s => setResult(s.latestAggregated)).catch(() => setResult(null));
  }, [selected]);

  if (loading) return <LoadingOverlay label="Loading sprints…" />;

  return (
    <>
      <div className="topbar">
        <div className="topbar-title"><Zap size={18} />Risk Analysis</div>
        <div className="topbar-actions">
          <select className="form-select" style={{ minWidth: 200 }} value={selected ?? ''} onChange={e => setSelected(Number(e.target.value))}>
            <option value="">— Select Sprint —</option>
            {sprints.map(s => <option key={s.id} value={s.id}>{s.name} ({s.status})</option>)}
          </select>
          <button className="btn btn-primary btn-sm" onClick={evaluate} disabled={!selected || evaluating}>
            {evaluating ? <><Spinner size={12} /> Evaluating…</> : <><Zap size={14} /> Run Evaluation</>}
          </button>
        </div>
      </div>

      <div className="page-body section-gap">
        {selected && sprints.find(s => s.id === selected) && (
          <SprintHeader sprint={sprints.find(s => s.id === selected)!} />
        )}

        {result ? (
          <div className="section-gap">
            <div className="grid-4">
              <div className="card">
                <div className="card-header"><span className="card-title">Overall Risk</span><RiskBadge level={result.overallLevel} /></div>
                <div className="card-body" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
                  <RiskGauge score={result.overallScore ?? 0} size={120} />
                  <div style={{ fontSize: 13, color: 'var(--text-muted)', textAlign: 'center' }}>Score: {Math.round(result.overallScore ?? 0)}/100</div>
                </div>
              </div>
              <div className="card">
                <div className="card-header"><span className="card-title">Over-Budget Risk</span></div>
                <div className="card-body" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
                  <RiskGauge score={result.overBudgetScore ?? 0} size={120} />
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Baseline: {Math.round(result.overBudgetBaselineRiskScore ?? 0)}</div>
                </div>
              </div>
              <div className="card">
                <div className="card-header"><span className="card-title">Req. Change Risk</span></div>
                <div className="card-body" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
                  <RiskGauge score={result.requirementChangeScore ?? 0} size={120} />
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Baseline: {Math.round(result.requirementChangeBaselineRiskScore ?? 0)}</div>
                </div>
              </div>
              <div className="card">
                <div className="card-header"><span className="card-title">Collaboration Risk</span></div>
                <div className="card-body" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
                  <RiskGauge score={result.communicationCollaborationScore ?? 0} size={120} />
                  <div style={{ fontSize: 12, color: 'var(--text-muted)', textAlign: 'center' }}>AI Analysis</div>
                </div>
              </div>
            </div>

            {result.findings?.length > 0 && (
              <div className="card">
                <div className="card-header"><span className="card-title">⚠ Risk Findings ({result.findings.length})</span></div>
                <div className="card-body section-gap" style={{ gap: 8 }}>
                  {result.findings.map((f, i) => (
                    <div key={i} className="finding-card" style={{ borderLeft: `3px solid ${f.severity === 'CRITICAL' ? '#ef4444' : f.severity === 'WARNING' ? '#f59e0b' : '#3b82f6'}` }}>
                      <div className="finding-body">
                        <div className="finding-code">{f.code} · {f.severity}</div>
                        <div className="finding-reason">{f.reason}</div>
                        <div className="finding-suggestion">→ {f.suggestion}</div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className="grid-2">
              {result.overBudgetFeatureImpacts?.length > 0 && (
                <div className="card">
                  <div className="card-header"><span className="card-title">Over-Budget Feature Impact (SHAP)</span></div>
                  <div className="card-body">
                    {result.overBudgetFeatureImpacts.slice(0, 8).map((f, i) => {
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
                </div>
              )}

              {result.requirementChangeFeatureImpacts?.length > 0 && (
                <div className="card">
                  <div className="card-header"><span className="card-title">Req. Change Feature Impact (SHAP)</span></div>
                  <div className="card-body">
                    {result.requirementChangeFeatureImpacts.slice(0, 8).map((f, i) => {
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
                </div>
              )}
            </div>

            {result.communicationCollaborationLlmExplanation && (
              <div className="card">
                <div className="card-header"><span className="card-title">AI Collaboration Analysis</span><span className="badge badge-medium">LLM</span></div>
                <div className="card-body">
                  <p style={{ color: 'var(--text-secondary)', fontSize: 13, lineHeight: 1.8 }}>{result.communicationCollaborationLlmExplanation}</p>
                  {result.communicationCollaborationRecommendations?.length > 0 && (
                    <div style={{ marginTop: 14 }}>
                      <div style={{ fontWeight: 600, fontSize: 12, color: 'var(--text-muted)', marginBottom: 8 }}>RECOMMENDATIONS</div>
                      <ul style={{ paddingLeft: 18, display: 'flex', flexDirection: 'column', gap: 6 }}>
                        {result.communicationCollaborationRecommendations.map((r, i) => (
                          <li key={i} style={{ color: 'var(--text-secondary)', fontSize: 13 }}>{r}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              </div>
            )}

            <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
              Evaluation ID: {result.evaluationId} · {result.degraded ? '⚠ Degraded mode' : '✓ All models healthy'}
            </div>
          </div>
        ) : selected ? (
          <div className="empty-state card">
            <div className="empty-state-icon"><Zap size={40} /></div>
            <div className="empty-state-title">No risk evaluation yet</div>
            <div className="empty-state-desc">Click "Run Evaluation" to perform AI risk analysis for this sprint.</div>
          </div>
        ) : (
          <div className="empty-state card">
            <div className="empty-state-icon">📋</div>
            <div className="empty-state-title">Select a sprint</div>
            <div className="empty-state-desc">Choose a sprint from the dropdown to evaluate its risk.</div>
          </div>
        )}
      </div>
    </>
  );
}

function SprintHeader({ sprint }: { sprint: SprintResponse }) {
  return (
    <div className="card">
      <div className="card-body" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h2>{sprint.name}</h2>
          {sprint.goal && <p style={{ color: 'var(--text-muted)', fontSize: 13, marginTop: 2 }}>{sprint.goal}</p>}
        </div>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <SprintStatusBadge status={sprint.status} />
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{formatDate(sprint.startDate)} → {formatDate(sprint.endDate)}</span>
          {sprint.capacityPoints && <span className="chip">{sprint.capacityPoints} pts</span>}
        </div>
      </div>
    </div>
  );
}
