import { useQuery } from '@tanstack/react-query';
import { fetchContests, fetchSyncStatus, fetchPerformanceSummary } from '../api/contests';
import { ContestCard, ContestCardSkeleton } from '../components/ContestCard';
import { useAuth } from '../context/AuthContext';
import { useCountdown } from '../hooks/useCountdown';
import type { Platform, Contest } from '../types/contest';
import { PLATFORM_LABELS, PLATFORM_COLORS, ALL_PLATFORMS } from '../types/contest';
import { useState } from 'react';
import { Link } from 'react-router-dom';

export function Dashboard() {
  const { user } = useAuth();
  const [platformFilter, setPlatformFilter] = useState<Platform | undefined>();
  const [search, setSearch] = useState('');

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['contests', 'upcoming', platformFilter, search],
    queryFn: () =>
      fetchContests({
        status: ['UPCOMING', 'LIVE'],
        platform: platformFilter,
        search: search || undefined,
        size: 15,
      }),
    refetchInterval: 60_000,
  });

  const { data: syncStatus } = useQuery({
    queryKey: ['syncStatus'],
    queryFn: fetchSyncStatus,
    refetchInterval: 120_000,
  });

  const { data: summary } = useQuery({
    queryKey: ['performance', 'summary'],
    queryFn: fetchPerformanceSummary,
    enabled: !!user,
  });

  const liveContests = data?.content.filter((c) => c.status === 'LIVE') ?? [];
  const upcomingContests = data?.content.filter((c) => c.status === 'UPCOMING') ?? [];

  // Next upcoming contest for hero spotlight
  const nextContest: Contest | undefined = upcomingContests[0];

  return (
    <div className="space-y-6">
      {/* Next Contest Spotlight */}
      {nextContest && (
        <div className="bento-card p-5">
          <NextContestSpotlight contest={nextContest} />
        </div>
      )}

      {/* Middle Bento Row: Sync Status + Quick Performance Snapshot */}
      <div className="grid grid-cols-1 md:grid-cols-12 gap-6 items-stretch">
        {/* Platform Sync Status (6 cols) */}
        <div className="md:col-span-6 bento-card p-5">
          <div className="flex items-center justify-between pb-3 border-b" style={{ borderColor: 'var(--border)' }}>
            <span className="text-xs font-bold uppercase tracking-wider" style={{ color: 'var(--text-secondary)' }}>
              Platform Sync Status
            </span>
            <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
              Automatic 2m polling
            </span>
          </div>

          <div className="mt-3 grid grid-cols-2 sm:grid-cols-4 gap-2">
            {syncStatus && syncStatus.length > 0 ? (
              syncStatus.map((s) => (
                <div
                  key={s.platform}
                  className="p-3 rounded border flex flex-col justify-between transition-colors hover:bg-[var(--bg-hover)]"
                  style={{ backgroundColor: 'var(--bg-card-subtle)', borderColor: 'var(--border-subtle)' }}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-[11px] font-bold" style={{ color: PLATFORM_COLORS[s.platform] ?? 'var(--text-primary)' }}>
                      {PLATFORM_LABELS[s.platform]}
                    </span>
                    <span
                      className="h-2 w-2 rounded-full"
                      style={{
                        backgroundColor: s.status === 'SUCCESS' ? 'var(--success)' : s.status === 'ERROR' ? 'var(--danger)' : 'var(--text-muted)',
                      }}
                      title={s.status}
                    />
                  </div>
                  <div className="mt-2 text-base font-black tabular-nums font-mono-editorial" style={{ color: 'var(--text-primary)' }}>
                    {s.contestsCount}
                  </div>
                  <span className="text-[10px]" style={{ color: 'var(--text-muted)' }}>
                    contests indexed
                  </span>
                </div>
              ))
            ) : (
              <div className="col-span-4 text-center py-4 text-xs text-muted">
                Loading sync status...
              </div>
            )}
          </div>
        </div>

        {/* User Analytics Preview Snapshot (6 cols) */}
        <div className="md:col-span-6 bento-card p-5 flex flex-col justify-between">
          <div className="flex items-center justify-between pb-3 border-b" style={{ borderColor: 'var(--border)' }}>
            <span className="text-xs font-bold uppercase tracking-wider" style={{ color: 'var(--text-secondary)' }}>
              Performance Snapshot
            </span>
            {user && (
              <Link to="/performance" className="text-xs font-semibold hover:underline" style={{ color: 'var(--accent)' }}>
                Full Analytics →
              </Link>
            )}
          </div>

          {user && summary ? (
            <div className="mt-3 grid grid-cols-3 gap-3">
              <div className="p-3 rounded border" style={{ backgroundColor: 'var(--bg-card-subtle)', borderColor: 'var(--border-subtle)' }}>
                <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                  Contests Attended
                </span>
                <div className="mt-1 text-2xl font-black tabular-nums font-mono-editorial" style={{ color: 'var(--text-primary)' }}>
                  {summary.totalContests}
                </div>
                <span className="text-[10px]" style={{ color: 'var(--text-muted)' }}>
                  +{summary.contestsLast30Days} this month
                </span>
              </div>

              <div className="p-3 rounded border" style={{ backgroundColor: 'var(--bg-card-subtle)', borderColor: 'var(--border-subtle)' }}>
                <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                  Profiles Linked
                </span>
                <div className="mt-1 text-2xl font-black tabular-nums font-mono-editorial" style={{ color: 'var(--text-primary)' }}>
                  {summary.connectedProfiles}
                </div>
                <span className="text-[10px]" style={{ color: 'var(--text-muted)' }}>
                  {summary.mostActivePlatform ?? 'None'} top
                </span>
              </div>

              <div className="p-3 rounded border" style={{ backgroundColor: 'var(--bg-card-subtle)', borderColor: 'var(--border-subtle)' }}>
                <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                  Active Contests
                </span>
                <div className="mt-1 text-2xl font-black tabular-nums font-mono-editorial" style={{ color: 'var(--accent)' }}>
                  {liveContests.length + upcomingContests.length}
                </div>
                <span className="text-[10px]" style={{ color: 'var(--text-muted)' }}>
                  ready to enter
                </span>
              </div>
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center text-center py-4">
              <p className="text-xs font-semibold" style={{ color: 'var(--text-secondary)' }}>
                Connect your handles to view real-time statistics
              </p>
              <p className="text-[11px] mt-0.5 text-muted max-w-xs">
                Track personal rating curves and contest records by linking your accounts.
              </p>
              <Link
                to={user ? '/settings' : '/login'}
                className="mt-3 rounded border px-3 py-1.5 text-xs font-semibold hover:bg-[var(--bg-hover)] transition-colors"
                style={{ borderColor: 'var(--border)', color: 'var(--text-primary)' }}
              >
                {user ? 'Link Profiles in Settings' : 'Sign in to Connect'}
              </Link>
            </div>
          )}
        </div>
      </div>

      {/* Contest Discovery Search & Filter Strip */}
      <div className="flex flex-wrap items-center justify-between gap-3 pt-2">
        <div className="flex flex-wrap items-center gap-1.5">
          <button
            onClick={() => setPlatformFilter(undefined)}
            className="rounded px-3 py-1.5 text-xs font-semibold font-mono-editorial transition-all"
            style={{
              backgroundColor: !platformFilter ? 'var(--btn-primary-bg)' : 'transparent',
              color: !platformFilter ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
              border: '1px solid var(--border)',
            }}
          >
            All Platforms
          </button>
          {ALL_PLATFORMS.map((p) => (
            <button
              key={p}
              onClick={() => setPlatformFilter(platformFilter === p ? undefined : p)}
              className="rounded px-3 py-1.5 text-xs font-semibold font-mono-editorial transition-all"
              style={{
                backgroundColor: platformFilter === p ? 'var(--btn-primary-bg)' : 'transparent',
                color: platformFilter === p ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
                border: '1px solid var(--border)',
              }}
            >
              {PLATFORM_LABELS[p]}
            </button>
          ))}
        </div>

        {/* Search Input */}
        <div className="relative">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--text-muted)" strokeWidth="2">
            <circle cx="11" cy="11" r="8" />
            <path d="M21 21l-4.35-4.35" />
          </svg>
          <input
            type="text"
            placeholder="Search contests..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="rounded border py-1.5 pl-9 pr-3 text-xs outline-none transition-colors focus:border-[var(--accent)] font-mono-editorial"
            style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border)', color: 'var(--text-primary)', width: '200px' }}
          />
        </div>
      </div>

      {/* Loading Skeletons */}
      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <ContestCardSkeleton key={i} />
          ))}
        </div>
      )}

      {/* Error State */}
      {error && (
        <div className="bento-card p-8 text-center">
          <p className="text-sm font-semibold" style={{ color: 'var(--danger)' }}>
            Failed to load upcoming contests
          </p>
          <p className="text-xs text-muted mt-1">Please verify the backend server is running.</p>
          <button
            onClick={() => refetch()}
            className="mt-3 rounded-xl border px-4 py-1.5 text-xs font-semibold hover:bg-[var(--bg-hover)] transition-colors"
            style={{ borderColor: 'var(--border)', color: 'var(--text-primary)' }}
          >
            Retry Fetch
          </button>
        </div>
      )}

      {/* Content Grids */}
      {!isLoading && !error && (
        <div className="space-y-6">
          {/* Live Contests */}
          {liveContests.length > 0 && (
            <section className="space-y-3">
              <div className="flex items-center gap-2">
                <span className="h-2.5 w-2.5 animate-pulse rounded-full" style={{ backgroundColor: 'var(--live)' }} />
                <h2 className="text-sm font-bold uppercase tracking-wider" style={{ color: 'var(--text-primary)' }}>
                  Live Contests ({liveContests.length})
                </h2>
              </div>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {liveContests.map((c) => (
                  <ContestCard key={c.id} contest={c} />
                ))}
              </div>
            </section>
          )}

          {/* Upcoming Contests */}
          {upcomingContests.length > 0 && (
            <section className="space-y-3">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-bold uppercase tracking-wider" style={{ color: 'var(--text-primary)' }}>
                  Upcoming Schedule ({upcomingContests.length})
                </h2>
                <Link to="/contests" className="text-xs font-semibold hover:underline" style={{ color: 'var(--accent)' }}>
                  View All →
                </Link>
              </div>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {upcomingContests.map((c) => (
                  <ContestCard key={c.id} contest={c} />
                ))}
              </div>
            </section>
          )}

          {data && data.content.length === 0 && (
            <div className="bento-card py-16 text-center">
              <p className="text-sm font-semibold" style={{ color: 'var(--text-secondary)' }}>
                No contests match your selected filter
              </p>
              <button
                onClick={() => {
                  setPlatformFilter(undefined);
                  setSearch('');
                }}
                className="mt-3 text-xs font-semibold hover:underline"
                style={{ color: 'var(--accent)' }}
              >
                Clear all filters
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function NextContestSpotlight({ contest }: { contest: Contest }) {
  const countdown = useCountdown(contest.startTime, contest.endTime);
  const platformColor = PLATFORM_COLORS[contest.platform] ?? 'var(--accent)';

  return (
    <div className="h-full flex flex-col justify-between">
      <div>
        <div className="flex items-center justify-between gap-2 mb-2">
          <span
            className="rounded-md px-2 py-0.5 text-[10px] font-bold"
            style={{ backgroundColor: platformColor + '18', color: platformColor }}
          >
            {PLATFORM_LABELS[contest.platform]}
          </span>
          <span className="text-xs font-bold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>
            Up Next
          </span>
        </div>

        <h3 className="text-base font-bold line-clamp-2 mt-2 leading-snug" style={{ color: 'var(--text-primary)' }}>
          {contest.name}
        </h3>

        <div className="mt-3 flex items-center gap-3 text-xs" style={{ color: 'var(--text-muted)' }}>
          <span>{new Date(contest.startTime).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })}</span>
          <span>•</span>
          <span>{new Date(contest.startTime).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}</span>
        </div>
      </div>

      <div className="mt-6 pt-4 border-t flex items-center justify-between gap-4" style={{ borderColor: 'var(--border)' }}>
        <div>
          <span className="text-[10px] uppercase font-bold tracking-wider" style={{ color: 'var(--text-muted)' }}>
            Starts In
          </span>
          <div className="text-xl font-bold font-mono-editorial tabular-nums tracking-tight" style={{ color: 'var(--accent)' }}>
            {countdown.label}
          </div>
        </div>

        <a
          href={contest.url}
          target="_blank"
          rel="noopener noreferrer"
          className="btn-editorial rounded px-4 py-2 text-xs font-medium inline-flex items-center justify-center"
        >
          Open Link
        </a>
      </div>
    </div>
  );
}
