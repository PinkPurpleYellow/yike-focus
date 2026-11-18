package app.yike.focus.ui

import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.yike.focus.domain.*
import android.view.View

internal class GlassSource(val layer: GraphicsLayer) {
    var coordinates by mutableStateOf<LayoutCoordinates?>(null)
    var view: View? = null
    var origin by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)
    var fallback by mutableStateOf<ImageBitmap?>(null)
    var sharpDetail: GraphicsLayer? = null
    var controlScene: GlassBlurScene? = null
    var readingScene: GlassBlurScene? = null
}
internal class GlassBlurScene(val layer: GraphicsLayer, val scale: Float = .5f)

/** Non-observable counters for device profiling; never trigger UI work. */
internal object GlassDiagnostics {
    var sourceRecords=0L;var blurRecords=0L;var effectUpdates=0L;var navCompositions=0L
    fun reset() { sourceRecords=0;blurRecords=0;effectUpdates=0;navCompositions=0 }
}
internal val LocalGlassBackground = staticCompositionLocalOf<GlassSource?> { null }
internal val LocalDialogBackdrop = staticCompositionLocalOf<GlassSource?> { null }
internal val LocalGlassContent = staticCompositionLocalOf<GlassSource?> { null }
internal val LocalInsideGlass = staticCompositionLocalOf { false }
internal val LocalSystemHighContrast = staticCompositionLocalOf { false }
enum class GlassRole { CONTROL, CONTENT, DIALOG }
internal val GlassRoleKey = SemanticsPropertyKey<String>("GlassMaterialRole")
internal val GlassPressKey = SemanticsPropertyKey<Float>("GlassPressProgress")

@Stable class GlassInteraction {
    var pressed by mutableStateOf(false)
    var position by mutableStateOf(Offset(.5f, .5f))
    var velocity by mutableStateOf(Offset.Zero)
    fun reset() { pressed = false; velocity = Offset.Zero }
}

@Composable fun rememberGlassInteraction(): GlassInteraction {
    val state = remember { GlassInteraction() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, state) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) state.reset() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); state.reset() }
    }
    return state
}

/** Observe, never consume: scrolling, taps, drag selection and long-press docking keep ownership. */
fun Modifier.glassTouch(state: GlassInteraction): Modifier = pointerInput(state) {
    awaitEachGesture {
        try {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.pressed = true
            var last = down.position; var time = down.uptimeMillis
            state.position = Offset(last.x / size.width.coerceAtLeast(1), last.y / size.height.coerceAtLeast(1))
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val dt = (change.uptimeMillis-time).coerceAtLeast(1).toFloat()
                state.position = Offset(change.position.x / size.width.coerceAtLeast(1), change.position.y / size.height.coerceAtLeast(1))
                val velocity=Offset((change.position.x-last.x)/dt/2f,(change.position.y-last.y)/dt/2f)
                state.velocity=state.velocity*.7f+velocity*.3f
                last = change.position; time = change.uptimeMillis
            } while (event.changes.any { it.pressed })
        } finally { state.reset() }
    }
}

internal fun glassTier(api: Int) = when { api >= 33 -> 2; api >= 31 -> 1; else -> 0 }

