package app.yike.focus.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = FocusApplication::class, qualifiers = "w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlipAnimationTest {
    @get:Rule val compose = createComposeRule()
    private var seconds by mutableLongStateOf(1500)
    private var phase by mutableStateOf(Phase.FOCUS)
    private var motion by mutableStateOf(true)
    private var root: View? = null

    @After fun stopWorkers() {
        closeTestApplication(ApplicationProvider.getApplicationContext<FocusApplication>())
    }

    private fun show() {
        compose.setContent {
            val view = LocalView.current
            SideEffect { root = view.rootView }
            FocusTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().padding(120.dp), contentAlignment = Alignment.Center) {
                        SplitFlipClock(seconds, phase, motion, 340.dp, large = true)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun update(value: Long) {
        compose.runOnIdle { seconds = value }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }
    private fun progress() = compose.onNodeWithTag("flip_clock").fetchSemanticsNode().config[FlipProgressKey]
    private fun assertSettled(value: Long) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val config = compose.onNodeWithTag("flip_clock").fetchSemanticsNode().config
        assertEquals(value, config[FlipTargetKey])
        assertEquals(1f, config[FlipProgressKey], 0.001f)
        compose.onNodeWithTag("flip_minutes_flap").assertDoesNotExist()
        compose.onNodeWithTag("flip_seconds_flap").assertDoesNotExist()
    }
    private fun frame(name: String): Int {
        var hash = 0
        compose.runOnIdle {
            val view = requireNotNull(root)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            hash = pixels.contentHashCode()
            val file = File("build/outputs/flip-frames-3.0.1/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        return hash
    }

    @Test fun minuteBoundaryHasTwoDistinctHalfFlapsAndContinuousIntermediateFrames() {
        show()
        val initial = frame("25-00-initial")
        update(1499)
        assertEquals(1500L, compose.onNodeWithTag("flip_clock").fetchSemanticsNode().config[FlipFromKey])
        compose.mainClock.advanceTimeBy(112)
        assertTrue(progress() in 0.1f..0.45f)
        val upperAngle = compose.onNodeWithTag("flip_seconds_flap").fetchSemanticsNode().config[FlipAngleKey]
        assertTrue("Old upper flap must rotate towards the center hinge", upperAngle < -1f && upperAngle > -90f)
        compose.onNodeWithTag("flip_minutes_flap").assertExists()
        val upper = frame("24-59-upper-112ms")
        compose.mainClock.advanceTimeBy(224)
        assertTrue(progress() in 0.55f..0.9f)
        val lowerAngle = compose.onNodeWithTag("flip_seconds_flap").fetchSemanticsNode().config[FlipAngleKey]
        assertTrue("New lower flap must unfold from the center hinge", lowerAngle > 0f && lowerAngle < 90f)
        val lower = frame("24-59-lower-336ms")
        compose.mainClock.advanceTimeBy(224)
        assertSettled(1499)
        val end = frame("24-59-final")
        assertEquals("Frames must contain continuous visual changes", 4, setOf(initial, upper, lower, end).size)
    }

    @Test fun onlyChangedPanelFlipsAndOneMinuteToZeroMinuteEndsCorrectly() {
        seconds = 60
        show()
        update(59)
        compose.mainClock.advanceTimeBy(112)
        compose.onNodeWithTag("flip_minutes_flap").assertExists()
        compose.onNodeWithTag("flip_seconds_flap").assertExists()
        frame("00-59-upper")
        compose.mainClock.advanceTimeBy(500)
        assertSettled(59)
        update(58)
        compose.mainClock.advanceTimeBy(112)
        compose.onNodeWithTag("flip_minutes_flap").assertDoesNotExist()
        compose.onNodeWithTag("flip_seconds_flap").assertExists()
        compose.mainClock.advanceTimeBy(500)
        assertSettled(58)
        update(1) // Jump: no backlog of old flips.
        assertSettled(1)
        update(0)
        compose.mainClock.advanceTimeBy(600)
        assertSettled(0)
        frame("00-00-final")
    }

    @Test fun interruptionPausePhaseChangeAndReducedMotionSettleLatestWithoutReplay() {
        show()
        update(1499)
        compose.mainClock.advanceTimeBy(112)
        update(1498) // Interrupt the current flap instead of queueing another animation.
        assertSettled(1498)
        update(1400)
        assertSettled(1400)
        update(1399)
        compose.mainClock.advanceTimeBy(112)
        compose.runOnIdle { motion = false }
        compose.mainClock.advanceTimeByFrame()
        assertSettled(1399)
        update(1398)
        assertSettled(1398)
        compose.runOnIdle { motion = true }
        compose.mainClock.advanceTimeByFrame()
        assertSettled(1398)
        update(1397)
        compose.mainClock.advanceTimeBy(112)
        assertTrue(progress() < 1f)
        compose.runOnIdle { phase = Phase.SHORT_BREAK; seconds = 300 }
        compose.mainClock.advanceTimeByFrame()
        assertSettled(300)
        compose.mainClock.advanceTimeBy(2000)
        assertSettled(300)
    }

    @Test fun realTimerDisplayStopsFlapsOnPauseAndBackgroundAndResumesWithoutCatchup() {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        var status by mutableStateOf(RunStatus.RUNNING)
        var enabled by mutableStateOf(true)
        seconds = 60
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                FocusTheme(FocusSettings(uiPreset = UiPreset.FLIP, decorativeMotion = enabled)) {
                    TimerDisplay(seconds * 1000, 60_000, phase, status)
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        update(59)
        compose.mainClock.advanceTimeBy(112)
        compose.runOnIdle { status = RunStatus.PAUSED }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1f, compose.onNodeWithTag("flip_clock", true).fetchSemanticsNode().config[FlipProgressKey], 0.001f)
        compose.runOnIdle { status = RunStatus.RUNNING; owner.lifecycle.currentState = Lifecycle.State.STARTED; seconds = 50 }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(50L, compose.onNodeWithTag("flip_clock", true).fetchSemanticsNode().config[FlipTargetKey])
        assertEquals(1f, compose.onNodeWithTag("flip_clock", true).fetchSemanticsNode().config[FlipProgressKey], 0.001f)
        compose.runOnIdle { enabled = false; seconds = 49 }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1f, compose.onNodeWithTag("flip_clock", true).fetchSemanticsNode().config[FlipProgressKey], 0.001f)
    }
}
