import React, { useMemo, useState } from 'react';
import { Calculator, Trophy, X } from 'lucide-react';
import { LiquidGlassSurface } from '../liquid/LiquidGlassSurface';
import {
  calculateWeightedAverage,
  marksNeededForTarget,
  type DesignStyle,
  type MeshThemePalette,
} from '../../lib/meshCore';
import type { LiquidOpticsConfig } from '../../lib/liquidGlassMath';
import type { Translations } from '../../lib/i18n';
import type { SubjectGrades, SubjectMarkItem } from '../../data/meshSeedData';

interface GradesViewProps {
  subjects: SubjectGrades[];
  highlightSubject?: string | null;
  onJumpToRanking: (subjectId: string) => void;
  designStyle: DesignStyle;
  optics: LiquidOpticsConfig;
  theme: MeshThemePalette;
  tr: Translations;
}

export const GradesView: React.FC<GradesViewProps> = ({
  subjects,
  highlightSubject,
  onJumpToRanking,
  designStyle,
  optics,
  theme,
  tr,
}) => {
  const [mode, setMode] = useState<'period' | 'final'>('period');
  const [targetGpa, setTargetGpa] = useState<number>(4.65);
  const [selectedMark, setSelectedMark] = useState<{
    subject: string;
    mark: SubjectMarkItem;
  } | null>(null);

  const overallGpa = useMemo(() => {
    const avgs = subjects.map((s) => calculateWeightedAverage(s.marks)).filter((a) => a > 0);
    if (avgs.length === 0) return 0;
    return Math.round((avgs.reduce((acc, v) => acc + v, 0) / avgs.length) * 100) / 100;
  }, [subjects]);

  const textPrimary = theme.isLight ? 'text-slate-900' : 'text-slate-50';
  const textMuted = theme.isLight ? 'text-slate-600' : 'text-slate-300/80';
  const dividerClass = theme.isLight ? 'border-slate-900/10' : 'border-white/10';

  const markColor = (val: number) => {
    if (val >= 5) return theme.isLight ? 'bg-emerald-500/15 text-emerald-700' : 'bg-emerald-500/20 text-emerald-300';
    if (val === 4) return theme.isLight ? 'bg-sky-500/15 text-sky-700' : 'bg-sky-500/20 text-sky-300';
    if (val === 3) return theme.isLight ? 'bg-amber-500/15 text-amber-700' : 'bg-amber-500/20 text-amber-300';
    return theme.isLight ? 'bg-rose-500/15 text-rose-700' : 'bg-rose-500/20 text-rose-300';
  };

  return (
    <div className="space-y-6">
      {/* Top GPA summary & target calculator controls */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
        <LiquidGlassSurface
          as="section"
          cornerRadius={28}
          designStyle={designStyle}
          optics={optics}
          isLight={theme.isLight}
          tintColor={theme.primary}
          className="p-5 sm:p-6 lg:col-span-5"
        >
          <p className={`text-xs font-medium ${textMuted}`}>{tr.overallGpa}</p>
          <div className="mt-2 flex items-baseline gap-3">
            <span
              className="font-mono text-4xl font-bold tracking-tight tabular-nums"
              style={{ color: theme.primary }}
            >
              {overallGpa.toFixed(2)}
            </span>
            <span className={`text-xs ${textMuted}`}>
              {subjects.length} предметов · 2026/27
            </span>
          </div>
          <div className="mt-4 flex gap-1.5">
            <button
              type="button"
              onClick={() => setMode('period')}
              className={`min-h-[40px] flex-1 rounded-xl px-3 py-1.5 text-xs font-semibold transition-colors whitespace-nowrap ${
                mode === 'period'
                  ? theme.isLight
                    ? 'bg-white text-slate-950 shadow-sm'
                    : 'bg-white/20 text-white'
                  : `${textMuted} hover:bg-white/10`
              }`}
            >
              {tr.periodMarks}
            </button>
            <button
              type="button"
              onClick={() => setMode('final')}
              className={`min-h-[40px] flex-1 rounded-xl px-3 py-1.5 text-xs font-semibold transition-colors whitespace-nowrap ${
                mode === 'final'
                  ? theme.isLight
                    ? 'bg-white text-slate-950 shadow-sm'
                    : 'bg-white/20 text-white'
                  : `${textMuted} hover:bg-white/10`
              }`}
            >
              {tr.finalMarks}
            </button>
          </div>
        </LiquidGlassSurface>

        <LiquidGlassSurface
          as="section"
          cornerRadius={28}
          designStyle={designStyle}
          optics={optics}
          isLight={theme.isLight}
          className="p-5 sm:p-6 lg:col-span-7"
        >
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <Calculator className="h-4 w-4" style={{ color: theme.primary }} aria-hidden="true" />
              <h2 className={`font-display text-sm font-semibold ${textPrimary}`}>
                {tr.targetCalculator}
              </h2>
            </div>
            <span className="font-mono text-lg font-bold tabular-nums" style={{ color: theme.primary }}>
              {targetGpa.toFixed(2)}
            </span>
          </div>
          <div className="mt-4 space-y-2">
            <label htmlFor="target-gpa-slider" className={`block text-xs ${textMuted}`}>
              {tr.needMarksToReach} {targetGpa.toFixed(2)}
            </label>
            <input
              id="target-gpa-slider"
              type="range"
              min="3.5"
              max="4.95"
              step="0.05"
              value={targetGpa}
              onChange={(e) => setTargetGpa(Number(e.target.value))}
              className="w-full accent-sky-400"
            />
            <div className={`flex justify-between font-mono text-[11px] tabular-nums ${textMuted}`}>
              <span>3.50</span>
              <span>4.25</span>
              <span>4.65</span>
              <span>4.95</span>
            </div>
          </div>
        </LiquidGlassSurface>
      </div>

      {/* Subject grades list */}
      <LiquidGlassSurface
        as="section"
        ariaLabel={tr.marks}
        cornerRadius={28}
        designStyle={designStyle}
        optics={optics}
        isLight={theme.isLight}
      >
        {subjects.map((sub, idx) => {
          const avg = calculateWeightedAverage(sub.marks);
          const needed = marksNeededForTarget(sub.marks, targetGpa, 5);
          const isHighlighted = highlightSubject && sub.subject.toLowerCase().includes(highlightSubject.toLowerCase());

          return (
            <div
              key={sub.subjectId}
              className={`px-5 py-4 sm:px-6 ${idx > 0 ? `border-t ${dividerClass}` : ''} ${
                isHighlighted ? (theme.isLight ? 'bg-sky-500/10' : 'bg-white/8') : ''
              }`}
            >
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <div className="flex items-center gap-3">
                    <h3 className={`font-display text-base font-semibold ${textPrimary}`}>
                      {sub.subject}
                    </h3>
                    <button
                      type="button"
                      onClick={() => onJumpToRanking(sub.subjectId)}
                      aria-label={`${tr.ranking}: ${sub.subject}`}
                      className={`inline-flex items-center gap-1 text-xs font-medium transition-colors hover:underline ${textMuted}`}
                    >
                      <Trophy className="h-3.5 w-3.5" aria-hidden="true" />
                      <span>{tr.ranking}</span>
                    </button>
                  </div>
                  <p className={`mt-0.5 text-xs ${textMuted}`}>
                    {sub.teacher}
                    <span aria-hidden="true"> · </span>
                    {needed === 0
                      ? tr.alreadyReached
                      : needed === null
                        ? tr.unreachable
                        : `${tr.needMarksToReach} ${targetGpa.toFixed(2)}: +${needed}`}
                  </p>
                </div>

                <div className="flex items-baseline gap-2 sm:text-right">
                  <span className={`text-xs ${textMuted}`}>{tr.subjectAverage}</span>
                  <span
                    className="font-mono text-2xl font-bold tabular-nums"
                    style={{ color: avg >= 4.5 ? '#34D399' : theme.primary }}
                  >
                    {avg.toFixed(2)}
                  </span>
                </div>
              </div>

              {mode === 'period' ? (
                <div className="mt-3 flex flex-wrap gap-2">
                  {sub.marks.map((m) => (
                    <button
                      key={m.id}
                      type="button"
                      onClick={() => setSelectedMark({ subject: sub.subject, mark: m })}
                      aria-label={`${sub.subject} ${m.value} (${m.controlForm}, ${m.date})`}
                      className={`inline-flex min-h-[40px] min-w-[44px] items-baseline justify-center gap-0.5 rounded-2xl px-3 py-2 font-mono text-sm font-bold tabular-nums transition-transform duration-150 hover:scale-105 focus-visible:outline-2 focus-visible:outline-sky-400 ${markColor(
                        m.value
                      )}`}
                    >
                      <span>{m.value}</span>
                      <sup className="text-[10px] opacity-75">×{m.weight}</sup>
                    </button>
                  ))}
                </div>
              ) : (
                <div className={`mt-3 grid grid-cols-3 gap-4 border-t pt-3 text-xs ${dividerClass}`}>
                  <div>
                    <span className={textMuted}>Триместр 1</span>
                    <p className={`mt-0.5 font-mono text-lg font-bold tabular-nums ${textPrimary}`}>{sub.term1}</p>
                  </div>
                  <div>
                    <span className={textMuted}>Триместр 2</span>
                    <p className={`mt-0.5 font-mono text-lg font-bold tabular-nums ${textPrimary}`}>{sub.term2}</p>
                  </div>
                  <div>
                    <span className={textMuted}>Прогноз за год</span>
                    <p className="mt-0.5 font-mono text-lg font-bold tabular-nums text-emerald-400">{sub.yearForecast}</p>
                  </div>
                </div>
              )}
            </div>
          );
        })}
      </LiquidGlassSurface>

      {/* Mark Detail Inspector Modal */}
      {selectedMark && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-4 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-label={selectedMark.subject}
        >
          <LiquidGlassSurface
            cornerRadius={28}
            designStyle={designStyle}
            optics={optics}
            isLight={theme.isLight}
            className="w-full max-w-md p-6"
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className={`text-xs ${textMuted}`}>{selectedMark.mark.date} · {selectedMark.mark.controlForm}</p>
                <h3 className={`mt-1 font-display text-lg font-bold ${textPrimary}`}>
                  {selectedMark.subject}
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setSelectedMark(null)}
                aria-label="Закрыть окно оценки"
                className={`flex h-10 w-10 items-center justify-center rounded-full transition-colors ${
                  theme.isLight ? 'hover:bg-slate-900/10' : 'hover:bg-white/10'
                }`}
              >
                <X className={`h-5 w-5 ${textPrimary}`} />
              </button>
            </div>

            <div className="mt-5 flex items-center gap-4">
              <div
                className={`flex h-16 w-16 items-baseline justify-center rounded-2xl pt-3.5 font-mono text-3xl font-bold tabular-nums ${markColor(
                  selectedMark.mark.value
                )}`}
              >
                {selectedMark.mark.value}
                <sup className="ml-0.5 text-xs">×{selectedMark.mark.weight}</sup>
              </div>
              <div className="space-y-1 text-sm">
                <p className={`font-medium ${textPrimary}`}>{selectedMark.mark.lessonTopic}</p>
                <p className={textMuted}>{selectedMark.mark.comment}</p>
              </div>
            </div>

            <div className="mt-6 flex justify-end">
              <button
                type="button"
                onClick={() => setSelectedMark(null)}
                className="min-h-[42px] rounded-xl px-5 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90"
                style={{ backgroundColor: theme.primary }}
              >
                {tr.close}
              </button>
            </div>
          </LiquidGlassSurface>
        </div>
      )}
    </div>
  );
};
