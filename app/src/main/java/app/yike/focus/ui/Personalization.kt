package app.yike.focus.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.yike.focus.FocusApplication
import app.yike.focus.data.*
import app.yike.focus.domain.*
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalizationCard(settings: FocusSettings, onChange: ((FocusSettings) -> FocusSettings) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as FocusApplication
    val scope = rememberCoroutineScope()
    var editingAppearance by rememberSaveable { mutableStateOf(false) }
    PaperCard(Modifier.fillMaxWidth()) {
        Text("外观与主题", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(settings.uiPreset.title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("${settings.themeStyle.title} · ${settings.themeMode.title} · ${settings.uiDensity.title}",
            color = Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text("${settings.interfaceMaterial.title} · 四套界面，可预览专注、统计、设置和全屏。", color = Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        FocusButton(onClick = { editingAppearance = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("open_appearance")) { Text("挑选与自定义外观") }
        if (settings.interfaceMaterial == InterfaceMaterial.GLASS) {
            val dock by app.repository.navDock.collectAsState(initial = NavDock.BOTTOM)
            Spacer(Modifier.height(16.dp))
            Text("导航位置", style = MaterialTheme.typography.titleMedium)
            Text("滑动胶囊切页；长按导航后拖向边缘。窄窗自动使用上下布局。", style = MaterialTheme.typography.bodySmall, color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavDock.entries.forEach { target -> FocusFilterChip(dock == target,
                    { scope.launch { runCatching { app.repository.setNavDock(target) }.onFailure { app.controller.notice.value = "导航位置未保存，请重试。" } } }, label = { Text(target.title) }, modifier = Modifier.heightIn(min = 48.dp)) }
            }
        }
    }
    if (editingAppearance) AppearanceEditor(settings, onApply = { draft ->
        scope.launch { try {
            val old=app.repository.currentSettings().backgroundFile
            app.repository.updateSettings { it.copy(backgroundFile=draft.backgroundFile,backgroundShade=draft.backgroundShade,uiPreset = draft.uiPreset, timerFace = draft.timerFace,
            uiDensity = draft.uiDensity, decorativeMotion = draft.decorativeMotion,
            themeStyle = draft.themeStyle, themeMode = draft.themeMode,
            interfaceMaterial = draft.interfaceMaterial, glassTransparency = draft.glassTransparency,
            glassBlur = draft.glassBlur, glassReadable = draft.glassReadable) }
            if (old != draft.backgroundFile) withContext(Dispatchers.IO) { ImageStore.file(context,old)?.delete() }
            editingAppearance = false
        } catch (e:java.io.IOException) { app.controller.notice.value="外观保存失败，请重试。" } }
    }, onDismiss = { editingAppearance = false })

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupCard() {
    val context = LocalContext.current
    val app = context.applicationContext as FocusApplication
    val manager = remember { BackupManager(context.applicationContext, app.repository) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<BackupData?>(null) }
    var restore by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try { manager.export(uri); result = "备份已导出。可通过系统文件管理器将文件传到另一台设备，再导入合并。" }
            catch (e: Exception) { result = "导出未完成：${e.localizedMessage ?: "请检查可用空间并重试"}。请删除不完整文件后重新导出。" }
            finally { busy = false }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try { pending = manager.read(uri); restore = false }
            catch (e: Exception) { result = "无法导入，未写入任何数据：${e.localizedMessage ?: "备份损坏或格式不支持"}" }
            finally { busy = false }
        }
    }
    PaperCard(Modifier.fillMaxWidth()) {
        Text("备份与恢复", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        Text("导出记录、任务、设置和背景；在其他设备导入时合并去重。备份文件未加密，请自行保管。", color = Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text("正在进行的专注不包含在备份中，完成或结束后再导出即可包含本次记录。", color = Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusButton(enabled = !busy, onClick = { export.launch("一刻专注-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.yikebackup") }) { Text("导出备份") }
            FocusOutlinedButton(enabled = !busy, onClick = { import.launch(arrayOf("*/*")) }) { Text("导入备份") }
        }
        if (busy) { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在处理，请稍候…", style = MaterialTheme.typography.bodySmall) }
    }
    pending?.let { data -> FocusAlertDialog(onDismissRequest = { if (!busy) pending = null }, title = { Text("合并这份备份？") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("${data.records.size} 条专注记录，${data.tasks.count { !it.deleted }} 项任务，${data.tasks.count { it.deleted }} 项删除标记。\n\n重复记录不增加统计；任务按修改时间合并，删除标记也参与合并。当前倒计时保持原状态。")
            Row(Modifier.fillMaxWidth().toggleable(restore, enabled = !busy, role = Role.Checkbox) { restore = it }
                .heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(restore, null, enabled = !busy, modifier = Modifier.padding(horizontal = 12.dp))
                Text("同时恢复设置及背景")
            }
        } },
        confirmButton = { FocusTextButton(enabled = !busy, onClick = { scope.launch {
            busy = true
            try {
                val r = manager.merge(data, restore)
                result = "合并完成：新增记录 ${r.records} 条，新增或更新任务 ${r.tasks} 项，重复 ${r.duplicates} 项，保留本机冲突 ${r.conflicts} 项。" +
                    when (r.gardenRestored) { true -> "\n花园进度已合并。"; false -> "\n花园恢复失败，可重新导入重试；任务与记录已保存。"; null -> "" } +
                    when { r.settingsError -> "\n记录和任务已保存，但设置恢复失败，可重新导入重试。"; r.settingsRestored == true -> "\n设置与背景已恢复；时长从下一阶段生效。"; else -> "\n保留了本机设置及背景。" }
                pending = null
            } catch (e: Exception) { result = "合并未完成：${e.localizedMessage ?: "请重试"}"; pending = null }
            finally { busy = false }
        } }) { Text(if (busy) "正在合并…" else "确认合并") } },
        dismissButton = { FocusTextButton(enabled = !busy, onClick = { pending = null }) { Text("取消") } }) }
    result?.let { message -> FocusAlertDialog(onDismissRequest = { result = null }, title = { Text("备份与恢复") },
        text = { Text(message, Modifier.verticalScroll(rememberScrollState())) }, confirmButton = { FocusTextButton(onClick = { result = null }) { Text("知道了") } }) }
}
