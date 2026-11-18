package app.yike.focus.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import app.yike.focus.FocusApplication
import app.yike.focus.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Composable fun SecretGarden(onDismiss: () -> Unit) {
    val app=LocalContext.current.applicationContext as FocusApplication
    val snapshot by remember(app) {combine(app.repository.records,app.repository.gardenUsed) {records,used -> records to used}}.collectAsState(initial=null)
    val records=snapshot?.first.orEmpty();val used=snapshot?.second.orEmpty()
    val available=Garden.available(records,used).size
    val stage=Garden.stage(records,used);val harvests=Garden.harvests(records,used)
    var busy by remember {mutableStateOf(false)};var ripe by remember {mutableStateOf(false)}
    val workspace by app.repository.workspace.collectAsState(initial="{}")
    val decor = org.json.JSONObject(workspace).optInt("decor") and decorMask(harvests)
    var draft by remember(decor){ mutableIntStateOf(decor) }
    var decorating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.repository.reviseWorkspace { it.put("discovered", true) } }
    var collection by remember {mutableStateOf(false)};var message by remember {mutableStateOf<String?>(null)}
    var variety by remember {mutableIntStateOf(harvests%Garden.VARIETIES)}
    val growth=remember {Animatable(stage.toFloat())};val water=remember {Animatable(0f)}
    val sway=remember {Animatable(0f)};val picking=remember {Animatable(0f)}
    val scope=rememberCoroutineScope();val motion=motionMillis(700)
    val activeMotion by rememberUpdatedState(motion)
    LaunchedEffect(stage,harvests,busy,ripe) {
        if(snapshot!=null && !busy && !ripe) {growth.snapTo(stage.toFloat());variety=harvests%Garden.VARIETIES}
    }
    fun waterPlant() {
        // Function references may outlive the composition that created the drag receiver.
        // Read the live snapshot, rather than capturing derived counters from that frame.
        val current=snapshot ?: return
        if(busy || ripe || Garden.available(current.first,current.second).isEmpty()) return
        busy=true;message=null
        val before=Garden.stage(current.first,current.second)
        val fruit=Garden.harvests(current.first,current.second)%Garden.VARIETIES
        scope.launch {
            try {
                if(app.repository.waterGarden()) {
                    variety=fruit;water.snapTo(0f)
                    if(activeMotion>0) coroutineScope {
                        launch {water.animateTo(1f,tween(700))}
                        launch {delay(140);growth.animateTo(before+1f,tween(650))}
                    } else growth.snapTo(before+1f)
                    ripe=before==3
                    if(!ripe) message=listOf("种子喝到了第一滴水。","叶子舒展开来了。","花开了，番茄快要熟了。")[before]
                } else message="水滴暂时用完了，下一轮专注后再来看看。"
            } catch(e:CancellationException) {throw e}
            catch(_:Exception) {message="这滴水还没浇下去，请再试一次。"}
            finally {busy=false;water.snapTo(0f)}
        }
    }
    fun pickFruit() {
        if(!ripe || busy) return
        busy=true
        scope.launch {
            try {
                if(activeMotion>0) picking.animateTo(1f,tween(800))
                ripe=false
                val current=snapshot
                growth.snapTo(if(current==null) 0f else Garden.stage(current.first,current.second).toFloat())
                picking.snapTo(0f)
                message="${GardenVarieties[variety].name}，已经放进你的收获篮。"
            } finally {busy=false}
        }
    }
    fun touchPlant() {
        if(ripe) {pickFruit();return}
        if(activeMotion>0 && !sway.isRunning) scope.launch {
            sway.animateTo(-3f,tween(100));sway.animateTo(2f,tween(160));sway.animateTo(0f,tween(220))
        }
    }
    var target by remember {mutableStateOf(Rect.Zero)}
    val dialogDensity=LocalDensity.current
    Dialog(onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        CompositionLocalProvider(LocalInsideGlass provides true,LocalDensity provides dialogDensity) {
        BoxWithConstraints(Modifier.widthIn(max=1080.dp).fillMaxWidth().fillMaxHeight(.94f)
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)
            .then(if(LocalAppearance.current.interfaceMaterial==InterfaceMaterial.GLASS) Modifier.glassSurface(DialogShape,GlassRole.DIALOG)
                else Modifier.background(Paper,DialogShape)).testTag("secret_garden")) {
            val wide=maxWidth>=760.dp && LocalDensity.current.fontScale<1.6f
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if(wide) 28.dp else 20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("一刻秘密花园",style=MaterialTheme.typography.headlineMedium)
                        Text("你留给自己的时间，在这里生长。",color=Muted,style=MaterialTheme.typography.bodyMedium)
                    }
                    FocusTextButton(onDismiss) {Text("返回")}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FocusFilterChip(!collection && !decorating,{collection=false;decorating=false},label={Text("温室")},modifier=Modifier.testTag("garden_tab"))
                    FocusFilterChip(decorating,{draft=decor;decorating=true;collection=false},label={Text("布置")},modifier=Modifier.testTag("decorate_tab"))
                    FocusFilterChip(collection,{collection=true;decorating=false},label={Text("收获篮")},modifier=Modifier.testTag("collection_tab"))
                }
                if(decorating) {
                    GardenLandscape(stage.toFloat(),0f,0f,0f,variety,Modifier.fillMaxWidth().height(220.dp),harvests,draft)
                    listOf(Triple("青瓷花盆",1,1),Triple("胡桃木窗台",2,3),Triple("暮紫山景",4,6)).forEach { (name,bit,threshold) ->
                        SwitchRow(name, if(harvests>=threshold) "已解锁，可随时更换" else "第 $threshold 次收获解锁", draft and bit!=0) {
                            if(harvests>=threshold) draft=draft xor bit else message="第 $threshold 次收获后可布置$name。"
                        }
                    }
                    FocusButton({scope.launch { app.repository.reviseWorkspace { it.put("decor",draft and decorMask(harvests)) };decorating=false;message="新布置已经安放好了。" }}){Text("应用布置")}
                } else if(collection) GardenCollection(harvests)
                else {
                    val scene:@Composable (Modifier)->Unit={m ->
                        Box(m.onGloballyPositioned {target=it.boundsInWindow()}) {
                            GardenLandscape(growth.value,sway.value,water.value,picking.value,variety,
                                Modifier.fillMaxWidth().aspectRatio(if(wide) 1.12f else 1.18f)
                                    .clickable(role=Role.Button,onClick=::touchPlant).testTag("garden_scene"),collected=(harvests-if(ripe) 1 else 0).coerceAtLeast(0),decor=decor)
                            Text(if(ripe) "轻点番茄，收下这一刻" else "轻触叶子，和它打个招呼",Modifier.align(Alignment.BottomCenter).padding(16.dp),
                                style=MaterialTheme.typography.bodySmall,color=if(Paper.red<.5f) Color(0xFFE4ECE5) else Color(0xFF405849))
                        }
                    }
                    val controls:@Composable (Modifier)->Unit={m ->
                        Column(m,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            Text(if(ripe) "你的专注，结果了。" else listOf("一粒种子，一点期待。","新芽探出了头。","叶子舒展开来了。","花开了，番茄快要熟了。")[stage],style=MaterialTheme.typography.titleLarge)
                            Text("可用水滴 $available  ·  已收获 $harvests 颗番茄",color=Muted,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.testTag("garden_totals"))
                            GardenGrowthSteps(if(ripe) 4 else stage)
                            if(ripe) FocusButton(::pickFruit,Modifier.fillMaxWidth().testTag("pick_tomato"),enabled=!busy) {Text(if(busy) "轻轻收好…" else "收下这颗番茄")}
                            else {
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    FocusButton(::waterPlant,Modifier.weight(1f).testTag("water_garden"),enabled=snapshot!=null && available>0 && !busy) {Text(if(busy) "正在浇水…" else "浇一滴水")}
                                    DraggableGardenWater(available>0 && !busy && snapshot!=null,{target},::waterPlant)
                                }
                                Text(if(available>0) "也可以把小水滴拖到花盆上。" else "完整专注一轮，就会留下一滴水。",style=MaterialTheme.typography.bodySmall,color=Muted)
                            }
                            Text(message ?: "四滴水，收获一颗小番茄。\n不用赶时间，也不用担心它枯萎。",style=MaterialTheme.typography.bodyMedium,color=Muted)
                            Text("每次收获，遇见下一种小番茄。",style=MaterialTheme.typography.bodySmall,color=Muted)
                        }
                    }
                    if(wide) Row(horizontalArrangement=Arrangement.spacedBy(28.dp),verticalAlignment=Alignment.CenterVertically) {
                        scene(Modifier.weight(1.45f));controls(Modifier.weight(1f))
                    } else {scene(Modifier.fillMaxWidth());controls(Modifier.fillMaxWidth())}
                }
            }
        }
        }
    }
}

