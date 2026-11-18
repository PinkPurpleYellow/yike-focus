package app.yike.focus.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.yike.focus.FocusApplication
import app.yike.focus.data.*
import app.yike.focus.domain.*
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = FocusApplication::class, qualifiers = "w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkspaceTest {
    @get:Rule val compose = createComposeRule()
    private val app
        get() = ApplicationProvider.getApplicationContext<FocusApplication>()

    @Before
    fun initialize() {
        resetGardenTestPreferences(app)
    }

    @After
    fun close() {
        closeTestApplication(app)
    }

    private fun picture(name: String) =
        compose.runOnIdle {
            val root = WindowInspector.getGlobalWindowViews().last { it.width > 0 && it.height > 0 }
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val out = File("build/outputs/workspace-3.0.0/$name.png")
            requireNotNull(out.parentFile).mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }

    @Test
    fun editorAddsResizesSavesAndCancels() {
        val settings = FocusSettings(interfaceMaterial = InterfaceMaterial.GLASS)
        compose.setContent {
            FocusTheme(settings) {
                CompositionLocalProvider(LocalDeskRepo provides app.repository) {
                    PreviewHost(settings, 0)
                }
            }
        }
        compose.onNodeWithTag("edit_focus").performScrollTo().performClick()
        compose.onNodeWithText("添加组件").performClick()
        compose.onNodeWithText("下一件任务").performScrollTo().performClick()
        compose.onNodeWithTag("tile_next").assertExists()
        compose.onNodeWithText("下一件任务").performScrollTo().performClick()
        compose.onNodeWithText("加宽").performClick()
        compose.onNodeWithText("完成").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("edit_focus").fetchSemanticsNodes().isNotEmpty()
        }
        val saved = runBlocking { app.repository.workspace.first() }
        assertTrue(JSONObject(saved).getString("focus").contains("next,3,2"))
        compose.onNodeWithTag("edit_focus").performScrollTo().performClick()
        compose.onNodeWithText("恢复预设").performClick()
        picture("编辑专注桌面")
        compose.onNodeWithText("取消").performClick()
        assertEquals(saved, runBlocking { app.repository.workspace.first() })
    }

    @Test
    fun physicalDragAndResizeChangeDraft() {
        compose.setContent {
            FocusTheme {
                CompositionLocalProvider(LocalDeskRepo provides app.repository) {
                    PreviewHost(FocusSettings(), 0)
                }
            }
        }
        compose.onNodeWithTag("edit_focus").performScrollTo().performClick()
        compose.onNodeWithTag("tile_timer").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(35f, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(width * .83f, 0f), delayMillis = 32)
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("resize_timer").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 22f))
            moveBy(androidx.compose.ui.geometry.Offset(0f, 104f))
            up()
        }
        compose.onNodeWithText("完成").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("edit_focus").fetchSemanticsNodes().isNotEmpty()
        }
        val list =
            decodeTiles(
                JSONObject(runBlocking { app.repository.workspace.first() }).getString("focus"),
                widgetNames.keys.toList(),
            )
        assertEquals("tasks", list.first().id)
        assertEquals(3, list.first { it.id == "timer" }.h)
    }

    @Test
    fun decorationPreviewAndDiscovery() {
        compose.setContent { FocusTheme { SecretGarden {} } }
        compose.onNodeWithTag("decorate_tab").performClick()
        compose.onNodeWithText("应用布置").performScrollTo().assertHasClickAction()
        picture("花房布置")
        compose.onNodeWithText("应用布置").performClick()
        assertTrue(
            JSONObject(runBlocking { app.repository.workspace.first() }).optBoolean("discovered")
        )
        assertEquals(listOf(0, 1, 3, 7), listOf(0, 1, 3, 6).map(::decorMask))
    }

    @Config(qualifiers = "w600dp-h360dp-land-mdpi")
    @Test
    fun fixedToolbarInShortWindow() {
        var count = 0
        compose.setContent { FocusTheme { AppearanceEditor(FocusSettings(), { count++ }, {}) } }
        val node = compose.onNodeWithTag("apply_appearance")
        node.assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().boundsInWindow
        val host = compose.onNodeWithTag("theme_editor").fetchSemanticsNode().boundsInWindow
        assertTrue(
            bounds.top >= host.top && bounds.bottom <= host.bottom && bounds.right <= host.right
        )
        node.performTouchInput {
            click(androidx.compose.ui.geometry.Offset(centerX, 2f))
            click(androidx.compose.ui.geometry.Offset(centerX, height - 3f))
        }
        assertEquals(2, count)
        picture("主题工具栏")
    }

    @Test
    fun restoringOnlySelectedPreferencesPreservesPreviousLayout() = runBlocking {
        val repository = app.repository
        val manager = BackupManager(app, repository)
        repository.reviseWorkspace { it.put("focus", "timer,2,2").put("decor", 3) }
        val old = BackupData(emptyList(), emptyList(), FocusSettings())
        manager.merge(old, true)
        assertEquals("timer,2,2", JSONObject(repository.workspace.first()).getString("focus"))
        val backup = old.copy(workspace = JSONObject().put("focus", "").put("decor", 1).toString())
        manager.merge(backup, false)
        assertEquals(3, JSONObject(repository.workspace.first()).getInt("decor"))
        manager.merge(backup, true)
        val updated = JSONObject(repository.workspace.first())
        assertEquals("", updated.getString("focus"))
        assertEquals(1, updated.getInt("decor"))
    }

    @Test
    fun statisticsWidgetsOpenDatesAndKeepIndependentPageLayouts() {
        runBlocking {
            app.repository.reviseWorkspace { it.put("stats", "heat,2,2;rank,2,2").put("focus", "") }
        }
        compose.setContent {
            FocusTheme {
                CompositionLocalProvider(LocalDeskRepo provides app.repository) {
                    PreviewHost(FocusSettings(), 1)
                }
            }
        }
        compose.onNodeWithText("近七天 · 任务用时").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("四周热力图").performScrollTo().assertIsDisplayed()
        val date = java.time.LocalDate.now().minusDays(2)
        compose
            .onNode(hasContentDescription("$date，", substring = true))
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("关闭").performClick()
        picture("我的统计桌面")
        assertEquals(
            "",
            JSONObject(runBlocking { app.repository.workspace.first() }).getString("focus"),
        )
    }

    @Test
    fun cellsNeverOverlapAndBackupOptionalFields() {
        val tiles =
            listOf(
                Tile("next", 1, 2),
                Tile("tasks", 2, 1),
                Tile("timer", 4, 3),
                Tile("summary", 2, 1),
            )
        for (n in listOf(1, 2, 4)) {
            val filled = mutableSetOf<Pair<Int, Int>>()
            slots(tiles, n).forEachIndexed { i, (x, y) ->
                repeat(tiles[i].h) { dy ->
                    repeat(tiles[i].w.coerceAtMost(n)) { dx ->
                        assertTrue(x + dx < n)
                        assertTrue(filled.add(x + dx to y + dy))
                    }
                }
            }
        }
        val old = BackupData(emptyList(), emptyList(), FocusSettings())
        assertNull(BackupCodec.decode(BackupCodec.encode(old)).workspace)
        val updated = old.copy(workspace = JSONObject().put("focus", encodeTiles(tiles)).toString())
        assertEquals(updated.workspace, BackupCodec.decode(BackupCodec.encode(updated)).workspace)
    }
}
