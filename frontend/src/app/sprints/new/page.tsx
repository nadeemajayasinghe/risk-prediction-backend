'use client';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { sprintsApi } from '@/lib/api';
import type { CreateSprintRequest, SprintStatus } from '@/lib/types';
import { useToast } from '@/context/ToastContext';
import { PlusCircle } from 'lucide-react';

const STATUSES: SprintStatus[] = ['PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED'];

export default function NewSprintPage() {
  const router = useRouter();
  const { toast } = useToast();
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<Partial<CreateSprintRequest>>({
    status: 'PLANNED',
    startDate: new Date().toISOString().slice(0, 10),
    endDate: '',
  });

  const set = (k: keyof CreateSprintRequest, v: unknown) =>
    setForm(p => ({ ...p, [k]: v }));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name || !form.startDate || !form.endDate || !form.status) {
      toast('Fill in required fields', 'error'); return;
    }
    setSaving(true);
    try {
      const created = await sprintsApi.create(form as CreateSprintRequest);
      toast(`Sprint "${created.name}" created`, 'success');
      router.push(`/sprints/${created.id}`);
    } catch {
      toast('Failed to create sprint', 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <div className="topbar">
        <div className="topbar-title">
          <PlusCircle size={18} />
          New Sprint
        </div>
      </div>

      <div className="page-body" style={{ maxWidth: 720 }}>
        <form onSubmit={submit} className="section-gap">
          <div className="card">
            <div className="card-header"><span className="card-title">Sprint Details</span></div>
            <div className="card-body section-gap" style={{ gap: 14 }}>
              <div className="form-group">
                <label className="form-label">Name *</label>
                <input className="form-input" required placeholder="e.g. Sprint 42" value={form.name ?? ''} onChange={e => set('name', e.target.value)} />
              </div>
              <div className="form-group">
                <label className="form-label">Goal</label>
                <textarea className="form-textarea" placeholder="Sprint goal…" value={form.goal ?? ''} onChange={e => set('goal', e.target.value)} />
              </div>
              <div className="form-grid-2">
                <div className="form-group">
                  <label className="form-label">Start Date *</label>
                  <input type="date" className="form-input" required value={form.startDate ?? ''} onChange={e => set('startDate', e.target.value)} />
                </div>
                <div className="form-group">
                  <label className="form-label">End Date *</label>
                  <input type="date" className="form-input" required value={form.endDate ?? ''} onChange={e => set('endDate', e.target.value)} />
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">Status *</label>
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
                  <input className="form-input" placeholder="team-alpha" value={form.teamId ?? ''} onChange={e => set('teamId', e.target.value)} />
                </div>
                <div className="form-group">
                  <label className="form-label">Team Type</label>
                  <input className="form-input" placeholder="Scrum / Kanban" value={form.teamType ?? ''} onChange={e => set('teamType', e.target.value)} />
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
            </div>
          </div>

          <div className="card">
            <div className="card-header"><span className="card-title">AI Parameters</span></div>
            <div className="card-body">
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
            <button type="submit" className="btn btn-primary" disabled={saving}>
              {saving ? 'Creating…' : 'Create Sprint'}
            </button>
          </div>
        </form>
      </div>
    </>
  );
}
