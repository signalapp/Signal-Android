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
    unreadCount: Int,
    isEmpty: Boolean,
    isMuted: Boolean,
    onEdit: () -> Unit = {},
    onMuteAll: () -> Unit = {},
    onUnmuteAll: () -> Unit = {},
    onReadAll: () -> Unit = {},
    onFolderSettings: () -> Unit = {}
  ) {
    show(
      context = context,
      anchorView = anchorView,
      rootView = rootView,
      folderType = folderType,
      unreadCount = unreadCount,
      isEmpty = isEmpty,
      isMuted = isMuted,
      callbacks = object : Callbacks {
        override fun onEdit() = onEdit()
        override fun onMuteAll() = onMuteAll()
        override fun onUnmuteAll() = onUnmuteAll()
        override fun onReadAll() = onReadAll()
        override fun onFolderSettings() = onFolderSettings()
      }
    )
  }

  private fun show(
    context: Context,
    anchorView: View,
    rootView: ViewGroup,
    unreadCount: Int,
    isEmpty: Boolean,
    isMuted: Boolean,
    folderType: ChatFolderRecord.FolderType,
    callbacks: Callbacks
  ) {
    val actions = mutableListOf<ActionItem>().apply {
      if (unreadCount > 0) {
        add(
          ActionItem(R.drawable.symbol_chat_check, context.getString(R.string.ChatFoldersFragment__mark_all_read)) {
            callbacks.onReadAll()
          }
        )
      }

      if (isMuted && !isEmpty) {
        add(
          ActionItem(R.drawable.symbol_bell_24, context.getString(R.string.ChatFoldersFragment__unmute_all)) {
            callbacks.onUnmuteAll()
          }
        )
      } else if (!isEmpty) {
        add(
          ActionItem(R.drawable.symbol_bell_slash_24, context.getString(R.string.ChatFoldersFragment__mute_all)) {
            callbacks.onMuteAll()
          }
        )
      }

      if (folderType == ChatFolderRecord.FolderType.ALL) {
        add(
          ActionItem(R.drawable.symbol_folder_settings, context.getString(R.string.conversation_list_fragment__folder_settings)) {
            callbacks.onFolderSettings()
          }
        )
      } else {
        add(
          ActionItem(R.drawable.symbol_edit_24, context.getString(R.string.ChatFoldersFragment__edit_folder)) {
            callbacks.onEdit()
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
    fun onMuteAll()
    fun onUnmuteAll()
    fun onReadAll()
    fun onFolderSettings()
  }
}
