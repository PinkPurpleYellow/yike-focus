package app.yike.focus.ui

import android.graphics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import app.yike.focus.MainActivity
import app.yike.focus.FocusApplication
import app.yike.focus.domain.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** The unchanged production Activity from this release, rendered on the host with identical input. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = FocusApplication::class, qualifiers = "w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperComparisonTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<FocusApplication>()
    @After fun stopWorkers() { closeTestApplication(app) }
    private fun storage(block: suspend () -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO)
        val result = scope.async { withTimeout(10_000) { block() } }
        try {
            compose.waitUntil(15_000) { ShadowLooper.idleMainLooper(); result.isCompleted }
            runBlocking { result.await() }
        } finally { scope.cancel() }
    }
    private fun snapshot(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val root = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val file = File("build/outputs/comparison-3.0.1/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun waitForWallpaper() {
        compose.waitUntil(10_000) {
            ShadowLooper.idleMainLooper()
            var loaded = false
            compose.runOnIdle {
                val root = compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                loaded = android.graphics.Color.red(bitmap.getPixel(root.width - 6, root.height / 2)) < 220
                bitmap.recycle()
            }
            loaded
        }
    }
    @Test fun actualActivityWithIdenticalWallpaper() {
        storage { app.repository.setLegalConsentVersion(1) }
        val controller = compose.runOnIdle { app.controller }
        compose.waitUntil(10_000) { ShadowLooper.idleMainLooper(); controller.loaded.value }
        val file = File(app.filesDir, "background-11111111-1111-1111-1111-111111111111.jpg")
        val bitmap = Bitmap.createBitmap(1280, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f,0f,1280f,800f,
            intArrayOf(0xFF0C2534.toInt(),0xFF6C9A94.toInt(),0xFFB9906A.toInt()),null,Shader.TileMode.CLAMP) }
        canvas.drawRect(0f,0f,1280f,800f,paint)
        paint.shader = null; paint.color = 0xFF244C52.toInt()
        canvas.drawPath(Path().apply { moveTo(0f,800f); lineTo(0f,540f); cubicTo(180f,320f,300f,630f,630f,400f); cubicTo(900f,190f,1100f,420f,1280f,240f); lineTo(1280f,800f); close() },paint)
        paint.color = 0xFF527C73.toInt()
        canvas.drawPath(Path().apply { moveTo(0f,800f); lineTo(0f,650f); cubicTo(450f,320f,650f,720f,1280f,470f); lineTo(1280f,800f); close() },paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,95,it) }; bitmap.recycle()
        storage { app.repository.updateSettings { it.copy(backgroundFile = file.name, backgroundShade = 0.65f,
            themeMode = ThemeMode.LIGHT, themeStyle = ThemeStyle.TOMATO, uiPreset = UiPreset.CLASSIC,
            timerFace = TimerFace.RING, decorativeMotion = false) } }
        waitForWallpaper()
        compose.onNodeWithTag("timer_primary").assertExists()
        snapshot("focus")
        compose.onNodeWithTag("enter_fullscreen").performScrollTo().performClick()
        compose.onNodeWithTag("exit_fullscreen",true).assertIsDisplayed()
        waitForWallpaper(); snapshot("fullscreen")
        compose.onNodeWithTag("exit_fullscreen",true).performClick()
        compose.onNodeWithTag("timer_primary").assertExists()
        file.delete()
    }
}
