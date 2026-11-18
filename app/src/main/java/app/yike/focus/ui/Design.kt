package app.yike.focus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import app.yike.focus.domain.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class FocusPalette(val paper: Color, val card: Color, val ink: Color, val muted: Color,
    val accent: Color, val accentSoft: Color, val secondary: Color, val secondarySoft: Color, val line: Color)
val LocalFocusPalette = staticCompositionLocalOf { palette(ThemeStyle.TOMATO, false) }
val Paper: Color @Composable get() = LocalFocusPalette.current.paper
val CardPaper: Color @Composable get() = LocalFocusPalette.current.card
val Ink: Color @Composable get() = LocalFocusPalette.current.ink
val Muted: Color @Composable get() = LocalFocusPalette.current.muted
val Tomato: Color @Composable get() = LocalFocusPalette.current.accent
val Peach: Color @Composable get() = LocalFocusPalette.current.accentSoft
val Sage: Color @Composable get() = LocalFocusPalette.current.secondary
val SageLight: Color @Composable get() = LocalFocusPalette.current.secondarySoft
val Line: Color @Composable get() = LocalFocusPalette.current.line
val LocalAppearance = staticCompositionLocalOf { FocusSettings() }
val LocalContentHeight = staticCompositionLocalOf { 800.dp }
val LayoutGap @Composable get() = if (LocalAppearance.current.uiDensity == UiDensity.COMPACT) 16.dp else 24.dp
val PanelPadding @Composable get() = if (LocalAppearance.current.uiDensity == UiDensity.COMPACT) 16.dp else 20.dp
val ButtonShape = RoundedCornerShape(12.dp)
val SmallShape = RoundedCornerShape(8.dp)
val DialogShape = RoundedCornerShape(24.dp)
val PanelShape @Composable get() = RoundedCornerShape(when (LocalAppearance.current.uiPreset) {
    UiPreset.CLASSIC -> 26.dp; UiPreset.MINIMAL -> 20.dp; UiPreset.FLIP -> 16.dp; UiPreset.JOURNAL -> 12.dp
})

fun palette(style: ThemeStyle, dark: Boolean): FocusPalette {
    val accent = when (style) {
        ThemeStyle.TOMATO -> if (dark) 0xFFEDA18C else 0xFFB04834
        ThemeStyle.MATCHA -> if (dark) 0xFFADCBA0 else 0xFF4E7044
        ThemeStyle.BLUE -> if (dark) 0xFF9DC5E6 else 0xFF376D94
        ThemeStyle.LILAC -> if (dark) 0xFFCBB0E5 else 0xFF8256A1
    }
    val primary = Color(accent)
    val background = if (dark) Color(0xFF1B201E) else Color(0xFFF8F5EF)
    val card = if (dark) Color(0xFF252B28) else Color(0xFFFFFDFA)
    return FocusPalette(background, card, if (dark) Color(0xFFECEDE5) else Color(0xFF343B34),
        if (dark) Color(0xFFB4BDB1) else Color(0xFF656D60), primary,
        androidx.compose.ui.graphics.lerp(card, primary, if (dark) 0.17f else 0.12f),
        if (dark) Color(0xFFB5CBA6) else Color(0xFF566E48),
        if (dark) Color(0xFF303B30) else Color(0xFFEBEFE4),
        if (dark) Color(0xFF424B43) else Color(0xFFE8E3D9))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusTheme(settings: FocusSettings = FocusSettings(), content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    val basePalette = palette(settings.themeStyle, dark)
    val p = if (settings.interfaceMaterial == InterfaceMaterial.GLASS) basePalette.copy(
        ink = if (dark) Color(0xFFF9FBF6) else Color(0xFF202A23),
        muted = if (dark) Color(0xFFDFE7DB) else Color(0xFF303D30)) else basePalette
    val base = if (dark) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalFocusPalette provides p, LocalAppearance provides settings,
        LocalContentColor provides p.ink,
        LocalSystemHighContrast provides observeSystemHighContrast(),
        androidx.compose.foundation.LocalIndication provides remember(p.accent) { FocusIndication(p.accent) },
        LocalRippleConfiguration provides null) {
    MaterialTheme(
        colorScheme = base.copy(
            primary = p.accent, onPrimary = if (dark) Color(0xFF20251E) else Color.White,
            primaryContainer = p.accentSoft, onPrimaryContainer = p.ink,
            secondary = p.secondary, onSecondary = if (dark) Color(0xFF20251E) else Color.White,
            secondaryContainer = p.secondarySoft, onSecondaryContainer = p.ink,
            background = p.paper, onBackground = p.ink, surface = p.card, onSurface = p.ink,
            surfaceVariant = p.secondarySoft, onSurfaceVariant = p.muted, outline = p.line,
            surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerHighest = p.accentSoft,
        ),
        shapes = Shapes(extraSmall = SmallShape, small = SmallShape, medium = ButtonShape,
            large = RoundedCornerShape(20.dp), extraLarge = DialogShape),
        typography = Typography(
            displayLarge = focusText(28, 34, FontWeight.SemiBold),
            displayMedium = focusText(28, 34, FontWeight.SemiBold),
            displaySmall = focusText(28, 34, FontWeight.SemiBold),
            headlineLarge = focusText(28, 34, FontWeight.SemiBold),
            headlineMedium = focusText(28, 34, FontWeight.SemiBold),
            headlineSmall = focusText(20, 26, FontWeight.SemiBold),
            titleLarge = focusText(20, 26, FontWeight.SemiBold),
            titleMedium = focusText(17, 24, FontWeight.Medium),
            titleSmall = focusText(15, 22, FontWeight.Medium),
            bodyLarge = focusText(17, 24),
            bodyMedium = focusText(15, 22),
            bodySmall = focusText(13, 18),
            labelLarge = focusText(17, 24, FontWeight.Medium),
            labelMedium = focusText(15, 22, FontWeight.Medium),
            labelSmall = focusText(13, 18, FontWeight.Medium),
        ),
        content = {
            CompositionLocalProvider(androidx.compose.foundation.LocalIndication provides remember(p.accent) { FocusIndication(p.accent) }) { content() }
        },
    )
    }
}

