/*
 * Шейдер в этом файле перенесён из Liquid Glass Studio
 * (https://github.com/iyinchao/liquid-glass-studio) и распространяется на условиях
 * его лицензии:
 *
 * MIT License
 *
 * Copyright (c) 2024 Charles Yin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.mesh.foss.ui

import ru.mesh.foss.core.*

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * Настоящее преломление для «жидкого стекла» — перенос шейдера Liquid Glass Studio
 * (Charles Yin, https://github.com/iyinchao/liquid-glass-studio, лицензия MIT;
 * src/shaders/fragment-main.glsl, шаг 9, и lib/sdf.glsl, lib/color.glsl) на AGSL.
 *
 * Что считает шейдер (как в оригинале):
 *  - SDF скруглённого прямоугольника с суперэллиптическими углами ([roundness] —
 *    степень n: 2 — обычная дуга, 4–5 — «квадрат со скруглением», как у иконок iOS);
 *  - у края, в пределах толщины стекла, — угол падения и преломления по закону
 *    Снелла, смещение выборки содержимого вдоль нормали и дисперсию (R, G, B
 *    преломляются по-разному);
 *  - френель — светлая полоса у самой кромки — и блик по углу нормали (свет
 *    сверху слева, отражение снизу справа), оба в пространстве LCH.
 *
 * Отличия от оригинала: в AGSL нет dFdx, поэтому нормаль — центральные разности с
 * шагом 1px; ось Y у Android смотрит вниз, поэтому для блика нормаль
 * переворачивается, чтобы угол −45° по-прежнему означал «сверху слева»; смещение
 * задаётся в dp напрямую (в оригинале оно рассчитано на фигуры шириной ~200px);
 * добавлено увеличение [zoom] для линзы под пальцем.
 *
 * Работает на Android 13+ (RuntimeShader). Если шейдер недоступен или не
 * скомпилировался, [available] = false, и стекло рисуется прежним способом.
 */
