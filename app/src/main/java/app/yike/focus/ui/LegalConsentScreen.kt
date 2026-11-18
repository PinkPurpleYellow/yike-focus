package app.yike.focus.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.yike.focus.BuildConfig

internal object LegalDocuments {
    val titles = linkedMapOf("user_agreement.txt" to "用户协议", "privacy_policy.txt" to "隐私政策", "permissions.txt" to "权限用途说明")
}

@Composable internal fun OnlinePolicyButton() {
    val context = LocalContext.current
    if (BuildConfig.POLICY_URL.isNotEmpty()) {
        FocusTextButton({
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.POLICY_URL))) }
            catch (_: android.content.ActivityNotFoundException) { android.widget.Toast.makeText(context,"请阅读应用内的隐私政策全文。",android.widget.Toast.LENGTH_SHORT).show() }
        }) { Text("用浏览器查看隐私政策") }
    }
}

@Composable fun LegalConsentScreen(onAgree: () -> Unit, onExit: () -> Unit, saveError: Boolean = false) {
    val context = LocalContext.current
    var document by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler { if (document != null) document = null else onExit() }
    Surface(Modifier.fillMaxSize(), color = Paper) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 580.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("欢迎使用一刻专注", style = MaterialTheme.typography.headlineMedium)
                Text("把注意力，留给这一刻。", color = Muted)
                Spacer(Modifier.height(8.dp))
                Text("任务、专注记录和壁纸保存在这台设备上。你可以通过系统文件选择器导出备份，或选择一张自己的壁纸。")
                Text("开始计时时可按需开启提醒。是否允许通知，由你决定。请先阅读以下说明。")
                LegalDocuments.titles.forEach { (name, title) -> FocusTextButton({ document = name }) { Text("《一刻专注$title》") } }
                OnlinePolicyButton()
                if (saveError) Text("同意状态未能保存，请检查存储空间后重试。", color = Tomato)
                FocusButton(onAgree, Modifier.fillMaxWidth().testTag("legal_agree")) { Text("同意并开始使用") }
                FocusTextButton(onExit, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("不同意并退出") }
            }
        }
    }
    document?.let { name ->
        val content = remember(name) { context.assets.open(name).bufferedReader().use { it.readText() } }
        FocusAlertDialog({ document = null }, title = { Text(LegalDocuments.titles.getValue(name)) },
            text = { Text(content, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { FocusTextButton({ document = null }) { Text("返回说明") } })
    }
}
