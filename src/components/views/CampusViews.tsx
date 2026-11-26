import React, { useEffect, useMemo, useRef, useState } from 'react';
import QRCode from 'qrcode';
import { ArrowDownRight, ArrowUpRight, Check, CreditCard, RefreshCw, Utensils } from 'lucide-react';
import { LiquidGlassSurface } from '../liquid/LiquidGlassSurface';
import type { DesignStyle, MeshThemePalette } from '../../lib/meshCore';
import type { LiquidOpticsConfig } from '../../lib/liquidGlassMath';
import type { Translations } from '../../lib/i18n';
import type {
  AttendanceAbsence,
  AttendancePass,
  CafeteriaDish,
  ClassRankEntry,
  MealTransaction,
  MeshAccount,
} from '../../data/meshSeedData';

interface CommonViewProps {
  designStyle: DesignStyle;
  optics: LiquidOpticsConfig;
  theme: MeshThemePalette;
  tr: Translations;
}

/* ---------- Class Ranking View ---------- */
export const RankingView: React.FC<
  CommonViewProps & {
    entries: ClassRankEntry[];
    selectedSubjectId: string;
    onSelectSubjectId: (id: string) => void;
  }
> = ({ entries, selectedSubjectId, onSelectSubjectId, designStyle, optics, theme, tr }) => {
  const subjects = [
    { id: 'all', label: tr.overallClass },
    { id: 'alg', label: 'Алгебра' },
    { id: 'inf', label: 'Информатика' },
    { id: 'phy', label: 'Физика' },
    { id: 'eng', label: 'Английский' },
  ];

  const ranked = useMemo(() => {
    const list = entries.map((e) => ({
      ...e,
      score: selectedSubjectId === 'all' ? e.average : (e.subjectAverages[selectedSubjectId] ?? e.average),
    }));
    return list.sort((a, b) => b.score - a.score);
  }, [entries, selectedSubjectId]);

  const textPrimary = theme.isLight ? 'text-slate-900' : 'text-slate-50';
  const textMuted = theme.isLight ? 'text-slate-600' : 'text-slate-300/80';
  const dividerClass = theme.isLight ? 'border-slate-900/10' : 'border-white/10';

  return (
    <div className="space-y-6">
      <LiquidGlassSurface
        cornerRadius={22}
        designStyle={designStyle}
        optics={optics}
        isLight={theme.isLight}
        className="p-1.5"
      >
        <div className="flex flex-wrap gap-1" role="tablist" aria-label={tr.ranking}>
          {subjects.map((sub) => (
            <button
              key={sub.id}
              type="button"
              role="tab"
              aria-selected={selectedSubjectId === sub.id}
              onClick={() => onSelectSubjectId(sub.id)}
              className={`min-h-[40px] flex-1 rounded-2xl px-3.5 py-2 text-xs font-semibold transition-colors whitespace-nowrap ${
                selectedSubjectId === sub.id
                  ? theme.isLight
                    ? 'bg-white text-slate-950 shadow-sm'
                    : 'bg-white/20 text-white'
                  : `${textMuted} hover:bg-white/10`
              }`}
            >
              {sub.label}
            </button>
          ))}
        </div>
      </LiquidGlassSurface>

      <LiquidGlassSurface
        as="section"
        ariaLabel={tr.classRank}
        cornerRadius={28}
        designStyle={designStyle}
        optics={optics}
        isLight={theme.isLight}
      >
        {ranked.map((row, idx) => (
          <div
            key={row.name}
            className={`flex items-center gap-4 px-5 py-4 sm:px-6 ${
              idx > 0 ? `border-t ${dividerClass}` : ''
            } ${row.isCurrentUser ? (theme.isLight ? 'bg-sky-500/10' : 'bg-white/10') : ''}`}
          >
            <div
              className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl font-mono text-sm font-bold tabular-nums ${
                idx === 0
                  ? 'bg-amber-400/25 text-amber-400'
                  : idx === 1
                    ? 'bg-slate-300/25 text-slate-300'
                    : idx === 2
                      ? 'bg-orange-400/25 text-orange-300'
                      : theme.isLight
                        ? 'bg-slate-900/6 text-slate-600'
                        : 'bg-white/6 text-slate-400'
              }`}
            >
              {idx + 1}
            </div>

            <div className="min-w-0 flex-1">
              <div className="flex items-center justify-between gap-2">
                <p className={`truncate font-display text-sm font-semibold ${textPrimary}`}>
                  {row.name}
                </p>
                <div className="flex items-center gap-2">
                  {row.delta !== 0 && (
                    <span
                      className={`inline-flex items-center text-xs font-mono tabular-nums ${
                        row.delta > 0 ? 'text-emerald-400' : 'text-rose-400'
                      }`}
                    >
                      {row.delta > 0 ? (
                        <ArrowUpRight className="h-3.5 w-3.5" aria-hidden="true" />
                      ) : (
                        <ArrowDownRight className="h-3.5 w-3.5" aria-hidden="true" />
                      )}
                      {Math.abs(row.delta)}
                    </span>
                  )}
                  <span className="font-mono text-base font-bold tabular-nums" style={{ color: theme.primary }}>
                    {row.score.toFixed(2)}
                  </span>
                </div>
              </div>
              <div
                className={`mt-2 h-1.5 w-full overflow-hidden rounded-full ${
                  theme.isLight ? 'bg-slate-900/10' : 'bg-white/10'
                }`}
              >
                <div
                  className="h-full rounded-full"
                  style={{
                    width: `${Math.round((row.score / 5) * 100)}%`,
                    backgroundColor: row.isCurrentUser ? theme.primary : theme.secondary,
                  }}
                />
              </div>
            </div>
          </div>
        ))}
      </LiquidGlassSurface>
    </div>
  );
};

/* ---------- Attendance View ---------- */
export const AttendanceView: React.FC<
  CommonViewProps & { passes: AttendancePass[]; absences: AttendanceAbsence[] }
> = ({ passes, absences, designStyle, optics, theme, tr }) => {
  const textPrimary = theme.isLight ? 'text-slate-900' : 'text-slate-50';
  const textMuted = theme.isLight ? 'text-slate-600' : 'text-slate-300/80';
  const dividerClass = theme.isLight ? 'border-slate-900/10' : 'border-white/10';

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
      <LiquidGlassSurface
        as="section"
        ariaLabel={tr.turnstileLog}
        cornerRadius={28}
        designStyle={designStyle}
        optics={optics}
        isLight={theme.isLight}
        className="lg:col-span-7"
      >
        <div className={`border-b px-6 py-4 ${dividerClass}`}>
          <h2 className={`font-display text-base font-bold ${textPrimary}`}>{tr.turnstileLog}</h2>
        </div>
        {passes.map((p, idx) => (
          <div
            key={p.id}
            className={`flex flex-col gap-2 px-6 py-4 sm:flex-row sm:items-center sm:justify-between ${
              idx > 0 ? `border-t ${dividerClass}` : ''
            }`}
          >
            <div>
              <p className={`font-display text-sm font-semibold ${textPrimary}`}>
                {p.weekday}, {p.date}
              </p>
              <p className={`text-xs ${textMuted}`}>{p.gate}</p>
            </div>
            <div className="flex items-center gap-5 font-mono text-xs tabular-nums">
              <div>
                <span className={`block text-[11px] font-sans ${textMuted}`}>{tr.entered}</span>
                <span className="font-semibold text-emerald-400">{p.entryTime}</span>
              </div>
              <div>
                <span className={`block text-[11px] font-sans ${textMuted}`}>{tr.exited}</span>
                <span className={textPrimary}>{p.exitTime}</span>
              </div>
              <div className="text-right">
                <span className={`block text-[11px] font-sans ${textMuted}`}>Время</span>
                <span className={textMuted}>{p.durationLabel}</span>
              </div>
            </div>
          </div>
        ))}
      </LiquidGlassSurface>

      <LiquidGlassSurface
        as="section"
        ariaLabel={tr.absences}
        cornerRadius={28}
        designStyle={designStyle}
        optics={optics}
        isLight={theme.isLight}
        className="lg:col-span-5"
      >
        <div className={`border-b px-6 py-4 ${dividerClass}`}>
          <h2 className={`font-display text-base font-bold ${textPrimary}`}>{tr.absences}</h2>
        </div>
        {absences.map((a, idx) => (
          <div key={a.id} className={`px-6 py-4 ${idx > 0 ? `border-t ${dividerClass}` : ''}`}>
            <div className="flex items-baseline justify-between gap-2">
              <p className={`font-display text-sm font-semibold ${textPrimary}`}>{a.subject}</p>
              <span className={`text-xs tabular-nums ${textMuted}`}>{a.date}</span>
            </div>
            <p className={`mt-1 text-xs ${textMuted}`}>{a.note}</p>
          </div>
        ))}
      </LiquidGlassSurface>
    </div>
  );
};

/* ---------- Meals & Balance View ---------- */
export const MealsView: React.FC<
  CommonViewProps & {
    dishes: CafeteriaDish[];
    transactions: MealTransaction[];
    onTogglePreorder: (id: string) => void;
  }
> = ({ dishes, transactions, onTogglePreorder, designStyle, optics, theme, tr }) => {
  const [dailyLimit, setDailyLimit] = useState(450);
  const preorderTotal = useMemo(
    () => dishes.filter((d) => d.preordered).reduce((sum, d) => sum + d.priceRub, 0),
    [dishes]
  );

  const textPrimary = theme.isLight ? 'text-slate-900' : 'text-slate-50';
  const textMuted = theme.isLight ? 'text-slate-600' : 'text-slate-300/80';
  const dividerClass = theme.isLight ? 'border-slate-900/10' : 'border-white/10';

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <LiquidGlassSurface
          cornerRadius={28}
          designStyle={designStyle}
          optics={optics}
          isLight={theme.isLight}
          tintColor={theme.primary}
          className="p-6"
        >
          <p className={`text-xs font-medium ${textMuted}`}>{tr.mealBalance}</p>
          <p className="mt-2 font-mono text-3xl font-bold tabular-nums" style={{ color: theme.primary }}>
            2 465 ₽
          </p>
          <p className={`mt-2 text-xs tabular-nums ${textMuted}`}>
            {tr.preordered}: {preorderTotal} ₽
          </p>
        </LiquidGlassSurface>

        <LiquidGlassSurface
          cornerRadius={28}
          designStyle={designStyle}
          optics={optics}
          isLight={theme.isLight}
          className="p-6"
        >
          <div className="flex items-center justify-between">
            <label htmlFor="daily-limit-range" className={`text-xs font-medium ${textMuted}`}>
              {tr.dailyLimit}
            </label>
            <span className={`font-mono text-lg font-bold tabular-nums ${textPrimary}`}>
              {dailyLimit} ₽
            </span>
          </div>
          <input
            id="daily-limit-range"
            type="range"
            min="200"
            max="1000"
            step="50"
            value={dailyLimit}
            onChange={(e) => setDailyLimit(Number(e.target.value))}
            className="mt-4 w-full accent-sky-400"
          />
        </LiquidGlassSurface>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        <LiquidGlassSurface
          as="section"
          ariaLabel={tr.cafeteriaMenu}
          cornerRadius={28}
          designStyle={designStyle}
          optics={optics}
          isLight={theme.isLight}
          className="lg:col-span-7"
        >
          <div className={`flex items-center gap-2 border-b px-6 py-4 ${dividerClass}`}>
            <Utensils className="h-4 w-4" style={{ color: theme.primary }} aria-hidden="true" />
            <h2 className={`font-display text-base font-bold ${textPrimary}`}>{tr.cafeteriaMenu}</h2>
          </div>
          {dishes.map((dish, idx) => (
            <div
              key={dish.id}
              className={`flex items-center justify-between gap-4 px-6 py-4 ${
                idx > 0 ? `border-t ${dividerClass}` : ''
              }`}
            >
              <div className="min-w-0">
                <p className={`font-display text-sm font-semibold ${textPrimary}`}>{dish.name}</p>
                <p className={`mt-0.5 text-xs tabular-nums ${textMuted}`}>
                  {dish.weightGrams} г · {dish.calories} ккал
                </p>
              </div>
              <div className="flex items-center gap-3 shrink-0">
                <span className={`font-mono text-sm font-bold tabular-nums ${textPrimary}`}>
                  {dish.priceRub} ₽
                </span>
                <button
                  type="button"
                  onClick={() => onTogglePreorder(dish.id)}
                  aria-pressed={dish.preordered}
                  className={`inline-flex min-h-[38px] items-center gap-1.5 rounded-xl px-3 py-1.5 text-xs font-semibold transition-colors whitespace-nowrap ${
                    dish.preordered
                      ? 'bg-emerald-500/20 text-emerald-400'
                      : theme.isLight
                        ? 'bg-slate-900/8 text-slate-800 hover:bg-slate-900/12'
                        : 'bg-white/10 text-white hover:bg-white/16'
                  }`}
                >
                  {dish.preordered && <Check className="h-3.5 w-3.5" aria-hidden="true" />}
                  <span>{dish.preordered ? tr.preordered : tr.preorder}</span>
                </button>
              </div>
            </div>
          ))}
        </LiquidGlassSurface>

        <LiquidGlassSurface
          as="section"
          cornerRadius={28}
          designStyle={designStyle}
          optics={optics}
          isLight={theme.isLight}
          className="lg:col-span-5"
        >
          <div className={`border-b px-6 py-4 ${dividerClass}`}>
            <h2 className={`font-display text-base font-bold ${textPrimary}`}>История списаний</h2>
          </div>
          {transactions.map((tx, idx) => (
            <div
              key={tx.id}
              className={`flex items-center justify-between gap-3 px-6 py-4 ${
                idx > 0 ? `border-t ${dividerClass}` : ''
              }`}
            >
              <div className="min-w-0">
                <p className={`text-sm font-medium ${textPrimary}`}>{tx.description}</p>
                <p className={`text-xs tabular-nums ${textMuted}`}>{tx.date}</p>
              </div>
              <span
                className={`font-mono text-sm font-bold tabular-nums shrink-0 ${
                  tx.amountRub > 0 ? 'text-emerald-400' : textPrimary
                }`}
              >
                {tx.amountRub > 0 ? `+${tx.amountRub}` : tx.amountRub} ₽
              </span>
            </div>
          ))}
        </LiquidGlassSurface>
      </div>
    </div>
  );
};

/* ---------- Moskvenok Digital Pass & Scannable QR View ---------- */
export const MoskvenokView: React.FC<CommonViewProps & { account: MeshAccount }> = ({
  account,
  designStyle,
  optics,
  theme,
  tr,
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [nonce, setNonce] = useState(1);

  const qrPayload = useMemo(
    () => `MOSKVENOK:2026:${account.profileId}:CARD-7704-8912-${String(nonce).padStart(2, '0')}`,
    [account.profileId, nonce]
  );

  useEffect(() => {
    if (!canvasRef.current) return;
    QRCode.toCanvas(
      canvasRef.current,
      qrPayload,
      {
        width: 210,
        margin: 2,
        color: {
          dark: '#0B101B',
          light: '#FFFFFF',
        },
      },
      () => {}
    );
  }, [qrPayload]);

  const textPrimary = theme.isLight ? 'text-slate-900' : 'text-slate-50';
  const textMuted = theme.isLight ? 'text-slate-600' : 'text-slate-300/80';

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <LiquidGlassSurface
        as="section"
        ariaLabel={tr.moskvenokCard}
        cornerRadius={32}
        designStyle={designStyle}
        optics={{ ...optics, refractionAmount: Math.max(26, optics.refractionAmount) }}
        isLight={theme.isLight}
        tintColor={theme.primary}
        className="p-6 sm:p-8"
      >
        <div className="flex flex-col items-center gap-8 sm:flex-row sm:items-center sm:justify-between">
          <div className="space-y-4 text-center sm:text-left">
            <div className="flex items-center justify-center gap-2 sm:justify-start">
              <CreditCard className="h-5 w-5" style={{ color: theme.primary }} aria-hidden="true" />
              <span className={`font-display text-lg font-bold ${textPrimary}`}>{tr.moskvenokCard}</span>
            </div>

            <div>
              <p className={`text-xs ${textMuted}`}>{account.school}</p>
              <p className={`mt-1 font-display text-2xl font-bold ${textPrimary}`}>{account.name}</p>
              <p className={`mt-0.5 text-sm ${textMuted}`}>{account.className}</p>
            </div>

            <div>
              <p className={`text-xs ${textMuted}`}>{tr.cardNumber}</p>
              <p className={`mt-0.5 font-mono text-base font-semibold tracking-wider tabular-nums ${textPrimary}`}>
                7704 8912 3948 1204
              </p>
            </div>

            <div className="flex flex-wrap items-center justify-center gap-3 sm:justify-start">
              <button
                type="button"
                onClick={() => setNonce((n) => n + 1)}
                className={`inline-flex min-h-[42px] items-center gap-2 rounded-2xl border px-4 py-2 text-xs font-semibold transition-colors ${
                  theme.isLight
                    ? 'border-slate-900/15 bg-white/75 text-slate-900 hover:bg-white'
                    : 'border-white/15 bg-white/10 text-white hover:bg-white/16'
                }`}
              >
                <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
                <span>Обновить динамический QR</span>
              </button>
            </div>
          </div>

          <div className="flex flex-col items-center gap-3">
            <div className="rounded-3xl bg-white p-3.5 shadow-xl">
              <canvas ref={canvasRef} data-testid="moskvenok-qr-canvas" />
            </div>
            <p className={`max-w-[220px] text-center text-xs ${textMuted}`}>
              {tr.scanAtTurnstile}
            </p>
          </div>
        </div>
      </LiquidGlassSurface>
    </div>
  );
};