object LiquidShader {
    internal const val SOURCE = """
uniform shader content;
uniform float2 shapeOrigin;
uniform float2 shapeSize;
uniform float radius;
uniform float roundness;
uniform float density;
uniform float zoom;
uniform float4 tint;
uniform float thickness;
uniform float refIndex;
uniform float offsetDp;
uniform float dispersion;
uniform float fresnelRange;
uniform float fresnelHardness;
uniform float fresnelFactor;
uniform float glareRange;
uniform float glareHardness;
uniform float glareFactor;
uniform float glareConvergence;
uniform float glareOpposite;
uniform float glareAngle;

const float PI = 3.14159265359;

float superellipseCorner(float2 p, float r, float n) {
  p = abs(p);
  return pow(pow(p.x, n) + pow(p.y, n), 1.0 / n) - r;
}

float shapeSdf(float2 p) {
  float2 halfSize = shapeSize * 0.5;
  float2 q = p - (shapeOrigin + halfSize);
  float cr = min(radius, min(halfSize.x, halfSize.y));
  float2 d = abs(q) - halfSize;
  if (d.x > -cr && d.y > -cr) {
    float2 cornerCenter = sign(q) * (halfSize - float2(cr));
    return superellipseCorner(q - cornerCenter, cr, roundness);
  }
  return min(max(d.x, d.y), 0.0) + length(max(d, 0.0));
}

float2 normalAt(float2 p) {
  float2 g = float2(
    shapeSdf(p + float2(1.0, 0.0)) - shapeSdf(p - float2(1.0, 0.0)),
    shapeSdf(p + float2(0.0, 1.0)) - shapeSdf(p - float2(0.0, 1.0))
  ) * 0.5;
  float l = length(g);
  return l > 0.0 ? g / l : float2(0.0);
}

float uncompand(float a) { return a > 0.04045 ? pow((a + 0.055) / 1.055, 2.4) : a / 12.92; }
float compand(float a) { return a <= 0.0031308 ? 12.92 * a : 1.055 * pow(a, 0.41666666666) - 0.055; }
float labF(float x) { return x > 0.00885645167 ? pow(x, 0.333333333) : 7.78703703704 * x + 0.13793103448; }
float labFInv(float x) { return x > 0.206897 ? x * x * x : 0.12841854934 * (x - 0.137931034); }

float3 srgbToLch(float3 c) {
  float3 rgb = float3(uncompand(c.x), uncompand(c.y), uncompand(c.z));
  float3x3 m = float3x3(
    0.4124, 0.3576, 0.1805,
    0.2126, 0.7152, 0.0722,
    0.0193, 0.1192, 0.9505
  );
  float3 white = float3(0.95045592705, 1.0, 1.08905775076);
  float3 xyz = (rgb * m) / white;
  float3 f = float3(labF(xyz.x), labF(xyz.y), labF(xyz.z));
  float3 lab = float3(116.0 * f.y - 16.0, 500.0 * (f.x - f.y), 200.0 * (f.y - f.z));
  return float3(lab.x, sqrt(dot(lab.yz, lab.yz)), atan(lab.z, lab.y) * 57.2957795131);
}

float3 lchToSrgb(float3 lch) {
  float3 lab = float3(lch.x, lch.y * cos(lch.z * 0.01745329251), lch.y * sin(lch.z * 0.01745329251));
  float w = (lab.x + 16.0) / 116.0;
  float3 white = float3(0.95045592705, 1.0, 1.08905775076);
  float3 xyz = white * float3(labFInv(w + lab.y / 500.0), labFInv(w), labFInv(w - lab.z / 200.0));
  float3x3 m = float3x3(
     3.2406255, -1.537208, -0.4986286,
    -0.9689307,  1.8757561, 0.0415175,
     0.0557101, -0.2040211, 1.0569959
  );
  float3 rgb = xyz * m;
  return clamp(float3(compand(rgb.x), compand(rgb.y), compand(rgb.z)), 0.0, 1.0);
}

float geoFactor(float sdfDp, float range, float hardness) {
  return clamp(pow(max(1.0 + sdfDp / 1500.0 * pow(500.0 / range, 2.0) + hardness, 0.0), 5.0), 0.0, 1.0);
}

half4 main(float2 p) {
  float sd = shapeSdf(p);
  half4 bg = content.eval(p);
  if (sd > 1.0) return bg;
  float depth = -sd / density;
  float2 center = shapeOrigin + shapeSize * 0.5;
  float2 base = center + (p - center) / zoom;

  float edge = 0.0;
  if (depth < thickness) {
    float x = 1.0 - max(depth, 0.0) / thickness;
    float thetaI = asin(clamp(x * x, -1.0, 1.0));
    float thetaT = asin(clamp(sin(thetaI) / refIndex, -1.0, 1.0));
    edge = -tan(thetaT - thetaI);
  }

  half4 color;
  float2 n = float2(0.0);
  if (edge <= 0.0) {
    color = content.eval(base);
  } else {
    n = normalAt(p);
    float2 off = -n * edge * offsetDp * density;
    half4 sr = content.eval(base + off * (1.0 + 0.02 * dispersion));
    half4 sg = content.eval(base + off);
    half4 sb = content.eval(base + off * (1.0 - 0.02 * dispersion));
    color = half4(sr.r, sg.g, sb.b, max(sg.a, max(sr.a, sb.a)));
  }

  color = mix(color, half4(half3(tint.rgb), 1.0), half(tint.a));

  if (edge > 0.0) {
    float sdfDp = -depth;
    float fres = geoFactor(sdfDp, fresnelRange, fresnelHardness);
    float3 fl = srgbToLch(mix(float3(1.0), tint.rgb, tint.a * 0.5));
    fl.x = clamp(fl.x + 20.0 * fres * fresnelFactor, 0.0, 100.0);
    color = mix(color, half4(half3(lchToSrgb(fl)), 1.0), half(fres * fresnelFactor * 0.7));

    float glareGeo = geoFactor(sdfDp, glareRange, glareHardness);
    float2 ng = float2(n.x, -n.y);
    float ang = atan(ng.y, ng.x);
    if (ang < 0.0) ang += 2.0 * PI;
    float ga = (ang - PI / 4.0 + glareAngle) * 2.0;
    bool far = (ga > PI * 1.5 && ga < PI * 3.5) || ga < -PI * 0.5;
    float gf = (0.5 + sin(ga) * 0.5) * (far ? 1.2 * glareOpposite : 1.2) * glareFactor;
    gf = clamp(pow(max(gf, 0.0), 0.1 + glareConvergence * 2.0), 0.0, 1.0);
    float3 under = color.a > 0.0 ? float3(color.rgb) / float(color.a) : float3(0.0);
    float3 gl = srgbToLch(mix(under, tint.rgb, tint.a * 0.5));
    gl.x = clamp(gl.x + 150.0 * gf * glareGeo, 0.0, 120.0);
    gl.y += 30.0 * gf * glareGeo;
    color = mix(color, half4(half3(lchToSrgb(gl)), 1.0), half(gf * glareGeo));
  }

  return mix(color, bg, half(smoothstep(-0.5, 0.5, sd)));
}
"""

