package app.yike.focus.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
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
import app.yike.focus.FocusApplication
import app.yike.focus.domain.*
import kotlinx.coroutines.cancel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Checks API linking/compatible drawing, not GPU refraction or device frame rate. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26,31,33], application = FocusApplication::class, qualifiers = "w800dp-h600dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassCompatibilityTest {
    @get:Rule val compose=createComposeRule()
    @After fun cleanup() { closeTestApplication(ApplicationProvider.getApplicationContext<FocusApplication>()) }
    @Test fun materialControlsAndDialogRenderWithoutNewPlatformLinkageErrors() {
        var view: View?=null
        var dialog by mutableStateOf(false)
        val settings=FocusSettings(interfaceMaterial=InterfaceMaterial.GLASS,decorativeMotion=false)
        compose.setContent { val v=LocalView.current; SideEffect { view=v }; FocusTheme(settings) {
            FocusBackdropHost(settings) {
                PaperCard(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("给自己一点专注的时间。")
                    FocusButton({ dialog=true }) { Text("查看") }
                }
                if(dialog) FocusAlertDialog({dialog=false},confirmButton={ FocusTextButton({dialog=false}) { Text("关闭") } },
                    title={Text("一刻专注")},text={Text("清楚地看见每一刻。")})
            }
        } }
        compose.onNodeWithText("给自己一点专注的时间。").assertIsDisplayed()
        compose.runOnIdle {
            val root=requireNotNull(view).rootView
            val image=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            val output=File("build/outputs/2.4.0/compatibility-api${android.os.Build.VERSION.SDK_INT}.png")
            output.parentFile!!.mkdirs(); output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
        }
        compose.onNodeWithText("查看").performClick()
        compose.onNodeWithText("清楚地看见每一刻。").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        assertEquals(if(android.os.Build.VERSION.SDK_INT>=33) 2 else if(android.os.Build.VERSION.SDK_INT>=31) 1 else 0,glassTier(android.os.Build.VERSION.SDK_INT))
    }
}
