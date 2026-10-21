import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchProfiles,
  addProfile,
  deleteProfile,
  startVerification,
  checkVerification,
  refreshProfile,
} from '../api/contests';
import { uploadAvatar, deleteAvatar } from '../api/auth';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { Link } from 'react-router-dom';
import { useState, useRef } from 'react';
import type { ChangeEvent } from 'react';
import { PLATFORM_LABELS, PLATFORM_URLS, PLATFORM_COLORS, ALL_PLATFORMS } from '../types/contest';
import type { Platform, ConnectedProfile } from '../types/contest';

const VERIFY_INSTRUCTIONS: Record<string, { field: string; url: string }> = {
  CODEFORCES: { field: 'organization', url: 'https://codeforces.com/settings/social' },
  LEETCODE: { field: 'website or summary', url: 'https://leetcode.com/profile/' },
  ATCODER: { field: 'affiliation', url: 'https://atcoder.jp/settings' },
};

export function Profiles() {
  const { user, refreshUser } = useAuth();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [addingPlatform, setAddingPlatform] = useState<string | null>(null);
  const [handle, setHandle] = useState('');
  const [formError, setFormError] = useState('');
  const [verifyingId, setVerifyingId] = useState<number | null>(null);
  const [verifyToken, setVerifyToken] = useState<string | null>(null);
  const [verifyInstructions, setVerifyInstructions] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);

  const { data: profiles, isLoading, error, refetch } = useQuery({
    queryKey: ['profiles'],
    queryFn: fetchProfiles,
    enabled: !!user,
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
          await refreshProfile(created.id);
          queryClient.invalidateQueries({ queryKey: ['profiles'] });
          queryClient.invalidateQueries({ queryKey: ['performance'] });
          toast('Profile data synchronized', 'success');
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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profiles'] });
      queryClient.invalidateQueries({ queryKey: ['performance'] });
      toast('Profile data refreshed', 'success');
    },
    onError: () => toast('Failed to refresh profile data', 'error'),
  });

  if (!user) {
    return (
      <div className="mx-auto max-w-md pt-20 text-center">
        <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl" style={{ backgroundColor: 'var(--accent-subtle)' }}>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" strokeWidth="2.5">
            <path d="M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0zM12 14a7 7 0 0 0-7 7h14a7 7 0 0 0-7-7z" />
          </svg>
        </div>
        <h1 className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>
          Connected Profiles
        </h1>
        <p className="mb-6 text-xs" style={{ color: 'var(--text-muted)' }}>
          Sign in to link and verify your competitive programming accounts.
        </p>
        <Link to="/login" className="inline-block rounded-xl px-5 py-2.5 text-xs font-semibold text-white shadow-xs" style={{ backgroundColor: 'var(--accent)' }}>
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

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      {/* Page Header */}
      <div>
        <h1 className="text-2xl font-black tracking-tight" style={{ color: 'var(--text-primary)' }}>
          Profile Management
        </h1>
        <p className="mt-0.5 text-xs" style={{ color: 'var(--text-muted)' }}>
          Connect your handles, verify ownership, and upload your profile photo
        </p>
      </div>

      {/* Avatar Bento Card */}
      <div className="bento-card p-6">
        <h2 className="text-sm font-bold uppercase tracking-wider mb-4" style={{ color: 'var(--text-secondary)' }}>
          Profile Picture
        </h2>
        <div className="flex flex-col sm:flex-row items-center gap-6">
          <div
            className="flex h-20 w-20 shrink-0 items-center justify-center overflow-hidden rounded-full ring-4 shadow-sm"
            style={{
              backgroundColor: 'var(--bg-hover)',
              boxShadow: '0 0 0 4px var(--accent-subtle)',
            }}
          >
            {user.avatarUrl ? (
              <img src={user.avatarUrl} alt="" className="h-full w-full object-cover" />
            ) : (
              <span className="text-2xl font-black" style={{ color: 'var(--accent)' }}>
                {user.username.charAt(0).toUpperCase()}
              </span>
            )}
          </div>

          <div className="space-y-2 text-center sm:text-left">
            <div className="flex flex-wrap items-center justify-center sm:justify-start gap-2.5">
              <input
                ref={fileInputRef}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                className="hidden"
                onChange={handleAvatarUpload}
              />
              <button
                onClick={() => fileInputRef.current?.click()}
                disabled={uploadingAvatar}
                className="rounded-xl px-4 py-2 text-xs font-semibold text-white shadow-xs transition-opacity hover:opacity-90 disabled:opacity-50"
                style={{ backgroundColor: 'var(--accent)' }}
              >
                {uploadingAvatar ? 'Uploading...' : user.avatarUrl ? 'Change Avatar' : 'Upload Avatar'}
              </button>
              {user.avatarUrl && (
                <button
                  onClick={handleAvatarDelete}
                  className="rounded-xl border px-3 py-2 text-xs font-semibold transition-colors hover:bg-[var(--bg-hover)]"
                  style={{ borderColor: 'var(--border)', color: 'var(--danger)' }}
                >
                  Remove
                </button>
              )}
            </div>
            <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
              Supports JPEG, PNG, or WebP up to 2MB. Displayed on your performance dashboard.
            </p>
          </div>
        </div>
      </div>

      {/* Connected Platforms Grid */}
      <div className="space-y-4">
        <h2 className="text-sm font-bold uppercase tracking-wider" style={{ color: 'var(--text-secondary)' }}>
          Coding Platform Accounts
        </h2>

        {isLoading && (
          <div className="space-y-3">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="h-20 animate-pulse rounded-2xl" style={{ backgroundColor: 'var(--bg-card)' }} />
            ))}
          </div>
        )}

        {error && (
          <div className="bento-card p-8 text-center">
            <p className="text-sm font-semibold" style={{ color: 'var(--danger)' }}>
              Failed to load profile connections
            </p>
            <button
              onClick={() => refetch()}
              className="mt-3 rounded-xl border px-4 py-1.5 text-xs font-semibold hover:bg-[var(--bg-hover)]"
              style={{ borderColor: 'var(--border)', color: 'var(--text-primary)' }}
            >
              Retry
            </button>
          </div>
        )}

        {!isLoading && !error && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {ALL_PLATFORMS.map((platformKey: Platform) => {
              const profile = connectedMap.get(platformKey);
              const color = PLATFORM_COLORS[platformKey] ?? 'var(--text-muted)';
              const label = PLATFORM_LABELS[platformKey];
              const canVerify = platformKey !== 'CODECHEF';
              const verifyInfo = VERIFY_INSTRUCTIONS[platformKey];

              return (
                <div key={platformKey} className="bento-card p-5 flex flex-col justify-between">
                  <div>
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex items-center gap-3">
                        <div
                          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl font-bold text-xs shadow-xs"
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
                                className="rounded-md px-1.5 py-0.5 text-[9px] font-bold text-white"
                                style={{ backgroundColor: 'var(--success)' }}
                              >
                                Verified
                              </span>
                            )}
                            {profile && !profile.verified && (
                              <span className="text-[10px] text-muted">Unverified</span>
                            )}
                            {!profile && (
                              <span className="text-[10px] text-muted">Disconnected</span>
                            )}
                          </div>
                          {profile ? (
                            <p className="mt-0.5 text-xs" style={{ color: 'var(--text-muted)' }}>
                              @{profile.handle}
                              {profile.rating != null && <span> • {profile.rating} rating</span>}
                            </p>
                          ) : (
                            <p className="mt-0.5 text-xs text-muted">Link to sync ratings</p>
                          )}
                        </div>
                      </div>

                      {/* Card Actions */}
                      <div className="flex items-center gap-1.5">
                        {profile && platformKey !== 'CODECHEF' && (
                          <button
                            onClick={() => refreshMutation.mutate(profile.id)}
                            disabled={refreshMutation.isPending}
                            className="p-1.5 rounded-lg border hover:bg-[var(--bg-hover)] transition-colors disabled:opacity-50"
                            style={{ borderColor: 'var(--border)', color: 'var(--text-secondary)' }}
                            title="Synchronize profile data"
                          >
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                              <path d="M21 2v6h-6M3 12a9 9 0 0 1 15-6.7L21 8M3 22v-6h6M21 12a9 9 0 0 1-15 6.7L3 16" />
                            </svg>
                          </button>
                        )}
                        {profile && (
                          <button
                            onClick={() => deleteMutation.mutate(profile.id)}
                            disabled={deleteMutation.isPending}
                            className="p-1.5 rounded-lg border hover:bg-[var(--bg-hover)] transition-colors disabled:opacity-50"
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

                    {profile?.lastSyncStatus === 'FAILED' && profile.lastSyncError && (
                      <p className="mt-2 text-[11px]" style={{ color: 'var(--danger)' }}>
                        Sync error: {profile.lastSyncError}
                      </p>
                    )}
                  </div>

                  {/* Add Platform Input Form */}
                  {!profile && addingPlatform === platformKey && (
                    <div className="mt-4 pt-3 border-t space-y-2" style={{ borderColor: 'var(--border)' }}>
                      <div className="flex gap-2">
                        <input
                          type="text"
                          placeholder={`${label} handle`}
                          value={handle}
                          onChange={(e) => setHandle(e.target.value)}
                          className="flex-1 rounded-xl border px-3 py-1.5 text-xs outline-none focus:border-[var(--accent)]"
                          style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border)', color: 'var(--text-primary)' }}
                          onKeyDown={(e) => e.key === 'Enter' && handleAdd(platformKey)}
                        />
                        <button
                          onClick={() => handleAdd(platformKey)}
                          disabled={addMutation.isPending}
                          className="rounded-xl px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
                          style={{ backgroundColor: 'var(--accent)' }}
                        >
                          {addMutation.isPending ? 'Connecting...' : 'Save'}
                        </button>
                        <button
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

                  {/* Verification Flow */}
                  {profile && !profile.verified && canVerify && verifyInfo && (
                    <div className="mt-3 pt-3 border-t" style={{ borderColor: 'var(--border)' }}>
                      {verifyingId === profile.id && verifyToken ? (
                        <div className="space-y-2 p-3 rounded-xl border" style={{ backgroundColor: 'var(--bg-card-subtle)', borderColor: 'var(--border-subtle)' }}>
                          <p className="text-[11px]" style={{ color: 'var(--text-secondary)' }}>
                            {verifyInstructions || `Add token to your ${verifyInfo.field} field:`}
                          </p>
                          <div className="flex items-center gap-2">
                            <code className="rounded-lg px-2 py-1 font-mono text-xs font-bold" style={{ backgroundColor: 'var(--bg-hover)', color: 'var(--accent)' }}>
                              {verifyToken}
                            </code>
                          </div>
                          <div className="flex items-center gap-2 pt-1">
                            <a
                              href={verifyInfo.url}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="rounded-lg border px-2.5 py-1 text-[11px] font-medium"
                              style={{ borderColor: 'var(--border)', color: 'var(--accent)' }}
                            >
                              Open Settings
                            </a>
                            <button
                              onClick={() => handleCheckVerify(profile.id)}
                              className="rounded-lg px-2.5 py-1 text-[11px] font-semibold text-white"
                              style={{ backgroundColor: 'var(--accent)' }}
                            >
                              Check
                            </button>
                            <button
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
    </div>
  );
}