    /** Параметры стекла. Значения по умолчанию — пресет Liquid Glass Studio. */
    data class Params(
        val roundness: Float = 2f,
        val thickness: Float = 16f,
        val refIndex: Float = 1.4f,
        val offsetDp: Float = 14f,
        val dispersion: Float = 7f,
        val fresnelRange: Float = 30f,
        val fresnelHardness: Float = 0.2f,
        val fresnelFactor: Float = 0.2f,
        val glareRange: Float = 30f,
        val glareHardness: Float = 0.2f,
        val glareFactor: Float = 0.9f,
        val glareConvergence: Float = 0.5f,
        val glareOpposite: Float = 0.8f,
        val glareAngleDeg: Float = -45f
    )

    private var shader: Any? = null

    /** Исходник, который скомпилировался на этом устройстве (современный или старый синтаксис). */
    private var activeSource: String = SOURCE

    /**
     * Только для превью в тестах: Robolectric не передаёт значения uniform в шейдер,
     * поэтому в этом режиме значения вшиваются в исходник константами (по шейдеру
     * на набор значений). В приложении выключено.
     */
    internal var bakeUniforms = false
    private val baked = HashMap<String, Any>()

    /**
     * Тот же шейдер со старым синтаксисом выборки `sample(content, p)` — для сборок
     * Skia, которые ещё не знают `content.eval(p)`.
     */
    private val LEGACY_SOURCE: String by lazy { SOURCE.replace("content.eval(", "sample(content, ") }

