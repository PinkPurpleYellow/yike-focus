package app.yike.focus.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.yike.focus.FocusApplication
import app.yike.focus.domain.*
import kotlinx.coroutines.cancel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.LocalDate
import kotlin.math.abs

/** Actual Compose scene rendering, including image pixels rather than only container existence. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = FocusApplication::class, qualifiers = "w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperUiTest {
    @get:Rule val compose = createComposeRule()
    private val files = mutableListOf<File>()
    @After fun cleanup() {
        files.forEach { it.delete() }
        closeTestApplication(ApplicationProvider.getApplicationContext<FocusApplication>())
    }

    private fun image(kind: Int): File {
        val context = ApplicationProvider.getApplicationContext<FocusApplication>()
        val file = File(context.filesDir, "background-00000000-0000-0000-0000-${kind.toString().padStart(12, '0')}.jpg")
        val bitmap = Bitmap.createBitmap(1280, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colors = when (kind) {
            0 -> intArrayOf(0xFFFFE5A8.toInt(), 0xFFFCFBF8.toInt(), 0xFFBFE0D3.toInt())
            1 -> intArrayOf(0xFF061926.toInt(), 0xFF233F54.toInt(), 0xFF020A13.toInt())
            else -> intArrayOf(0xFF23424F.toInt(), 0xFFD3B999.toInt(), 0xFF416D61.toInt())
        }
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 1280f, 800f, colors, null, Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, 1280f, 800f, paint)
        if (kind == 2) {
            paint.shader = null
            for (x in 0 until 1280 step 16) {
                paint.color = if ((x / 16) % 2 == 0) 0x4452D5AD else 0x668A5328
                canvas.drawCircle(x.toFloat(), (x * 7 % 800).toFloat(), 160f, paint)
            }
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle(); files += file
        return file
    }

    private fun snapshot(view: View, name: String? = null): Bitmap {
        lateinit var result: Bitmap
        compose.runOnIdle {
            val root = view.rootView
            result = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(result))
            if (name != null) {
                val file = File("build/outputs/wallpaper-3.0.1/$name.png")
                file.parentFile!!.mkdirs()
                file.outputStream().use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        return result
    }

    private fun assertPixelNear(expected: Int, actual: Int) {
        for (shift in listOf(0, 8, 16)) assertTrue("Expected wallpaper pixel ${expected.toUInt().toString(16)}, got ${actual.toUInt().toString(16)}",
            abs(((expected shr shift) and 255) - ((actual shr shift) and 255)) <= 4)
    }

    @Test fun scrolledGlassKeepsTheHostWallpaperAtTheSameScreenPixel() {
        val context = ApplicationProvider.getApplicationContext<FocusApplication>()
        val file = File(context.filesDir, "background-a231.jpg").also { files += it }
        val fixture = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(fixture)
        val paint = Paint()
        for (y in 0 until 540 step 60) {
            paint.color = if (y % 120 == 0) android.graphics.Color.RED else android.graphics.Color.BLUE
            canvas.drawRect(0f, y.toFloat(), 960f, y+60f, paint)
        }
        file.outputStream().use { fixture.compress(Bitmap.CompressFormat.JPEG, 98, it) }; fixture.recycle()
        val settings = FocusSettings(backgroundFile = file.name, interfaceMaterial = InterfaceMaterial.GLASS,
            glassBlur = .12f, backgroundShade = .1f, glassTransparency = 1f)
        var view: View? = null
        var ready = false
        compose.setContent {
            val v = LocalView.current
            FocusTheme(settings) { FocusBackdropHost(settings) {
                val loaded = LocalGlassBackground.current?.fallback != null && LocalFocusBackdrop.current?.bitmap != null
                SideEffect { view = v; ready = loaded }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("stripe_scroll")) {
                    Spacer(Modifier.height(200.dp))
                    Box(Modifier.padding(start = 160.dp).width(800.dp).height(540.dp).glassSurface(PanelShape))
                    Spacer(Modifier.height(600.dp))
                }
            } }
        }
        compose.waitUntil(15000) { ShadowLooper.idleMainLooper(); ready }
        for (pass in 0..1) {
            if (pass == 1) compose.onNodeWithTag("stripe_scroll").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 213f) }
            compose.waitForIdle()
            val frame = snapshot(requireNotNull(view))
            val mismatches = (250..460 step 3).count { y ->
                fun channel(x: Int): Int { val c=frame.getPixel(x,y);return android.graphics.Color.red(c)-android.graphics.Color.blue(c) }
                val outside = channel(90); val inside = channel(370)
                kotlin.math.abs(outside) > 120 && inside * outside <= 0
            }
            frame.recycle()
            assertTrue("Wrong wallpaper stripe inside glass after scroll pass $pass: $mismatches", mismatches <= 2)
        }
    }

    @Test fun renderSuppliedWallpaperAcrossPagesWhenAvailable() {
        // The source distribution can also run this case using the generated test photograph.
        val file = image(2)
        System.getenv("YIKE_WALLPAPER_FIXTURE")?.let { File(it).copyTo(file, overwrite = true) }
        val settings = FocusSettings(backgroundFile=file.name, interfaceMaterial=InterfaceMaterial.GLASS, uiPreset=UiPreset.MINIMAL)
        var view: View? = null
        var page by mutableIntStateOf(0)
        var loaded = false
        compose.setContent { val v = LocalView.current; FocusTheme(settings) { FocusBackdropHost(settings) {
            val ready = LocalGlassBackground.current?.fallback != null && LocalFocusBackdrop.current?.bitmap != null
            SideEffect { loaded=ready; view=v }
            if (page == 2) AppearanceEditor(settings, {}, {}) else PreviewHost(settings,page)
        } } }
        compose.waitUntil(15000) { ShadowLooper.idleMainLooper(); loaded }
        for (i in 0..1) {
            compose.runOnIdle { page = i }; compose.waitForIdle()
            compose.onNodeWithTag("page_scroll").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 500f) }
            compose.waitForIdle(); snapshot(requireNotNull(view), "continuity-page-$i").recycle()
        }
        compose.runOnIdle { page = 2 }
        compose.onNodeWithTag("appearance_preset_JOURNAL").performScrollTo()
        compose.onNodeWithTag("preview_page_0").assertIsSelected()
        compose.waitForIdle()
        val root = compose.runOnIdle {
            android.view.inspector.WindowInspector.getGlobalWindowViews().last { it.width > 0 && it.height > 0 }
        }
        snapshot(root, "continuity-editor").recycle()
    }
    @Test fun exposedTextContrastCoversEveryPaletteBothModesAndShadeExtremes() {
        ThemeStyle.entries.forEach { style -> listOf(false, true).forEach { dark ->
            val p = palette(style, dark)
            listOf(0.55f, 0.65f, 0.95f).forEach { requested ->
                val shade = readableWallpaperShade(p, requested)
                assertTrue(shade >= requested && shade <= 0.95f)
                listOf(Color.Black, Color.White, Color.Red, Color.Green, Color.Blue).forEach { pixel ->
                    assertTrue("$style dark=$dark shade=$requested", textContrast(wallpaperForeground(p), p.paper.copy(alpha = shade).compositeOver(pixel)) >= 4.499f)
                }
            }
        } }
    }

    @Test fun warmHeadingsReturnAndSecondaryPagesIgnoreStoredWallpaper() {
        var page by mutableIntStateOf(0)
        var settings by mutableStateOf(FocusSettings(decorativeMotion = false))
        var view: View? = null
        compose.setContent {
            val v = LocalView.current; SideEffect { view = v }
            FocusTheme(settings) { PreviewHost(settings, page) }
        }
        compose.onNodeWithText("把注意力，留给这一刻。").assertIsDisplayed()
        compose.onNodeWithText("今天想完成").assertIsDisplayed()
        for ((index, text) in listOf(1 to "每一刻，都算数。", 2 to "找到适合你的节奏。")) {
            compose.runOnIdle { page = index }
            compose.onNodeWithText(text).assertIsDisplayed()
            val before = snapshot(requireNotNull(view))
            compose.runOnIdle { settings = settings.copy(backgroundFile = image(0).name) }
            compose.waitForIdle()
            val after = snapshot(requireNotNull(view))
            val bounds = compose.onNodeWithTag("section_heading", true).fetchSemanticsNode().boundsInRoot
            // The settings wallpaper thumbnail may change; the page heading itself must not.
            for (x in bounds.left.toInt() until bounds.right.toInt() step 3)
                for (y in bounds.top.toInt() until bounds.bottom.toInt() step 3)
                    assertEquals("Saving a wallpaper must not change the secondary page heading", before.getPixel(x,y), after.getPixel(x,y))
            before.recycle(); after.recycle()
            compose.runOnIdle { settings = settings.copy(backgroundFile = "") }
        }
    }

    @Test fun wallpaperMatrixKeepsFullscreenAndMinimalSurfacesOpen() {
        val paths = (0..2).map { image(it) }
        var settings by mutableStateOf(FocusSettings(backgroundFile = paths[0].name, decorativeMotion = false))
        var page by mutableIntStateOf(3)
        var view: View? = null
        var loadedFile = ""
        compose.setContent {
            val v = LocalView.current; SideEffect { view = v }
            FocusTheme(settings) { FocusBackdropHost(settings) {
                val name = LocalFocusBackdrop.current?.loadedFile.orEmpty()
                SideEffect { loadedFile = name }
                PreviewHost(settings, page)
            } }
        }
        val expectedSource = paths.map { android.graphics.BitmapFactory.decodeFile(it.path) }
        try {
            for (kind in 0..2) for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                for (shade in listOf(0.55f, 0.95f)) for (preset in UiPreset.entries) for (face in listOf(TimerFace.RING, TimerFace.DIGITAL, TimerFace.FLIP)) {
                    compose.runOnIdle { settings = settings.copy(backgroundFile = paths[kind].name,
                        themeMode = mode, backgroundShade = shade, uiPreset = preset, timerFace = face); page = 3 }
                    compose.waitUntil(10_000) { ShadowLooper.idleMainLooper(); compose.waitForIdle(); loadedFile == paths[kind].name }
                    compose.onNodeWithTag("exit_fullscreen", true).assertIsDisplayed()
                    compose.onAllNodesWithTag("fullscreen_reading_surface", true).assertCountEquals(0)
                    val name = if (shade == 0.55f && face == TimerFace.DIGITAL) "$kind-$mode-$preset-fullscreen" else null
                    val rendered = snapshot(requireNotNull(view), name)
                    val bounds = compose.onNodeWithTag("fullscreen_content", true).fetchSemanticsNode().boundsInRoot
                    val x = (bounds.left + 3).toInt(); val y = (bounds.top + 3).toInt()
                    val p = palette(settings.themeStyle, mode == ThemeMode.DARK)
                    val expected = p.paper.copy(alpha = readableWallpaperShade(p, shade)).compositeOver(Color(expectedSource[kind].getPixel(x, y))).toArgb()
                    // A point inside the old giant surface must now be the unbounded wallpaper itself.
                    assertPixelNear(expected, rendered.getPixel(x, y)); rendered.recycle()
                }
                for (preset in UiPreset.entries) {
                    compose.runOnIdle { settings = settings.copy(uiPreset = preset, timerFace = TimerFace.FOLLOW_PRESET, backgroundShade = 0.65f); page = 0 }
                    compose.onNodeWithText("把注意力，留给这一刻。").assertIsDisplayed()
                    compose.onNodeWithTag("timer_primary").assertIsDisplayed()
                    snapshot(requireNotNull(view), "$kind-$mode-$preset-focus").recycle()
                }
            }
        } finally { expectedSource.forEach { it.recycle() } }
    }

    @Test fun fullscreenFadeReusesDecodedImageAndMaintainsWallpaperPixels() {
        val file = image(2)
        val settings = FocusSettings(backgroundFile = file.name)
        var fullscreen by mutableStateOf(false)
        var view: View? = null
        var loaded: androidx.compose.ui.graphics.ImageBitmap? = null
        compose.setContent {
            val v = LocalView.current; SideEffect { view = v }
            FocusTheme(settings) {
                FocusBackdropHost(settings) {
                    val decoded = LocalFocusBackdrop.current?.bitmap
                    SideEffect { loaded = decoded }
                    FocusContentTransition(fullscreen) { expanded -> PreviewHost(settings, if (expanded) 3 else 0) }
                }
            }
        }
        compose.waitUntil(10_000) { loaded != null }
        compose.waitForIdle()
        val original = loaded
        val first = snapshot(requireNotNull(view), "transition-before")
        val expected = first.getPixel(1270, 400); first.recycle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { fullscreen = true }
        for ((i, step) in listOf(16L, 80L, 160L).withIndex()) {
            compose.mainClock.advanceTimeBy(step)
            compose.waitForIdle()
            assertSame("Fullscreen must reuse the already decoded image", original, loaded)
            val frame = snapshot(requireNotNull(view), "transition-$i")
            assertEquals(expected, frame.getPixel(1270, 400)); frame.recycle()
        }
        compose.runOnIdle { fullscreen = false }
        compose.mainClock.advanceTimeBy(240)
        assertSame(original, loaded)
    }

    @Test fun darkModeDigitsAndTaskHeadingUseReadableForeground() {
        val settings = FocusSettings(themeMode = ThemeMode.DARK)
        var view: View? = null
        compose.setContent {
            val v = LocalView.current; SideEffect { view = v }
            FocusTheme(settings) { PreviewHost(settings, 0) }
        }
        val bitmap = snapshot(requireNotNull(view))
        try {
            for (node in listOf(compose.onNodeWithTag("countdown", true), compose.onNodeWithText("今天想完成"))) {
                val bounds = node.fetchSemanticsNode().boundsInRoot
                var readablePixels = 0
                for (x in bounds.left.toInt() until bounds.right.toInt())
                    for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
                        val pixel = bitmap.getPixel(x,y)
                        if (android.graphics.Color.red(pixel) > 210 && android.graphics.Color.green(pixel) > 210 && android.graphics.Color.blue(pixel) > 210) readablePixels++
                    }
                assertTrue("Dark mode text must render with a bright foreground", readablePixels > 30)
            }
        } finally { bitmap.recycle() }
    }
}
