package app.yike.focus.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import app.yike.focus.FocusApplication
import app.yike.focus.data.TaskEntity
import app.yike.focus.domain.*
import app.yike.focus.platform.TimerAction
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout

/** Host-side Android/Compose rendering. This does not substitute for device/background tests. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = FocusApplication::class, qualifiers = "w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppearanceUiTest {
    @get:Rule val compose = createComposeRule()
    @After fun stopTestApplicationWorkers() {
        // A new Application is created per Robolectric test. Cancel workers owned by the old one.
        closeTestApplication(ApplicationProvider.getApplicationContext<FocusApplication>())
    }

    @Test fun allPresetsRenderFocusStatisticsSettingsAndFullscreenInBothModes() {
        var settings by mutableStateOf(FocusSettings())
        var page by mutableIntStateOf(0)
        var hostView: View? = null
        compose.setContent {
            val view = LocalView.current
            SideEffect { hostView = view }
            FocusTheme(settings) { PreviewHost(settings, page) }
        }
        UiPreset.entries.forEach { preset ->
            listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
                for (index in 0..3) {
                    compose.runOnIdle { settings = settings.copy(uiPreset = preset, themeMode = mode); page = index }
                    compose.waitForIdle()
                    if (index == 0 || index == 3) compose.onNodeWithTag("countdown", useUnmergedTree = true).assertExists()
                    compose.runOnIdle {
                        val root = requireNotNull(hostView).rootView
                        val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                        root.draw(Canvas(image))
                        val file = File("build/outputs/appearance-previews-3.0.1/${preset.name}-${mode.name}-page$index.png")
                        requireNotNull(file.parentFile).mkdirs()
                        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        val pixels = (0 until image.width step 8).flatMap { x -> (0 until image.height step 8).map { y -> image.getPixel(x, y) } }.distinct()
                        assertTrue("Rendered image must contain text/graphics", pixels.size > 10)
                        image.recycle()
                    }
                }
            }
        }
    }

    @Test fun previewCancelDoesNotSaveAndApplyKeepsNonAppearancePreferences() {
        val original = FocusSettings(focusMinutes = 37, ambient = Ambient.RAIN, backgroundFile = "background-existing.jpg")
        var saved = original
        var open by mutableStateOf(true)
        compose.setContent { FocusTheme(saved) {
            if (open) AppearanceEditor(saved, { saved = it; open = false }, { open = false })
            else Button(onClick = { open = true }) { Text("重新选择") }
        } }
        compose.onNodeWithTag("appearance_preset_FLIP").performScrollTo().performClick()
        assertEquals(original, saved)
        compose.onNodeWithTag("cancel_appearance").performClick()
        assertEquals(original, saved)
        compose.onNodeWithText("重新选择").performClick()
        compose.onNodeWithTag("appearance_preset_JOURNAL").performScrollTo().performClick()
        compose.onNodeWithTag("apply_appearance").performClick()
        assertEquals(UiPreset.JOURNAL, saved.uiPreset)
        assertEquals(37, saved.focusMinutes)
        assertEquals(Ambient.RAIN, saved.ambient)
        assertEquals(original.backgroundFile, saved.backgroundFile)
    }

    @Test @Config(qualifiers = "w360dp-h800dp-port-mdpi")
    fun compactWindowAndLargeTextCanReachTimerControlsInEveryPreset() {
        var settings by mutableStateOf(FocusSettings())
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                FocusTheme(settings) { PreviewHost(settings, 0) }
            }
        }
        UiPreset.entries.forEach { preset ->
            compose.runOnIdle { settings = settings.copy(uiPreset = preset, uiDensity = UiDensity.COMPACT) }
            compose.onNodeWithTag("timer_primary").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("add_task").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun aboutShowsOnlyPublicVersionAndKeepsDeveloperContact() {
        compose.setContent { FocusTheme { PreviewHost(FocusSettings(), 2) } }
        compose.onNodeWithTag("settings_section_5").performScrollTo().performClick()
        compose.onNodeWithTag("app_version").performScrollTo().assertTextEquals("版本 ${app.yike.focus.BuildConfig.VERSION_NAME}")
        compose.onNodeWithTag("developer_name").performScrollTo().assertTextEquals("PinkPurpleYellow")
        compose.onNodeWithTag("developer_email").performScrollTo().assertTextEquals("ff1921570609@outlook.com")
    }

    @Test fun appearancePreferencesPersistWithoutOverwritingPausedTimerSnapshot() {
        val app = ApplicationProvider.getApplicationContext<FocusApplication>()
        compose.setContent { Text("外观持久化验证") }
        val controller = compose.runOnIdle { app.controller }
        compose.waitUntil(10_000) { ShadowLooper.idleMainLooper(); controller.loaded.value }
        val original = storage { app.repository.currentSettings() }
        storage { app.controller.handle(TimerAction.END); app.controller.handle(TimerAction.START); app.controller.handle(TimerAction.PAUSE) }
        val paused = app.controller.state.value
        try {
            UiPreset.entries.forEach { preset ->
                val expected = original.copy(uiPreset = preset, timerFace = TimerFace.FLIP,
                    uiDensity = UiDensity.COMPACT, decorativeMotion = false)
                storage { app.repository.updateSettings { expected } }
                compose.waitUntil(5_000) { ShadowLooper.idleMainLooper(); app.controller.settings.value.uiPreset == preset }
                assertEquals(expected, storage { app.repository.currentSettings() })
                assertEquals(paused, app.controller.state.value)
                assertEquals(paused, storage { app.repository.readSnapshot() })
            }
        } finally {
            storage { app.controller.handle(TimerAction.END); app.repository.updateSettings { original } }
        }
    }

    // Robolectric pauses the Android main looper. Keep it moving while DataStore/Room use IO.
    private fun <T> storage(block: suspend () -> T): T {
        val scope = CoroutineScope(Dispatchers.IO)
        val result = scope.async { withTimeout(10_000) { block() } }
        try {
            compose.waitUntil(15_000) { ShadowLooper.idleMainLooper(); result.isCompleted }
            return runBlocking { result.await() }
        } finally { scope.cancel() }
    }
}

@Composable
internal fun PreviewHost(settings: FocusSettings, page: Int, onPage: (Int) -> Unit = {}) {
    val today = LocalDate.now()
    val records = remember { (0L..6L).map { i ->
        val start = today.minusDays(i).atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = start + (25 + i * 5) * 60_000
        FocusRecord("sample-$i", null, "阅读第三章，整理今天的学习笔记", start, end, end - start, true, listOf(FocusSpan(start, end)))
    } }
    val stats = Statistics.forDay(records, today)
    val state = TimerEngine.ready(settings)
    val tasks = listOf(TaskEntity(id = "demo", title = "阅读第三章，整理今天的学习笔记"))
        FocusBackdropHost(settings, enabled = settings.interfaceMaterial == InterfaceMaterial.GLASS || page == 0 || page == 3) {
        if (page == 3) FullscreenTimer(state, settings, 0, tasks.first().title, {}, {}, {}, {})
        else AppScaffold(settings, page, today, onPage = onPage) {
            when (page) {
                0 -> FocusScreen(state, settings, 0, tasks, "demo", stats, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, false)
                1 -> StatsScreen(records, null, today, stats, 8)
                else -> SettingsScreen(settings) { }
            }
        }
        }
}
