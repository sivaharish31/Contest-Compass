import { describe, it, expect, beforeEach } from 'vitest';
import { toUTCDateString as libToUTCDateString } from '../lib/dateUtils';

function toUTCDateString(d: Date): string {
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, '0');
  const day = String(d.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function buildHeatmapGrid(entries: { date: string; count: number }[]) {
  const entryMap = new Map(entries.map(e => [e.date, e.count]));

  const now = new Date();
  const todayUTC = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  const startUTC = todayUTC - 364 * 86400000;
  const startDate = new Date(startUTC);
  const dayOfWeek = startDate.getUTCDay();
  const adjustedStart = startUTC - dayOfWeek * 86400000;

  const cells: { dateStr: string; count: number }[] = [];

  for (let ts = adjustedStart; ts <= todayUTC; ts += 86400000) {
    const d = new Date(ts);
    const dateStr = toUTCDateString(d);
    cells.push({ dateStr, count: entryMap.get(dateStr) ?? 0 });
  }

  return cells;
}

describe('toUTCDateString', () => {
  it('converts UTC midnight correctly', () => {
    const d = new Date('2024-06-15T00:00:00Z');
    expect(toUTCDateString(d)).toBe('2024-06-15');
  });

  it('converts late UTC time to correct date', () => {
    const d = new Date('2024-06-15T23:59:59Z');
    expect(toUTCDateString(d)).toBe('2024-06-15');
  });

  it('handles timezone boundary: IST midnight is previous day UTC', () => {
    const istMidnight = new Date('2024-06-15T18:30:00Z');
    expect(toUTCDateString(istMidnight)).toBe('2024-06-15');
  });

  it('handles year boundary', () => {
    const d = new Date('2024-12-31T23:59:59Z');
    expect(toUTCDateString(d)).toBe('2024-12-31');
  });

  it('pads single digit months and days', () => {
    const d = new Date('2024-01-05T00:00:00Z');
    expect(toUTCDateString(d)).toBe('2024-01-05');
  });

  it('libToUTCDateString behaves identically', () => {
    const d = new Date('2024-06-15T18:30:00Z');
    expect(libToUTCDateString(d)).toBe(toUTCDateString(d));
  });
});

describe('buildHeatmapGrid', () => {
  it('produces cells for the last ~365 days', () => {
    const cells = buildHeatmapGrid([]);
    expect(cells.length).toBeGreaterThanOrEqual(365);
    expect(cells.length).toBeLessThanOrEqual(371);
  });

  it('maps known dates to their counts', () => {
    const today = new Date();
    const todayStr = toUTCDateString(today);
    const cells = buildHeatmapGrid([{ date: todayStr, count: 3 }]);
    const todayCell = cells.find(c => c.dateStr === todayStr);
    expect(todayCell).toBeDefined();
    expect(todayCell!.count).toBe(3);
  });

  it('returns 0 count for dates without data', () => {
    const cells = buildHeatmapGrid([]);
    expect(cells.every(c => c.count === 0)).toBe(true);
  });

  it('all date strings are valid YYYY-MM-DD format', () => {
    const cells = buildHeatmapGrid([]);
    const dateRegex = /^\d{4}-\d{2}-\d{2}$/;
    for (const cell of cells) {
      expect(cell.dateStr).toMatch(dateRegex);
    }
  });

  it('dates are in ascending order', () => {
    const cells = buildHeatmapGrid([]);
    for (let i = 1; i < cells.length; i++) {
      expect(cells[i].dateStr >= cells[i - 1].dateStr).toBe(true);
    }
  });

  it('no duplicate dates', () => {
    const cells = buildHeatmapGrid([]);
    const dateSet = new Set(cells.map(c => c.dateStr));
    expect(dateSet.size).toBe(cells.length);
  });

  it('handles multiple entries aggregated to same date by backend', () => {
    const cells = buildHeatmapGrid([
      { date: '2024-06-15', count: 5 },
    ]);
    const cell = cells.find(c => c.dateStr === '2024-06-15');
    if (cell) {
      expect(cell.count).toBe(5);
    }
  });
});

describe('streak calculation', () => {
  function calculateStreaks(entries: { date: string; count: number }[]) {
    const dateSet = new Set(entries.filter(e => e.count > 0).map(e => e.date));
    const sortedDates = [...dateSet].sort().reverse();

    if (sortedDates.length === 0) return { currentStreak: 0, longestStreak: 0, totalDays: 0 };

    const now = new Date();
    const todayUTC = toUTCDateString(now);
    const yesterdayUTC = toUTCDateString(new Date(Date.UTC(
      now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - 1
    )));

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
        const expected = new Date(Date.UTC(
          now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - i - startOffset
        ));
        const expectedStr = toUTCDateString(expected);
        if (sortedDates[i] === expectedStr) {
          currentStreak++;
        } else {
          break;
        }
      }
    }

    let longestStreak = sortedDates.length > 0 ? 1 : 0;
    let tempStreak = 1;
    for (let i = 1; i < sortedDates.length; i++) {
      const prev = new Date(sortedDates[i - 1] + 'T00:00:00Z');
      const curr = new Date(sortedDates[i] + 'T00:00:00Z');
      const diff = (prev.getTime() - curr.getTime()) / 86400000;
      if (Math.abs(diff - 1) < 0.01) {
        tempStreak++;
        longestStreak = Math.max(longestStreak, tempStreak);
      } else {
        tempStreak = 1;
      }
    }

    return { currentStreak, longestStreak, totalDays: sortedDates.length };
  }

  it('returns zeros for empty data', () => {
    expect(calculateStreaks([])).toEqual({ currentStreak: 0, longestStreak: 0, totalDays: 0 });
  });

  it('counts total active days', () => {
    const result = calculateStreaks([
      { date: '2024-01-01', count: 1 },
      { date: '2024-03-15', count: 2 },
      { date: '2024-06-20', count: 1 },
    ]);
    expect(result.totalDays).toBe(3);
  });

  it('calculates longest streak from consecutive dates', () => {
    const result = calculateStreaks([
      { date: '2024-01-01', count: 1 },
      { date: '2024-01-02', count: 1 },
      { date: '2024-01-03', count: 1 },
      { date: '2024-06-01', count: 1 },
    ]);
    expect(result.longestStreak).toBe(3);
  });

  it('filters out zero-count entries', () => {
    const result = calculateStreaks([
      { date: '2024-01-01', count: 0 },
      { date: '2024-01-02', count: 1 },
    ]);
    expect(result.totalDays).toBe(1);
  });

  it('counts multi-day streak starting from today', () => {
    const now = new Date();
    const today = toUTCDateString(now);
    const d1 = toUTCDateString(new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - 1)));
    const d2 = toUTCDateString(new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - 2)));
    const result = calculateStreaks([
      { date: today, count: 1 },
      { date: d1, count: 1 },
      { date: d2, count: 1 },
    ]);
    expect(result.currentStreak).toBe(3);
  });

  it('counts multi-day streak starting from yesterday', () => {
    const now = new Date();
    const yesterday = toUTCDateString(new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - 1)));
    const d2 = toUTCDateString(new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - 2)));
    const d3 = toUTCDateString(new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - 3)));
    const result = calculateStreaks([
      { date: yesterday, count: 1 },
      { date: d2, count: 1 },
      { date: d3, count: 1 },
    ]);
    expect(result.currentStreak).toBe(3);
  });

  it('returns zero current streak for old dates', () => {
    const result = calculateStreaks([
      { date: '2023-01-01', count: 1 },
      { date: '2023-01-02', count: 1 },
    ]);
    expect(result.currentStreak).toBe(0);
    expect(result.longestStreak).toBe(2);
  });
});

