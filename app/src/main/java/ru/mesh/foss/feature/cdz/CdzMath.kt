package ru.mesh.foss.feature.cdz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Мини-представление LaTeX из `content/math` и HTML-редактора.
 *
 * Полноценный TeX здесь не нужен: API присылает короткие формулы. Важно
 * сохранить вложенность (`\frac`, `\sqrt`, `\text`, индексы), чтобы
 * отрисовать их нативно, а не показывать сырые команды.
 */
sealed interface CdzMathNode {
    data class Text(
        val value: String,
        val style: CdzMathStyle = CdzMathStyle(),
    ) : CdzMathNode

    data class Sequence(val children: List<CdzMathNode>) : CdzMathNode

    data class Script(
        val base: CdzMathNode?,
        val script: CdzMathNode,
        val superscript: Boolean,
    ) : CdzMathNode

    data class Fraction(
        val numerator: CdzMathNode,
        val denominator: CdzMathNode,
    ) : CdzMathNode

    data class Radical(
        val body: CdzMathNode,
        val index: CdzMathNode? = null,
    ) : CdzMathNode

    data class Space(val widthEm: Float = 0.32f) : CdzMathNode

    data class Overline(val body: CdzMathNode) : CdzMathNode

    data class Underline(val body: CdzMathNode) : CdzMathNode
}

data class CdzMathStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val underline: Boolean = false,
)

/** Inline-элемент внутри формулы: дробь или корень. */
data class CdzMathInline(
    val position: Int,
    val node: CdzMathNode,
    val alternateText: String,
)

/**
 * Формула, разложенная на обычный текст со стилями и inline-элементы.
 *
 * Индексы/степени остаются текстом с `BaselineShift`, поэтому кириллица
 * (`p_{мат}`, `v_{М0}`) не превращается в «плоские» символы.
 */
data class CdzMathRender(
    val text: String,
    val spans: List<CdzTextSpan>,
    val inlines: List<CdzMathInline>,
)

fun parseMathNodes(raw: String?): CdzMathNode {
    val source = raw?.trim().orEmpty()
    if (source.isEmpty()) return CdzMathNode.Sequence(emptyList())
    return CdzMathNode.Sequence(MathParser(source).parse())
}

/** Формула для inline-вставки: корни и дроби становятся Compose-контентом. */
fun parseMath(raw: String?): CdzMathRender =
    renderMath(parseMathNodes(raw), inlineAsText = false)

/** Текстовая версия формулы для вариантов ответа и accessibility. */
fun parseMathText(raw: String?): String =
    renderMath(parseMathNodes(raw), inlineAsText = true).text

private fun renderMath(root: CdzMathNode, inlineAsText: Boolean): CdzMathRender {
    val text = StringBuilder()
    val spans = mutableListOf<CdzTextSpan>()
    val inlines = mutableListOf<CdzMathInline>()

    fun appendText(value: String, style: CdzMathStyle, inherited: Set<String>) {
        if (value.isEmpty()) return
        val start = text.length
        text.append(value)
        val tokens = buildSet {
            addAll(inherited)
            if (style.bold) add("BOLD")
            if (style.italic) add("ITALIC")
            if (style.code) add("CODE")
            if (style.underline) add("UNDERLINE")
        }
        if (tokens.isNotEmpty()) {
            spans += CdzTextSpan(start, text.length, tokens)
        }
    }

    fun appendNode(node: CdzMathNode, tokens: Set<String> = emptySet()) {
        when (node) {
            is CdzMathNode.Text -> appendText(node.value, node.style, tokens)
            is CdzMathNode.Sequence -> node.children.forEach { appendNode(it, tokens) }
            is CdzMathNode.Space -> {
                repeat(if (node.widthEm >= 0.8f) 2 else 1) { text.append(' ') }
            }
            is CdzMathNode.Script -> {
                val start = text.length
                appendNode(node.base ?: CdzMathNode.Text(""), tokens)
                if (text.length == start) {
                    // Пустая база не должна съедать индекс.
                    text.append('\u200B')
                }
                val simple = simpleScriptText(node.script, node.superscript)
                if (simple != null) {
                    text.append(simple)
                } else {
                    val scriptStart = text.length
                    appendNode(node.script, tokens + if (node.superscript) "SUP" else "SUB")
                    if (scriptStart == text.length) {
                        text.append('\u200B')
                    }
                }
            }
            is CdzMathNode.Fraction -> {
                val alternate = "${plainText(node.numerator)}/${plainText(node.denominator)}"
                if (inlineAsText) {
                    text.append(alternate)
                } else {
                    inlines += CdzMathInline(text.length, node, alternate)
                }
            }
            is CdzMathNode.Radical -> {
                val alternate = "√(${plainText(node.body)})"
                if (inlineAsText) {
                    text.append(alternate)
                } else {
                    inlines += CdzMathInline(text.length, node, alternate)
                }
            }
            // Compose TextDecoration не умеет overline; не теряем содержимое
            // формулы и не показываем сырые команды.
            is CdzMathNode.Overline -> appendNode(node.body, tokens)
            is CdzMathNode.Underline -> appendNode(node.body, tokens + "UNDERLINE")
        }
    }

    appendNode(root)
    return CdzMathRender(
        text = text.toString().replace('\u200B', ' '),
        spans = spans,
        inlines = inlines,
    )
}

