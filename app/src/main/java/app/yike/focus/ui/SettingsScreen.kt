package app.yike.focus.ui

import app.yike.focus.FocusApplication

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.os.SystemClock
import androidx.compose.ui.semantics.*
import app.yike.focus.domain.SecretTapGate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.yike.focus.domain.InterfaceMaterial
import app.yike.focus.domain.FocusSettings

private const val AUTHOR = "PinkPurpleYellow"
private const val EMAIL = "ff1921570609@outlook.com"
private data class NumberEdit(val label: String, val value: Int, val min: Int, val max: Int, val unit: String, val save: (Int) -> Unit)

@Composable
fun SettingsScreen(settings: FocusSettings, onChange: ((FocusSettings) -> FocusSettings) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val lifecycle = LocalLifecycleOwner.current
    var permissionRevision by remember { mutableIntStateOf(0) }
    var numberEdit by remember { mutableStateOf<NumberEdit?>(null) }
    var backgroundHelp by remember { mutableStateOf(false) }
    val secretGate = remember { SecretTapGate() }
    var garden by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var document by remember { mutableStateOf<String?>(null) }
    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val version = packageInfo.versionName ?: app.yike.focus.BuildConfig.VERSION_NAME
    val notificationAllowed = remember(permissionRevision) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exactAllowed = remember(permissionRevision) {
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) permissionRevision++ }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    fun launch(intent: Intent) {
        try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { Toast.makeText(context, "当前系统没有对应的设置入口", Toast.LENGTH_SHORT).show() }
    }
    fun mail() {
        try {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", EMAIL, null))
                .putExtra(Intent.EXTRA_SUBJECT, "一刻专注 ${packageInfo.versionName} 使用反馈"))
        } catch (_: ActivityNotFoundException) {
            clipboard.setText(AnnotatedString(EMAIL))
            Toast.makeText(context, "未找到邮件应用，邮箱已复制", Toast.LENGTH_SHORT).show()
        }
    }

    SectionTitle("找到适合你的节奏。", "时间、声音和提醒，都可以按你的习惯来。")
    Spacer(Modifier.height(LayoutGap))
    val deskApp=context.applicationContext as FocusApplication
    val workspace by deskApp.repository.workspace.collectAsState(initial="{}")
    val gardenUsed by deskApp.repository.gardenUsed.collectAsState(initial=emptySet())
    val gardenVisible = org.json.JSONObject(workspace).optBoolean("discovered") || gardenUsed.isNotEmpty()
    SettingsPanels(listOf(
        SettingsSection("专注节奏") {
            PaperCard(Modifier.fillMaxWidth()) {
                Text("专注节奏", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                NumberRow("专注时长", "一次只做一件事", "${settings.focusMinutes} 分钟") {
                    numberEdit = NumberEdit("专注时长", settings.focusMinutes, 1, 180, "分钟") { v -> onChange { it.copy(focusMinutes = v) } }
                }
                NumberRow("短休息", "给眼睛和思绪一点空隙", "${settings.shortBreakMinutes} 分钟") {
                    numberEdit = NumberEdit("短休息", settings.shortBreakMinutes, 1, 180, "分钟") { v -> onChange { it.copy(shortBreakMinutes = v) } }
                }
                NumberRow("长休息", "完成一组后，好好歇一下", "${settings.longBreakMinutes} 分钟") {
                    numberEdit = NumberEdit("长休息", settings.longBreakMinutes, 1, 180, "分钟") { v -> onChange { it.copy(longBreakMinutes = v) } }
                }
                NumberRow("长休息间隔", "每完成几轮专注后安排", "${settings.longBreakEvery} 轮") {
                    numberEdit = NumberEdit("长休息间隔", settings.longBreakEvery, 1, 12, "轮") { v -> onChange { it.copy(longBreakEvery = v) } }
                }
                NumberRow("每日目标", "完成一轮才计入目标", "${settings.dailyGoal} 轮") {
                    numberEdit = NumberEdit("每日目标", settings.dailyGoal, 1, 40, "轮") { v -> onChange { it.copy(dailyGoal = v) } }
                }
                Spacer(Modifier.height(12.dp))
                Surface(modifier = Modifier.glassSurface(ButtonShape,GlassRole.CONTROL),color = if(LocalAppearance.current.interfaceMaterial == InterfaceMaterial.GLASS) androidx.compose.ui.graphics.Color.Transparent else SageLight, shape = ButtonShape) {
                    Text("专注结束自动休息；休息结束后，由你手动开启下一轮。进行中的阶段不受时长修改影响。",
                        Modifier.padding(16.dp), color = Sage, style = MaterialTheme.typography.bodySmall)
                }
            }

        },
        SettingsSection("声音与屏幕") {
            PaperCard(Modifier.fillMaxWidth()) {
                Text("声音与屏幕", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                SwitchRow("轻柔提示音", "专注和休息结束时播放短提示", settings.sound) { v -> onChange { it.copy(sound = v) } }
                SwitchRow("振动提醒", "仅在设备具有振动功能时生效", settings.vibration) { v -> onChange { it.copy(vibration = v) } }
                SwitchRow("专注时屏幕常亮", "仅在本应用的专注计时中生效", settings.keepAwake) { v -> onChange { it.copy(keepAwake = v) } }
                Text("背景声可在专注页选择。提示音受系统通知音量、静音及勿扰设置影响。", color = Muted, style = MaterialTheme.typography.bodySmall)
            }

        },
        SettingsSection("外观与主题") {
            PersonalizationCard(settings, onChange)
        },
        SettingsSection("备份与恢复") {
            BackupCard()
        },
        SettingsSection("后台与提醒") {
            PaperCard(Modifier.fillMaxWidth()) {
                Text("后台与提醒", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                NumberRow("通知权限", if (notificationAllowed) "可以显示进度和完成提醒" else "未允许时可能错过完成提醒", if (notificationAllowed) "已允许" else "去开启") {
                    launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
                NumberRow("精确计时提醒", if (exactAllowed) "系统允许按截止时间唤醒提醒" else "熄屏提醒可能延迟，请检查权限", if (exactAllowed) "已允许" else "去开启") {
                    if (Build.VERSION.SDK_INT in 31..32) launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    else launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
                NumberRow("平板后台运行帮助", "vivo / OriginOS 及其他安卓系统", "查看") { backgroundHelp = true }
            }

        },
        SettingsSection("关于") {
            PaperCard(Modifier.fillMaxWidth().testTag("about_card")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph("tomato", Modifier.size(48.dp).clickable {
                        if (secretGate.tap(SystemClock.elapsedRealtime())) garden = true
                    }.semantics {
                        contentDescription = "一刻专注图标"
                        customActions = listOf(CustomAccessibilityAction("进入秘密花园") { garden = true; true })
                    }.testTag("secret_tomato"), Tomato); Spacer(Modifier.width(12.dp))
                    Column {
                        Text("关于一刻专注", style = MaterialTheme.typography.titleLarge)
                        Text("版本 $version", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("app_version"))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("面向安卓平板的离线番茄钟。\n让重要的事，拥有一段不被打扰的时间。", color = Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp)); HorizontalDivider(color = Line); Spacer(Modifier.height(16.dp))
                Text("开发者", color = Muted, style = MaterialTheme.typography.bodySmall)
                SelectionContainer { Text(AUTHOR, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp).testTag("developer_name")) }
                Spacer(Modifier.height(16.dp))
                Text("联系与反馈", color = Muted, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(EMAIL, modifier = Modifier.weight(1f).clickable { mail() }.padding(vertical = 12.dp).testTag("developer_email"),
                        color = Tomato, style = MaterialTheme.typography.bodyMedium)
                    FocusIconButton(modifier = Modifier.semantics { contentDescription = "复制开发者邮箱" }, onClick = { clipboard.setText(AnnotatedString(EMAIL)); Toast.makeText(context, "邮箱已复制", Toast.LENGTH_SHORT).show() }) { Glyph("copy", Modifier.size(19.dp), Muted) }
                }
                FocusTextButton(onClick = { mail() }, contentPadding = PaddingValues(0.dp)) { Text("发送使用反馈") }
                for ((asset, title) in LegalDocuments.titles.filterKeys { it != "permissions.txt" }) {
                    FocusTextButton({ document = asset }) { Text(title) }
                }
                OnlinePolicyButton()
                FocusTextButton(onClick = { document = "changelog.txt" }, contentPadding = PaddingValues(0.dp)) { Text("查看更新日志") }
                FocusTextButton(onClick = { document = "audio_credits.txt" }, contentPadding = PaddingValues(0.dp)) { Text("音源来源与许可") }
                FocusTextButton(onClick = { document = "permissions.txt" }, contentPadding = PaddingValues(0.dp)) { Text("权限用途说明") }
                FocusTextButton(onClick = { showLicenses = true }, contentPadding = PaddingValues(0.dp)) { Text("第三方开源许可", color = Muted) }
                Spacer(Modifier.height(8.dp))
                Text("© 2026 PinkPurpleYellow", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            PaperCard(Modifier.fillMaxWidth()) {
                Text("你的记录，留在这里", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("无需账号，不申请联网权限，不上传任务或专注记录。数据保存在本机；可导出备份在设备间迁移，卸载前请保管好备份。",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    ), settings, if (gardenVisible) ({ garden = true }) else null)
    if (garden) SecretGarden { garden = false }
    numberEdit?.let { edit -> NumberDialog(edit) { numberEdit = null } }
    if (backgroundHelp) FocusAlertDialog(onDismissRequest = { backgroundHelp = false },
        title = { Text("让熄屏提醒更可靠") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("vivo / OriginOS", fontWeight = FontWeight.Medium)
            Text("在系统设置中搜索“后台耗电管理”或“自启动”，为一刻专注允许后台运行；也可在最近任务中锁定应用。具体名称随系统版本变化。", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Text("其他安卓平板", fontWeight = FontWeight.Medium)
            Text("检查本应用的通知、电池优化及后台活动限制，允许计时期间继续运行。", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Text("安装后可将专注设为 1 分钟，锁屏验证一次。系统“强行停止”应用会终止提醒；重启后计时恢复为暂停。", color = Muted, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { FocusTextButton(onClick = { launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("打开应用设置") } },
        dismissButton = { FocusTextButton(onClick = { backgroundHelp = false }) { Text("知道了") } })
    document?.let { name ->
        val text = remember(name) { context.assets.open(name).bufferedReader().use { it.readText() } }
        FocusAlertDialog(onDismissRequest = { document = null }, title = { Text(when(name) { "changelog.txt" -> "更新日志"; "audio_credits.txt" -> "音源来源与许可"; else -> LegalDocuments.titles[name] ?: "权限用途说明" }) },
            text = { SelectionContainer { Text(text, Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium) } },
            confirmButton = { FocusTextButton(onClick = { document = null }) { Text("关闭") } })
    }
    if (showLicenses) {
        val licenses = remember { context.assets.open("open_source_licenses.txt").bufferedReader().use { it.readText() } }
        FocusAlertDialog(onDismissRequest = { showLicenses = false }, title = { Text("第三方开源许可") },
            text = { SelectionContainer { Text(licenses, Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) } },
            confirmButton = { FocusTextButton(onClick = { showLicenses = false }) { Text("关闭") } })
    }
}

@Composable
private fun NumberRow(title: String, description: String, value: String, onClick: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(vertical = 16.dp)) {
        val stacked = maxWidth < 380.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.6f
        val label: @Composable () -> Unit = {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        val selection: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(value, color = Tomato, style = MaterialTheme.typography.labelLarge)
                Glyph("next", Modifier.size(16.dp), Muted)
            }
        }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { label(); selection() }
        else Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { label() }; Spacer(Modifier.width(16.dp)); selection()
        }
    }
    HorizontalDivider(color = Line, thickness = 0.8.dp)
}

@Composable
internal fun SwitchRow(title: String, description: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("switch_row_" + title).toggleable(checked, role = Role.Switch, onValueChange = onToggle).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(4.dp)); Text(description, color = Muted, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.width(12.dp)); FocusSwitch(checked = checked, onCheckedChange = onToggle, modifier = Modifier.clearAndSetSemantics { })
    }
}

@Composable
private fun NumberDialog(edit: NumberEdit, onDismiss: () -> Unit) {
    val initial = edit.value.toString()
    var text by remember(edit.label) { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val number = text.text.toIntOrNull()
    val valid = number != null && number in edit.min..edit.max
    FocusAlertDialog(onDismissRequest = onDismiss, title = { Text(edit.label) }, text = {
        FocusTextField(value = text, onValueChange = { if (it.text.length <= 3 && it.text.all(Char::isDigit)) text = it },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            suffix = { Text(edit.unit) }, label = { Text(edit.label) },
            supportingText = { Text("可设为 ${edit.min}–${edit.max} ${edit.unit}") }, isError = !valid, modifier = Modifier.fillMaxWidth())
    }, confirmButton = { FocusTextButton(enabled = valid, onClick = { edit.save(number!!); onDismiss() }) { Text("保存") } },
        dismissButton = { FocusTextButton(onClick = onDismiss) { Text("取消") } })
}
