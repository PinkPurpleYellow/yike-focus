package app.yike.focus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.yike.focus.domain.*

class SettingsSection(val title: String, val content: @Composable () -> Unit)

private data class Category(
    val title: String,
    val icon: String,
    val detail: String,
    val sections: List<Int>,
)

@Composable
fun SettingsPanels(
    sections: List<SettingsSection>,
    settings: FocusSettings = LocalAppearance.current,
    onGarden: (() -> Unit)? = null,
) {
    val groups =
        listOf(
            Category(
                "专注与休息",
                "focus",
                "${settings.focusMinutes} / ${settings.shortBreakMinutes} 分钟",
                listOf(0),
            ),
            Category("声音与提醒", "sound", if (settings.sound) "提示音开" else "提示音关", listOf(1, 4)),
            Category("外观与布局", "settings", settings.interfaceMaterial.title, listOf(2)),
            Category("数据与备份", "copy", "本机", listOf(3)),
            Category("关于", "tomato", app.yike.focus.BuildConfig.VERSION_NAME, listOf(5)),
        )
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var inDetail by rememberSaveable { mutableStateOf(false) }
    var listOffset by rememberSaveable { mutableIntStateOf(0) }
    val scroll = LocalDeskScroll.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 840.dp && LocalDensity.current.fontScale < 1.6f
        androidx.activity.compose.BackHandler(!wide && inDetail) { inDetail = false }
        LaunchedEffect(wide, inDetail) {
            if (!wide) scroll?.scrollTo(if (inDetail) 0 else listOffset)
        }
        val nav: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(groups.take(3), groups.drop(3)).forEachIndexed { part, entries ->
                    val skin =
                        if (settings.interfaceMaterial == InterfaceMaterial.GLASS)
                            Modifier.glassSurface(PanelShape)
                        else Modifier.background(CardPaper, PanelShape)
                    Column(Modifier.fillMaxWidth().clip(PanelShape).then(skin)) {
                        entries.forEachIndexed { index, category ->
                            if (index > 0)
                                HorizontalDivider(
                                    Modifier.padding(start = 56.dp, end = 16.dp),
                                    color = Line,
                                )
                            val i = groups.indexOf(category)
                            SettingsLink(
                                category,
                                wide && i == selected,
                                "settings_section_${category.sections.first()}",
                            ) {
                                if (!wide) listOffset = scroll?.value ?: 0
                                selected = i
                                inDetail = true
                            }
                        }
                        if (part == 1 && onGarden != null) {
                            HorizontalDivider(
                                Modifier.padding(start = 56.dp, end = 16.dp),
                                color = Line,
                            )
                            SettingsLink(
                                Category("我的花房", "leaf", "", emptyList()),
                                false,
                                "settings_garden",
                                onGarden,
                            )
                        }
                    }
                }
            }
        }
        val detail: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LayoutGap)) {
                if (!wide) FocusTextButton({ inDetail = false }) { Text("‹ 设置") }
                groups[selected].sections.forEach { sections.getOrNull(it)?.content?.invoke() }
            }
        }
        if (wide)
            Row(horizontalArrangement = Arrangement.spacedBy(LayoutGap)) {
                Column(
                    Modifier.width(
                        (this@BoxWithConstraints.maxWidth * .33f).coerceIn(260.dp, 368.dp)
                    )
                ) {
                    nav()
                }
                Column(Modifier.weight(1f)) { detail() }
            }
        else if (inDetail) detail() else nav()
    }
}

@Composable
private fun SettingsLink(category: Category, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (selected) Modifier.background(Tomato.copy(alpha = .08f)) else Modifier)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .testTag(tag)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(category.icon, Modifier.size(22.dp), if (selected) Tomato else Sage)
        Spacer(Modifier.width(14.dp))
        BoxWithConstraints(Modifier.weight(1f)) {
            if (maxWidth / LocalDensity.current.fontScale < 160.dp)
                Column {
                    Text(category.title, style = MaterialTheme.typography.titleMedium)
                    if (category.detail.isNotEmpty())
                        Text(
                            category.detail,
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                }
            else
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        category.title,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(category.detail, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
        }
        Spacer(Modifier.width(8.dp))
        Glyph("next", Modifier.size(15.dp), Muted)
    }
}
