package app.yike.focus.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.yike.focus.FocusApplication
import app.yike.focus.MainActivity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk=[34], application=FocusApplication::class, qualifiers="w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConsentTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=androidx.test.core.app.ApplicationProvider.getApplicationContext<FocusApplication>()
    @After fun close() { closeTestApplication(app) }
    private fun gate() {
        runBlocking { app.repository.setLegalConsentVersion(0) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("legal_agree").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("timer_primary").assertDoesNotExist()
    }
    @Test fun readAgreementThenAgreeAndRecreate() {
        gate()
        compose.runOnIdle {
            val root = compose.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            val file=File("build/outputs/legal-3.0.0/welcome.png")
            requireNotNull(file.parentFile).mkdirs()
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) };bmp.recycle()
        }
        for(title in LegalDocuments.titles.values) {
            compose.onNodeWithText("《一刻专注$title》").performScrollTo().performClick()
            compose.onNodeWithText("返回说明").performClick()
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("legal_agree").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("timer_primary").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("timer_primary").fetchSemanticsNodes().isNotEmpty() }
        Assert.assertEquals(1,runBlocking { app.repository.legalConsentVersion.first() })
    }
    @Test fun decliningDoesNotRecordConsent() {
        gate();compose.onNodeWithText("不同意并退出").performScrollTo().performClick()
        Assert.assertEquals(0, runBlocking { app.repository.legalConsentVersion.first() })
    }
}
