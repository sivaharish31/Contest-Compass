import { useState } from 'react';
import type { RatingEntry, Platform } from '../../types/contest';
import { PLATFORM_LABELS, PLATFORM_COLORS } from '../../types/contest';

interface ContestHistoryTableProps {
  entries: RatingEntry[];
  selectedPlatform: string | null;
}

export function ContestHistoryTable({ entries, selectedPlatform }: ContestHistoryTableProps) {
  const [showAll, setShowAll] = useState(false);

  const reversed = [...entries].reverse();
  const displayed = showAll ? reversed : reversed.slice(0, 10);

  return (
    <div className="bento-card overflow-hidden">
      <div className="flex items-center justify-between px-6 py-4 border-b" style={{ borderColor: 'var(--border)' }}>
        <div>
          <h3 className="text-sm font-semibold tracking-tight" style={{ color: 'var(--text-primary)' }}>
            Contest History
          </h3>
          <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
            {entries.length} recorded contest appearances
          </p>
        </div>
        {entries.length > 10 && (
          <button
            onClick={() => setShowAll(!showAll)}
            className="text-xs font-semibold hover:underline"
            style={{ color: 'var(--accent)' }}
          >
            {showAll ? 'Show Latest 10' : `View All (${entries.length})`}
          </button>
        )}
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs">
          <thead>
            <tr className="border-b" style={{ backgroundColor: 'var(--bg-card-subtle)', borderColor: 'var(--border)' }}>
              {selectedPlatform === 'OVERALL' && (
                <th className="px-6 py-3 font-semibold" style={{ color: 'var(--text-muted)' }}>
                  Platform
                </th>
              )}
              <th className="px-6 py-3 font-semibold" style={{ color: 'var(--text-muted)' }}>
                Contest
              </th>
              <th className="px-6 py-3 font-semibold" style={{ color: 'var(--text-muted)' }}>
                Date
              </th>
              <th className="px-6 py-3 font-semibold text-right" style={{ color: 'var(--text-muted)' }}>
                Rank
              </th>
              <th className="px-6 py-3 font-semibold text-right" style={{ color: 'var(--text-muted)' }}>
                Rating
              </th>
              <th className="px-6 py-3 font-semibold text-right" style={{ color: 'var(--text-muted)' }}>
                Change
              </th>
            </tr>
          </thead>
          <tbody className="divide-y" style={{ borderColor: 'var(--border-subtle)' }}>
            {displayed.map((e, idx) => {
              const diff = e.ratingBefore != null ? e.ratingAfter - e.ratingBefore : null;
              return (
                <tr
                  key={idx}
                  className="transition-colors hover:bg-[var(--bg-hover)]"
                >
                  {selectedPlatform === 'OVERALL' && (
                    <td className="px-6 py-3">
                      <span
                        className="rounded-md px-2 py-0.5 text-[10px] font-bold"
                        style={{
                          backgroundColor: (PLATFORM_COLORS[e.platform as Platform] ?? '#6366f1') + '15',
                          color: PLATFORM_COLORS[e.platform as Platform] ?? '#6366f1',
                        }}
                      >
                        {PLATFORM_LABELS[e.platform as Platform] ?? e.platform}
                      </span>
                    </td>
                  )}
                  <td className="px-6 py-3 font-semibold max-w-xs truncate" style={{ color: 'var(--text-primary)' }}>
                    {e.contestName ?? 'Contest'}
                  </td>
                  <td className="px-6 py-3 tabular-nums" style={{ color: 'var(--text-secondary)' }}>
                    {new Date(e.contestDate).toLocaleDateString(undefined, {
                      month: 'short',
                      day: 'numeric',
                      year: 'numeric',
                    })}
                  </td>
                  <td className="px-6 py-3 text-right tabular-nums" style={{ color: 'var(--text-secondary)' }}>
                    {e.rankInContest ? `#${e.rankInContest.toLocaleString()}` : '—'}
                  </td>
                  <td className="px-6 py-3 text-right font-black tabular-nums" style={{ color: 'var(--text-primary)' }}>
                    {e.ratingAfter.toLocaleString()}
                  </td>
                  <td className="px-6 py-3 text-right font-bold tabular-nums">
                    {diff != null ? (
                      <span style={{ color: diff >= 0 ? 'var(--success)' : 'var(--danger)' }}>
                        {diff >= 0 ? '+' : ''}
                        {diff}
                      </span>
                    ) : (
                      <span style={{ color: 'var(--text-muted)' }}>—</span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
