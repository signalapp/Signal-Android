package org.thoughtcrime.securesms.components.settings.app.chats.folders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ChatType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Maintains the state of the [ChatFoldersFragment] and [CreateFoldersFragment]
 */
class ChatFoldersViewModel : ViewModel() {

  private val internalState = MutableStateFlow(ChatFoldersSettingsState())
  val state = internalState.asStateFlow()

  fun loadCurrentFolders(context: Context) {
    viewModelScope.launch(Dispatchers.IO) {
      val folders = ChatFoldersRepository.getCurrentFolders(includeUnreadAndMutedCounts = false)
      val suggestedFolders = getSuggestedFolders(context, folders)

      internalState.update {
        it.copy(
          folders = folders,
          suggestedFolders = suggestedFolders,
          currentFolder = ChatFolderRecord(),
          originalFolder = ChatFolderRecord()
        )
      }
    }
  }

  private fun getSuggestedFolders(context: Context, currentFolders: List<ChatFolderRecord>): List<ChatFolderRecord> {
    var showIndividualSuggestion = true
    var showGroupSuggestion = true
    var showUnreadSuggestion = true

    currentFolders
      .filter { folder -> folder.includedChats.isEmpty() && folder.excludedChats.isEmpty() }
      .forEach { folder ->
        if (folder.showIndividualChats && !folder.showGroupChats) {
          showIndividualSuggestion = false
        } else if (folder.showGroupChats && !folder.showIndividualChats) {
          showGroupSuggestion = false
        } else if (folder.showUnread && folder.showIndividualChats && folder.showGroupChats) {
          showUnreadSuggestion = false
        }
      }

    val suggestions: MutableList<ChatFolderRecord> = mutableListOf()
    if (showIndividualSuggestion) {
      suggestions.add(
        ChatFolderRecord(
          name = context.getString(R.string.ChatFoldersFragment__one_on_one_chats),
          showIndividualChats = true,
          folderType = ChatFolderRecord.FolderType.INDIVIDUAL,
          showMutedChats = true
        )
      )
    }
    if (showGroupSuggestion) {
      suggestions.add(
        ChatFolderRecord(
          name = context.getString(R.string.ChatFoldersFragment__groups),
          showGroupChats = true,
          folderType = ChatFolderRecord.FolderType.GROUP,
          showMutedChats = true
        )
      )
    }
    if (showUnreadSuggestion) {
      suggestions.add(
        ChatFolderRecord(
          name = context.getString(R.string.ChatFoldersFragment__unread),
          showUnread = true,
          showIndividualChats = true,
          showGroupChats = true,
          showMutedChats = true,
          folderType = ChatFolderRecord.FolderType.UNREAD
        )
      )
    }
    return suggestions
  }

  fun setCurrentFolder(folder: ChatFolderRecord) {
    viewModelScope.launch(Dispatchers.IO) {
      val includedRecipients = folder.includedChats.mapNotNull { threadId ->
        SignalDatabase.threads.getRecipientForThreadId(threadId)
      }
      val excludedRecipients = folder.excludedChats.mapNotNull { threadId ->
        SignalDatabase.threads.getRecipientForThreadId(threadId)
      }

      val updatedFolder = folder.copy(
        includedRecipients = includedRecipients.toSet(),
        excludedRecipients = excludedRecipients.toSet()
      )

      internalState.update {
        it.copy(originalFolder = updatedFolder, currentFolder = updatedFolder)
      }
    }
  }

  fun updateName(name: String) {
    val updatedFolder = internalState.value.currentFolder.copy(
      name = name.substring(0, minOf(name.length, 32))
    )

    internalState.update {
      it.copy(currentFolder = updatedFolder)
    }
  }

  fun toggleShowUnread(showUnread: Boolean) {
    val updatedFolder = internalState.value.currentFolder.copy(
      showUnread = showUnread
    )

    internalState.update {
      it.copy(currentFolder = updatedFolder)
    }
  }

  fun toggleShowMutedChats(showMuted: Boolean) {
    val updatedFolder = internalState.value.currentFolder.copy(
      showMutedChats = showMuted
    )

    internalState.update {
      it.copy(currentFolder = updatedFolder)
    }
  }

  fun showDeleteDialog(show: Boolean) {
    internalState.update {
      it.copy(showDeleteDialog = show)
    }
  }

  fun deleteFolder(context: Context) {
    viewModelScope.launch(Dispatchers.IO) {
      ChatFoldersRepository.deleteFolder(internalState.value.originalFolder)

      loadCurrentFolders(context)
      internalState.update {
        it.copy(showDeleteDialog = false)
      }
    }
  }

  fun showConfirmationDialog(show: Boolean) {
    internalState.update {
      it.copy(showConfirmationDialog = show)
    }
  }

  fun createFolder(context: Context, folder: ChatFolderRecord? = null) {
    viewModelScope.launch(Dispatchers.IO) {
      val currentFolder = folder ?: internalState.value.currentFolder
      ChatFoldersRepository.createFolder(currentFolder)
      loadCurrentFolders(context)

      internalState.update {
        it.copy(showConfirmationDialog = false)
      }
    }
  }

  fun updatePosition(fromIndex: Int, toIndex: Int) {
    viewModelScope.launch(Dispatchers.IO) {
      val folders = state.value.folders.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
      val updatedFolders = folders.mapIndexed { index, chatFolderRecord ->
        chatFolderRecord.copy(position = index)
      }
      ChatFoldersRepository.updatePositions(updatedFolders)

      internalState.update {
        it.copy(folders = updatedFolders)
      }
    }
  }

