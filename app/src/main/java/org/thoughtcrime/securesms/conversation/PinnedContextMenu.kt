package org.thoughtcrime.securesms.conversation

import android.content.Context
import android.view.View
import android.view.ViewGroup
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.util.hasGiftBadge
import org.thoughtcrime.securesms.util.isPoll
import org.thoughtcrime.securesms.util.isViewOnceMessage

/**
 * A context menu shown when long pressing on a pinned messages
 */
object PinnedContextMenu {

  fun show(
    context: Context,
    anchorView: View,
    rootView: ViewGroup = anchorView.rootView as ViewGroup,
    message: MmsMessageRecord,
    isGroup: Boolean,
    canUnpin: Boolean,
    onUnpin: () -> Unit = {},
    onCopy: () -> Unit = {},
    onDelete: () -> Unit = {},
    onSave: () -> Unit = {}
  ) {
    show(
      context = context,
      anchorView = anchorView,
      rootView = rootView,
      message = message,
      isGroup = isGroup,
      canUnpin = canUnpin,
      callbacks = object : Callbacks {
        override fun onUnpin() = onUnpin()
        override fun onCopy() = onCopy()
        override fun onDelete() = onDelete()
        override fun onSave() = onSave()
      }
    )
  }

  private fun show(
    context: Context,
    anchorView: View,
    rootView: ViewGroup,
    message: MmsMessageRecord,
    isGroup: Boolean,
    canUnpin: Boolean,
    callbacks: Callbacks
  ) {
    val actions = mutableListOf<ActionItem>().apply {
      if (canUnpin) {
        add(
          ActionItem(R.drawable.symbol_pin_slash_24, context.getString(R.string.PinnedMessage__unpin)) {
            callbacks.onUnpin()
          }
        )
      }

      if (message.body.isNotEmpty() &&
        !message.isRemoteDelete &&
        !message.isPaymentNotification &&
        !message.isPoll() &&
        !message.hasGiftBadge()
      ) {
        add(
          ActionItem(R.drawable.symbol_copy_android_24, context.getString(R.string.conversation_selection__menu_copy)) {
            callbacks.onCopy()
          }
        )
      }

      if (!message.isRemoteDelete) {
        add(
          ActionItem(R.drawable.symbol_trash_24, context.getString(R.string.conversation_selection__menu_delete)) {
            callbacks.onDelete()
          }
        )
      }

      if (
        !message.isViewOnceMessage() &&
        !message.isMediaPending &&
        !message.hasGiftBadge() &&
        message.containsMediaSlide() &&
        message.slideDeck.getStickerSlide() == null
      ) {
        add(
          ActionItem(R.drawable.symbol_save_android_24, context.getString(R.string.conversation_selection__menu_save)) {
            callbacks.onSave()
          }
        )
      }
    }

    val horizontalPosition = if (message.isOutgoing) SignalContextMenu.HorizontalPosition.END else SignalContextMenu.HorizontalPosition.START
    val offsetX = if (message.isOutgoing || !isGroup) 16f else 48f
    SignalContextMenu.Builder(anchorView, rootView)
      .preferredHorizontalPosition(horizontalPosition)
      .preferredVerticalPosition(SignalContextMenu.VerticalPosition.BELOW)
      .offsetX(DimensionUnit.DP.toPixels(offsetX).toInt())
      .offsetY(DimensionUnit.DP.toPixels(4f).toInt())
      .show(actions)
  }

  private interface Callbacks {
    fun onUnpin()
    fun onCopy()
    fun onDelete()
    fun onSave()
  }
}