private fun simpleScriptText(node: CdzMathNode, superscript: Boolean): String? {
    val text = when (node) {
        is CdzMathNode.Text -> node.value
        is CdzMathNode.Sequence -> node.children.singleOrNull()
            ?.let { child -> (child as? CdzMathNode.Text)?.value }
        else -> null
    } ?: return null
    if (text.length != 1) return null
    return (if (superscript) mathSuperscript else mathSubscript)[text[0]]?.toString()
}

private fun plainText(node: CdzMathNode): String {
    val rendered = renderMath(node, inlineAsText = true)
    return rendered.text.ifBlank { "…" }
}

fun mathAlternateText(node: CdzMathNode): String = plainText(node)

/** Собирает AnnotatedString для текстовых частей формулы. */
fun buildMathAnnotatedString(node: CdzMathNode): AnnotatedString {
    val rendered = renderMath(node, inlineAsText = true)
    return buildAnnotatedString {
        append(rendered.text)
        rendered.spans.forEach { span ->
            addStyle(mathSpanStyle(span.tokens), span.start, span.end)
        }
    }
}

private fun mathSpanStyle(tokens: Set<String>): SpanStyle {
    var style = SpanStyle()
    if ("BOLD" in tokens) style = style.copy(fontWeight = FontWeight.Bold)
    if ("ITALIC" in tokens) style = style.copy(fontStyle = FontStyle.Italic)
    if ("CODE" in tokens) style = style.copy(fontFamily = FontFamily.Monospace)
    val decorations = buildList {
        if ("UNDERLINE" in tokens) add(TextDecoration.Underline)
    }
    if (decorations.isNotEmpty()) {
        style = style.copy(textDecoration = TextDecoration.combine(decorations))
    }
    if ("SUB" in tokens) {
        style = style.copy(
            baselineShift = BaselineShift.Subscript,
            fontSize = 0.78.em,
        )
    }
    if ("SUP" in tokens) {
        style = style.copy(
            baselineShift = BaselineShift.Superscript,
            fontSize = 0.78.em,
        )
    }
    return style
}

/**
 * Рисует формулу внутри текста: обычные символы — `Text`, дробь/корень —
 * компактный Column/Row с реальной чертой или знаком корня.
 */
