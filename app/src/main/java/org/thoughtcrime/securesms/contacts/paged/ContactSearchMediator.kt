package org.thoughtcrime.securesms.contacts.paged

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationFilterRequest
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.search.SearchRepository
import org.thoughtcrime.securesms.stories.settings.custom.PrivateStorySettingsFragment
import org.thoughtcrime.securesms.stories.settings.my.MyStorySettingsFragment
import org.thoughtcrime.securesms.stories.settings.privacy.ChooseInitialMyStoryMembershipBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.Debouncer
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.adapter.mapping.PagingMappingAdapter
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil
import java.util.concurrent.TimeUnit

/**
 * This mediator serves as the delegate for interacting with the ContactSearch* framework.
 *
 * @param fragment The fragment displaying the content search results.
 * @param fixedContacts Contacts which are "pre-selected" (for example, already a member of a group we're adding to)
 * @param selectionLimits [SelectionLimits] describing how large the result set can be.
 * @param displayCheckBox Whether or not to display checkboxes on items.
 * @param displaySmsTag   Whether or not to display the SMS tag on items.
 * @param displaySecondaryInformation Whether or not to display phone numbers on known contacts.
 * @param mapStateToConfiguration Maps a [ContactSearchState] to a [ContactSearchConfiguration]
 * @param callbacks Hooks to help process, filter, and react to selection
 * @param performSafetyNumberChecks Whether to perform safety number checks for selected users
 * @param adapterFactory A factory for creating an instance of [PagingMappingAdapter] to display items
 * @param arbitraryRepository A repository for managing [ContactSearchKey.Arbitrary] data
 */
