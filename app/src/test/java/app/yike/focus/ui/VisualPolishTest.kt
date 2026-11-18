package app.yike.focus.ui

import android.view.accessibility.AccessibilityManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.yike.focus.FocusApplication
import app.yike.focus.data.TaskEntity
import app.yike.focus.domain.*
import kotlinx.coroutines.cancel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = FocusApplication::class, qualifiers = "w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualPolishTest {
    @get:Rule val compose = createComposeRule()
    @After fun stopWorkers() {
        closeTestApplication(ApplicationProvider.getApplicationContext<FocusApplication>())
    }

    @Test fun allPaletteTextPairsMeetContrastForNormalText() {
        fun contrast(a: Color, b: Color): Float {
            val x = a.luminance(); val y = b.luminance()
            return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
        }
        val failures = mutableListOf<String>()
        ThemeStyle.entries.forEach { style -> listOf(false, true).forEach { dark ->
            val p = palette(style, dark)
            val pairs = listOf("body" to (p.ink to p.paper), "secondary text" to (p.muted to p.card),
                "accent selection" to (p.accent to p.accentSoft), "accent on background" to (p.accent to p.paper),
                "secondary selection" to (p.secondary to p.secondarySoft),
                "button" to ((if (dark) Color(0xFF20251E) else Color.White) to p.accent))
            pairs.forEach { (name, pair) ->
                val ratio = contrast(pair.first, pair.second)
                if (ratio < 4.5f) failures += "$style dark=$dark $name ratio=$ratio"
            }
        } }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun fullscreenRetainsClockBoundsAfterWrappedControlsFadeAndTapRestoresThem() {
        val settings = FocusSettings(uiPreset = UiPreset.FLIP)
        val state = TimerEngine.start(TimerEngine.ready(settings), settings, ClockPoint(1000, 1000))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                FocusTheme(settings) { FullscreenTimer(state, settings, 1000, "阅读与学习", {}, {}, {}, {}) }
            }
        }
        compose.waitForIdle()
        val before = compose.onNodeWithTag("countdown", true).fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(5500)
        ShadowLooper.idleMainLooper(6, TimeUnit.SECONDS)
        compose.mainClock.advanceTimeBy(240)
        compose.waitForIdle()
        assertFalse(compose.onNodeWithTag("fullscreen_controls", true).fetchSemanticsNode().config[FullscreenControlsVisibleKey])
        assertEquals(before, compose.onNodeWithTag("countdown", true).fetchSemanticsNode().boundsInRoot)
        compose.onRoot().performTouchInput { click(Offset(20f, 20f)) }
        compose.mainClock.advanceTimeBy(240)
        assertTrue(compose.onNodeWithTag("fullscreen_controls", true).fetchSemanticsNode().config[FullscreenControlsVisibleKey])
        assertEquals(before, compose.onNodeWithTag("countdown", true).fetchSemanticsNode().boundsInRoot)
    }

    @Test fun touchExplorationKeepsFullscreenControlsVisible() {
        val context = ApplicationProvider.getApplicationContext<FocusApplication>()
        val manager = context.getSystemService(AccessibilityManager::class.java)
        shadowOf(manager).setTouchExplorationEnabled(true)
        val settings = FocusSettings()
        val state = TimerEngine.start(TimerEngine.ready(settings), settings, ClockPoint(1000, 1000))
        compose.setContent { FocusTheme(settings) { FullscreenTimer(state, settings, 1000, "自由专注", {}, {}, {}, {}) } }
        compose.mainClock.advanceTimeBy(7000)
        ShadowLooper.idleMainLooper(7, TimeUnit.SECONDS)
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag("fullscreen_controls", true).fetchSemanticsNode().config[FullscreenControlsVisibleKey])
        compose.onNodeWithTag("exit_fullscreen", true).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w360dp-h800dp-port-mdpi")
    fun longCurrentTaskCanBeReadFullyAtTwoTimesFontSize() {
        val title = "阅读并整理学习笔记，检查每一个步骤，保持自己的节奏。".repeat(6)
        val settings = FocusSettings(uiPreset = UiPreset.MINIMAL)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                FocusTheme(settings) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        FocusScreen(TimerEngine.ready(settings), settings, 0, listOf(TaskEntity(id = "long", title = title)),
                            "long", Statistics.forDay(emptyList(), LocalDate.now()), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, false)
                    }
                }
            }
        }
        compose.onNodeWithTag("current_task").performScrollTo().performClick()
        compose.onNodeWithText("当前任务").assertIsDisplayed()
        val textNodes = compose.onAllNodesWithText(title, useUnmergedTree = true).fetchSemanticsNodes()
        var fullyReadable = false
        compose.runOnIdle {
            textNodes.forEach { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                if (results.any { it.layoutInput.maxLines > 2 && it.lineCount > 2 && !it.didOverflowHeight }) fullyReadable = true
            }
        }
        assertTrue("The detail dialog must contain the entire wrapped task", fullyReadable)
    }

    @Test fun customBackgroundRendersWithoutAnEnclosingFullscreenSurface() {
        val context = ApplicationProvider.getApplicationContext<FocusApplication>()
        val file = File(context.filesDir, "background-12345678-1234-1234-1234-123456789abc.jpg")
        val bitmap = Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }
        canvas.drawRect(0f, 0f, 320f, 400f, paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        var page by mutableIntStateOf(0)
        var settings by mutableStateOf(FocusSettings(uiPreset = UiPreset.MINIMAL, backgroundFile = file.name, backgroundShade = 0.55f))
        compose.setContent { FocusTheme(settings) { PreviewHost(settings, page) } }
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("custom_background_image", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("timer_primary").assertIsDisplayed()
            compose.runOnIdle { settings = settings.copy(themeMode = ThemeMode.DARK); page = 3 }
            compose.onNodeWithTag("fullscreen_content", true).assertIsDisplayed()
            compose.onNodeWithTag("exit_fullscreen", true).assertIsDisplayed()
        } finally { file.delete() }
    }
}
