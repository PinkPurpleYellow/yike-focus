package app.yike.focus

import android.graphics.Bitmap
import android.os.*
import android.view.FrameMetrics
import android.view.PixelCopy
import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import app.yike.focus.domain.*
import app.yike.focus.ui.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion=33)
class GlassMotionDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun pixels():Bitmap {
        val w=compose.activity.window
        val bitmap=Bitmap.createBitmap(w.decorView.width,w.decorView.height,Bitmap.Config.ARGB_8888)
        val latch=CountDownLatch(1);var result=-1
        compose.runOnUiThread {PixelCopy.request(w,bitmap,{result=it;latch.countDown()},Handler(Looper.getMainLooper()))}
        assertTrue(latch.await(8,TimeUnit.SECONDS));assertEquals(PixelCopy.SUCCESS,result);return bitmap
    }
    @Test fun bothIconsAndLabelsAreRefractedByTheMovingLens() {
        var position by mutableFloatStateOf(-10f);var touch:GlassInteraction?=null
        compose.activityRule.scenario.onActivity {it.setContent {
            FocusTheme(FocusSettings(interfaceMaterial=InterfaceMaterial.GLASS)) {
                val interaction=rememberGlassInteraction();SideEffect {touch=interaction}
                Box(Modifier.fillMaxSize().background(Color.White),contentAlignment=Alignment.Center) {
                    Row(Modifier.size(440.dp,76.dp).navigationContentLens({position},false,interaction).testTag("foreground_lens")) {
                        repeat(3) {i -> Column(Modifier.weight(1f).fillMaxHeight(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            Glyph(listOf("focus","stats","settings")[i],Modifier.size(24.dp),Color.Black)
                            Text(listOf("专注","统计","设置")[i],color=Color.Black)
                        } }
                    }
                }
            }
        } }
        compose.waitForIdle();val before=pixels()
        val bounds=compose.onNodeWithTag("foreground_lens").fetchSemanticsNode().boundsInWindow
        compose.runOnIdle {position=0f;touch!!.pressed=true}
        compose.mainClock.advanceTimeBy(300);compose.waitForIdle();val after=pixels()
        fun difference(top:Float,bottom:Float):Int {
            var n=0
            for(y in top.toInt() until bottom.toInt()) for(x in bounds.left.toInt() until (bounds.left+bounds.width/3).toInt())
                if(before.getPixel(x,y)!=after.getPixel(x,y)) n++
            return n
        }
        assertTrue("Icon must participate in refraction",difference(bounds.top,bounds.center.y)>10)
        assertTrue("Label must participate in refraction",difference(bounds.center.y,bounds.bottom)>10)
        before.recycle();after.recycle()
    }
    @Test fun continuousNavigationDragRecordsActualFrameTimesAndSharedSceneReuse() {
        var page by mutableIntStateOf(0)
        compose.activityRule.scenario.onActivity {it.setContent {
            val settings=FocusSettings(interfaceMaterial=InterfaceMaterial.GLASS)
            FocusTheme(settings) {AppScaffold(settings,page,java.time.LocalDate.of(2026,11,12),onPage={page=it}) {
                repeat(8) {PaperCard(Modifier.fillMaxWidth()) {Text("把注意力，留给这一刻。");Spacer(Modifier.height(36.dp))}}
            } }
        } }
        compose.waitForIdle();SystemClock.sleep(500)
        val thread=HandlerThread("glass-frame-metrics").apply {start()}
        val samples=Collections.synchronizedList(mutableListOf<Long>())
        val listener=Window.OnFrameMetricsAvailableListener {_,metrics,_ ->samples.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))}
        val window=compose.activity.window
        compose.runOnUiThread {window.addOnFrameMetricsAvailableListener(listener,Handler(thread.looper))}
        compose.runOnIdle {GlassDiagnostics.reset()}
        try {
            compose.onNodeWithTag("glass_navigation").performTouchInput {down(Offset(width*.16f,centerY));moveBy(Offset(20f,0f))}
            compose.waitForIdle();val before=GlassDiagnostics.sourceRecords
            repeat(120) {i ->
                val direction=if((i/30)%2==0) 1f else -1f
                compose.onNodeWithTag("glass_navigation").performTouchInput {moveBy(Offset(4f*direction,0f),delayMillis=16)}
                compose.waitForIdle();SystemClock.sleep(16)
            }
            val sceneRefreshes=GlassDiagnostics.sourceRecords-before
            compose.onNodeWithTag("glass_navigation").performTouchInput {cancel()}
            val sorted=synchronized(samples) {samples.sorted()}
            assertTrue("No actual frames collected",sorted.size>=30)
            assertTrue("Dragging a static page should reuse the scene, refreshes=$sceneRefreshes",sceneRefreshes<=8)
            val refresh=compose.activity.display?.refreshRate ?: 60f;val budget=1_000_000_000.0/refresh
            val json=JSONObject().put("device",Build.MODEL).put("android",Build.VERSION.SDK_INT).put("refreshHz",refresh)
                .put("frames",sorted.size).put("p50Ms",sorted[sorted.size/2]/1e6).put("p95Ms",sorted[(sorted.size*.95).toInt().coerceAtMost(sorted.lastIndex)]/1e6)
                .put("overBudgetRatio",sorted.count {it>budget}.toDouble()/sorted.size).put("sceneRefreshesDuringDrag",sceneRefreshes)
                .put("effectUpdates",GlassDiagnostics.effectUpdates).put("note","Measured device rendering; no host simulation")
            File(compose.activity.getExternalFilesDir(null),"glass-motion-2.4.0.json").writeText(json.toString(2))
        } finally {compose.runOnUiThread {window.removeOnFrameMetricsAvailableListener(listener)};thread.quitSafely()}
    }
}
