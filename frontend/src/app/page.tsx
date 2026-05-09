'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { sprintsApi, reportingApi } from '@/lib/api';
import type { SprintResponse, RiskSummaryResponse } from '@/lib/types';
import { SprintStatusBadge, RiskBadge } from '@/components/ui/Badges';
import { RiskGauge } from '@/components/ui/RiskGauge';
import { LoadingOverlay } from '@/components/ui/Spinner';
import { formatDate } from '@/lib/utils';
import { LayoutDashboard, Zap, AlertTriangle, CheckCircle, Clock, TrendingUp } from 'lucide-react';

export default function DashboardPage() {
  const [sprints, setSprints] = useState<SprintResponse[]>([]);
  const [summaries, setSummaries] = useState<Record<number, RiskSummaryResponse>>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    sprintsApi.list()
      .then(async (data) => {
        setSprints(data);
        // Fetch summaries for active sprints
        const active = data.filter(s => s.status === 'ACTIVE');
        const results = await Promise.allSettled(
          active.map(s => reportingApi.summary(s.id))
        );
        const map: Record<number, RiskSummaryResponse> = {};
        results.forEach((r, i) => {
          if (r.status === 'fulfilled') map[active[i].id] = r.value;
        });
        setSummaries(map);
      })
      .finally(() => setLoading(false));
  }, []);

  const activeSprints = sprints.filter(s => s.status === 'ACTIVE');
  const plannedSprints = sprints.filter(s => s.status === 'PLANNED');
  const completedSprints = sprints.filter(s => s.status === 'COMPLETED');

  const highRisk = Object.values(summaries).filter(
    s => s.latestAggregated?.overallLevel === 'HIGH'
  ).length;

  if (loading) return <LoadingOverlay label="Loading dashboard…" />;

  return (
    <>
      <div className="topbar">
        <div className="topbar-title">
          <LayoutDashboard size={18} />
          Dashboard
        </div>
        <div className="topbar-actions">
          <Link href="/sprints/new" className="btn btn-primary btn-sm">
            + New Sprint
          </Link>
        </div>
      </div>

      <div className="page-body section-gap">
        {/* Overview Stats */}
        <div className="stat-grid">
          <StatCard icon={<Clock size={18} color="#60a5fa" />} label="Active Sprints" value={activeSprints.length} sub="Currently running" />
          <StatCard icon={<TrendingUp size={18} color="#a78bfa" />} label="Planned Sprints" value={plannedSprints.length} sub="Upcoming" />
          <StatCard icon={<CheckCircle size={18} color="#4ade80" />} label="Completed" value={completedSprints.length} sub="Finished sprints" />
          <StatCard icon={<AlertTriangle size={18} color="#f87171" />} label="High Risk" value={highRisk} sub="Active sprints at risk" color="#f87171" />
          <StatCard icon={<Zap size={18} color="#fbbf24" />} label="Total Sprints" value={sprints.length} sub="All time" />
        </div>

        {/* Active Sprints with Risk */}
        <div>
          <h2 style={{ marginBottom: 14, color: 'var(--text-primary)' }}>Active Sprints</h2>
          {activeSprints.length === 0 ? (
            <div className="card">
              <div className="empty-state">
                <div className="empty-state-icon">🏃</div>
                <div className="empty-state-title">No active sprints</div>
                <div className="empty-state-desc">Create a sprint and mark it as Active to see it here.</div>
                <Link href="/sprints/new" className="btn btn-primary" style={{ marginTop: 8 }}>Create Sprint</Link>
              </div>
            </div>
          ) : (
            <div className="grid-2" style={{ gap: 14 }}>
              {activeSprints.map(sprint => {
                const summary = summaries[sprint.id];
                const risk = summary?.latestAggregated;
                return (
                  <Link key={sprint.id} href={`/sprints/${sprint.id}`} style={{ textDecoration: 'none' }}>
                    <div className="card" style={{ cursor: 'pointer', transition: 'border-color .15s' }}
                      onMouseEnter={e => (e.currentTarget.style.borderColor = 'var(--brand)')}
                      onMouseLeave={e => (e.currentTarget.style.borderColor = 'var(--border)')}>
                      <div className="card-header">
                        <div>
                          <div className="card-title">{sprint.name}</div>
                          <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>
                            {formatDate(sprint.startDate)} → {formatDate(sprint.endDate)}
                          </div>
                        </div>
                        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 6 }}>
                          <SprintStatusBadge status={sprint.status} />
                          {risk && <RiskBadge level={risk.overallLevel} />}
                        </div>
                      </div>
                      <div className="card-body" style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
                        {risk ? (
                          <>
                            <RiskGauge score={risk.overallScore ?? 0} size={80} />
                            <div style={{ flex: 1 }}>
                              <div className="stat-label" style={{ marginBottom: 10 }}>Risk Scores</div>
                              <ScoreBar label="Over-Budget" score={risk.overBudgetScore} />
                              <ScoreBar label="Req. Changes" score={risk.requirementChangeScore} />
                              <ScoreBar label="Collaboration" score={risk.communicationCollaborationScore} />
                            </div>
                          </>
                        ) : (
                          <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>
                            No risk evaluation yet. <Link href={`/sprints/${sprint.id}`} style={{ color: 'var(--brand-light)' }}>Run evaluation →</Link>
                          </div>
                        )}
                      </div>
                    </div>
                  </Link>
                );
              })}
            </div>
          )}
        </div>

        {/* Recent Sprints Table */}
        <div className="card">
          <div className="card-header">
            <span className="card-title">All Sprints</span>
            <Link href="/sprints" className="btn btn-secondary btn-sm">View All</Link>
          </div>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Name</th>
                  <th>Team</th>
                  <th>Start</th>
                  <th>End</th>
                  <th>Status</th>
                  <th>Capacity</th>
                </tr>
              </thead>
              <tbody>
                {sprints.slice(0, 8).map(s => (
                  <tr key={s.id}>
                    <td className="primary">{s.id}</td>
                    <td>
                      <Link href={`/sprints/${s.id}`} style={{ color: 'var(--brand-light)', textDecoration: 'none' }}>
                        {s.name}
                      </Link>
                    </td>
                    <td>{s.teamId ?? '—'}</td>
                    <td>{formatDate(s.startDate)}</td>
                    <td>{formatDate(s.endDate)}</td>
                    <td><SprintStatusBadge status={s.status} /></td>
                    <td>{s.capacityPoints ?? '—'} pts</td>
                  </tr>
                ))}
                {sprints.length === 0 && (
                  <tr><td colSpan={7} className="text-center" style={{ color: 'var(--text-muted)', padding: 40 }}>No sprints found</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </>
  );
}

function StatCard({ icon, label, value, sub, color }: {
  icon: React.ReactNode;
  label: string;
  value: number;
  sub: string;
  color?: string;
}) {
  return (
    <div className="stat-card">
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
        <div className="stat-label">{label}</div>
        {icon}
      </div>
      <div className="stat-value" style={{ color: color ?? 'var(--text-primary)' }}>{value}</div>
      <div className="stat-sub">{sub}</div>
    </div>
  );
}

function ScoreBar({ label, score }: { label: string; score: number | null | undefined }) {
  const s = score ?? 0;
  const color = s < 33 ? '#16a34a' : s < 66 ? '#d97706' : '#dc2626';
  return (
    <div style={{ marginBottom: 6 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--text-muted)', marginBottom: 3 }}>
        <span>{label}</span><span>{Math.round(s)}</span>
      </div>
      <div className="progress-bar">
        <div className="progress-fill" style={{ width: `${s}%`, background: color }} />
      </div>
    </div>
  );
}
