/**
 * Authentic iOS 26 Liquid Glass optical physics engine.
 *
 * Combines:
 *  - Kyant0/AndroidLiquidGlass (`com.kyant.backdrop.internal.Shaders`):
 *      Rounded-rect SDF, analytical normal gradient, circular bezel refraction
 *      `circleMap(1 - (-sd / refractionHeight))`, depth effect, and corner-weighted
 *      spectral chromatic dispersion.
 *  - rdev/liquid-glass-react (`shader-utils.ts`):
 *      Dynamic offscreen-canvas SVG `<feDisplacementMap>` RGB channel map generation
 *      for genuine pixel-bending backdrop refraction in Chromium/Electron & Web.
 */

export interface Vec2 {
  x: number;
  y: number;
}

export interface LiquidOpticsConfig {
  /** Height/thickness of the curved refractive bezel in px (iOS 26 default: 16-24px). */
  refractionHeight: number;
  /** Maximum optical pixel shift at the outer rim in px (iOS 26 default: 24-38px). */
  refractionAmount: number;
  /** Chromatic dispersion intensity across R/G/B wavelengths (0 to 1.5). */
  chromaticAberration: number;
  /** Center-to-edge dome depth bias (0 = flat center, 0.35 = convex lens). */
  depthEffect: number;
  /** Backdrop Gaussian blur in px (iOS 26 keeps blur light, 3-8px, so refraction stays crisp). */
  blurPx: number;
  /** Specular rim highlight intensity (0 to 1.5). */
  specularIntensity: number;
}

export const DEFAULT_LIQUID_OPTICS: LiquidOpticsConfig = {
  refractionHeight: 20,
  refractionAmount: 30,
  chromaticAberration: 0.85,
  depthEffect: 0.18,
  blurPx: 6,
  specularIntensity: 1.0,
};

/**
 * Signed distance to a rounded rectangle centered at (0, 0) with half-extents (hx, hy) and corner radius r.
 * Negative inside the shape, 0 on the boundary, positive outside.
 */
export function sdRoundedRect(x: number, y: number, hx: number, hy: number, r: number): number {
  const cr = Math.min(Math.max(0, r), hx, hy);
  const qx = Math.abs(x) - hx + cr;
  const qy = Math.abs(y) - hy + cr;
  const outX = Math.max(qx, 0);
  const outY = Math.max(qy, 0);
  return Math.hypot(outX, outY) + Math.min(Math.max(qx, qy), 0) - cr;
}

/**
 * Analytical outward unit normal gradient of sdRoundedRect (matches Kyant0 Shaders.kt gradSdRoundedRect).
 */
export function gradSdRoundedRect(x: number, y: number, hx: number, hy: number, r: number): Vec2 {
  const cr = Math.min(Math.max(0, r), hx, hy);
  const sx = x >= 0 ? 1 : -1;
  const sy = y >= 0 ? 1 : -1;
  const qx = Math.abs(x) - hx + cr;
  const qy = Math.abs(y) - hy + cr;

  let gx = 0;
  let gy = 0;
  if (qx > 0 || qy > 0) {
    const ox = Math.max(qx, 0);
    const oy = Math.max(qy, 0);
    const len = Math.hypot(ox, oy) || 1;
    gx = (ox / len) * sx;
    gy = (oy / len) * sy;
  } else if (qx > qy) {
    gx = sx;
    gy = 0;
  } else {
    gx = 0;
    gy = sy;
  }
  return { x: gx, y: gy };
}

/**
 * Circular lens bezel profile from Kyant0/AndroidLiquidGlass:
 * smoothly bends light rays near the curved glass edge (x in [0, 1]).
 */
export function circleMap(x: number): number {
  const clamped = Math.min(1, Math.max(0, x));
  return 1 - Math.sqrt(Math.max(0, 1 - clamped * clamped));
}

/**
 * Computes the optical displacement vector (dx, dy) in pixels for a point (x, y)
 * relative to the center of a glass element of size (width x height) and cornerRadius.
 */
export function computeRefractionVector(
  x: number,
  y: number,
  width: number,
  height: number,
  cornerRadius: number,
  optics: Pick<LiquidOpticsConfig, 'refractionHeight' | 'refractionAmount' | 'depthEffect'>
): Vec2 {
  const hx = width * 0.5;
  const hy = height * 0.5;
  if (hx <= 0 || hy <= 0 || optics.refractionHeight <= 0 || optics.refractionAmount <= 0) {
    return { x: 0, y: 0 };
  }

  const sd = sdRoundedRect(x, y, hx, hy, cornerRadius);
  if (sd > 0 || -sd >= optics.refractionHeight) {
    return { x: 0, y: 0 };
  }

  const normalizedEdge = 1 - -sd / optics.refractionHeight;
  const d = circleMap(normalizedEdge) * optics.refractionAmount;

  const gradRadius = Math.min(cornerRadius * 1.5, Math.min(hx, hy));
  const grad = gradSdRoundedRect(x, y, hx, hy, gradRadius);

  const centerLen = Math.hypot(x, y);
  const cx = centerLen > 0.0001 ? x / centerLen : 0;
  const cy = centerLen > 0.0001 ? y / centerLen : 0;

  let nx = grad.x + optics.depthEffect * cx;
  let ny = grad.y + optics.depthEffect * cy;
  const nLen = Math.hypot(nx, ny);
  if (nLen > 0.0001) {
    nx /= nLen;
    ny /= nLen;
  }

  // Negative direction pulls edge background inward like a thick convex iOS 26 lens
  return { x: -d * nx, y: -d * ny };
}

