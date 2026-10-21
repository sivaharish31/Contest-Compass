import type { PlatformStat, Platform } from '../../types/contest';
import { PLATFORM_LABELS, PLATFORM_COLORS, PLATFORM_URLS } from '../../types/contest';

interface ContestRankingsCardProps {
  platforms: PlatformStat[];
}

export function ContestRankingsCard({ platforms }: ContestRankingsCardProps) {
  const getBadgeIcon = (platform: string) => {
    switch (platform.toUpperCase()) {
      case 'LEETCODE':
        return (
          <div className="flex h-9 w-9 items-center justify-center rounded-md font-bold text-sm shadow-xs" style={{ backgroundColor: 'rgba(245, 158, 11, 0.12)', color: '#f59e0b' }}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
            </svg>
          </div>
        );
      case 'CODECHEF':
        return (
          <div className="flex h-9 w-9 items-center justify-center rounded-md font-bold text-sm shadow-xs" style={{ backgroundColor: 'rgba(133, 77, 14, 0.12)', color: '#854d0e' }}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
            </svg>
          </div>
        );
      case 'CODEFORCES':
        return (
          <div className="flex h-9 w-9 items-center justify-center rounded-md font-bold text-sm shadow-xs" style={{ backgroundColor: 'rgba(24, 144, 255, 0.12)', color: '#1890ff' }}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M18 20V10" />
              <path d="M12 20V4" />
              <path d="M6 20v-6" />
            </svg>
          </div>
        );
      case 'ATCODER':
        return (
          <div className="flex h-9 w-9 items-center justify-center rounded-md font-bold text-sm shadow-xs" style={{ backgroundColor: 'rgba(51, 65, 85, 0.12)', color: '#71717a' }}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="17 11 12 6 7 11" />
              <polyline points="17 18 12 13 7 18" />
            </svg>
          </div>
        );
      default:
        return (
          <div className="flex h-9 w-9 items-center justify-center rounded-md font-bold text-sm shadow-xs" style={{ backgroundColor: 'var(--bg-hover)', color: 'var(--text-secondary)' }}>
            CP
          </div>
        );
    }
  };

  return (
    <div className="bento-card p-6 flex flex-col justify-between">
      <div className="pb-4 border-b" style={{ borderColor: 'var(--border)' }}>
        <h3 className="text-sm font-semibold tracking-tight" style={{ color: 'var(--text-primary)' }}>
          Contest Rankings
        </h3>
        <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
          Verified ratings across competitive platforms
        </p>
      </div>

      <div className="mt-4 space-y-4 divide-y" style={{ borderColor: 'var(--border-subtle)' }}>
        {platforms.map((p) => {
          const url = PLATFORM_URLS[p.platform] ? `${PLATFORM_URLS[p.platform]}${p.handle}` : undefined;
          return (
            <div key={p.platform} className="pt-3 first:pt-0 flex items-center justify-between gap-4">
              <div className="flex items-center gap-3 min-w-0">
                {getBadgeIcon(p.platform)}
                <div className="min-w-0">
                  <div className="flex items-center gap-1.5">
                    <span className="text-xs font-bold uppercase tracking-wider" style={{ color: 'var(--text-secondary)' }}>
                      {PLATFORM_LABELS[p.platform as Platform] ?? p.platform}
                    </span>
                    {p.verified && (
                      <span className="text-[11px]" style={{ color: 'var(--success)' }} title="Verified Handle">
                        ✓
                      </span>
                    )}
                  </div>
                  {p.rankTitle ? (
                    <span
                      className="inline-block mt-0.5 text-xs font-semibold capitalize"
                      style={{ color: PLATFORM_COLORS[p.platform as Platform] ?? 'var(--text-primary)' }}
                    >
                      {p.rankTitle}
                    </span>
                  ) : (
                    <span className="text-xs truncate block" style={{ color: 'var(--text-muted)' }}>
                      @{p.handle}
                    </span>
                  )}
                </div>
              </div>

              <div className="text-right shrink-0">
                <div className="text-2xl font-black tabular-nums tracking-tight font-mono-code" style={{ color: 'var(--text-primary)' }}>
                  {p.rating != null ? p.rating : '—'}
                </div>
                <div className="text-[11px] font-medium tabular-nums font-mono-code" style={{ color: 'var(--text-muted)' }}>
                  {p.maxRating != null ? `(max: ${p.maxRating})` : url ? (
                    <a
                      href={url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="hover:text-[var(--accent)] underline"
                      style={{ color: 'var(--text-muted)' }}
                    >
                      View profile
                    </a>
                  ) : 'Unrated'}
                </div>
              </div>
            </div>
          );
        })}

        {platforms.length === 0 && (
          <div className="text-center py-6">
            <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
              No platform rankings connected yet.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
