package app.yike.focus.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import app.yike.focus.domain.InterfaceMaterial
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun motionMillis(value: Int): Int =
    if (LocalAppearance.current.decorativeMotion && ValueAnimator.areAnimatorsEnabled()) value else 0

@Composable
private fun buttonFeedback(): Pair<MutableInteractionSource, Float> {
    val source = remember { MutableInteractionSource() }
    return source to 1f
}

@Composable
fun FocusButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonShape, colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit) {
    val (source, opacity) = buttonFeedback()
    val touch = rememberGlassInteraction()
    val glass = LocalAppearance.current.interfaceMaterial == InterfaceMaterial.GLASS
    Button(onClick, modifier.heightIn(min = 52.dp).glassTouch(touch).glassSurface(shape, GlassRole.CONTROL, interaction = touch).focusOutline(source, shape).graphicsLayer { alpha = opacity }, enabled,
        shape = shape, colors = if (glass) ButtonDefaults.buttonColors(containerColor = Tomato.copy(alpha = .16f), contentColor = Ink) else colors, contentPadding = contentPadding, interactionSource = source, content = content)
}

@Composable
fun FocusOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonShape, colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    content: @Composable RowScope.() -> Unit) {
    val (source, opacity) = buttonFeedback()
    val touch = rememberGlassInteraction()
    OutlinedButton(onClick, modifier.glassTouch(touch).glassSurface(shape, GlassRole.CONTROL, interaction = touch).focusOutline(source, shape).heightIn(min = 52.dp).graphicsLayer { alpha = opacity }, enabled,
        shape = shape, border = if (LocalAppearance.current.interfaceMaterial == InterfaceMaterial.GLASS) null else ButtonDefaults.outlinedButtonBorder(enabled), colors = colors, interactionSource = source,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), content = content)
}

@Composable
fun FocusTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit) {
    val (source, opacity) = buttonFeedback()
    TextButton(onClick, modifier.heightIn(min = 48.dp).focusOutline(source).graphicsLayer { alpha = opacity }, enabled,
        shape = ButtonShape, interactionSource = source, contentPadding = contentPadding, content = content)
}

@Composable
fun FocusTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonShape, colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    content: @Composable RowScope.() -> Unit) {
    val (source, opacity) = buttonFeedback()
    val touch = rememberGlassInteraction()
    FilledTonalButton(onClick, modifier.glassTouch(touch).glassSurface(shape, GlassRole.CONTROL, interaction = touch).focusOutline(source, shape).heightIn(min = 52.dp).graphicsLayer { alpha = opacity }, enabled,
        shape = shape, colors = if (LocalAppearance.current.interfaceMaterial == InterfaceMaterial.GLASS) ButtonDefaults.filledTonalButtonColors(containerColor = Color.Transparent, contentColor = Ink) else colors, interactionSource = source, content = content)
}

@Composable fun FocusFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, shape: Shape = SmallShape, colors: SelectableChipColors = FilterChipDefaults.filterChipColors()) {
    val source = remember { MutableInteractionSource() }
    if (LocalAppearance.current.interfaceMaterial != InterfaceMaterial.GLASS) {
        FilterChip(selected, onClick, label, modifier = modifier.focusOutline(source, shape), shape = shape, colors = colors, interactionSource = source)
        return
    }
    // The target owns input. Only the inset visual owns material, tint and shape.
    Box(modifier.heightIn(min = 48.dp).selectable(selected, interactionSource = source,
        indication = null, role = Role.RadioButton, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.glassSurface(shape, GlassRole.CONTROL)
            .background(if (selected) Tomato.copy(alpha = .16f) else Color.Transparent, shape)
            .focusOutline(source, shape).padding(horizontal = 14.dp, vertical = 7.dp)) {
            CompositionLocalProvider(LocalContentColor provides if (selected) Tomato else Ink, content = label)
        }
    }
}


@Composable fun FocusIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, tonal: Boolean = false,
    content: @Composable () -> Unit) {
    val touch = rememberGlassInteraction()
    val source = remember { MutableInteractionSource() }
    if(LocalAppearance.current.interfaceMaterial != InterfaceMaterial.GLASS) {
        if(tonal) FilledTonalIconButton(onClick,modifier.focusOutline(source),enabled=enabled,interactionSource=source,
            colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=Peach),content=content)
        else IconButton(onClick,modifier.focusOutline(source),enabled=enabled,interactionSource=source,content=content)
        return
    }
    Box(modifier.sizeIn(minWidth = 48.dp,minHeight = 48.dp)
        .focusOutline(source).clickable(enabled=enabled, role=Role.Button, interactionSource=source,
            indication=androidx.compose.foundation.LocalIndication.current, onClick=onClick), contentAlignment=Alignment.Center) {
        Box(Modifier.size(38.dp).glassTouch(touch).glassSurface(ButtonShape,GlassRole.CONTROL,interaction=touch)
            .graphicsLayer { alpha = if(enabled) 1f else .46f }, contentAlignment=Alignment.Center) { content() }
    }
}

