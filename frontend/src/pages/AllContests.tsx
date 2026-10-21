import { useQuery } from '@tanstack/react-query';
import { fetchContests } from '../api/contests';
import { ContestCard, ContestCardSkeleton } from '../components/ContestCard';
import type { Platform, ContestStatus } from '../types/contest';
import { PLATFORM_LABELS, ALL_PLATFORMS } from '../types/contest';
import { useState } from 'react';

const STATUS_OPTIONS: { value: ContestStatus[]; label: string }[] = [
  { value: ['UPCOMING', 'LIVE', 'FINISHED'], label: 'All Contests' },
  { value: ['UPCOMING', 'LIVE'], label: 'Active & Upcoming' },
  { value: ['UPCOMING'], label: 'Upcoming Only' },
  { value: ['LIVE'], label: 'Live Now' },
  { value: ['FINISHED'], label: 'Past Contests' },
];

export function AllContests() {
  const [platform, setPlatform] = useState<Platform | undefined>();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [statusIdx, setStatusIdx] = useState(0);
  const statuses = STATUS_OPTIONS[statusIdx].value;

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['contests', 'all', platform, search, page, statuses],
    queryFn: () =>
      fetchContests({
        status: statuses,
        platform,
        search: search || undefined,
        page,
        size: 18,
      }),
  });

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
            All Contests
          </h1>
          <p className="mt-0.5 text-xs" style={{ color: 'var(--text-muted)' }}>
            Filter, search, and discover scheduled competitive programming rounds
          </p>
        </div>

        {data && (
          <div
            className="inline-flex items-center gap-2 rounded px-2.5 py-1 text-xs font-mono-editorial border"
            style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border)' }}
          >
            <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: 'var(--accent)' }} />
            <span className="tabular-nums" style={{ color: 'var(--text-primary)' }}>
              {data.totalElements} contests indexed
            </span>
          </div>
        )}
      </div>

      {/* Filter & Search Bar */}
      <div className="bento-card p-3.5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2.5">
            {/* Platform Selector */}
            <select
              value={platform ?? ''}
              onChange={(e) => {
                setPlatform((e.target.value || undefined) as Platform | undefined);
                setPage(0);
              }}
              className="rounded border px-2.5 py-1.5 text-xs font-medium outline-none transition-colors focus:border-[var(--accent)]"
              style={{
                backgroundColor: 'var(--bg-elevated)',
                borderColor: 'var(--border)',
                color: 'var(--text-primary)',
              }}
            >
              <option value="">All Platforms</option>
              {ALL_PLATFORMS.map((p) => (
                <option key={p} value={p}>
                  {PLATFORM_LABELS[p]}
                </option>
              ))}
            </select>

            {/* Status Dropdown */}
            <select
              value={statusIdx}
              onChange={(e) => {
                setStatusIdx(Number(e.target.value));
                setPage(0);
              }}
              className="rounded border px-2.5 py-1.5 text-xs font-medium outline-none transition-colors focus:border-[var(--accent)]"
              style={{
                backgroundColor: 'var(--bg-elevated)',
                borderColor: 'var(--border)',
                color: 'var(--text-primary)',
              }}
            >
              {STATUS_OPTIONS.map((opt, i) => (
                <option key={i} value={i}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>

          {/* Search Box */}
          <div className="relative flex-1 sm:flex-initial">
            <svg
              className="absolute left-3 top-1/2 -translate-y-1/2"
              width="13"
              height="13"
              viewBox="0 0 24 24"
              fill="none"
              stroke="var(--text-muted)"
              strokeWidth="2"
            >
              <circle cx="11" cy="11" r="8" />
              <path d="M21 21l-4.35-4.35" />
            </svg>
            <input
              type="text"
              placeholder="Search by contest title..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
              className="w-full sm:w-64 rounded border py-1.5 pl-8 pr-3 text-xs outline-none transition-colors focus:border-[var(--accent)]"
              style={{
                backgroundColor: 'var(--bg-elevated)',
                borderColor: 'var(--border)',
                color: 'var(--text-primary)',
              }}
            />
          </div>
        </div>
      </div>

      {/* Loading Skeletons */}
      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <ContestCardSkeleton key={i} />
          ))}
        </div>
      )}

      {/* Error Card */}
      {error && (
        <div className="bento-card p-8 text-center">
          <p className="text-sm font-semibold" style={{ color: 'var(--danger)' }}>
            Failed to retrieve contest records
          </p>
          <p className="text-xs text-muted mt-1">Check backend connection on port 8080.</p>
          <button
            onClick={() => refetch()}
            className="btn-editorial-outline mt-3 rounded px-4 py-1.5 text-xs font-medium"
          >
            Retry Query
          </button>
        </div>
      )}

      {/* Contest Grid */}
      {!isLoading && !error && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {data?.content.map((c) => (
              <ContestCard key={c.id} contest={c} />
            ))}
          </div>

          {data && data.content.length === 0 && (
            <div className="bento-card py-16 text-center">
              <p className="text-sm font-semibold" style={{ color: 'var(--text-secondary)' }}>
                No contests match your query
              </p>
              <button
                onClick={() => {
                  setPlatform(undefined);
                  setSearch('');
                  setStatusIdx(0);
                }}
                className="mt-3 text-xs font-semibold hover:underline"
                style={{ color: 'var(--accent)' }}
              >
                Reset search & filters
              </button>
            </div>
          )}

          {/* Pagination Controls */}
          {data && data.totalPages > 1 && (
            <div className="flex items-center justify-center gap-3 pt-4">
              <button
                onClick={() => setPage(Math.max(0, page - 1))}
                disabled={page === 0}
                className="btn-editorial-outline rounded px-3.5 py-1.5 text-xs font-medium disabled:opacity-30"
              >
                Previous
              </button>
              <span className="text-xs font-medium font-mono-editorial tabular-nums" style={{ color: 'var(--text-muted)' }}>
                Page {page + 1} of {data.totalPages}
              </span>
              <button
                onClick={() => setPage(Math.min(data.totalPages - 1, page + 1))}
                disabled={page >= data.totalPages - 1}
                className="btn-editorial-outline rounded px-3.5 py-1.5 text-xs font-medium disabled:opacity-30"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