class ContactSearchMediator(
  private val fragment: Fragment,
  private val fixedContacts: Set<ContactSearchKey> = setOf(),
  selectionLimits: SelectionLimits,
  displayOptions: ContactSearchAdapter.DisplayOptions,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
  private val callbacks: Callbacks = SimpleCallbacks(),
  performSafetyNumberChecks: Boolean = true,
  adapterFactory: AdapterFactory = DefaultAdapterFactory,
  arbitraryRepository: ArbitraryRepository? = null
) {

  private val queryDebouncer = Debouncer(300, TimeUnit.MILLISECONDS)

  private val viewModel: ContactSearchViewModel = ViewModelProvider(
    fragment,
    ContactSearchViewModel.Factory(
      selectionLimits = selectionLimits,
      repository = ContactSearchRepository(),
      performSafetyNumberChecks = performSafetyNumberChecks,
      arbitraryRepository = arbitraryRepository,
      searchRepository = SearchRepository(fragment.requireContext().getString(R.string.note_to_self)),
      contactSearchPagedDataSourceRepository = ContactSearchPagedDataSourceRepository(fragment.requireContext())
    )
  )[ContactSearchViewModel::class.java]

  val adapter = adapterFactory.create(
    context = fragment.requireContext(),
    fixedContacts = fixedContacts,
    displayOptions = displayOptions,
    callbacks = object : ContactSearchAdapter.ClickCallbacks {
      override fun onStoryClicked(view: View, story: ContactSearchData.Story, isSelected: Boolean) {
        toggleStorySelection(view, story, isSelected)
      }

      override fun onKnownRecipientClicked(view: View, knownRecipient: ContactSearchData.KnownRecipient, isSelected: Boolean) {
        toggleSelection(view, knownRecipient, isSelected)
      }

      override fun onExpandClicked(expand: ContactSearchData.Expand) {
        viewModel.expandSection(expand.sectionKey)
      }
    },
    longClickCallbacks = ContactSearchAdapter.LongClickCallbacksAdapter(),
    storyContextMenuCallbacks = StoryContextMenuCallbacks(),
    callButtonClickCallbacks = ContactSearchAdapter.EmptyCallButtonClickCallbacks
  )

  init {
    val dataAndSelection: LiveData<Pair<List<ContactSearchData>, Set<ContactSearchKey>>> = LiveDataUtil.combineLatest(
      viewModel.data,
      viewModel.selectionState,
      ::Pair
    )

    dataAndSelection.observe(fragment.viewLifecycleOwner) { (data, selection) ->
      adapter.submitList(ContactSearchAdapter.toMappingModelList(data, selection, arbitraryRepository), {
        callbacks.onAdapterListCommitted(data.size)
      })
    }

    viewModel.controller.observe(fragment.viewLifecycleOwner) { controller ->
      adapter.setPagingController(controller)
    }

    viewModel.configurationState.observe(fragment.viewLifecycleOwner) {
      viewModel.setConfiguration(mapStateToConfiguration(it))
    }
  }

  fun onFilterChanged(filter: String?) {
    queryDebouncer.publish {
      viewModel.setQuery(filter)
    }
  }

  fun onConversationFilterRequestChanged(conversationFilterRequest: ConversationFilterRequest) {
    viewModel.setConversationFilterRequest(conversationFilterRequest)
  }

  fun setKeysSelected(keys: Set<ContactSearchKey>) {
    viewModel.setKeysSelected(callbacks.onBeforeContactsSelected(null, keys))
  }

  fun setKeysNotSelected(keys: Set<ContactSearchKey>) {
    keys.forEach {
      callbacks.onContactDeselected(null, it)
    }
    viewModel.setKeysNotSelected(keys)
  }

  fun clearSelection() {
    viewModel.clearSelection()
  }

  fun getSelectedContacts(): Set<ContactSearchKey> {
    return viewModel.getSelectedContacts()
  }

  fun getFixedContactsSize(): Int {
    return fixedContacts.size
  }

  fun getSelectionState(): LiveData<Set<ContactSearchKey>> {
    return viewModel.selectionState
  }

  fun getErrorEvents(): Observable<ContactSearchError> {
    return viewModel.errorEventsStream.observeOn(AndroidSchedulers.mainThread())
  }

  fun addToVisibleGroupStories(groupStories: Set<ContactSearchKey.RecipientSearchKey>) {
    viewModel.addToVisibleGroupStories(groupStories)
  }

  fun refresh() {
    viewModel.refresh()
  }

  private fun toggleStorySelection(view: View, contactSearchData: ContactSearchData.Story, isSelected: Boolean) {
    if (contactSearchData.recipient.isMyStory && !SignalStore.storyValues().userHasBeenNotifiedAboutStories) {
      ChooseInitialMyStoryMembershipBottomSheetDialogFragment.show(fragment.childFragmentManager)
    } else {
      toggleSelection(view, contactSearchData, isSelected)
    }
  }

  private fun toggleSelection(view: View, contactSearchData: ContactSearchData, isSelected: Boolean) {
    return if (isSelected) {
      callbacks.onContactDeselected(view, contactSearchData.contactSearchKey)
      viewModel.setKeysNotSelected(setOf(contactSearchData.contactSearchKey))
    } else {
      viewModel.setKeysSelected(callbacks.onBeforeContactsSelected(view, setOf(contactSearchData.contactSearchKey)))
    }
  }

  private inner class StoryContextMenuCallbacks : ContactSearchAdapter.StoryContextMenuCallbacks {
    override fun onOpenStorySettings(story: ContactSearchData.Story) {
      if (story.recipient.isMyStory) {
        MyStorySettingsFragment.createAsDialog()
          .show(fragment.childFragmentManager, null)
      } else {
        PrivateStorySettingsFragment.createAsDialog(story.recipient.requireDistributionListId())
          .show(fragment.childFragmentManager, null)
      }
    }

    override fun onRemoveGroupStory(story: ContactSearchData.Story, isSelected: Boolean) {
      MaterialAlertDialogBuilder(fragment.requireContext())
        .setTitle(R.string.ContactSearchMediator__remove_group_story)
        .setMessage(R.string.ContactSearchMediator__this_will_remove)
        .setPositiveButton(R.string.ContactSearchMediator__remove) { _, _ -> viewModel.removeGroupStory(story) }
        .setNegativeButton(android.R.string.cancel) { _, _ -> }
        .show()
    }

    override fun onDeletePrivateStory(story: ContactSearchData.Story, isSelected: Boolean) {
      MaterialAlertDialogBuilder(fragment.requireContext())
        .setTitle(R.string.ContactSearchMediator__delete_story)
        .setMessage(fragment.getString(R.string.ContactSearchMediator__delete_the_custom, story.recipient.getDisplayName(fragment.requireContext())))
        .setPositiveButton(SpanUtil.color(ContextCompat.getColor(fragment.requireContext(), R.color.signal_colorError), fragment.getString(R.string.ContactSearchMediator__delete))) { _, _ -> viewModel.deletePrivateStory(story) }
        .setNegativeButton(android.R.string.cancel) { _, _ -> }
        .show()
    }
  }

  interface Callbacks {
    fun onBeforeContactsSelected(view: View?, contactSearchKeys: Set<ContactSearchKey>): Set<ContactSearchKey>
    fun onContactDeselected(view: View?, contactSearchKey: ContactSearchKey)
    fun onAdapterListCommitted(size: Int)
  }

  open class SimpleCallbacks : Callbacks {
    override fun onBeforeContactsSelected(view: View?, contactSearchKeys: Set<ContactSearchKey>): Set<ContactSearchKey> {
      return contactSearchKeys
    }

    override fun onContactDeselected(view: View?, contactSearchKey: ContactSearchKey) = Unit
    override fun onAdapterListCommitted(size: Int) = Unit
  }

  /**
   * Wraps the construction of a PagingMappingAdapter<ContactSearchKey> so that it can
   * be swapped for another implementation, allow listeners to be wrapped, etc.
   */
  fun interface AdapterFactory {
    fun create(
      context: Context,
      fixedContacts: Set<ContactSearchKey>,
      displayOptions: ContactSearchAdapter.DisplayOptions,
      callbacks: ContactSearchAdapter.ClickCallbacks,
      longClickCallbacks: ContactSearchAdapter.LongClickCallbacks,
      storyContextMenuCallbacks: ContactSearchAdapter.StoryContextMenuCallbacks,
      callButtonClickCallbacks: ContactSearchAdapter.CallButtonClickCallbacks
    ): PagingMappingAdapter<ContactSearchKey>
  }

  private object DefaultAdapterFactory : AdapterFactory {
    override fun create(
      context: Context,
      fixedContacts: Set<ContactSearchKey>,
      displayOptions: ContactSearchAdapter.DisplayOptions,
      callbacks: ContactSearchAdapter.ClickCallbacks,
      longClickCallbacks: ContactSearchAdapter.LongClickCallbacks,
      storyContextMenuCallbacks: ContactSearchAdapter.StoryContextMenuCallbacks,
      callButtonClickCallbacks: ContactSearchAdapter.CallButtonClickCallbacks
    ): PagingMappingAdapter<ContactSearchKey> {
      return ContactSearchAdapter(context, fixedContacts, displayOptions, callbacks, longClickCallbacks, storyContextMenuCallbacks, callButtonClickCallbacks)
    }
  }
}
