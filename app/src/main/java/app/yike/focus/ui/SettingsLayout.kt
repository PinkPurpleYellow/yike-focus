package app.yike.focus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.yike.focus.domain.*

class SettingsSection(val title: String, val content: @Composable () -> Unit)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPanels(sections: List<SettingsSection>) {
    val preset = LocalAppearance.current.uiPreset
    val gap = LayoutGap
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val index = selected.coerceIn(sections.indices)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= 840.dp && LocalDensity.current.fontScale < 1.6f
        if (preset == UiPreset.CLASSIC && wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) { sections.take(2).forEach { it.content() } }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) { sections.drop(2).forEach { it.content() } }
            }
        } else if (preset == UiPreset.CLASSIC || preset == UiPreset.MINIMAL) {
            Column(Modifier.widthIn(max = if (preset == UiPreset.MINIMAL) 860.dp else 1500.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(gap)) { sections.forEach { it.content() } }
        } else if (preset == UiPreset.FLIP && wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
                Column(Modifier.width(200.dp).then(if (LocalAppearance.current.interfaceMaterial == InterfaceMaterial.GLASS) Modifier.glassSurface(PanelShape) else Modifier.background(CardPaper, PanelShape)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("设置分类", Modifier.padding(12.dp), color = Muted, style = MaterialTheme.typography.labelSmall)
                    sections.forEachIndexed { i, section ->
                        FocusTonalButton(onClick = { selected = i }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("settings_section_$i"),
                            shape = ButtonShape, colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (i == index) Peach else CardPaper)) {
                            Text(section.title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Column(Modifier.weight(1f)) { sections[index].content() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sections.forEachIndexed { i, section ->
                        FocusFilterChip(selected = i == index, onClick = { selected = i }, label = { Text(section.title, style = MaterialTheme.typography.bodyMedium) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("settings_section_$i"), shape = SmallShape)
                    }
                }
                
                sections[index].content()
            }
        }
    }
}
