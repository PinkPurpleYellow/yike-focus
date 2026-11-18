package app.yike.focus.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
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
class LayoutMatrixTest {
    @get:Rule val compose = createComposeRule()
    @After fun stopWorkers() {
        closeTestApplication(ApplicationProvider.getApplicationContext<FocusApplication>())
    }

    private fun assertTextFitsHorizontally() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
        compose.runOnIdle {
            nodes.forEach { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                layouts.forEach { result ->
                    // Paragraphs retain the maximum layout constraint; compare painted lines with the text node.
                    val inkWidth = (0 until result.lineCount).maxOfOrNull { result.getLineRight(it) - result.getLineLeft(it) } ?: 0f
                    assertTrue("Text overflows horizontally: ${result.layoutInput.text} ($inkWidth > ${result.size.width})",
                        inkWidth <= result.size.width + 1f)
                }
            }
        }
    }
    private fun snapshot(view: View, name: String) {
        compose.runOnIdle {
            val root = view.rootView
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            val file = File("build/outputs/layout-matrix-3.0.0/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }
    private fun matrix(name: String, baseline: Boolean = false) {
        var settings by mutableStateOf(FocusSettings())
        var scale by mutableFloatStateOf(1f)
        var page by mutableIntStateOf(0)
        var view: View? = null
        compose.setContent {
            val current = LocalView.current
            SideEffect { view = current }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                FocusTheme(settings) { PreviewHost(settings, page) { page = it } }
            }
        }
        listOf(1f, 1.3f, 1.6f, 2f).forEachIndexed { i, font ->
            UiPreset.entries.forEach { preset ->
                compose.runOnIdle {
                    scale = font
                    settings = settings.copy(uiPreset = preset,
                        uiDensity = if (i % 2 == 0) UiDensity.COMFORTABLE else UiDensity.COMPACT,
                        themeStyle = ThemeStyle.entries[(i + preset.ordinal) % ThemeStyle.entries.size],
                        themeMode = if (i % 2 == 0) ThemeMode.LIGHT else ThemeMode.DARK)
                    page = 0
                }
                compose.waitForIdle()
                if (baseline && font == 1f) {
                    compose.onNodeWithTag("page_header").performScrollTo()
                    compose.onNodeWithTag("timer_primary").assertIsDisplayed()
                    val bounds = compose.onNodeWithTag("timer_primary").fetchSemanticsNode().boundsInRoot
                    val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                    assertTrue("Primary action must be entirely above the fold", bounds.top >= root.top && bounds.bottom <= root.bottom)
                }
                compose.onNodeWithTag("timer_primary").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("nav_2").assertIsDisplayed()
                assertTextFitsHorizontally()
                snapshot(requireNotNull(view), "$name-${preset.name}-font$font-${settings.uiDensity.name}")
                compose.onNodeWithTag("add_task").performScrollTo().assertIsDisplayed()
            }
        }
        // Large text in secondary pages, including the full about email.
        compose.runOnIdle { scale = 2f; settings = settings.copy(uiPreset = UiPreset.CLASSIC); page = 1 }
        compose.waitForIdle()
        assertTextFitsHorizontally()
        snapshot(requireNotNull(view), "$name-statistics-font2")
        compose.onNodeWithTag("nav_2").performClick()
        compose.onNodeWithTag("settings_section_5").performScrollTo().performClick()
        compose.onNodeWithTag("developer_email").performScrollTo().assertTextEquals("ff1921570609@outlook.com")
        assertTextFitsHorizontally()
        snapshot(requireNotNull(view), "$name-about-font2")
        compose.runOnIdle { page = 3; settings = settings.copy(uiPreset = UiPreset.FLIP) }
        compose.onNodeWithTag("exit_fullscreen", true).performScrollTo().assertIsDisplayed()
        assertTextFitsHorizontally()
        snapshot(requireNotNull(view), "$name-fullscreen-font2")
    }

    @Test fun landscape1280x800() = matrix("1280x800", baseline = true)
    @Test @Config(qualifiers = "w360dp-h800dp-port-mdpi") fun narrow360x800() = matrix("360x800")
    @Test @Config(qualifiers = "w600dp-h480dp-land-mdpi") fun short600x480() = matrix("600x480")
    @Test @Config(qualifiers = "w800dp-h1280dp-port-mdpi") fun portrait800x1280() = matrix("800x1280")
    @Test @Config(qualifiers = "w1600dp-h1000dp-land-mdpi") fun large1600x1000() = matrix("1600x1000")
}
