/**
 * Pure MESHFOSS core domain logic ported directly from:
 *  - ru.mesh.foss.core.MeshCore
 *  - ru.mesh.foss.app.NavTabs
 *  - ru.mesh.foss.ui.AppTheme
 *
 * Kept UI-free for deterministic node:test verification.
 */

export type DesignStyle = 'liquid' | 'frosted' | 'glass' | 'plain';
export type UiLanguageCode = 'ru' | 'en' | 'es' | 'fr' | 'hu';

export type NavTabKey =
  | 'schedule'
  | 'marks'
  | 'homeworks'
  | 'ranking'
  | 'attendance'
  | 'moskvenok'
  | 'food'
  | 'settings';

export interface NavLayout {
  bottom: NavTabKey[];
  more: NavTabKey[];
}

export interface MeshThemePalette {
  id: string;
  nameRu: string;
  nameEn: string;
  primary: string;
  background: string;
  surface: string;
  secondary: string;
  tertiary: string;
  isLight: boolean;
}

export const MESH_THEMES: MeshThemePalette[] = [
  {
    id: 'midnight',
    nameRu: 'Полночь',
    nameEn: 'Midnight',
    primary: '#38BDF8',
    background: '#0B101B',
    surface: '#161F31',
    secondary: '#818CF8',
    tertiary: '#2DD4BF',
    isLight: false,
  },
  {
    id: 'nebula',
    nameRu: 'Туманность',
    nameEn: 'Nebula',
    primary: '#C4B5FD',
    background: '#0B0818',
    surface: '#1C1633',
    secondary: '#A5B4FC',
    tertiary: '#F0ABFC',
    isLight: false,
  },
  {
    id: 'ocean',
    nameRu: 'Океан',
    nameEn: 'Ocean',
    primary: '#67E8F9',
    background: '#071923',
    surface: '#102A3A',
    secondary: '#38BDF8',
    tertiary: '#34D399',
    isLight: false,
  },
  {
    id: 'forest',
    nameRu: 'Лес',
    nameEn: 'Forest',
    primary: '#86EFAC',
    background: '#0D1B16',
    surface: '#183126',
    secondary: '#34D399',
    tertiary: '#FDE047',
    isLight: false,
  },
  {
    id: 'sunset',
    nameRu: 'Закат',
    nameEn: 'Sunset',
    primary: '#FDA4AF',
    background: '#211318',
    surface: '#352027',
    secondary: '#FB923C',
    tertiary: '#F472B6',
    isLight: false,
  },
  {
    id: 'lavender',
    nameRu: 'Лаванда',
    nameEn: 'Lavender',
    primary: '#CFBAFF',
    background: '#1C182B',
    surface: '#312842',
    secondary: '#93C5FD',
    tertiary: '#F9A8D4',
    isLight: false,
  },
  {
    id: 'amber',
    nameRu: 'Янтарь',
    nameEn: 'Amber',
    primary: '#FFD17B',
    background: '#211B10',
    surface: '#39301D',
    secondary: '#F59E0B',
    tertiary: '#FB7185',
    isLight: false,
  },
  {
    id: 'graphite',
    nameRu: 'Графит',
    nameEn: 'Graphite',
    primary: '#E2E8F0',
    background: '#141517',
    surface: '#25282D',
    secondary: '#94A3B8',
    tertiary: '#60A5FA',
    isLight: false,
  },
  {
    id: 'glacier',
    nameRu: 'Ледник',
    nameEn: 'Glacier',
    primary: '#0284C7',
    background: '#E7F5FE',
    surface: '#FFFFFF',
    secondary: '#0EA5E9',
    tertiary: '#6366F1',
    isLight: true,
  },
  {
    id: 'sakura',
    nameRu: 'Сакура',
    nameEn: 'Sakura',
    primary: '#BE185D',
    background: '#FCEEF4',
    surface: '#FFFBFD',
    secondary: '#EC4899',
    tertiary: '#8B5CF6',
    isLight: true,
  },
  {
    id: 'light',
    nameRu: 'Светлая',
    nameEn: 'Light',
    primary: '#2563EB',
    background: '#F1F5F9',
    surface: '#FFFFFF',
    secondary: '#0EA5E9',
    tertiary: '#14B8A6',
    isLight: true,
  },
];

export const ALL_NAV_TABS: NavTabKey[] = [
  'schedule',
  'marks',
  'homeworks',
  'ranking',
  'attendance',
  'moskvenok',
  'food',
  'settings',
];

