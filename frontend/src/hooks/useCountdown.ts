import { useState, useEffect } from 'react';

interface CountdownResult {
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
  total: number;
  label: string;
  isLive: boolean;
  isPast: boolean;
}

export function useCountdown(startTime: string, endTime: string | null): CountdownResult {
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const interval = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(interval);
  }, []);

  const start = new Date(startTime).getTime();
  const end = endTime ? new Date(endTime).getTime() : null;
  const isLive = now >= start && (end === null || now < end);
  const isPast = end !== null && now >= end;

  const target = isLive && end ? end : start;
  const diff = Math.max(0, target - now);

  const totalSeconds = Math.floor(diff / 1000);
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  let label: string;
  if (isPast) {
    label = 'Ended';
  } else if (isLive) {
    label = `Ends in ${formatCompact(days, hours, minutes, seconds)}`;
  } else if (diff === 0) {
    label = 'Starting now';
  } else {
    label = `Starts in ${formatCompact(days, hours, minutes, seconds)}`;
  }

  return { days, hours, minutes, seconds, total: totalSeconds, label, isLive, isPast };
}

function formatCompact(d: number, h: number, m: number, s: number): string {
  if (d > 0) return `${d}d ${h}h`;
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m ${s}s`;
}