private fun focusText(size: Int, leading: Int, weight: FontWeight = FontWeight.Normal) =
    androidx.compose.ui.text.TextStyle(fontSize = size.sp, lineHeight = leading.sp,
        fontWeight = weight, fontFamily = FontFamily.Default, letterSpacing = 0.sp)

@Composable
fun PaperCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val preset = LocalAppearance.current.uiPreset
    val line = Line
    val accent = Tomato
    val shape = PanelShape
    val decoration = when (preset) {
        UiPreset.MINIMAL -> Modifier.background(CardPaper.copy(alpha = 0.94f), shape).border(0.8.dp, line, shape)
        UiPreset.JOURNAL -> Modifier.background(CardPaper, shape).border(1.dp, line, shape).drawBehind {
            drawLine(accent.copy(alpha = 0.3f), Offset(10.dp.toPx(), 10.dp.toPx()), Offset(10.dp.toPx(), size.height - 10.dp.toPx()), 2.dp.toPx())
        }
        else -> Modifier.background(CardPaper, shape).border(1.dp, line, shape)
    }
    val glass = LocalAppearance.current.interfaceMaterial == InterfaceMaterial.GLASS
    Column(modifier.then(if (glass) Modifier.glassSurface(shape) else decoration).padding(PanelPadding)) {
        CompositionLocalProvider(LocalInsideGlass provides glass) { content() }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    WallpaperContent {
    Column(Modifier.testTag("section_heading")) {
    Text(title, color = Ink, style = MaterialTheme.typography.headlineLarge)
    if (subtitle != null) {
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
    }
    }
}

@Composable
fun Glyph(name: String, modifier: Modifier = Modifier, color: Color = Ink) {
    val card = CardPaper
    val sage = Sage
    Canvas(modifier.size(24.dp)) {
        val u = size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), 1.8f * u, StrokeCap.Round)
        when (name) {
            "play" -> drawPath(Path().apply { moveTo(8*u, 5*u); lineTo(19*u, 12*u); lineTo(8*u, 19*u); close() }, color)
            "pause" -> { drawRoundRect(color, p(7f,5f), Size(3*u,14*u)); drawRoundRect(color, p(14f,5f), Size(3*u,14*u)) }
            "add" -> { line(12f,5f,12f,19f); line(5f,12f,19f,12f) }
            "close" -> { line(6f,6f,18f,18f); line(18f,6f,6f,18f) }
            "check" -> { line(5f,12f,10f,17f); line(10f,17f,19f,7f) }
            "stats" -> { line(5f,19f,5f,12f); line(12f,19f,12f,5f); line(19f,19f,19f,9f) }
            "settings" -> {
                line(4f,6f,20f,6f); line(4f,12f,20f,12f); line(4f,18f,20f,18f)
                listOf(p(9f,6f),p(16f,12f),p(8f,18f)).forEach { drawCircle(card,3*u,it); drawCircle(color,2.6f*u,it,style=Stroke(1.7f*u)) }
            }
            "more" -> listOf(5f,12f,19f).forEach { drawCircle(color,1.5f*u,p(it,12f)) }
            "back" -> { line(14f,5f,7f,12f); line(7f,12f,14f,19f) }
            "next" -> { line(10f,5f,17f,12f); line(17f,12f,10f,19f) }
            "down" -> { line(5f,9f,12f,16f); line(12f,16f,19f,9f) }
            "up" -> { line(5f,15f,12f,8f); line(12f,8f,19f,15f) }
            "fullscreen" -> {
                line(9f,4f,4f,4f); line(4f,4f,4f,9f); line(15f,4f,20f,4f); line(20f,4f,20f,9f)
                line(4f,15f,4f,20f); line(4f,20f,9f,20f); line(15f,20f,20f,20f); line(20f,20f,20f,15f)
            }
            "copy" -> { drawRoundRect(color,p(8f,7f),Size(11*u,14*u),style=Stroke(1.7f*u)); line(5f,16f,5f,3f); line(5f,3f,15f,3f) }
            "leaf" -> {
                drawPath(Path().apply { moveTo(5*u,19*u); cubicTo(1*u,8*u,10*u,3*u,20*u,4*u); cubicTo(21*u,15*u,15*u,21*u,5*u,19*u) },color,style=Stroke(1.8f*u))
                line(5f,19f,15f,9f)
            }
            "tomato" -> {
                drawOval(color,p(3f,7f),Size(18*u,15*u))
                drawPath(Path().apply { moveTo(12*u,10*u); lineTo(7*u,5*u); lineTo(11*u,6*u); lineTo(12*u,2*u); lineTo(13*u,6*u); lineTo(18*u,5*u); close() },sage)
            }
            else -> {
                drawCircle(color,8.5f*u,p(12f,12f),style=Stroke(1.8f*u))
                line(12f,7f,12f,12f); line(12f,12f,16f,14f)
            }
        }
    }
}

fun clockText(millis: Long): String {
    val seconds = ((millis + 999) / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
fun durationText(millis: Long): String {
    val seconds = millis / 1000
    return if (seconds < 60) "${seconds}秒" else if (seconds % 60 == 0L) "${seconds / 60}分钟" else "${seconds / 60}分${seconds % 60}秒"
}
