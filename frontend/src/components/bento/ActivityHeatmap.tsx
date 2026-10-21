import { useState, useMemo } from 'react';
import type { HeatmapEntry } from '../../api/contests';
import { PLATFORM_LABELS, PLATFORM_COLORS } from '../../types/contest';
import type { Platform } from '../../types/contest';
import { toUTCDateString } from '../../lib/dateUtils';

interface ActivityHeatmapProps {
  entries: HeatmapEntry[];
  currentStreak: number;
  longestStreak: number;
  totalDays: number;
  selectedPlatform: string;
  onPlatformChange: (platform: string) => void;
  availablePlatforms: string[];
}

export function ActivityHeatmap({
  entries,
  currentStreak,
  longestStreak,
  totalDays,
  selectedPlatform,
  onPlatformChange,
  availablePlatforms,
}: ActivityHeatmapProps) {
  const [hoveredCell, setHoveredCell] = useState<{
    date: string;
    entry?: HeatmapEntry;
    x: number;
    y: number;
  } | null>(null);

  const [selectedDate, setSelectedDate] = useState<string | null>(null);

  const entryMap = useMemo(() => {
    return new Map<string, HeatmapEntry>(entries.map((e) => [e.date, e]));
  }, [entries]);

  const totalActivity = useMemo(() => entries.reduce((acc, curr) => acc + curr.count, 0), [entries]);
  const totalProblems = useMemo(() => entries.reduce((acc, curr) => acc + (curr.problemsSolved ?? (curr.details?.filter(d => d.type === 'PROBLEMS').reduce((s, d) => s + (d.count ?? 0), 0) ?? 0)), 0), [entries]);
  const totalContests = useMemo(() => entries.reduce((acc, curr) => acc + (curr.contestsAttended ?? (curr.details?.filter(d => d.type === 'CONTEST').length ?? 0)), 0), [entries]);
  const maxCount = useMemo(() => Math.max(...entries.map((e) => e.count), 1), [entries]);

  // Selected date details
  const inspectedEntry = selectedDate ? entryMap.get(selectedDate) : null;

  // Generate 52-53 weeks aligned to UTC
  const now = new Date();
  const todayUTC = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  const startUTC = todayUTC - 364 * 86400000;
  const startDate = new Date(startUTC);
  const dayOfWeek = startDate.getUTCDay();
  const adjustedStart = startUTC - dayOfWeek * 86400000;

  const weeks: { date: Date; dateStr: string; entry?: HeatmapEntry }[][] = [];
  let currentWeek: { date: Date; dateStr: string; entry?: HeatmapEntry }[] = [];

  for (let ts = adjustedStart; ts <= todayUTC; ts += 86400000) {
    const d = new Date(ts);
    const dateStr = toUTCDateString(d);
    currentWeek.push({ date: d, dateStr, entry: entryMap.get(dateStr) });
    if (currentWeek.length === 7) {
      weeks.push(currentWeek);
      currentWeek = [];
    }
  }
  if (currentWeek.length > 0) weeks.push(currentWeek);

  const cellSize = 13;
  const gap = 3;

  // Month labels
  const months: { label: string; weekIdx: number }[] = [];
  let lastMonth = -1;
  weeks.forEach((week, wi) => {
    const firstDay = week[0];
    const m = firstDay.date.getUTCMonth();
    if (m !== lastMonth) {
      months.push({
        label: firstDay.date.toLocaleDateString(undefined, { month: 'short' }),
        weekIdx: wi,
      });
      lastMonth = m;
    }
  });

  const getCellColor = (count: number) => {
    if (count === 0) return 'var(--bg-hover)';
    const ratio = count / maxCount;
    if (ratio <= 0.25) return 'color-mix(in srgb, var(--accent) 30%, var(--bg-hover))';
    if (ratio <= 0.5) return 'color-mix(in srgb, var(--accent) 55%, var(--bg-hover))';
    if (ratio <= 0.75) return 'color-mix(in srgb, var(--accent) 80%, var(--bg-hover))';
    return 'var(--accent)';
  };

  return (
    <div className="bento-card p-5 relative">
      {/* Top Header & Stats */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b" style={{ borderColor: 'var(--border)' }}>
        <div>
          <div className="flex items-center gap-2">
            <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: 'var(--accent)' }} />
            <h3 className="text-xs font-bold uppercase tracking-wider font-mono-editorial" style={{ color: 'var(--text-primary)' }}>
              Unified Activity Timeline
            </h3>
          </div>
          <p className="text-[11px] mt-0.5" style={{ color: 'var(--text-muted)' }}>
            Combines verified problems solved & contest attendances across all connected platforms
          </p>
        </div>

        {/* Platform Filter Buttons */}
        <div className="flex items-center gap-1 overflow-x-auto py-0.5 text-xs">
          <button
            type="button"
            onClick={() => onPlatformChange('ALL')}
            className="rounded px-2.5 py-1 text-xs font-mono-editorial font-semibold transition-all shrink-0"
            style={{
              backgroundColor: selectedPlatform === 'ALL' ? 'var(--btn-primary-bg)' : 'var(--bg-hover)',
              color: selectedPlatform === 'ALL' ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
            }}
          >
            ALL
          </button>
          {availablePlatforms.map((p) => (
            <button
              key={p}
              type="button"
              onClick={() => onPlatformChange(p)}
              className="rounded px-2.5 py-1 text-xs font-mono-editorial font-semibold transition-all shrink-0"
              style={{
                backgroundColor: selectedPlatform === p ? 'var(--btn-primary-bg)' : 'var(--bg-hover)',
                color: selectedPlatform === p ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
              }}
            >
              {PLATFORM_LABELS[p as Platform] ?? p}
            </button>
          ))}
        </div>
      </div>

      {/* Aggregate Counts Bar */}
      <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 py-3 border-b text-xs font-mono-editorial" style={{ borderColor: 'var(--border-subtle)' }}>
        <div>
          <span className="text-[10px] uppercase font-bold tracking-wider" style={{ color: 'var(--text-muted)' }}>Total Activity</span>
          <p className="text-base font-black tabular-nums mt-0.5" style={{ color: 'var(--text-primary)' }}>
            {totalActivity.toLocaleString()}
          </p>
        </div>
        <div>
          <span className="text-[10px] uppercase font-bold tracking-wider" style={{ color: 'var(--text-muted)' }}>Problems Solved</span>
          <p className="text-base font-black tabular-nums mt-0.5" style={{ color: 'var(--text-primary)' }}>
            {totalProblems.toLocaleString()}
          </p>
        </div>
        <div>
          <span className="text-[10px] uppercase font-bold tracking-wider" style={{ color: 'var(--text-muted)' }}>Contests Attended</span>
          <p className="text-base font-black tabular-nums mt-0.5" style={{ color: 'var(--text-primary)' }}>
            {totalContests.toLocaleString()}
          </p>
        </div>
        <div>
          <span className="text-[10px] uppercase font-bold tracking-wider" style={{ color: 'var(--text-muted)' }}>Active Days</span>
          <p className="text-base font-black tabular-nums mt-0.5" style={{ color: 'var(--text-primary)' }}>
            {totalDays} <span className="text-[11px] font-normal" style={{ color: 'var(--text-muted)' }}>days</span>
          </p>
        </div>
        <div>
          <span className="text-[10px] uppercase font-bold tracking-wider" style={{ color: 'var(--text-muted)' }}>Streak (Curr / Max)</span>
          <p className="text-base font-black tabular-nums mt-0.5" style={{ color: 'var(--accent)' }}>
            {currentStreak}d <span className="text-[11px] font-normal" style={{ color: 'var(--text-muted)' }}>/ {longestStreak}d</span>
          </p>
        </div>
      </div>

      {/* Calendar Area */}
      <div className="mt-4 overflow-x-auto pb-2 scrollbar-thin">
        <div style={{ minWidth: weeks.length * (cellSize + gap) + 32 }}>
          {/* Months Row */}
          <div className="relative flex select-none" style={{ paddingLeft: 28, height: 16 }}>
            {months.map((m, i) => (
              <div
                key={i}
                className="absolute text-[10px] font-medium"
                style={{ color: 'var(--text-muted)', left: 28 + m.weekIdx * (cellSize + gap) }}
              >
                {m.label}
              </div>
            ))}
          </div>

          <div className="mt-2 flex gap-1">
            {/* Weekdays Column */}
            <div className="flex flex-col gap-[3px] pr-1.5 select-none">
              {['S', 'M', 'T', 'W', 'T', 'F', 'S'].map((day, i) => (
                <div
                  key={i}
                  className="flex items-center justify-end text-[9px] font-semibold"
                  style={{ height: cellSize, width: 18, color: 'var(--text-muted)' }}
                >
                  {i % 2 === 1 ? day : ''}
                </div>
              ))}
            </div>

            {/* Weeks Grid */}
            <div className="flex gap-[3px]">
              {weeks.map((week, wi) => (
                <div key={wi} className="flex flex-col gap-[3px]">
                  {week.map((day, di) => {
                    const count = day.entry?.count ?? 0;
                    const bg = getCellColor(count);
                    const isSelected = selectedDate === day.dateStr;

                    return (
                      <button
                        key={di}
                        type="button"
                        className="rounded-[3px] cursor-pointer transition-transform hover:scale-125 hover:z-10 relative focus:outline-none"
                        style={{
                          width: cellSize,
                          height: cellSize,
                          backgroundColor: bg,
                          outline: isSelected ? '2px solid var(--accent)' : 'none',
                          outlineOffset: 1,
                        }}
                        onClick={() => setSelectedDate(isSelected ? null : day.dateStr)}
                        onMouseEnter={(e) => {
                          const rect = e.currentTarget.getBoundingClientRect();
                          setHoveredCell({
                            date: day.dateStr,
                            entry: day.entry,
                            x: rect.left + rect.width / 2,
                            y: rect.top,
                          });
                        }}
                        onMouseLeave={() => setHoveredCell(null)}
                        aria-label={`${day.dateStr}: ${count} activities`}
                      />
                    );
                  })}
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Floating Hover Tooltip */}
      {hoveredCell && (
        <div
          className="fixed z-50 pointer-events-none -translate-x-1/2 -translate-y-full mb-2 rounded-md px-3 py-2 text-xs shadow-xl border"
          style={{
            left: hoveredCell.x,
            top: hoveredCell.y - 8,
            backgroundColor: 'var(--bg-card)',
            borderColor: 'var(--border)',
            color: 'var(--text-primary)',
            minWidth: 160,
          }}
        >
          <div className="font-bold flex items-center justify-between gap-3 font-mono-code">
            <span>{new Date(hoveredCell.date + 'T00:00:00Z').toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' })}</span>
            <span className="font-black tabular-nums" style={{ color: (hoveredCell.entry?.count ?? 0) > 0 ? 'var(--accent)' : 'var(--text-muted)' }}>
              {hoveredCell.entry?.count ?? 0} {hoveredCell.entry?.count === 1 ? 'activity' : 'activities'}
            </span>
          </div>

          {hoveredCell.entry && hoveredCell.entry.count > 0 && (
            <div className="mt-1 pt-1 border-t space-y-0.5 text-[11px] font-mono-code" style={{ borderColor: 'var(--border-subtle)' }}>
              {(hoveredCell.entry.problemsSolved ?? 0) > 0 && (
                <div className="flex items-center justify-between" style={{ color: 'var(--text-secondary)' }}>
                  <span>Problems solved:</span>
                  <span className="font-bold tabular-nums" style={{ color: 'var(--text-primary)' }}>{hoveredCell.entry.problemsSolved}</span>
                </div>
              )}
              {(hoveredCell.entry.contestsAttended ?? 0) > 0 && (
                <div className="flex items-center justify-between" style={{ color: 'var(--accent)' }}>
                  <span>Contests attended:</span>
                  <span className="font-bold tabular-nums">{hoveredCell.entry.contestsAttended}</span>
                </div>
              )}
              {hoveredCell.entry.platforms && Object.keys(hoveredCell.entry.platforms).length > 0 && (
                <div className="pt-1 flex flex-wrap gap-1">
                  {Object.entries(hoveredCell.entry.platforms).map(([plat, pData]) => (
                    <span
                      key={plat}
                      className="px-1.5 py-0.5 rounded text-[9px] font-semibold"
                      style={{
                        backgroundColor: (PLATFORM_COLORS[plat as Platform] ?? '#8B7CFF') + '20',
                        color: PLATFORM_COLORS[plat as Platform] ?? 'var(--accent)',
                      }}
                    >
                      {PLATFORM_LABELS[plat as Platform] ?? plat}
                      {(pData.problemsSolved ?? 0) > 0 ? ` (${pData.problemsSolved}p)` : ''}
                      {(pData.contestsAttended ?? 0) > 0 ? ` (${pData.contestsAttended}c)` : ''}
                    </span>
                  ))}
                </div>
              )}
            </div>
          )}
          <div className="mt-1 text-[9px]" style={{ color: 'var(--text-muted)' }}>
            Click cell to inspect details
          </div>
        </div>
      )}

      {/* Selected Date Detail Inspection Panel */}
      {selectedDate && (
        <div
          className="mt-4 p-4 rounded-md border animate-in fade-in slide-in-from-top-1 duration-150"
          style={{ backgroundColor: 'var(--bg-card-subtle)', borderColor: 'var(--border)' }}
        >
          <div className="flex items-center justify-between pb-2 border-b" style={{ borderColor: 'var(--border-subtle)' }}>
            <div className="flex items-center gap-2">
              <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: 'var(--accent)' }} />
              <span className="text-xs font-bold font-mono-code uppercase tracking-wider" style={{ color: 'var(--text-primary)' }}>
                Activity Detail: {new Date(selectedDate + 'T00:00:00Z').toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' })}
              </span>
            </div>
            <button
              type="button"
              onClick={() => setSelectedDate(null)}
              className="p-1 rounded hover:bg-[var(--bg-hover)] text-xs"
              style={{ color: 'var(--text-muted)' }}
              title="Close details"
            >
              ✕
            </button>
          </div>

          {inspectedEntry && inspectedEntry.count > 0 ? (
            <div className="mt-3 space-y-2">
              <div className="flex flex-wrap items-center gap-4 text-xs font-mono-code">
                <span className="font-semibold" style={{ color: 'var(--text-secondary)' }}>
                  Total: <strong style={{ color: 'var(--text-primary)' }}>{inspectedEntry.count}</strong>
                </span>
                {(inspectedEntry.problemsSolved ?? 0) > 0 && (
                  <span className="font-semibold" style={{ color: 'var(--text-secondary)' }}>
                    Problems: <strong style={{ color: 'var(--text-primary)' }}>{inspectedEntry.problemsSolved}</strong>
                  </span>
                )}
                {(inspectedEntry.contestsAttended ?? 0) > 0 && (
                  <span className="font-semibold" style={{ color: 'var(--accent)' }}>
                    Contests: <strong>{inspectedEntry.contestsAttended}</strong>
                  </span>
                )}
              </div>

              {/* Event details list */}
              {inspectedEntry.details && inspectedEntry.details.length > 0 ? (
                <div className="mt-2 space-y-1.5">
                  {inspectedEntry.details.map((item, idx) => {
                    const platColor = PLATFORM_COLORS[item.platform as Platform] ?? 'var(--accent)';
                    return (
                      <div
                        key={idx}
                        className="flex items-center justify-between p-2 rounded border text-xs"
                        style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border-subtle)' }}
                      >
                        <div className="flex items-center gap-2">
                          <span
                            className="px-1.5 py-0.5 rounded text-[10px] font-bold font-mono-code"
                            style={{ backgroundColor: platColor + '20', color: platColor }}
                          >
                            {PLATFORM_LABELS[item.platform as Platform] ?? item.platform}
                          </span>
                          <span className="font-medium" style={{ color: 'var(--text-primary)' }}>
                            {item.type === 'CONTEST' ? (item.name || 'Ranked Contest') : `${item.count ?? 1} problems solved`}
                          </span>
                        </div>

                        <div className="flex items-center gap-2 text-[11px] font-mono-code" style={{ color: 'var(--text-muted)' }}>
                          {item.type === 'CONTEST' && item.rank != null && item.rank > 0 && (
                            <span>Rank #{item.rank.toLocaleString()}</span>
                          )}
                          {item.type === 'CONTEST' && item.ratingAfter != null && (
                            <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                              Rating: {item.ratingAfter}
                            </span>
                          )}
                          {item.type === 'PROBLEMS' && (
                            <span className="font-bold" style={{ color: 'var(--accent)' }}>✓ Solved</span>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="text-xs mt-2 font-mono-code" style={{ color: 'var(--text-muted)' }}>
                  Platform breakdown: {Object.entries(inspectedEntry.platforms ?? {}).map(([p, d]) => `${PLATFORM_LABELS[p as Platform] ?? p} (${d.problemsSolved ?? 0}p, ${d.contestsAttended ?? 0}c)`).join(', ')}
                </div>
              )}
            </div>
          ) : (
            <p className="mt-2 text-xs font-mono-code" style={{ color: 'var(--text-muted)' }}>
              No recorded problem submissions or contest attendances on this date.
            </p>
          )}
        </div>
      )}

      {/* Legend & Subtext */}
      <div className="mt-3 flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-[11px]" style={{ color: 'var(--text-muted)' }}>
        <div className="flex items-center gap-2 font-mono-code text-[10px]">
          <span>UTC TRACKING</span>
          <span>•</span>
          <span className="hidden sm:inline">CLICK CELL TO INSPECT RECORD</span>
          <span className="sm:hidden">SWIPE FOR TIMELINE</span>
        </div>
        <div className="flex items-center gap-1.5 self-end sm:self-auto font-mono-code text-[10px]">
          <span>LESS</span>
          {[
            'var(--bg-hover)',
            'color-mix(in srgb, var(--accent) 25%, var(--bg-hover))',
            'color-mix(in srgb, var(--accent) 50%, var(--bg-hover))',
            'color-mix(in srgb, var(--accent) 75%, var(--bg-hover))',
            'var(--accent)',
          ].map((c, i) => (
            <div
              key={i}
              className="rounded-[2px]"
              style={{ width: cellSize - 2, height: cellSize - 2, backgroundColor: c }}
            />
          ))}
          <span>MORE</span>
        </div>
      </div>
    </div>
  );
}
