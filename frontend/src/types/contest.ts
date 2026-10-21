export type Platform = 'CODEFORCES' | 'LEETCODE' | 'CODECHEF' | 'ATCODER';

export type ContestStatus = 'UPCOMING' | 'LIVE' | 'FINISHED' | 'CANCELLED';

export interface Contest {
  id: number;
  externalId: string;
  platform: Platform;
  name: string;
  startTime: string;
  endTime: string | null;
  durationSeconds: number | null;
  url: string;
  registrationUrl: string | null;
  status: ContestStatus;
  ratingMin: number | null;
  ratingMax: number | null;
  dataSource: string | null;
  sourceConfidence: string | null;
  lastSyncedAt: string;
  reminderSet: boolean | null;
}

export interface ContestPage {
  content: Contest[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface SyncStatus {
  id: number;
  platform: Platform;
  lastSyncedAt: string | null;
  status: string;
  errorMessage: string | null;
  contestsCount: number;
}

export interface UserProfile {
  id: number;
  email: string;
  username: string;
  experienceLevel: string;
  timezone: string;
  avatarUrl: string | null;
}

export interface ConnectedProfile {
  id: number;
  platform: string;
  handle: string;
  verified: boolean;
  rating: number | null;
  maxRating: number | null;
  rankTitle: string | null;
  problemsSolved: number | null;
  easySolved: number | null;
  mediumSolved: number | null;
  hardSolved: number | null;
  profileUrl: string | null;
  verificationMethod: string | null;
  verificationExpiresAt: string | null;
  verifiedAt: string | null;
  fetchedAt: string | null;
  createdAt: string;
  lastSyncStatus: string | null;
  lastSyncError: string | null;
}

export interface UserPreferences {
  emailNotifications: boolean;
  browserNotifications: boolean;
  preferredPlatforms: string;
  preferredContestTimes: string | null;
}

export interface ReminderInfo {
  id: number;
  contestId: number;
  contestName: string;
  contestPlatform: string;
  contestStartTime: string;
  remindAt: string;
  leadMinutes: number;
  status: string;
  createdAt: string;
}

export interface PlatformStat {
  platform: string;
  handle: string;
  verified: boolean;
  rating: number | null;
  maxRating: number | null;
  rankTitle: string | null;
  problemsSolved: number | null;
  easySolved?: number | null;
  mediumSolved?: number | null;
  hardSolved?: number | null;
  hasDifficultyBreakdown?: boolean;
  fetchedAt: string | null;
  contestCount: number;
  contestsLast30Days: number;
}

export interface PerformanceSummary {
  connectedProfiles: number;
  totalContests: number;
  contestsLast30Days: number;
  mostActivePlatform: string | null;
  lastSyncedAt: string | null;
  totalEasySolved?: number | null;
  totalMediumSolved?: number | null;
  totalHardSolved?: number | null;
  hasDifficultyBreakdown?: boolean;
  platforms: PlatformStat[];
}

export interface RatingEntry {
  contestName: string;
  contestDate: string;
  ratingBefore: number | null;
  ratingAfter: number;
  rankInContest: number | null;
  performance: number | null;
  platform?: string; // Platform identifier for multi-platform charts
}

export interface RatingHistoryResponse {
  platform: string;
  handle?: string;
  verified?: boolean;
  currentRating?: number | null;
  maxRating?: number | null;
  entries: RatingEntry[];
  dateRange?: string;
}

export const PLATFORM_LABELS: Record<Platform, string> = {
  CODEFORCES: 'Codeforces',
  LEETCODE: 'LeetCode',
  CODECHEF: 'CodeChef',
  ATCODER: 'AtCoder',
};

export const PLATFORM_COLORS: Record<Platform, string> = {
  CODEFORCES: '#3b82f6',
  LEETCODE: '#f59e0b',
  CODECHEF: '#8b6f47',
  ATCODER: '#71717a',
};

export const PLATFORM_URLS: Record<string, string> = {
  CODEFORCES: 'https://codeforces.com/profile/',
  LEETCODE: 'https://leetcode.com/u/',
  CODECHEF: 'https://www.codechef.com/users/',
  ATCODER: 'https://atcoder.jp/users/',
};

export const ALL_PLATFORMS: Platform[] = ['CODEFORCES', 'LEETCODE', 'CODECHEF', 'ATCODER'];
