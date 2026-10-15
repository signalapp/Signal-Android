package org.thoughtcrime.securesms.stories.settings.select

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import org.signal.core.ui.viewModel
import org.signal.core.util.dp
import org.thoughtcrime.securesms.ContactSelectionListFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode
import org.thoughtcrime.securesms.contacts.HeaderAction
import org.thoughtcrime.securesms.contacts.paged.ChatType
import org.thoughtcrime.securesms.contacts.selection.ContactSelectionArguments
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.SystemWindowInsetsSetter
import org.thoughtcrime.securesms.util.fragments.findListener
import java.util.Optional
import java.util.function.Consumer

abstract class BaseStoryRecipientSelectionFragment : Fragment(R.layout.stories_base_recipient_selection_fragment), ContactSelectionListFragment.OnContactSelectedListener, ContactSelectionListFragment.HeaderActionProvider {

  private val viewModel: BaseStoryRecipientSelectionViewModel by viewModel {
    BaseStoryRecipientSelectionViewModel(distributionListId)
  }

  protected open val toolbarTitleId: Int = R.string.CreateStoryViewerSelectionFragment__choose_viewers
  abstract val actionButtonLabel: Int
  abstract val distributionListId: DistributionListId?
  protected open val checkboxResource: Int = R.drawable.contact_selection_checkbox

  private lateinit var toolbar: Toolbar
  private lateinit var searchField: EditText

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val actionButton: MaterialButton = view.findViewById(R.id.action_button)

    searchField = view.findViewById(R.id.search_field)

    toolbar = view.findViewById(R.id.toolbar)
    toolbar.setNavigationOnClickListener {
      exitFlow()
    }

    toolbar.setTitle(toolbarTitleId)

    toolbar.updateLayoutParams { height = ViewGroup.LayoutParams.WRAP_CONTENT }
    SystemWindowInsetsSetter.attach(toolbar, viewLifecycleOwner, WindowInsetsCompat.Type.statusBars())
    SystemWindowInsetsSetter.attach(view.findViewById(R.id.contacts_container), viewLifecycleOwner, WindowInsetsCompat.Type.navigationBars())
    SystemWindowInsetsSetter.attach(actionButton, viewLifecycleOwner, WindowInsetsCompat.Type.navigationBars(), SystemWindowInsetsSetter.ApplyMode.MARGIN)

    actionButton.setText(actionButtonLabel)

    searchField.doAfterTextChanged {
      val contactSelectionListFragment = getAttachedContactSelectionFragment()

      if (it.isNullOrEmpty()) {
        contactSelectionListFragment.resetQueryFilter()
      } else {
        contactSelectionListFragment.setQueryFilter(it.toString())
      }
    }

    actionButton.setOnClickListener {
      viewModel.onAction()
    }

    if (childFragmentManager.findFragmentById(R.id.fragment_container) == null) {
      initializeContactSelectionFragment()
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
          viewModel.state.collect {
            actionButton.isEnabled = it.selection.isNotEmpty()

            if (it.distributionListId == null || it.privateStory != null) {
              if (it.isStartingSelection) {
                getAttachedContactSelectionFragment().markSelected(it.selection.toSet())
                viewModel.onStartingSelectionAdded()
              }
              presentTitle(toolbar, it.selection.size)
            }
          }
        }

        launch {
          viewModel.actions.collect { action ->
            when (action) {
              BaseStoryRecipientSelectionViewModel.Action.ExitFlow -> exitFlow()
              BaseStoryRecipientSelectionViewModel.Action.GoToNextScreen -> goToNextScreen(
                getAttachedContactSelectionFragment().selectedContacts.map { it.getOrCreateRecipientId() }.toSet()
              )
            }
          }
        }
      }
    }
  }

  protected open fun presentTitle(toolbar: Toolbar, size: Int) {
    if (size == 0) {
      toolbar.setTitle(R.string.CreateStoryViewerSelectionFragment__choose_viewers)
    } else {
      toolbar.title = resources.getQuantityString(R.plurals.SelectViewersFragment__d_viewers, size, size)
    }
  }

  private fun getAttachedContactSelectionFragment(): ContactSelectionListFragment {
    return childFragmentManager.findFragmentById(R.id.contacts_container) as ContactSelectionListFragment
  }

  protected open fun goToNextScreen(recipients: Set<RecipientId>) {
    throw UnsupportedOperationException()
  }

  private fun exitFlow() {
    val callback = findListener<Callback>()
    if (callback == null) {
      findNavController().popBackStack()
    } else {
      callback.exitFlow()
    }
  }

  override fun onBeforeContactSelected(isFromUnknownSearchKey: Boolean, recipientId: Optional<RecipientId>, number: String?, chatType: Optional<ChatType>, callback: Consumer<Boolean>) {
    viewModel.addRecipient(recipientId.get())

    if (searchField.text.isNotBlank()) {
      searchField.setText("")
    }

    callback.accept(true)
  }

  override fun onContactDeselected(recipientId: Optional<RecipientId>, number: String?, chatType: Optional<ChatType>) {
    viewModel.removeRecipient(recipientId.get())
  }

  override fun onSelectionChanged() = Unit

  override fun getHeaderAction(): HeaderAction {
    return HeaderAction(
      R.string.BaseStoryRecipientSelectionFragment__select_all
    ) {
      viewLifecycleOwner.lifecycleScope.launch {
        val updatedRecipients = viewModel.toggleSelectAll()
        getAttachedContactSelectionFragment().markSelected(updatedRecipients)
      }
    }
  }

  private fun initializeContactSelectionFragment() {
    val contactSelectionListFragment = ContactSelectionListFragment()
    val arguments = ContactSelectionArguments(
      displayMode = ContactSelectionDisplayMode.FLAG_PUSH or ContactSelectionDisplayMode.FLAG_HIDE_NEW,
      isRefreshable = false,
      includeRecents = false,
      selectionLimits = SelectionLimits.NO_LIMITS,
      canSelectSelf = false,
      currentSelection = emptySet(),
      displayChips = true,
      recyclerPadBottom = 76.dp,
      recyclerChildClipping = false
    )

    contactSelectionListFragment.arguments = arguments.toArgumentBundle()

    childFragmentManager.beginTransaction()
      .replace(R.id.contacts_container, contactSelectionListFragment)
      .commitNowAllowingStateLoss()
  }

  interface Callback {
    fun exitFlow()
  }
}