@Composable
fun CdzMathInline(
    node: CdzMathNode,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    when (node) {
        is CdzMathNode.Fraction -> {
            val density = LocalDensity.current
            val line = with(density) { 1.dp }
            Column(
                modifier = modifier.width(IntrinsicSize.Max),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = buildMathAnnotatedString(node.numerator),
                    style = style,
                    color = color,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(line)
                        .drawBehind { drawLine(color, Offset.Zero, Offset(size.width, 0f), line.toPx()) },
                )
                Text(
                    text = buildMathAnnotatedString(node.denominator),
                    style = style,
                    color = color,
                )
            }
        }
        is CdzMathNode.Radical -> {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "√",
                    style = style,
                    color = color,
                )
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .padding(top = 1.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .drawBehind {
                                drawLine(
                                    color,
                                    Offset.Zero,
                                    Offset(size.width, 0f),
                                    1.dp.toPx(),
                                )
                            },
                    )
                    CdzMathInline(
                        node = node.body,
                        style = style,
                        color = color,
                    )
                }
            }
        }
        is CdzMathNode.Sequence -> {
            val groups = mutableListOf<CdzMathNode>()
            val pending = mutableListOf<CdzMathNode>()
            node.children.forEach { child ->
                if (child is CdzMathNode.Fraction || child is CdzMathNode.Radical) {
                    if (pending.isNotEmpty()) {
                        groups += CdzMathNode.Sequence(pending.toList())
                        pending.clear()
                    }
                    groups += child
                } else {
                    pending += child
                }
            }
            if (pending.isNotEmpty()) {
                groups += CdzMathNode.Sequence(pending.toList())
            }
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.Bottom,
            ) {
                groups.forEach { group ->
                    if (group is CdzMathNode.Fraction || group is CdzMathNode.Radical) {
                        CdzMathInline(group, style = style, color = color)
                    } else {
                        Text(
                            text = buildMathAnnotatedString(group),
                            style = style,
                            color = color,
                        )
                    }
                }
            }
        }
        else -> Text(
            text = buildMathAnnotatedString(node),
            style = style,
            color = color,
            modifier = modifier,
        )
    }
}

/** Высота inline-контента: дроби и корни требуют больше места. */
fun mathPlaceholderHeightEm(node: CdzMathNode): Float = when (node) {
    is CdzMathNode.Fraction -> 2.3f
    is CdzMathNode.Radical -> 2.1f
    is CdzMathNode.Sequence -> node.children.maxOfOrNull(::mathPlaceholderHeightEm) ?: 1.5f
    is CdzMathNode.Script -> 1.7f
    else -> 1.5f
}

/** Ширина inline-контента в `em`, чтобы placeholder не схлопывался. */
fun mathPlaceholderWidthEm(node: CdzMathNode): Float = when (node) {
    is CdzMathNode.Fraction -> 2.6f
    is CdzMathNode.Radical -> 2.4f
    is CdzMathNode.Sequence -> node.children.sumOf { mathPlaceholderWidthEm(it).toDouble() }
        .toFloat().coerceIn(1.2f, 12f)
    else -> (mathAlternateText(node).length * 0.58f + 0.6f).coerceIn(1.2f, 12f)
}

private class MathParser(private val source: String) {
    private var index = 0

    fun parse(style: CdzMathStyle = CdzMathStyle()): List<CdzMathNode> {
        val out = mutableListOf<CdzMathNode>()
        while (index < source.length) {
            when (val char = source[index]) {
                '^', '_' -> {
                    index++
                    val script = parseScript(style)
                    val base = out.removeLastOrNull()
                    out += CdzMathNode.Script(base, script, char == '^')
                }
                '{' -> {
                    index++
                    out += CdzMathNode.Sequence(parse(style))
                }
                '}' -> {
                    index++
                    break
                }
                '\\' -> parseCommand(style)?.let(out::add)
                '&' -> {
                    index++
                    out += CdzMathNode.Space(0.2f)
                }
                '~' -> {
                    index++
                    out += CdzMathNode.Space(0.5f)
                }
                else -> {
                    index++
                    out += CdzMathNode.Text(char.toString(), style)
                }
            }
        }
        return out
    }

    private fun parseScript(style: CdzMathStyle): CdzMathNode {
        if (index >= source.length) return CdzMathNode.Text("")
        return when (source[index]) {
            '{' -> {
                index++
                CdzMathNode.Sequence(parse(style))
            }
            '\\' -> parseCommand(style) ?: CdzMathNode.Text("")
            else -> {
                val char = source[index]
                index++
                CdzMathNode.Text(char.toString(), style)
            }
        }
    }