export const DEFAULT_BOTTOM_TABS: NavTabKey[] = ['schedule', 'marks', 'homeworks', 'ranking'];
export const DEFAULT_MORE_TABS: NavTabKey[] = ['attendance', 'moskvenok', 'food', 'settings'];
export const MAX_BOTTOM_TABS = 4;

/**
 * Ported from NavTabs.sanitize in MESHFOSS Android:
 * removes unknown/duplicate keys, caps bottom dock at 4 items, puts remaining tabs in `more`.
 */
export function sanitizeNavLayout(bottom: NavTabKey[], more: NavTabKey[]): NavLayout {
  const validSet = new Set<NavTabKey>(ALL_NAV_TABS);
  const cleanBottom: NavTabKey[] = [];
  for (const key of bottom) {
    if (validSet.has(key) && !cleanBottom.includes(key) && cleanBottom.length < MAX_BOTTOM_TABS) {
      cleanBottom.push(key);
    }
  }
  const cleanMore: NavTabKey[] = [];
  for (const key of more) {
    if (validSet.has(key) && !cleanBottom.includes(key) && !cleanMore.includes(key)) {
      cleanMore.push(key);
    }
  }
  for (const key of ALL_NAV_TABS) {
    if (!cleanBottom.includes(key) && !cleanMore.includes(key)) {
      cleanMore.push(key);
    }
  }
  return { bottom: cleanBottom, more: cleanMore };
}

/* ---------- Token & Cookie JSON Extraction (ported from MeshCore.kt & CookieJson.kt) ---------- */

const TOKEN_KEY_RE = /^(token|access_token|accessToken|auth_token|authToken|auth-token|bearer|jwt|aupd_token)$/i;
const JWT_RE = /eyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}/;
const OPAQUE_RE = /^[A-Za-z0-9._~+/=-]{24,}$/;

export function looksLikeToken(value: string | null | undefined): boolean {
  const text = value?.trim();
  if (!text || text.length < 20 || /\s/.test(text)) return false;
  if (JWT_RE.test(text)) return true;
  return OPAQUE_RE.test(text) && /[A-Za-z]/.test(text) && /\d/.test(text);
}

export function extractTokenFromJson(node: unknown, depth = 0): string | null {
  if (node == null || depth > 6) return null;
  if (typeof node === 'string') {
    return looksLikeToken(node) ? node.trim() : null;
  }
  if (Array.isArray(node)) {
    for (const item of node) {
      // Cookie array objects: { name: "aupd_token", value: "eyJ..." }
      if (item && typeof item === 'object' && 'name' in item && 'value' in item) {
        const name = String((item as Record<string, unknown>).name ?? '');
        const val = String((item as Record<string, unknown>).value ?? '');
        if (TOKEN_KEY_RE.test(name) && looksLikeToken(val)) return val.trim();
      }
      const found = extractTokenFromJson(item, depth + 1);
      if (found) return found;
    }
    return null;
  }
  if (typeof node === 'object') {
    const obj = node as Record<string, unknown>;
    for (const key of Object.keys(obj)) {
      if (TOKEN_KEY_RE.test(key) && typeof obj[key] === 'string' && looksLikeToken(obj[key] as string)) {
        return (obj[key] as string).trim();
      }
    }
    for (const key of Object.keys(obj)) {
      const found = extractTokenFromJson(obj[key], depth + 1);
      if (found) return found;
    }
  }
  return null;
}

export function extractTokenFromText(text: string | null | undefined): string | null {
  const source = text?.trim();
  if (!source) return null;
  try {
    const parsed = JSON.parse(source);
    const fromJson = extractTokenFromJson(parsed);
    if (fromJson) return fromJson;
  } catch {
    // Plain text or raw JWT
  }
  const jwtMatch = source.match(JWT_RE);
  if (jwtMatch) return jwtMatch[0];
  if (looksLikeToken(source)) return source;
  return null;
}

export interface ParsedCookieImport {
  ok: boolean;
  token: string | null;
  cookieHeader: string;
  cookieCount: number;
  error?: string;
}

/**
 * Parses exported `mos_cookies.json` (array of {name, value} or key-value map).
 */
