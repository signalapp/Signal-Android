package org.thoughtcrime.securesms.stories.settings.select

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import org.thoughtcrime.securesms.ContactSelectionListFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader
import org.thoughtcrime.securesms.contacts.HeaderAction
import org.thoughtcrime.securesms.contacts.selection.ContactSelectionArguments
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sharing.ShareContact
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.fragments.findListener
import org.whispersystems.libsignal.util.guava.Optional
import java.util.function.Consumer

abstract class BaseStoryRecipientSelectionFragment : Fragment(R.layout.stories_base_recipient_selection_fragment), ContactSelectionListFragment.OnContactSelectedListener, ContactSelectionListFragment.HeaderActionProvider {

  private val viewModel: BaseStoryRecipientSelectionViewModel by viewModels(
    factoryProducer = {
      BaseStoryRecipientSelectionViewModel.Factory(distributionListId, BaseStoryRecipientSelectionRepository())
    }
  )

  private val lifecycleDisposable = LifecycleDisposable()

  protected open val toolbarTitleId: Int = R.string.CreateStoryViewerSelectionFragment__choose_viewers
  abstract val actionButtonLabel: Int
  abstract val distributionListId: DistributionListId?

  private lateinit var toolbar: Toolbar

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val searchField: EditText = view.findViewById(R.id.search_field)
    val actionButton: MaterialButton = view.findViewById(R.id.action_button)

    toolbar = view.findViewById(R.id.toolbar)
    toolbar.setNavigationOnClickListener {
      exitFlow()
    }

    toolbar.setTitle(toolbarTitleId)

    lifecycleDisposable.bindTo(viewLifecycleOwner)

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

    viewModel.state.observe(viewLifecycleOwner) {
      getAttachedContactSelectionFragment().markSelected(it.map(::ShareContact).toSet())
      presentTitle(toolbar, it.size)
    }

    lifecycleDisposable += viewModel.actionObservable.subscribe { action ->
      when (action) {
        is BaseStoryRecipientSelectionViewModel.Action.ExitFlow -> exitFlow()
        is BaseStoryRecipientSelectionViewModel.Action.GoToNextScreen -> goToNextScreen(
          getAttachedContactSelectionFragment().selectedContacts.map { it.getOrCreateRecipientId(requireContext()) }.toSet()
        )
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

  override fun onBeforeContactSelected(recipientId: Optional<RecipientId>, number: String?, callback: Consumer<Boolean>) {
    viewModel.addRecipient(recipientId.get())
    callback.accept(true)
  }

  override fun onContactDeselected(recipientId: Optional<RecipientId>, number: String?) {
    viewModel.removeRecipient(recipientId.get())
  }

  override fun onSelectionChanged() = Unit

  override fun getHeaderAction(): HeaderAction {
    return HeaderAction(
      R.string.BaseStoryRecipientSelectionFragment__select_all,
    ) {
      viewModel.toggleSelectAll()
    }
  }

  private fun initializeContactSelectionFragment() {
    val contactSelectionListFragment = ContactSelectionListFragment()
    val arguments = ContactSelectionArguments(
      displayMode = ContactsCursorLoader.DisplayMode.FLAG_PUSH,
      isRefreshable = false,
      displayRecents = false,
      selectionLimits = SelectionLimits.NO_LIMITS,
      canSelectSelf = false,
      currentSelection = emptyList(),
      displaySelectionCount = false,
      displayChips = true
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
