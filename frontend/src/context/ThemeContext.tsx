import { createContext, useContext, useState, useEffect, useCallback, useRef, type ReactNode } from 'react';

type Mode = 'dark' | 'light' | 'system';

const STORAGE_KEY = 'contestradar-appearance';
const DEFAULT_MODE: Mode = 'system';
const DEFAULT_ACCENT_DARK = '#D4D4D4';
const DEFAULT_ACCENT_LIGHT = '#404040';

interface Appearance {
  mode: Mode;
  accentColor: string | null;
}

interface ThemeState {
  mode: Mode;
  resolvedMode: 'dark' | 'light';
  accentColor: string | null;
  defaultAccent: string;
  previewActive: boolean;
  setMode: (m: Mode) => void;
  setAccent: (c: string | null) => void;
  applyDraft: (override?: Appearance) => void;
  cancelDraft: () => void;
  resetDefaults: () => void;
  startPreview: () => void;
  draft: Appearance;
  setDraft: (d: Appearance) => void;
}

const ThemeContext = createContext<ThemeState | null>(null);

function resolveSystemMode(): 'dark' | 'light' {
  if (typeof window === 'undefined') return 'dark';
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function loadSaved(): Appearance {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      const legacy = localStorage.getItem('contestradar-theme');
      if (legacy === 'light') return { mode: 'light', accentColor: null };
      if (legacy === 'dark') return { mode: 'dark', accentColor: null };
      return { mode: DEFAULT_MODE, accentColor: null };
    }
    const parsed = JSON.parse(raw);
    const mode: Mode = ['dark', 'light', 'system'].includes(parsed.mode) ? parsed.mode : DEFAULT_MODE;
    const accentColor = typeof parsed.accentColor === 'string' && /^#[0-9a-fA-F]{6}$/.test(parsed.accentColor)
      ? parsed.accentColor
      : null;
    return { mode, accentColor };
  } catch {
    return { mode: DEFAULT_MODE, accentColor: null };
  }
}

function savePref(appearance: Appearance) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(appearance));
  localStorage.removeItem('contestradar-theme');
}

