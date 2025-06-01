package org.thoughtcrime.securesms.conversationlist

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.combineLatest
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlowable
import kotlinx.parcelize.Parcelize
import org.signal.paging.PagedData
import org.signal.paging.PagingConfig
import org.signal.paging.ProxyPagingController
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFoldersRepository
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationFilterRequest
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationFilterSource
import org.thoughtcrime.securesms.conversationlist.model.Conversation
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.conversationlist.model.ConversationSet
import org.thoughtcrime.securesms.database.RxDatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.MarkReadReceiver
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.rx.RxStore
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import java.util.concurrent.TimeUnit

sealed class ConversationListViewModel(
  private val isArchived: Boolean,
  private val savedStateHandle: SavedStateHandle
) : ViewModel() {

  companion object {
    private const val STATE = "state"

    private var coldStart = true
  }

  private val disposables: CompositeDisposable = CompositeDisposable()

  private var saveableState: SaveableState
    get() = savedStateHandle[STATE] ?: SaveableState()
    set(value) {
      savedStateHandle[STATE] = value
    }

  private val store = RxStore(ConversationListState()).addTo(disposables)
  private val conversationListDataSource: Flowable<ConversationListDataSource>
  private val pagingConfig = PagingConfig.Builder()
    .setPageSize(15)
    .setBufferPages(2)
    .build()

  val conversationsState: Flowable<List<Conversation>> = store.mapDistinctForUi { it.conversations }
  val selectedState: Flowable<ConversationSet> = store.mapDistinctForUi { it.selectedConversations }
  val filterRequestState: Flowable<ConversationFilterRequest> = savedStateHandle.getStateFlow(STATE, SaveableState()).map { it.filterRequest }.asFlowable().observeOn(AndroidSchedulers.mainThread())
  val chatFolderState: Flowable<List<ChatFolderMappingModel>> = savedStateHandle.getStateFlow(STATE, SaveableState()).map { it.chatFolders }.asFlowable().observeOn(AndroidSchedulers.mainThread())
  val hasNoConversations: Flowable<Boolean>

  val controller = ProxyPagingController<Long>()

  val folders: List<ChatFolderMappingModel>
    get() = saveableState.chatFolders
  val currentFolder: ChatFolderRecord
    get() = saveableState.currentFolder
  val conversationFilterRequest: ConversationFilterRequest
    get() = saveableState.filterRequest
  val pinnedCount: Int
    get() = store.state.pinnedCount
  val webSocketState: Observable<WebSocketConnectionState>
    get() = AppDependencies.webSocketObserver.observeOn(AndroidSchedulers.mainThread())

  @get:JvmName("currentSelectedConversations")
  val currentSelectedConversations: Set<Conversation>
    get() = store.state.internalSelection

  init {
    val saveableStateFlowable = savedStateHandle.getStateFlow(STATE, SaveableState()).asFlowable()

    conversationListDataSource = saveableStateFlowable
      .subscribeOn(Schedulers.io())
      .filter { it.currentFolder.id != -1L }
      .map { it.filterRequest to it.currentFolder }
      .distinctUntilChanged()
      .map { (filterRequest, folder) ->
        ConversationListDataSource.create(
          folder,
          filterRequest.filter,
          isArchived,
          SignalStore.uiHints.canDisplayPullToFilterTip() && filterRequest.source === ConversationFilterSource.OVERFLOW
        )
      }
      .replay(1)
      .refCount()

    val pagedData = conversationListDataSource
      .map { PagedData.createForObservable(it, pagingConfig) }
      .doOnNext { controller.set(it.controller) }
      .switchMap { it.data.toFlowable(BackpressureStrategy.LATEST) }

    store.update(pagedData) { conversations, state -> state.copy(conversations = conversations) }
      .addTo(disposables)

    RxDatabaseObserver
      .conversationList
      .throttleLatest(500, TimeUnit.MILLISECONDS)
      .subscribe { controller.onDataInvalidated() }
      .addTo(disposables)

    Flowable.merge(
      RxDatabaseObserver
        .conversationList
        .debounce(250, TimeUnit.MILLISECONDS),
      RxDatabaseObserver
        .chatFolders
        .throttleLatest(500, TimeUnit.MILLISECONDS)
    )
      .subscribe { loadCurrentFolders() }
      .addTo(disposables)

    val pinnedCount = RxDatabaseObserver
      .conversationList
      .map { SignalDatabase.threads.getPinnedConversationListCount(ConversationFilter.OFF) }
      .distinctUntilChanged()

    store.update(pinnedCount) { pinned, state -> state.copy(pinnedCount = pinned) }
      .addTo(disposables)

    hasNoConversations = store
      .stateFlowable
      .subscribeOn(Schedulers.io())
      .combineLatest(saveableStateFlowable.map { it.filterRequest })
      .map { (state, filterRequest) -> filterRequest to state.conversations }
      .distinctUntilChanged()
      .map { (filterRequest, conversations) ->
        if (conversations.isNotEmpty()) {
          false
        } else {
          val archivedCount = SignalDatabase.threads.getArchivedConversationListCount(filterRequest.filter)
          val unarchivedCount = SignalDatabase.threads.getUnarchivedConversationListCount(filterRequest.filter)
          (archivedCount + unarchivedCount) == 0
        }
      }
      .observeOn(AndroidSchedulers.mainThread())
  }

  override fun onCleared() {
    disposables.dispose()
    super.onCleared()
  }

  fun onVisible() {
    if (!coldStart) {
      AppDependencies.databaseObserver.notifyConversationListListeners()
    }
    coldStart = false
  }

  fun startSelection(conversation: Conversation) {
    setSelection(setOf(conversation))
  }

  fun endSelection() {
    setSelection(emptySet())
  }

  fun onSelectAllClick() {
    conversationListDataSource
      .subscribeOn(Schedulers.io())
      .firstOrError()
      .map { dataSource ->
        val totalSize = dataSource.size()
        dataSource.load(0, totalSize, totalSize) { disposables.isDisposed }
      }
      .subscribe { newSelection -> setSelection(newSelection) }
      .addTo(disposables)
  }

  fun toggleConversationSelected(conversation: Conversation) {
    val newSelection: MutableSet<Conversation> = store.state.internalSelection.toMutableSet()

    if (newSelection.contains(conversation)) {
      newSelection.remove(conversation)
    } else {
      newSelection.add(conversation)
    }

    setSelection(newSelection)
  }

  fun setFiltered(isFiltered: Boolean, conversationFilterSource: ConversationFilterSource) {
    saveableState = saveableState.copy(
      filterRequest = ConversationFilterRequest(
        filter = if (isFiltered) ConversationFilter.UNREAD else ConversationFilter.OFF,
        source = conversationFilterSource
      )
    )
  }

  private fun loadCurrentFolders() {
    viewModelScope.launch(Dispatchers.IO) {
      val folders = ChatFoldersRepository.getCurrentFolders()
      val unreadCountAndMutedStatus = ChatFoldersRepository.getUnreadCountAndMutedStatusForFolders(folders)

      val selectedFolderId = if (currentFolder.id == -1L) {
        folders.firstOrNull()?.id
      } else {
        currentFolder.id
      }
      val chatFolders = folders.map { folder ->
        ChatFolderMappingModel(
          chatFolder = folder,
          unreadCount = unreadCountAndMutedStatus[folder.id]?.first ?: 0,
          isMuted = unreadCountAndMutedStatus[folder.id]?.second ?: false,
          isSelected = selectedFolderId == folder.id
        )
      }

      saveableState = saveableState.copy(
        currentFolder = folders.find { folder -> folder.id == selectedFolderId } ?: ChatFolderRecord(),
        chatFolders = chatFolders
      )
    }
  }

  private fun setSelection(newSelection: Collection<Conversation>) {
    store.update {
      val selection = newSelection.toSet()
      it.copy(internalSelection = selection, selectedConversations = ConversationSet(selection))
    }
  }

  fun select(chatFolder: ChatFolderRecord) {
    saveableState = saveableState.copy(
      currentFolder = chatFolder,
      chatFolders = folders.map { model ->
        model.copy(isSelected = chatFolder.id == model.chatFolder.id)
      }
    )
  }

  fun onUpdateMute(chatFolder: ChatFolderRecord, until: Long) {
    viewModelScope.launch(Dispatchers.IO) {
      val ids = SignalDatabase.threads.getRecipientIdsByChatFolder(chatFolder)
      val recipientIds: List<RecipientId> = ids.filter { id ->
        Recipient.resolved(id).muteUntil != until
      }
      if (recipientIds.isNotEmpty()) {
        SignalDatabase.recipients.setMuted(recipientIds, until)
      }
    }
  }

  fun markChatFolderRead(chatFolder: ChatFolderRecord) {
    viewModelScope.launch(Dispatchers.IO) {
      val ids = SignalDatabase.threads.getThreadIdsByChatFolder(chatFolder)
      val messageIds = SignalDatabase.threads.setRead(ids)
      AppDependencies.messageNotifier.updateNotification(AppDependencies.application)
      MarkReadReceiver.process(messageIds)
    }
  }

  fun removeChatFromFolder(threadId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
      SignalDatabase.chatFolders.removeFromFolder(currentFolder.id, threadId)
      scheduleChatFolderSync(currentFolder.id)
    }
  }

  fun addToFolder(folderId: Long, threadIds: List<Long>) {
    viewModelScope.launch(Dispatchers.IO) {
      val includedChats = folders.find { it.chatFolder.id == folderId }?.chatFolder?.includedChats
      val threadIdsNotIncluded = threadIds.filterNot { threadId ->
        includedChats?.contains(threadId) ?: false
      }
      SignalDatabase.chatFolders.addToFolder(folderId, threadIdsNotIncluded)
      scheduleChatFolderSync(folderId)
    }
  }

  private fun scheduleChatFolderSync(id: Long) {
    SignalDatabase.chatFolders.markNeedsSync(id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  /**
   * Easily persistable state to ensure proper restoration upon VM recreation.
   */
  @Parcelize
  private data class SaveableState(
    val chatFolders: List<ChatFolderMappingModel> = emptyList(),
    val currentFolder: ChatFolderRecord = ChatFolderRecord(),
    val filterRequest: ConversationFilterRequest = ConversationFilterRequest(ConversationFilter.OFF, ConversationFilterSource.DRAG)
  ) : Parcelable

  private data class ConversationListState(
    val conversations: List<Conversation> = emptyList(),
    val selectedConversations: ConversationSet = ConversationSet(),
    val internalSelection: Set<Conversation> = emptySet(),
    val pinnedCount: Int = 0
  )

  class UnarchivedConversationListViewModel(savedStateHandle: SavedStateHandle) : ConversationListViewModel(isArchived = false, savedStateHandle = savedStateHandle)
  class ArchivedConversationListViewModel(savedStateHandle: SavedStateHandle) : ConversationListViewModel(isArchived = true, savedStateHandle = savedStateHandle)

  class Factory(
    private val isArchived: Boolean
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
      val savedStateHandle = extras.createSavedStateHandle()

      return if (isArchived) {
        ArchivedConversationListViewModel(savedStateHandle) as T
      } else {
        UnarchivedConversationListViewModel(savedStateHandle) as T
      }
    }
  }
}
