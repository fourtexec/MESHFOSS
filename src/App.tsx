import React, { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import {
  Award,
  BookOpenCheck,
  CalendarDays,
  CheckSquare,
  CreditCard,
  GraduationCap,
  MoreHorizontal,
  Settings,
  UserCircle2,
  Utensils,
  X,
} from 'lucide-react';
import {
  LiquidAmbientWallpaper,
  LiquidGlassDock,
  LiquidGlassSurface,
} from './components/liquid/LiquidGlassSurface';
import { ScheduleView } from './components/views/ScheduleView';
import { GradesView } from './components/views/GradesView';
import { HomeworksView } from './components/views/HomeworksView';
import {
  AttendanceView,
  MealsView,
  MoskvenokView,
  RankingView,
} from './components/views/CampusViews';
import { SettingsView } from './components/views/SettingsView';
import { AccountsAuthModal } from './components/modals/AccountsAuthModal';
import {
  DEFAULT_BOTTOM_TABS,
  DEFAULT_MORE_TABS,
  MESH_THEMES,
  sanitizeNavLayout,
  type DesignStyle,
  type NavLayout,
  type NavTabKey,
  type UiLanguageCode,
} from './lib/meshCore';
import { DEFAULT_LIQUID_OPTICS, type LiquidOpticsConfig } from './lib/liquidGlassMath';
import { t } from './lib/i18n';
import {
  SEED_ABSENCES,
  SEED_ACCOUNTS,
  SEED_GRADES,
  SEED_HOMEWORKS,
  SEED_MENU,
  SEED_PASSES,
  SEED_RANKING,
  SEED_SCHEDULE,
  SEED_TRANSACTIONS,
  type CafeteriaDish,
  type HomeworkItem,
  type MeshAccount,
} from './data/meshSeedData';

const STORAGE_KEY = 'meshfoss_desktop_state_v25';

function loadSavedState<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return fallback;
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    return (parsed[key] as T) ?? fallback;
  } catch {
    return fallback;
  }
}

