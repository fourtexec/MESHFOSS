import React, { useMemo, useState } from 'react';
import { AlertTriangle, Check, CheckCircle2, Circle, Play, Search, Sparkles, X } from 'lucide-react';
import { LiquidGlassSurface } from '../liquid/LiquidGlassSurface';
import type { DesignStyle, MeshThemePalette } from '../../lib/meshCore';
import type { LiquidOpticsConfig } from '../../lib/liquidGlassMath';
import type { Translations } from '../../lib/i18n';
import type { HomeworkItem } from '../../data/meshSeedData';

interface HomeworksViewProps {
  homeworks: HomeworkItem[];
  focusedHomeworkId?: string | null;
  onToggleDone: (id: string) => void;
  onCompleteCdz: (homeworkId: string, scorePercent: number) => void;
  designStyle: DesignStyle;
  optics: LiquidOpticsConfig;
  theme: MeshThemePalette;
  tr: Translations;
}

type FilterMode = 'all' | 'pending' | 'done' | 'cdz';

export const HomeworksView: React.FC<HomeworksViewProps> = ({
  homeworks,
  focusedHomeworkId,
  onToggleDone,
  onCompleteCdz,
  designStyle,
  optics,
  theme,
  tr,
}) => {
  const [filter, setFilter] = useState<FilterMode>('all');
  const [search, setSearch] = useState('');
  const [activeCdzHomeworkId, setActiveCdzHomeworkId] = useState<string | null>(null);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [savedBanner, setSavedBanner] = useState(false);
  const [submittedResult, setSubmittedResult] = useState<{ correct: number; total: number; percent: number } | null>(null);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return homeworks.filter((hw) => {
      if (filter === 'pending' && hw.done) return false;
      if (filter === 'done' && !hw.done) return false;
      if (filter === 'cdz' && !hw.cdz) return false;
      if (q && !hw.subject.toLowerCase().includes(q) && !hw.description.toLowerCase().includes(q)) {
        return false;
      }
      return true;
    });
  }, [homeworks, filter, search]);

  const activeCdzHomework = useMemo(
    () => homeworks.find((h) => h.id === activeCdzHomeworkId) ?? null,
    [homeworks, activeCdzHomeworkId]
  );

  const pendingCount = useMemo(() => homeworks.filter((h) => !h.done).length, [homeworks]);
  const doneCount = homeworks.length - pendingCount;

  const textPrimary = theme.isLight ? 'text-slate-900' : 'text-slate-50';
  const textMuted = theme.isLight ? 'text-slate-600' : 'text-slate-300/80';
  const dividerClass = theme.isLight ? 'border-slate-900/10' : 'border-white/10';

  const openCdz = (hwId: string) => {
    setActiveCdzHomeworkId(hwId);
    setSubmittedResult(null);
    setSavedBanner(false);
  };

  const handleSubmitCdz = () => {
    if (!activeCdzHomework?.cdz) return;
    const qs = activeCdzHomework.cdz.questions;
    let correct = 0;
    for (const q of qs) {
      const userAns = (answers[q.id] ?? '').trim().toLowerCase();
      if (userAns === q.correctAnswer.trim().toLowerCase()) {
        correct += 1;
      }
    }
    const percent = Math.round((correct / Math.max(1, qs.length)) * 100);
    setSubmittedResult({ correct, total: qs.length, percent });
    onCompleteCdz(activeCdzHomework.id, percent);
  };

  return (
    <div className="space-y-6">
      {/* Search & segmented filter bar */}
      <LiquidGlassSurface
        cornerRadius={24}
        designStyle={designStyle}
        optics={optics}
        isLight={theme.isLight}
        className="p-4"
      >
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div className="relative flex-1">
            <Search className={`pointer-events-none absolute top-1/2 left-3.5 h-4 w-4 -translate-y-1/2 ${textMuted}`} aria-hidden="true" />
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={tr.searchPlaceholder}
              aria-label={tr.searchPlaceholder}
              className={`min-h-[42px] w-full rounded-2xl border py-2 pr-4 pl-10 text-sm outline-none transition-colors focus:border-sky-400 ${
                theme.isLight
                  ? 'border-slate-900/12 bg-white/70 text-slate-900 placeholder:text-slate-500'
                  : 'border-white/12 bg-white/6 text-white placeholder:text-slate-400'
              }`}
            />
          </div>

          <div className="flex flex-wrap items-center gap-1" role="tablist" aria-label={tr.homeworks}>
            {(
              [
                ['all', `${tr.all} (${homeworks.length})`],
                ['pending', `${tr.pending} (${pendingCount})`],
                ['done', `${tr.done} (${doneCount})`],
                ['cdz', tr.cdzOnly],
              ] as const
            ).map(([key, label]) => (
              <button
                key={key}
                type="button"
                role="tab"
                aria-selected={filter === key}
                onClick={() => setFilter(key)}
                className={`min-h-[40px] rounded-xl px-3.5 py-1.5 text-xs font-semibold transition-colors whitespace-nowrap shrink-0 ${
                  filter === key
                    ? theme.isLight
                      ? 'bg-white text-slate-950 shadow-sm'
                      : 'bg-white/20 text-white'
                    : `${textMuted} hover:bg-white/10`
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
      </LiquidGlassSurface>

      {/* Homework list */}
      <LiquidGlassSurface
        as="section"
        ariaLabel={tr.homeworks}
        cornerRadius={28}
        designStyle={designStyle}
        optics={optics}
        isLight={theme.isLight}
      >
        {filtered.length === 0 ? (
          <div className="px-6 py-12 text-center">
            <p className={`text-sm ${textMuted}`}>{tr.emptyHomework}</p>
          </div>
        ) : (
          filtered.map((hw, idx) => {
            const focused = focusedHomeworkId === hw.id;
            return (
              <div
                key={hw.id}
                className={`flex items-start gap-4 px-5 py-4 sm:px-6 ${
                  idx > 0 ? `border-t ${dividerClass}` : ''
                } ${focused ? (theme.isLight ? 'bg-sky-500/10' : 'bg-white/8') : ''}`}
              >
                <button
                  type="button"
                  onClick={() => onToggleDone(hw.id)}
                  aria-label={`${hw.done ? tr.done : tr.pending}: ${hw.subject}`}
                  aria-pressed={hw.done}
                  className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl transition-colors hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-sky-400"
                >
                  {hw.done ? (
                    <CheckCircle2 className="h-5 w-5 text-emerald-400" />
                  ) : (
                    <Circle className={`h-5 w-5 ${textMuted}`} />
                  )}
                </button>

                <div className="min-w-0 flex-1 space-y-1.5">
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <h3
                      className={`font-display text-base font-semibold ${
                        hw.done ? `line-through opacity-60 ${textPrimary}` : textPrimary
                      }`}
                    >
                      {hw.subject}
                    </h3>
                    <span className={`text-xs tabular-nums ${textMuted}`}>{hw.dueLabel}</span>
                  </div>

                  <p className={`text-sm leading-relaxed ${hw.done ? 'opacity-60' : ''} ${textMuted}`}>
                    {hw.description}
                  </p>

                  {hw.cdz && (
                    <div className="pt-2 flex flex-wrap items-center justify-between gap-3">
                      <div className={`text-xs ${textMuted}`}>
                        <span className={`font-semibold ${textPrimary}`}>{hw.cdz.title}</span>
                        <span aria-hidden="true"> · </span>
                        <span>{hw.cdz.publisher}</span>
                        {typeof hw.cdz.scorePercent === 'number' && (
                          <>
                            <span aria-hidden="true"> · </span>
                            <span className="font-mono font-semibold text-emerald-400 tabular-nums">
                              LRS {hw.cdz.scorePercent}%
                            </span>
                          </>
                        )}
                      </div>

                      <button
                        type="button"
                        onClick={() => openCdz(hw.id)}
                        className="inline-flex min-h-[40px] items-center gap-2 rounded-2xl px-4 py-2 text-xs font-semibold text-white shadow-sm transition-opacity hover:opacity-90 whitespace-nowrap shrink-0"
                        style={{ backgroundColor: theme.primary }}
                      >
                        <Play className="h-3.5 w-3.5" aria-hidden="true" />
                        <span>{tr.openCdzSolver}</span>
                      </button>
                    </div>
                  )}
                </div>
              </div>
            );
          })
        )}
      </LiquidGlassSurface>

      {/* Native CDZ Solver Modal (ported from CdzNativeScreen.kt) */}
      {activeCdzHomework?.cdz && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-md"
          role="dialog"
          aria-modal="true"
          aria-label={activeCdzHomework.cdz.title}
        >
          <LiquidGlassSurface
            cornerRadius={30}
            designStyle={designStyle}
            optics={optics}
            isLight={theme.isLight}
            className="max-h-[88vh] w-full max-w-2xl overflow-y-auto p-6"
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className={`text-xs ${textMuted}`}>
                  {activeCdzHomework.subject} · {activeCdzHomework.cdz.publisher}
                </p>
                <h2 className={`mt-1 font-display text-xl font-bold ${textPrimary}`}>
                  {activeCdzHomework.cdz.title}
                </h2>
              </div>
              <button
                type="button"
                onClick={() => setActiveCdzHomeworkId(null)}
                aria-label={tr.close}
                className={`flex h-10 w-10 items-center justify-center rounded-full ${
                  theme.isLight ? 'hover:bg-slate-900/10' : 'hover:bg-white/10'
                }`}
              >
                <X className={`h-5 w-5 ${textPrimary}`} />
              </button>
            </div>

            {activeCdzHomework.cdz.hasTimer && (
              <div
                className={`mt-4 flex items-center gap-2.5 rounded-2xl border px-4 py-3 text-xs ${
                  theme.isLight
                    ? 'border-amber-600/30 bg-amber-500/10 text-amber-900'
                    : 'border-amber-400/30 bg-amber-500/15 text-amber-200'
                }`}
              >
                <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" />
                <span>
                  {tr.cdzTimerWarning} ({activeCdzHomework.cdz.durationMinutes} {tr.minutesLeft})
                </span>
              </div>
            )}

            <div className="mt-5 space-y-5">
              {activeCdzHomework.cdz.questions.map((q, i) => (
                <div key={q.id} className={`space-y-3 border-t pt-4 ${dividerClass}`}>
                  <p className={`text-sm font-semibold ${textPrimary}`}>
                    {i + 1}. {q.prompt}
                  </p>

                  {q.type === 'choice' && q.options ? (
                    <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                      {q.options.map((opt) => {
                        const selected = answers[q.id] === opt;
                        return (
                          <button
                            key={opt}
                            type="button"
                            onClick={() => setAnswers((prev) => ({ ...prev, [q.id]: opt }))}
                            className={`flex min-h-[44px] items-center justify-between rounded-2xl border px-4 py-2.5 text-left text-sm font-medium transition-colors ${
                              selected
                                ? 'border-sky-400 bg-sky-500/20 text-sky-400'
                                : theme.isLight
                                  ? 'border-slate-900/12 bg-white/60 text-slate-900 hover:bg-white'
                                  : 'border-white/12 bg-white/6 text-slate-100 hover:bg-white/12'
                            }`}
                          >
                            <span>{opt}</span>
                            {selected && <Check className="h-4 w-4 shrink-0" />}
                          </button>
                        );
                      })}
                    </div>
                  ) : (
                    <input
                      type="text"
                      value={answers[q.id] ?? ''}
                      onChange={(e) => setAnswers((prev) => ({ ...prev, [q.id]: e.target.value }))}
                      placeholder="Введите ответ…"
                      aria-label={`Ответ на вопрос ${i + 1}`}
                      className={`min-h-[44px] w-full rounded-2xl border px-4 py-2 text-sm outline-none focus:border-sky-400 ${
                        theme.isLight
                          ? 'border-slate-900/15 bg-white/80 text-slate-900'
                          : 'border-white/15 bg-white/8 text-white'
                      }`}
                    />
                  )}
                </div>
              ))}
            </div>

            {savedBanner && !submittedResult && (
              <p className="mt-4 text-xs font-medium text-emerald-400">
                Черновик ответов сохранён локально.
              </p>
            )}

            {submittedResult && (
              <div
                className={`mt-5 flex items-center justify-between rounded-2xl border px-4 py-3 ${
                  theme.isLight
                    ? 'border-emerald-600/25 bg-emerald-500/12 text-emerald-900'
                    : 'border-emerald-400/30 bg-emerald-500/15 text-emerald-200'
                }`}
              >
                <div className="flex items-center gap-2 text-sm font-semibold">
                  <Sparkles className="h-4 w-4" aria-hidden="true" />
                  <span>
                    Результат LRS: {submittedResult.correct} / {submittedResult.total}
                  </span>
                </div>
                <span className="font-mono text-lg font-bold tabular-nums">
                  {submittedResult.percent}%
                </span>
              </div>
            )}

            <div className="mt-6 flex flex-wrap items-center justify-end gap-3">
              <button
                type="button"
                onClick={() => setSavedBanner(true)}
                className={`min-h-[42px] rounded-2xl border px-4 py-2 text-sm font-semibold transition-colors ${
                  theme.isLight
                    ? 'border-slate-900/15 text-slate-800 hover:bg-slate-900/5'
                    : 'border-white/15 text-slate-100 hover:bg-white/10'
                }`}
              >
                {tr.saveProgress}
              </button>
              <button
                type="button"
                onClick={handleSubmitCdz}
                className="min-h-[42px] rounded-2xl px-5 py-2 text-sm font-semibold text-white shadow-sm transition-opacity hover:opacity-90"
                style={{ backgroundColor: theme.primary }}
              >
                {tr.submitCdz}
              </button>
            </div>
          </LiquidGlassSurface>
        </div>
      )}
    </div>
  );
};
