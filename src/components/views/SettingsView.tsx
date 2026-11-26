import React, { useState } from 'react';
import { Check, Copy, Laptop, RotateCcw, Sliders, Smartphone } from 'lucide-react';
import { LiquidGlassSurface } from '../liquid/LiquidGlassSurface';
import {
  ALL_NAV_TABS,
  MAX_BOTTOM_TABS,
  MESH_THEMES,
  sanitizeNavLayout,
  type DesignStyle,
  type MeshThemePalette,
  type NavLayout,
  type NavTabKey,
  type UiLanguageCode,
} from '../../lib/meshCore';
import { DEFAULT_LIQUID_OPTICS, type LiquidOpticsConfig } from '../../lib/liquidGlassMath';
import type { Translations } from '../../lib/i18n';

interface SettingsViewProps {
  designStyle: DesignStyle;
  onChangeDesignStyle: (style: DesignStyle) => void;
  theme: MeshThemePalette;
  onChangeThemeId: (id: string) => void;
  lang: UiLanguageCode;
  onChangeLang: (lang: UiLanguageCode) => void;
  optics: LiquidOpticsConfig;
  onChangeOptics: (optics: LiquidOpticsConfig) => void;
  navLayout: NavLayout;
  onChangeNavLayout: (layout: NavLayout) => void;
  tr: Translations;
}

const LANGUAGES: { code: UiLanguageCode; label: string }[] = [
  { code: 'ru', label: 'Русский' },
  { code: 'en', label: 'English' },
  { code: 'es', label: 'Español' },
  { code: 'fr', label: 'Français' },
  { code: 'hu', label: 'Magyar' },
];

