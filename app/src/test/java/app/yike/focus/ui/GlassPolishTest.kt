package app.yike.focus.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
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
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk=[34],application=FocusApplication::class,qualifiers="w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassPolishTest {
    @get:Rule val compose=createComposeRule()
    @After fun cleanup() { closeTestApplication(ApplicationProvider.getApplicationContext<FocusApplication>()) }
    private fun capture(view:View,name:String): Bitmap {
        lateinit var bitmap:Bitmap
        compose.runOnIdle {
            val root=view.rootView
            bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val file=File("build/outputs/2.4.0/polish-$name.png");file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        return bitmap
    }
    private fun assertSamePixels(a:Bitmap,b:Bitmap) {
        val x=IntArray(a.width*a.height);val y=IntArray(x.size)
        a.getPixels(x,0,a.width,0,0,a.width,a.height);b.getPixels(y,0,b.width,0,0,b.width,b.height)
        assertEquals("A pointer press painted an unexpected overlay",0,x.indices.count { x[it]!=y[it] })
        a.recycle();b.recycle()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun allThemesRemovePointerSlabsWithoutRemovingSemanticActions() {
        var settings by mutableStateOf(FocusSettings(decorativeMotion=false))
        var view:View?=null;var checked by mutableStateOf(false)
        var indication:Any?=null;var ripple:Any?=Any()
        compose.setContent { val v=LocalView.current;SideEffect { view=v };FocusTheme(settings) {
            val i=LocalIndication.current;val r=LocalRippleConfiguration.current
            SideEffect { indication=i;ripple=r }
            FocusBackdropHost(settings) {
                PaperCard(Modifier.fillMaxWidth().padding(24.dp)) {
                    SwitchRow("振动提醒","仅在设备具有振动功能时生效",checked) {checked=it}
                    FocusButton({},Modifier.testTag("press_button")) {Text("开始专注")}
                }
            }
        } }
        for(material in InterfaceMaterial.entries) {
            compose.runOnIdle { settings=settings.copy(interfaceMaterial=material) }
            assertTrue(indication is FocusIndication);assertNull(ripple)
            for(tag in listOf("switch_row_振动提醒","press_button")) {
                compose.onNodeWithTag(tag).assertHasClickAction()
                val before=capture(requireNotNull(view),"$material-$tag-before")
                compose.onNodeWithTag(tag).performTouchInput { down(Offset(25f,centerY));advanceEventTime(250) }
                compose.mainClock.advanceTimeBy(300)
                val during=capture(requireNotNull(view),"$material-$tag-held")
                compose.onNodeWithTag(tag).performTouchInput { cancel() }
                assertSamePixels(before,during)
            }
        }
    }

    @Test fun navigationPressHasNoRectangleAndCancelKeepsPage() {
        var page by mutableIntStateOf(1);var view:View?=null
        val settings=FocusSettings(interfaceMaterial=InterfaceMaterial.GLASS,decorativeMotion=false)
        compose.setContent { val v=LocalView.current;SideEffect {view=v};FocusTheme(settings) {
            AppScaffold(settings,page,java.time.LocalDate.of(2026,11,12),onPage={page=it}) {Text("每一刻，都算数。")}
        } }
        val before=capture(requireNotNull(view),"nav-before")
        compose.onNodeWithTag("nav_1").performTouchInput { down(center);advanceEventTime(250) }
        compose.mainClock.advanceTimeBy(300)
        val during=capture(requireNotNull(view),"nav-held")
        compose.onNodeWithTag("nav_1").performTouchInput {cancel()}
        assertSamePixels(before,during);assertEquals(1,page)
        compose.onNodeWithTag("nav_1").assertIsSelected()
    }

    @Test fun floatingNavigationDoesNotClipOrMaskTheScrollablePage() {
        var view:View?=null;var clicked=false
        val settings=FocusSettings(interfaceMaterial=InterfaceMaterial.GLASS,decorativeMotion=false)
        compose.setContent {val v=LocalView.current;SideEffect{view=v};FocusTheme(settings) {
            AppScaffold(settings,1,java.time.LocalDate.of(2026,11,12),onPage={}) {
                Box(Modifier.fillMaxWidth().height(1100.dp).background(Color(0xFF925A39)))
                FocusButton({clicked=true},Modifier.testTag("last_action")) {Text("最后一条记录")}
            }
        } }
        val nav=compose.onNodeWithTag("glass_navigation").fetchSemanticsNode().boundsInRoot
        val image=capture(requireNotNull(view),"navigation-over-content")
        // Outside the floating capsule, the card must remain visible to the window bottom.
        assertEquals(android.graphics.Color.rgb(146,90,57),image.getPixel(40,nav.center.y.toInt()))
        image.recycle()
        compose.onNodeWithTag("last_action").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("page_scroll").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {it(0f,2000f)}
        val last=compose.onNodeWithTag("last_action").fetchSemanticsNode().boundsInRoot
        assertTrue("The final action must scroll clear of the floating navigation",last.bottom<=nav.top)
        compose.onNodeWithTag("last_action").performClick();assertTrue(clicked)
    }

    @Test fun glassSwitchAndSliderSupportTouchAndAccessibilityValues() {
        var checked by mutableStateOf(false);var value by mutableFloatStateOf(.25f)
        val settings=FocusSettings(interfaceMaterial=InterfaceMaterial.GLASS,decorativeMotion=false)
        compose.setContent {FocusTheme(settings) {FocusBackdropHost(settings) {
            Column(Modifier.fillMaxWidth().padding(32.dp)) {
                FocusSwitch(checked,{checked=it},Modifier.testTag("toggle"))
                FocusSlider(value,{value=it},Modifier.testTag("slider"))
                FocusCheckbox(checked,{checked=it},Modifier.testTag("check"))
            }
        } } }
        compose.onNodeWithTag("toggle").performClick().assertIsOn()
        compose.onNodeWithTag("toggle").performTouchInput {swipe(Offset(width*.8f,centerY),Offset(width*.1f,centerY))}
        compose.onNodeWithTag("toggle").assertIsOff()
        compose.onNodeWithTag("slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(.8f)}
        assertEquals(.8f,value,.01f)
        compose.onNodeWithTag("check").performClick().assertIsOn()
    }

    @Test fun gesturesDriveLensContinuouslyAndResetOnCancel() {
        var touch:GlassInteraction?=null;var view:View?=null
        val settings=FocusSettings(interfaceMaterial=InterfaceMaterial.GLASS)
        compose.setContent {val v=LocalView.current;SideEffect{view=v};FocusTheme(settings) {FocusBackdropHost(settings) {
            val state=rememberGlassInteraction();SideEffect {touch=state}
            Box(Modifier.padding(60.dp).size(320.dp,96.dp).glassTouch(state)
                .glassSurface(ButtonShape,GlassRole.CONTROL,interaction=state).testTag("lens"))
        } } }
        compose.mainClock.autoAdvance=false
        capture(requireNotNull(view),"lens-rest").recycle()
        compose.onNodeWithTag("lens").performTouchInput {down(Offset(width*.25f,centerY))}
        compose.waitForIdle(); compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(144); compose.waitForIdle()
        assertTrue(requireNotNull(touch).pressed)
        val progress=compose.onNodeWithTag("lens").fetchSemanticsNode().config[GlassPressKey]
        assertTrue("progress=$progress animator=${android.animation.ValueAnimator.areAnimatorsEnabled()}",progress>.8f)
        capture(requireNotNull(view),"lens-pressed").recycle()
        compose.onNodeWithTag("lens").performTouchInput {moveTo(Offset(width*.75f,centerY),delayMillis=80)}
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
        assertTrue(requireNotNull(touch).position.x>.6f)
        capture(requireNotNull(view),"lens-dragged").recycle()
        compose.onNodeWithTag("lens").performTouchInput {cancel()}
        compose.waitForIdle(); compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(272); compose.waitForIdle()
        assertFalse(requireNotNull(touch).pressed)
        assertEquals(Offset.Zero,requireNotNull(touch).velocity)
        assertEquals(0f,compose.onNodeWithTag("lens").fetchSemanticsNode().config[GlassPressKey],.001f)
        capture(requireNotNull(view),"lens-cancelled").recycle()
    }

    @Test fun glassWallpaperIsSharedAcrossAllPagesWithoutDecodingAgain() {
        val app=ApplicationProvider.getApplicationContext<FocusApplication>()
        val file=File(app.filesDir,"background-33333333-3333-3333-3333-333333333333.jpg")
        val image=Bitmap.createBitmap(1280,800,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image);canvas.drawColor(android.graphics.Color.rgb(78,116,132))
        val paint=android.graphics.Paint().apply {color=android.graphics.Color.rgb(218,176,132)}
        for(i in 0..12) canvas.drawCircle(i*110f,(i*139%800).toFloat(),90f,paint)
        file.outputStream().use {image.compress(Bitmap.CompressFormat.JPEG,95,it)};image.recycle()
        val settings=FocusSettings(interfaceMaterial=InterfaceMaterial.GLASS,backgroundFile=file.name,decorativeMotion=false)
        var page by mutableIntStateOf(0);var decoded:androidx.compose.ui.graphics.ImageBitmap?=null;var view:View?=null;var frostReady=false
        compose.setContent {val v=LocalView.current;SideEffect{view=v};FocusTheme(settings) {
            FocusBackdropHost(settings) {
                val context=LocalFocusBackdrop.current;val frost=LocalGlassBackground.current?.fallback;SideEffect {decoded=context?.bitmap;frostReady=frost!=null}
                PreviewHost(settings,page) {page=it}
            }
        } }
        compose.waitUntil(15_000) {ShadowLooper.idleMainLooper();decoded!=null && frostReady}
        val original=decoded
        for(i in 0..3) {
            compose.runOnIdle {page=i}
            assertTrue(compose.onAllNodesWithTag("custom_background_image").fetchSemanticsNodes().any { it.boundsInRoot.width>=1279f })
            assertSame(original,decoded)
            capture(requireNotNull(view),"wallpaper-page$i").recycle()
        }
    }

    @Test fun cachedFrostPreservesSolidBackgroundColor() {
        val bitmap=Bitmap.createBitmap(1280,800,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(78,116,132))
        val blurred=cachedFrost(bitmap.asImageBitmap(),.6f).asAndroidBitmap()
        assertEquals(android.graphics.Color.rgb(78,116,132),blurred.getPixel(128,80))
        bitmap.recycle();blurred.recycle()
    }

    @Test fun hostSkiaShaderGraphCompilesWithActualCornersAndDynamicUniforms() {
        // Robolectric's bundled Skia uses sample(child,p); Android AGSL uses child.eval(p).
        // Only the child-sampling spelling changes; all optical math and uniform handling is tested.
        val optics=GlassOptics(4f,GlassOptics.SOURCE.replace("scene.eval(","sample(scene,"))
        for(shape in listOf(floatArrayOf(26f,26f,26f,26f),floatArrayOf(48f,48f,48f,48f),floatArrayOf(0f,8f,16f,24f))) {
            for(progress in listOf(0f,.25f,.5f,1f,0f)) {
                assertNotNull(optics.configure(320f,96f,40f,shape,1f,GlassRole.CONTROL,progress,.75f,.5f,.2f,0f,false))
            }
        }
    }

    @Test fun opticalReferenceShowsContinuousMagnificationAndRoundedEdges() {
        // CPU reference only. Robolectric cannot draw RuntimeShader on its software Canvas.
        // The real Android shader is compiled/configured above, and PixelCopy is tested on-device.
        val input=Bitmap.createBitmap(400,208,Bitmap.Config.ARGB_8888)
        val inputCanvas=Canvas(input);inputCanvas.drawColor(android.graphics.Color.WHITE)
        val paint=android.graphics.Paint().apply {color=android.graphics.Color.BLACK}
        for(x in 0..399 step 20) inputCanvas.drawRect(x.toFloat(),0f,x+10f,208f,paint)
        val frames=mutableListOf<Bitmap>()
        for((index,progress) in listOf(0f,.25f,.5f,.75f,1f).withIndex()) {
            val output=input.copy(Bitmap.Config.ARGB_8888,true)
            val depth=7f+progress*9f;val magnification=1.035f+progress*.085f
            val centerX=160f+((.25f+progress*.5f)-.5f)*320f*.1f*progress
            for(y in 40 until 168) for(x in 40 until 360) {
                val cx=x+0.5f-200f;val cy=y+0.5f-104f
                val qx=kotlin.math.abs(cx)-134f;val qy=kotlin.math.abs(cy)-38f
                val ox=maxOf(qx,0f);val oy=maxOf(qy,0f)
                val length=kotlin.math.sqrt(ox*ox+oy*oy)
                val sd=length+minOf(maxOf(qx,qy),0f)-26f
                if(sd>0f) continue
                val edge=(1f+sd/18f).coerceIn(0f,1f)
                val bend=(1f-kotlin.math.sqrt(maxOf(0f,1f-edge*edge)))*depth
                val nx=kotlin.math.sign(cx)*(if(maxOf(qx,qy)>.001f) ox/maxOf(length,.001f) else if(qx>qy) 1f else 0f)
                val ny=kotlin.math.sign(cy)*(if(maxOf(qx,qy)>.001f) oy/maxOf(length,.001f) else if(qy>=qx) 1f else 0f)
                val sx=centerX+((x+.5f-40f)-centerX)/magnification-nx*bend-.5f*depth*.12f*progress*edge+40f
                val sy=64f+((y+.5f-40f)-64f)/magnification-ny*bend+40f
                output.setPixel(x,y,input.getPixel(sx.toInt().coerceIn(0,399),sy.toInt().coerceIn(0,207)))
            }
            val file=File("build/outputs/2.4.0/polish-optics-$index.png");file.parentFile!!.mkdirs()
            file.outputStream().use {output.compress(Bitmap.CompressFormat.PNG,100,it)}
            frames+=output
        }
        fun spacing(bitmap:Bitmap):Float {
            val values=(110 until 290).map {x -> (bitmap.getPixel(x,104) shr 16) and 255}
            val crosses=(1 until values.size).filter {values[it-1]<128 && values[it]>=128}
            assertTrue(crosses.size>=3)
            return (crosses.last()-crosses.first()).toFloat()/(crosses.size-1)
        }
        val ratio=spacing(frames.last())/spacing(frames.first())
        assertTrue("The optical reference must magnify the input grid: $ratio",ratio>1.05f)
        File("build/outputs/2.4.0/polish-optics.json").writeText("{\"restToPressedGridSpacingRatio\":$ratio,\"renderer\":\"CPU optical reference; shader compile/uniform checks are separate; not Android GPU\"}")
        frames.forEach {it.recycle()};input.recycle()

    }
}
