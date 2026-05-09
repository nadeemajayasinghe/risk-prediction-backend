'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { sprintsApi } from '@/lib/api';
import type { SprintResponse } from '@/lib/types';
import { SprintStatusBadge } from '@/components/ui/Badges';
import { LoadingOverlay } from '@/components/ui/Spinner';
import { formatDate } from '@/lib/utils';
import { List, PlusCircle, Search, Trash2, Edit } from 'lucide-react';
import { useToast } from '@/context/ToastContext';

export default function SprintsPage() {
  const [sprints, setSprints] = useState<SprintResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<string>('ALL');
  const { toast } = useToast();

  const load = () => {
    setLoading(true);
    sprintsApi.list().then(setSprints).finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const deleteSprint = async (id: number) => {
    if (!confirm('Delete this sprint? This action cannot be undone.')) return;
    try {
      await sprintsApi.delete(id);
      toast('Sprint deleted', 'success');
      load();
    } catch {
      toast('Failed to delete sprint', 'error');
    }
  };

  const filtered = sprints.filter(s => {
    const matchSearch = s.name.toLowerCase().includes(search.toLowerCase()) ||
      (s.teamId ?? '').toLowerCase().includes(search.toLowerCase());
    const matchFilter = filter === 'ALL' || s.status === filter;
    return matchSearch && matchFilter;
  });

  return (
    <>
      <div className="topbar">
        <div className="topbar-title">
          <List size={18} />
          Sprints
        </div>
        <div className="topbar-actions">
          <Link href="/sprints/new" className="btn btn-primary btn-sm">
            <PlusCircle size={14} /> New Sprint
          </Link>
        </div>
      </div>

      <div className="page-body section-gap">
        {/* Filters */}
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <div style={{ position: 'relative', flex: 1, minWidth: 200 }}>
            <Search size={14} style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
            <input
              className="form-input"
              style={{ paddingLeft: 32 }}
              placeholder="Search sprints…"
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
          </div>
          {(['ALL', 'PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED'] as const).map(s => (
            <button
              key={s}
              className={`btn ${filter === s ? 'btn-primary' : 'btn-secondary'} btn-sm`}
              onClick={() => setFilter(s)}
            >
              {s}
            </button>
          ))}
        </div>

        {loading ? <LoadingOverlay /> : (
          <div className="card">
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Name</th>
                    <th>Goal</th>
                    <th>Team</th>
                    <th>Start</th>
                    <th>End</th>
                    <th>Status</th>
                    <th>Capacity</th>
                    <th>Size</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(s => (
                    <tr key={s.id}>
                      <td className="primary">{s.id}</td>
                      <td>
                        <Link href={`/sprints/${s.id}`} style={{ color: 'var(--brand-light)', textDecoration: 'none', fontWeight: 500 }}>
                          {s.name}
                        </Link>
                      </td>
                      <td style={{ maxWidth: 200 }} className="truncate">{s.goal ?? '—'}</td>
                      <td>{s.teamId ?? '—'}</td>
                      <td>{formatDate(s.startDate)}</td>
                      <td>{formatDate(s.endDate)}</td>
                      <td><SprintStatusBadge status={s.status} /></td>
                      <td>{s.capacityPoints ?? '—'} pts</td>
                      <td>{s.teamSize ?? '—'}</td>
                      <td>
                        <div style={{ display: 'flex', gap: 4 }}>
                          <Link href={`/sprints/${s.id}/edit`} className="btn btn-ghost btn-icon btn-sm">
                            <Edit size={13} />
                          </Link>
                          <button className="btn btn-danger btn-icon btn-sm" onClick={() => deleteSprint(s.id)}>
                            <Trash2 size={13} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {filtered.length === 0 && (
                    <tr>
                      <td colSpan={10} className="text-center" style={{ padding: 50, color: 'var(--text-muted)' }}>
                        No sprints match your criteria
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </>
  );
}
