import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchPreferences,
  updatePreferences,
  fetchProfiles,
  addProfile,
  deleteProfile,
  startVerification,
  checkVerification,
  refreshProfile,
  updateProfileHandle,
} from '../api/contests';
import { uploadAvatar, deleteAvatar, updateUserProfile } from '../api/auth';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { Link } from 'react-router-dom';
import { useState, useEffect, useRef } from 'react';
import type { ChangeEvent } from 'react';
import { PLATFORM_LABELS, PLATFORM_URLS, PLATFORM_COLORS, ALL_PLATFORMS } from '../types/contest';
import type { Platform, ConnectedProfile } from '../types/contest';
import { useTheme, DEFAULT_ACCENT_DARK, DEFAULT_ACCENT_LIGHT, type Mode } from '../context/ThemeContext';

const EXPERIENCE_LEVELS = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'];
const COMMON_TIMEZONES = [
  'UTC', 'Asia/Kolkata', 'America/New_York', 'America/Los_Angeles',
  'Europe/London', 'Europe/Berlin', 'Asia/Tokyo', 'Asia/Shanghai',
  'Australia/Sydney', 'America/Chicago', 'America/Sao_Paulo',
];

const VERIFY_INSTRUCTIONS: Record<string, { field: string; url: string }> = {
  CODEFORCES: { field: 'organization', url: 'https://codeforces.com/settings/social' },
  LEETCODE: { field: 'website or summary', url: 'https://leetcode.com/profile/' },
  ATCODER: { field: 'affiliation', url: 'https://atcoder.jp/settings' },
};

type Section = 'PROFILE' | 'PLATFORMS' | 'APPEARANCE' | 'NOTIFICATIONS';

