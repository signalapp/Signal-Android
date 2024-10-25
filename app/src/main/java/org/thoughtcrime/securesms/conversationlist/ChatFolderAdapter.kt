package org.thoughtcrime.securesms.conversationlist

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderContextMenu
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible

/**
* RecyclerView adapter for the chat folders displayed on conversation list
*/
class ChatFolderAdapter(val callbacks: Callbacks) : MappingAdapter() {

  init {
    registerFactory(ChatFolderMappingModel::class.java, LayoutFactory({ v -> ViewHolder(v, callbacks) }, R.layout.chat_folder_item))
  }

  class ViewHolder(itemView: View, private val callbacks: Callbacks) : MappingViewHolder<ChatFolderMappingModel>(itemView) {

    private val name: TextView = findViewById(R.id.name)
    private val unreadCount: TextView = findViewById(R.id.unread_count)

    override fun bind(model: ChatFolderMappingModel) {
      itemView.isSelected = model.isSelected

      val folder = model.chatFolder
      name.text = getName(itemView.context, folder)
      unreadCount.visible = folder.unreadCount > 0
      unreadCount.text = if (folder.unreadCount > 99) itemView.context.getString(R.string.ChatFolderAdapter__99p) else folder.unreadCount.toString()
      itemView.setOnClickListener {
        callbacks.onChatFolderClicked(model.chatFolder)
      }
      itemView.setOnLongClickListener { view ->
        ChatFolderContextMenu.show(
          context = itemView.context,
          anchorView = view,
          folderType = model.chatFolder.folderType,
          unreadCount = folder.unreadCount,
          isMuted = folder.isMuted,
          onEdit = { callbacks.onEdit(model.chatFolder) },
          onMuteAll = { callbacks.onMuteAll(model.chatFolder) },
          onUnmuteAll = { callbacks.onUnmuteAll(model.chatFolder) },
          onReadAll = { callbacks.onReadAll(model.chatFolder) },
          onFolderSettings = { callbacks.onFolderSettings() }
        )
        true
      }
      if (model.isSelected) {
        itemView.backgroundTintList = if (callbacks.isScrolled()) {
          ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.signal_colorBackground))
        } else {
          ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.signal_colorSurface2))
        }
      } else {
        itemView.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.transparent))
      }
    }

    private fun getName(context: Context, folder: ChatFolderRecord): String {
      return if (folder.folderType == ChatFolderRecord.FolderType.ALL) {
        context.getString(R.string.ChatFoldersFragment__all_chats)
      } else {
        folder.name
      }
    }
  }

  interface Callbacks {
    fun onChatFolderClicked(chatFolder: ChatFolderRecord)
    fun onEdit(chatFolder: ChatFolderRecord)
    fun onMuteAll(chatFolder: ChatFolderRecord)
    fun onUnmuteAll(chatFolder: ChatFolderRecord)
    fun onReadAll(chatFolder: ChatFolderRecord)
    fun onFolderSettings()
    fun isScrolled(): Boolean
  }
}