@Composable fun FocusSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (LocalAppearance.current.interfaceMaterial != InterfaceMaterial.GLASS) {
        Switch(checked,onCheckedChange,modifier,enabled=enabled); return
    }
    val touch = rememberGlassInteraction()
    var drag by remember { mutableStateOf<Float?>(null) }
    val distance = with(LocalDensity.current) { 24.dp.toPx() }
    val fraction by animateFloatAsState(if(checked) 1f else 0f,tween(motionMillis(200)),label="glass_switch")
    val source = remember { MutableInteractionSource() }
    val active = enabled && onCheckedChange != null
    val currentChecked by rememberUpdatedState(checked)
    val currentChange by rememberUpdatedState(onCheckedChange)
    val position = (drag ?: fraction).coerceIn(0f,1f)
    Box(modifier.size(64.dp,48.dp).glassTouch(touch).focusOutline(source,CircleShape)
        .toggleable(checked,enabled=active,role=Role.Switch,interactionSource=source,
            indication=androidx.compose.foundation.LocalIndication.current,onValueChange={ currentChange?.invoke(it) })
        .draggable(rememberDraggableState { delta -> drag=((drag ?: if(currentChecked) 1f else 0f)+delta/distance).coerceIn(0f,1f) },
            Orientation.Horizontal,enabled=active,onDragStopped={ if(drag!=null) currentChange?.invoke(drag!! >= .5f); drag=null }),
        contentAlignment=Alignment.Center) {
        Box(Modifier.size(56.dp,32.dp).glassSurface(CircleShape,GlassRole.CONTROL)
            .background(Tomato.copy(alpha=if(checked) .25f else .045f),CircleShape)) {
            Box(Modifier.offset { IntOffset((4.dp.toPx()+24.dp.toPx()*position).roundToInt(),4.dp.roundToPx()) }
                .size(24.dp).glassDeform(touch).glassSurface(CircleShape,GlassRole.CONTROL,interaction=touch))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FocusSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, onValueChangeFinished: (() -> Unit)? = null) {
    if (LocalAppearance.current.interfaceMaterial != InterfaceMaterial.GLASS) {
        Slider(value,onValueChange,modifier,enabled=enabled,valueRange=valueRange,onValueChangeFinished=onValueChangeFinished); return
    }
    val touch=rememberGlassInteraction()
    val source=remember { MutableInteractionSource() }
    val colors=SliderDefaults.colors(activeTrackColor=Tomato.copy(alpha=.65f),inactiveTrackColor=Ink.copy(alpha=.10f))
    Slider(value,onValueChange,modifier.heightIn(min=48.dp).glassTouch(touch).focusOutline(source),enabled=enabled,
        valueRange=valueRange,onValueChangeFinished=onValueChangeFinished,interactionSource=source,colors=colors,
        thumb={ Box(Modifier.size(40.dp,28.dp).glassDeform(touch).glassSurface(CircleShape,GlassRole.CONTROL,interaction=touch)) },
        track={ SliderDefaults.Track(it,modifier=Modifier.height(6.dp),colors=colors) })
}

@Composable fun FocusCheckbox(checked: Boolean,onCheckedChange: ((Boolean)->Unit)?,modifier: Modifier=Modifier,enabled: Boolean=true) {
    if(LocalAppearance.current.interfaceMaterial != InterfaceMaterial.GLASS) {
        Checkbox(checked,onCheckedChange,modifier,enabled=enabled);return
    }
    val touch=rememberGlassInteraction()
    Box(modifier.size(48.dp).glassTouch(touch).toggleable(checked,enabled=enabled&&onCheckedChange!=null,
        role=Role.Checkbox,onValueChange={ onCheckedChange?.invoke(it) }),contentAlignment=Alignment.Center) {
        Box(Modifier.size(24.dp).glassSurface(RoundedCornerShape(7.dp),GlassRole.CONTROL,interaction=touch)
            .background(Tomato.copy(alpha=if(checked) .17f else 0f),RoundedCornerShape(7.dp)),contentAlignment=Alignment.Center) {
            if(checked) Glyph("check",Modifier.size(18.dp),Tomato)
        }
    }
}

@Composable fun FocusRadioButton(selected: Boolean,onClick: (() -> Unit)?,modifier: Modifier=Modifier,enabled:Boolean=true) {
    if(LocalAppearance.current.interfaceMaterial != InterfaceMaterial.GLASS) { RadioButton(selected,onClick,modifier,enabled=enabled);return }
    val touch=rememberGlassInteraction()
    Box(modifier.size(48.dp).glassTouch(touch).selectable(selected,enabled=enabled&&onClick!=null,role=Role.RadioButton,onClick={onClick?.invoke()}),contentAlignment=Alignment.Center) {
        Box(Modifier.size(24.dp).glassDeform(touch).glassSurface(CircleShape,GlassRole.CONTROL,interaction=touch),contentAlignment=Alignment.Center) {
            if(selected) Box(Modifier.size(10.dp).background(Tomato,CircleShape))
        }
    }
}

@Composable fun FocusDropdownMenu(expanded:Boolean,onDismissRequest:()->Unit,content:@Composable ColumnScope.()->Unit) {
    if(LocalAppearance.current.interfaceMaterial != InterfaceMaterial.GLASS) { DropdownMenu(expanded,onDismissRequest,content=content);return }
    if(expanded) Popup(alignment=Alignment.TopEnd,onDismissRequest=onDismissRequest,properties=PopupProperties(focusable=true)) {
        Column(Modifier.widthIn(min=180.dp,max=320.dp).glassSurface(DialogShape,GlassRole.DIALOG).padding(8.dp)) { content() }
    }
}

@Composable fun FocusSnackbarHost(hostState:SnackbarHostState,modifier:Modifier=Modifier) {
    SnackbarHost(hostState,modifier) { data ->
        if(LocalAppearance.current.interfaceMaterial != InterfaceMaterial.GLASS) Snackbar(data)
        else Row(Modifier.padding(12.dp).glassSurface(DialogShape,GlassRole.DIALOG).padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(data.visuals.message,Modifier.weight(1f),color=Ink)
            data.visuals.actionLabel?.let { label -> FocusTextButton({data.performAction()}) {Text(label)} }
        }
    }
}

@Composable private fun focusFieldColors(): TextFieldColors = if(LocalAppearance.current.interfaceMaterial == InterfaceMaterial.GLASS)
    OutlinedTextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,
        disabledContainerColor=Color.Transparent,errorContainerColor=Color.Transparent,
        focusedBorderColor=Tomato.copy(alpha=.5f),unfocusedBorderColor=Line.copy(alpha=.45f),
        focusedLabelColor=Ink,unfocusedLabelColor=Muted) else OutlinedTextFieldDefaults.colors()

