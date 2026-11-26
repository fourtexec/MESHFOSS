import React, { useEffect, useId, useMemo, useRef, useState } from 'react';
import { motion } from 'motion/react';
import {
  computeChromaticScales,
  generateLiquidDisplacementMap,
  type LiquidOpticsConfig,
} from '../../lib/liquidGlassMath';
import type { DesignStyle, MeshThemePalette } from '../../lib/meshCore';

interface LiquidGlassSurfaceProps {
  children: React.ReactNode;
  className?: string;
  cornerRadius?: number;
  designStyle: DesignStyle;
  optics: LiquidOpticsConfig;
  isLight: boolean;
  tintColor?: string;
  interactive?: boolean;
  as?: 'div' | 'section' | 'aside' | 'header' | 'nav' | 'article';
  ariaLabel?: string;
  testId?: string;
}

/**
 * Authentic iOS 26 Liquid Glass container ported from Kyant0/AndroidLiquidGlass
 * and rdev/liquid-glass-react:
 *  - Dynamically generates a physical RoundedRect SDF + circleMap displacement map
 *  - Mounts a real SVG <filter> with RGB chromatic aberration (<feDisplacementMap> x 3)
 *  - Applies backdrop-filter: url(#filter) blur() saturate(185%)
 *  - Adds specular top-left / bottom-right glass bevel rim + pointer-tracking liquid glint
 */
