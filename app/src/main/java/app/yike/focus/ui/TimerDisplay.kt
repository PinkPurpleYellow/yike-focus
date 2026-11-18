package app.yike.focus.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.yike.focus.domain.*
import kotlin.math.cos
import kotlin.math.sin

/** Presentation only: the controller supplies time; this component cannot advance or settle a session. */
@Composable
fun TimerDisplay(remaining: Long, duration: Long, phase: Phase, status: RunStatus,
    settings: FocusSettings = LocalAppearance.current, modifier: Modifier = Modifier,
    large: Boolean = false, heightLimit: Dp = if (large) 420.dp else 260.dp, preview: Boolean = false) {
    val colors = LocalFocusPalette.current
    val visualPalette = LocalFocusBackdrop.current?.palette ?: colors
    val accent = if (phase == Phase.FOCUS) visualPalette.accent else visualPalette.secondary
    val text = clockText(remaining)
    val seconds = ((remaining + 999) / 1000).coerceAtLeast(0)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val motion = !preview && settings.decorativeMotion && status == RunStatus.RUNNING &&
        lifecycle.isAtLeast(Lifecycle.State.RESUMED) && ValueAnimator.areAnimatorsEnabled()
    BoxWithConstraints(modifier.fillMaxWidth().testTag("countdown").clearAndSetSemantics {
        contentDescription = "${phase.title}剩余 ${seconds / 60} 分 ${seconds % 60} 秒" +
            if (status == RunStatus.PAUSED) "，已暂停" else ""
    }, contentAlignment = Alignment.Center) {
        when (settings.resolvedTimerFace()) {
            TimerFace.RING -> {
                val diameter = minOf(maxWidth, heightLimit, if (large) 500.dp else 260.dp)
                val font = fittedClockSize(text, diameter * 0.72f, diameter * 0.34f, if (large) 120 else 72)
                Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val r = size.minDimension / 2
                        repeat(60) { i ->
                            val angle = (i * 6 - 90) * Math.PI / 180
                            val inner = r - if (i % 5 == 0) 7.dp.toPx() else 4.dp.toPx()
                            drawLine(if (i % 5 == 0) colors.muted.copy(alpha = 0.5f) else colors.line,
                                Offset(center.x + (inner * cos(angle)).toFloat(), center.y + (inner * sin(angle)).toFloat()),
                                Offset(center.x + (r * cos(angle)).toFloat(), center.y + (r * sin(angle)).toFloat()),
                                1.dp.toPx(), StrokeCap.Round)
                        }
                        val inset = 16.dp.toPx()
                        val bounds = Size(size.width - inset * 2, size.height - inset * 2)
                        val stroke = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(colors.line, -90f, 360f, false, Offset(inset, inset), bounds, style = stroke)
                        drawArc(accent, -90f, (remaining.toFloat() / duration.coerceAtLeast(1) * 359.9f).coerceIn(0f, 359.9f),
                            false, Offset(inset, inset), bounds, style = stroke)
                    }
                    Text(text, color = Ink, style = clockStyle(font), maxLines = 1, softWrap = false)
                }
            }
            TimerFace.FLIP -> SplitFlipClock(seconds, phase, motion, heightLimit, large, accent, maxOf(2, (duration / 60_000).toString().length))
            else -> {
                val width = minOf(maxWidth, if (large) 1100.dp else 680.dp)
                val font = fittedClockSize(text, width, heightLimit * 0.7f, if (large) 240 else 112)
                Column(Modifier.width(width).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text, color = Ink, style = clockStyle(font), maxLines = 1, softWrap = false)
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { (remaining.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(0.75f).height(3.dp), color = accent, trackColor = Line)
                }
            }
        }
    }
}

private fun clockStyle(font: TextUnit) = TextStyle(fontSize = font, lineHeight = font,
    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
    platformStyle = PlatformTextStyle(includeFontPadding = false))

/** Measure with the current font scale. Only the display numerals fit their allotted canvas. */
@Composable
private fun fittedClockSize(value: String, width: Dp, height: Dp, maximum: Int): TextUnit {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val measured = measurer.measure(value, clockStyle(100.sp), softWrap = false)
    val ratio = with(density) { minOf(width.toPx() / measured.size.width.coerceAtLeast(1),
        height.toPx() / measured.size.height.coerceAtLeast(1), maximum / 100f) }
    return (100f * ratio).coerceAtLeast(12f).sp
}

internal val FlipProgressKey = SemanticsPropertyKey<Float>("FlipProgress")
internal val FlipFromKey = SemanticsPropertyKey<Long>("FlipFrom")
internal val FlipTargetKey = SemanticsPropertyKey<Long>("FlipTarget")
internal val FlipAngleKey = SemanticsPropertyKey<Float>("FlipAngle")

