import { useMemo } from 'react';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts';
import type { RatingHistoryResponse, RatingEntry, PlatformStat, Platform } from '../../types/contest';
import { PLATFORM_LABELS, PLATFORM_COLORS } from '../../types/contest';

interface RatingHeroChartProps {
  history: RatingHistoryResponse | undefined;
  isLoading: boolean;
  selectedPlatform: string | null;
  onPlatformChange: (platform: string) => void;
  platformsWithData: PlatformStat[];
  dateRange: number | undefined;
  onDateRangeChange: (days: number | undefined) => void;
}

const DATE_RANGES = [
  { label: '30D', days: 30 },
  { label: '90D', days: 90 },
  { label: '6M', days: 180 },
  { label: '1Y', days: 365 },
  { label: 'All', days: undefined },
];

export function RatingHeroChart({
  history,
  isLoading,
  selectedPlatform,
  onPlatformChange,
  platformsWithData,
  dateRange,
  onDateRangeChange,
}: RatingHeroChartProps) {
  // Overall mode data preparation
  const overallChartData = useMemo(() => {
    if (selectedPlatform !== 'OVERALL' || !history?.entries.length) return null;

    const uniquePlatforms = [...new Set(history.entries.map((e) => e.platform).filter(Boolean))] as string[];

    const mergedData: Record<string, unknown>[] = [];
    for (const entry of history.entries) {
      mergedData.push({
        date: new Date(entry.contestDate).getTime(),
        dateLabel: new Date(entry.contestDate).toLocaleDateString(undefined, {
          month: 'short',
          day: 'numeric',
        }),
        [`${entry.platform}_rating`]: entry.ratingAfter,
        [`${entry.platform}_name`]: entry.contestName,
        [`${entry.platform}_rank`]: entry.rankInContest,
        [`${entry.platform}_before`]: entry.ratingBefore,
      });
    }

    return { mergedData, uniquePlatforms };
  }, [selectedPlatform, history]);

  // Find latest entry for hero metrics banner matching Screenshot 2
  const latestEntry: RatingEntry | undefined = useMemo(() => {
    if (!history?.entries?.length) return undefined;
    return history.entries[history.entries.length - 1];
  }, [history]);

  const activeColor =
    selectedPlatform && selectedPlatform !== 'OVERALL'
      ? PLATFORM_COLORS[selectedPlatform as Platform] ?? 'var(--accent)'
      : 'var(--accent)';

  return (
    <div className="bento-card p-6 space-y-4">
      {/* Top Banner: Latest Rating & Contest context (Inspired by Screenshot 1 & 2) */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b" style={{ borderColor: 'var(--border)' }}>
        <div>
          <div className="flex items-baseline gap-3">
            <span className="text-xs font-semibold uppercase tracking-wider font-mono-editorial" style={{ color: 'var(--text-muted)' }}>
              Rating
            </span>
            <span className="text-3xl font-black tabular-nums tracking-tight font-mono-editorial" style={{ color: 'var(--text-primary)' }}>
              {latestEntry?.ratingAfter ?? history?.currentRating ?? '—'}
            </span>
            {latestEntry?.ratingBefore != null && (
              <span
                className="text-xs font-bold tabular-nums font-mono-editorial"
                style={{
                  color:
                    latestEntry.ratingAfter >= latestEntry.ratingBefore
                      ? 'var(--success)'
                      : 'var(--danger)',
                }}
              >
                {latestEntry.ratingAfter >= latestEntry.ratingBefore ? '+' : ''}
                {latestEntry.ratingAfter - latestEntry.ratingBefore}
              </span>
            )}
          </div>

          {latestEntry && (
            <div className="mt-1 flex flex-wrap items-center gap-2 text-xs" style={{ color: 'var(--text-secondary)' }}>
              <span className="font-mono-editorial">{new Date(latestEntry.contestDate).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })}</span>
              <span>•</span>
              <span className="font-medium truncate max-w-xs">{latestEntry.contestName}</span>
              {latestEntry.rankInContest && (
                <>
                  <span>•</span>
                  <span className="tabular-nums font-mono-editorial" style={{ color: 'var(--text-muted)' }}>
                    Rank #{latestEntry.rankInContest.toLocaleString()}
                  </span>
                </>
              )}
            </div>
          )}
        </div>

        {/* Platform Tabs & Date Filters */}
        <div className="flex flex-wrap items-center gap-3">
          {/* Platform Pills */}
          <div className="flex flex-wrap gap-1 p-1 rounded-md border" style={{ backgroundColor: 'var(--bg-hover)', borderColor: 'var(--border-subtle)' }}>
            <button
              onClick={() => onPlatformChange('OVERALL')}
              className="rounded px-2.5 py-1 text-xs font-semibold transition-all"
              style={{
                backgroundColor: selectedPlatform === 'OVERALL' ? 'var(--bg-card)' : 'transparent',
                color: selectedPlatform === 'OVERALL' ? 'var(--text-primary)' : 'var(--text-muted)',
                boxShadow: selectedPlatform === 'OVERALL' ? 'var(--card-shadow)' : 'none',
              }}
            >
              Overall
            </button>
            {platformsWithData.map((p) => (
              <button
                key={p.platform}
                onClick={() => onPlatformChange(p.platform)}
                className="rounded px-2.5 py-1 text-xs font-semibold transition-all"
                style={{
                  backgroundColor: selectedPlatform === p.platform ? 'var(--bg-card)' : 'transparent',
                  color:
                    selectedPlatform === p.platform
                      ? PLATFORM_COLORS[p.platform as Platform] ?? 'var(--text-primary)'
                      : 'var(--text-muted)',
                  boxShadow: selectedPlatform === p.platform ? 'var(--card-shadow)' : 'none',
                }}
              >
                {PLATFORM_LABELS[p.platform as Platform] ?? p.platform}
              </button>
            ))}
          </div>

          {/* Date Range Buttons */}
          <div className="flex gap-0.5 rounded-md p-0.5 border" style={{ backgroundColor: 'var(--bg-hover)', borderColor: 'var(--border-subtle)' }}>
            {DATE_RANGES.map((r) => (
              <button
                key={r.label}
                onClick={() => onDateRangeChange(r.days)}
                className="rounded px-2 py-1 text-[11px] font-medium font-mono-code transition-colors"
                style={{
                  backgroundColor: dateRange === r.days ? 'var(--bg-card)' : 'transparent',
                  color: dateRange === r.days ? 'var(--text-primary)' : 'var(--text-muted)',
                  boxShadow: dateRange === r.days ? '0 1px 2px rgba(0,0,0,.05)' : 'none',
                }}
              >
                {r.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Chart Canvas */}
      <div className="pt-2">
        {isLoading && (
          <div className="flex h-[280px] items-center justify-center rounded-md" style={{ backgroundColor: 'var(--bg-hover)' }}>
            <span className="text-xs font-medium" style={{ color: 'var(--text-muted)' }}>
              Loading rating progression...
            </span>
          </div>
        )}

        {!isLoading && selectedPlatform === 'OVERALL' && overallChartData && (
          <div className="h-[280px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={overallChartData.mergedData} margin={{ top: 10, right: 10, left: -15, bottom: 0 }}>
                <defs>
                  {overallChartData.uniquePlatforms.map((p) => (
                    <linearGradient key={p} id={`grad-hero-${p}`} x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor={PLATFORM_COLORS[p as Platform] ?? 'var(--accent)'} stopOpacity={0.25} />
                      <stop offset="100%" stopColor={PLATFORM_COLORS[p as Platform] ?? 'var(--accent)'} stopOpacity={0.0} />
                    </linearGradient>
                  ))}
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" opacity={0.6} vertical={false} />
                <XAxis
                  dataKey="dateLabel"
                  tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
                  stroke="none"
                  tickLine={false}
                  interval="preserveStartEnd"
                />
                <YAxis
                  tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
                  stroke="none"
                  tickLine={false}
                  width={45}
                  domain={['auto', 'auto']}
                />
                <Tooltip
                  contentStyle={{
                    backgroundColor: 'var(--bg-card)',
                    border: '1px solid var(--border)',
                    borderRadius: '6px',
                    fontSize: '12px',
                    boxShadow: 'var(--card-shadow)',
                  }}
                  formatter={(val, name) => {
                    const platformKey = String(name).replace('_rating', '');
                    return [String(val), PLATFORM_LABELS[platformKey as Platform] ?? platformKey];
                  }}
                />
                <Legend
                  iconType="circle"
                  iconSize={8}
                  wrapperStyle={{ fontSize: '11px', paddingTop: '10px' }}
                  formatter={(val: string) => {
                    const key = val.replace('_rating', '');
                    return PLATFORM_LABELS[key as Platform] ?? key;
                  }}
                />
                {overallChartData.uniquePlatforms.map((p) => (
                  <Area
                    key={p}
                    type="monotone"
                    dataKey={`${p}_rating`}
                    stroke={PLATFORM_COLORS[p as Platform] ?? '#6366f1'}
                    strokeWidth={2.5}
                    fill={`url(#grad-hero-${p})`}
                    dot={false}
                    activeDot={{ r: 5, strokeWidth: 0, fill: PLATFORM_COLORS[p as Platform] ?? '#6366f1' }}
                    connectNulls
                  />
                ))}
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}

        {!isLoading && selectedPlatform !== 'OVERALL' && history && history.entries.length > 0 && (
          <div className="h-[280px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart
                data={history.entries.map((e) => ({
                  ...e,
                  dateLabel: new Date(e.contestDate).toLocaleDateString(undefined, {
                    month: 'short',
                    day: 'numeric',
                  }),
                }))}
                margin={{ top: 10, right: 10, left: -15, bottom: 0 }}
              >
                <defs>
                  <linearGradient id={`grad-single-${selectedPlatform}`} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={activeColor} stopOpacity={0.25} />
                    <stop offset="100%" stopColor={activeColor} stopOpacity={0.0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" opacity={0.6} vertical={false} />
                <XAxis
                  dataKey="dateLabel"
                  tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
                  stroke="none"
                  tickLine={false}
                  interval="preserveStartEnd"
                />
                <YAxis
                  tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
                  stroke="none"
                  tickLine={false}
                  width={45}
                  domain={['auto', 'auto']}
                />
                <Tooltip
                  content={({ payload }) => {
                    if (!payload?.length) return null;
                    const e = payload[0].payload as RatingEntry & { dateLabel: string };
                    const diff = e.ratingBefore != null ? e.ratingAfter - e.ratingBefore : null;
                    return (
                      <div
                        className="rounded border p-3 shadow-md"
                        style={{ backgroundColor: 'var(--bg-elevated)', borderColor: 'var(--border)' }}
                      >
                        <p className="font-semibold text-xs truncate max-w-xs" style={{ color: 'var(--text-primary)' }}>
                          {e.contestName ?? 'Contest'}
                        </p>
                        <p className="mt-0.5 text-[10px] font-mono-editorial" style={{ color: 'var(--text-muted)' }}>
                          {new Date(e.contestDate).toLocaleDateString()} {e.rankInContest && `• Rank #${e.rankInContest}`}
                        </p>
                        <div className="mt-2 flex items-baseline gap-2">
                          <span className="text-base font-black font-mono-editorial" style={{ color: activeColor }}>
                            {e.ratingAfter}
                          </span>
                          {diff != null && (
                            <span
                              className="text-xs font-bold tabular-nums font-mono-editorial"
                              style={{ color: diff >= 0 ? 'var(--success)' : 'var(--danger)' }}
                            >
                              {diff >= 0 ? '+' : ''}
                              {diff}
                            </span>
                          )}
                        </div>
                      </div>
                    );
                  }}
                />
                <Area
                  type="monotone"
                  dataKey="ratingAfter"
                  stroke={activeColor}
                  strokeWidth={2.5}
                  fill={`url(#grad-single-${selectedPlatform})`}
                  dot={{ fill: activeColor, r: 3, strokeWidth: 0 }}
                  activeDot={{ r: 5, strokeWidth: 0 }}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}

        {!isLoading && (!history || history.entries.length === 0) && (
          <div className="flex h-[280px] flex-col items-center justify-center rounded-md text-center" style={{ backgroundColor: 'var(--bg-hover)' }}>
            <p className="text-sm font-semibold" style={{ color: 'var(--text-secondary)' }}>
              No rating history recorded
            </p>
            <p className="mt-1 text-xs max-w-xs" style={{ color: 'var(--text-muted)' }}>
              Connect and synchronize your competitive programming profile to view historical performance trends.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