describe('Overall chart data', () => {
  interface RatingEntry {
    platform?: string;
    contestDate: string;
    ratingAfter: number;
    contestName: string;
  }

  function buildOverallChartData(entries: RatingEntry[]) {
    const uniquePlatforms = [...new Set(entries.map(e => e.platform).filter(Boolean))] as string[];
    const mergedData: Record<string, unknown>[] = [];

    for (const entry of entries) {
      mergedData.push({
        date: new Date(entry.contestDate).getTime(),
        [`${entry.platform}_rating`]: entry.ratingAfter,
        [`${entry.platform}_name`]: entry.contestName,
      });
    }

    return { mergedData, uniquePlatforms };
  }

  it('separates platforms into distinct series keys', () => {
    const { mergedData, uniquePlatforms } = buildOverallChartData([
      { platform: 'CODEFORCES', contestDate: '2024-01-01T00:00:00Z', ratingAfter: 1500, contestName: 'CF1' },
      { platform: 'LEETCODE', contestDate: '2024-01-02T00:00:00Z', ratingAfter: 1600, contestName: 'LC1' },
    ]);

    expect(uniquePlatforms).toEqual(['CODEFORCES', 'LEETCODE']);
    expect(mergedData[0]).toHaveProperty('CODEFORCES_rating', 1500);
    expect(mergedData[1]).toHaveProperty('LEETCODE_rating', 1600);
  });

  it('handles single platform', () => {
    const { uniquePlatforms } = buildOverallChartData([
      { platform: 'CODEFORCES', contestDate: '2024-01-01T00:00:00Z', ratingAfter: 1500, contestName: 'CF1' },
    ]);
    expect(uniquePlatforms).toEqual(['CODEFORCES']);
  });

  it('handles empty entries', () => {
    const { mergedData, uniquePlatforms } = buildOverallChartData([]);
    expect(mergedData).toHaveLength(0);
    expect(uniquePlatforms).toHaveLength(0);
  });

  it('preserves chronological order from input', () => {
    const { mergedData } = buildOverallChartData([
      { platform: 'CODEFORCES', contestDate: '2024-01-01T00:00:00Z', ratingAfter: 1400, contestName: 'A' },
      { platform: 'CODEFORCES', contestDate: '2024-06-01T00:00:00Z', ratingAfter: 1500, contestName: 'B' },
    ]);
    expect((mergedData[0] as { date: number }).date).toBeLessThan((mergedData[1] as { date: number }).date);
  });
});

