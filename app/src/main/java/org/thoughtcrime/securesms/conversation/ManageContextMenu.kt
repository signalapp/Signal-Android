package org.thoughtcrime.securesms.conversation

import android.content.Context
import android.view.View
import android.view.ViewGroup
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu

/**
 * A context menu shown when handling selected media only permissions.
 * Will give users the ability to go to settings or to choose more media to give permission to
 */
object ManageContextMenu {

  fun show(
    context: Context,
    anchorView: View,
    rootView: ViewGroup = anchorView.rootView as ViewGroup,
    showAbove: Boolean = false,
    showAtStart: Boolean = false,
    onSelectMore: () -> Unit,
    onSettings: () -> Unit
  ) {
    show(
      context = context,
      anchorView = anchorView,
      rootView = rootView,
      showAbove = showAbove,
      showAtStart = showAtStart,
      callbacks = object : Callbacks {
        override fun onSelectMore() = onSelectMore()
        override fun onSettings() = onSettings()
      }
    )
  }

  private fun show(
    context: Context,
    anchorView: View,
    rootView: ViewGroup = anchorView.rootView as ViewGroup,
    showAbove: Boolean = false,
    showAtStart: Boolean = false,
    callbacks: Callbacks
  ) {
    val actions = mutableListOf<ActionItem>().apply {
      add(
        ActionItem(R.drawable.symbol_settings_android_24, context.getString(R.string.AttachmentKeyboard_go_to_settings)) {
          callbacks.onSettings()
        }
      )
      add(
        ActionItem(R.drawable.symbol_album_tilt_24, context.getString(R.string.AttachmentKeyboard_select_more_photos)) {
          callbacks.onSelectMore()
        }
      )
    }

    if (!showAbove) {
      actions.reverse()
    }

    SignalContextMenu.Builder(anchorView, rootView)
      .preferredHorizontalPosition(if (showAtStart) SignalContextMenu.HorizontalPosition.START else SignalContextMenu.HorizontalPosition.END)
      .preferredVerticalPosition(if (showAbove) SignalContextMenu.VerticalPosition.ABOVE else SignalContextMenu.VerticalPosition.BELOW)
      .offsetY(DimensionUnit.DP.toPixels(8f).toInt())
      .show(actions)
  }

  private interface Callbacks {
    fun onSelectMore()
    fun onSettings()
  }
}
