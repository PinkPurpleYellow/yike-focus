package app.yike.focus.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Pointer presses never paint a slab; keyboard accessibility focus still has an outline. */
internal data class FocusIndication(val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): Modifier.Node = object : Modifier.Node(), DrawModifierNode {
        private val focuses = mutableSetOf<FocusInteraction.Focus>()
        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect {
                    when (it) {
                        is FocusInteraction.Focus -> focuses.add(it)
                        is FocusInteraction.Unfocus -> focuses.remove(it.focus)
                    }
                    invalidateDraw()
                }
            }
        }
        override fun ContentDrawScope.draw() {
            drawContent()
            if (focuses.isNotEmpty()) drawRoundRect(color, cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable internal fun Modifier.focusOutline(source: InteractionSource, shape: Shape = ButtonShape): Modifier {
    val focused by source.collectIsFocusedAsState()
    return if (focused) border(2.dp, Tomato, shape) else this
}