    private fun parseCommand(style: CdzMathStyle): CdzMathNode? {
        index++ // backslash
        if (index >= source.length) return CdzMathNode.Text("\\")
        val start = index
        if (!source[index].isLetter()) {
            val char = source[index]
            index++
            return when (char) {
                '\\' -> CdzMathNode.Text("\n")
                ' ', ',', ';', ':', '!' -> CdzMathNode.Space(if (char == '!') 0f else 0.32f)
                else -> CdzMathNode.Text(char.toString(), style)
            }
        }
        while (index < source.length && source[index].isLetter()) index++
        val command = source.substring(start, index)
        return when (command) {
            "frac", "dfrac", "tfrac" -> {
                val numerator = CdzMathNode.Sequence(parseGroup(style))
                val denominator = CdzMathNode.Sequence(parseGroup(style))
                CdzMathNode.Fraction(numerator, denominator)
            }
            "sqrt" -> {
                val rootIndex = parseOptionalGroup(style)
                val body = CdzMathNode.Sequence(parseGroup(style))
                CdzMathNode.Radical(body, rootIndex)
            }
            "text", "textrm", "textnormal", "mbox" -> {
                CdzMathNode.Text(cleanTextGroup(readRawGroup()), style.copy(italic = false))
            }
            "operatorname" -> {
                CdzMathNode.Text(cleanTextGroup(readRawGroup()), style.copy(italic = false))
            }
            "mathrm" -> CdzMathNode.Sequence(parseGroup(style.copy(italic = false)))
            "mathbf", "textbf", "bm" -> CdzMathNode.Sequence(parseGroup(style.copy(bold = true)))
            "mathit", "textit", "emph" -> CdzMathNode.Sequence(parseGroup(style.copy(italic = true)))
            "mathtt", "texttt" -> CdzMathNode.Sequence(parseGroup(style.copy(code = true)))
            "underline" -> CdzMathNode.Underline(CdzMathNode.Sequence(parseGroup(style)))
            "overline" -> CdzMathNode.Overline(CdzMathNode.Sequence(parseGroup(style)))
            "bar" -> CdzMathNode.Overline(CdzMathNode.Sequence(parseGroup(style)))
            "vec" -> CdzMathNode.Overline(CdzMathNode.Sequence(parseGroup(style)))
            "hat" -> CdzMathNode.Overline(CdzMathNode.Sequence(parseGroup(style)))
            "dot" -> CdzMathNode.Overline(CdzMathNode.Sequence(parseGroup(style)))
            "ce", "pu" -> CdzMathNode.Sequence(parseGroup(style))
            "left", "right", "big", "Big", "bigg", "Bigg", "bigl", "bigr" -> {
                skipDelimiter()
                null
            }
            "displaystyle", "textstyle", "scriptstyle", "scriptscriptstyle",
            "limits", "nolimits", "mathstrut", "strut" -> null
            "quad" -> CdzMathNode.Space(1.0f)
            "qquad" -> CdzMathNode.Space(2.0f)
            "space" -> CdzMathNode.Space(0.6f)
            "newline", "cr" -> CdzMathNode.Text("\n")
            "begin", "end" -> {
                readRawGroup()
                null
            }
            "label", "tag", "color" -> {
                readRawGroup()
                null
            }
            "boxed" -> CdzMathNode.Sequence(parseGroup(style))
            "phantom", "hphantom", "vphantom" -> {
                readRawGroup()
                null
            }
            else -> mathSymbols[command]?.let { CdzMathNode.Text(it, style) }
                ?: CdzMathNode.Text(command, style)
        }
    }

    private fun parseGroup(style: CdzMathStyle): List<CdzMathNode> {
        if (index >= source.length) return emptyList()
        if (source[index] != '{') {
            return listOf(parseScript(style))
        }
        index++
        val nodes = parse(style)
        return nodes
    }

    private fun parseOptionalGroup(style: CdzMathStyle): CdzMathNode? {
        if (index >= source.length || source[index] != '[') return null
        val end = source.indexOf(']', index + 1)
        if (end < 0) return null
        val body = source.substring(index + 1, end)
        index = end + 1
        return CdzMathNode.Sequence(MathParser(body).parse(style))
    }