@Composable private fun DraggableGardenWater(enabled:Boolean,target:()->Rect,onWater:()->Unit) {
    var shift by remember {mutableStateOf(Offset.Zero)};var start by remember {mutableStateOf(Offset.Zero)}
    var bounds by remember {mutableStateOf(Rect.Zero)}
    val currentTarget by rememberUpdatedState(target);val currentWater by rememberUpdatedState(onWater)
    Box(Modifier.size(56.dp).zIndex(10f).onGloballyPositioned {bounds=it.boundsInWindow()}
        .pointerInput(enabled) {
            if(enabled) detectDragGestures(onDragStart={start=bounds.topLeft+it;shift=Offset.Zero},
                onDrag={change,amount -> change.consume();shift+=amount},
                onDragEnd={val inside=currentTarget().contains(start+shift);shift=Offset.Zero;if(inside) currentWater()},
                onDragCancel={shift=Offset.Zero})
        // Keep the gesture receiver outside the translated layer: pointer deltas must
        // stay in the stationary window coordinate system while the drop follows them.
        }.graphicsLayer {translationX=shift.x;translationY=shift.y}.glassSurface(ButtonShape,GlassRole.CONTROL,sampleMotion={shift})
        .clickable(enabled=enabled,role=Role.Button,onClick=onWater)
        .semantics {contentDescription="拖动水滴到花盆，也可轻点浇水"}.testTag("garden_drag_water"),contentAlignment=Alignment.Center) {
        GardenDrop(Color(0xFF5389A1),Modifier.size(26.dp))
    }
}

