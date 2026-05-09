'use client';
import { riskScoreColor } from '@/lib/utils';

interface Props {
  score: number;       // 0-100
  size?: number;       // px
}

export function RiskGauge({ score, size = 100 }: Props) {
  const color = riskScoreColor(score);
  const r = 40;
  const circ = 2 * Math.PI * r;
  const offset = circ * (1 - score / 100);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
      <svg width={size} height={size} viewBox="0 0 100 100">
        <circle cx="50" cy="50" r={r} fill="none" stroke="var(--border)" strokeWidth="8" />
        <circle
          cx="50" cy="50" r={r} fill="none"
          stroke={color} strokeWidth="8"
          strokeDasharray={circ}
          strokeDashoffset={offset}
          strokeLinecap="round"
          transform="rotate(-90 50 50)"
          style={{ transition: 'stroke-dashoffset .5s ease' }}
        />
        <text x="50" y="55" textAnchor="middle" fill={color} fontSize="18" fontWeight="700">
          {Math.round(score)}
        </text>
      </svg>
    </div>
  );
}
