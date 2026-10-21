import { api } from './client';
import type { ContestPage, Platform, ContestStatus, SyncStatus, UserPreferences, ConnectedProfile, ReminderInfo, PerformanceSummary, RatingHistoryResponse } from '../types/contest';

export interface ContestFilters {
  status?: ContestStatus[];
  platform?: Platform;
  search?: string;
  page?: number;
  size?: number;
}

export function fetchContests(filters: ContestFilters = {}): Promise<ContestPage> {
  const params = new URLSearchParams();
  if (filters.status?.length) params.set('status', filters.status.join(','));
  if (filters.platform) params.set('platform', filters.platform);
  if (filters.search) params.set('search', filters.search);
  if (filters.page !== undefined) params.set('page', String(filters.page));
  if (filters.size !== undefined) params.set('size', String(filters.size));
  const qs = params.toString();
  return api.get<ContestPage>(`/contests${qs ? `?${qs}` : ''}`);
}

export function fetchSyncStatus(): Promise<SyncStatus[]> {
  return api.get<SyncStatus[]>('/contests/sync-status');
}

export interface SyncTriggerResult {
  status: 'started' | 'skipped';
  reason?: string;
}

export function triggerSync(): Promise<SyncTriggerResult> {
  return api.post<SyncTriggerResult>('/contests/sync');
}

export function fetchPreferences(): Promise<UserPreferences> {
  return api.get<UserPreferences>('/preferences');
}

export function updatePreferences(prefs: Partial<UserPreferences>): Promise<UserPreferences> {
  return api.put<UserPreferences>('/preferences', prefs);
}

export function fetchProfiles(): Promise<ConnectedProfile[]> {
  return api.get<ConnectedProfile[]>('/profiles');
}

export function addProfile(data: { platform: string; handle: string; profileUrl?: string }): Promise<ConnectedProfile> {
  return api.post<ConnectedProfile>('/profiles', data);
}

export function deleteProfile(id: number): Promise<void> {
  return api.delete<void>(`/profiles/${id}`);
}

export function startVerification(profileId: number): Promise<{ token: string; method: string; instructions: string }> {
  return api.post<{ token: string; method: string; instructions: string }>(`/profiles/${profileId}/verify/start`);
}

export function checkVerification(profileId: number): Promise<{ verified: boolean; message: string }> {
  return api.post<{ verified: boolean; message: string }>(`/profiles/${profileId}/verify/check`);
}

export interface RefreshResult {
  profile: ConnectedProfile;
  syncStatus: 'SUCCESS' | 'PARTIAL' | 'FAILED';
  syncError?: string;
}

export async function refreshProfile(profileId: number): Promise<RefreshResult> {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const raw: any = await api.post(`/profiles/${profileId}/refresh`);

  if (raw.syncStatus === 'FAILED' || raw.syncStatus === 'PARTIAL') {
    return { profile: raw.profile, syncStatus: raw.syncStatus, syncError: raw.syncError };
  }

  return { profile: raw as ConnectedProfile, syncStatus: 'SUCCESS' };
}

export function updateProfileHandle(
  id: number,
  data: { platform: string; handle: string; profileUrl?: string }
): Promise<ConnectedProfile> {
  return api.put<ConnectedProfile>(`/profiles/${id}`, data);
}

export function fetchReminders(): Promise<ReminderInfo[]> {
  return api.get<ReminderInfo[]>('/reminders');
}

export function setReminder(contestId: number, leadMinutes: number): Promise<ReminderInfo> {
  return api.post<ReminderInfo>('/reminders', { contestId, leadMinutes });
}

export function cancelReminder(contestId: number): Promise<void> {
  return api.delete<void>(`/reminders/${contestId}`);
}

export function fetchPerformanceSummary(): Promise<PerformanceSummary> {
  return api.get<PerformanceSummary>('/performance/summary');
}

export function fetchRatingHistory(platform: string): Promise<RatingHistoryResponse> {
  return api.get<RatingHistoryResponse>(`/performance/history/${platform}`);
}

export interface HeatmapDetail {
  type: 'CONTEST' | 'PROBLEMS';
  platform: string;
  name?: string;
  count?: number;
  rank?: number;
  ratingAfter?: number;
}

export interface HeatmapEntry {
  date: string;
  count: number;
  problemsSolved?: number;
  contestsAttended?: number;
  platforms?: Record<string, { problemsSolved?: number; contestsAttended?: number }>;
  details?: HeatmapDetail[];
}

export interface HeatmapResponse {
  entries: HeatmapEntry[];
  platform?: string;
  dataSource: string;
  description: string;
  totalProblemsSolved?: number;
  totalContestsAttended?: number;
  totalActivity?: number;
}

export function fetchActivityHeatmap(platform?: string): Promise<HeatmapResponse> {
  const params = platform ? `?platform=${platform}` : '';
  return api.get<HeatmapResponse>(`/performance/heatmap${params}`);
}

export function fetchRatingHistoryWithRange(platform: string, days?: number): Promise<RatingHistoryResponse> {
  const params = days ? `?days=${days}` : '';
  return api.get<RatingHistoryResponse>(`/performance/history/${platform}${params}`);
}

export function fetchAllRatingHistory(days?: number): Promise<RatingHistoryResponse> {
  const params = days ? `?days=${days}` : '';
  return api.get<RatingHistoryResponse>(`/performance/history/all${params}`);
}

export interface BackendRecommendation {
  contest: import('../types/contest').Contest;
  score: number;
  reasons: string[];
  userLevel: string;
}

export function fetchRecommendations(): Promise<BackendRecommendation[]> {
  return api.get<BackendRecommendation[]>('/performance/recommendations');
}
