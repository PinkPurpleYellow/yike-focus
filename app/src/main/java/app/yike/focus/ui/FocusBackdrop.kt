package app.yike.focus.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.yike.focus.data.ImageStore
import app.yike.focus.domain.FocusSettings
import app.yike.focus.domain.UiPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A tiny shared image cache also avoids a decode flash when the appearance editor opens. */
private object BackdropBitmaps {
    private val images = linkedMapOf<String, ImageBitmap>()
    @Synchronized fun get(key: String) = images[key]
    @Synchronized fun put(key: String, image: ImageBitmap) {
        images[key] = image
        while(images.size > 2) images.remove(images.keys.first())
    }
}

/** Scene-owned image: entering fullscreen does not restart decoding or replace its drawing layer. */
internal data class FocusBackdropContext(val bitmap: ImageBitmap?, val enabled: Boolean,
    val palette: FocusPalette, val loadedFile: String)
internal val LocalFocusBackdrop = staticCompositionLocalOf<FocusBackdropContext?> { null }

internal fun wallpaperForeground(p: FocusPalette): Color =
    if (p.paper.luminance() > 0.5f) Color(0xFF171D19) else Color(0xFFFDFBF6)

internal fun textContrast(a: Color, b: Color): Float =
    (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)

/** A continuous veil keeps text readable even over black/white extremes, without bounded panels.
 * The user's saved shade is never changed. Only very low dark-mode values need a small safety floor. */
internal fun readableWallpaperShade(p: FocusPalette, requested: Float): Float {
    val foreground = wallpaperForeground(p)
    fun readable(alpha: Float) = listOf(Color.Black, Color.White).all {
        textContrast(foreground, p.paper.copy(alpha = alpha).compositeOver(it)) >= 4.5f
    }
    val saved = requested.coerceIn(0.55f, 0.95f)
    if (readable(saved)) return saved
    var low = saved; var high = 0.95f
    repeat(14) { val mid = (low + high) / 2; if (readable(mid)) high = mid else low = mid }
    return high
}

@Composable
fun FocusBackdropHost(settings: FocusSettings, enabled: Boolean = true, independent: Boolean = false,
    content: @Composable () -> Unit) {
    // Nested page components also work alone in previews; the production scene owns the outer host.
    if (!independent && LocalFocusBackdrop.current != null) { content(); return }
    val context = LocalContext.current.applicationContext
    val p = LocalFocusPalette.current
    val glass = settings.interfaceMaterial == app.yike.focus.domain.InterfaceMaterial.GLASS
    var image by remember(context) { mutableStateOf(settings.backgroundFile to BackdropBitmaps.get(settings.backgroundFile)) }
    var frost by remember { mutableStateOf<ImageBitmap?>(null) }
    val layer = rememberGraphicsLayer()
    val source = remember(layer) { GlassSource(layer) }
    LaunchedEffect(settings.backgroundFile, settings.glassBlur, glass, enabled, p) {
        val loaded = withContext(Dispatchers.IO) {
            BackdropBitmaps.get(settings.backgroundFile) ?: runCatching {
                ImageStore.file(context,settings.backgroundFile)?.let {BitmapFactory.decodeFile(it.path)?.asImageBitmap()}
                    ?.also {BackdropBitmaps.put(settings.backgroundFile,it)}
            }.getOrNull()
        }
        val blurred = if(glass && enabled) withContext(Dispatchers.Default) {
            if(loaded!=null) cachedFrost(loaded,settings.glassBlur) else {
                val bmp=android.graphics.Bitmap.createBitmap(256,160,android.graphics.Bitmap.Config.ARGB_8888)
                val canvas=android.graphics.Canvas(bmp);canvas.drawColor(p.paper.toArgb())
                val paint=android.graphics.Paint().apply {shader=android.graphics.RadialGradient(33f,16f,180f,
                    intArrayOf(p.accent.copy(alpha=.22f).toArgb(),android.graphics.Color.TRANSPARENT),null,android.graphics.Shader.TileMode.CLAMP)}
                canvas.drawRect(0f,0f,256f,160f,paint)
                paint.shader=android.graphics.RadialGradient(230f,128f,154f,intArrayOf(p.secondary.copy(alpha=.18f).toArgb(),android.graphics.Color.TRANSPARENT),null,android.graphics.Shader.TileMode.CLAMP)
                canvas.drawRect(0f,0f,256f,160f,paint);bmp.asImageBitmap()
            }
        } else null
        // Publish a matching image/cache pair together; never paint the previous scene's frost.
        frost=blurred;image=settings.backgroundFile to loaded
    }
    val bitmap=image.second
    SideEffect {source.fallback=frost}
    CompositionLocalProvider(LocalDialogBackdrop provides null, LocalGlassBackground provides source, LocalFocusBackdrop provides FocusBackdropContext(image.second, enabled, p, image.first)) {
        Box(Modifier.fillMaxSize().background(p.paper).testTag("focus_scene")) {
            Box(Modifier.matchParentSize().recordGlassSource(source)) {
                if (enabled) FocusBackdrop(settings, Modifier.matchParentSize())
                else Box(Modifier.matchParentSize().background(p.paper))
                if (glass && (!enabled || bitmap == null)) Canvas(Modifier.matchParentSize()) {
                    drawRect(Brush.radialGradient(listOf(p.accent.copy(alpha = .22f), Color.Transparent),
                        center = Offset(size.width * .13f, size.height * .1f), radius = size.width * .7f))
                    drawRect(Brush.radialGradient(listOf(p.secondary.copy(alpha = .18f), Color.Transparent),
                        center = Offset(size.width * .9f, size.height * .8f), radius = size.width * .6f))
                }
            }
            content()
        }
    }
}