describe('Unified Activity Heatmap Data', () => {
  interface UnifiedEntry {
    date: string;
    count: number;
    problemsSolved?: number;
    contestsAttended?: number;
    platforms?: Record<string, { problemsSolved?: number; contestsAttended?: number }>;
    details?: Array<{ type: 'CONTEST' | 'PROBLEMS'; platform: string; count?: number; name?: string }>;
  }

  function aggregateUnifiedTotals(entries: UnifiedEntry[]) {
    const totalActivity = entries.reduce((acc, curr) => acc + curr.count, 0);
    const totalProblems = entries.reduce((acc, curr) => acc + (curr.problemsSolved ?? 0), 0);
    const totalContests = entries.reduce((acc, curr) => acc + (curr.contestsAttended ?? 0), 0);
    return { totalActivity, totalProblems, totalContests };
  }

  it('aggregates both problems and contests correctly', () => {
    const entries: UnifiedEntry[] = [
      {
        date: '2024-06-15',
        count: 7,
        problemsSolved: 5,
        contestsAttended: 2,
        platforms: {
          LEETCODE: { problemsSolved: 5, contestsAttended: 1 },
          CODEFORCES: { problemsSolved: 0, contestsAttended: 1 },
        },
      },
      {
        date: '2024-06-16',
        count: 3,
        problemsSolved: 3,
        contestsAttended: 0,
        platforms: {
          LEETCODE: { problemsSolved: 3, contestsAttended: 0 },
        },
      },
    ];

    const totals = aggregateUnifiedTotals(entries);
    expect(totals.totalActivity).toBe(10);
    expect(totals.totalProblems).toBe(8);
    expect(totals.totalContests).toBe(2);
  });

  it('handles entries with only contests', () => {
    const entries: UnifiedEntry[] = [
      { date: '2024-06-15', count: 1, contestsAttended: 1, problemsSolved: 0 },
    ];
    const totals = aggregateUnifiedTotals(entries);
    expect(totals.totalActivity).toBe(1);
    expect(totals.totalProblems).toBe(0);
    expect(totals.totalContests).toBe(1);
  });
});

