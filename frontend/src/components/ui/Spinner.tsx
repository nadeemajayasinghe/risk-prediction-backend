'use client';

export function Spinner({ size = 20 }: { size?: number }) {
  return (
    <div
      style={{
        width: size, height: size,
        border: '2px solid var(--border)',
        borderTopColor: 'var(--brand)',
        borderRadius: '50%',
        animation: 'spin .6s linear infinite',
      }}
    />
  );
}

export function LoadingOverlay({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="loading-overlay">
      <Spinner />
      <span>{label}</span>
    </div>
  );
}