@Composable internal fun observeSystemHighContrast(): Boolean {
    val context = LocalContext.current
    var highContrast by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        fun refresh() { highContrast = runCatching { Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1 }.getOrDefault(false) }
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        refresh()
        context.contentResolver.registerContentObserver(Settings.Secure.getUriFor("high_text_contrast_enabled"), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return highContrast
}

@Composable internal fun glassReadable(): Boolean = LocalAppearance.current.glassReadable || LocalSystemHighContrast.current

/** The material can flex; sharp text and icon layers are not stretched. */
@Composable internal fun Modifier.glassDeform(state: GlassInteraction): Modifier {
    val duration=motionMillis(if(state.pressed) 120 else 240)
    val p by animateFloatAsState(if(duration>0 && state.pressed) 1f else 0f,tween(duration),label="glass_flex")
    return graphicsLayer {
        val speed=kotlin.math.abs(state.velocity.x).coerceIn(0f,1f)
        scaleX=1f+p*(.035f+.025f*speed)
        scaleY=1f+p*(.035f-.015f*speed)
    }
}

/** Different thicknesses, all visibly translucent; enhanced reading is an explicit override. */
internal fun glassOpacity(s: FocusSettings, role: GlassRole, readable: Boolean): Float {
    val t = s.glassTransparency.coerceIn(0f, 1f)
    val base = when (role) {
        GlassRole.CONTROL -> .32f - t * .22f
        GlassRole.CONTENT -> .44f - t * .19f
        GlassRole.DIALOG -> .76f - t * .30f
    }
    return if (readable) maxOf(base, .92f) else base
}

@Composable internal fun Modifier.recordGlassSource(source: GlassSource, underlay: GlassSource? = null): Modifier {
    val view = LocalView.current
    val settings=LocalAppearance.current
    val live=settings.interfaceMaterial==InterfaceMaterial.GLASS
    val density=LocalDensity.current.density
    val control=rememberGraphicsLayer();val reading=rememberGraphicsLayer()
    val detail=rememberGraphicsLayer()
    val foreground=rememberGraphicsLayer()
    val controlScene=remember(control) {GlassBlurScene(control)}
    val readingScene=remember(reading) {GlassBlurScene(reading)}
    val controlBlur=remember(settings.glassBlur,density) {
        if(Build.VERSION.SDK_INT>=31) android.graphics.RenderEffect.createBlurEffect(
            (1f+settings.glassBlur*6f)*density*.5f,(1f+settings.glassBlur*6f)*density*.5f,
            android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() else null
    }
    val readingBlur=remember(settings.glassBlur,density) {
        if(Build.VERSION.SDK_INT>=31) android.graphics.RenderEffect.createBlurEffect(
            (2f+settings.glassBlur*18f)*density*.5f,(2f+settings.glassBlur*18f)*density*.5f,
            android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() else null
    }
    SideEffect {
        source.controlScene=controlScene;source.readingScene=readingScene
        source.sharpDetail = if (underlay == null) null else detail
        detail.alpha = (.46f - .32f * settings.glassBlur).coerceIn(.12f, .46f)
        control.renderEffect=controlBlur;reading.renderEffect=readingBlur
    }
    return onGloballyPositioned {
        source.coordinates = it; source.view = view
        source.origin = it.localToWindow(Offset.Zero); source.size = it.size
    }.drawWithContent {
        if (live && Build.VERSION.SDK_INT >= 31 && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
            if (underlay == null) {
                source.layer.record { this@drawWithContent.drawContent() }
            } else {
                foreground.record { this@drawWithContent.drawContent() }
                source.layer.record {
                    translate(underlay.origin.x-source.origin.x, underlay.origin.y-source.origin.y) { drawLayer(underlay.layer) }
                    drawLayer(foreground)
                }
            }
            if (underlay != null && Build.VERSION.SDK_INT >= 33) {
                detail.record { drawLayer(source.layer) }
            }
            GlassDiagnostics.sourceRecords++
            // One half-resolution live blur per scene/role. Moving lenses only sample these
            // retained nodes; they do not blur another copy of the whole page on every frame.
            val small=IntSize(maxOf(1,(size.width*.5f).toInt()),maxOf(1,(size.height*.5f).toInt()))
            control.record(size=small) {scale(.5f,.5f,pivot=Offset.Zero) {drawLayer(source.layer)}}
            GlassDiagnostics.blurRecords++
            run {
                reading.record(size=small) {scale(.5f,.5f,pivot=Offset.Zero) {drawLayer(source.layer)}}
                GlassDiagnostics.blurRecords++
            }
            // The composite is for lenses. Only the foreground belongs over the visible wallpaper.
            drawLayer(if (underlay == null) source.layer else foreground)
        } else drawContent()
    }
}

/** Local -> screen affine matrix. Includes layer scale/translation and separate Dialog windows. */
internal fun windowMatrix(c: LayoutCoordinates, view: View): android.graphics.Matrix {
    val screen = IntArray(2); val window = IntArray(2)
    view.getLocationOnScreen(screen); view.getLocationInWindow(window)
    val a = c.localToWindow(Offset.Zero) + Offset((screen[0]-window[0]).toFloat(), (screen[1]-window[1]).toFloat())
    val x = c.localToWindow(Offset(1f,0f)) - c.localToWindow(Offset.Zero)
    val y = c.localToWindow(Offset(0f,1f)) - c.localToWindow(Offset.Zero)
    return android.graphics.Matrix().apply { setValues(floatArrayOf(x.x,y.x,a.x,x.y,y.y,a.y,0f,0f,1f)) }
}

/** Sharp foreground is always drawn after the isolated sampled background. */
@Composable fun Modifier.glassSurface(shape: Shape, role: GlassRole = GlassRole.CONTENT,
    navigation: Boolean = false, interaction: GlassInteraction? = null, sampleMotion: (() -> Offset)? = null): Modifier {
    val settings = LocalAppearance.current
    if (settings.interfaceMaterial != InterfaceMaterial.GLASS) return this
    // Content within a glass card shares that material. Interactive controls remain their own lens.
    if (LocalInsideGlass.current && role == GlassRole.CONTENT) return this
    val background = LocalGlassBackground.current
    val source = when {
        navigation -> LocalGlassContent.current ?: background
        role == GlassRole.DIALOG && LocalDialogBackdrop.current?.view !== LocalView.current -> LocalDialogBackdrop.current ?: background
        else -> background
    }
    val sample = rememberGraphicsLayer()
    val view = LocalView.current
    val readable = glassReadable()
    val opacity = glassOpacity(settings, role, readable)
    val paper = Paper; val tint = CardPaper
    val wallpaper = LocalFocusBackdrop.current?.takeIf { it.enabled }?.bitmap
    val hasWallpaper = wallpaper != null
    val frost = background?.fallback
    val wallpaperBrush = remember(frost) { frost?.let { android.graphics.BitmapShader(it.asAndroidBitmap(),
        android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP) } }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }
    val crop = remember { android.graphics.Matrix() }
    // LayoutCoordinates is mutable in place when an ancestor scrolls. Keep an observable
    // position as well, so retained display lists get recorded again after placement.
    var placedOrigin by remember { mutableStateOf(Offset.Unspecified) }
    val wallpaperShade = settings.backgroundShade.coerceIn(0f, .95f)
    val outline = Color.White.copy(alpha = if (tint.luminance() > .5f) .68f else .32f)
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current.density
    val cardTouch = if (role == GlassRole.CONTENT && !readable && motionMillis(1) > 0) rememberGlassInteraction() else null
    val touch = interaction ?: cardTouch
    val motion = motionMillis(if (touch?.pressed == true) 120 else 240)
    val progress by animateFloatAsState(if (motion > 0 && touch?.pressed == true) 1f else 0f,
        tween(motion), label = "glass_depth")
    val optics = remember { if (Build.VERSION.SDK_INT >= 33) runCatching { GlassOptics(1f,alreadyBlurred=true) }.getOrNull() else null }
    val inverse=remember {android.graphics.Matrix()};val map=remember {android.graphics.Matrix()}
    val cornerValues=remember {FloatArray(4)}
    return then(if (cardTouch != null) Modifier.glassTouch(cardTouch) else Modifier).onGloballyPositioned {
        coords = it
        placedOrigin = it.localToWindow(Offset.Zero)
    }.semantics {
        this[GlassRoleKey] = role.name; this[GlassPressKey] = progress
    }.clip(shape).drawWithContent {
        // Draw-phase observation refreshes sample coordinates without relayout while moving.
        placedOrigin
        source?.origin
        sampleMotion?.invoke()
        val target = coords; val from = source?.coordinates
        val pad = 24f * density
        val gpu = Build.VERSION.SDK_INT >= 31 && drawContext.canvas.nativeCanvas.isHardwareAccelerated &&
            target?.isAttached == true && from?.isAttached == true && source.view != null
        if (wallpaper != null && source === background && source != null) {
            // Map the entire host image, including its original centre crop, into this draw
            // space. The clamped bitmap has no transparent RenderNode boundary inside a card.
            if (frost != null && wallpaperBrush != null && from?.isAttached == true &&
                target?.isAttached == true && source.size.width > 0 && source.size.height > 0 &&
                windowMatrix(target, view).invert(inverse)) {
                val scale = maxOf(source.size.width.toFloat() / wallpaper.width, source.size.height.toFloat() / wallpaper.height)
                val width = kotlin.math.round(wallpaper.width * scale)
                val height = kotlin.math.round(wallpaper.height * scale)
                crop.setScale(width / frost.width, height / frost.height)
                crop.postTranslate(kotlin.math.round((source.size.width - width) / 2), kotlin.math.round((source.size.height - height) / 2))
                map.setConcat(inverse, windowMatrix(from, source.view!!))
                map.preConcat(crop)
                wallpaperBrush.setLocalMatrix(map)
                paint.shader = wallpaperBrush
                if (gpu && Build.VERSION.SDK_INT >= 33 && optics != null && !readable) {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    cornerValues.fill(0f)
                    if (outline is Outline.Rounded) with(outline.roundRect) {
                        cornerValues[0] = topLeftCornerRadius.x; cornerValues[1] = topRightCornerRadius.x
                        cornerValues[2] = bottomRightCornerRadius.x; cornerValues[3] = bottomLeftCornerRadius.x
                    }
                    val pointer = touch?.position ?: Offset(.5f, .5f)
                    val velocity = touch?.velocity ?: Offset.Zero
                    val shader = optics.configure(size.width, size.height, 0f, cornerValues, density, role,
                        progress, pointer.x, pointer.y, velocity.x, velocity.y, false)
                    shader.setInputShader("scene", wallpaperBrush)
                    paint.shader = shader
                }
                paint.alpha = 255
                drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                drawRect(paper.copy(alpha = wallpaperShade))
            }
        } else if (gpu) {
            val ok = windowMatrix(target,view).invert(inverse)
            if (ok) {
                map.setConcat(inverse, windowMatrix(from, source.view!!));map.postTranslate(pad,pad)
                val scene=if(role==GlassRole.CONTROL) source.controlScene else source.readingScene
                sample.record(size = IntSize((size.width+pad*2).toInt(), (size.height+pad*2).toInt())) {
                    val native = drawContext.canvas.nativeCanvas; val save = native.save()
                    native.concat(map)
                    if(scene!=null) scale(1f/scene.scale,1f/scene.scale,pivot=Offset.Zero) {drawLayer(scene.layer)}
                    else drawLayer(source.layer)
                    // Keep local contrast for the moving lens; the reading surface stays frosted.
                    if (navigation && Build.VERSION.SDK_INT >= 33 && !readable) {
                        source.sharpDetail?.let { drawLayer(it) }
                    }
                    native.restoreToCount(save)
                }
                val o = shape.createOutline(size, layoutDirection, this)
                cornerValues.fill(0f)
                if(o is Outline.Rounded) {cornerValues[0]=o.roundRect.topLeftCornerRadius.x;cornerValues[1]=o.roundRect.topRightCornerRadius.x
                    cornerValues[2]=o.roundRect.bottomRightCornerRadius.x;cornerValues[3]=o.roundRect.bottomLeftCornerRadius.x}
                if (Build.VERSION.SDK_INT >= 33 && optics != null) {
                    val p = touch?.position ?: Offset(.5f,.5f); val v = touch?.velocity ?: Offset.Zero
                    sample.renderEffect = optics.update(size.width,size.height,pad,cornerValues,density,role,progress,p.x,p.y,v.x,v.y,readable).asComposeRenderEffect()
                } else sample.renderEffect = null
                translate(-pad,-pad) { drawLayer(sample) }
            }
        } else {
            val image = background?.fallback
            val backgroundCoordinates = background?.coordinates
            if (image != null && background.size.width > 0 && target?.isAttached == true &&
                backgroundCoordinates?.isAttached == true && background.view != null) {
                val ratio = maxOf(background.size.width.toFloat()/image.width,background.size.height.toFloat()/image.height)
                val w = image.width*ratio; val h = image.height*ratio
                val inverse = android.graphics.Matrix()
                if(windowMatrix(target,view).invert(inverse)) {
                    val map=android.graphics.Matrix().apply {setConcat(inverse,windowMatrix(backgroundCoordinates,background.view!!))}
                    val native=drawContext.canvas.nativeCanvas;val saved=native.save()
                    native.concat(map)
                    translate((background.size.width-w)/2,(background.size.height-h)/2) {
                        drawImage(image,dstSize=IntSize(w.toInt(),h.toInt()))
                    }
                    native.restoreToCount(saved)
                }
                if(hasWallpaper) drawRect(paper.copy(alpha = wallpaperShade))
            }
        }
        drawRect(tint.copy(alpha = opacity))
        drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .12f+progress*.07f),Color.Transparent,Color.White.copy(alpha = .025f))))
        // A small inset shadow provides thickness without washing out the foreground.
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .035f),Color.Transparent,Color.Black.copy(alpha = .025f))))
        if (cardTouch != null && progress > 0f) {
            val pointer = cardTouch.position
            val center = Offset(pointer.x.coerceIn(0f,1f)*size.width, pointer.y.coerceIn(0f,1f)*size.height)
            drawOutline(shape.createOutline(size, layoutDirection, this), Brush.radialGradient(listOf(Color.White.copy(alpha=.28f*progress), Color.Transparent),
                center=center, radius=80.dp.toPx()), style=Stroke(2.dp.toPx()))
        }
        drawContent()
    }.border(.8.dp,Brush.linearGradient(listOf(outline,outline.copy(alpha=.07f),outline.copy(alpha=.42f))),shape)
}