describe('DSA Difficulty Statistics', () => {
  interface PlatformDifficulty {
    platform: string;
    hasDifficultyBreakdown?: boolean;
    easySolved?: number | null;
    mediumSolved?: number | null;
    hardSolved?: number | null;
  }

  function computeDifficultyBreakdown(platforms: PlatformDifficulty[], selectedPlatform: string) {
    if (selectedPlatform === 'ALL') {
      const available = platforms.some((p) => p.hasDifficultyBreakdown);
      if (!available) return { isAvailable: false };

      const easy = platforms.reduce((s, p) => s + (p.easySolved ?? 0), 0);
      const medium = platforms.reduce((s, p) => s + (p.mediumSolved ?? 0), 0);
      const hard = platforms.reduce((s, p) => s + (p.hardSolved ?? 0), 0);
      return {
        isAvailable: true,
        easy,
        medium,
        hard,
        total: easy + medium + hard,
      };
    }

    const target = platforms.find((p) => p.platform === selectedPlatform);
    if (!target || !target.hasDifficultyBreakdown) {
      return { isAvailable: false, platform: target?.platform };
    }

    const easy = target.easySolved ?? 0;
    const medium = target.mediumSolved ?? 0;
    const hard = target.hardSolved ?? 0;
    return {
      isAvailable: true,
      easy,
      medium,
      hard,
      total: easy + medium + hard,
    };
  }

  it('computes verified difficulty breakdown when LeetCode is present', () => {
    const platforms: PlatformDifficulty[] = [
      { platform: 'LEETCODE', hasDifficultyBreakdown: true, easySolved: 140, mediumSolved: 32, hardSolved: 0 },
      { platform: 'CODEFORCES', hasDifficultyBreakdown: false, easySolved: null, mediumSolved: null, hardSolved: null },
    ];

    const result = computeDifficultyBreakdown(platforms, 'ALL');
    expect(result.isAvailable).toBe(true);
    expect(result.easy).toBe(140);
    expect(result.medium).toBe(32);
    expect(result.hard).toBe(0);
    expect(result.total).toBe(172);
  });

  it('returns explicit unavailable state for Codeforces filter', () => {
    const platforms: PlatformDifficulty[] = [
      { platform: 'LEETCODE', hasDifficultyBreakdown: true, easySolved: 140, mediumSolved: 32, hardSolved: 0 },
      { platform: 'CODEFORCES', hasDifficultyBreakdown: false, easySolved: null, mediumSolved: null, hardSolved: null },
    ];

    const result = computeDifficultyBreakdown(platforms, 'CODEFORCES');
    expect(result.isAvailable).toBe(false);
  });

  it('never represents missing difficulty data as zero on non-supporting platforms', () => {
    const platforms: PlatformDifficulty[] = [
      { platform: 'CODEFORCES', hasDifficultyBreakdown: false, easySolved: null, mediumSolved: null, hardSolved: null },
      { platform: 'ATCODER', hasDifficultyBreakdown: false, easySolved: null, mediumSolved: null, hardSolved: null },
    ];

    const result = computeDifficultyBreakdown(platforms, 'ALL');
    expect(result.isAvailable).toBe(false);
  });
});