@Composable private fun GardenGrowthSteps(stage:Int) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        repeat(4) {i -> Box(Modifier.weight(1f).height(5.dp).background(if(i<stage) Sage else Sage.copy(alpha=.16f),SmallShape))}
    }
}

@Composable internal fun GardenCollection(harvests:Int) {
    var detail by remember {mutableIntStateOf(-1)}
    if(detail in GardenVarieties.indices) FocusAlertDialog({detail=-1},title={Text(GardenVarieties[detail].name)},text={Column{GardenFruit(detail,harvests>detail,Modifier.fillMaxWidth().height(160.dp));Text("第 ${detail+1} 次收获时相见 · 已收藏 ${Garden.varietyCount(harvests,detail)} 颗")}},confirmButton={FocusTextButton({detail=-1}){Text("关闭")}})
    Column(verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text("把努力，慢慢攒成一篮甜。",style=MaterialTheme.typography.titleLarge)
        Text("已收获 $harvests 颗番茄 · 已遇见 ${minOf(harvests,Garden.VARIETIES)} 种",color=Muted,style=MaterialTheme.typography.bodyMedium)
        BoxWithConstraints {
            val columns=when {
                maxWidth<420.dp || LocalDensity.current.fontScale>=1.6f -> 1
                maxWidth>=680.dp -> 3
                else -> 2
            }
            Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                GardenVarieties.chunked(columns).forEach {row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        row.forEach {v -> val i=GardenVarieties.indexOf(v);val count=Garden.varietyCount(harvests,i)
                            PaperCard(Modifier.weight(1f).clickable { detail=i }.testTag("garden_variety_$i")) {
                                GardenFruit(i,count>0,Modifier.fillMaxWidth().height(108.dp))
                                Text(if(count>0) v.name else "还没遇见",style=MaterialTheme.typography.titleMedium)
                                Text(if(count>0) "收藏 $count 颗" else "第 ${i+1} 次收获时相见",color=Muted,style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Stable preview API retained for the existing growth-stage render tests. */
@Composable internal fun GardenPlant(stage:Int,sway:Float,modifier:Modifier=Modifier) =
    GardenLandscape(stage.toFloat(),sway,0f,0f,0,modifier)

internal fun decorMask(harvests:Int) = when { harvests>=6 ->7; harvests>=3 ->3; harvests>=1 ->1; else ->0 }

@Composable internal fun GardenWidget() {
    val repo=LocalDeskRepo.current ?: return
    val snapshot by remember(repo){combine(repo.records,repo.gardenUsed){records, used ->
        Triple(Garden.available(records,used).size,Garden.stage(records,used),Garden.harvests(records,used))
    }}.collectAsState(initial=Triple(0,0,0))
    var open by remember {mutableStateOf(false)}
    PaperCard(Modifier.fillMaxWidth()) {
        Text("我的花房",style=MaterialTheme.typography.titleMedium)
        Text("${listOf("种子","萌芽","长叶","开花")[snapshot.second]} · ${snapshot.first} 滴水 · ${snapshot.third} 次收获")
        FocusTextButton({open=true}){Text("去温室看看")}
    }
    if(open) SecretGarden {open=false}
}