export const SettingsView: React.FC<SettingsViewProps> = ({
  designStyle,
  onChangeDesignStyle,
  theme,
  onChangeThemeId,
  lang,
  onChangeLang,
  optics,
  onChangeOptics,
  navLayout,
  onChangeNavLayout,
  tr,
}) => {
  const [copiedCmd, setCopiedCmd] = useState<string | null>(null);

  const textPrimary = theme.isLight ? 'text-slate-900' : 'text-slate-50';
  const textMuted = theme.isLight ? 'text-slate-600' : 'text-slate-300/80';
  const dividerClass = theme.isLight ? 'border-slate-900/10' : 'border-white/10';

  const tabLabel = (key: NavTabKey): string => {
    const map: Record<NavTabKey, string> = {
      schedule: tr.schedule,
      marks: tr.marks,
      homeworks: tr.homeworks,
      ranking: tr.ranking,
      attendance: tr.attendance,
      moskvenok: tr.moskvenok,
      food: tr.food,
      settings: tr.settings,
    };
    return map[key];
  };

  const toggleBottomTab = (key: NavTabKey) => {
    const inBottom = navLayout.bottom.includes(key);
    if (inBottom) {
      if (navLayout.bottom.length <= 2) return;
      onChangeNavLayout(
        sanitizeNavLayout(
          navLayout.bottom.filter((k) => k !== key),
          [key, ...navLayout.more]
        )
      );
    } else {
      if (navLayout.bottom.length >= MAX_BOTTOM_TABS) return;
      onChangeNavLayout(
        sanitizeNavLayout(
          [...navLayout.bottom, key],
          navLayout.more.filter((k) => k !== key)
        )
      );
    }
  };

  const copyCommand = async (cmd: string) => {
    try {
      await navigator.clipboard?.writeText(cmd);
      setCopiedCmd(cmd);
      setTimeout(() => setCopiedCmd(null), 1800);
    } catch {
      // Clipboard API unavailable in restricted iframe
    }
  };

  return (
    <div className="space-y-6">
      {/* Design style & iOS 26 Liquid Glass Optical Engine */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        <LiquidGlassSurface
          as="section"
          ariaLabel={tr.designStyle}
          cornerRadius={28}
          designStyle={designStyle}
          optics={optics}
          isLight={theme.isLight}
          className="p-6 lg:col-span-6"
        >
          <h2 className={`font-display text-base font-bold ${textPrimary}`}>{tr.designStyle}</h2>
          <p className={`mt-1 text-xs ${textMuted}`}>
            SVG SDF + Kyant0/AndroidLiquidGlass optical refraction engine
          </p>

          <div className="mt-4 grid grid-cols-2 gap-2.5">
            {(
              [
                ['liquid', tr.liquidGlass],
                ['frosted', tr.frostedGlass],
                ['glass', tr.classicGlass],
                ['plain', tr.plainMaterial],
              ] as const
            ).map(([id, label]) => {
              const active = designStyle === id;
              return (
                <button
                  key={id}
                  type="button"
                  onClick={() => onChangeDesignStyle(id)}
                  aria-pressed={active}
                  className={`flex min-h-[46px] items-center justify-between rounded-2xl border px-4 py-2.5 text-left text-xs font-semibold transition-all whitespace-nowrap ${
                    active
                      ? theme.isLight
                        ? 'border-sky-600/45 bg-sky-600/15 text-sky-950'
                        : 'border-sky-400/50 bg-sky-500/20 text-sky-200'
                      : theme.isLight
                        ? 'border-slate-900/12 bg-white/55 text-slate-800 hover:bg-white'
                        : 'border-white/12 bg-white/6 text-slate-200 hover:bg-white/12'
                  }`}
                >
                  <span className="truncate">{label}</span>
                  {active && <Check className="h-4 w-4 shrink-0" aria-hidden="true" />}
                </button>
              );
            })}
          </div>

          <div className={`mt-6 border-t pt-5 ${dividerClass}`}>
            <h3 className={`font-display text-sm font-bold ${textPrimary}`}>{tr.language}</h3>
            <div className="mt-3 flex flex-wrap gap-2">
              {LANGUAGES.map((l) => {
                const active = lang === l.code;
                return (
                  <button
                    key={l.code}
                    type="button"
                    onClick={() => onChangeLang(l.code)}
                    aria-pressed={active}
                    className={`min-h-[40px] rounded-xl border px-3.5 py-1.5 text-xs font-semibold transition-colors whitespace-nowrap ${
                      active
                        ? theme.isLight
                          ? 'border-sky-600/45 bg-sky-600/15 text-sky-950'
                          : 'border-sky-400/50 bg-sky-500/20 text-sky-200'
                        : theme.isLight
                          ? 'border-slate-900/12 bg-white/55 text-slate-800 hover:bg-white'
                          : 'border-white/12 bg-white/6 text-slate-200 hover:bg-white/12'
                    }`}
                  >
                    {l.label}
                  </button>
                );
              })}
            </div>
          </div>
        </LiquidGlassSurface>

        {/* Live Liquid Glass Optics Sliders */}
        <LiquidGlassSurface
          as="section"
          ariaLabel={tr.liquidOptics}
          cornerRadius={28}
          designStyle={designStyle}
          optics={optics}
          isLight={theme.isLight}
          className="p-6 lg:col-span-6"
        >
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <Sliders className="h-4 w-4" style={{ color: theme.primary }} aria-hidden="true" />
              <h2 className={`font-display text-base font-bold ${textPrimary}`}>{tr.liquidOptics}</h2>
            </div>
            <button
              type="button"
              onClick={() => onChangeOptics(DEFAULT_LIQUID_OPTICS)}
              className={`inline-flex min-h-[36px] items-center gap-1.5 rounded-xl px-3 py-1 text-xs font-medium transition-colors ${textMuted} hover:bg-white/10`}
            >
              <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
              <span>iOS 26 Default</span>
            </button>
          </div>

          <div className="mt-5 space-y-4">
            <div>
              <div className="flex justify-between text-xs">
                <label htmlFor="opt-refract" className={textPrimary}>
                  {tr.refractionDepth}
                </label>
                <span className={`font-mono tabular-nums ${textMuted}`}>{optics.refractionAmount}px</span>
              </div>
              <input
                id="opt-refract"
                type="range"
                min="8"
                max="48"
                step="2"
                value={optics.refractionAmount}
                onChange={(e) => onChangeOptics({ ...optics, refractionAmount: Number(e.target.value) })}
                className="mt-1.5 w-full accent-sky-500"
              />
            </div>

            <div>
              <div className="flex justify-between text-xs">
                <label htmlFor="opt-chroma" className={textPrimary}>
                  {tr.chromaticDispersion}
                </label>
                <span className={`font-mono tabular-nums ${textMuted}`}>
                  {Math.round(optics.chromaticAberration * 100)}%
                </span>
              </div>
              <input
                id="opt-chroma"
                type="range"
                min="0"
                max="1.5"
                step="0.05"
                value={optics.chromaticAberration}
                onChange={(e) => onChangeOptics({ ...optics, chromaticAberration: Number(e.target.value) })}
                className="mt-1.5 w-full accent-sky-500"
              />
            </div>

            <div>
              <div className="flex justify-between text-xs">
                <label htmlFor="opt-blur" className={textPrimary}>
                  {tr.glassBlur}
                </label>
                <span className={`font-mono tabular-nums ${textMuted}`}>{optics.blurPx}px</span>
              </div>
              <input
                id="opt-blur"
                type="range"
                min="2"
                max="18"
                step="1"
                value={optics.blurPx}
                onChange={(e) => onChangeOptics({ ...optics, blurPx: Number(e.target.value) })}
                className="mt-1.5 w-full accent-sky-500"
              />
            </div>

            <div>
              <div className="flex justify-between text-xs">
                <label htmlFor="opt-spec" className={textPrimary}>
                  {tr.specularGlow}
                </label>
                <span className={`font-mono tabular-nums ${textMuted}`}>
                  {Math.round(optics.specularIntensity * 100)}%
                </span>
              </div>
              <input
                id="opt-spec"
                type="range"
                min="0"
                max="1.4"
                step="0.1"
                value={optics.specularIntensity}
                onChange={(e) => onChangeOptics({ ...optics, specularIntensity: Number(e.target.value) })}
                className="mt-1.5 w-full accent-sky-500"
              />
            </div>
          </div>
        </LiquidGlassSurface>
      </div>

      {/* 11 MESHFOSS Themes */}
      <LiquidGlassSurface
        as="section"
        ariaLabel={tr.themePalette}
        cornerRadius={28}
        designStyle={designStyle}
        optics={optics}
        isLight={theme.isLight}
        className="p-6"
      >
        <h2 className={`font-display text-base font-bold ${textPrimary}`}>{tr.themePalette}</h2>
        <div className="mt-4 grid grid-cols-2 gap-2.5 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
          {MESH_THEMES.map((pal) => {
            const active = pal.id === theme.id;
            return (
              <button
                key={pal.id}
                type="button"
                onClick={() => onChangeThemeId(pal.id)}
                aria-pressed={active}
                className={`flex min-h-[52px] items-center gap-2.5 rounded-2xl border px-3 py-2 text-left text-xs font-semibold transition-all ${
                  active
                    ? 'border-sky-500 ring-2 ring-sky-500/35'
                    : theme.isLight
                      ? 'border-slate-900/12 hover:bg-white/60'
                      : 'border-white/12 hover:bg-white/10'
                }`}
              >
                <span
                  aria-hidden="true"
                  className="h-6 w-6 shrink-0 rounded-full border border-white/30"
                  style={{
                    background: `linear-gradient(135deg, ${pal.background} 20%, ${pal.primary} 100%)`,
                  }}
                />
                <span className={`truncate ${textPrimary}`}>
                  {lang === 'ru' ? pal.nameRu : pal.nameEn}
                </span>
              </button>
            );
          })}
        </div>
      </LiquidGlassSurface>

      {/* Dock Customization + Desktop & Android GitHub Actions Build Hub */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        <LiquidGlassSurface
          as="section"
          ariaLabel={tr.dockCustomization}
          cornerRadius={28}
          designStyle={designStyle}
          optics={optics}
          isLight={theme.isLight}
          className="p-6 lg:col-span-5"
        >
          <h2 className={`font-display text-base font-bold ${textPrimary}`}>{tr.dockCustomization}</h2>
          <p className={`mt-1 text-xs ${textMuted}`}>
            {navLayout.bottom.length} / {MAX_BOTTOM_TABS} вкладок в плавающей линзе Liquid Glass
          </p>
          <div className="mt-4 flex flex-wrap gap-2">
            {ALL_NAV_TABS.map((key) => {
              const selected = navLayout.bottom.includes(key);
              const disabled = !selected && navLayout.bottom.length >= MAX_BOTTOM_TABS;
              return (
                <button
                  key={key}
                  type="button"
                  disabled={disabled}
                  onClick={() => toggleBottomTab(key)}
                  aria-pressed={selected}
                  className={`inline-flex min-h-[40px] items-center gap-1.5 rounded-xl border px-3.5 py-1.5 text-xs font-semibold transition-colors whitespace-nowrap ${
                    selected
                      ? theme.isLight
                        ? 'border-sky-600/45 bg-sky-600/15 text-sky-950'
                        : 'border-sky-400/50 bg-sky-500/20 text-sky-200'
                      : disabled
                        ? 'cursor-not-allowed border-transparent opacity-40'
                        : theme.isLight
                          ? 'border-slate-900/12 bg-white/55 text-slate-700 hover:bg-white'
                          : 'border-white/12 bg-white/6 text-slate-300 hover:bg-white/12'
                  }`}
                >
                  {selected && <Check className="h-3.5 w-3.5" aria-hidden="true" />}
                  <span>{tabLabel(key)}</span>
                </button>
              );
            })}
          </div>
        </LiquidGlassSurface>

        <LiquidGlassSurface
          as="section"
          ariaLabel={tr.desktopBuilds}
          cornerRadius={28}
          designStyle={designStyle}
          optics={optics}
          isLight={theme.isLight}
          className="p-6 lg:col-span-7"
        >
          <div className="flex items-center gap-2">
            <Laptop className="h-4 w-4" style={{ color: theme.primary }} aria-hidden="true" />
            <h2 className={`font-display text-base font-bold ${textPrimary}`}>{tr.desktopBuilds}</h2>
          </div>

          <div className={`mt-4 divide-y text-xs ${dividerClass}`}>
            {[
              {
                os: 'Windows (x64 & arm64)',
                formats: 'NSIS Installer (.exe) · Portable (.exe) · Windows 11 Acrylic',
                workflow: '.github/workflows/desktop.yml',
                cmd: 'npm run desktop:build',
              },
              {
                os: 'macOS (Apple Silicon & Intel)',
                formats: 'DMG & ZIP (arm64 / x64) · Tahoe Vibrancy under-window',
                workflow: '.github/workflows/desktop.yml',
                cmd: 'npm --prefix desktop run dist:mac',
              },
              {
                os: 'Linux (x64 & arm64)',
                formats: 'AppImage · Debian (.deb) · Fedora/RPM (.rpm)',
                workflow: '.github/workflows/desktop.yml',
                cmd: 'npm --prefix desktop run dist:linux',
              },
              {
                os: 'Android (Kotlin / Compose + Kyant0 Backdrop 2.0.1)',
                formats: 'Signed Release APK · AGSL 7-band Liquid Glass',
                workflow: '.github/workflows/android.yml',
                cmd: './gradlew assembleRelease',
              },
            ].map((row) => (
              <div key={row.os} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <p className={`font-display font-semibold ${textPrimary}`}>{row.os}</p>
                  <p className={`mt-0.5 ${textMuted}`}>{row.formats}</p>
                </div>
                <button
                  type="button"
                  onClick={() => copyCommand(row.cmd)}
                  className={`inline-flex min-h-[36px] shrink-0 items-center gap-1.5 rounded-xl border px-3 py-1.5 font-mono text-[11px] transition-colors ${
                    theme.isLight
                      ? 'border-slate-900/12 bg-white/70 text-slate-800 hover:bg-white'
                      : 'border-white/12 bg-white/6 text-slate-200 hover:bg-white/12'
                  }`}
                >
                  {row.os.startsWith('Android') ? (
                    <Smartphone className="h-3.5 w-3.5" aria-hidden="true" />
                  ) : (
                    <Copy className="h-3.5 w-3.5" aria-hidden="true" />
                  )}
                  <span>{copiedCmd === row.cmd ? 'Copied' : row.cmd}</span>
                </button>
              </div>
            ))}
          </div>
        </LiquidGlassSurface>
      </div>
    </div>
  );
};