/**
 * Computes per-channel chromatic dispersion scales for R, G, B SVG <feDisplacementMap> passes.
 */
export function computeChromaticScales(
  baseScale: number,
  chromaticAberration: number
): { red: number; green: number; blue: number } {
  const spread = Math.max(0, chromaticAberration) * 0.14;
  return {
    red: baseScale * (1 + spread),
    green: baseScale,
    blue: baseScale * (1 - spread),
  };
}

/**
 * Computes directional iOS 26 specular rim highlight intensity at a boundary normal.
 */
export function computeSpecularRim(normal: Vec2, lightAngleRad = -Math.PI / 4, falloff = 2.2): number {
  const lx = Math.cos(lightAngleRad);
  const ly = Math.sin(lightAngleRad);
  const dot = normal.x * lx + normal.y * ly;
  return Math.pow(Math.abs(dot), falloff);
}

const displacementCache = new Map<string, { dataUrl: string; maxScale: number }>();

/**
 * Generates an encoded RGBA PNG data URL for SVG `<feImage>` + `<feDisplacementMap>`.
 * Red channel = X displacement (128 = 0), Green/Blue channel = Y displacement (128 = 0).
 */
export function generateLiquidDisplacementMap(
  width: number,
  height: number,
  cornerRadius: number,
  optics: LiquidOpticsConfig
): { dataUrl: string; maxScale: number } {
  const sampleW = Math.max(24, Math.min(180, Math.round(width * 0.5)));
  const sampleH = Math.max(24, Math.min(180, Math.round(height * 0.5)));
  const scaledRadius = (cornerRadius / Math.max(1, Math.min(width, height))) * Math.min(sampleW, sampleH);
  const scaledHeight = (optics.refractionHeight / Math.max(1, Math.min(width, height))) * Math.min(sampleW, sampleH);

  const cacheKey = `${sampleW}x${sampleH}:r${Math.round(scaledRadius)}:h${Math.round(scaledHeight)}:a${Math.round(optics.refractionAmount)}:d${optics.depthEffect.toFixed(2)}`;
  const cached = displacementCache.get(cacheKey);
  if (cached) return cached;

  if (typeof document === 'undefined') {
    return { dataUrl: '', maxScale: optics.refractionAmount };
  }

  const canvas = document.createElement('canvas');
  canvas.width = sampleW;
  canvas.height = sampleH;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    return { dataUrl: '', maxScale: optics.refractionAmount };
  }

  const vectors = new Float32Array(sampleW * sampleH * 2);
  let maxMag = 1;

  for (let py = 0; py < sampleH; py++) {
    const cy = py + 0.5 - sampleH * 0.5;
    for (let px = 0; px < sampleW; px++) {
      const cx = px + 0.5 - sampleW * 0.5;
      const v = computeRefractionVector(cx, cy, sampleW, sampleH, scaledRadius, {
        refractionHeight: Math.max(3, scaledHeight),
        refractionAmount: optics.refractionAmount,
        depthEffect: optics.depthEffect,
      });
      const idx = (py * sampleW + px) * 2;
      vectors[idx] = v.x;
      vectors[idx + 1] = v.y;
      maxMag = Math.max(maxMag, Math.abs(v.x), Math.abs(v.y));
    }
  }

  const imageData = ctx.createImageData(sampleW, sampleH);
  const data = imageData.data;

  for (let py = 0; py < sampleH; py++) {
    for (let px = 0; px < sampleW; px++) {
      const idx = (py * sampleW + px) * 2;
      const edgeDist = Math.min(px, py, sampleW - 1 - px, sampleH - 1 - py);
      const edgeFeather = Math.min(1, edgeDist / 2);
      const dx = (vectors[idx] * edgeFeather) / maxMag;
      const dy = (vectors[idx + 1] * edgeFeather) / maxMag;

      const r = Math.max(0, Math.min(255, Math.round((dx * 0.5 + 0.5) * 255)));
      const g = Math.max(0, Math.min(255, Math.round((dy * 0.5 + 0.5) * 255)));

      const p = (py * sampleW + px) * 4;
      data[p] = r;
      data[p + 1] = g;
      data[p + 2] = g;
      data[p + 3] = 255;
    }
  }

  ctx.putImageData(imageData, 0, 0);
  const result = { dataUrl: canvas.toDataURL('image/png'), maxScale: maxMag };
  if (displacementCache.size > 48) {
    const firstKey = displacementCache.keys().next().value;
    if (firstKey) displacementCache.delete(firstKey);
  }
  displacementCache.set(cacheKey, result);
  return result;
}
