package app.yike.focus

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import app.yike.focus.domain.*
import app.yike.focus.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual window pixels: software host screenshots cannot pass this optical acceptance test. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion=33)
class GlassGpuTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun pixels(name:String): Bitmap {
        val latch=CountDownLatch(1);var code=-1
        val window=compose.activity.window
        val bitmap=Bitmap.createBitmap(window.decorView.width,window.decorView.height,Bitmap.Config.ARGB_8888)
        compose.runOnUiThread {PixelCopy.request(window,bitmap,{code=it;latch.countDown()},Handler(Looper.getMainLooper()))}
        assertTrue("PixelCopy timed out",latch.await(8,TimeUnit.SECONDS));assertEquals(PixelCopy.SUCCESS,code)
        val folder=File(compose.activity.getExternalFilesDir(null),"glass-gpu-2.4.0").apply{mkdirs()}
        File(folder,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        return bitmap
    }
    @Test fun stationaryNavigationSamplesTheScrollingPage() {
        compose.activityRule.scenario.onActivity { activity -> activity.setContent {
            val settings = FocusSettings(interfaceMaterial = InterfaceMaterial.GLASS)
            FocusTheme(settings) {
                AppScaffold(settings, 0, java.time.LocalDate.now(), onPage = {}) {
                    repeat(48) { index ->
                        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                            drawRect(if (index % 2 == 0) Color.Red else Color.Blue)
                        }
                    }
                }
            }
        } }
        val bounds = compose.onNodeWithTag("glass_navigation").fetchSemanticsNode().boundsInWindow
        val before = pixels("06-stationary-navigation")
        compose.onNodeWithTag("page_scroll").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {
            it(0f, 140f * compose.activity.resources.displayMetrics.density)
        }
        compose.waitForIdle()
        val after = pixels("07-page-scrolled")
        try {
            val left = (bounds.left + bounds.width * .15f).toInt()
            val right = (bounds.right - bounds.width * .15f).toInt()
            val y = (bounds.top + bounds.height * .25f).toInt()
            val changed = (left until right).count { x ->
                kotlin.math.abs(android.graphics.Color.red(before.getPixel(x,y)) - android.graphics.Color.red(after.getPixel(x,y))) > 20
            }
            assertTrue("Stationary lens must sample newly exposed bands", changed > (right-left)/3)
        } finally { before.recycle(); after.recycle() }
    }

    @Test fun scrolledPhotoCardMatchesAdjacentWallpaperAcrossItsVisibleHeight() {
        val activity = compose.activity
        val file = File(activity.filesDir, "background-${java.util.UUID.randomUUID()}.jpg")
        val fixture = Bitmap.createBitmap(300, 600, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(fixture)
        val paint = android.graphics.Paint()
        for (y in 0 until 600 step 30) {
            paint.color = if (y % 60 == 0) android.graphics.Color.RED else android.graphics.Color.BLUE
            canvas.drawRect(0f, y.toFloat(), 300f, y + 30f, paint)
        }
        file.outputStream().use { fixture.compress(Bitmap.CompressFormat.JPEG, 99, it) }
        fixture.recycle()
        try {
            var loaded by mutableStateOf(false)
            compose.activityRule.scenario.onActivity { it.setContent {
                val settings = FocusSettings(backgroundFile=file.name, interfaceMaterial=InterfaceMaterial.GLASS,
                    glassTransparency=1f, glassBlur=.12f, backgroundShade=.1f)
                FocusTheme(settings) { FocusBackdropHost(settings) {
                    val ready = LocalGlassBackground.current?.fallback != null && LocalFocusBackdrop.current?.bitmap != null
                    SideEffect { loaded = ready }
                    BoxWithConstraints(Modifier.fillMaxSize().testTag("photo_host")) {
                        val h = maxHeight
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("photo_scroll")) {
                            Spacer(Modifier.height(h / 4))
                            Box(Modifier.padding(start=48.dp, end=16.dp).fillMaxWidth().height(h*.7f)
                                .glassSurface(PanelShape).testTag("photo_card"))
                            Spacer(Modifier.height(h))
                        }
                    }
                } }
            } }
            compose.waitUntil(15000) { loaded }
            repeat(2) { pass ->
                val host = compose.onNodeWithTag("photo_host").fetchSemanticsNode().boundsInWindow
                if (pass == 1) compose.onNodeWithTag("photo_scroll").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f,host.height*.31f) }
                compose.waitForIdle()
                val card = compose.onNodeWithTag("photo_card").fetchSemanticsNode().boundsInWindow
                val pixels = pixels("wallpaper-alignment-$pass")
                try {
                    fun difference(x: Int,y: Int):Int { val c=pixels.getPixel(x,y);return android.graphics.Color.red(c)-android.graphics.Color.blue(c) }
                    val count = (card.top.toInt()+16 until card.bottom.toInt()-16 step 4).count { y ->
                        val external = difference((host.left+card.left).toInt()/2, y)
                        kotlin.math.abs(external)>120 && external*difference(card.center.x.toInt(), y)<0
                    }
                    assertTrue("Wallpaper and glass disagree on stripe colour in $count rows", count<=3)
                } finally { pixels.recycle() }
            }
        } finally { file.delete() }
    }
    private fun spacing(bitmap:Bitmap,left:Int,right:Int,y:Int):Float {
        val values=(left until right).map {x -> val c=bitmap.getPixel(x,y);((c shr 16) and 255)+((c shr 8) and 255)+(c and 255)}
        val threshold=(values.min()+values.max())/2
        val crossings=(1 until values.size).filter {values[it-1]<threshold && values[it]>=threshold}
        assertTrue("Not enough grid contrast to measure lens",crossings.size>=3)
        return (crossings.last()-crossings.first()).toFloat()/(crossings.size-1)
    }
    @Test fun hardwareLensMagnifiesGridAndTracksFingerWithoutForegroundBlur() {
        var enabled by mutableStateOf(false)
        var interaction:GlassInteraction?=null
        val settings=FocusSettings(interfaceMaterial=InterfaceMaterial.GLASS,glassBlur=0f,glassTransparency=1f)
        compose.activityRule.scenario.onActivity {activity -> activity.setContent {
            FocusTheme(settings) {
                val layer=rememberGraphicsLayer();val source=remember(layer){GlassSource(layer)}
                val touch=rememberGlassInteraction();SideEffect{interaction=touch}
                CompositionLocalProvider(LocalGlassBackground provides source) {
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                        Canvas(Modifier.fillMaxSize().recordGlassSource(source)) {
                            drawRect(Color.White)
                            val step=20.dp.toPx();var x=0f
                            while(x<size.width) {drawRect(Color.Black,Offset(x,0f),androidx.compose.ui.geometry.Size(step/2,size.height));x+=step}
                        }
                        Box(Modifier.size(320.dp,128.dp).glassTouch(touch)
                            .then(if(enabled) Modifier.glassSurface(ButtonShape,GlassRole.CONTROL,interaction=touch) else Modifier)
                            .testTag("gpu_lens")) {
                            // A sharp foreground fiducial is outside the measurement scan line.
                            Canvas(Modifier.size(10.dp).align(Alignment.TopCenter)) {drawRect(Color.Magenta)}
                        }
                    }
                }
            }
        } }
        compose.waitForIdle()
        val bounds=compose.onNodeWithTag("gpu_lens").fetchSemanticsNode().boundsInWindow
        val left=(bounds.left+bounds.width*.2f).toInt();val right=(bounds.right-bounds.width*.2f).toInt();val y=bounds.center.y.toInt()
        val baseline=pixels("01-grid")
        compose.runOnIdle {enabled=true};compose.waitForIdle()
        val resting=pixels("02-rest")
        compose.onNodeWithTag("gpu_lens").performTouchInput {down(center);advanceEventTime(150)}
        compose.mainClock.advanceTimeBy(160);compose.waitForIdle()
        assertTrue(requireNotNull(interaction).pressed)
        val pressed=pixels("03-pressed")
        val before=spacing(baseline,left,right,y);val after=spacing(pressed,left,right,y)
        assertTrue("Expected observable background magnification, got ${after/before}",after/before>1.06f)
        compose.onNodeWithTag("gpu_lens").performTouchInput {moveBy(Offset(50f,0f),delayMillis=80)}
        compose.waitForIdle();pixels("04-drag").recycle()
        compose.onNodeWithTag("gpu_lens").performTouchInput {cancel()}
        compose.mainClock.advanceTimeBy(300);compose.waitForIdle()
        assertFalse(requireNotNull(interaction).pressed)
        pixels("05-cancel").recycle()
        val px=bounds.center.x.toInt();val py=(bounds.top+4).toInt()
        assertEquals("Foreground fiducial must stay sharp",baseline.getPixel(px,py),pressed.getPixel(px,py))
        baseline.recycle();resting.recycle();pressed.recycle()
    }
}