    private fun readRawGroup(): String {
        if (index >= source.length) return ""
        if (source[index] != '{') {
            val char = source[index]
            index++
            return char.toString()
        }
        index++
        var depth = 1
        val out = StringBuilder()
        while (index < source.length && depth > 0) {
            val char = source[index]
            when (char) {
                '{' -> {
                    depth++
                    out.append(char)
                }
                '}' -> {
                    depth--
                    if (depth > 0) out.append(char)
                }
                else -> out.append(char)
            }
            index++
        }
        return out.toString()
    }

    private fun skipDelimiter() {
        if (index >= source.length) return
        when (source[index]) {
            '\\' -> parseCommand(CdzMathStyle())
            '{' -> readRawGroup()
            else -> index++
        }
    }
}

private fun cleanTextGroup(raw: String): String {
    var text = raw
    while (text.startsWith("{") && text.endsWith("}")) {
        text = text.substring(1, text.length - 1)
    }
    return text
        .replace("\\,", " ")
        .replace("\\ ", " ")
        .replace("~", " ")
        .replace(Regex("""\\([{}%$&#_])"""), "$1")
}

private val mathSubscript = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
    'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
    'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
    'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
    'v' to 'ᵥ', 'x' to 'ₓ',
)

private val mathSuperscript = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
    'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ',
    'f' to 'ᶠ', 'g' to 'ᵍ', 'h' to 'ʰ', 'i' to 'ⁱ', 'j' to 'ʲ',
    'k' to 'ᵏ', 'l' to 'ˡ', 'm' to 'ᵐ', 'n' to 'ⁿ', 'o' to 'ᵒ',
    'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ', 'u' to 'ᵘ',
    'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ',
)

private val mathSymbols = mapOf(
    "cdot" to "·", "times" to "×", "div" to "÷",
    "pm" to "±", "mp" to "∓", "approx" to "≈",
    "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥",
    "ne" to "≠", "neq" to "≠", "equiv" to "≡",
    "ll" to "≪", "gg" to "≫", "sim" to "∼", "simeq" to "≃",
    "cong" to "≅", "propto" to "∝",
    "rightarrow" to "→", "to" to "→", "Rightarrow" to "⇒",
    "leftarrow" to "←", "Leftarrow" to "⇐",
    "leftrightarrow" to "↔", "Leftrightarrow" to "⇔",
    "rightleftharpoons" to "⇌", "uparrow" to "↑", "downarrow" to "↓",
    "infty" to "∞", "angle" to "∠", "prime" to "′",
    "parallel" to "∥", "perp" to "⊥", "triangle" to "△",
    "in" to "∈", "notin" to "∉", "subset" to "⊂", "supset" to "⊃",
    "subseteq" to "⊆", "supseteq" to "⊇", "setminus" to "∖",
    "cup" to "∪", "cap" to "∩", "emptyset" to "∅", "varnothing" to "∅",
    "sum" to "∑", "prod" to "∏", "int" to "∫", "oint" to "∮",
    "ldots" to "…", "dots" to "…", "cdots" to "⋯",
    "colon" to ":", "ast" to "∗", "star" to "⋆", "bullet" to "•",
    "oplus" to "⊕", "otimes" to "⊗", "forall" to "∀", "exists" to "∃",
    "nabla" to "∇", "partial" to "∂", "ell" to "ℓ", "hbar" to "ℏ",
    "degree" to "°", "circ" to "°", "textdegree" to "°",
    "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
    "Delta" to "Δ", "epsilon" to "ε", "varepsilon" to "ε",
    "zeta" to "ζ", "eta" to "η", "theta" to "θ", "Theta" to "Θ",
    "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "Lambda" to "Λ",
    "mu" to "μ", "nu" to "ν", "xi" to "ξ", "Xi" to "Ξ",
    "pi" to "π", "Pi" to "Π", "rho" to "ρ", "sigma" to "σ",
    "Sigma" to "Σ", "tau" to "τ", "upsilon" to "υ", "phi" to "φ",
    "varphi" to "φ", "Phi" to "Φ", "chi" to "χ", "psi" to "ψ",
    "Psi" to "Ψ", "omega" to "ω", "Omega" to "Ω",
)