@Composable fun FocusTextField(value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,
    label:(@Composable ()->Unit)?=null,placeholder:(@Composable ()->Unit)?=null,supportingText:(@Composable ()->Unit)?=null,
    minLines:Int=1,maxLines:Int=Int.MAX_VALUE,keyboardOptions:KeyboardOptions=KeyboardOptions.Default,
    keyboardActions:KeyboardActions=KeyboardActions.Default) {
    OutlinedTextField(value,onValueChange,modifier.glassSurface(SmallShape,GlassRole.CONTROL),label=label,placeholder=placeholder,
        supportingText=supportingText,minLines=minLines,maxLines=maxLines,keyboardOptions=keyboardOptions,keyboardActions=keyboardActions,
        shape=SmallShape,colors=focusFieldColors())
}

@Composable fun FocusTextField(value:TextFieldValue,onValueChange:(TextFieldValue)->Unit,modifier:Modifier=Modifier,
    label:(@Composable ()->Unit)?=null,suffix:(@Composable ()->Unit)?=null,supportingText:(@Composable ()->Unit)?=null,
    singleLine:Boolean=false,isError:Boolean=false,keyboardOptions:KeyboardOptions=KeyboardOptions.Default) {
    OutlinedTextField(value,onValueChange,modifier.glassSurface(SmallShape,GlassRole.CONTROL),label=label,suffix=suffix,
        supportingText=supportingText,singleLine=singleLine,isError=isError,keyboardOptions=keyboardOptions,
        shape=SmallShape,colors=focusFieldColors())
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FocusDatePickerDialog(onDismissRequest:()->Unit,confirmButton:@Composable ()->Unit,
    dismissButton:@Composable ()->Unit,content:@Composable ColumnScope.()->Unit) {
    if(LocalAppearance.current.interfaceMaterial != InterfaceMaterial.GLASS) {
        DatePickerDialog(onDismissRequest,confirmButton=confirmButton,dismissButton=dismissButton,content=content)
    } else FocusAlertDialog(onDismissRequest,confirmButton=confirmButton,dismissButton=dismissButton,
        text={ Column(Modifier.heightIn(max=540.dp)) { content() } })
}

@Composable fun FocusMenuItem(text:@Composable ()->Unit,onClick:()->Unit) {
    val source=remember {MutableInteractionSource()}
    DropdownMenuItem(text,onClick,modifier=Modifier.focusOutline(source),interactionSource=source)
}
