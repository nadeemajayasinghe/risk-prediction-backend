import type { RiskLevel } from './types';
import { clsx, type ClassValue } from 'clsx';

export function cn(...inputs: ClassValue[]) {
  return clsx(inputs);
}

export function riskLevelColor(level: RiskLevel | null | undefined) {
  switch (level) {
    case 'LOW': return { bg: '#16a34a', text: '#dcfce7', badge: 'badge-low' };
    case 'MEDIUM': return { bg: '#d97706', text: '#fef9c3', badge: 'badge-medium' };
    case 'HIGH': return { bg: '#dc2626', text: '#fee2e2', badge: 'badge-high' };
    default: return { bg: '#6b7280', text: '#f3f4f6', badge: 'badge-unknown' };
  }
}

export function riskScoreColor(score: number) {
  if (score < 33) return '#16a34a';
  if (score < 66) return '#d97706';
  return '#dc2626';
}

export function formatDate(iso: string | null | undefined) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function formatDateTime(iso: string | null | undefined) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en-GB', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

export function sprintStatusColor(status: string) {
  switch (status) {
    case 'ACTIVE': return { dot: '#22c55e', label: 'Active' };
    case 'PLANNED': return { dot: '#60a5fa', label: 'Planned' };
    case 'COMPLETED': return { dot: '#a855f7', label: 'Completed' };
    case 'CANCELLED': return { dot: '#ef4444', label: 'Cancelled' };
    default: return { dot: '#6b7280', label: status };
  }
}

export function severity(s: string) {
  switch (s) {
    case 'CRITICAL': return { color: '#ef4444', icon: '🔴' };
    case 'WARNING': return { color: '#f59e0b', icon: '🟡' };
    default: return { color: '#3b82f6', icon: '🔵' };
  }
}
