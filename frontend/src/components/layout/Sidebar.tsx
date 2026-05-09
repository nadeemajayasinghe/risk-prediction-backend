'use client';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  LayoutDashboard,
  Zap,
  BarChart2,
  PlusCircle,
  List,
  GitBranch,
  Settings,
  Activity,
} from 'lucide-react';

interface NavItem {
  href: string;
  label: string;
  icon: React.ReactNode;
}

const primary: NavItem[] = [
  { href: '/', label: 'Dashboard', icon: <LayoutDashboard size={16} /> },
  { href: '/sprints', label: 'Sprints', icon: <List size={16} /> },
  { href: '/sprints/new', label: 'New Sprint', icon: <PlusCircle size={16} /> },
];

const secondary: NavItem[] = [
  { href: '/risk', label: 'Risk Analysis', icon: <Zap size={16} /> },
  { href: '/reports', label: 'Reports', icon: <BarChart2 size={16} /> },
  { href: '/trends', label: 'Trends', icon: <Activity size={16} /> },
  { href: '/compare', label: 'Compare', icon: <GitBranch size={16} /> },
];

export default function Sidebar() {
  const pathname = usePathname();

  const active = (href: string) =>
    href === '/' ? pathname === '/' : pathname.startsWith(href);

  return (
    <aside className="sidebar">
      <div className="sidebar-logo">
        <div className="sidebar-logo-icon">⚡</div>
        <div>
          <div className="sidebar-logo-text">AgileRisk</div>
          <div className="sidebar-logo-sub">AI Project Manager</div>
        </div>
      </div>

      <nav className="sidebar-nav">
        <div className="nav-section-title">Main</div>
        {primary.map(n => (
          <Link key={n.href} href={n.href} className={`nav-link ${active(n.href) ? 'active' : ''}`}>
            {n.icon}
            <span>{n.label}</span>
          </Link>
        ))}

        <div className="nav-section-title">Analytics</div>
        {secondary.map(n => (
          <Link key={n.href} href={n.href} className={`nav-link ${active(n.href) ? 'active' : ''}`}>
            {n.icon}
            <span>{n.label}</span>
          </Link>
        ))}

        <div className="nav-section-title">System</div>
        <Link href="/settings" className={`nav-link ${active('/settings') ? 'active' : ''}`}>
          <Settings size={16} />
          <span>Settings</span>
        </Link>
      </nav>

      <div style={{ padding: '12px 14px', borderTop: '1px solid var(--border)' }}>
        <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>AgileRisk v1.0</div>
      </div>
    </aside>
  );
}