export function Settings() {
  const { user, refreshUser } = useAuth();
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const [activeSection, setActiveSection] = useState<Section>('PROFILE');

  // ---------- Profile edit state ----------
  const [editingProfile, setEditingProfile] = useState(false);
  const [editUsername, setEditUsername] = useState('');
  const [editTimezone, setEditTimezone] = useState('');
  const [editExperience, setEditExperience] = useState('');

  // ---------- Avatar state ----------
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);

  // ---------- Platform handle edit state ----------
  const [editingHandleId, setEditingHandleId] = useState<number | null>(null);
  const [newHandle, setNewHandle] = useState('');
  const [handleEditError, setHandleEditError] = useState('');

  // ---------- Add-platform state ----------
  const [addingPlatform, setAddingPlatform] = useState<string | null>(null);
  const [handle, setHandle] = useState('');
  const [formError, setFormError] = useState('');

  // ---------- Verification state ----------
  const [verifyingId, setVerifyingId] = useState<number | null>(null);
  const [verifyToken, setVerifyToken] = useState<string | null>(null);
  const [verifyInstructions, setVerifyInstructions] = useState<string | null>(null);

  // ---------- Notification/pref state ----------
  const [emailNotif, setEmailNotif] = useState(true);
  const [browserNotif, setBrowserNotif] = useState(false);
  const [selectedPlatforms, setSelectedPlatforms] = useState<string[]>([]);

  // ---------- Queries ----------
  const { data: prefs } = useQuery({
    queryKey: ['preferences'],
    queryFn: fetchPreferences,
    enabled: !!user,
  });

  const {
    data: profiles,
    isLoading: profilesLoading,
    error: profilesError,
    refetch: refetchProfiles,
  } = useQuery({
    queryKey: ['profiles'],
    queryFn: fetchProfiles,
    enabled: !!user,
  });

  useEffect(() => {
    if (prefs) {
      setEmailNotif(prefs.emailNotifications);
      setBrowserNotif(prefs.browserNotifications);
      setSelectedPlatforms(
        prefs.preferredPlatforms
          ? prefs.preferredPlatforms.split(',').map((p) => p.trim()).filter(Boolean)
          : []
      );
    }
  }, [prefs]);

  // Populate profile edit fields when user changes
  useEffect(() => {
    if (user && editingProfile) {
      setEditUsername(user.username ?? '');
      setEditTimezone(user.timezone ?? 'UTC');
      setEditExperience(user.experienceLevel ?? 'BEGINNER');
    }
  }, [user, editingProfile]);

  // ---------- Mutations ----------
  const profileMutation = useMutation({
    mutationFn: () =>
      updateUserProfile({
        username: editUsername.trim() || undefined,
        timezone: editTimezone || undefined,
        experienceLevel: editExperience || undefined,
      }),
    onSuccess: async () => {
      await refreshUser();
      queryClient.invalidateQueries({ queryKey: ['auth'] });
      toast('Profile updated successfully', 'success');
      setEditingProfile(false);
    },
    onError: () => toast('Failed to update profile. Username may already be taken.', 'error'),
  });

  const prefMutation = useMutation({
    mutationFn: () =>
      updatePreferences({
        emailNotifications: emailNotif,
        browserNotifications: browserNotif,
        // Only send preferredPlatforms when the server value has loaded.
        // The backend only updates this field when non-null, so passing
        // undefined (serialized as absent in JSON → null on server) safely
        // preserves the stored value when prefs haven't finished loading.
        preferredPlatforms: prefs !== undefined ? selectedPlatforms.join(',') : undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['preferences'] });
      toast('Preferences saved', 'success');
    },
    onError: () => toast('Failed to save preferences', 'error'),
  });

  const addMutation = useMutation({
    mutationFn: (data: { platform: string; handle: string; profileUrl?: string }) => addProfile(data),
    onSuccess: async (created) => {
      queryClient.invalidateQueries({ queryKey: ['profiles'] });
      toast('Profile connected — syncing data...', 'success');
      setAddingPlatform(null);
      setHandle('');
      setFormError('');
      if (created.platform !== 'CODECHEF') {
        try {
          const result = await refreshProfile(created.id);
          queryClient.invalidateQueries({ queryKey: ['profiles'] });
          queryClient.invalidateQueries({ queryKey: ['performance'] });
          if (result.syncStatus === 'FAILED') {
            toast('Profile saved, but sync failed. Use refresh to retry.', 'error');
          } else if (result.syncStatus === 'PARTIAL') {
            toast('Profile connected. Some data could not be synced.', 'info');
          } else {
            toast('Profile data synchronized', 'success');
          }
        } catch {
          toast('Profile saved, but sync failed. Use refresh to retry.', 'info');
        }
      }
    },
    onError: () => setFormError('Failed to add profile. The handle may already be claimed.'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteProfile(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profiles'] });
      queryClient.invalidateQueries({ queryKey: ['performance'] });
      toast('Profile removed', 'info');
    },
    onError: () => toast('Failed to remove profile', 'error'),
  });

  const refreshMutation = useMutation({
    mutationFn: (id: number) => refreshProfile(id),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['profiles'] });
      queryClient.invalidateQueries({ queryKey: ['performance'] });
      if (result.syncStatus === 'FAILED') {
        toast(result.syncError ? `Sync failed: ${result.syncError}` : 'Sync failed', 'error');
      } else if (result.syncStatus === 'PARTIAL') {
        toast(result.syncError ? `Partial sync: ${result.syncError}` : 'Some data could not be synced', 'info');
      } else {
        toast('Profile data refreshed', 'success');
      }
    },
    onError: () => toast('Failed to refresh profile data', 'error'),
  });

  const updateHandleMutation = useMutation({
    mutationFn: ({ id, platform, handle: h }: { id: number; platform: string; handle: string }) => {
      const profileUrl = PLATFORM_URLS[platform] ? PLATFORM_URLS[platform] + h.trim() : undefined;
      return updateProfileHandle(id, { platform, handle: h.trim(), profileUrl });
    },
    onSuccess: async (updated) => {
      queryClient.invalidateQueries({ queryKey: ['profiles'] });
      queryClient.invalidateQueries({ queryKey: ['performance'] });
      toast('Handle updated — re-syncing...', 'success');
      setEditingHandleId(null);
      setNewHandle('');
      setHandleEditError('');
      if (updated.platform !== 'CODECHEF') {
        try {
          const result = await refreshProfile(updated.id);
          queryClient.invalidateQueries({ queryKey: ['profiles'] });
          queryClient.invalidateQueries({ queryKey: ['performance'] });
          if (result.syncStatus === 'FAILED') {
            toast('Handle updated but sync failed. Refresh manually.', 'error');
          } else if (result.syncStatus === 'PARTIAL') {
            toast('Handle updated. Some data could not be synced.', 'info');
          }
        } catch {
          toast('Handle updated but sync failed. Refresh manually.', 'info');
        }
      }
    },
    onError: () => {
      setHandleEditError('Failed to update handle. It may already be claimed.');
    },
  });

  if (!user) {
    return (
      <div className="mx-auto max-w-md pt-20 text-center">
        <div
          className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-md border"
          style={{ backgroundColor: 'var(--accent-subtle)', borderColor: 'var(--border)' }}
        >
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" strokeWidth="2">
            <circle cx="12" cy="12" r="3" />
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
          </svg>
        </div>
        <h1 className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>Settings & Account</h1>
        <p className="mb-6 text-xs" style={{ color: 'var(--text-muted)' }}>
          Sign in to manage your connected profiles and notification preferences.
        </p>
        <Link to="/login" className="btn-editorial inline-block rounded px-5 py-2 text-xs font-medium">
          Sign in
        </Link>
      </div>
    );
  }

  const connectedMap = new Map((profiles ?? []).map((p) => [p.platform, p]));

  const handleAdd = (platformKey: string) => {
    setFormError('');
    if (!handle.trim()) {
      setFormError('Please enter your handle');
      return;
    }
    const profileUrl = PLATFORM_URLS[platformKey] ? PLATFORM_URLS[platformKey] + handle.trim() : undefined;
    addMutation.mutate({ platform: platformKey, handle: handle.trim(), profileUrl });
  };

  const handleStartVerify = async (profile: ConnectedProfile) => {
    try {
      const result = await startVerification(profile.id);
      setVerifyingId(profile.id);
      setVerifyToken(result.token);
      setVerifyInstructions(result.instructions ?? null);
    } catch {
      toast('Verification not available for this platform', 'error');
    }
  };

  const handleCheckVerify = async (profileId: number) => {
    try {
      const result = await checkVerification(profileId);
      if (result.verified) {
        toast('Profile verified!', 'success');
        setVerifyingId(null);
        setVerifyToken(null);
        setVerifyInstructions(null);
        queryClient.invalidateQueries({ queryKey: ['profiles'] });
      } else {
        toast(result.message || 'Verification not yet confirmed', 'error');
      }
    } catch {
      toast('Verification check failed', 'error');
    }
  };

  const handleAvatarUpload = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploadingAvatar(true);
    try {
      await uploadAvatar(file);
      queryClient.invalidateQueries({ queryKey: ['auth'] });
      toast('Profile picture updated', 'success');
      await refreshUser();
    } catch {
      toast('Failed to upload. Max 2MB, JPEG/PNG/WebP only.', 'error');
    } finally {
      setUploadingAvatar(false);
    }
  };

  const handleAvatarDelete = async () => {
    try {
      await deleteAvatar();
      queryClient.invalidateQueries({ queryKey: ['auth'] });
      toast('Profile picture removed', 'info');
      await refreshUser();
    } catch {
      toast('Failed to remove picture', 'error');
    }
  };

  const navItems: { key: Section; label: string; badge?: string }[] = [
    { key: 'PROFILE', label: 'Profile' },
    { key: 'PLATFORMS', label: 'Connected Platforms', badge: profiles?.length ? String(profiles.length) : undefined },
    { key: 'APPEARANCE', label: 'Appearance' },
    { key: 'NOTIFICATIONS', label: 'Notifications' },
  ];

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
          Settings & Account
        </h1>
        <p className="mt-0.5 text-xs" style={{ color: 'var(--text-muted)' }}>
          Manage your profile, connected coding accounts, and contest preferences
        </p>
      </div>

      {/* Navigation Tabs */}
      <div
        className="flex items-center gap-1 p-1 rounded border overflow-x-auto text-xs"
        style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border)' }}
      >
        {navItems.map(({ key, label, badge }) => (
          <button
            key={key}
            type="button"
            onClick={() => setActiveSection(key)}
            className="rounded px-3.5 py-1.5 font-medium transition-all shrink-0 font-mono-editorial flex items-center gap-1.5"
            style={{
              backgroundColor: activeSection === key ? 'var(--btn-primary-bg)' : 'transparent',
              color: activeSection === key ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
            }}
          >
            {label}
            {badge && (
              <span
                className="rounded px-1.5 text-[10px] font-bold"
                style={{
                  backgroundColor: activeSection === key ? 'rgba(255,255,255,0.2)' : 'var(--accent-subtle)',
                  color: activeSection === key ? 'var(--btn-primary-text)' : 'var(--accent)',
                }}
              >
                {badge}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* ── SECTION: PROFILE ── */}
      {activeSection === 'PROFILE' && (
        <div className="space-y-5">
          {/* Avatar + Identity card */}
          <div className="bento-card p-6">
            <div className="flex flex-col sm:flex-row items-start gap-6">
              {/* Avatar */}
              <div className="relative shrink-0">
                <div
                  className="flex h-20 w-20 items-center justify-center overflow-hidden rounded-full border-2"
                  style={{ backgroundColor: 'var(--bg-hover)', borderColor: 'var(--accent)' }}
                >
                  {user.avatarUrl ? (
                    <img src={user.avatarUrl} alt="" className="h-full w-full object-cover" />
                  ) : (
                    <span className="text-2xl font-bold font-mono-editorial" style={{ color: 'var(--accent)' }}>
                      {user.username.charAt(0).toUpperCase()}
                    </span>
                  )}
                </div>
              </div>

              {/* Identity + Avatar controls */}
              <div className="flex-1 min-w-0">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <h2 className="text-lg font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
                      {user.username}
                    </h2>
                    <p className="text-xs" style={{ color: 'var(--text-muted)' }}>{user.email}</p>
                    <div className="mt-1.5 flex flex-wrap gap-2">
                      {user.timezone && (
                        <span className="text-[11px] font-mono-editorial px-2 py-0.5 rounded border" style={{ borderColor: 'var(--border)', color: 'var(--text-muted)', backgroundColor: 'var(--bg-elevated)' }}>
                          {user.timezone}
                        </span>
                      )}
                      {user.experienceLevel && (
                        <span className="text-[11px] font-mono-editorial px-2 py-0.5 rounded border capitalize" style={{ borderColor: 'var(--border)', color: 'var(--text-muted)', backgroundColor: 'var(--bg-elevated)' }}>
                          {user.experienceLevel.toLowerCase()}
                        </span>
                      )}
                    </div>
                  </div>

                  {!editingProfile && (
                    <button
                      type="button"
                      onClick={() => {
                        setEditUsername(user.username ?? '');
                        setEditTimezone(user.timezone ?? 'UTC');
                        setEditExperience(user.experienceLevel ?? 'BEGINNER');
                        setEditingProfile(true);
                      }}
                      className="btn-editorial-outline rounded px-3.5 py-1.5 text-xs font-medium"
                    >
                      Edit Profile
                    </button>
                  )}
                </div>

                {/* Avatar upload controls */}
                <div className="mt-4 flex flex-wrap items-center gap-2.5">
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    className="hidden"
                    onChange={handleAvatarUpload}
                  />
                  <button
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    disabled={uploadingAvatar}
                    className="btn-editorial rounded px-3.5 py-1.5 text-xs font-medium disabled:opacity-50"
                  >
                    {uploadingAvatar ? 'Uploading...' : user.avatarUrl ? 'Change Avatar' : 'Upload Avatar'}
                  </button>
                  {user.avatarUrl && (
                    <button
                      type="button"
                      onClick={handleAvatarDelete}
                      className="btn-editorial-outline rounded px-3 py-1.5 text-xs font-medium"
                      style={{ color: 'var(--danger)' }}
                    >
                      Remove
                    </button>
                  )}
                  <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                    JPEG, PNG, or WebP · max 2 MB
                  </span>
                </div>
              </div>
            </div>

            {/* Edit form */}
            {editingProfile && (
              <div
                className="mt-5 pt-5 border-t space-y-4"
                style={{ borderColor: 'var(--border)' }}
              >
                <h3 className="text-xs font-bold uppercase tracking-wider" style={{ color: 'var(--text-secondary)' }}>
                  Edit Profile Information
                </h3>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  {/* Username */}
                  <div>
                    <label className="block text-[11px] font-medium mb-1" style={{ color: 'var(--text-muted)' }}>
                      Username
                    </label>
                    <input
                      type="text"
                      value={editUsername}
                      onChange={(e) => setEditUsername(e.target.value)}
                      className="w-full rounded border px-3 py-2 text-xs font-mono-editorial outline-none focus:border-[var(--accent)]"
                      style={{ backgroundColor: 'var(--bg-elevated)', borderColor: 'var(--border)', color: 'var(--text-primary)' }}
                      placeholder="Username"
                    />
                  </div>

                  {/* Timezone */}
                  <div>
                    <label className="block text-[11px] font-medium mb-1" style={{ color: 'var(--text-muted)' }}>
                      Timezone
                    </label>
                    <select
                      value={editTimezone}
                      onChange={(e) => setEditTimezone(e.target.value)}
                      className="w-full rounded border px-3 py-2 text-xs font-mono-editorial outline-none focus:border-[var(--accent)]"
                      style={{ backgroundColor: 'var(--bg-elevated)', borderColor: 'var(--border)', color: 'var(--text-primary)' }}
                    >
                      {COMMON_TIMEZONES.map((tz) => (
                        <option key={tz} value={tz}>{tz}</option>
                      ))}
                    </select>
                  </div>

                  {/* Experience Level */}
                  <div>
                    <label className="block text-[11px] font-medium mb-1" style={{ color: 'var(--text-muted)' }}>
                      Experience Level
                    </label>
                    <select
                      value={editExperience}
                      onChange={(e) => setEditExperience(e.target.value)}
                      className="w-full rounded border px-3 py-2 text-xs font-mono-editorial outline-none focus:border-[var(--accent)]"
                      style={{ backgroundColor: 'var(--bg-elevated)', borderColor: 'var(--border)', color: 'var(--text-primary)' }}
                    >
                      {EXPERIENCE_LEVELS.map((lvl) => (
                        <option key={lvl} value={lvl}>{lvl.charAt(0) + lvl.slice(1).toLowerCase()}</option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="flex items-center gap-3 pt-1">
                  <button
                    type="button"
                    onClick={() => profileMutation.mutate()}
                    disabled={profileMutation.isPending || !editUsername.trim()}
                    className="btn-editorial rounded px-5 py-2 text-xs font-medium disabled:opacity-50"
                  >
                    {profileMutation.isPending ? 'Saving...' : 'Save Changes'}
                  </button>
                  <button
                    type="button"
                    onClick={() => setEditingProfile(false)}
                    className="text-xs text-muted hover:text-[var(--text-primary)] transition-colors"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* Quick links */}
          <div className="bento-card p-5">
            <h3 className="text-xs font-bold uppercase tracking-wider mb-4" style={{ color: 'var(--text-secondary)' }}>
              Quick Actions
            </h3>
            <div className="flex flex-wrap gap-3">
              <Link
                to="/performance"
                className="rounded border px-4 py-2 text-xs font-medium font-mono-editorial hover:opacity-80 transition-opacity"
                style={{ borderColor: 'var(--border)', color: 'var(--text-secondary)', backgroundColor: 'var(--bg-elevated)' }}
              >
                View Performance Dashboard →
              </Link>
              <button
                type="button"
                onClick={() => setActiveSection('PLATFORMS')}
                className="rounded border px-4 py-2 text-xs font-medium font-mono-editorial hover:opacity-80 transition-opacity"
                style={{ borderColor: 'var(--border)', color: 'var(--text-secondary)', backgroundColor: 'var(--bg-elevated)' }}
              >
                Manage Connected Accounts →
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── SECTION: CONNECTED PLATFORMS ── */}
      {activeSection === 'PLATFORMS' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-sm font-bold uppercase tracking-wider" style={{ color: 'var(--text-secondary)' }}>
                Connected Coding Accounts
              </h2>
              <p className="text-xs text-muted mt-0.5">
                Link external platforms to synchronize rating history, problem-solving counts, and contest statistics.
              </p>
            </div>
            {profiles && profiles.length > 0 && (
              <span
                className="text-[11px] px-2 py-0.5 rounded font-medium font-mono-editorial border"
                style={{ backgroundColor: 'var(--accent-subtle)', borderColor: 'var(--border)', color: 'var(--accent)' }}
              >
                {profiles.length} Active
              </span>
            )}
          </div>

          {profilesLoading && (
            <div className="space-y-3">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="h-20 animate-pulse rounded" style={{ backgroundColor: 'var(--bg-card)' }} />
              ))}
            </div>
          )}

          {profilesError && (
            <div className="bento-card p-6 text-center">
              <p className="text-xs font-semibold" style={{ color: 'var(--danger)' }}>
                Failed to load connected profiles
              </p>
              <button
                type="button"
                onClick={() => refetchProfiles()}
                className="btn-editorial-outline mt-2 rounded px-3 py-1 text-xs font-medium"
              >
                Retry
              </button>
            </div>
          )}

          {!profilesLoading && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {ALL_PLATFORMS.map((platformKey: Platform) => {
                const profile = connectedMap.get(platformKey);
                const color = PLATFORM_COLORS[platformKey] ?? 'var(--text-muted)';
                const label = PLATFORM_LABELS[platformKey];
                const canVerify = platformKey !== 'CODECHEF';
                const verifyInfo = VERIFY_INSTRUCTIONS[platformKey];
                const isEditingThisHandle = editingHandleId === profile?.id;

                return (
                  <div key={platformKey} className="bento-card p-5 flex flex-col justify-between">
                    <div>
                      {/* Platform header */}
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex items-center gap-3">
                          <div
                            className="flex h-9 w-9 shrink-0 items-center justify-center rounded font-mono-editorial font-bold text-xs"
                            style={{ backgroundColor: color + '18', color }}
                          >
                            {label.slice(0, 2).toUpperCase()}
                          </div>
                          <div>
                            <div className="flex items-center gap-2">
                              <span className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
                                {label}
                              </span>
                              {profile?.verified && (
                                <span
                                  className="rounded px-1.5 py-0.5 text-[9px] font-bold font-mono-editorial"
                                  style={{ backgroundColor: 'var(--btn-primary-bg)', color: 'var(--btn-primary-text)' }}
                                >
                                  Verified
                                </span>
                              )}
                              {profile && !profile.verified && (
                                <span className="text-[10px] text-muted font-mono-editorial">Unverified</span>
                              )}
                              {!profile && (
                                <span className="text-[10px] text-muted font-mono-editorial">Disconnected</span>
                              )}
                            </div>
                            {profile ? (
                              <p className="mt-0.5 text-xs font-mono-editorial" style={{ color: 'var(--text-muted)' }}>
                                @{profile.handle}
                                {profile.rating != null && <span> · {profile.rating} rating</span>}
                                {profile.problemsSolved != null && <span> · {profile.problemsSolved} solved</span>}
                              </p>
                            ) : (
                              <p className="mt-0.5 text-[11px] text-muted">
                                {platformKey === 'CODECHEF'
                                  ? 'Manual handle link (no public API sync)'
                                  : 'Connect to sync ratings & activity'}
                              </p>
                            )}
                          </div>
                        </div>

                        {/* Card action buttons */}
                        <div className="flex items-center gap-1.5">
                          {/* Edit handle button */}
                          {profile && !isEditingThisHandle && (
                            <button
                              type="button"
                              onClick={() => {
                                setEditingHandleId(profile.id);
                                setNewHandle(profile.handle);
                                setHandleEditError('');
                              }}
                              className="p-1.5 rounded border hover:bg-[var(--bg-hover)] transition-colors"
                              style={{ borderColor: 'var(--border)', color: 'var(--text-secondary)' }}
                              title="Edit handle"
                            >
                              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                              </svg>
                            </button>
                          )}

                          {/* Refresh button */}
                          {profile && platformKey !== 'CODECHEF' && !isEditingThisHandle && (
                            <button
                              type="button"
                              onClick={() => refreshMutation.mutate(profile.id)}
                              disabled={refreshMutation.isPending}
                              className="p-1.5 rounded border hover:bg-[var(--bg-hover)] transition-colors disabled:opacity-50"
                              style={{ borderColor: 'var(--border)', color: 'var(--text-secondary)' }}
                              title="Synchronize profile data"
                            >
                              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                                <path d="M21 2v6h-6M3 12a9 9 0 0 1 15-6.7L21 8M3 22v-6h6M21 12a9 9 0 0 1-15 6.7L3 16" />
                              </svg>
                            </button>
                          )}

                          {/* Delete button */}
                          {profile && !isEditingThisHandle && (
                            <button
                              type="button"
                              onClick={() => deleteMutation.mutate(profile.id)}
                              disabled={deleteMutation.isPending}
                              className="p-1.5 rounded border hover:bg-[var(--bg-hover)] transition-colors disabled:opacity-50"
                              style={{ borderColor: 'var(--border)', color: 'var(--danger)' }}
                              title="Disconnect profile"
                            >
                              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <polyline points="3 6 5 6 21 6" />
                                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                              </svg>
                            </button>
                          )}
                        </div>
                      </div>

                      {/* Sync error */}
                      {profile?.lastSyncStatus === 'FAILED' && profile.lastSyncError && (
                        <p className="mt-2 text-[11px]" style={{ color: 'var(--danger)' }}>
                          Sync error: {profile.lastSyncError}
                        </p>
                      )}

                      {/* Inline handle edit form */}
                      {isEditingThisHandle && profile && (
                        <div className="mt-3 pt-3 border-t space-y-2" style={{ borderColor: 'var(--border)' }}>
                          <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                            Change {label} handle
                          </p>
                          <div className="flex gap-2">
                            <input
                              type="text"
                              value={newHandle}
                              onChange={(e) => setNewHandle(e.target.value)}
                              placeholder={`${label} handle`}
                              className="flex-1 rounded border px-3 py-1.5 text-xs font-mono-editorial outline-none focus:border-[var(--accent)]"
                              style={{ backgroundColor: 'var(--bg-elevated)', borderColor: 'var(--border)', color: 'var(--text-primary)' }}
                              onKeyDown={(e) => {
                                if (e.key === 'Enter' && newHandle.trim()) {
                                  updateHandleMutation.mutate({ id: profile.id, platform: platformKey, handle: newHandle });
                                }
                              }}
                            />
                            <button
                              type="button"
                              onClick={() => {
                                if (!newHandle.trim()) return;
                                updateHandleMutation.mutate({ id: profile.id, platform: platformKey, handle: newHandle });
                              }}
                              disabled={updateHandleMutation.isPending || !newHandle.trim()}
                              className="btn-editorial rounded px-3 py-1.5 text-xs font-medium disabled:opacity-50"
                            >
                              {updateHandleMutation.isPending ? 'Saving...' : 'Save'}
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                setEditingHandleId(null);
                                setNewHandle('');
                                setHandleEditError('');
                              }}
                              className="px-2 py-1.5 text-xs font-medium text-muted hover:text-[var(--text-primary)]"
                            >
                              Cancel
                            </button>
                          </div>
                          {handleEditError && (
                            <p className="text-[11px]" style={{ color: 'var(--danger)' }}>{handleEditError}</p>
                          )}
                        </div>
                      )}
                    </div>

                    {/* Add platform connect form */}
                    {!profile && addingPlatform === platformKey && (
                      <div className="mt-4 pt-3 border-t space-y-2" style={{ borderColor: 'var(--border)' }}>
                        <div className="flex gap-2">
                          <input
                            type="text"
                            placeholder={`${label} handle`}
                            value={handle}
                            onChange={(e) => setHandle(e.target.value)}
                            className="flex-1 rounded border px-3 py-1.5 text-xs font-mono-editorial outline-none focus:border-[var(--accent)]"
                            style={{ backgroundColor: 'var(--bg-elevated)', borderColor: 'var(--border)', color: 'var(--text-primary)' }}
                            onKeyDown={(e) => e.key === 'Enter' && handleAdd(platformKey)}
                          />
                          <button
                            type="button"
                            onClick={() => handleAdd(platformKey)}
                            disabled={addMutation.isPending}
                            className="btn-editorial rounded px-3 py-1.5 text-xs font-medium disabled:opacity-50"
                          >
                            {addMutation.isPending ? 'Connecting...' : 'Save'}
                          </button>
                          <button
                            type="button"
                            onClick={() => setAddingPlatform(null)}
                            className="px-2 py-1.5 text-xs font-medium text-muted hover:text-[var(--text-primary)]"
                          >
                            Cancel
                          </button>
                        </div>
                        {formError && <p className="text-[11px]" style={{ color: 'var(--danger)' }}>{formError}</p>}
                      </div>
                    )}

                    {!profile && addingPlatform !== platformKey && (
                      <div className="mt-4 pt-3 border-t" style={{ borderColor: 'var(--border)' }}>
                        <button
                          type="button"
                          onClick={() => {
                            setAddingPlatform(platformKey);
                            setHandle('');
                            setFormError('');
                          }}
                          className="text-xs font-semibold hover:underline"
                          style={{ color: 'var(--accent)' }}
                        >
                          + Connect {label}
                        </button>
                      </div>
                    )}

                    {/* Verification flow */}
                    {profile && !profile.verified && canVerify && verifyInfo && (
                      <div className="mt-3 pt-3 border-t" style={{ borderColor: 'var(--border)' }}>
                        {verifyingId === profile.id && verifyToken ? (
                          <div className="space-y-2 p-3 rounded border" style={{ backgroundColor: 'var(--bg-elevated)', borderColor: 'var(--border)' }}>
                            <p className="text-[11px]" style={{ color: 'var(--text-secondary)' }}>
                              {verifyInstructions || `Add token to your ${verifyInfo.field} field:`}
                            </p>
                            <div className="flex items-center gap-2">
                              <code className="rounded px-2 py-1 font-mono-editorial text-xs font-bold" style={{ backgroundColor: 'var(--bg-hover)', color: 'var(--accent)' }}>
                                {verifyToken}
                              </code>
                            </div>
                            <div className="flex items-center gap-2 pt-1">
                              <a
                                href={verifyInfo.url}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="rounded border px-2.5 py-1 text-[11px] font-medium"
                                style={{ borderColor: 'var(--border)', color: 'var(--accent)' }}
                              >
                                Open Profile Settings
                              </a>
                              <button
                                type="button"
                                onClick={() => handleCheckVerify(profile.id)}
                                className="btn-editorial rounded px-2.5 py-1 text-[11px] font-medium"
                              >
                                Check Verification
                              </button>
                              <button
                                type="button"
                                onClick={() => {
                                  setVerifyingId(null);
                                  setVerifyToken(null);
                                  setVerifyInstructions(null);
                                }}
                                className="text-[11px] text-muted hover:text-[var(--text-primary)]"
                              >
                                Cancel
                              </button>
                            </div>
                          </div>
                        ) : (
                          <button
                            type="button"
                            onClick={() => handleStartVerify(profile)}
                            className="text-xs font-semibold hover:underline"
                            style={{ color: 'var(--accent)' }}
                          >
                            Verify Handle Ownership →
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* ── SECTION: APPEARANCE ── */}
      {activeSection === 'APPEARANCE' && <AppearanceSettings />}

      {/* ── SECTION: NOTIFICATIONS ── */}
      {activeSection === 'NOTIFICATIONS' && (
        <div className="bento-card p-6 space-y-6">
          <div className="pb-3 border-b" style={{ borderColor: 'var(--border)' }}>
            <h2 className="text-sm font-bold uppercase tracking-wider" style={{ color: 'var(--text-secondary)' }}>
              Notifications & Reminder Alerts
            </h2>
            <p className="text-xs text-muted mt-0.5">
              Control dispatch methods for contest start reminders.
            </p>
          </div>

          <div className="space-y-4">
            <label
              className="flex items-center justify-between cursor-pointer p-3.5 rounded border"
              style={{ backgroundColor: 'var(--bg-elevated)', borderColor: 'var(--border)' }}
            >
              <div>
                <span className="text-xs font-bold block" style={{ color: 'var(--text-primary)' }}>
                  Email Reminders
                </span>
                <span className="text-[11px] text-muted">
                  Receive email digests 15 minutes prior to registered contests
                </span>
              </div>
              <input
                type="checkbox"
                checked={emailNotif}
                onChange={(e) => setEmailNotif(e.target.checked)}
                className="h-4 w-4 rounded cursor-pointer"
                style={{ accentColor: 'var(--accent)' }}
              />
            </label>

            <label
              className="flex items-center justify-between cursor-pointer p-3.5 rounded border"
              style={{ backgroundColor: 'var(--bg-elevated)', borderColor: 'var(--border)' }}
            >
              <div>
                <span className="text-xs font-bold block" style={{ color: 'var(--text-primary)' }}>
                  Browser Desktop Notifications
                </span>
                <span className="text-[11px] text-muted">
                  Display browser alerts for countdown milestones
                </span>
              </div>
              <input
                type="checkbox"
                checked={browserNotif}
                onChange={(e) => setBrowserNotif(e.target.checked)}
                className="h-4 w-4 rounded cursor-pointer"
                style={{ accentColor: 'var(--accent)' }}
              />
            </label>
          </div>

          <div className="pt-4 border-t flex justify-end" style={{ borderColor: 'var(--border)' }}>
            <button
              type="button"
              onClick={() => prefMutation.mutate()}
              disabled={prefMutation.isPending}
              className="btn-editorial rounded px-5 py-2.5 text-xs font-medium disabled:opacity-50"
            >
              {prefMutation.isPending ? 'Saving Alerts...' : 'Save Alert Preferences'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

const MODE_OPTIONS: { value: Mode; label: string; desc: string }[] = [
  { value: 'dark', label: 'Dark', desc: 'Dark background, light text' },
  { value: 'light', label: 'Light', desc: 'Light background, dark text' },
  { value: 'system', label: 'System', desc: 'Match your OS preference' },
];

function AppearanceSettings() {
  const theme = useTheme();
  const [localMode, setLocalMode] = useState<Mode>(theme.mode);
  const [localAccent, setLocalAccent] = useState<string>(theme.accentColor ?? theme.defaultAccent);

  const isValidHex = /^#[0-9a-fA-F]{6}$/.test(localAccent);
  const hasChanges = localMode !== theme.mode || localAccent !== (theme.accentColor ?? theme.defaultAccent);
  const defaultForMode = localMode === 'dark' || (localMode === 'system' && theme.resolvedMode === 'dark') ? DEFAULT_ACCENT_DARK : DEFAULT_ACCENT_LIGHT;

  function handlePreview() {
    theme.setDraft({ mode: localMode, accentColor: localAccent === defaultForMode ? null : localAccent });
    theme.startPreview();
  }

  function handleApply() {
    theme.applyDraft({ mode: localMode, accentColor: localAccent === defaultForMode ? null : localAccent });
  }

  function handleCancel() {
    if (theme.previewActive) theme.cancelDraft();
    setLocalMode(theme.mode);
    setLocalAccent(theme.accentColor ?? theme.defaultAccent);
  }

  function handleReset() {
    theme.resetDefaults();
    setLocalMode('system');
    setLocalAccent(theme.resolvedMode === 'dark' ? DEFAULT_ACCENT_DARK : DEFAULT_ACCENT_LIGHT);
  }

  return (
    <div className="space-y-5">
      {/* Display Mode */}
      <div className="bento-card p-6 space-y-4">
        <div className="pb-3 border-b" style={{ borderColor: 'var(--border)' }}>
          <h2 className="text-sm font-bold uppercase tracking-wider" style={{ color: 'var(--text-secondary)' }}>
            Display Mode
          </h2>
          <p className="text-xs text-muted mt-0.5">Choose how Contest Compass appears on your screen</p>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3" role="radiogroup" aria-label="Display mode">
          {MODE_OPTIONS.map(({ value, label, desc }) => (
            <button
              key={value}
              type="button"
              role="radio"
              aria-checked={localMode === value}
              onClick={() => setLocalMode(value)}
              className="p-3.5 rounded border text-left transition-all"
              style={{
                backgroundColor: localMode === value ? 'var(--accent-subtle)' : 'var(--bg-elevated)',
                borderColor: localMode === value ? 'var(--accent)' : 'var(--border)',
              }}
            >
              <span className="text-xs font-bold block" style={{ color: localMode === value ? 'var(--text-primary)' : 'var(--text-secondary)' }}>
                {label}
              </span>
              <span className="text-[10px] mt-0.5 block" style={{ color: 'var(--text-muted)' }}>{desc}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Accent Color */}
      <div className="bento-card p-6 space-y-4">
        <div className="pb-3 border-b" style={{ borderColor: 'var(--border)' }}>
          <h2 className="text-sm font-bold uppercase tracking-wider" style={{ color: 'var(--text-secondary)' }}>
            Accent Color
          </h2>
          <p className="text-xs text-muted mt-0.5">Customize the highlight color used for buttons, indicators, and interactive elements</p>
        </div>
        <div className="flex items-center gap-3">
          <input
            type="color"
            value={isValidHex ? localAccent : '#000000'}
            onChange={(e) => setLocalAccent(e.target.value)}
            className="h-9 w-9 cursor-pointer rounded border appearance-none shrink-0"
            style={{ borderColor: 'var(--border)', backgroundColor: isValidHex ? localAccent : 'transparent' }}
            aria-label="Choose accent color"
          />
          <input
            type="text"
            value={localAccent}
            onChange={(e) => setLocalAccent(e.target.value)}
            maxLength={7}
            spellCheck={false}
            className="w-28 rounded border px-2.5 py-2 text-xs font-mono-editorial outline-none focus:border-[var(--accent)]"
            style={{ backgroundColor: 'var(--bg-elevated)', borderColor: isValidHex ? 'var(--border)' : 'var(--danger)', color: 'var(--text-primary)' }}
            aria-label="Accent hex value"
            aria-invalid={!isValidHex}
          />
          <button
            type="button"
            onClick={() => setLocalAccent(defaultForMode)}
            className="rounded border px-3 py-2 text-[11px] font-medium font-mono-editorial transition-colors hover:bg-[var(--bg-hover)]"
            style={{ borderColor: 'var(--border)', color: 'var(--text-muted)' }}
          >
            Reset to default
          </button>
          {isValidHex && (
            <div className="flex items-center gap-2">
              <div className="h-4 w-4 rounded-sm" style={{ backgroundColor: localAccent }} />
              <span className="text-[10px] font-mono-editorial" style={{ color: 'var(--text-muted)' }}>Preview</span>
            </div>
          )}
        </div>
        {!isValidHex && localAccent.length > 0 && (
          <p className="text-[10px]" style={{ color: 'var(--danger)' }}>Enter a valid hex color (e.g. #3B82F6)</p>
        )}
      </div>

      {/* Actions */}
      <div className="flex flex-wrap items-center gap-2">
        {hasChanges && isValidHex && (
          <button type="button" onClick={handlePreview} className="btn-editorial-outline rounded px-4 py-2 text-xs font-medium">
            Preview changes
          </button>
        )}
        <button
          type="button"
          onClick={handleApply}
          disabled={!isValidHex}
          className="btn-editorial rounded px-4 py-2 text-xs font-medium disabled:opacity-40"
        >
          Apply
        </button>
        {(hasChanges || theme.previewActive) && (
          <button type="button" onClick={handleCancel} className="rounded px-3 py-2 text-xs font-medium transition-colors hover:bg-[var(--bg-hover)]" style={{ color: 'var(--text-muted)' }}>
            Cancel
          </button>
        )}
        <button type="button" onClick={handleReset} className="ml-auto rounded px-3 py-2 text-[11px] font-medium font-mono-editorial transition-colors hover:bg-[var(--bg-hover)]" style={{ color: 'var(--text-muted)' }}>
          Reset all defaults
        </button>
      </div>

      {theme.previewActive && (
        <div className="rounded border px-4 py-2 text-[11px] font-mono-editorial" style={{ backgroundColor: 'var(--accent-subtle)', borderColor: 'var(--accent)', color: 'var(--accent)' }}>
          Preview active — Apply to save your changes, or Cancel to revert.
        </div>
      )}
    </div>
  );
}
