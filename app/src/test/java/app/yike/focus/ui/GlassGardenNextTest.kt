package app.yike.focus.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.yike.focus.FocusApplication
import app.yike.focus.domain.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk=[34],application=FocusApplication::class,qualifiers="w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassGardenNextTest {
    @get:Rule val compose=createComposeRule()
    @Before fun resetState() { resetGardenTestPreferences(app()) }
    @After fun cleanup() { resetGardenTestPreferences(app());closeTestApplication(app()) }
    private fun app()=ApplicationProvider.getApplicationContext<FocusApplication>()
    private fun capture(view:View,name:String) {
        compose.runOnIdle {
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            val root=if(dialog?.isShowing==true) requireNotNull(dialog.window).decorView else view.rootView
            val bmp=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp));val file=File("build/outputs/2.4.0/next-$name.png");file.parentFile!!.mkdirs()
            file.outputStream().use {bmp.compress(Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()
        }
    }
    private fun seed(count:Int,used:Int=0)=runBlocking {
        repeat(count) {i -> app().repository.saveRecord(FocusRecord("next-$i",null,"阅读",1000,2000,1000,true,listOf(FocusSpan(1000,2000))))}
        app().repository.mergeGardenUsed((0 until used).map {"next-$it"}.toSet())
    }
    @Test fun foregroundLensShaderCompilesForBothOrientationsAndContinuousPositions() {
        val optics=NavigationOptics(NavigationOptics.SOURCE.replace("scene.eval(","sample(scene,"))
        for(vertical in listOf(false,true)) for(i in 0..20) for(p in listOf(0f,.5f,1f))
            assertNotNull(optics.configure(440f,76f,i/10f,vertical,p,1f,Offset(.5f,0f)))
    }
    @Test fun draggingLensTracksEveryStepWithoutRecomposingTheNavigationEachTime() {
        val settings=FocusSettings(interfaceMaterial=InterfaceMaterial.GLASS)
        var page by mutableIntStateOf(0);var view:View?=null
        compose.setContent {val v=LocalView.current;SideEffect{view=v};FocusTheme(settings) {
            AppScaffold(settings,page,java.time.LocalDate.of(2026,11,12),onPage={page=it}) {Text("把注意力，留给这一刻。")}
        } }
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("glass_navigation").performTouchInput {down(Offset(width*.16f,centerY));moveBy(Offset(20f,0f))}
        compose.waitForIdle();compose.mainClock.advanceTimeBy(160)
        val start=GlassDiagnostics.navCompositions
        val positions=mutableListOf<Float>()
        for(i in 1..12) {
            compose.onNodeWithTag("glass_navigation").performTouchInput {moveBy(Offset(20f,0f),delayMillis=16)}
            compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
            positions+=compose.onNode(SemanticsMatcher.keyIsDefined(NavigationLensPosition)).fetchSemanticsNode().config[NavigationLensPosition]
            if(i in listOf(1,4,8,12)) capture(requireNotNull(view),"navigation-drag-$i")
        }
        assertTrue(positions.zipWithNext().all {(a,b)->b>a})
        assertTrue("Drag caused repeated navigation recomposition: ${GlassDiagnostics.navCompositions-start}",GlassDiagnostics.navCompositions-start<=2)
        compose.onNodeWithTag("glass_navigation").performTouchInput {up()}
        compose.mainClock.advanceTimeBy(350);compose.waitForIdle();assertEquals(2,page)
    }
    @Test fun collectionCountsUseExistingWaterIdsWithoutAdditionalRewards() {
        for(total in 0..1000) assertEquals(total,(0 until Garden.VARIETIES).sumOf {Garden.varietyCount(total,it)})
        seed(24,24)
        val records=runBlocking {app().repository.dao.allRecords().map {it.toDomain()}}
        val used=runBlocking {app().repository.currentGardenUsed()}
        assertEquals(6,Garden.harvests(records,used));assertEquals(0,Garden.available(records,used).size)
        for(i in 0..5) assertEquals(1,Garden.varietyCount(6,i))
    }
    @Test fun waterGrowPickAndCollectionKeepTheSamePersistentAccounting() {
        seed(5,3);var view:View?=null
        val settings=FocusSettings(interfaceMaterial=InterfaceMaterial.GLASS,decorativeMotion=false)
        compose.setContent {val v=LocalView.current;SideEffect{view=v};FocusTheme(settings) {FocusBackdropHost(settings) {SecretGarden {}}} }
        compose.waitUntil(10_000) {compose.onAllNodesWithText("可用水滴 2  ·  已收获 0 颗番茄").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("water_garden").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("pick_tomato").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(4,runBlocking {app().repository.currentGardenUsed().size})
        capture(requireNotNull(view),"garden-ripe")
        compose.onNodeWithTag("pick_tomato").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("water_garden").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(4,runBlocking {app().repository.currentGardenUsed().size})
        compose.onNodeWithTag("collection_tab").performClick()
        compose.onNodeWithText("晨光红果").assertExists();compose.onNodeWithText("收藏 1 颗").assertExists()
        capture(requireNotNull(view),"garden-collection")
        compose.onNodeWithTag("garden_tab").performClick();capture(requireNotNull(view),"garden-room")
    }
    @Test fun cancelledWaterDragDoesNotConsumeWater() {
        seed(2)
        compose.setContent {FocusTheme(FocusSettings(decorativeMotion=false)) {FocusBackdropHost(FocusSettings()) {SecretGarden {}}} }
        compose.waitUntil(10_000) {compose.onAllNodesWithText("可用水滴 2  ·  已收获 0 颗番茄").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("garden_drag_water").performTouchInput {down(center);moveBy(Offset(-80f,-40f));cancel()}
        assertTrue(runBlocking {app().repository.currentGardenUsed().isEmpty()})
    }
    @Test fun draggingWaterIntoTheSceneConsumesExactlyOneDrop() {
        seed(5)
        val settings=FocusSettings(decorativeMotion=false)
        compose.setContent {FocusTheme(settings) {FocusBackdropHost(settings) {SecretGarden {}}} }
        compose.waitUntil(10_000) {compose.onAllNodesWithText("可用水滴 5  ·  已收获 0 颗番茄").fetchSemanticsNodes().isNotEmpty()}
        for(i in 1..4) {
            val scene=compose.onNodeWithTag("garden_scene").fetchSemanticsNode().boundsInRoot
            val drop=compose.onNodeWithTag("garden_drag_water").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("garden_drag_water").performTouchInput {
                swipe(center,center+scene.center-drop.center,durationMillis=500)
            }
            compose.waitUntil(10_000) {compose.onAllNodesWithText("可用水滴 ${5-i}  ·  已收获 ${i/4} 颗番茄").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(i,runBlocking {app().repository.currentGardenUsed().size})
            if(i==1) compose.onNodeWithText("新芽探出了头。").assertExists()
        }
        compose.onNodeWithTag("pick_tomato").assertExists()
    }
    @Test @Config(qualifiers="w360dp-h800dp-port-mdpi")
    fun gardenNarrowLargeTypeAndDarkModeKeepAllControlsReachable() {
        seed(28,24)
        var dark by mutableStateOf(false);var font by mutableFloatStateOf(1f);var view:View?=null
        compose.setContent {
            val settings=FocusSettings(interfaceMaterial=InterfaceMaterial.GLASS,themeMode=if(dark) ThemeMode.DARK else ThemeMode.LIGHT,decorativeMotion=false)
            val v=LocalView.current;SideEffect {view=v}
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f,font)) {
                FocusTheme(settings) {FocusBackdropHost(settings) {SecretGarden {}}}
            }
        }
        compose.waitUntil(10_000) {compose.onAllNodesWithText("可用水滴 4  ·  已收获 6 颗番茄").fetchSemanticsNodes().isNotEmpty()}
        var normalTitleHeight=0
        for(mode in listOf(false,true)) for(scale in listOf(1f,2f)) {
            compose.runOnIdle {dark=mode;font=scale}
            val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText("一刻秘密花园").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
            if(scale==1f) normalTitleHeight=layouts.single().size.height
            else assertTrue("Dialog must honor large system type",layouts.single().size.height>normalTitleHeight)
            compose.onNodeWithTag("water_garden").performScrollTo().assertIsDisplayed()
            capture(requireNotNull(view),"garden-narrow-$mode-$scale")
            compose.onNodeWithTag("collection_tab").performScrollTo().performClick()
            compose.onNodeWithText("日落条纹").performScrollTo().assertIsDisplayed()
            capture(requireNotNull(view),"collection-narrow-$mode-$scale")
            compose.onNodeWithTag("garden_tab").performScrollTo().performClick()
        }
    }
    @Test fun gardenAnimationFramesShowGrowthAndWaterBetweenStages() {
        var progress by mutableFloatStateOf(0f);var pick by mutableFloatStateOf(0f);var view:View?=null
        compose.setContent {val v=LocalView.current;SideEffect{view=v};FocusTheme {
            GardenLandscape(if(pick>0f) 4f else 2f+progress,0f,progress,pick,0,Modifier.size(800.dp,700.dp))
        } }
        for(i in 0..8) {compose.runOnIdle {progress=i/8f};capture(requireNotNull(view),"garden-motion-$i")}
        for(i in 0..8) {compose.runOnIdle {progress=0f;pick=(i+.01f)/8.01f};capture(requireNotNull(view),"garden-picking-$i")}
    }
}