/** Old and new values are both rendered. Re-entering foreground or interrupted changes settle immediately. */
@Composable
internal fun SplitFlipClock(seconds: Long, phase: Phase, motion: Boolean, heightLimit: Dp,
    large: Boolean = false, accent: Color = Tomato, minuteDigits: Int = 2) {
    var from by remember { mutableLongStateOf(seconds) }
    var target by remember { mutableLongStateOf(seconds) }
    var previousPhase by remember { mutableStateOf(phase) }
    var previousMotion by remember { mutableStateOf(motion) }
    var turning by remember { mutableStateOf(false) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(seconds, phase, motion) {
        val animate = motion && previousMotion && phase == previousPhase && target - seconds == 1L && !turning
        from = if (animate) target else seconds
        target = seconds
        previousPhase = phase
        previousMotion = motion
        turning = animate
        progress.snapTo(if (animate) 0f else 1f)
        if (animate) progress.animateTo(1f, tween(480, easing = LinearEasing))
        turning = false
    }
    val old = clockText(from * 1000)
    val next = clockText(target * 1000)
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("flip_clock").semantics {
        this[FlipProgressKey] = progress.value
        this[FlipFromKey] = from
        this[FlipTargetKey] = target
    }, contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, if (large) 1000.dp else 640.dp)
        val height = minOf(heightLimit, width * 0.36f)
        val minutes = next.substringBefore(':').padStart(minuteDigits, '0')
        val colonWidth = maxOf(12.dp, width * 0.035f)
        val available = width - colonWidth - 16.dp
        val minuteWidth = available * (minutes.length.toFloat() / (minutes.length + 2))
        val font = fittedClockSize(minutes, minuteWidth * 0.9f, height * 0.82f, if (large) 240 else 160)
        Row(Modifier.width(width), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FlipPanel(old.substringBefore(':').padStart(minuteDigits, '0'), minutes, progress.value, height, font, accent,
                Modifier.weight(minutes.length.toFloat()).height(height), "minutes")
            Canvas(Modifier.width(colonWidth).height(height)) {
                val radius = minOf(size.width * 0.22f, size.height * 0.022f)
                drawCircle(accent, radius, Offset(size.width / 2, size.height * 0.41f))
                drawCircle(accent, radius, Offset(size.width / 2, size.height * 0.59f))
            }
            FlipPanel(old.substringAfter(':'), next.substringAfter(':'), progress.value, height, font, accent,
                Modifier.weight(2f).height(height), "seconds")
        }
    }
}

@Composable
private fun FlipPanel(old: String, next: String, progress: Float, height: Dp, font: TextUnit,
    accent: Color, modifier: Modifier, tag: String) {
    val light = Paper.luminance() > 0.5f
    val glass = LocalAppearance.current.interfaceMaterial == InterfaceMaterial.GLASS
    val face = if(glass) CardPaper.copy(alpha=.12f) else if(light) Color(0xFF2B302D) else Color(0xFF101512)
    val digits = if(glass) Ink else if(light) Color(0xFFFAF8F2) else Ink
    val changing = old != next && progress < 1f
    Box(modifier.clip(RoundedCornerShape(12.dp))) {
        FlipHalf(next, true, height, font, face, digits, Modifier.align(Alignment.TopCenter))
        FlipHalf(if (changing) old else next, false, height, font, face, digits, Modifier.align(Alignment.BottomCenter))
        if (changing) {
            val first = progress < 0.5f
            val fraction = if (first) progress * 2f else (progress - 0.5f) * 2f
            val eased = FastOutSlowInEasing.transform(fraction.coerceIn(0f, 1f))
            val angle = if (first) -90f * eased else 90f * (1f - eased)
            FlipHalf(if (first) old else next, first, height, font, face, digits,
                Modifier.align(if (first) Alignment.TopCenter else Alignment.BottomCenter)
                    .testTag("flip_${tag}_flap").semantics { this[FlipAngleKey] = angle },
                angle = angle, shade = if (first) 0.28f * eased else 0.28f * (1f - eased))
        }
        Canvas(Modifier.matchParentSize()) {
            drawLine(Color.Black.copy(alpha = 0.55f), Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.09f), Offset(0f, size.height / 2 + 1.dp.toPx()),
                Offset(size.width, size.height / 2 + 1.dp.toPx()), 0.5.dp.toPx())
            drawCircle(accent.copy(alpha = 0.7f), 1.5.dp.toPx(), Offset(4.dp.toPx(), size.height / 2))
            drawCircle(accent.copy(alpha = 0.7f), 1.5.dp.toPx(), Offset(size.width - 4.dp.toPx(), size.height / 2))
        }
    }
}

@Composable
private fun FlipHalf(value: String, top: Boolean, height: Dp, font: TextUnit, face: Color,
    digits: Color, modifier: Modifier, angle: Float = 0f, shade: Float = 0f) {
    val measurer = rememberTextMeasurer()
    val text = measurer.measure(value, clockStyle(font), softWrap = false)
    Canvas(modifier.fillMaxWidth().height(height / 2).glassSurface(RoundedCornerShape(0.dp),GlassRole.CONTROL).graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, if (top) 1f else 0f)
        rotationX = angle
        cameraDistance = 18 * density
        clip = true
    }) {
        drawRect(face)
        clipRect {
            drawText(text, color = digits, topLeft = Offset((size.width - text.size.width) / 2,
                (size.height * 2 - text.size.height) / 2 - if (top) 0f else size.height))
        }
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (top) 0.04f else 0.1f), Color.Transparent)))
        if (shade > 0f) drawRect(Color.Black.copy(alpha = shade))
    }
}