@Composable
private fun FocusBackdrop(settings: FocusSettings, modifier: Modifier) {
    val state = requireNotNull(LocalFocusBackdrop.current)
    Box(modifier.background(state.palette.paper)) {
        state.bitmap?.let { bitmap ->
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().testTag("custom_background_image"))
            Box(Modifier.matchParentSize().background(state.palette.paper.copy(
                alpha = if(settings.interfaceMaterial == app.yike.focus.domain.InterfaceMaterial.GLASS) settings.backgroundShade.coerceIn(0f,.95f) else readableWallpaperShade(state.palette, settings.backgroundShade))))
        }
        if (settings.uiPreset == UiPreset.JOURNAL) {
            val line = state.palette.line.copy(alpha = 0.42f)
            Canvas(Modifier.matchParentSize()) {
                var y = 40.dp.toPx()
                while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 0.5.dp.toPx()); y += 40.dp.toPx() }
            }
        }
    }
}

/** Only exposed text adopts wallpaper colors. Solid cards retain the exact theme palette. */
@Composable
internal fun WallpaperContent(enabled: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalFocusBackdrop.current
    if (!enabled || context?.enabled != true || context.bitmap == null) { content(); return }
    val p = context.palette
    val ink = wallpaperForeground(p)
    // Normal controls continue to use the Material color scheme; exposed labels use readable ink.
    val shade = readableWallpaperShade(p, LocalAppearance.current.backgroundShade)
    fun accent(color: Color) = if (listOf(Color.Black, Color.White).all {
        textContrast(color, p.paper.copy(alpha = shade).compositeOver(it)) >= 4.5f
    }) color else ink
    CompositionLocalProvider(LocalFocusPalette provides p.copy(ink = ink, muted = ink,
        accent = accent(p.accent), secondary = accent(p.secondary)),
        androidx.compose.material3.LocalContentColor provides ink, content = content)
}

@Composable
fun FocusContentTransition(fullscreen: Boolean, content: @Composable (Boolean) -> Unit) {
    Crossfade(targetState = fullscreen, modifier = Modifier.fillMaxSize(),
        animationSpec = tween(motionMillis(200)), label = "focus_fullscreen", content = content)
}