export const LiquidGlassSurface: React.FC<LiquidGlassSurfaceProps> = ({
  children,
  className = '',
  cornerRadius = 26,
  designStyle,
  optics,
  isLight,
  tintColor,
  interactive = false,
  as: Tag = 'div',
  ariaLabel,
  testId,
}) => {
  const rawId = useId();
  const filterId = `lg-refract-${rawId.replace(/:/g, '')}`;
  const containerRef = useRef<HTMLElement | null>(null);
  const [dims, setDims] = useState({ width: 320, height: 140 });
  const [pointer, setPointer] = useState<{ x: number; y: number; active: boolean }>({
    x: 0.5,
    y: 0.25,
    active: false,
  });

  useEffect(() => {
    const el = containerRef.current;
    if (!el || typeof ResizeObserver === 'undefined') return;
    const update = () => {
      const rect = el.getBoundingClientRect();
      const w = Math.max(48, Math.round(rect.width));
      const h = Math.max(40, Math.round(rect.height));
      setDims((prev) =>
        Math.abs(prev.width - w) > 8 || Math.abs(prev.height - h) > 8 ? { width: w, height: h } : prev
      );
    };
    update();
    const ro = new ResizeObserver(update);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const isLiquid = designStyle === 'liquid';
  const isFrosted = designStyle === 'frosted';
  const isGlass = designStyle === 'glass';

  const displacement = useMemo(() => {
    if (!isLiquid) return { dataUrl: '', maxScale: 0 };
    return generateLiquidDisplacementMap(dims.width, dims.height, cornerRadius, optics);
  }, [isLiquid, dims.width, dims.height, cornerRadius, optics]);

  const chromatic = useMemo(
    () => computeChromaticScales(Math.min(36, displacement.maxScale * 0.72), optics.chromaticAberration),
    [displacement.maxScale, optics.chromaticAberration]
  );

  const handleMouseMove = (e: React.MouseEvent<HTMLElement>) => {
    if (!interactive && !isLiquid) return;
    const rect = e.currentTarget.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return;
    setPointer({
      x: (e.clientX - rect.left) / rect.width,
      y: (e.clientY - rect.top) / rect.height,
      active: true,
    });
  };

  const handleMouseLeave = () => {
    setPointer((prev) => ({ ...prev, active: false }));
  };

  const surfaceBackground = useMemo(() => {
    if (designStyle === 'plain') {
      return isLight ? 'rgba(255, 255, 255, 0.96)' : 'rgba(22, 31, 49, 0.94)';
    }
    if (isFrosted) {
      return isLight ? 'rgba(255, 255, 255, 0.76)' : 'rgba(20, 26, 40, 0.68)';
    }
    if (isGlass) {
      return isLight ? 'rgba(255, 255, 255, 0.56)' : 'rgba(255, 255, 255, 0.08)';
    }
    // Authentic iOS 26 Liquid Glass: crystal-clear low-opacity optical tint so refracted background shines through
    return isLight
      ? 'linear-gradient(155deg, rgba(255,255,255,0.68) 0%, rgba(248,252,255,0.44) 55%, rgba(255,255,255,0.58) 100%)'
      : 'linear-gradient(155deg, rgba(255,255,255,0.14) 0%, rgba(16,22,36,0.34) 55%, rgba(255,255,255,0.08) 100%)';
  }, [designStyle, isFrosted, isGlass, isLight]);

  const backdropFilterValue = useMemo(() => {
    if (designStyle === 'plain') return 'none';
    if (isFrosted) return `blur(${Math.max(14, optics.blurPx * 2.4)}px) saturate(160%)`;
    if (isGlass) return `blur(${Math.max(10, optics.blurPx * 1.8)}px) saturate(150%)`;
    // Real iOS 26 optical stack: SVG displacement filter + light blur + Apple vibrancy saturation
    const blur = Math.max(2, optics.blurPx);
    const sat = isLight ? 165 : 195;
    const bright = isLight ? 1.04 : 1.08;
    if (displacement.dataUrl) {
      return `url(#${filterId}) blur(${blur}px) saturate(${sat}%) brightness(${bright})`;
    }
    return `blur(${blur}px) saturate(${sat}%) brightness(${bright})`;
  }, [designStyle, isFrosted, isGlass, optics.blurPx, isLight, displacement.dataUrl, filterId]);

  const rimClass =
    designStyle === 'plain'
      ? isLight
        ? 'border border-slate-200/90 shadow-sm'
        : 'border border-white/10 shadow-sm'
      : isLiquid
        ? isLight
          ? 'liquid-rim-light liquid-chromatic-ring'
          : 'liquid-rim-dark liquid-chromatic-ring'
        : isLight
          ? 'border border-white/80 shadow-md'
          : 'border border-white/15 shadow-lg';

  return (
    <Tag
      ref={containerRef as React.RefObject<never>}
      aria-label={ariaLabel}
      data-testid={testId}
      data-design-style={designStyle}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      className={`relative overflow-hidden transition-colors duration-200 ${rimClass} ${className}`}
      style={{
        borderRadius: `${cornerRadius}px`,
        background: surfaceBackground,
        backdropFilter: backdropFilterValue,
        WebkitBackdropFilter: backdropFilterValue,
      }}
    >
      {/* Element-scoped real optical SVG refraction + chromatic aberration filter */}
      {isLiquid && displacement.dataUrl && (
        <svg
          width="0"
          height="0"
          aria-hidden="true"
          className="pointer-events-none absolute h-0 w-0"
          data-testid="liquid-glass-svg-filter"
        >
          <defs>
            <filter
              id={filterId}
              x="-15%"
              y="-15%"
              width="130%"
              height="130%"
              colorInterpolationFilters="sRGB"
            >
              <feImage
                href={displacement.dataUrl}
                x="0"
                y="0"
                width="100%"
                height="100%"
                preserveAspectRatio="none"
                result="DISP_MAP"
              />
              {/* Red channel */}
              <feDisplacementMap
                in="SourceGraphic"
                in2="DISP_MAP"
                scale={chromatic.red}
                xChannelSelector="R"
                yChannelSelector="G"
                result="RED_SHIFT"
              />
              <feColorMatrix
                in="RED_SHIFT"
                type="matrix"
                values="1 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 1 0"
                result="RED_CH"
              />
              {/* Green channel */}
              <feDisplacementMap
                in="SourceGraphic"
                in2="DISP_MAP"
                scale={chromatic.green}
                xChannelSelector="R"
                yChannelSelector="G"
                result="GREEN_SHIFT"
              />
              <feColorMatrix
                in="GREEN_SHIFT"
                type="matrix"
                values="0 0 0 0 0  0 1 0 0 0  0 0 0 0 0  0 0 0 1 0"
                result="GREEN_CH"
              />
              {/* Blue channel */}
              <feDisplacementMap
                in="SourceGraphic"
                in2="DISP_MAP"
                scale={chromatic.blue}
                xChannelSelector="R"
                yChannelSelector="G"
                result="BLUE_SHIFT"
              />
              <feColorMatrix
                in="BLUE_SHIFT"
                type="matrix"
                values="0 0 0 0 0  0 0 0 0 0  0 0 1 0 0  0 0 0 1 0"
                result="BLUE_CH"
              />
              <feBlend in="RED_CH" in2="GREEN_CH" mode="screen" result="RG" />
              <feBlend in="RG" in2="BLUE_CH" mode="screen" result="RGB" />
            </filter>
          </defs>
        </svg>
      )}

      {/* Optional accent glass tint overlay */}
      {tintColor && isLiquid && (
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0"
          style={{
            background: tintColor,
            opacity: isLight ? 0.14 : 0.18,
          }}
        />
      )}

      {/* iOS 26 specular dome sheen & pointer-reactive liquid glint */}
      {isLiquid && optics.specularIntensity > 0 && (
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 transition-opacity duration-200"
          style={{
            opacity: optics.specularIntensity * (pointer.active ? 1 : 0.72),
            background: `radial-gradient(420px circle at ${Math.round(pointer.x * 100)}% ${Math.round(
              pointer.y * 100
            )}%, ${isLight ? 'rgba(255,255,255,0.58)' : 'rgba(255,255,255,0.16)'}, transparent 58%)`,
          }}
        />
      )}

      <div className="relative z-10 h-full w-full">{children}</div>
    </Tag>
  );
};

interface DockItem {
  key: string;
  label: string;
  icon: React.ReactNode;
  badgeCount?: number;
}

interface LiquidGlassDockProps {
  items: DockItem[];
  activeKey: string;
  onSelect: (key: string) => void;
  designStyle: DesignStyle;
  optics: LiquidOpticsConfig;
  isLight: boolean;
  primaryColor: string;
  layoutId?: string;
  ariaLabel?: string;
}

/**
 * Floating iOS 26 Liquid Glass Tab Bar with sliding optical glass lens
 * inspired by Kyant0/AndroidLiquidGlass LiquidBottomTabs & Apple iOS 26 Tab Bar.
 */
export const LiquidGlassDock: React.FC<LiquidGlassDockProps> = ({
  items,
  activeKey,
  onSelect,
  designStyle,
  optics,
  isLight,
  primaryColor,
  layoutId = 'ios26-liquid-lens',
  ariaLabel = 'Primary navigation',
}) => {
  return (
    <LiquidGlassSurface
      as="nav"
      ariaLabel={ariaLabel}
      cornerRadius={30}
      designStyle={designStyle}
      optics={{
        ...optics,
        refractionHeight: Math.max(16, optics.refractionHeight),
        refractionAmount: Math.max(24, optics.refractionAmount),
      }}
      isLight={isLight}
      interactive
      className="p-1.5"
    >
      <div className="relative flex items-center gap-1">
        {items.map((item) => {
          const active = item.key === activeKey;
          return (
            <button
              key={item.key}
              type="button"
              onClick={() => onSelect(item.key)}
              aria-current={active ? 'page' : undefined}
              className={`relative flex min-h-[52px] min-w-0 flex-1 flex-col items-center justify-center gap-0.5 rounded-[24px] px-1.5 py-1.5 text-[10px] font-semibold transition-colors duration-150 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-400 sm:min-h-[46px] sm:flex-row sm:gap-1.5 sm:px-2.5 sm:text-xs whitespace-nowrap ${
                active
                  ? isLight
                    ? 'text-slate-950'
                    : 'text-white'
                  : isLight
                    ? 'text-slate-700 hover:text-slate-950'
                    : 'text-slate-300 hover:text-white'
              }`}
            >
              {active && (
                <motion.span
                  layoutId={layoutId}
                  transition={{
                    type: 'spring',
                    stiffness: 340,
                    damping: 26,
                    mass: 0.78,
                  }}
                  aria-hidden="true"
                  className={`absolute inset-0 rounded-[24px] ${
                    designStyle === 'liquid'
                      ? isLight
                        ? 'liquid-lens-light liquid-chromatic-ring bg-white/78'
                        : 'liquid-lens-dark liquid-chromatic-ring bg-white/18'
                      : isLight
                        ? 'bg-white shadow-sm'
                        : 'bg-white/14'
                  }`}
                />
              )}
              <span
                className="relative z-10 flex items-center justify-center"
                style={{ color: active ? primaryColor : undefined }}
              >
                {item.icon}
              </span>
              <span className="relative z-10 max-w-[66px] truncate sm:max-w-none">{item.label}</span>
              {typeof item.badgeCount === 'number' && item.badgeCount > 0 && (
                <span className="relative z-10 hidden font-mono text-[11px] tabular-nums opacity-75 sm:inline">
                  {item.badgeCount}
                </span>
              )}
            </button>
          );
        })}
      </div>
    </LiquidGlassSurface>
  );
};

interface LiquidAmbientWallpaperProps {
  theme: MeshThemePalette;
  designStyle: DesignStyle;
}

/**
 * Architectural fluid light-ribbon canvas designed specifically so iOS 26 Liquid Glass
 * borders visibly refract crisp light contours, gradients, and caustics.
 */
export const LiquidAmbientWallpaper: React.FC<LiquidAmbientWallpaperProps> = ({
  theme,
  designStyle,
}) => {
  if (designStyle === 'plain') {
    return (
      <div
        aria-hidden="true"
        className="pointer-events-none fixed inset-0 z-0 transition-colors duration-300"
        style={{ backgroundColor: theme.background }}
      />
    );
  }

  return (
    <div
      aria-hidden="true"
      className="pointer-events-none fixed inset-0 z-0 overflow-hidden transition-colors duration-300"
      style={{ backgroundColor: theme.background }}
    >
      <svg
        className="h-full w-full object-cover"
        viewBox="0 0 1440 900"
        preserveAspectRatio="xMidYMid slice"
      >
        <defs>
          <radialGradient id="lg-orb-1" cx="14%" cy="16%" r="55%">
            <stop offset="0%" stopColor={theme.primary} stopOpacity={theme.isLight ? 0.42 : 0.48} />
            <stop offset="55%" stopColor={theme.secondary} stopOpacity={theme.isLight ? 0.16 : 0.18} />
            <stop offset="100%" stopColor={theme.background} stopOpacity="0" />
          </radialGradient>
          <radialGradient id="lg-orb-2" cx="88%" cy="22%" r="48%">
            <stop offset="0%" stopColor={theme.tertiary} stopOpacity={theme.isLight ? 0.36 : 0.42} />
            <stop offset="55%" stopColor={theme.primary} stopOpacity={theme.isLight ? 0.14 : 0.14} />
            <stop offset="100%" stopColor={theme.background} stopOpacity="0" />
          </radialGradient>
          <radialGradient id="lg-orb-3" cx="48%" cy="94%" r="54%">
            <stop offset="0%" stopColor={theme.secondary} stopOpacity={theme.isLight ? 0.34 : 0.38} />
            <stop offset="60%" stopColor={theme.tertiary} stopOpacity={theme.isLight ? 0.12 : 0.12} />
            <stop offset="100%" stopColor={theme.background} stopOpacity="0" />
          </radialGradient>
          <linearGradient id="lg-ribbon-a" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor={theme.primary} stopOpacity={theme.isLight ? 0.32 : 0.34} />
            <stop offset="50%" stopColor={theme.secondary} stopOpacity={theme.isLight ? 0.2 : 0.24} />
            <stop offset="100%" stopColor={theme.tertiary} stopOpacity="0" />
          </linearGradient>
          <linearGradient id="lg-ribbon-b" x1="100%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stopColor={theme.tertiary} stopOpacity={theme.isLight ? 0.28 : 0.3} />
            <stop offset="100%" stopColor={theme.primary} stopOpacity="0" />
          </linearGradient>
        </defs>

        <rect width="1440" height="900" fill={theme.background} />
        <rect width="1440" height="900" fill="url(#lg-orb-1)" />
        <rect width="1440" height="900" fill="url(#lg-orb-2)" />
        <rect width="1440" height="900" fill="url(#lg-orb-3)" />

        {/* Architectural silk curves that bend crisply behind Liquid Glass bevels */}
        <path
          d="M-80 280 C 260 120, 560 480, 960 220 C 1200 65, 1380 180, 1520 120 L 1520 420 C 1280 510, 960 320, 640 520 C 320 710, 80 560, -80 640 Z"
          fill="url(#lg-ribbon-a)"
        />
        <path
          d="M-40 760 C 340 540, 720 820, 1120 560 C 1300 440, 1400 500, 1500 460 L 1500 940 L -40 940 Z"
          fill="url(#lg-ribbon-b)"
        />
        {/* Crisp luminous specular edge arcs for visible refractive distortion */}
        <path
          d="M-60 284 C 260 124, 560 484, 960 224 C 1200 69, 1380 184, 1520 124"
          fill="none"
          stroke={theme.isLight ? '#FFFFFF' : theme.primary}
          strokeOpacity={theme.isLight ? 0.55 : 0.32}
          strokeWidth="2"
        />
        <path
          d="M-40 760 C 340 540, 720 820, 1120 560 C 1300 440, 1400 500, 1500 460"
          fill="none"
          stroke={theme.isLight ? '#FFFFFF' : theme.tertiary}
          strokeOpacity={theme.isLight ? 0.45 : 0.24}
          strokeWidth="1.5"
        />
      </svg>
    </div>
  );
};