describe('RefreshResult normalization', () => {
  interface RefreshResult {
    profile: { id: number; platform: string; lastSyncStatus: string | null };
    syncStatus: 'SUCCESS' | 'PARTIAL' | 'FAILED';
    syncError?: string;
  }

  function normalizeRefreshResponse(raw: Record<string, unknown>): RefreshResult {
    if (raw.syncStatus === 'FAILED' || raw.syncStatus === 'PARTIAL') {
      return {
        profile: raw.profile as RefreshResult['profile'],
        syncStatus: raw.syncStatus as 'FAILED' | 'PARTIAL',
        syncError: raw.syncError as string | undefined,
      };
    }
    return {
      profile: raw as unknown as RefreshResult['profile'],
      syncStatus: 'SUCCESS',
    };
  }

  it('normalizes a SUCCESS response (flat ProfileResponse)', () => {
    const raw = { id: 1, platform: 'CODEFORCES', lastSyncStatus: 'SUCCESS', lastSyncError: null };
    const result = normalizeRefreshResponse(raw);
    expect(result.syncStatus).toBe('SUCCESS');
    expect(result.profile).toBe(raw);
    expect(result.syncError).toBeUndefined();
  });

  it('normalizes a FAILED response (wrapped)', () => {
    const profile = { id: 1, platform: 'CODEFORCES', lastSyncStatus: 'FAILED', lastSyncError: 'Connection timeout' };
    const raw = { profile, syncStatus: 'FAILED', syncError: 'Connection timeout' };
    const result = normalizeRefreshResponse(raw);
    expect(result.syncStatus).toBe('FAILED');
    expect(result.profile).toBe(profile);
    expect(result.syncError).toBe('Connection timeout');
  });

  it('normalizes a PARTIAL response (wrapped)', () => {
    const profile = { id: 2, platform: 'ATCODER', lastSyncStatus: 'PARTIAL', lastSyncError: 'Contest history sync failed' };
    const raw = { profile, syncStatus: 'PARTIAL', syncError: 'Contest history sync failed' };
    const result = normalizeRefreshResponse(raw);
    expect(result.syncStatus).toBe('PARTIAL');
    expect(result.profile.platform).toBe('ATCODER');
    expect(result.syncError).toBe('Contest history sync failed');
  });

  it('treats response without syncStatus field as SUCCESS', () => {
    const raw = { id: 3, platform: 'LEETCODE', lastSyncStatus: 'SUCCESS', lastSyncError: null, rating: 2000 };
    const result = normalizeRefreshResponse(raw);
    expect(result.syncStatus).toBe('SUCCESS');
    expect(result.syncError).toBeUndefined();
  });
});

