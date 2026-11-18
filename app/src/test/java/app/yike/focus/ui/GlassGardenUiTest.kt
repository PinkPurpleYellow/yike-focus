package app.yike.focus.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.yike.focus.FocusApplication
import app.yike.focus.data.*
import app.yike.focus.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import org.robolectric.shadows.ShadowLooper

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = FocusApplication::class, qualifiers = "w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassGardenUiTest {
    @get:Rule val compose = createComposeRule()
    @After fun stopWorkers() { closeTestApplication(ApplicationProvider.getApplicationContext<FocusApplication>()) }
    private fun app() = ApplicationProvider.getApplicationContext<FocusApplication>()
    private fun snapshot(view: View, name: String) {
        compose.runOnIdle {
            val root = view.rootView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val file = File("build/outputs/3.0.0/$name.png"); file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    @Test fun glassPaletteReadingFloorsAndCompatibilityTiers() {
        assertEquals(0, glassTier(26)); assertEquals(1, glassTier(31)); assertEquals(2, glassTier(33))
        ThemeStyle.entries.forEach { style -> listOf(false, true).forEach { dark ->
            val basePalette = palette(style,dark)
            val p = basePalette.copy(ink=if(dark) Color(0xFFF9FBF6) else Color(0xFF202A23), muted=if(dark) Color(0xFFDFE7DB) else Color(0xFF303D30))
            for (t in listOf(0f, .5f, 1f)) for (role in listOf(GlassRole.CONTENT, GlassRole.DIALOG)) {
                val s = FocusSettings(glassTransparency = t)
                for (pixel in listOf(Color.Black, Color.White)) {
                    val base = p.paper.copy(alpha = readableWallpaperShade(p, .55f)).compositeOver(pixel)
                    val surface = p.card.copy(alpha = glassOpacity(s, role, false)).compositeOver(base)
                    assertTrue("$style $dark $t $role ${textContrast(p.muted,surface)}", textContrast(p.muted, surface) >= 4.5f)
                }
                assertTrue(glassOpacity(s, role, true) >= glassOpacity(s, role, false))
            }
        } }
        assertEquals(NavDock.BOTTOM, allowedDock(NavDock.LEFT, 360f, 1f))
        assertEquals(NavDock.BOTTOM, allowedDock(NavDock.RIGHT, 1280f, 2f))
        assertEquals(NavDock.LEFT, nearestDock(Offset(0f, 300f), 1280f, 800f, true))
        assertEquals(NavDock.TOP, nearestDock(Offset(100f, 0f), 360f, 800f, false))
    }
    @Test fun glassAllPresetsColorsModesAndFourPagesRender() {
        var settings by mutableStateOf(FocusSettings(interfaceMaterial = InterfaceMaterial.GLASS, decorativeMotion = false))
        var page by mutableIntStateOf(0)
        var view: View? = null
        compose.setContent { val v = LocalView.current; SideEffect { view = v }; FocusTheme(settings) { PreviewHost(settings,page) { page = it } } }
        for (preset in UiPreset.entries) for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) for (color in ThemeStyle.entries) {
            compose.runOnIdle { settings = settings.copy(uiPreset = preset, themeMode = mode, themeStyle = color); page = 0 }
            compose.onNodeWithTag("timer_primary").assertIsDisplayed()
            compose.onNodeWithTag("glass_navigation").assertIsDisplayed()
            if (color == ThemeStyle.TOMATO) snapshot(requireNotNull(view), "glass-${preset.name}-$mode-focus")
        }
        for (pageIndex in 1..3) {
            compose.runOnIdle { page = pageIndex; settings = settings.copy(uiPreset = UiPreset.CLASSIC, themeMode = ThemeMode.LIGHT) }
            snapshot(requireNotNull(view), "glass-page$pageIndex")
            if (pageIndex == 3) compose.onAllNodesWithTag("glass_navigation").assertCountEquals(0)
        }
    }
    @Test fun dragSwitchCancelLongPressDockAndAccessiblePositionAreSeparate() {
        val settings = FocusSettings(interfaceMaterial = InterfaceMaterial.GLASS, decorativeMotion = false)
        var page by mutableIntStateOf(0); var dock by mutableStateOf(NavDock.BOTTOM)
        compose.setContent { FocusTheme(settings) {
            AppScaffold(settings, page, java.time.LocalDate.of(2026,11,12), onPage = { page = it }, navigationDock = dock, onDock = { dock = it }) {
                androidx.compose.material3.Text("内容 $page")
            }
        } }
        compose.onNodeWithTag("nav_1").performClick(); assertEquals(1,page)
        compose.onNodeWithTag("glass_navigation").performTouchInput { swipe(Offset(width*.5f,height*.5f), Offset(width*.92f,height*.5f)) }
        assertEquals(2,page); assertEquals(NavDock.BOTTOM,dock)
        compose.onNodeWithTag("glass_navigation").performTouchInput {
            down(Offset(width*.85f,height*.5f)); moveBy(Offset(-width*.35f,0f)); cancel()
        }
        assertEquals(2,page)
        compose.onNodeWithTag("glass_navigation").performTouchInput {
            down(center); advanceEventTime(650); moveBy(Offset(0f,-600f),delayMillis=50); up()
        }
        assertEquals(NavDock.TOP,dock); assertEquals(2,page)
        val actions = compose.onNodeWithTag("glass_navigation").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(actions.first { it.label == "移到左侧" }.action()) }
        assertEquals(NavDock.LEFT,dock)
    }
    private fun layoutMatrix(name: String) {
        var scale by mutableFloatStateOf(1f)
        var settings by mutableStateOf(FocusSettings(interfaceMaterial = InterfaceMaterial.GLASS, decorativeMotion = false))
        var view: View? = null
        compose.setContent { val v = LocalView.current; SideEffect { view = v }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,scale)) { FocusTheme(settings) { PreviewHost(settings,0) } }
        }
        for (font in listOf(1f,1.3f,1.6f,2f)) for (preset in UiPreset.entries) {
            compose.runOnIdle { scale = font; settings = settings.copy(uiPreset = preset,uiDensity = if (font < 1.6f) UiDensity.COMFORTABLE else UiDensity.COMPACT) }
            compose.onNodeWithTag("nav_2").assertIsDisplayed()
            compose.onNodeWithTag("timer_primary").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("add_task").performScrollTo().assertIsDisplayed()
            val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),true).fetchSemanticsNodes()
            compose.runOnIdle { nodes.forEach { node ->
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                layouts.forEach { result ->
                    val width = (0 until result.lineCount).maxOfOrNull { result.getLineRight(it)-result.getLineLeft(it) } ?: 0f
                    assertTrue("Glass text overflows: ${result.layoutInput.text}", width <= result.size.width+1)
                }
            } }
            snapshot(requireNotNull(view), "layout-$name-$preset-$font")
        }
    }
    @Test fun glassLayout1280x800() = layoutMatrix("1280x800")
    @Test @Config(qualifiers = "w360dp-h800dp-port-mdpi") fun glassLayout360x800() = layoutMatrix("360x800")
    @Test @Config(qualifiers = "w600dp-h480dp-land-mdpi") fun glassLayout600x480() = layoutMatrix("600x480")
    @Test @Config(qualifiers = "w800dp-h1280dp-port-mdpi") fun glassLayout800x1280() = layoutMatrix("800x1280")
    @Test @Config(qualifiers = "w1600dp-h1000dp-land-mdpi") fun glassLayout1600x1000() = layoutMatrix("1600x1000")
    @Test fun gardenWaterIsSerializedPersistsAndBackupMergeIsIdempotent() = runBlocking {
        val repository = app().repository
        val records = (1..5).map { FocusRecord("garden-$it",null,"读书",1000,2000,1000,true,listOf(FocusSpan(1000,2000))) }
        records.forEach { repository.saveRecord(it) }
        val results = coroutineScope { (1..8).map { async(Dispatchers.IO) { repository.waterGarden() } }.awaitAll() }
        assertEquals(5,results.count { it }); assertEquals(5,repository.currentGardenUsed().size)
        repository.mergeGardenUsed(setOf("garden-1")); repository.mergeGardenUsed(setOf("garden-1"))
        assertEquals(5,repository.currentGardenUsed().size)
        repository.updateSettings { it.copy(interfaceMaterial = InterfaceMaterial.GLASS,glassBlur = .4f) }
        repository.setNavDock(NavDock.RIGHT)
        assertEquals(InterfaceMaterial.GLASS,repository.currentSettings().interfaceMaterial)
        assertEquals(NavDock.RIGHT,repository.navDock.first())
    }
    @Test fun hiddenEntranceSevenTapsShowsGardenAndWateringChangesPlant() {
        runBlocking { repeat(4) { i -> app().repository.saveRecord(FocusRecord("hidden-$i",null,"阅读",1000,2000,1000,true,listOf(FocusSpan(1000,2000)))) } }
        val settings = FocusSettings(decorativeMotion = false)
        compose.setContent { FocusTheme(settings) {
            AppScaffold(settings,2,java.time.LocalDate.of(2026,11,12),onPage = {}) { SettingsScreen(settings) {} }
        } }
        compose.onNodeWithTag("settings_section_5").performScrollTo().performClick()
        compose.onNodeWithTag("secret_tomato").performScrollTo()
        repeat(6) { compose.onNodeWithTag("secret_tomato").performClick() }
        compose.onAllNodesWithTag("secret_garden").assertCountEquals(0)
        compose.onNodeWithTag("secret_tomato").performClick()
        compose.onNodeWithTag("secret_garden").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("可用水滴 4  ·  已收获 0 颗番茄").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("water_garden").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("可用水滴 3  ·  已收获 0 颗番茄").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("新芽探出了头。").assertIsDisplayed()
    }
    @Test fun gardenPreviewShowsAllGrowthStages() {
        var stage by mutableIntStateOf(0); var view: View? = null
        compose.setContent { val v=LocalView.current; SideEffect { view=v }; FocusTheme() {
            Column(Modifier.fillMaxSize()) { androidx.compose.material3.Text("一刻秘密花园"); GardenPlant(stage,0f,Modifier.fillMaxWidth().height(400.dp)) }
        } }
        for (i in 0..4) { compose.runOnIdle { stage=i }; snapshot(requireNotNull(view),"garden-stage$i") }
    }
    @Test fun wallpaperGlassAndFullscreenKeepBackgroundContinuousWithSharpText() {
        val file = File(app().filesDir,"background-22222222-2222-2222-2222-222222222222.jpg")
        val bmp = Bitmap.createBitmap(1280,800,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = android.graphics.Paint().apply { shader = android.graphics.LinearGradient(0f,0f,1280f,800f,
            intArrayOf(0xFF183C4D.toInt(),0xFFB8C5AD.toInt(),0xFFD7B49A.toInt()),null,android.graphics.Shader.TileMode.CLAMP) }
        canvas.drawRect(0f,0f,1280f,800f,paint); paint.shader=null
        for (i in 0..10) { paint.color=if(i%2==0) 0x55296160 else 0x55FFEAC2
            canvas.drawCircle(i*130f,(i*137%800).toFloat(),130f,paint) }
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG,95,it) }; bmp.recycle()
        var settings by mutableStateOf(FocusSettings(interfaceMaterial = InterfaceMaterial.GLASS, backgroundFile = file.name,
            glassTransparency = 1f, decorativeMotion = false))
        var page by mutableIntStateOf(0); var view: View? = null; var decoded: androidx.compose.ui.graphics.ImageBitmap? = null
        compose.setContent { val v=LocalView.current; SideEffect { view=v }; FocusTheme(settings) {
            FocusBackdropHost(settings) {
                val image = LocalFocusBackdrop.current?.bitmap; SideEffect { decoded=image }
                PreviewHost(settings,page) { page=it }
            }
        } }
        compose.waitUntil(15_000) { ShadowLooper.idleMainLooper(); decoded!=null }
        val original = decoded
        for (mode in listOf(ThemeMode.LIGHT,ThemeMode.DARK)) for (preset in UiPreset.entries) {
            compose.runOnIdle { settings=settings.copy(themeMode=mode,uiPreset=preset); page=0 }
            snapshot(requireNotNull(view),"wallpaper-glass-$preset-$mode")
            compose.runOnIdle { page=3 }
            compose.onAllNodesWithTag("glass_navigation").assertCountEquals(0)
            assertSame(original,decoded)
            snapshot(requireNotNull(view),"wallpaper-fullscreen-$preset-$mode")
        }
        file.delete()
    }
    @Test fun controlledNavigationMotionFramesAreContinuousAndEndOnTarget() {
        val settings=FocusSettings(interfaceMaterial=InterfaceMaterial.GLASS)
        var page by mutableIntStateOf(0); var view: View?=null
        compose.setContent { val v=LocalView.current; SideEffect { view=v }; FocusTheme(settings) {
            AppScaffold(settings,page,java.time.LocalDate.of(2026,11,12),onPage={ page=it }) {
                androidx.compose.material3.Text("给自己一点专注的时间。")
            }
        } }
        compose.mainClock.autoAdvance=false
        snapshot(requireNotNull(view),"motion-nav-0")
        compose.onNodeWithTag("nav_2").performClick()
        for ((i,step) in listOf(16L,64L,64L,96L).withIndex()) {
            compose.mainClock.advanceTimeBy(step); snapshot(requireNotNull(view),"motion-nav-${i+1}")
        }
        compose.onNodeWithTag("nav_2").assertIsSelected()
    }
}
