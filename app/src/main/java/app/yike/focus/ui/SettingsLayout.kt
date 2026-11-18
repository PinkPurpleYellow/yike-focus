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
    val groups = listOf("专注与休息" to listOf(0), "声音与提醒" to listOf(1,4), "外观与布局" to listOf(2), "数据与备份" to listOf(3), "关于" to listOf(5))
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var inDetail by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide=maxWidth>=840.dp && LocalDensity.current.fontScale<1.6f
        androidx.activity.compose.BackHandler(!wide && inDetail){inDetail=false}
        val nav:@Composable ()->Unit={ Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            groups.forEachIndexed { i,(name,indices) -> FocusFilterChip(wide && selected==i,{selected=i;inDetail=true},label={Text(name)},modifier=Modifier.fillMaxWidth().testTag("settings_section_${indices.first()}")) }
        } }
        val detail:@Composable ()->Unit={Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(LayoutGap)) {
            if(!wide) FocusTextButton({inDetail=false}){Text("‹ 设置")}
            groups[selected].second.forEach{ sections.getOrNull(it)?.content?.invoke() }
        }}
        if(wide) Row(horizontalArrangement=Arrangement.spacedBy(LayoutGap)){Column(Modifier.width(180.dp)){nav()};Column(Modifier.weight(1f)){detail()}}
        else if(inDetail) detail() else nav()
    }
}
