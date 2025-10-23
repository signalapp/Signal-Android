package org.thoughtcrime.securesms.contacts.paged

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.signal.paging.LivePagedData
import org.signal.paging.PagedData
import org.signal.paging.PagingConfig
import org.signal.paging.PagingController
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationFilterRequest
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.search.SearchRepository
import org.thoughtcrime.securesms.util.livedata.Store
import org.whispersystems.signalservice.api.util.Preconditions

/**
 * Simple, reusable view model that manages a ContactSearchPagedDataSource as well as filter and expansion state.
 */
class ContactSearchViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val selectionLimits: SelectionLimits,
  private val contactSearchRepository: ContactSearchRepository,
  private val performSafetyNumberChecks: Boolean,
  private val arbitraryRepository: ArbitraryRepository?,
  private val searchRepository: SearchRepository,
  private val contactSearchPagedDataSourceRepository: ContactSearchPagedDataSourceRepository
) : ViewModel() {

  companion object {
    private const val QUERY = "query"
  }

  private val safetyNumberRepository: SafetyNumberRepository by lazy { SafetyNumberRepository() }

  private val disposables = CompositeDisposable()

  private val pagingConfig = PagingConfig.Builder()
    .setBufferPages(1)
    .setPageSize(20)
    .setStartIndex(0)
    .build()

  private val pagedData = MutableLiveData<LivePagedData<ContactSearchKey, ContactSearchData>>()
  private val configurationStore = Store(ContactSearchState(query = savedStateHandle[QUERY]))
  private val internalSelectedContacts = MutableStateFlow<Set<ContactSearchKey>>(emptySet())
  private val errorEvents = PublishSubject.create<ContactSearchError>()

  val controller: LiveData<PagingController<ContactSearchKey>> = pagedData.map { it.controller }
  val data: LiveData<List<ContactSearchData>> = pagedData.switchMap { it.data }
  val configurationState: LiveData<ContactSearchState> = configurationStore.stateLiveData
  private val selectedContacts: StateFlow<Set<ContactSearchKey>> = internalSelectedContacts
  val selectionState: LiveData<Set<ContactSearchKey>> = selectedContacts.asLiveData()

  val errorEventsStream: Observable<ContactSearchError> = errorEvents

  override fun onCleared() {
    disposables.clear()
  }

  fun setConfiguration(contactSearchConfiguration: ContactSearchConfiguration) {
    val pagedDataSource = ContactSearchPagedDataSource(
      contactSearchConfiguration,
      arbitraryRepository = arbitraryRepository,
      searchRepository = searchRepository,
      contactSearchPagedDataSourceRepository = contactSearchPagedDataSourceRepository
    )
    pagedData.value = PagedData.createForLiveData(pagedDataSource, pagingConfig)
  }

  fun getQuery(): String? = savedStateHandle[QUERY]

  fun setQuery(query: String?) {
    savedStateHandle[QUERY] = query
    configurationStore.update { it.copy(query = query) }
  }

  fun setConversationFilterRequest(conversationFilterRequest: ConversationFilterRequest) {
    configurationStore.update { it.copy(conversationFilterRequest = conversationFilterRequest) }
  }

  fun expandSection(sectionKey: ContactSearchConfiguration.SectionKey) {
    configurationStore.update { it.copy(expandedSections = it.expandedSections + sectionKey) }
  }

  fun setKeysSelected(contactSearchKeys: Set<ContactSearchKey>) {
    disposables += contactSearchRepository.filterOutUnselectableContactSearchKeys(contactSearchKeys).subscribe { results ->
      if (results.any { !it.isSelectable }) {
        errorEvents.onNext(ContactSearchError.CONTACT_NOT_SELECTABLE)
        return@subscribe
      }

      val newSelectionEntries = results.filter { it.isSelectable }.map { it.key } - getSelectedContacts()
      val newSelectionSize = newSelectionEntries.size + getSelectedContacts().size
      if (selectionLimits.hasRecommendedLimit() && getSelectedContacts().size < selectionLimits.recommendedLimit && newSelectionSize >= selectionLimits.recommendedLimit) {
        errorEvents.onNext(ContactSearchError.RECOMMENDED_LIMIT_REACHED)
      } else if (selectionLimits.hasHardLimit() && newSelectionSize > selectionLimits.hardLimit) {
        errorEvents.onNext(ContactSearchError.HARD_LIMIT_REACHED)
        return@subscribe
      }

      if (performSafetyNumberChecks) {
        safetyNumberRepository.batchSafetyNumberCheck(newSelectionEntries)
      }

      internalSelectedContacts.update { it + newSelectionEntries }
    }
  }

  fun setKeysNotSelected(contactSearchKeys: Set<ContactSearchKey>) {
    internalSelectedContacts.update { it - contactSearchKeys }
  }

  fun getSelectedContacts(): Set<ContactSearchKey> {
    return selectedContacts.value
  }

  fun clearSelection() {
    internalSelectedContacts.update { emptySet() }
  }

  fun addToVisibleGroupStories(groupStories: Set<ContactSearchKey.RecipientSearchKey>) {
    disposables += contactSearchRepository.markDisplayAsStory(groupStories.map { it.recipientId }).subscribe {
      configurationStore.update { state ->
        state.copy(
          groupStories = state.groupStories + groupStories.map {
            val recipient = Recipient.resolved(it.recipientId)
            ContactSearchData.Story(recipient, recipient.participantIds.size, DistributionListPrivacyMode.ALL)
          }
        )
      }
    }
  }

  fun removeGroupStory(story: ContactSearchData.Story) {
    Preconditions.checkArgument(story.recipient.isGroup)
    setKeysNotSelected(setOf(story.contactSearchKey))
    disposables += contactSearchRepository.unmarkDisplayAsStory(story.recipient.requireGroupId()).subscribe {
      configurationStore.update { state ->
        state.copy(
          groupStories = state.groupStories.filter { it.recipient.id == story.recipient.id }.toSet()
        )
      }
      refresh()
    }
  }

  fun deletePrivateStory(story: ContactSearchData.Story) {
    Preconditions.checkArgument(story.recipient.isDistributionList && !story.recipient.isMyStory)
    setKeysNotSelected(setOf(story.contactSearchKey))
    disposables += contactSearchRepository.deletePrivateStory(story.recipient.requireDistributionListId()).subscribe {
      refresh()
    }
  }

  fun refresh() {
    controller.value?.onDataInvalidated()
  }

  class Factory(
    private val selectionLimits: SelectionLimits,
    private val repository: ContactSearchRepository,
    private val performSafetyNumberChecks: Boolean,
    private val arbitraryRepository: ArbitraryRepository?,
    private val searchRepository: SearchRepository,
    private val contactSearchPagedDataSourceRepository: ContactSearchPagedDataSourceRepository
  ) : AbstractSavedStateViewModelFactory() {
    override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
      return modelClass.cast(
        ContactSearchViewModel(
          savedStateHandle = handle,
          selectionLimits = selectionLimits,
          contactSearchRepository = repository,
          performSafetyNumberChecks = performSafetyNumberChecks,
          arbitraryRepository = arbitraryRepository,
          searchRepository = searchRepository,
          contactSearchPagedDataSourceRepository = contactSearchPagedDataSourceRepository
        )
      ) as T
    }
  }
}