export default function App() {
  const [activeTab, setActiveTab] = useState<NavTabKey>('schedule');
  const [selectedDay, setSelectedDay] = useState<number>(2); // Wednesday
  const [designStyle, setDesignStyle] = useState<DesignStyle>(() =>
    loadSavedState<DesignStyle>('designStyle', 'liquid')
  );
  const [themeId, setThemeId] = useState<string>(() => loadSavedState<string>('themeId', 'midnight'));
  const [lang, setLang] = useState<UiLanguageCode>(() => loadSavedState<UiLanguageCode>('lang', 'ru'));
  const [optics, setOptics] = useState<LiquidOpticsConfig>(() =>
    loadSavedState<LiquidOpticsConfig>('optics', DEFAULT_LIQUID_OPTICS)
  );
  const [navLayout, setNavLayout] = useState<NavLayout>(() =>
    sanitizeNavLayout(
      loadSavedState<NavTabKey[]>('navBottom', DEFAULT_BOTTOM_TABS),
      loadSavedState<NavTabKey[]>('navMore', DEFAULT_MORE_TABS)
    )
  );

  const [accounts, setAccounts] = useState<MeshAccount[]>(SEED_ACCOUNTS);
  const [activeAccountId, setActiveAccountId] = useState<string>(SEED_ACCOUNTS[0].id);
  const [authModalOpen, setAuthModalOpen] = useState(false);
  const [moreSheetOpen, setMoreSheetOpen] = useState(false);

  const [homeworks, setHomeworks] = useState<HomeworkItem[]>(() =>
    loadSavedState<HomeworkItem[]>('homeworks', SEED_HOMEWORKS)
  );
  const [dishes, setDishes] = useState<CafeteriaDish[]>(SEED_MENU);
  const [focusedHomeworkId, setFocusedHomeworkId] = useState<string | null>(null);
  const [highlightSubject, setHighlightSubject] = useState<string | null>(null);
  const [rankingSubjectId, setRankingSubjectId] = useState<string>('all');

  const theme = useMemo(
    () => MESH_THEMES.find((p) => p.id === themeId) ?? MESH_THEMES[0],
    [themeId]
  );

  const activeAccount = useMemo(
    () => accounts.find((a) => a.id === activeAccountId) ?? accounts[0],
    [accounts, activeAccountId]
  );

  const tr = useMemo(() => t(lang), [lang]);

  // Sync Tailwind dark class & CSS theme variables cleanly without split-brain contrast
  useEffect(() => {
    const root = document.documentElement;
    if (theme.isLight) {
      root.classList.remove('dark');
    } else {
      root.classList.add('dark');
    }
    root.lang = lang;
    root.style.setProperty('--mesh-primary', theme.primary);
    root.style.setProperty('--mesh-bg', theme.background);
    root.style.setProperty('--mesh-surface', theme.surface);
    root.style.setProperty('--mesh-secondary', theme.secondary);
    root.style.setProperty('--mesh-tertiary', theme.tertiary);
  }, [theme, lang]);

  useEffect(() => {
    try {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          designStyle,
          themeId,
          lang,
          optics,
          navBottom: navLayout.bottom,
          navMore: navLayout.more,
          homeworks,
        })
      );
    } catch {
      // Storage quota ignored gracefully
    }
  }, [designStyle, themeId, lang, optics, navLayout, homeworks]);

  const pendingHomeworkCount = useMemo(
    () => homeworks.filter((h) => !h.done).length,
    [homeworks]
  );

  const tabMeta: Record<NavTabKey, { label: string; icon: React.ReactNode; badge?: number }> = {
    schedule: { label: tr.schedule, icon: <CalendarDays className="h-[18px] w-[18px]" /> },
    marks: { label: tr.marks, icon: <GraduationCap className="h-[18px] w-[18px]" /> },
    homeworks: {
      label: tr.homeworks,
      icon: <BookOpenCheck className="h-[18px] w-[18px]" />,
      badge: pendingHomeworkCount,
    },
    ranking: { label: tr.ranking, icon: <Award className="h-[18px] w-[18px]" /> },
    attendance: { label: tr.attendance, icon: <CheckSquare className="h-[18px] w-[18px]" /> },
    moskvenok: { label: tr.moskvenok, icon: <CreditCard className="h-[18px] w-[18px]" /> },
    food: { label: tr.food, icon: <Utensils className="h-[18px] w-[18px]" /> },
    settings: { label: tr.settings, icon: <Settings className="h-[18px] w-[18px]" /> },
  };

  const dockItems = useMemo(() => {
    const primary = navLayout.bottom.map((key) => ({
      key,
      label: tabMeta[key].label,
      icon: tabMeta[key].icon,
      badgeCount: tabMeta[key].badge,
    }));
    return [
      ...primary,
      {
        key: 'more',
        label: tr.more,
        icon: <MoreHorizontal className="h-[18px] w-[18px]" />,
      },
    ];
  }, [navLayout.bottom, tr, pendingHomeworkCount]);

  const dockActiveKey = navLayout.bottom.includes(activeTab) ? activeTab : 'more';

  const handleToggleHomeworkDone = (id: string) => {
    setHomeworks((prev) => prev.map((h) => (h.id === id ? { ...h, done: !h.done } : h)));
  };

  const handleCompleteCdz = (homeworkId: string, scorePercent: number) => {
    setHomeworks((prev) =>
      prev.map((h) =>
        h.id === homeworkId && h.cdz
          ? {
              ...h,
              done: scorePercent >= 50 ? true : h.done,
              cdz: { ...h.cdz, status: 'completed', scorePercent },
            }
          : h
      )
    );
  };

  const handleTogglePreorder = (id: string) => {
    setDishes((prev) => prev.map((d) => (d.id === id ? { ...d, preordered: !d.preordered } : d)));
  };

  const textPrimary = theme.isLight ? 'text-slate-900' : 'text-slate-50';
  const textMuted = theme.isLight ? 'text-slate-600' : 'text-slate-300/80';

  return (
    <div
      className={`relative min-h-screen w-full transition-colors duration-300 ${
        theme.isLight ? 'text-slate-900' : 'text-slate-50'
      }`}
      data-theme={theme.id}
      data-design={designStyle}
    >
      <LiquidAmbientWallpaper theme={theme} designStyle={designStyle} />

      <div className="relative z-10 mx-auto flex min-h-screen max-w-[1440px] gap-6 px-4 pt-4 pb-28 sm:px-6 lg:py-6">
        {/* Desktop Liquid Glass Sidebar */}
        <aside className="hidden w-[256px] shrink-0 lg:flex lg:flex-col">
          <LiquidGlassSurface
            as="div"
            cornerRadius={30}
            designStyle={designStyle}
            optics={optics}
            isLight={theme.isLight}
            className="sticky top-6 flex h-[calc(100vh-3rem)] flex-col p-4"
          >
            {/* Brand mark */}
            <div className="flex items-center gap-3 px-2 py-2">
              <div
                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl font-display text-lg font-extrabold text-white shadow-md"
                style={{
                  background: `linear-gradient(135deg, ${theme.primary}, ${theme.secondary})`,
                }}
              >
                M
              </div>
              <div className="min-w-0">
                <span className={`block font-display text-base font-extrabold tracking-tight ${textPrimary}`}>
                  MESHFOSS
                </span>
                <span className={`block truncate text-xs ${textMuted}`}>
                  school.mos.ru
                </span>
              </div>
            </div>

            {/* Sidebar Navigation */}
            <nav aria-label="Sidebar navigation" className="mt-5 flex-1 space-y-1 overflow-y-auto">
              {(
                [
                  'schedule',
                  'marks',
                  'homeworks',
                  'ranking',
                  'attendance',
                  'moskvenok',
                  'food',
                  'settings',
                ] as NavTabKey[]
              ).map((key) => {
                const active = activeTab === key;
                const meta = tabMeta[key];
                return (
                  <button
                    key={key}
                    type="button"
                    onClick={() => setActiveTab(key)}
                    aria-current={active ? 'page' : undefined}
                    className={`relative flex min-h-[44px] w-full items-center justify-between gap-3 rounded-2xl px-3.5 py-2.5 text-left text-sm font-semibold transition-colors duration-150 focus-visible:outline-2 focus-visible:outline-sky-400 whitespace-nowrap ${
                      active
                        ? theme.isLight
                          ? 'text-slate-950'
                          : 'text-white'
                        : theme.isLight
                          ? 'text-slate-700 hover:bg-white/45 hover:text-slate-950'
                          : 'text-slate-300 hover:bg-white/8 hover:text-white'
                    }`}
                  >
                    {active && (
                      <motion.span
                        layoutId="desktop-sidebar-liquid-lens"
                        transition={{ type: 'spring', stiffness: 360, damping: 28, mass: 0.78 }}
                        aria-hidden="true"
                        className={`absolute inset-0 rounded-2xl ${
                          designStyle === 'liquid'
                            ? theme.isLight
                              ? 'liquid-lens-light liquid-chromatic-ring bg-white/82'
                              : 'liquid-lens-dark liquid-chromatic-ring bg-white/18'
                            : theme.isLight
                              ? 'bg-white shadow-sm'
                              : 'bg-white/14'
                        }`}
                      />
                    )}
                    <span className="relative z-10 flex items-center gap-3 min-w-0">
                      <span style={{ color: active ? theme.primary : undefined }}>{meta.icon}</span>
                      <span className="truncate">{meta.label}</span>
                    </span>
                    {typeof meta.badge === 'number' && meta.badge > 0 && (
                      <span className={`relative z-10 font-mono text-xs tabular-nums ${textMuted}`}>
                        {meta.badge}
                      </span>
                    )}
                  </button>
                );
              })}
            </nav>

            {/* Account profile trigger */}
            <button
              type="button"
              onClick={() => setAuthModalOpen(true)}
              aria-label={`${tr.accounts}: ${activeAccount.name}`}
              className={`mt-4 flex min-h-[54px] w-full items-center gap-3 rounded-2xl border p-2.5 text-left transition-colors ${
                theme.isLight
                  ? 'border-slate-900/10 bg-white/50 hover:bg-white/80'
                  : 'border-white/10 bg-white/6 hover:bg-white/12'
              }`}
            >
              <div
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl font-display text-xs font-bold text-white"
                style={{ backgroundColor: theme.primary }}
              >
                {activeAccount.initials}
              </div>
              <div className="min-w-0 flex-1">
                <p className={`truncate font-display text-xs font-bold ${textPrimary}`}>
                  {activeAccount.name}
                </p>
                <p className={`truncate text-[11px] ${textMuted}`}>
                  {activeAccount.isLive ? tr.liveMesh : activeAccount.className}
                </p>
              </div>
            </button>
          </LiquidGlassSurface>
        </aside>

        {/* Main Workspace */}
        <div className="flex min-w-0 flex-1 flex-col">
          {/* Clean Top Bar Contract: Screen Title + School Context — Account Action */}
          <header className="mb-6 flex items-center justify-between gap-4">
            <div className="min-w-0">
              <p className={`truncate text-xs font-medium ${textMuted}`}>
                {activeAccount.school} · {activeAccount.className}
              </p>
              <h1 className={`mt-0.5 font-display text-2xl font-extrabold tracking-tight sm:text-3xl ${textPrimary}`}>
                {tabMeta[activeTab].label}
              </h1>
            </div>

            <LiquidGlassSurface
              cornerRadius={20}
              designStyle={designStyle}
              optics={{ ...optics, refractionAmount: Math.min(20, optics.refractionAmount) }}
              isLight={theme.isLight}
              className="shrink-0 p-1"
            >
              <button
                type="button"
                onClick={() => setAuthModalOpen(true)}
                className={`inline-flex min-h-[40px] items-center gap-2 rounded-2xl px-3.5 py-2 text-xs font-semibold transition-colors whitespace-nowrap shrink-0 ${
                  theme.isLight
                    ? 'text-slate-900 hover:bg-white/60'
                    : 'text-white hover:bg-white/12'
                }`}
              >
                <UserCircle2 className="h-4 w-4" style={{ color: theme.primary }} aria-hidden="true" />
                <span>{activeAccount.isLive ? activeAccount.name : tr.signInMesh}</span>
              </button>
            </LiquidGlassSurface>
          </header>

          {/* Active Feature Screen */}
          <main className="flex-1 pb-24 lg:pb-8">
            {activeTab === 'schedule' && (
              <ScheduleView
                lessons={SEED_SCHEDULE}
                selectedDay={selectedDay}
                onSelectDay={setSelectedDay}
                onOpenHomework={(hwId) => {
                  setFocusedHomeworkId(hwId);
                  setActiveTab('homeworks');
                }}
                onOpenMarks={(subjectName) => {
                  setHighlightSubject(subjectName);
                  setActiveTab('marks');
                }}
                designStyle={designStyle}
                optics={optics}
                theme={theme}
                tr={tr}
                lang={lang}
              />
            )}

            {activeTab === 'marks' && (
              <GradesView
                subjects={SEED_GRADES}
                highlightSubject={highlightSubject}
                onJumpToRanking={(subjectId) => {
                  setRankingSubjectId(subjectId);
                  setActiveTab('ranking');
                }}
                designStyle={designStyle}
                optics={optics}
                theme={theme}
                tr={tr}
              />
            )}

            {activeTab === 'homeworks' && (
              <HomeworksView
                homeworks={homeworks}
                focusedHomeworkId={focusedHomeworkId}
                onToggleDone={handleToggleHomeworkDone}
                onCompleteCdz={handleCompleteCdz}
                designStyle={designStyle}
                optics={optics}
                theme={theme}
                tr={tr}
              />
            )}

            {activeTab === 'ranking' && (
              <RankingView
                entries={SEED_RANKING}
                selectedSubjectId={rankingSubjectId}
                onSelectSubjectId={setRankingSubjectId}
                designStyle={designStyle}
                optics={optics}
                theme={theme}
                tr={tr}
              />
            )}

            {activeTab === 'attendance' && (
              <AttendanceView
                passes={SEED_PASSES}
                absences={SEED_ABSENCES}
                designStyle={designStyle}
                optics={optics}
                theme={theme}
                tr={tr}
              />
            )}

            {activeTab === 'food' && (
              <MealsView
                dishes={dishes}
                transactions={SEED_TRANSACTIONS}
                onTogglePreorder={handleTogglePreorder}
                designStyle={designStyle}
                optics={optics}
                theme={theme}
                tr={tr}
              />
            )}

            {activeTab === 'moskvenok' && (
              <MoskvenokView
                account={activeAccount}
                designStyle={designStyle}
                optics={optics}
                theme={theme}
                tr={tr}
              />
            )}

            {activeTab === 'settings' && (
              <SettingsView
                designStyle={designStyle}
                onChangeDesignStyle={setDesignStyle}
                theme={theme}
                onChangeThemeId={setThemeId}
                lang={lang}
                onChangeLang={setLang}
                optics={optics}
                onChangeOptics={setOptics}
                navLayout={navLayout}
                onChangeNavLayout={setNavLayout}
                tr={tr}
              />
            )}
          </main>
        </div>
      </div>

      {/* Floating iOS 26 Liquid Glass Optical Lens Dock (Mobile & Tablet) */}
      <div className="pointer-events-none fixed inset-x-0 bottom-3 z-40 flex justify-center px-3 lg:hidden">
        <div className="pointer-events-auto w-full max-w-xl">
          <LiquidGlassDock
            items={dockItems}
            activeKey={dockActiveKey}
            onSelect={(key) => {
              if (key === 'more') {
                setMoreSheetOpen(true);
              } else {
                setMoreSheetOpen(false);
                setActiveTab(key as NavTabKey);
              }
            }}
            designStyle={designStyle}
            optics={optics}
            isLight={theme.isLight}
            primaryColor={theme.primary}
            ariaLabel="Liquid Glass floating dock"
          />
        </div>
      </div>

      {/* Mobile Liquid Glass "More" Sheet (matches MESHFOSS Android "Ещё") */}
      {moreSheetOpen && (
        <div
          className="fixed inset-0 z-50 flex items-end justify-center bg-black/55 p-3 backdrop-blur-sm lg:hidden"
          role="dialog"
          aria-modal="true"
          aria-label={tr.more}
        >
          <LiquidGlassSurface
            cornerRadius={30}
            designStyle={designStyle}
            optics={optics}
            isLight={theme.isLight}
            className="w-full max-w-md p-5"
          >
            <div className="flex items-center justify-between">
              <h2 className={`font-display text-base font-bold ${textPrimary}`}>{tr.more}</h2>
              <button
                type="button"
                onClick={() => setMoreSheetOpen(false)}
                aria-label="Закрыть меню"
                className="flex h-10 w-10 items-center justify-center rounded-full hover:bg-white/10"
              >
                <X className={`h-5 w-5 ${textPrimary}`} />
              </button>
            </div>
            <div className="mt-4 grid grid-cols-2 gap-2.5">
              {navLayout.more.map((key) => (
                <button
                  key={key}
                  type="button"
                  onClick={() => {
                    setActiveTab(key);
                    setMoreSheetOpen(false);
                  }}
                  className={`flex min-h-[50px] items-center gap-3 rounded-2xl border px-4 py-3 text-left text-sm font-semibold transition-colors ${
                    theme.isLight
                      ? 'border-slate-900/12 bg-white/65 text-slate-900 hover:bg-white'
                      : 'border-white/12 bg-white/8 text-white hover:bg-white/14'
                  }`}
                >
                  <span style={{ color: theme.primary }}>{tabMeta[key].icon}</span>
                  <span className="truncate">{tabMeta[key].label}</span>
                </button>
              ))}
            </div>
          </LiquidGlassSurface>
        </div>
      )}

      <AccountsAuthModal
        open={authModalOpen}
        onClose={() => setAuthModalOpen(false)}
        accounts={accounts}
        activeAccountId={activeAccountId}
        onSelectAccount={setActiveAccountId}
        onAddLiveAccount={(acc) => {
          setAccounts((prev) => [acc, ...prev]);
          setActiveAccountId(acc.id);
        }}
        designStyle={designStyle}
        optics={optics}
        theme={theme}
        tr={tr}
      />
    </div>
  );
}
