package org.thoughtcrime.securesms.components.settings.app.chats.folders

import android.content.Context
import android.view.View
import android.view.ViewGroup
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu

/**
 * A context menu shown when long pressing on a chat folder.
 */
object ChatFolderContextMenu {

  fun show(
    context: Context,
    anchorView: View,
    rootView: ViewGroup = anchorView.rootView as ViewGroup,
    folderType: ChatFolderRecord.FolderType,
    onEdit: () -> Unit = {},
    onAdd: () -> Unit = {},
    onMuteAll: () -> Unit = {},
    onReadAll: () -> Unit = {},
    onDelete: () -> Unit = {},
    onReorder: () -> Unit = {}
  ) {
    show(
      context = context,
      anchorView = anchorView,
      rootView = rootView,
      folderType = folderType,
      callbacks = object : Callbacks {
        override fun onEdit() = onEdit()
        override fun onAdd() = onAdd()
        override fun onMuteAll() = onMuteAll()
        override fun onReadAll() = onReadAll()
        override fun onDelete() = onDelete()
        override fun onReorder() = onReorder()
      }
    )
  }

  private fun show(
    context: Context,
    anchorView: View,
    rootView: ViewGroup,
    folderType: ChatFolderRecord.FolderType,
    callbacks: Callbacks
  ) {
    val actions = mutableListOf<ActionItem>().apply {
      if (folderType == ChatFolderRecord.FolderType.ALL) {
        add(
          ActionItem(R.drawable.symbol_plus_24, context.getString(R.string.ChatFoldersFragment__add_new_folder)) {
            callbacks.onAdd()
          }
        )
        add(
          ActionItem(R.drawable.symbol_bell_slash_24, context.getString(R.string.ChatFoldersFragment__mute_all)) {
            callbacks.onMuteAll()
          }
        )
        add(
          ActionItem(R.drawable.symbol_chat_check, context.getString(R.string.ChatFoldersFragment__mark_all_read)) {
            callbacks.onReadAll()
          }
        )
        add(
          ActionItem(R.drawable.symbol_exchange_24, context.getString(R.string.ChatFoldersFragment__reorder_folder)) {
            callbacks.onReorder()
          }
        )
      } else {
        add(
          ActionItem(R.drawable.symbol_edit_24, context.getString(R.string.ChatFoldersFragment__edit_folder)) {
            callbacks.onEdit()
          }
        )
        add(
          ActionItem(R.drawable.symbol_plus_24, context.getString(R.string.ChatFoldersFragment__add_new_folder)) {
            callbacks.onAdd()
          }
        )
        add(
          ActionItem(R.drawable.symbol_bell_slash_24, context.getString(R.string.ChatFoldersFragment__mute_all)) {
            callbacks.onMuteAll()
          }
        )
        add(
          ActionItem(R.drawable.symbol_chat_check, context.getString(R.string.ChatFoldersFragment__mark_all_read)) {
            callbacks.onReadAll()
          }
        )
        add(
          ActionItem(R.drawable.symbol_trash_24, context.getString(R.string.ChatFoldersFragment__delete_folder)) {
            callbacks.onDelete()
          }
        )
        add(
          ActionItem(R.drawable.symbol_exchange_24, context.getString(R.string.ChatFoldersFragment__reorder_folder)) {
            callbacks.onReorder()
          }
        )
      }
    }

    SignalContextMenu.Builder(anchorView, rootView)
      .preferredHorizontalPosition(SignalContextMenu.HorizontalPosition.START)
      .preferredVerticalPosition(SignalContextMenu.VerticalPosition.BELOW)
      .offsetY(DimensionUnit.DP.toPixels(8f).toInt())
      .show(actions)
  }

  private interface Callbacks {
    fun onEdit()
    fun onAdd()
    fun onMuteAll()
    fun onReadAll()
    fun onDelete()
    fun onReorder()
  }
}
