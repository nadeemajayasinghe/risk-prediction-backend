'use client';
import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { sprintsApi } from '@/lib/api';
import type { SprintResponse, UpdateSprintRequest, SprintStatus } from '@/lib/types';
import { useToast } from '@/context/ToastContext';
import { LoadingOverlay } from '@/components/ui/Spinner';
import { Edit } from 'lucide-react';

const STATUSES: SprintStatus[] = ['PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED'];

export default function EditSprintPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { toast } = useToast();
  const [sprint, setSprint] = useState<SprintResponse | null>(null);
  const [form, setForm] = useState<Partial<UpdateSprintRequest>>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    sprintsApi.get(Number(id)).then(s => {
      setSprint(s);
      setForm({
        name: s.name,
        goal: s.goal ?? '',
        startDate: s.startDate,
        endDate: s.endDate,
        status: s.status,
        teamId: s.teamId ?? '',
        capacityPoints: s.capacityPoints ?? undefined,
        teamType: s.teamType ?? '',
        teamSize: s.teamSize ?? undefined,
        complexity: s.complexity ?? undefined,
        baseVelocity: s.baseVelocity ?? undefined,
        sprintCapacityHours: s.sprintCapacityHours ?? undefined,
      });
    });
  }, [id]);

  const set = (k: keyof UpdateSprintRequest, v: unknown) => setForm(p => ({ ...p, [k]: v }));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await sprintsApi.update(Number(id), form as UpdateSprintRequest);
      toast('Sprint updated', 'success');
      router.push(`/sprints/${id}`);
    } catch {
      toast('Failed to update sprint', 'error');
    } finally { setSaving(false); }
  };

  if (!sprint) return <LoadingOverlay />;

  return (
    <>
      <div className="topbar">
        <div className="topbar-title"><Edit size={18} /> Edit Sprint #{id}</div>
      </div>
      <div className="page-body" style={{ maxWidth: 720 }}>
        <form onSubmit={submit} className="section-gap">
          <div className="card">
            <div className="card-header"><span className="card-title">Sprint Details</span></div>
            <div className="card-body section-gap" style={{ gap: 14 }}>
              <div className="form-group">
                <label className="form-label">Name</label>
                <input className="form-input" value={form.name ?? ''} onChange={e => set('name', e.target.value)} />
              </div>
              <div className="form-group">
                <label className="form-label">Goal</label>
                <textarea className="form-textarea" value={form.goal ?? ''} onChange={e => set('goal', e.target.value)} />
              </div>
              <div className="form-grid-2">
                <div className="form-group">
                  <label className="form-label">Start Date</label>
                  <input type="date" className="form-input" value={form.startDate ?? ''} onChange={e => set('startDate', e.target.value)} />
                </div>
                <div className="form-group">
                  <label className="form-label">End Date</label>
                  <input type="date" className="form-input" value={form.endDate ?? ''} onChange={e => set('endDate', e.target.value)} />
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">Status</label>
                <select className="form-select" value={form.status} onChange={e => set('status', e.target.value)}>
                  {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
                </select>
              </div>
            </div>
          </div>

          <div className="card">
            <div className="card-header"><span className="card-title">Team & Capacity</span></div>
            <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div className="form-grid-2">
                <div className="form-group">
                  <label className="form-label">Team ID</label>
                  <input className="form-input" value={form.teamId ?? ''} onChange={e => set('teamId', e.target.value)} />
                </div>
                <div className="form-group">
                  <label className="form-label">Team Type</label>
                  <input className="form-input" value={form.teamType ?? ''} onChange={e => set('teamType', e.target.value)} />
                </div>
              </div>
              <div className="form-grid-3">
                <div className="form-group">
                  <label className="form-label">Team Size</label>
                  <input type="number" min={0} className="form-input" value={form.teamSize ?? ''} onChange={e => set('teamSize', Number(e.target.value))} />
                </div>
                <div className="form-group">
                  <label className="form-label">Capacity Points</label>
                  <input type="number" min={0} className="form-input" value={form.capacityPoints ?? ''} onChange={e => set('capacityPoints', Number(e.target.value))} />
                </div>
                <div className="form-group">
                  <label className="form-label">Sprint Capacity (hrs)</label>
                  <input type="number" min={0} step="0.5" className="form-input" value={form.sprintCapacityHours ?? ''} onChange={e => set('sprintCapacityHours', Number(e.target.value))} />
                </div>
              </div>
              <div className="form-grid-2">
                <div className="form-group">
                  <label className="form-label">Base Velocity</label>
                  <input type="number" min={0} step="0.1" className="form-input" value={form.baseVelocity ?? ''} onChange={e => set('baseVelocity', Number(e.target.value))} />
                </div>
                <div className="form-group">
                  <label className="form-label">Complexity (0-10)</label>
                  <input type="number" min={0} max={10} step="0.1" className="form-input" value={form.complexity ?? ''} onChange={e => set('complexity', Number(e.target.value))} />
                </div>
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
            <button type="button" className="btn btn-secondary" onClick={() => router.back()}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Saving…' : 'Save Changes'}</button>
          </div>
        </form>
      </div>
    </>
  );
}
