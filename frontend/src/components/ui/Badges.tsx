'use client';
import { RiskLevel } from '@/lib/types';
import { riskLevelColor } from '@/lib/utils';

export function RiskBadge({ level }: { level: RiskLevel | null | undefined }) {
  const { badge } = riskLevelColor(level);
  return <span className={`badge ${badge}`}>{level ?? 'UNKNOWN'}</span>;
}

export function SprintStatusBadge({ status }: { status: string }) {
  const cls = {
    PLANNED: 'badge-planned',
    ACTIVE: 'badge-active',
    COMPLETED: 'badge-completed',
    CANCELLED: 'badge-cancelled',
  }[status] ?? 'badge-unknown';
  return <span className={`badge ${cls}`}>{status}</span>;
}