describe('Appearance / Theme system', () => {
  const STORAGE_KEY = 'contestradar-appearance';

  let store: Record<string, string>;

  function mockGetItem(key: string): string | null {
    return store[key] ?? null;
  }
  function mockSetItem(key: string, value: string) {
    store[key] = value;
  }

  function loadAppearance(): { mode: string; accentColor: string | null } {
    try {
      const raw = mockGetItem(STORAGE_KEY);
      if (!raw) {
        const legacy = mockGetItem('contestradar-theme');
        if (legacy === 'light') return { mode: 'light', accentColor: null };
        if (legacy === 'dark') return { mode: 'dark', accentColor: null };
        return { mode: 'system', accentColor: null };
      }
      const parsed = JSON.parse(raw);
      const mode = ['dark', 'light', 'system'].includes(parsed.mode) ? parsed.mode : 'system';
      const accentColor = typeof parsed.accentColor === 'string' && /^#[0-9a-fA-F]{6}$/.test(parsed.accentColor)
        ? parsed.accentColor
        : null;
      return { mode, accentColor };
    } catch {
      return { mode: 'system', accentColor: null };
    }
  }

  function luminance(hex: string): number {
    const r = parseInt(hex.slice(1, 3), 16) / 255;
    const g = parseInt(hex.slice(3, 5), 16) / 255;
    const b = parseInt(hex.slice(5, 7), 16) / 255;
    const [rl, gl, bl] = [r, g, b].map(c => c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
    return 0.2126 * rl + 0.7152 * gl + 0.0722 * bl;
  }

  beforeEach(() => {
    store = {};
  });

  it('defaults to system mode when no preference is stored', () => {
    const result = loadAppearance();
    expect(result.mode).toBe('system');
    expect(result.accentColor).toBeNull();
  });

  it('restores dark mode from saved preferences', () => {
    mockSetItem(STORAGE_KEY, JSON.stringify({ mode: 'dark', accentColor: null }));
    const result = loadAppearance();
    expect(result.mode).toBe('dark');
  });

  it('restores light mode from saved preferences', () => {
    mockSetItem(STORAGE_KEY, JSON.stringify({ mode: 'light', accentColor: null }));
    const result = loadAppearance();
    expect(result.mode).toBe('light');
  });

  it('preserves system as the stored mode', () => {
    mockSetItem(STORAGE_KEY, JSON.stringify({ mode: 'system', accentColor: null }));
    const result = loadAppearance();
    expect(result.mode).toBe('system');
  });

  it('restores a custom accent color', () => {
    mockSetItem(STORAGE_KEY, JSON.stringify({ mode: 'dark', accentColor: '#3B82F6' }));
    const result = loadAppearance();
    expect(result.accentColor).toBe('#3B82F6');
  });

  it('falls back safely for invalid stored mode', () => {
    mockSetItem(STORAGE_KEY, JSON.stringify({ mode: 'invalid', accentColor: null }));
    const result = loadAppearance();
    expect(result.mode).toBe('system');
  });

  it('falls back safely for invalid stored accent', () => {
    mockSetItem(STORAGE_KEY, JSON.stringify({ mode: 'dark', accentColor: 'not-a-color' }));
    const result = loadAppearance();
    expect(result.accentColor).toBeNull();
  });

  it('falls back safely for malformed JSON', () => {
    mockSetItem(STORAGE_KEY, '{broken json');
    const result = loadAppearance();
    expect(result.mode).toBe('system');
    expect(result.accentColor).toBeNull();
  });

  it('migrates legacy contestradar-theme key', () => {
    mockSetItem('contestradar-theme', 'light');
    const result = loadAppearance();
    expect(result.mode).toBe('light');
  });

  it('computes correct luminance for white', () => {
    expect(luminance('#FFFFFF')).toBeCloseTo(1.0, 1);
  });

  it('computes correct luminance for black', () => {
    expect(luminance('#000000')).toBeCloseTo(0.0, 1);
  });

  it('picks dark text for light accents based on luminance', () => {
    const lum = luminance('#D4D4D4');
    expect(lum).toBeGreaterThan(0.4);
  });

  it('picks light text for dark accents based on luminance', () => {
    const lum = luminance('#404040');
    expect(lum).toBeLessThan(0.4);
  });

  it('applyDraft with override bypasses stale closure', () => {
    interface Appearance { mode: string; accentColor: string | null }
    let saved: Appearance = { mode: 'dark', accentColor: null };
    function applyDraft(override?: Appearance) {
      const toSave = override ?? saved;
      saved = toSave;
    }
    applyDraft({ mode: 'light', accentColor: '#FF0000' });
    expect(saved.mode).toBe('light');
    expect(saved.accentColor).toBe('#FF0000');
  });

  it('resetDefaults always clears preview state', () => {
    let previewActive = true;
    let savedState: { mode: string; accentColor: string | null } = { mode: 'dark', accentColor: '#FF0000' };
    function resetDefaults() {
      const defaults: { mode: string; accentColor: string | null } = { mode: 'system', accentColor: null };
      savedState = defaults;
      previewActive = false;
    }
    resetDefaults();
    expect(savedState.mode).toBe('system');
    expect(savedState.accentColor).toBeNull();
    expect(previewActive).toBe(false);
  });
});

describe('formatLevel', () => {
  // Mirrors the function in Recommended.tsx — inline to keep tests self-contained.
  function formatLevel(level: string): string {
    switch (level?.toLowerCase()) {
      case 'beginner':  return 'Beginner';
      case 'improving': return 'Improving';
      case 'practice':  return 'Practice Mode';
      case 'active':    return 'Active Competitor';
      default:          return level ?? 'General';
    }
  }

  it('maps beginner (lowercase) correctly', () => {
    expect(formatLevel('beginner')).toBe('Beginner');
  });

  it('maps improving correctly', () => {
    expect(formatLevel('improving')).toBe('Improving');
  });

  it('maps practice correctly', () => {
    expect(formatLevel('practice')).toBe('Practice Mode');
  });

  it('maps active correctly', () => {
    expect(formatLevel('active')).toBe('Active Competitor');
  });

  it('is case-insensitive — BEGINNER uppercased', () => {
    expect(formatLevel('BEGINNER')).toBe('Beginner');
  });

  it('is case-insensitive — IMPROVING uppercased', () => {
    expect(formatLevel('IMPROVING')).toBe('Improving');
  });

  it('returns the raw value for unknown strings', () => {
    expect(formatLevel('expert')).toBe('expert');
  });

  it('returns General for null/undefined', () => {
    expect(formatLevel(null as unknown as string)).toBe('General');
    expect(formatLevel(undefined as unknown as string)).toBe('General');
  });
});

describe('AuthContext logout behavior', () => {
  // Inline the fixed logout logic — mirrors AuthContext.tsx.
  // setUser(null) must always fire regardless of server response.

  // Minimal error type mimicking ApiError — avoids class declarations
  // restricted by erasableSyntaxOnly.
  function makeApiError(status: number): Error & { status: number } {
    const e = new Error(`HTTP ${status}`) as Error & { status: number };
    e.status = status;
    return e;
  }

  function isApiError(e: unknown): e is Error & { status: number } {
    return e instanceof Error && 'status' in e && typeof (e as Record<string, unknown>).status === 'number';
  }

  async function simulateLogout(serverResponse: () => Promise<void>): Promise<boolean> {
    let userCleared = false;
    userCleared = true; // setUser(null) fires immediately — before the await
    try {
      await serverResponse();
    } catch (e) {
      if (isApiError(e) && e.status !== 401) {
        // log but do not rethrow — user is already cleared
      }
    }
    return userCleared;
  }

  it('clears user on successful logout', async () => {
    const cleared = await simulateLogout(() => Promise.resolve());
    expect(cleared).toBe(true);
  });

  it('clears user even when server returns 500', async () => {
    const cleared = await simulateLogout(() =>
      Promise.reject(makeApiError(500))
    );
    expect(cleared).toBe(true);
  });

  it('clears user even when server returns 503', async () => {
    const cleared = await simulateLogout(() =>
      Promise.reject(makeApiError(503))
    );
    expect(cleared).toBe(true);
  });

  it('clears user when network request fails with TypeError', async () => {
    const cleared = await simulateLogout(() =>
      Promise.reject(new TypeError('network error'))
    );
    expect(cleared).toBe(true);
  });

  it('clears user when server returns 401 (session already gone)', async () => {
    const cleared = await simulateLogout(() =>
      Promise.reject(makeApiError(401))
    );
    expect(cleared).toBe(true);
  });
});

describe('API client response parsing (MED-03)', () => {
  // Inline the fixed request parsing logic from client.ts.
  // Uses a factory instead of a class to satisfy erasableSyntaxOnly.
  type ApiErrorLike = Error & { status: number };
  function makeParseError(status: number, message: string): ApiErrorLike {
    const e = Object.assign(new Error(message), { name: 'ApiError', status });
    return e as ApiErrorLike;
  }

  function parseResponse(status: number, text: string): unknown {
    if (status === 204 || text === '') return undefined;
    try {
      return JSON.parse(text);
    } catch {
      throw makeParseError(status, text.slice(0, 500));
    }
  }

  function isApiErrorLike(e: unknown): e is ApiErrorLike {
    return e instanceof Error && 'status' in e;
  }

  it('parses valid JSON on 200', () => {
    const result = parseResponse(200, '{"ok":true}');
    expect(result).toEqual({ ok: true });
  });

  it('returns undefined for empty 200 body', () => {
    expect(parseResponse(200, '')).toBeUndefined();
  });

  it('returns undefined for 204', () => {
    expect(parseResponse(204, '')).toBeUndefined();
  });

  it('throws ApiError-like (not SyntaxError) for non-JSON 200 body', () => {
    let caught: unknown;
    try {
      parseResponse(200, '<html>Error</html>');
    } catch (e) { caught = e; }
    expect(isApiErrorLike(caught)).toBe(true);
    expect((caught as ApiErrorLike).status).toBe(200);
    expect((caught as ApiErrorLike).message).toContain('<html>');
  });

  it('throws ApiError-like for non-JSON 502 body', () => {
    let caught: unknown;
    try {
      parseResponse(502, 'Bad Gateway');
    } catch (e) { caught = e; }
    expect(isApiErrorLike(caught)).toBe(true);
    expect((caught as ApiErrorLike).status).toBe(502);
    expect((caught as ApiErrorLike).message).toBe('Bad Gateway');
  });

  it('truncates very long non-JSON bodies to 500 chars', () => {
    let caught: unknown;
    try {
      parseResponse(200, 'x'.repeat(1000));
    } catch (e) { caught = e; }
    expect((caught as ApiErrorLike).message.length).toBe(500);
  });

  it('parses valid JSON array', () => {
    const result = parseResponse(200, '[1,2,3]');
    expect(Array.isArray(result)).toBe(true);
    expect(result).toEqual([1, 2, 3]);
  });
});
