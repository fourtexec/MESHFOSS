import React, { useMemo } from 'react';
import { ArrowRight, BookOpen, Clock } from 'lucide-react';
import { LiquidGlassSurface } from '../liquid/LiquidGlassSurface';
import { getLessonCountdown, type DesignStyle, type MeshThemePalette } from '../../lib/meshCore';
import type { LiquidOpticsConfig } from '../../lib/liquidGlassMath';
import type { Translations } from '../../lib/i18n';
import { WEEKDAYS, type ScheduleLesson } from '../../data/meshSeedData';

interface ScheduleViewProps {
  lessons: ScheduleLesson[];
  selectedDay: number;
  onSelectDay: (day: number) => void;
  onOpenHomework: (homeworkId: string) => void;
  onOpenMarks: (subjectName: string) => void;
  designStyle: DesignStyle;
  optics: LiquidOpticsConfig;
  theme: MeshThemePalette;
  tr: Translations;
  lang: string;
}

export const ScheduleView: React.FC<ScheduleViewProps> = ({
  lessons,
  selectedDay,
  onSelectDay,
  onOpenHomework,
  onOpenMarks,
  designStyle,
  optics,
  theme,
  tr,
  lang,
}) => {
  const dayLessons = useMemo(
    () => lessons.filter((l) => l.dayIndex === selectedDay).sort((a, b) => a.number - b.number),
    [lessons, selectedDay]
  );

  // Deterministic live-school simulation time (10:42 = 642 min) so lesson progress is immediately visible
  const countdown = useMemo(() => {
    const wednesdayLessons = lessons.filter((l) => l.dayIndex === 2);
    return getLessonCountdown(wednesdayLessons, 642);
  }, [lessons]);

  const textPrimary = theme.isLight ? 'text-slate-900' : 'text-slate-50';
  const textMuted = theme.isLight ? 'text-slate-600' : 'text-slate-300/80';
  const dividerClass = theme.isLight ? 'border-slate-900/10' : 'border-white/10';

  return (
    <div className="space-y-6">
      {/* Live StatusWidget countdown strip */}
      <LiquidGlassSurface
        as="section"
        ariaLabel={tr.lessonInProgress}
        cornerRadius={28}
        designStyle={designStyle}
        optics={optics}
        isLight={theme.isLight}
        tintColor={theme.primary}
        className="p-5 sm:p-6"
      >
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="space-y-1">
            <div className={`flex items-center gap-2 text-xs font-medium ${textMuted}`}>
              <Clock className="h-3.5 w-3.5" aria-hidden="true" />
              <span>
                {countdown.state === 'in_progress'
                  ? tr.lessonInProgress
                  : countdown.state === 'upcoming'
                    ? tr.nextLesson
                    : tr.lessonsFinished}
              </span>
              {countdown.lesson && (
                <>
                  <span aria-hidden="true">·</span>
                  <span>
                    {tr.room} {countdown.lesson.room}
                  </span>
                </>
              )}
            </div>
            <h2 className={`font-display text-xl font-bold tracking-tight sm:text-2xl ${textPrimary}`}>
              {countdown.lesson ? countdown.lesson.subject : tr.lessonsFinished}
            </h2>
          </div>

          {countdown.lesson && (
            <div className="flex items-baseline gap-2 sm:text-right">
              <span
                className="font-mono text-3xl font-semibold tracking-tight tabular-nums"
                style={{ color: theme.primary }}
              >
                {countdown.minutesRemaining}
              </span>
              <span className={`text-sm font-medium ${textMuted}`}>{tr.minutesLeft}</span>
            </div>
          )}
        </div>

        {countdown.lesson && (
          <div className="mt-4">
            <div
              className={`h-1.5 w-full overflow-hidden rounded-full ${
                theme.isLight ? 'bg-slate-900/10' : 'bg-white/15'
              }`}
            >
              <div
                className="h-full rounded-full transition-all duration-300"
                style={{
                  width: `${Math.max(8, Math.round(countdown.progress * 100))}%`,
                  backgroundColor: theme.primary,
                }}
              />
            </div>
          </div>
        )}
      </LiquidGlassSurface>

      {/* Weekday segmented selector */}
      <LiquidGlassSurface
        as="div"
        cornerRadius={22}
        designStyle={designStyle}
        optics={{ ...optics, refractionAmount: Math.min(22, optics.refractionAmount) }}
        isLight={theme.isLight}
        className="p-1.5"
      >
        <div className="grid grid-cols-5 gap-1" role="tablist" aria-label={tr.schedule}>
          {WEEKDAYS.map((day) => {
            const active = day.index === selectedDay;
            return (
              <button
                key={day.index}
                type="button"
                role="tab"
                aria-selected={active}
                onClick={() => onSelectDay(day.index)}
                className={`flex min-h-[44px] flex-col items-center justify-center rounded-[16px] px-2 py-1.5 text-xs font-semibold transition-all duration-150 focus-visible:outline-2 focus-visible:outline-sky-400 whitespace-nowrap ${
                  active
                    ? theme.isLight
                      ? 'bg-white text-slate-950 shadow-sm'
                      : 'bg-white/18 text-white shadow-sm'
                    : theme.isLight
                      ? 'text-slate-600 hover:bg-white/40 hover:text-slate-900'
                      : 'text-slate-300 hover:bg-white/8 hover:text-white'
                }`}
              >
                <span>{lang === 'ru' ? day.shortRu : day.shortEn}</span>
                <span className="mt-0.5 text-[11px] font-normal tabular-nums opacity-75">
                  {day.dateLabel}
                </span>
              </button>
            );
          })}
        </div>
      </LiquidGlassSurface>

      {/* Lessons list */}
      <LiquidGlassSurface
        as="section"
        ariaLabel={tr.schedule}
        cornerRadius={28}
        designStyle={designStyle}
        optics={optics}
        isLight={theme.isLight}
        className="divide-y divide-transparent"
      >
        {dayLessons.map((lesson, idx) => {
          const isCurrent = selectedDay === 2 && countdown.lesson?.id === lesson.id;
          return (
            <div
              key={lesson.id}
              className={`flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-start sm:gap-5 sm:px-6 ${
                idx > 0 ? `border-t ${dividerClass}` : ''
              } ${
                isCurrent
                  ? theme.isLight
                    ? 'bg-sky-500/[0.08]'
                    : 'bg-white/[0.06]'
                  : ''
              }`}
            >
              {/* Time & period number */}
              <div className="flex shrink-0 items-baseline gap-3 sm:w-28 sm:flex-col sm:gap-0.5">
                <span
                  className="font-mono text-sm font-semibold tabular-nums"
                  style={{ color: isCurrent ? theme.primary : undefined }}
                >
                  {lesson.startTime} – {lesson.endTime}
                </span>
                <span className={`text-xs tabular-nums ${textMuted}`}>
                  #{lesson.number} · {tr.room} {lesson.room}
                </span>
              </div>

              {/* Subject details */}
              <div className="min-w-0 flex-1 space-y-1.5">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h3 className={`font-display text-base font-semibold ${textPrimary}`}>
                    {lesson.subject}
                  </h3>
                  {lesson.mark && (
                    <button
                      type="button"
                      onClick={() => onOpenMarks(lesson.subject)}
                      aria-label={`${lesson.subject}: ${lesson.mark.value}`}
                      className={`inline-flex items-baseline gap-0.5 rounded-xl px-2.5 py-1 font-mono text-sm font-bold tabular-nums transition-colors ${
                        lesson.mark.value === 5
                          ? 'bg-emerald-500/20 text-emerald-400 dark:text-emerald-300'
                          : 'bg-sky-500/20 text-sky-600 dark:text-sky-300'
                      }`}
                    >
                      <span>{lesson.mark.value}</span>
                      <sup className="text-[10px] opacity-80">{lesson.mark.weight}</sup>
                    </button>
                  )}
                </div>

                <p className={`text-sm leading-relaxed ${textMuted}`}>
                  {lesson.topic}
                </p>

                <div className={`flex flex-wrap items-center gap-x-3 gap-y-1 text-xs ${textMuted}`}>
                  <span>{lesson.teacher}</span>
                  {lesson.homeworkPreview && lesson.homeworkId && (
                    <>
                      <span aria-hidden="true">·</span>
                      <button
                        type="button"
                        onClick={() => onOpenHomework(lesson.homeworkId!)}
                        className="inline-flex items-center gap-1 font-medium underline-offset-4 hover:underline"
                        style={{ color: theme.primary }}
                      >
                        <BookOpen className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                        <span className="truncate max-w-[320px]">{lesson.homeworkPreview}</span>
                        <ArrowRight className="h-3 w-3 shrink-0" aria-hidden="true" />
                      </button>
                    </>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </LiquidGlassSurface>
    </div>
  );
};
