package org.thoughtcrime.securesms.contacts.paged

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

class ContactSearchMediator(
  private val fragment: Fragment,
  selectionLimits: SelectionLimits,
  displayCheckBox: Boolean,
  displaySmsTag: ContactSearchAdapter.DisplaySmsTag,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
  private val contactSelectionPreFilter: (View?, Set<ContactSearchKey>) -> Set<ContactSearchKey> = { _, s -> s },
  performSafetyNumberChecks: Boolean = true,
  adapterFactory: AdapterFactory = DefaultAdapterFactory,
  arbitraryRepository: ArbitraryRepository? = null,
) {

  private val queryDebouncer = Debouncer(300, TimeUnit.MILLISECONDS)

  private val viewModel: ContactSearchViewModel = ViewModelProvider(
    fragment,
    ContactSearchViewModel.Factory(
      selectionLimits = selectionLimits,
      repository = ContactSearchRepository(),
      performSafetyNumberChecks = performSafetyNumberChecks,
      arbitraryRepository = arbitraryRepository,
      searchRepository = SearchRepository(fragment.requireContext().getString(R.string.note_to_self))
    )
  )[ContactSearchViewModel::class.java]

  val adapter = adapterFactory.create(
    displayCheckBox = displayCheckBox,
    displaySmsTag = displaySmsTag,
    recipientListener = this::toggleSelection,
    storyListener = this::toggleStorySelection,
    storyContextMenuCallbacks = StoryContextMenuCallbacks()
  ) { viewModel.expandSection(it.sectionKey) }

  init {
    val dataAndSelection: LiveData<Pair<List<ContactSearchData>, Set<ContactSearchKey>>> = LiveDataUtil.combineLatest(
      viewModel.data,
      viewModel.selectionState,
      ::Pair
    )

    dataAndSelection.observe(fragment.viewLifecycleOwner) { (data, selection) ->
      adapter.submitList(ContactSearchAdapter.toMappingModelList(data, selection, arbitraryRepository))
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
    viewModel.setKeysSelected(contactSelectionPreFilter(null, keys))
  }

  fun setKeysNotSelected(keys: Set<ContactSearchKey>) {
    viewModel.setKeysNotSelected(keys)
  }

  fun getSelectedContacts(): Set<ContactSearchKey> {
    return viewModel.getSelectedContacts()
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
      viewModel.setKeysNotSelected(setOf(contactSearchData.contactSearchKey))
    } else {
      viewModel.setKeysSelected(contactSelectionPreFilter(view, setOf(contactSearchData.contactSearchKey)))
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

  /**
   * Wraps the construction of a PagingMappingAdapter<ContactSearchKey> so that it can
   * be swapped for another implementation, allow listeners to be wrapped, etc.
   */
  fun interface AdapterFactory {
    fun create(
      displayCheckBox: Boolean,
      displaySmsTag: ContactSearchAdapter.DisplaySmsTag,
      recipientListener: (View, ContactSearchData.KnownRecipient, Boolean) -> Unit,
      storyListener: (View, ContactSearchData.Story, Boolean) -> Unit,
      storyContextMenuCallbacks: ContactSearchAdapter.StoryContextMenuCallbacks,
      expandListener: (ContactSearchData.Expand) -> Unit
    ): PagingMappingAdapter<ContactSearchKey>
  }

  private object DefaultAdapterFactory : AdapterFactory {
    override fun create(
      displayCheckBox: Boolean,
      displaySmsTag: ContactSearchAdapter.DisplaySmsTag,
      recipientListener: (View, ContactSearchData.KnownRecipient, Boolean) -> Unit,
      storyListener: (View, ContactSearchData.Story, Boolean) -> Unit,
      storyContextMenuCallbacks: ContactSearchAdapter.StoryContextMenuCallbacks,
      expandListener: (ContactSearchData.Expand) -> Unit
    ): PagingMappingAdapter<ContactSearchKey> {
      return ContactSearchAdapter(displayCheckBox, displaySmsTag, recipientListener, storyListener, storyContextMenuCallbacks, expandListener)
    }
  }
}
