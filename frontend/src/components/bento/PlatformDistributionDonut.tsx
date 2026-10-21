import { useState, useMemo } from 'react';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import type { PlatformStat, Platform } from '../../types/contest';
import { PLATFORM_LABELS, PLATFORM_COLORS } from '../../types/contest';

interface PlatformDistributionProps {
  platforms: PlatformStat[];
  totalEasySolved?: number | null;
  totalMediumSolved?: number | null;
  totalHardSolved?: number | null;
  hasDifficultyBreakdown?: boolean;
}

type DifficultyState =
  | {
      isAvailable: true;
      source: string;
      easy: number;
      medium: number;
      hard: number;
      total: number;
    }
  | {
      isAvailable: false;
      platform?: string;
      platformLabel?: string;
    };

export function PlatformDistributionDonut({
  platforms,
  totalEasySolved,
  totalMediumSolved,
  totalHardSolved,
  hasDifficultyBreakdown: overallHasDifficulty,
}: PlatformDistributionProps) {
  const [selectedTab, setSelectedTab] = useState<string>('ALL');

  // Chart data for platform contest distribution
  const chartData = useMemo(() => {
    return platforms
      .filter((p) => p.contestCount > 0)
      .map((p) => ({
        name: PLATFORM_LABELS[p.platform as Platform] ?? p.platform,
        value: p.contestCount,
        color: PLATFORM_COLORS[p.platform as Platform] ?? 'var(--accent)',
        platform: p.platform,
      }));
  }, [platforms]);

  const totalContests = useMemo(() => chartData.reduce((acc, curr) => acc + curr.value, 0), [chartData]);

  // Determine difficulty data based on selected filter
  const difficultyData: DifficultyState | null = useMemo(() => {
    if (selectedTab === 'ALL') {
      const hasDiff = overallHasDifficulty ?? platforms.some((p) => p.hasDifficultyBreakdown);
      if (!hasDiff) return null;

      const easy = totalEasySolved ?? platforms.reduce((s, p) => s + (p.easySolved ?? 0), 0);
      const medium = totalMediumSolved ?? platforms.reduce((s, p) => s + (p.mediumSolved ?? 0), 0);
      const hard = totalHardSolved ?? platforms.reduce((s, p) => s + (p.hardSolved ?? 0), 0);
      const total = easy + medium + hard;

      return {
        isAvailable: true,
        source: 'Verified LeetCode Profile',
        easy,
        medium,
        hard,
        total,
      };
    }

    const target = platforms.find((p) => p.platform === selectedTab);
    if (!target) return null;

    if (target.hasDifficultyBreakdown && (target.easySolved != null || target.mediumSolved != null || target.hardSolved != null)) {
      const easy = target.easySolved ?? 0;
      const medium = target.mediumSolved ?? 0;
      const hard = target.hardSolved ?? 0;
      return {
        isAvailable: true,
        source: PLATFORM_LABELS[target.platform as Platform] ?? target.platform,
        easy,
        medium,
        hard,
        total: easy + medium + hard,
      };
    }

    // Platform does not support Easy/Medium/Hard tiers
    return {
      isAvailable: false,
      platform: target.platform,
      platformLabel: PLATFORM_LABELS[target.platform as Platform] ?? target.platform,
    };
  }, [selectedTab, platforms, totalEasySolved, totalMediumSolved, totalHardSolved, overallHasDifficulty]);

  return (
    <div className="bento-card p-5 flex flex-col justify-between space-y-5">
      {/* Header with Title and Platform Filter */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b" style={{ borderColor: 'var(--border)' }}>
        <div>
          <div className="flex items-center gap-2">
            <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: 'var(--accent)' }} />
            <h3 className="text-xs font-bold uppercase tracking-wider" style={{ color: 'var(--text-primary)' }}>
              Platform Distribution & DSA Breakdown
            </h3>
          </div>
          <p className="text-[11px] mt-0.5" style={{ color: 'var(--text-muted)' }}>
            Contest attendance distribution alongside verified DSA problem-solving tiers
          </p>
        </div>

        {/* Platform Toggle Pill */}
        <div className="flex items-center gap-1 overflow-x-auto py-0.5 text-xs">
          <button
            type="button"
            onClick={() => setSelectedTab('ALL')}
            className="rounded px-2.5 py-1 text-xs font-mono-editorial font-medium transition-all shrink-0"
            style={{
              backgroundColor: selectedTab === 'ALL' ? 'var(--btn-primary-bg)' : 'var(--bg-hover)',
              color: selectedTab === 'ALL' ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
            }}
          >
            ALL
          </button>
          {platforms.map((p) => (
            <button
              key={p.platform}
              type="button"
              onClick={() => setSelectedTab(p.platform)}
              className="rounded px-2.5 py-1 text-xs font-mono-editorial font-medium transition-all shrink-0"
              style={{
                backgroundColor: selectedTab === p.platform ? 'var(--btn-primary-bg)' : 'var(--bg-hover)',
                color: selectedTab === p.platform ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
              }}
            >
              {PLATFORM_LABELS[p.platform as Platform] ?? p.platform}
            </button>
          ))}
        </div>
      </div>

      {/* Main Content Grid: Left side Donut Chart, Right side DSA Difficulty Progress */}
      <div className="grid grid-cols-1 md:grid-cols-12 gap-6 items-center">
        {/* Left Side: Contest Distribution Donut (5 cols) */}
        <div className="md:col-span-5 flex flex-col sm:flex-row md:flex-col items-center justify-center gap-4 p-3.5 rounded-md border" style={{ backgroundColor: 'var(--bg-card-subtle)', borderColor: 'var(--border-subtle)' }}>
          <div className="relative w-36 h-36 flex items-center justify-center shrink-0">
            {chartData.length > 0 ? (
              <>
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={chartData}
                      innerRadius={46}
                      outerRadius={62}
                      paddingAngle={3}
                      dataKey="value"
                      stroke="none"
                    >
                      {chartData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={entry.color} />
                      ))}
                    </Pie>
                    <Tooltip
                      contentStyle={{
                        backgroundColor: 'var(--bg-card)',
                        borderColor: 'var(--border)',
                        borderRadius: '4px',
                        fontSize: '11px',
                        fontFamily: 'ui-monospace, monospace',
                        boxShadow: 'var(--card-shadow)',
                      }}
                      itemStyle={{ color: 'var(--text-primary)' }}
                    />
                  </PieChart>
                </ResponsiveContainer>
                <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                  <span className="text-xl font-bold font-mono tabular-nums" style={{ color: 'var(--text-primary)' }}>
                    {totalContests}
                  </span>
                  <span className="text-[9px] uppercase font-mono tracking-wider" style={{ color: 'var(--text-muted)' }}>
                    CONTESTS
                  </span>
                </div>
              </>
            ) : (
              <div className="flex flex-col items-center justify-center text-center p-2">
                <span className="text-xs font-mono" style={{ color: 'var(--text-muted)' }}>
                  NO DATA
                </span>
              </div>
            )}
          </div>

          {/* Compact Legend */}
          <div className="w-full space-y-1.5 text-xs">
            {chartData.map((item) => {
              const pct = totalContests > 0 ? Math.round((item.value / totalContests) * 100) : 0;
              return (
                <div key={item.name} className="flex items-center justify-between text-[11px] font-mono">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="h-1.5 w-1.5 rounded-full shrink-0" style={{ backgroundColor: item.color }} />
                    <span className="truncate" style={{ color: 'var(--text-primary)' }}>
                      {item.name}
                    </span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className="font-semibold tabular-nums" style={{ color: 'var(--text-secondary)' }}>
                      {item.value}
                    </span>
                    <span className="text-[10px] tabular-nums" style={{ color: 'var(--text-muted)' }}>
                      ({pct}%)
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Right Side: DSA Difficulty Breakdown (7 cols) */}
        <div className="md:col-span-7 space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold font-mono-editorial uppercase tracking-wider" style={{ color: 'var(--text-secondary)' }}>
              DSA Difficulty Tiers
            </span>
            {difficultyData && difficultyData.isAvailable && (
              <span className="text-[10px] font-mono-editorial px-2 py-0.5 rounded font-semibold" style={{ color: 'var(--accent)', backgroundColor: 'var(--accent-subtle)' }}>
                VERIFIED
              </span>
            )}
          </div>

          {difficultyData && difficultyData.isAvailable ? (
            <div className="space-y-3">
              {/* Summary Stats Row */}
              <div className="flex items-baseline justify-between p-3 rounded border" style={{ backgroundColor: 'var(--bg-card-subtle)', borderColor: 'var(--border-subtle)' }}>
                <div>
                  <span className="text-[10px] font-mono-editorial uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>
                    TOTAL SOLVED
                  </span>
                  <div className="text-2xl font-black font-mono-editorial tabular-nums mt-0.5" style={{ color: 'var(--text-primary)' }}>
                    {difficultyData.total.toLocaleString()}
                  </div>
                </div>
                <div className="text-right">
                  <span className="text-[10px] font-mono-editorial" style={{ color: 'var(--text-muted)' }}>
                    {difficultyData.source}
                  </span>
                </div>
              </div>

              {/* Easy Progress Bar */}
              <div className="space-y-1">
                <div className="flex items-center justify-between text-xs font-mono-editorial">
                  <span className="font-semibold flex items-center gap-1.5" style={{ color: 'var(--success)' }}>
                    <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: 'var(--success)' }} />
                    EASY
                  </span>
                  <div className="flex items-center gap-2">
                    <span className="font-bold tabular-nums" style={{ color: 'var(--text-primary)' }}>
                      {difficultyData.easy}
                    </span>
                    <span className="text-[10px] tabular-nums" style={{ color: 'var(--text-muted)' }}>
                      ({difficultyData.total > 0 ? Math.round((difficultyData.easy / difficultyData.total) * 100) : 0}%)
                    </span>
                  </div>
                </div>
                <div className="h-1.5 w-full rounded-full overflow-hidden" style={{ backgroundColor: 'var(--bg-hover)' }}>
                  <div
                    className="h-full rounded-full transition-all duration-300"
                    style={{
                      width: `${difficultyData.total > 0 ? (difficultyData.easy / difficultyData.total) * 100 : 0}%`,
                      backgroundColor: 'var(--success)',
                    }}
                  />
                </div>
              </div>

              {/* Medium Progress Bar */}
              <div className="space-y-1">
                <div className="flex items-center justify-between text-xs font-mono-editorial">
                  <span className="font-semibold flex items-center gap-1.5" style={{ color: 'var(--warning)' }}>
                    <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: 'var(--warning)' }} />
                    MEDIUM
                  </span>
                  <div className="flex items-center gap-2">
                    <span className="font-bold tabular-nums" style={{ color: 'var(--text-primary)' }}>
                      {difficultyData.medium}
                    </span>
                    <span className="text-[10px] tabular-nums" style={{ color: 'var(--text-muted)' }}>
                      ({difficultyData.total > 0 ? Math.round((difficultyData.medium / difficultyData.total) * 100) : 0}%)
                    </span>
                  </div>
                </div>
                <div className="h-1.5 w-full rounded-full overflow-hidden" style={{ backgroundColor: 'var(--bg-hover)' }}>
                  <div
                    className="h-full rounded-full transition-all duration-300"
                    style={{
                      width: `${difficultyData.total > 0 ? (difficultyData.medium / difficultyData.total) * 100 : 0}%`,
                      backgroundColor: 'var(--warning)',
                    }}
                  />
                </div>
              </div>

              {/* Hard Progress Bar */}
              <div className="space-y-1">
                <div className="flex items-center justify-between text-xs font-mono-editorial">
                  <span className="font-semibold flex items-center gap-1.5" style={{ color: 'var(--danger)' }}>
                    <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: 'var(--danger)' }} />
                    HARD
                  </span>
                  <div className="flex items-center gap-2">
                    <span className="font-bold tabular-nums" style={{ color: 'var(--text-primary)' }}>
                      {difficultyData.hard}
                    </span>
                    <span className="text-[10px] tabular-nums" style={{ color: 'var(--text-muted)' }}>
                      ({difficultyData.total > 0 ? Math.round((difficultyData.hard / difficultyData.total) * 100) : 0}%)
                    </span>
                  </div>
                </div>
                <div className="h-1.5 w-full rounded-full overflow-hidden" style={{ backgroundColor: 'var(--bg-hover)' }}>
                  <div
                    className="h-full rounded-full transition-all duration-300"
                    style={{
                      width: `${difficultyData.total > 0 ? (difficultyData.hard / difficultyData.total) * 100 : 0}%`,
                      backgroundColor: 'var(--danger)',
                    }}
                  />
                </div>
              </div>
            </div>
          ) : (
            /* Explicit Unavailable State */
            <div className="p-4 rounded border text-center space-y-2" style={{ backgroundColor: 'var(--bg-card-subtle)', borderColor: 'var(--border-subtle)' }}>
              <div className="mx-auto flex h-8 w-8 items-center justify-center rounded" style={{ backgroundColor: 'var(--bg-hover)', color: 'var(--text-muted)' }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="10" />
                  <line x1="12" y1="8" x2="12" y2="12" />
                  <line x1="12" y1="16" x2="12.01" y2="16" />
                </svg>
              </div>
              <h4 className="text-xs font-bold font-mono-editorial" style={{ color: 'var(--text-primary)' }}>
                {difficultyData?.platformLabel
                  ? `${difficultyData.platformLabel.toUpperCase()}: TIERED DIFFICULTY UNAVAILABLE`
                  : 'DSA DIFFICULTY CLASSIFICATION UNAVAILABLE'}
              </h4>
              <p className="text-[11px] leading-relaxed max-w-xs mx-auto" style={{ color: 'var(--text-muted)' }}>
                {difficultyData?.platform === 'CODEFORCES'
                  ? 'Codeforces utilizes numerical problem ratings (800–3500) rather than Easy/Medium/Hard tiers. Link LeetCode for categorized problem progress.'
                  : 'This platform does not expose public Easy/Medium/Hard problem statistics. Connect your LeetCode profile to view tiered difficulty breakdown.'}
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
