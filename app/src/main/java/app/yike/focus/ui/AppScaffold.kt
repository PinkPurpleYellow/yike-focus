package app.yike.focus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.yike.focus.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared production shell. Tests and the appearance preview use this same navigation and height budget. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppScaffold(settings: FocusSettings, page: Int, today: LocalDate,
    snackbar: SnackbarHostState = remember { SnackbarHostState() }, onPage: (Int) -> Unit,
    navigationDock: NavDock = NavDock.BOTTOM, onDock: (NavDock) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit) {
    if (settings.interfaceMaterial == InterfaceMaterial.GLASS) {
        GlassAppScaffold(settings, page, today, snackbar, onPage, navigationDock, onDock, content)
        return
    }
    FocusBackdropHost(settings, enabled = page == 0) {
    Surface(Modifier.fillMaxSize(), color = if (page == 0) androidx.compose.ui.graphics.Color.Transparent else Paper) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth >= 840.dp && LocalDensity.current.fontScale < 1.6f
            Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent,
                snackbarHost = { FocusSnackbarHost(snackbar) },
                bottomBar = { if (!wide) AppNavigation(page, false, onPage) }) { insets ->
                Row(Modifier.fillMaxSize().padding(insets)) {
                    if (wide) AppNavigation(page, true, onPage)
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        val padding = if (settings.uiDensity == UiDensity.COMPACT) 16.dp else if (wide) 32.dp else 24.dp
                        CompositionLocalProvider(LocalContentHeight provides (maxHeight - padding * 2)) {
                            key(page) {
                                Column(Modifier.widthIn(max = 1360.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                                    .padding(padding).testTag("page_scroll")) {
                                    WallpaperContent(enabled = page == 0) {
                                    FlowRow(Modifier.fillMaxWidth().testTag("page_header"), horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (!wide) Text("一刻专注", style = MaterialTheme.typography.titleSmall, color = Muted)
                                        Text(today.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)),
                                            style = MaterialTheme.typography.bodySmall, color = Muted)
                                    }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    content()
                                    Spacer(Modifier.height(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun AppNavigation(page: Int, rail: Boolean, onPage: (Int) -> Unit) {
    val entries = listOf("专注" to "focus", "统计" to "stats", "设置" to "settings")
    val item: @Composable (Int, String, String, Modifier) -> Unit = { index, title, icon, modifier ->
        Column(modifier.selectable(page == index, role = Role.Tab, onClick = { onPage(index) })
            .testTag("nav_$index").heightIn(min = 72.dp).padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            Surface(color = if (page == index) Peach else androidx.compose.ui.graphics.Color.Transparent, shape = ButtonShape) {
                Box(Modifier.width(56.dp).height(32.dp), contentAlignment = Alignment.Center) {
                    Glyph(icon, Modifier.size(24.dp), if (page == index) Tomato else Muted)
                }
            }
            Text(title, color = if (page == index) Tomato else Muted, style = MaterialTheme.typography.titleSmall)
        }
    }
    if (rail) Column(Modifier.width(96.dp).fillMaxHeight().background(CardPaper).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Glyph("tomato", Modifier.size(32.dp), Tomato)
        Spacer(Modifier.height(8.dp))
        Text("一刻专注", style = MaterialTheme.typography.bodySmall, color = Muted)
        Spacer(Modifier.height(32.dp))
        entries.forEachIndexed { index, (title, icon) -> item(index, title, icon, Modifier.fillMaxWidth()) }
    } else Surface(color = CardPaper) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars), verticalAlignment = Alignment.CenterVertically) {
            entries.forEachIndexed { index, (title, icon) -> item(index, title, icon, Modifier.weight(1f)) }
        }
    }
}