function luminance(hex: string): number {
  const r = parseInt(hex.slice(1, 3), 16) / 255;
  const g = parseInt(hex.slice(3, 5), 16) / 255;
  const b = parseInt(hex.slice(5, 7), 16) / 255;
  const [rl, gl, bl] = [r, g, b].map(c => c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
  return 0.2126 * rl + 0.7152 * gl + 0.0722 * bl;
}

function hexToRgb(hex: string): [number, number, number] {
  return [
    parseInt(hex.slice(1, 3), 16),
    parseInt(hex.slice(3, 5), 16),
    parseInt(hex.slice(5, 7), 16),
  ];
}

function mixHex(hex: string, target: string, ratio: number): string {
  const [r1, g1, b1] = hexToRgb(hex);
  const [r2, g2, b2] = hexToRgb(target);
  const mix = (a: number, b: number) => Math.round(a + (b - a) * ratio);
  return `#${[mix(r1, r2), mix(g1, g2), mix(b1, b2)].map(v => v.toString(16).padStart(2, '0')).join('')}`;
}

function applyAccentVars(accent: string, isDark: boolean) {
  const el = document.documentElement;
  const [r, g, b] = hexToRgb(accent);
  const lum = luminance(accent);
  const hoverTarget = isDark ? '#ffffff' : '#000000';
  const hoverColor = mixHex(accent, hoverTarget, 0.15);
  const secondaryColor = mixHex(accent, hoverTarget, 0.1);
  const btnText = lum > 0.4 ? '#0D0D0D' : '#F4F4F4';

  el.style.setProperty('--accent', accent);
  el.style.setProperty('--accent-secondary', secondaryColor);
  el.style.setProperty('--accent-hover', hoverColor);
  el.style.setProperty('--accent-subtle', `rgba(${r},${g},${b},${isDark ? 0.12 : 0.08})`);
  el.style.setProperty('--accent-muted', `rgba(${r},${g},${b},${isDark ? 0.18 : 0.14})`);
  el.style.setProperty('--accent-glow', `rgba(${r},${g},${b},${isDark ? 0.25 : 0.20})`);
  el.style.setProperty('--accent-noir', accent);
  el.style.setProperty('--ring', `rgba(${r},${g},${b},${isDark ? 0.4 : 0.3})`);
  el.style.setProperty('--btn-primary-bg', accent);
  el.style.setProperty('--btn-primary-text', btnText);
  el.style.setProperty('--btn-primary-hover', hoverColor);
}

function clearAccentVars() {
  const el = document.documentElement;
  const props = [
    '--accent', '--accent-secondary', '--accent-hover', '--accent-subtle',
    '--accent-muted', '--accent-glow', '--accent-noir', '--ring',
    '--btn-primary-bg', '--btn-primary-text', '--btn-primary-hover',
  ];
  props.forEach(p => el.style.removeProperty(p));
}

function applyMode(resolved: 'dark' | 'light') {
  const el = document.documentElement;
  el.setAttribute('data-theme', resolved);
  if (resolved === 'dark') {
    el.classList.add('dark');
  } else {
    el.classList.remove('dark');
  }
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [saved, setSaved] = useState<Appearance>(loadSaved);
  const [draft, setDraft] = useState<Appearance>(saved);
  const [previewActive, setPreviewActive] = useState(false);
  const [systemMode, setSystemMode] = useState<'dark' | 'light'>(resolveSystemMode);
  const savedRef = useRef(saved);
  useEffect(() => { savedRef.current = saved; }, [saved]);

  useEffect(() => {
    const mql = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = (e: MediaQueryListEvent) => setSystemMode(e.matches ? 'dark' : 'light');
    mql.addEventListener('change', handler);
    return () => mql.removeEventListener('change', handler);
  }, []);

  const resolve = (mode: Mode): 'dark' | 'light' => mode === 'system' ? systemMode : mode;

  const active = previewActive ? draft : saved;
  const resolvedMode = resolve(active.mode);
  const defaultAccent = resolvedMode === 'dark' ? DEFAULT_ACCENT_DARK : DEFAULT_ACCENT_LIGHT;

  useEffect(() => {
    applyMode(resolvedMode);
    if (active.accentColor) {
      applyAccentVars(active.accentColor, resolvedMode === 'dark');
    } else {
      clearAccentVars();
    }
  }, [resolvedMode, active.accentColor]);

  const setMode = useCallback((m: Mode) => {
    if (previewActive) {
      setDraft(prev => ({ ...prev, mode: m }));
    } else {
      const next = { ...saved, mode: m };
      setSaved(next);
      setDraft(next);
      savePref(next);
    }
  }, [previewActive, saved]);

  const setAccent = useCallback((c: string | null) => {
    if (previewActive) {
      setDraft(prev => ({ ...prev, accentColor: c }));
    } else {
      const next = { ...saved, accentColor: c };
      setSaved(next);
      setDraft(next);
      savePref(next);
    }
  }, [previewActive, saved]);

  const startPreview = useCallback(() => {
    setPreviewActive(true);
  }, []);

  const applyDraft = useCallback((override?: Appearance) => {
    const toSave = override ?? draft;
    setSaved(toSave);
    setDraft(toSave);
    savePref(toSave);
    setPreviewActive(false);
  }, [draft]);

  const cancelDraft = useCallback(() => {
    setDraft(savedRef.current);
    setPreviewActive(false);
  }, []);

  const resetDefaults = useCallback(() => {
    const defaults: Appearance = { mode: DEFAULT_MODE, accentColor: null };
    setSaved(defaults);
    setDraft(defaults);
    savePref(defaults);
    setPreviewActive(false);
  }, []);

  return (
    <ThemeContext.Provider
      value={{
        mode: active.mode,
        resolvedMode,
        accentColor: active.accentColor,
        defaultAccent,
        previewActive,
        setMode,
        setAccent,
        applyDraft,
        cancelDraft,
        resetDefaults,
        startPreview,
        draft,
        setDraft,
      }}
    >
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
}

export { DEFAULT_ACCENT_DARK, DEFAULT_ACCENT_LIGHT, DEFAULT_MODE };
export type { Mode, Appearance };
