package org.thoughtcrime.securesms.contacts.paged

import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.util.adapter.mapping.PagingMappingAdapter
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil

class ContactSearchMediator(
  fragment: Fragment,
  recyclerView: RecyclerView,
  selectionLimits: SelectionLimits,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration
) {

  private val viewModel: ContactSearchViewModel = ViewModelProvider(fragment, ContactSearchViewModel.Factory(selectionLimits, ContactSearchRepository())).get(ContactSearchViewModel::class.java)

  init {
    val adapter = PagingMappingAdapter<ContactSearchKey>()
    recyclerView.adapter = adapter

    ContactSearchItems.register(
      mappingAdapter = adapter,
      recipientListener = this::toggleSelection,
      storyListener = this::toggleSelection,
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
    viewModel.setKeysSelected(keys)
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

  fun addToVisibleGroupStories(groupStories: Set<ContactSearchKey.Story>) {
    viewModel.addToVisibleGroupStories(groupStories)
  }

  private fun toggleSelection(contactSearchData: ContactSearchData, isSelected: Boolean) {
    if (isSelected) {
      viewModel.setKeysNotSelected(setOf(contactSearchData.contactSearchKey))
    } else {
      viewModel.setKeysSelected(setOf(contactSearchData.contactSearchKey))
    }
  }
}
