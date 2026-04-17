package org.thoughtcrime.securesms.contacts.paged

import androidx.compose.runtime.Stable
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.paging.PagedData
import org.signal.paging.PagingConfig
import org.signal.paging.PagingController
import org.signal.paging.StateFlowPagedData
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationFilterRequest
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.search.SearchFilter
import org.thoughtcrime.securesms.search.SearchRepository
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList
import org.thoughtcrime.securesms.util.adapter.mapping.PagingMappingAdapter
import org.whispersystems.signalservice.api.util.Preconditions

/**
 * Manages paged contact search data, query/filter state, and contact selection. Drives
 * [ContactSearch] / [ContactSearchView] and can also be used standalone via
 * [bindAdapterToLifecycle] when only the data pipeline is needed (no Compose surface).
 *
 * Create via [Factory] and scope to the host Fragment or Activity. All state is exposed as
 * [kotlinx.coroutines.flow.StateFlow] so it can be collected from Compose or coroutine scopes.
 *
 * @param fixedContacts Pre-selected contacts that cannot be deselected (e.g. existing group
 *                      members). Owned here rather than by the UI layer.
 */
@Stable
class ContactSearchViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val selectionLimits: SelectionLimits,
  private val isMultiSelect: Boolean,
  private val contactSearchRepository: ContactSearchRepository,
  private val performSafetyNumberChecks: Boolean,
  val arbitraryRepository: ArbitraryRepository?,
  private val searchRepository: SearchRepository,
  private val contactSearchPagedDataSourceRepository: ContactSearchPagedDataSourceRepository,
  val fixedContacts: Set<ContactSearchKey> = emptySet()
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

  private val pagedData = MutableStateFlow<StateFlowPagedData<ContactSearchKey, ContactSearchData>?>(null)
  private val internalConfigurationState = MutableStateFlow(ContactSearchState(query = savedStateHandle[QUERY]))
  private val internalSelectedContacts = MutableStateFlow<Set<ContactSearchKey>>(emptySet())
  private val errorEvents = PublishSubject.create<ContactSearchError>()
  private val rawQuery = MutableStateFlow<String?>(savedStateHandle[QUERY])

  init {
    viewModelScope.launch {
      rawQuery.drop(1).debounce(300).collect { query ->
        savedStateHandle[QUERY] = query
        internalConfigurationState.update { it.copy(query = query) }
      }
    }
  }

  /** The paging controller for the current data source. Null until [setConfiguration] is called. */
  val controller: StateFlow<PagingController<ContactSearchKey>?> = pagedData
    .map { it?.controller }
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  /** Raw paged contact data. Prefer [mappingModels] for binding to an adapter. */
  val data: StateFlow<List<ContactSearchData>> = pagedData
    .flatMapLatest { it?.data ?: flowOf(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  /** The current query/filter/expansion state. Changes here trigger a new [setConfiguration] call via the Compose layer or [bindAdapterToLifecycle]. */
  val configurationState: StateFlow<ContactSearchState> = internalConfigurationState

  /** Currently selected contact keys, excluding [fixedContacts]. */
  val selectionState: StateFlow<Set<ContactSearchKey>> = internalSelectedContacts

  /** Adapter-ready models combining [data] with [selectionState]. Suitable for direct submission to a [ContactSearchAdapter]. */
  val mappingModels: StateFlow<MappingModelList> = combine(data, selectionState) { contactData, selection ->
    ContactSearchAdapter.toMappingModelList(contactData, selection, arbitraryRepository)
  }.stateIn(viewModelScope, SharingStarted.Eagerly, MappingModelList())

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
    pagedData.value = PagedData.createForStateFlow(pagedDataSource, pagingConfig)
  }

  fun getQuery(): String? = rawQuery.value

  fun setQuery(query: String?) {
    rawQuery.value = query
  }

  fun setConversationFilterRequest(conversationFilterRequest: ConversationFilterRequest) {
    internalConfigurationState.update { it.copy(conversationFilterRequest = conversationFilterRequest) }
  }

  fun setSearchFilter(searchFilter: SearchFilter) {
    internalConfigurationState.update { it.copy(searchFilter = searchFilter) }
  }

  fun expandSection(sectionKey: ContactSearchConfiguration.SectionKey) {
    internalConfigurationState.update { it.copy(expandedSections = it.expandedSections + sectionKey) }
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

      if (!isMultiSelect && newSelectionEntries.isNotEmpty()) {
        internalSelectedContacts.update { newSelectionEntries.toSet() }
      } else {
        internalSelectedContacts.update { it + newSelectionEntries }
      }
    }
  }

  fun setKeysNotSelected(contactSearchKeys: Set<ContactSearchKey>) {
    internalSelectedContacts.update { it - contactSearchKeys }
  }

  fun getSelectedContacts(): Set<ContactSearchKey> {
    return internalSelectedContacts.value
  }

  fun clearSelection() {
    internalSelectedContacts.update { emptySet() }
  }

  fun addToVisibleGroupStories(groupStories: Set<ContactSearchKey.RecipientSearchKey>) {
    disposables += contactSearchRepository.markDisplayAsStory(groupStories.map { it.recipientId }).subscribe {
      internalConfigurationState.update { state ->
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
      internalConfigurationState.update { state ->
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

  fun getFixedContactsSize(): Int = fixedContacts.size

  fun refresh() {
    controller.value?.onDataInvalidated()
  }

  class Factory(
    private val selectionLimits: SelectionLimits,
    private val isMultiSelect: Boolean = true,
    private val repository: ContactSearchRepository,
    private val performSafetyNumberChecks: Boolean,
    private val arbitraryRepository: ArbitraryRepository?,
    private val searchRepository: SearchRepository,
    private val contactSearchPagedDataSourceRepository: ContactSearchPagedDataSourceRepository,
    private val fixedContacts: Set<ContactSearchKey> = emptySet()
  ) : AbstractSavedStateViewModelFactory() {
    override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
      return modelClass.cast(
        ContactSearchViewModel(
          savedStateHandle = handle,
          selectionLimits = selectionLimits,
          isMultiSelect = isMultiSelect,
          contactSearchRepository = repository,
          performSafetyNumberChecks = performSafetyNumberChecks,
          arbitraryRepository = arbitraryRepository,
          searchRepository = searchRepository,
          contactSearchPagedDataSourceRepository = contactSearchPagedDataSourceRepository,
          fixedContacts = fixedContacts
        )
      ) as T
    }
  }
}

/**
 * Wires the three core flows of [ContactSearchViewModel] to a [PagingMappingAdapter], scoped to
 * the given [LifecycleOwner]. Designed for Java callers that create the adapter directly (without
 * [ContactSearchView]) and only need the data pipeline, not a full Compose surface.
 *
 * Call once from `onViewCreated` after constructing the ViewModel and adapter.
 */
fun ContactSearchViewModel.bindAdapterToLifecycle(
  lifecycleOwner: LifecycleOwner,
  adapter: PagingMappingAdapter<ContactSearchKey>,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration
) {
  lifecycleOwner.lifecycleScope.launch {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      launch { mappingModels.collect { adapter.submitList(it) } }
      launch { controller.collect { it?.let { c -> adapter.setPagingController(c) } } }
      launch { configurationState.collect { setConfiguration(mapStateToConfiguration(it)) } }
    }
  }
}
