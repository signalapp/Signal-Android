/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.theme.LocalExtendedColors

/**
 * Rounded, inset highlight drawn behind a row while it's pressed, focused, or hovered.
 */
object RowPressIndication : IndicationNodeFactory {
  override fun create(interactionSource: InteractionSource): DelegatableNode = RowPressIndicationNode(interactionSource)

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = javaClass.hashCode()
}

private class RowPressIndicationNode(
  private val interactionSource: InteractionSource
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

  private val alpha = Animatable(0f)
  private var animationJob: Job? = null

  override fun onAttach() {
    coroutineScope.launch {
      val active = mutableSetOf<Interaction>()
      interactionSource.interactions.collect { interaction ->
        val wasActive = active.isNotEmpty()
        when (interaction) {
          is PressInteraction.Press, is FocusInteraction.Focus, is HoverInteraction.Enter -> active += interaction
          is PressInteraction.Release -> active -= interaction.press
          is PressInteraction.Cancel -> active -= interaction.press
          is FocusInteraction.Unfocus -> active -= interaction.focus
          is HoverInteraction.Exit -> active -= interaction.enter
        }

        val isActive = active.isNotEmpty()
        if (isActive && !wasActive) {
          animationJob?.cancel()
          animationJob = launch { alpha.animateTo(1f, tween(durationMillis = 100)) }
        } else if (!isActive && wasActive) {
          val pendingFadeIn = animationJob
          animationJob = launch {
            pendingFadeIn?.join()
            alpha.animateTo(0f, tween(durationMillis = 150))
          }
        }
      }
    }
  }

  override fun ContentDrawScope.draw() {
    val currentAlpha = alpha.value
    if (currentAlpha > 0f) {
      val inset = 12.dp.toPx()
      val cornerRadius = 18.dp.toPx()
      drawRoundRect(
        color = currentValueOf(LocalExtendedColors).colorSurface3,
        topLeft = Offset(inset, 0f),
        size = Size(size.width - inset * 2, size.height),
        cornerRadius = CornerRadius(cornerRadius),
        alpha = currentAlpha
      )
    }
    drawContent()
  }
}