/** CPU work only when wallpaper/blur changes, never on the timer or animation frame. */
internal fun cachedFrost(bitmap: ImageBitmap, strength: Float): ImageBitmap {
    val original = bitmap.asAndroidBitmap()
    val ratio = minOf(1f, 768f / maxOf(original.width, original.height))
    val w = maxOf(1, (original.width * ratio).toInt()); val h = maxOf(1, (original.height * ratio).toInt())
    val small = Bitmap.createScaledBitmap(original, w, h, true)
    val pixels = IntArray(w * h); small.getPixels(pixels, 0, w, 0, 0, w, h)
    val temp = IntArray(pixels.size); val radius = (1 + strength * 8).toInt()
    repeat(3) {
        for (y in 0 until h) for (x in 0 until w) {
            var r = 0; var g = 0; var b = 0; var n = 0
            for (d in -radius..radius) { val p = pixels[y * w + (x + d).coerceIn(0, w - 1)]
                r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255; n++ }
            temp[y * w + x] = (255 shl 24) or ((r/n) shl 16) or ((g/n) shl 8) or (b/n)
        }
        for (y in 0 until h) for (x in 0 until w) {
            var r = 0; var g = 0; var b = 0; var n = 0
            for (d in -radius..radius) { val p = temp[(y + d).coerceIn(0, h - 1) * w + x]
                r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255; n++ }
            pixels[y * w + x] = (255 shl 24) or ((r/n) shl 16) or ((g/n) shl 8) or (b/n)
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun FocusAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null) {
    if (LocalAppearance.current.interfaceMaterial != InterfaceMaterial.GLASS) {
        AlertDialog(onDismissRequest, confirmButton, modifier, dismissButton = dismissButton, title = title, text = text)
        return
    }
    Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier.widthIn(max = 560.dp).fillMaxWidth().padding(20.dp).imePadding()
            .glassSurface(DialogShape, GlassRole.DIALOG).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CompositionLocalProvider(LocalInsideGlass provides true) {
                title?.let { ProvideTextStyle(MaterialTheme.typography.headlineSmall) { it() } }
                Box(Modifier.weight(1f, fill = false)) { text?.let { ProvideTextStyle(MaterialTheme.typography.bodyMedium) { it() } } }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    dismissButton?.invoke(); confirmButton()
                }
            }
        }
    }
}
