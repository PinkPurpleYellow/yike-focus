package app.yike.focus.ui

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.yike.focus.BuildConfig
import app.yike.focus.FocusApplication
import app.yike.focus.domain.*
import java.io.File
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk=[34],application=FocusApplication::class,qualifiers="w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ButtonReviewTest {
    @get:Rule val compose = createComposeRule()
    @After fun close() { closeTestApplication(ApplicationProvider.getApplicationContext<FocusApplication>()) }
    @Test fun actualPageActionsInBothMaterials() {
        var config by mutableStateOf(FocusSettings())
        var page by mutableIntStateOf(0)
        compose.setContent { FocusTheme(config) { PreviewHost(config, page) { page = it } } }
        val lines = mutableListOf("材质\t页面\t标签\t文字或朗读名称\t可用\t选中")
        for (material in InterfaceMaterial.entries) {
            for (index in 0..3) {
                compose.runOnIdle { config = config.copy(interfaceMaterial = material); page = index }
                compose.waitForIdle()
                val actions = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
                Assert.assertTrue(actions.isNotEmpty())
                for (node in actions) {
                    val info = node.config
                    val text = info.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
                    val name = info.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                    lines += listOf(material.title, listOf("专注", "统计", "设置", "全屏")[index],
                        info.getOrNull(SemanticsProperties.TestTag).orEmpty(), text ?: name.orEmpty(),
                        (SemanticsProperties.Disabled !in info).toString(), info.getOrNull(SemanticsProperties.Selected)?.toString().orEmpty()).joinToString("\t")
                }
                if (index == 1) {
                    compose.onNodeWithText("前一天").performScrollTo().performClick()
                    compose.onNodeWithText("回到今天").performScrollTo().performClick()
                    compose.onNodeWithText("回到今天").assertDoesNotExist()
                    compose.onNodeWithText("后一天").assertIsNotEnabled()
                }
            }
        }
        val out = File("build/outputs/button-review-${BuildConfig.VERSION_NAME}/actions.tsv")
        requireNotNull(out.parentFile).mkdirs()
        out.writeText(lines.joinToString("\n"))
    }

    @Test fun previewControlsAndSecondaryActions() {
        var glass by mutableStateOf(true)
        var applied = 0
        var dismissed = 0
        compose.setContent {
            val settings = FocusSettings(interfaceMaterial = if (glass) InterfaceMaterial.GLASS else InterfaceMaterial.TRADITIONAL)
            key(glass) { AppearanceEditor(settings, onApply = { applied++ }, onDismiss = { dismissed++ }) }
        }
        for (material in listOf(true,false)) {
            compose.runOnIdle { glass = material }
            compose.onNodeWithTag("preview_page_1").performScrollTo().performClick()
            compose.onNodeWithTag("preview_page_1").assertIsSelected()
            compose.onNodeWithTag("preview_page_0").assertIsNotSelected()
            compose.onNodeWithTag("apply_appearance").assertHasClickAction()
            compose.runOnIdle {
                val root = android.view.inspector.WindowInspector.getGlobalWindowViews().last { it.width > 0 && it.height > 0 }
                val image = Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
                root.draw(android.graphics.Canvas(image))
                val out = File("build/outputs/button-review-${BuildConfig.VERSION_NAME}/editor-$material.png")
                requireNotNull(out.parentFile).mkdirs()
                out.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
            compose.onNodeWithTag("appearance_preset_FLIP").performScrollTo().performClick()
            compose.onNodeWithTag("appearance_preset_FLIP").assertIsSelected()
            compose.onNodeWithTag("apply_appearance").performClick()
            compose.onNodeWithTag("cancel_appearance").performClick()
        }
        Assert.assertEquals(2, applied); Assert.assertEquals(2,dismissed)
    }
}
