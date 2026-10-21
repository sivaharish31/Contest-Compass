import type { Contest } from '../types/contest';
import { PLATFORM_LABELS, PLATFORM_COLORS } from '../types/contest';
import { useCountdown } from '../hooks/useCountdown';

interface ContestCardProps {
  contest: Contest;
}

export function ContestCard({ contest }: ContestCardProps) {
  const countdown = useCountdown(contest.startTime, contest.endTime);

  const startDate = new Date(contest.startTime);
  const formattedDate = startDate.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
  const formattedTime = startDate.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  const durationLabel = contest.durationSeconds ? formatDuration(contest.durationSeconds) : null;
  const platformColor = PLATFORM_COLORS[contest.platform] ?? 'var(--accent)';

  return (
    <div
      className="bento-card group flex flex-col justify-between overflow-hidden"
      style={{
        borderColor: countdown.isLive ? 'var(--live)' : undefined,
      }}
    >
      <div className="flex flex-1 flex-col p-5">
        <div className="mb-3 flex items-center justify-between gap-2">
          <span
            className="rounded px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider"
            style={{ backgroundColor: platformColor + '18', color: platformColor }}
          >
            {PLATFORM_LABELS[contest.platform]}
          </span>
          {(countdown.isLive || contest.status === 'LIVE') && (
            <span className="flex items-center gap-1.5 text-[10px] font-bold" style={{ color: 'var(--live)' }}>
              <span className="inline-block h-2 w-2 animate-pulse rounded-full" style={{ backgroundColor: 'var(--live)' }} />
              LIVE NOW
            </span>
          )}
          {(countdown.isPast || contest.status === 'FINISHED') && !countdown.isLive && (
            <span className="text-[10px] font-medium" style={{ color: 'var(--text-muted)' }}>
              Concluded
            </span>
          )}
        </div>

        <h3 className="line-clamp-2 text-sm font-bold leading-snug tracking-tight" style={{ color: 'var(--text-primary)' }} title={contest.name}>
          {contest.name}
        </h3>

        <div className="mt-3 grid grid-cols-2 gap-x-3 gap-y-1.5 text-[11px]" style={{ color: 'var(--text-secondary)' }}>
          <div className="flex items-center gap-1.5">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="3" y="4" width="18" height="18" rx="2" />
              <path d="M16 2v4M8 2v4M3 10h18" />
            </svg>
            <span>{formattedDate}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10" />
              <path d="M12 6v6l4 2" />
            </svg>
            <span>{formattedTime}</span>
          </div>
          {durationLabel && (
            <div className="flex items-center gap-1.5 col-span-2">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M5 12h14M12 5l7 7-7 7" />
              </svg>
              <span>Duration: {durationLabel}</span>
            </div>
          )}
        </div>

        <div
          className="mt-4 rounded px-3 py-2 text-center text-xs font-semibold font-mono-editorial"
          style={{
            backgroundColor: countdown.isPast ? 'var(--bg-hover)' : countdown.isLive ? 'var(--danger-muted)' : 'var(--accent-subtle)',
            color: countdown.isPast ? 'var(--text-muted)' : countdown.isLive ? 'var(--live)' : 'var(--accent)',
            border: `1px solid ${countdown.isPast ? 'var(--border)' : countdown.isLive ? 'var(--danger)' : 'var(--border)'}`,
          }}
        >
          {countdown.label}
        </div>

        <div className="mt-4 flex gap-2 pt-2 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
          <a
            href={contest.url}
            target="_blank"
            rel="noopener noreferrer"
            className="btn-editorial flex-1 rounded py-2 text-center text-xs font-medium"
          >
            Open Contest
          </a>
          {contest.registrationUrl && contest.registrationUrl !== contest.url && (
            <a
              href={contest.registrationUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="btn-editorial-outline flex-1 rounded py-2 text-center text-xs font-medium"
            >
              Register
            </a>
          )}
        </div>
      </div>
    </div>
  );
}

export function ContestCardSkeleton() {
  return (
    <div className="bento-card p-5 animate-pulse space-y-4">
      <div className="flex justify-between">
        <div className="h-4 w-16 rounded" style={{ backgroundColor: 'var(--bg-hover)' }} />
        <div className="h-4 w-12 rounded" style={{ backgroundColor: 'var(--bg-hover)' }} />
      </div>
      <div className="h-10 w-full rounded" style={{ backgroundColor: 'var(--bg-hover)' }} />
      <div className="grid grid-cols-2 gap-2">
        <div className="h-4 rounded" style={{ backgroundColor: 'var(--bg-hover)' }} />
        <div className="h-4 rounded" style={{ backgroundColor: 'var(--bg-hover)' }} />
      </div>
      <div className="h-8 w-full rounded" style={{ backgroundColor: 'var(--bg-hover)' }} />
    </div>
  );
}

function formatDuration(seconds: number): string {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (hours === 0) return `${minutes}m`;
  if (minutes === 0) return `${hours}h`;
  return `${hours}h ${minutes}m`;
}