export function parseCookieJson(raw: string): ParsedCookieImport {
  const trimmed = raw.trim();
  if (!trimmed) {
    return { ok: false, token: null, cookieHeader: '', cookieCount: 0, error: 'Вставьте JSON-файл cookie или токен МЭШ.' };
  }
  try {
    const parsed = JSON.parse(trimmed);
    const pairs: string[] = [];
    if (Array.isArray(parsed)) {
      for (const entry of parsed) {
        if (entry && typeof entry === 'object' && 'name' in entry && 'value' in entry) {
          const n = String((entry as Record<string, unknown>).name).trim();
          const v = String((entry as Record<string, unknown>).value).trim();
          if (n) pairs.push(`${n}=${v}`);
        }
      }
    } else if (parsed && typeof parsed === 'object') {
      for (const [k, v] of Object.entries(parsed as Record<string, unknown>)) {
        if (typeof v === 'string' || typeof v === 'number') {
          pairs.push(`${k}=${String(v)}`);
        }
      }
    }
    const token = extractTokenFromJson(parsed);
    if (pairs.length === 0 && !token) {
      return { ok: false, token: null, cookieHeader: '', cookieCount: 0, error: 'В JSON не найдены cookie или токен МЭШ.' };
    }
    return {
      ok: true,
      token,
      cookieHeader: pairs.join('; '),
      cookieCount: pairs.length,
    };
  } catch {
    const token = extractTokenFromText(trimmed);
    if (token) {
      return { ok: true, token, cookieHeader: '', cookieCount: 0 };
    }
    return { ok: false, token: null, cookieHeader: '', cookieCount: 0, error: 'Некорректный формат JSON или токена.' };
  }
}

/* ---------- Weighted Grades & Target Calculator ---------- */

export interface WeightedMark {
  value: number;
  weight: number;
}

export function calculateWeightedAverage(marks: WeightedMark[]): number {
  let weightedSum = 0;
  let totalWeight = 0;
  for (const m of marks) {
    const w = Math.max(1, m.weight);
    weightedSum += m.value * w;
    totalWeight += w;
  }
  if (totalWeight === 0) return 0;
  return Math.round((weightedSum / totalWeight) * 100) / 100;
}

/**
 * Calculates how many marks of value `gradeToAdd` (weight 1) are needed to reach >= `targetAverage`.
 * Returns 0 if already reached, or null if mathematically unreachable (e.g. target > gradeToAdd).
 */
export function marksNeededForTarget(
  marks: WeightedMark[],
  targetAverage: number,
  gradeToAdd = 5
): number | null {
  let sum = 0;
  let weight = 0;
  for (const m of marks) {
    const w = Math.max(1, m.weight);
    sum += m.value * w;
    weight += w;
  }
  if (weight > 0 && sum / weight >= targetAverage) return 0;
  if (targetAverage >= gradeToAdd) return null;

  // (sum + n * gradeToAdd) / (weight + n) >= targetAverage
  // n * (gradeToAdd - targetAverage) >= targetAverage * weight - sum
  const needed = Math.ceil((targetAverage * weight - sum) / (gradeToAdd - targetAverage));
  return Math.max(0, needed);
}

/* ---------- Lesson Countdown (StatusWidget logic) ---------- */

export interface LessonWindow {
  id: string;
  subject: string;
  startMinutes: number; // minutes from midnight, e.g. 9*60 + 15
  endMinutes: number;
  room: string;
}

export interface LessonCountdownResult {
  state: 'in_progress' | 'upcoming' | 'finished';
  lesson: LessonWindow | null;
  minutesRemaining: number;
  progress: number; // 0..1
}

export function getLessonCountdown(
  lessons: LessonWindow[],
  nowMinutes: number
): LessonCountdownResult {
  const sorted = [...lessons].sort((a, b) => a.startMinutes - b.startMinutes);
  for (const lesson of sorted) {
    if (nowMinutes >= lesson.startMinutes && nowMinutes < lesson.endMinutes) {
      const duration = Math.max(1, lesson.endMinutes - lesson.startMinutes);
      return {
        state: 'in_progress',
        lesson,
        minutesRemaining: lesson.endMinutes - nowMinutes,
        progress: Math.min(1, Math.max(0, (nowMinutes - lesson.startMinutes) / duration)),
      };
    }
  }
  for (const lesson of sorted) {
    if (nowMinutes < lesson.startMinutes) {
      return {
        state: 'upcoming',
        lesson,
        minutesRemaining: lesson.startMinutes - nowMinutes,
        progress: 0,
      };
    }
  }
  return {
    state: 'finished',
    lesson: null,
    minutesRemaining: 0,
    progress: 1,
  };
}
