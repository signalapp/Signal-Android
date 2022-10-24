package org.thoughtcrime.securesms.contacts.paged

import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.stories.settings.custom.PrivateStorySettingsFragment
import org.thoughtcrime.securesms.stories.settings.my.MyStorySettingsFragment
import org.thoughtcrime.securesms.stories.settings.privacy.ChooseInitialMyStoryMembershipBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.adapter.mapping.PagingMappingAdapter
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil

class ContactSearchMediator(
  private val fragment: Fragment,
  recyclerView: RecyclerView,
  selectionLimits: SelectionLimits,
  displayCheckBox: Boolean,
  displaySmsTag: ContactSearchItems.DisplaySmsTag,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
  private val contactSelectionPreFilter: (View?, Set<ContactSearchKey>) -> Set<ContactSearchKey> = { _, s -> s },
  performSafetyNumberChecks: Boolean = true
) {

  private val viewModel: ContactSearchViewModel = ViewModelProvider(fragment, ContactSearchViewModel.Factory(selectionLimits, ContactSearchRepository(), performSafetyNumberChecks)).get(ContactSearchViewModel::class.java)

  init {

    val adapter = PagingMappingAdapter<ContactSearchKey>()
    recyclerView.adapter = adapter

    ContactSearchItems.register(
      mappingAdapter = adapter,
      displayCheckBox = displayCheckBox,
      displaySmsTag = displaySmsTag,
      recipientListener = this::toggleSelection,
      storyListener = this::toggleStorySelection,
      storyContextMenuCallbacks = StoryContextMenuCallbacks(),
      expandListener = { viewModel.expandSection(it.sectionKey) }
    )

    val dataAndSelection: LiveData<Pair<List<ContactSearchData>, Set<ContactSearchKey>>> = LiveDataUtil.combineLatest(
      viewModel.data,
      viewModel.selectionState,
      ::Pair
    )

    dataAndSelection.observe(fragment.viewLifecycleOwner) { (data, selection) ->
      adapter.submitList(ContactSearchItems.toMappingModelList(data, selection))
    }

    viewModel.controller.observe(fragment.viewLifecycleOwner) { controller ->
      adapter.setPagingController(controller)
    }

    viewModel.configurationState.observe(fragment.viewLifecycleOwner) {
      viewModel.setConfiguration(mapStateToConfiguration(it))
    }
  }

  fun onFilterChanged(filter: String?) {
    viewModel.setQuery(filter)
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

  fun addToVisibleGroupStories(groupStories: Set<ContactSearchKey.RecipientSearchKey.Story>) {
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

  private inner class StoryContextMenuCallbacks : ContactSearchItems.StoryContextMenuCallbacks {
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
}
