/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.max
import kotlin.math.min

/**
 * Stores information related to the positional and display state of a
 * [TriggerAlignedPopup].
 */
@Stable
class TriggerAlignedPopupState private constructor(
  initialDisplay: Boolean = false,
  initialTriggerBounds: IntRect = IntRect.Zero
) {

  var display by mutableStateOf(initialDisplay)

  private var triggerBounds by mutableStateOf(initialTriggerBounds)

  val popupPositionProvider = derivedStateOf<PopupPositionProvider> {
    object : PopupPositionProvider {
      override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val desiredXOffset = triggerBounds.left + triggerBounds.width / 2 - popupContentSize.width / 2
        val maxXOffset = windowSize.width - popupContentSize.width

        return IntOffset(max(0, min(desiredXOffset, maxXOffset)), anchorBounds.top - popupContentSize.height)
      }
    }
  }

  companion object {

    @Composable
    fun rememberTriggerAlignedPopupState(): TriggerAlignedPopupState {
      return rememberSaveable(
        saver = Saver(
          save = {
            it.display to it.triggerBounds
          },
          restore = {
            TriggerAlignedPopupState(
              it.first,
              it.second
            )
          }
        )
      ) {
        TriggerAlignedPopupState()
      }
    }

    /**
     * Sets the given composable as the popup trigger. This does NOT
     * display the popup. Rather, it just sets positional information
     * in the state. It is still up to the caller to call `state.displayed = true`
     * in order to display the popup itself.
     */
    fun Modifier.popupTrigger(state: TriggerAlignedPopupState): Modifier {
      return this.onPlaced {
        state.triggerBounds = it.boundsInWindow().roundToIntRect()
      }
    }
  }
}

/**
 * Focusable popup window that aligns itself with its trigger, if provided.
 *
 * See [TriggerAlignedPopupState.Companion.popupTrigger] for more information.
 */
@Composable
fun TriggerAlignedPopup(
  state: TriggerAlignedPopupState,
  onDismissRequest: () -> Unit = { state.display = false },
  content: @Composable () -> Unit
) {
  if (state.display) {
    val positionProvider by state.popupPositionProvider
    Popup(
      properties = PopupProperties(focusable = true),
      onDismissRequest = onDismissRequest,
      popupPositionProvider = positionProvider
    ) {
      (LocalView.current.parent as? DialogWindowProvider)?.apply {
        this.window.setBackgroundDrawable(ColorDrawable(0))
      }

      content()
    }
  }
}
