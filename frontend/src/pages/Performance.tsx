import { useQuery } from '@tanstack/react-query';
import {
  fetchPerformanceSummary,
  fetchRatingHistoryWithRange,
  fetchAllRatingHistory,
  fetchActivityHeatmap,
} from '../api/contests';
import { useAuth } from '../context/AuthContext';
import { Link } from 'react-router-dom';
import { useState, useMemo, useEffect } from 'react';

// Bento Components
import { ActivityHeatmap } from '../components/bento/ActivityHeatmap';
import { toUTCDateString } from '../lib/dateUtils';
import { PlatformDistributionDonut } from '../components/bento/PlatformDistributionDonut';
import { RatingHeroChart } from '../components/bento/RatingHeroChart';
import { ContestRankingsCard } from '../components/bento/ContestRankingsCard';
import { ContestHistoryTable } from '../components/bento/ContestHistoryTable';

export function Performance() {
  const { user } = useAuth();

  const [selectedPlatform, setSelectedPlatform] = useState<string | null>(null);
  const [heatmapPlatform, setHeatmapPlatform] = useState<string>('ALL');
  const [dateRange, setDateRange] = useState<number | undefined>(undefined);

  // Queries
  const { data: summary, isLoading: summaryLoading, error: summaryError } = useQuery({
    queryKey: ['performance', 'summary'],
    queryFn: fetchPerformanceSummary,
    enabled: !!user,
  });

  const platforms = summary?.platforms ?? [];
  const platformsWithData = platforms.filter((p) => p.contestCount > 0);

  // Auto-select first platform with contest data after load
  useEffect(() => {
    if (!selectedPlatform && summary && summary.platforms.length > 0) {
      const withHistory = summary.platforms.find((p) => p.contestCount > 0);
      setSelectedPlatform(withHistory ? withHistory.platform : 'OVERALL');
    }
  }, [summary, selectedPlatform]);

  // Rating History query
  const { data: history, isLoading: historyLoading } = useQuery({
    queryKey: ['performance', 'history', selectedPlatform, dateRange],
    queryFn: () => {
      if (selectedPlatform === 'OVERALL') {
        return fetchAllRatingHistory(dateRange);
      }
      return fetchRatingHistoryWithRange(selectedPlatform!, dateRange);
    },
    enabled: !!user && !!selectedPlatform,
  });

  // Heatmap query — unified problem-solving + contest activity
  const { data: heatmapData } = useQuery({
    queryKey: ['performance', 'heatmap', heatmapPlatform],
    queryFn: () => fetchActivityHeatmap(heatmapPlatform === 'ALL' ? undefined : heatmapPlatform),
    enabled: !!user,
  });

  // Calculate streaks using UTC date strings
  const activityStats = useMemo(() => {
    if (!heatmapData?.entries.length) return { currentStreak: 0, longestStreak: 0, totalDays: 0 };

    const dateSet = new Set(heatmapData.entries.filter((e) => e.count > 0).map((e) => e.date));
    const sortedDates = [...dateSet].sort().reverse();

    if (sortedDates.length === 0) return { currentStreak: 0, longestStreak: 0, totalDays: 0 };

    const now = new Date();
    const todayUTC = toUTCDateString(now);
    const yesterdayUTC = toUTCDateString(
      new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - 1))
    );

    let currentStreak = 0;
    let startOffset = 0;
    if (sortedDates[0] === todayUTC) {
      startOffset = 0;
    } else if (sortedDates[0] === yesterdayUTC) {
      startOffset = 1;
    }

    if (startOffset === 0 && sortedDates[0] !== todayUTC) {
      currentStreak = 0;
    } else {
      for (let i = 0; i < sortedDates.length; i++) {
        const expected = new Date(
          Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - i - startOffset)
        );
        const expectedStr = toUTCDateString(expected);
        if (sortedDates[i] === expectedStr) {
          currentStreak++;
        } else {
          break;
        }
      }
    }

    let longestStreak = 1;
    let tempStreak = 1;
    for (let i = 1; i < sortedDates.length; i++) {
      const prev = new Date(sortedDates[i - 1] + 'T00:00:00Z');
      const curr = new Date(sortedDates[i] + 'T00:00:00Z');
      const diffDays = (prev.getTime() - curr.getTime()) / 86400000;
      if (Math.abs(diffDays - 1) < 0.01) {
        tempStreak++;
        longestStreak = Math.max(longestStreak, tempStreak);
      } else {
        tempStreak = 1;
      }
    }

    return { currentStreak, longestStreak, totalDays: sortedDates.length };
  }, [heatmapData]);

  // Total verified problems solved across all connected platforms
  const totalProblemsSolved = platforms.reduce((sum, p) => sum + (p.problemsSolved || 0), 0);

  if (!user) {
    return (
      <div className="mx-auto max-w-md pt-20 text-center">
        <div
          className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-2xl shadow-xs"
          style={{ backgroundColor: 'var(--accent-subtle)' }}
        >
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" strokeWidth="2.5">
            <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
          </svg>
        </div>
        <h1 className="text-2xl font-black tracking-tight" style={{ color: 'var(--text-primary)' }}>
          Performance Dashboard
        </h1>
        <p className="mx-auto mt-2 max-w-sm text-xs leading-relaxed" style={{ color: 'var(--text-muted)' }}>
          Sign in to access your unified competitive programming analytics, rating history curves, and calendar activity timeline.
        </p>
        <Link to="/login" className="mt-6 inline-block btn-noir px-6 py-2.5 text-xs font-semibold shadow-xs">
          Sign in to Contest Compass
        </Link>
      </div>
    );
  }

  if (summaryLoading) {
    return (
      <div className="space-y-6">
        <div className="h-8 w-56 animate-pulse rounded" style={{ backgroundColor: 'var(--bg-hover)' }} />
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="h-20 animate-pulse rounded" style={{ backgroundColor: 'var(--bg-card)' }} />
          ))}
        </div>
        <div className="h-72 animate-pulse rounded" style={{ backgroundColor: 'var(--bg-card)' }} />
        <div className="h-64 animate-pulse rounded" style={{ backgroundColor: 'var(--bg-card)' }} />
      </div>
    );
  }

  if (summaryError) {
    return (
      <div className="space-y-6">
        <h1 className="text-xl font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
          Performance Analytics
        </h1>
        <div className="bento-card p-10 text-center">
          <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded" style={{ backgroundColor: 'var(--danger-muted)' }}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--danger)" strokeWidth="2.5">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
          </div>
          <h3 className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
            Unable to load performance metrics
          </h3>
          <p className="mt-1 text-xs text-muted max-w-sm mx-auto">
            Please ensure the backend service and MySQL database are active and reachable.
          </p>
        </div>
      </div>
    );
  }

  const hasData = platforms.length > 0;

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-black tracking-tight" style={{ color: 'var(--text-primary)' }}>
            Performance Dashboard
          </h1>
          <p className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
            Verified ratings, cross-platform activity timeline, and DSA difficulty breakdown
          </p>
        </div>
        <div className="flex items-center gap-3">
          {summary?.lastSyncedAt && (
            <div className="flex items-center gap-2 text-xs font-mono-editorial" style={{ color: 'var(--text-muted)' }}>
              <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: 'var(--accent)' }} />
              <span>
                Synced {new Date(summary.lastSyncedAt).toLocaleDateString(undefined, {
                  month: 'short',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </span>
            </div>
          )}
          <Link
            to="/settings"
            className="rounded border px-3 py-1.5 text-[11px] font-medium font-mono-editorial hover:opacity-80 transition-opacity"
            style={{ borderColor: 'var(--border)', color: 'var(--text-secondary)', backgroundColor: 'var(--bg-elevated)' }}
          >
            Manage Profiles
          </Link>
        </div>
      </div>

      {!hasData ? (
        <div className="bento-card p-12 text-center">
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-md" style={{ backgroundColor: 'var(--accent-subtle)' }}>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" strokeWidth="2.5">
              <path d="M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0zM12 14a7 7 0 0 0-7 7h14a7 7 0 0 0-7-7z" />
            </svg>
          </div>
          <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
            No Connected Coding Profiles
          </h3>
          <p className="mt-1 text-xs text-muted max-w-md mx-auto">
            Link your Codeforces, LeetCode, CodeChef, or AtCoder profiles in Settings to unlock rating graphs, activity calendar, and DSA metrics.
          </p>
          <Link
            to="/settings"
            className="mt-6 inline-block btn-editorial px-5 py-2.5 text-xs font-semibold shadow-xs"
          >
            Connect Profiles in Settings
          </Link>
        </div>
      ) : (
        <div className="space-y-6">
          {/* Stat Cards */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            <div className="bento-card p-4">
              <span className="text-[10px] font-bold font-mono-editorial uppercase tracking-wider text-muted">
                Questions Solved
              </span>
              <div className="text-2xl font-black tabular-nums tracking-tight mt-1 font-mono-editorial" style={{ color: 'var(--text-primary)' }}>
                {totalProblemsSolved > 0 ? totalProblemsSolved.toLocaleString() : '—'}
              </div>
              <p className="mt-1 text-[10px]" style={{ color: 'var(--text-muted)' }}>
                {totalProblemsSolved > 0 ? 'Verified across platforms' : 'Not reported'}
              </p>
            </div>

            <div className="bento-card p-4">
              <span className="text-[10px] font-bold font-mono-editorial uppercase tracking-wider text-muted">
                Contests Attended
              </span>
              <div className="text-2xl font-black tabular-nums tracking-tight mt-1 font-mono-editorial" style={{ color: 'var(--accent)' }}>
                {summary?.totalContests ?? 0}
              </div>
              <p className="mt-1 text-[10px]" style={{ color: 'var(--text-muted)' }}>
                {summary?.contestsLast30Days ?? 0} in last 30 days
              </p>
            </div>

            <div className="bento-card p-4">
              <span className="text-[10px] font-bold font-mono-editorial uppercase tracking-wider text-muted">
                Active Days
              </span>
              <div className="text-2xl font-black tabular-nums tracking-tight mt-1 font-mono-editorial" style={{ color: 'var(--text-primary)' }}>
                {activityStats.totalDays}
              </div>
              <p className="mt-1 text-[10px]" style={{ color: 'var(--text-muted)' }}>
                Days with verified activity
              </p>
            </div>

            <div className="bento-card p-4">
              <span className="text-[10px] font-bold font-mono-editorial uppercase tracking-wider text-muted">
                Current Streak
              </span>
              <div className="text-2xl font-black tabular-nums tracking-tight mt-1 font-mono-editorial" style={{ color: 'var(--accent)' }}>
                {activityStats.currentStreak}d
              </div>
              <p className="mt-1 text-[10px]" style={{ color: 'var(--text-muted)' }}>
                Longest: {activityStats.longestStreak} days
              </p>
            </div>
          </div>

          {/* Unified Activity Heatmap */}
          <ActivityHeatmap
            entries={heatmapData?.entries ?? []}
            currentStreak={activityStats.currentStreak}
            longestStreak={activityStats.longestStreak}
            totalDays={activityStats.totalDays}
            selectedPlatform={heatmapPlatform}
            onPlatformChange={setHeatmapPlatform}
            availablePlatforms={platformsWithData.map((p) => p.platform)}
          />

          {/* Rating History & Platform Rankings */}
          <div className="grid grid-cols-1 xl:grid-cols-12 gap-6 items-start">
            <div className="xl:col-span-8">
              <RatingHeroChart
                history={history}
                isLoading={historyLoading}
                selectedPlatform={selectedPlatform}
                onPlatformChange={setSelectedPlatform}
                platformsWithData={platformsWithData}
                dateRange={dateRange}
                onDateRangeChange={setDateRange}
              />
            </div>

            <div className="xl:col-span-4">
              <ContestRankingsCard platforms={platforms} />
            </div>
          </div>

          {/* Platform Distribution & DSA Difficulty Breakdown */}
          <PlatformDistributionDonut
            platforms={platforms}
            totalEasySolved={summary?.totalEasySolved}
            totalMediumSolved={summary?.totalMediumSolved}
            totalHardSolved={summary?.totalHardSolved}
            hasDifficultyBreakdown={summary?.hasDifficultyBreakdown}
          />

          {/* Contest History Table */}
          {selectedPlatform && history && history.entries.length > 0 && (
            <ContestHistoryTable
              entries={history.entries}
              selectedPlatform={selectedPlatform}
            />
          )}
        </div>
      )}
    </div>
  );
}
