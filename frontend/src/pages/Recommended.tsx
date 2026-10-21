import { useQuery } from '@tanstack/react-query';
import { fetchContests, fetchRecommendations } from '../api/contests';
import type { BackendRecommendation } from '../api/contests';
import { ContestCard, ContestCardSkeleton } from '../components/ContestCard';
import { useAuth } from '../context/AuthContext';
import { Link } from 'react-router-dom';
import { useState, useMemo } from 'react';
import { PLATFORM_LABELS, PLATFORM_COLORS, ALL_PLATFORMS } from '../types/contest';
import type { Platform } from '../types/contest';

function formatLevel(level: string): string {
  switch (level?.toLowerCase()) {
    case 'beginner':  return 'Beginner';
    case 'improving': return 'Improving';
    case 'practice':  return 'Practice Mode';
    case 'active':    return 'Active Competitor';
    default:          return level ?? 'General';
  }
}

export function Recommended() {
  const { user } = useAuth();

  const {
    data: recs,
    isLoading: recsLoading,
    error: recsError,
    refetch,
  } = useQuery({
    queryKey: ['recommendations'],
    queryFn: fetchRecommendations,
    enabled: !!user,
    refetchInterval: 120_000,
  });

  const { data: publicContests, isLoading: publicLoading } = useQuery({
    queryKey: ['contests', 'recommended-public'],
    queryFn: () => fetchContests({ status: ['UPCOMING', 'LIVE'], size: 12 }),
    enabled: !user,
    refetchInterval: 120_000,
  });

  const [platformFilter, setPlatformFilter] = useState<Platform | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [dateRange, setDateRange] = useState<number | null>(null);

  const filtered = useMemo(() => {
    if (!recs) return [];
    return recs.filter((rec: BackendRecommendation) => {
      if (platformFilter && rec.contest.platform !== platformFilter) return false;
      if (searchQuery && !rec.contest.name.toLowerCase().includes(searchQuery.toLowerCase())) return false;
      if (dateRange) {
        const start = new Date(rec.contest.startTime).getTime();
        const now = Date.now();
        if (start > now + dateRange * 86400000) return false;
      }
      return true;
    });
  }, [recs, platformFilter, searchQuery, dateRange]);

  const hasActiveFilters = platformFilter !== null || searchQuery !== '' || dateRange !== null;

  if (!user) {
    return (
      <div className="space-y-6">
        <div className="bento-card p-6 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
              Curated Contests
            </h1>
            <p className="mt-1 text-xs" style={{ color: 'var(--text-muted)' }}>
              Sign in to receive personalized contest recommendations tailored to your rating level and preferred platforms.
            </p>
          </div>
          <Link
            to="/login"
            className="btn-editorial rounded px-4 py-2 text-xs font-medium shrink-0"
          >
            Sign in for Personalized Feed
          </Link>
        </div>

        {publicLoading && (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <ContestCardSkeleton key={i} />
            ))}
          </div>
        )}

        {publicContests && publicContests.content.length > 0 && (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {publicContests.content.map((c) => (
              <ContestCard key={c.id} contest={c} />
            ))}
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
            Recommended For You
          </h1>
        </div>
        {recs && recs.length > 0 && (
          <div
            className="inline-flex items-center gap-2 rounded px-2.5 py-1 text-xs font-mono-editorial border"
            style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border)' }}
          >
            <span className="text-[10px] uppercase font-semibold text-muted">Skill Level:</span>
            <span style={{ color: 'var(--accent)' }}>{formatLevel(recs[0].userLevel)}</span>
          </div>
        )}
      </div>

      {/* Filters */}
      {recs && recs.length > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          {/* Platform filter */}
          <div className="flex items-center gap-1 overflow-x-auto">
            <button
              type="button"
              onClick={() => setPlatformFilter(null)}
              className="shrink-0 rounded px-2.5 py-1 text-[11px] font-medium font-mono-editorial transition-colors"
              style={{
                backgroundColor: platformFilter === null ? 'var(--accent)' : 'var(--bg-elevated)',
                color: platformFilter === null ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
              }}
            >
              All
            </button>
            {ALL_PLATFORMS.map((p) => {
              const hasRecs = recs.some((r: BackendRecommendation) => r.contest.platform === p);
              if (!hasRecs) return null;
              const color = PLATFORM_COLORS[p];
              return (
                <button
                  key={p}
                  type="button"
                  onClick={() => setPlatformFilter(platformFilter === p ? null : p)}
                  className="shrink-0 rounded px-2.5 py-1 text-[11px] font-medium font-mono-editorial transition-colors"
                  style={{
                    backgroundColor: platformFilter === p ? color : 'var(--bg-elevated)',
                    color: platformFilter === p ? '#fff' : 'var(--text-secondary)',
                  }}
                >
                  {PLATFORM_LABELS[p]}
                </button>
              );
            })}
          </div>

          {/* Date range */}
          <select
            value={dateRange ?? ''}
            onChange={(e) => setDateRange(e.target.value ? Number(e.target.value) : null)}
            className="rounded border px-2 py-1 text-[11px] font-mono-editorial outline-none"
            style={{ backgroundColor: 'var(--bg-elevated)', borderColor: 'var(--border)', color: 'var(--text-secondary)' }}
            aria-label="Date range filter"
          >
            <option value="">Any time</option>
            <option value="7">Next 7 days</option>
            <option value="14">Next 14 days</option>
            <option value="30">Next 30 days</option>
          </select>

          {/* Search */}
          <input
            type="text"
            placeholder="Search contests..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="rounded border px-2.5 py-1 text-[11px] font-mono-editorial outline-none focus:border-[var(--accent)] w-40"
            style={{ backgroundColor: 'var(--bg-elevated)', borderColor: 'var(--border)', color: 'var(--text-primary)' }}
            aria-label="Search recommendations"
          />

          {hasActiveFilters && (
            <button
              type="button"
              onClick={() => { setPlatformFilter(null); setSearchQuery(''); setDateRange(null); }}
              className="rounded px-2 py-1 text-[10px] font-medium transition-colors hover:bg-[var(--bg-hover)]"
              style={{ color: 'var(--text-muted)' }}
            >
              Clear filters
            </button>
          )}
        </div>
      )}

      {recsLoading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <ContestCardSkeleton key={i} />
          ))}
        </div>
      )}

      {recsError && (
        <div className="bento-card p-8 text-center">
          <p className="text-sm font-semibold" style={{ color: 'var(--danger)' }}>
            Unable to load recommendations
          </p>
          <p className="text-xs text-muted mt-1">Please try refreshing in a few moments.</p>
          <button onClick={() => refetch()} className="btn-editorial-outline mt-3 rounded px-4 py-1.5 text-xs font-medium">Retry</button>
        </div>
      )}

      {!recsLoading && !recsError && filtered.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((rec: BackendRecommendation) => (
            <div key={rec.contest.id} className="space-y-2">
              <div
                className="flex items-center justify-between rounded px-3 py-1.5 border text-xs"
                style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border)' }}
              >
                <div className="flex items-center gap-2 min-w-0">
                  <span
                    className="flex h-5 w-8 items-center justify-center rounded font-mono-editorial font-bold text-[11px] tabular-nums text-white"
                    style={{ backgroundColor: rec.score >= 80 ? 'var(--success)' : rec.score >= 50 ? 'var(--accent)' : 'var(--warning)' }}
                  >
                    {rec.score}
                  </span>
                  <span className="truncate font-medium text-[11px]" style={{ color: 'var(--text-secondary)' }}>
                    {rec.reasons[0] ?? 'Platform Match'}
                  </span>
                </div>
              </div>
              <ContestCard contest={rec.contest} />
            </div>
          ))}
        </div>
      )}

      {!recsLoading && !recsError && recs && recs.length > 0 && filtered.length === 0 && (
        <div className="bento-card py-12 text-center">
          <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>No matches</p>
          <p className="text-xs text-muted mt-1">No recommendations match your current filters.</p>
          <button
            type="button"
            onClick={() => { setPlatformFilter(null); setSearchQuery(''); setDateRange(null); }}
            className="btn-editorial-outline mt-3 rounded px-4 py-1.5 text-xs font-medium"
          >
            Clear filters
          </button>
        </div>
      )}

      {!recsLoading && !recsError && recs && recs.length === 0 && (
        <div className="bento-card py-16 text-center">
          <h3 className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
            No personalized recommendations yet
          </h3>
          <p className="mt-1 text-xs text-muted max-w-sm mx-auto">
            Connect your competitive programming profiles to get tailored contest suggestions.
          </p>
          <Link to="/settings" className="btn-editorial mt-5 inline-block rounded px-4 py-2 text-xs font-medium">
            Connect Profiles
          </Link>
        </div>
      )}
    </div>
  );
}
