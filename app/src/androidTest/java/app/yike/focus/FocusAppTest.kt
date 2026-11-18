package app.yike.focus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.yike.focus.data.TaskEntity
import app.yike.focus.domain.*
import app.yike.focus.platform.TimerAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FocusAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @org.junit.Before fun acceptPolicyForFunctionalTests() = runBlocking { app.repository.setLegalConsentVersion(1) }
    private val app get() = compose.activity.application as FocusApplication

    @Test fun aboutShowsDeveloperEmailAndActualPackageVersion() {
        compose.waitUntil(10_000) { app.controller.loaded.value }
        compose.onNodeWithTag("nav_2").performClick()
        compose.onNodeWithTag("settings_section_5").performScrollTo().performClick()
        compose.onNodeWithTag("developer_name").performScrollTo().assertTextEquals("PinkPurpleYellow")
        compose.onNodeWithTag("developer_email").performScrollTo().assertTextEquals("ff1921570609@outlook.com").assertHasClickAction()
        compose.onNodeWithTag("app_version").performScrollTo().assertTextEquals("版本 ${BuildConfig.VERSION_NAME}")
    }

    @Test fun taskAndSettingsPersistAndDuplicateRecordsAreIgnored() = runBlocking {
        val repository = app.repository
        val old = repository.currentSettings()
        val task = TaskEntity(title = "数据库验收任务")
        val id = UUID.randomUUID().toString()
        try {
            repository.saveTask(task)
            repository.selectTask(task.id)
            assertEquals(task.title, repository.selectedTask()?.title)
            repository.updateSettings { it.copy(dailyGoal = 9, themeStyle = ThemeStyle.LILAC, themeMode = ThemeMode.DARK) }
            assertEquals(9, repository.currentSettings().dailyGoal)
            assertEquals(ThemeStyle.LILAC, repository.currentSettings().themeStyle)
            assertEquals(ThemeMode.DARK, repository.currentSettings().themeMode)
            repository.saveTask(task.copy(title = "编辑后的任务", completed = true))
            assertNull(repository.selectedTask())
            val span = FocusSpan(1_000, 31_000)
            val record = FocusRecord(id, task.id, task.title, 1_000, 31_000, 30_000, false, listOf(span))
            repository.saveRecord(record)
            repository.saveRecord(record)
            val stored = repository.records.first().filter { it.id == id }
            assertEquals(1, stored.size)
            assertEquals(30_000L, stored.single().focusedMs)
            assertEquals(listOf(span), stored.single().spans)
        } finally {
            repository.deleteTask(task.id)
            repository.selectTask(null)
            repository.updateSettings { old }
        }
    }

    @Test fun countdownPausesAndSurvivesActivityRecreation() {
        compose.waitUntil(10_000) { app.controller.loaded.value }
        runBlocking { app.controller.handle(TimerAction.END); app.controller.handle(TimerAction.START) }
        compose.waitUntil(5_000) { app.controller.state.value.status == RunStatus.RUNNING }
        runBlocking { app.controller.handle(TimerAction.PAUSE) }
        val before = app.controller.state.value
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(before.sessionId, app.controller.state.value.sessionId)
        assertEquals(before.remainingMs, app.controller.state.value.remainingMs)
        assertEquals(RunStatus.PAUSED, app.controller.state.value.status)
        runBlocking { app.controller.handle(TimerAction.END) }
    }

    @Test fun fullscreenSurvivesRecreationAndKeepsTheSameTimer() {
        compose.waitUntil(10_000) { app.controller.loaded.value }
        runBlocking { app.controller.handle(TimerAction.END); app.controller.handle(TimerAction.START); app.controller.handle(TimerAction.PAUSE) }
        val before = app.controller.state.value
        compose.onNodeWithTag("nav_0").performClick()
        compose.onNodeWithTag("enter_fullscreen").performScrollTo().performClick()
        compose.onNodeWithText("退出全屏").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("退出全屏").assertExists().performClick()
        assertEquals(before.sessionId, app.controller.state.value.sessionId)
        assertEquals(before.remainingMs, app.controller.state.value.remainingMs)
        runBlocking { app.controller.handle(TimerAction.END) }
    }
    @Test fun changingAppearancePreservesPausedSessionAndPersistsAcrossRecreation() {
        compose.waitUntil(10_000) { app.controller.loaded.value }
        val original = runBlocking { app.repository.currentSettings() }
        try {
            runBlocking { app.controller.handle(TimerAction.END); app.controller.handle(TimerAction.START); app.controller.handle(TimerAction.PAUSE) }
            val before = app.controller.state.value
            UiPreset.entries.forEach { preset ->
                runBlocking { app.repository.updateSettings { it.copy(uiPreset = preset, uiDensity = UiDensity.COMPACT, decorativeMotion = false) } }
                compose.waitUntil(5_000) { app.controller.settings.value.uiPreset == preset }
                assertEquals(before, app.controller.state.value)
            }
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            assertEquals(before, app.controller.state.value)
            assertEquals(UiPreset.JOURNAL, runBlocking { app.repository.currentSettings() }.uiPreset)
        } finally {
            runBlocking { app.controller.handle(TimerAction.END); app.repository.updateSettings { original } }
        }
    }
}