    /** Есть ли настоящее преломление на этом устройстве. */
    val available: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (
            runCatching { shader = RuntimeShader(SOURCE); activeSource = SOURCE }.isSuccess ||
                runCatching { shader = RuntimeShader(LEGACY_SOURCE); activeSource = LEGACY_SOURCE }.isSuccess
            )
    }

    /**
     * Можно ли применить шейдер к слою как RenderEffect (нужно линзе). Проверяется
     * один раз: если эффект не создаётся, линза рисуется прежним способом, а не
     * роняет приложение.
     */
    val effectAvailable: Boolean by lazy {
        available && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            runCatching { RenderEffect.createRuntimeShaderEffect(shader as RuntimeShader, "content") }.isSuccess
    }

    /**
     * Заполняет шейдер параметрами стекла формы ([origin], [size], радиус [radius]
     * в px). [tint] — заливка стекла (альфа — её доля), [zoom] — увеличение внутри
     * фигуры. Шейдер один на всё приложение: эффект и кисть снимают копию значений.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun configure(
        origin: Offset,
        size: Size,
        radius: Float,
        density: Float,
        tint: Color,
        params: Params,
        zoom: Float
    ): RuntimeShader {
        if (!bakeUniforms) {
            // Обычный путь: значения прямо в общий шейдер, без промежуточных массивов —
            // панели перерисовываются при каждой прокрутке.
            val s = shader as RuntimeShader
            s.setFloatUniform("shapeOrigin", origin.x, origin.y)
            s.setFloatUniform("shapeSize", size.width, size.height)
            s.setFloatUniform("radius", radius)
            s.setFloatUniform("roundness", params.roundness)
            s.setFloatUniform("density", density)
            s.setFloatUniform("zoom", zoom)
            s.setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
            s.setFloatUniform("thickness", params.thickness)
            s.setFloatUniform("refIndex", params.refIndex)
            s.setFloatUniform("offsetDp", params.offsetDp)
            s.setFloatUniform("dispersion", params.dispersion)
            s.setFloatUniform("fresnelRange", params.fresnelRange)
            s.setFloatUniform("fresnelHardness", params.fresnelHardness)
            s.setFloatUniform("fresnelFactor", params.fresnelFactor)
            s.setFloatUniform("glareRange", params.glareRange)
            s.setFloatUniform("glareHardness", params.glareHardness)
            s.setFloatUniform("glareFactor", params.glareFactor)
            s.setFloatUniform("glareConvergence", params.glareConvergence)
            s.setFloatUniform("glareOpposite", params.glareOpposite)
            s.setFloatUniform("glareAngle", Math.toRadians(params.glareAngleDeg.toDouble()).toFloat())
            return s
        }
        val values = linkedMapOf(
            "shapeOrigin" to floatArrayOf(origin.x, origin.y),
            "shapeSize" to floatArrayOf(size.width, size.height),
            "radius" to floatArrayOf(radius),
            "roundness" to floatArrayOf(params.roundness),
            "density" to floatArrayOf(density),
            "zoom" to floatArrayOf(zoom),
            "tint" to floatArrayOf(tint.red, tint.green, tint.blue, tint.alpha),
            "thickness" to floatArrayOf(params.thickness),
            "refIndex" to floatArrayOf(params.refIndex),
            "offsetDp" to floatArrayOf(params.offsetDp),
            "dispersion" to floatArrayOf(params.dispersion),
            "fresnelRange" to floatArrayOf(params.fresnelRange),
            "fresnelHardness" to floatArrayOf(params.fresnelHardness),
            "fresnelFactor" to floatArrayOf(params.fresnelFactor),
            "glareRange" to floatArrayOf(params.glareRange),
            "glareHardness" to floatArrayOf(params.glareHardness),
            "glareFactor" to floatArrayOf(params.glareFactor),
            "glareConvergence" to floatArrayOf(params.glareConvergence),
            "glareOpposite" to floatArrayOf(params.glareOpposite),
            "glareAngle" to floatArrayOf(Math.toRadians(params.glareAngleDeg.toDouble()).toFloat())
        )
        var src = activeSource
        values.forEach { (name, v) ->
            val type = if (v.size == 1) "float" else "float${v.size}"
            val literal = v.joinToString(", ") { String.format(java.util.Locale.US, "%.6f", it) }
            src = src.replace("uniform $type $name;", "const $type $name = $type($literal);")
        }
        return baked.getOrPut(src) { RuntimeShader(src) } as RuntimeShader
    }

    /**
     * Кисть стекла для панели: [content] — то, что под стеклом (фон в виде
     * BitmapShader, уже сдвинутый в координаты панели). Кисть снимает значения при
     * отрисовке, поэтому один шейдер служит всем панелям по очереди.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun brush(
        content: android.graphics.Shader,
        origin: Offset,
        size: Size,
        radius: Float,
        density: Float,
        tint: Color,
        params: Params,
        zoom: Float = 1f
    ): androidx.compose.ui.graphics.ShaderBrush {
        val s = configure(origin, size, radius, density, tint, params, zoom)
        s.setInputShader("content", content)
        return androidx.compose.ui.graphics.ShaderBrush(s)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun effect(
        origin: Offset,
        size: Size,
        radius: Float,
        density: Float,
        tint: Color,
        params: Params,
        zoom: Float = 1f
    ): androidx.compose.ui.graphics.RenderEffect {
        val s = configure(origin, size, radius, density, tint, params, zoom)
        return RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
    }
}