  fun updateFolder(context: Context) {
    viewModelScope.launch(Dispatchers.IO) {
      ChatFoldersRepository.updateFolder(internalState.value.currentFolder)
      loadCurrentFolders(context)

      internalState.update {
        it.copy(showConfirmationDialog = false)
      }
    }
  }

  fun setPendingChats() {
    viewModelScope.launch(Dispatchers.IO) {
      val currentFolder = internalState.value.currentFolder
      val includedChats = currentFolder.includedRecipients.map { recipient -> recipient.id }.toMutableSet()
      val excludedChats = currentFolder.excludedRecipients.map { recipient -> recipient.id }.toMutableSet()

      val chatTypes: MutableSet<ChatType> = mutableSetOf()
      if (currentFolder.showIndividualChats) {
        chatTypes.add(ChatType.INDIVIDUAL)
      }
      if (currentFolder.showGroupChats) {
        chatTypes.add(ChatType.GROUPS)
      }

      internalState.update {
        it.copy(
          pendingIncludedRecipients = includedChats,
          pendingExcludedRecipients = excludedChats,
          pendingChatTypes = chatTypes
        )
      }
    }
  }

  fun addThreadToIncludedChat(threadId: Long?) {
    if (threadId == null || threadId == -1L) {
      return
    }
    viewModelScope.launch {
      val updatedFolder = internalState.value.currentFolder
      val recipient = SignalDatabase.threads.getRecipientForThreadId(threadId)
      if (recipient != null) {
        internalState.update {
          it.copy(
            currentFolder = updatedFolder.copy(
              includedRecipients = setOf(recipient)
            )
          )
        }
      }
    }
  }

  fun addIncludedChat(recipientId: RecipientId) {
    val includedChats = internalState.value.pendingIncludedRecipients.plus(recipientId)
    internalState.update {
      it.copy(pendingIncludedRecipients = includedChats)
    }
  }

  fun addExcludedChat(recipientId: RecipientId) {
    val excludedChats = internalState.value.pendingExcludedRecipients.plus(recipientId)
    internalState.update {
      it.copy(pendingExcludedRecipients = excludedChats)
    }
  }

  fun removeIncludedChat(recipientId: RecipientId) {
    val includedChats = internalState.value.pendingIncludedRecipients.minus(recipientId)
    internalState.update {
      it.copy(pendingIncludedRecipients = includedChats)
    }
  }

  fun removeExcludedChat(recipientId: RecipientId) {
    val excludedChats = internalState.value.pendingExcludedRecipients.minus(recipientId)
    internalState.update {
      it.copy(pendingExcludedRecipients = excludedChats)
    }
  }

  fun addChatType(chatType: ChatType) {
    val updatedChatTypes = internalState.value.pendingChatTypes.plus(chatType)
    internalState.update {
      it.copy(
        pendingChatTypes = updatedChatTypes
      )
    }
  }

  fun removeChatType(chatType: ChatType) {
    val updatedChatTypes = internalState.value.pendingChatTypes.minus(chatType)
    internalState.update {
      it.copy(
        pendingChatTypes = updatedChatTypes
      )
    }
  }

  fun savePendingChats() {
    viewModelScope.launch(Dispatchers.IO) {
      val updatedFolder = internalState.value.currentFolder
      val includedChatIds = internalState.value.pendingIncludedRecipients
      val excludedChatIds = internalState.value.pendingExcludedRecipients
      val showIndividualChats = internalState.value.pendingChatTypes.contains(ChatType.INDIVIDUAL)
      val showGroupChats = internalState.value.pendingChatTypes.contains(ChatType.GROUPS)

      val includedRecipients = includedChatIds.map(Recipient::resolved).toSet()
      val excludedRecipients = excludedChatIds.map(Recipient::resolved).toSet()

      internalState.update {
        it.copy(
          currentFolder = updatedFolder.copy(
            includedRecipients = includedRecipients,
            excludedRecipients = excludedRecipients,
            showIndividualChats = showIndividualChats,
            showGroupChats = showGroupChats
          ),
          pendingIncludedRecipients = emptySet(),
          pendingExcludedRecipients = emptySet()
        )
      }
    }
  }

  fun enableButton(): Boolean {
    return internalState.value.pendingIncludedRecipients.isNotEmpty() ||
      internalState.value.pendingChatTypes.isNotEmpty() ||
      internalState.value.pendingExcludedRecipients.isNotEmpty()
  }

  fun hasChanges(): Boolean {
    val currentFolder = state.value.currentFolder
    val originalFolder = state.value.originalFolder

    return if (currentFolder.id == -1L) {
      currentFolder.name.isNotEmpty() &&
        (currentFolder.includedRecipients.isNotEmpty() || currentFolder.showIndividualChats || currentFolder.showGroupChats)
    } else {
      originalFolder != currentFolder ||
        originalFolder.includedRecipients != currentFolder.includedRecipients ||
        originalFolder.excludedRecipients != currentFolder.excludedRecipients
    }
  }

  fun setCurrentFolderId(folderId: Long) {
    if (folderId != -1L) {
      viewModelScope.launch(Dispatchers.IO) {
        val folder = ChatFoldersRepository.getFolder(folderId)
        setCurrentFolder(folder)
      }
    }
  }

  fun hasEmptyName(): Boolean {
    return state.value.currentFolder.name.isEmpty()
  }
}
