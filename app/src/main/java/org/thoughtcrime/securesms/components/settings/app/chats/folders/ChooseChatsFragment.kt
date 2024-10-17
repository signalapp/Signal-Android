package org.thoughtcrime.securesms.components.settings.app.chats.folders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.ContactSelectionListFragment
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ContactFilterView
import org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode
import org.thoughtcrime.securesms.contacts.SelectedContact
import org.thoughtcrime.securesms.contacts.paged.ChatType
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.ViewUtil
import java.util.Optional
import java.util.function.Consumer

class ChooseChatsFragment : LoggingFragment(), ContactSelectionListFragment.OnContactSelectedListener {

  private val viewModel: ChatFoldersViewModel by activityViewModels()

  private var includeChatsMode: Boolean = true

  private lateinit var contactFilterView: ContactFilterView
  private lateinit var doneButton: MaterialButton
  private lateinit var selectionFragment: ContactSelectionListFragment

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    includeChatsMode = arguments?.getBoolean(KEY_INCLUDE_CHATS) ?: true
    val currentSelection: Set<RecipientId> = if (includeChatsMode) {
      viewModel.state.value.pendingExcludedRecipients
    } else {
      viewModel.state.value.pendingIncludedRecipients
    }

    childFragmentManager.addFragmentOnAttachListener { _, fragment ->
      fragment.arguments = Bundle().apply {
        putInt(ContactSelectionListFragment.DISPLAY_MODE, getDefaultDisplayMode())
        putBoolean(ContactSelectionListFragment.REFRESHABLE, false)
        putBoolean(ContactSelectionListFragment.RECENTS, true)
        putParcelable(ContactSelectionListFragment.SELECTION_LIMITS, SelectionLimits.NO_LIMITS)
        putParcelableArrayList(ContactSelectionListFragment.CURRENT_SELECTION, ArrayList<RecipientId>(currentSelection))
        putBoolean(ContactSelectionListFragment.INCLUDE_CHAT_TYPES, includeChatsMode)
        putBoolean(ContactSelectionListFragment.HIDE_COUNT, true)
        putBoolean(ContactSelectionListFragment.DISPLAY_CHIPS, true)
        putBoolean(ContactSelectionListFragment.CAN_SELECT_SELF, true)
        putBoolean(ContactSelectionListFragment.RV_CLIP, false)
        putInt(ContactSelectionListFragment.RV_PADDING_BOTTOM, ViewUtil.dpToPx(60))
      }
    }

    return inflater.inflate(R.layout.choose_chats_fragment, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val toolbar: Toolbar = view.findViewById(R.id.toolbar)

    if (includeChatsMode) {
      toolbar.setTitle(R.string.CreateFoldersFragment__included_chats)
    } else {
      toolbar.setTitle(R.string.CreateFoldersFragment__exceptions)
    }
    toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

    selectionFragment = childFragmentManager.findFragmentById(R.id.contact_selection_list) as ContactSelectionListFragment
    contactFilterView = view.findViewById(R.id.contact_filter_edit_text)
    contactFilterView.setOnFilterChangedListener {
      if (it.isNullOrEmpty()) {
        selectionFragment.resetQueryFilter()
      } else {
        selectionFragment.setQueryFilter(it)
      }
    }

    doneButton = view.findViewById(R.id.done_button)
    doneButton.setOnClickListener {
      viewModel.savePendingChats()
      findNavController().popBackStack()
    }
    updateEnabledButton()
  }

  override fun onStart() {
    super.onStart()

    if (includeChatsMode && viewModel.state.value.pendingChatTypes.contains(ChatType.INDIVIDUAL)) {
      selectionFragment.markContactSelected(SelectedContact.forChatType(ChatType.INDIVIDUAL))
    }
    if (includeChatsMode && viewModel.state.value.pendingChatTypes.contains(ChatType.GROUPS)) {
      selectionFragment.markContactSelected(SelectedContact.forChatType(ChatType.GROUPS))
    }

    val activeSelection: Set<RecipientId> = if (includeChatsMode) {
      viewModel.state.value.pendingIncludedRecipients
    } else {
      viewModel.state.value.pendingExcludedRecipients
    }

    selectionFragment.markSelected(activeSelection)
  }

  override fun onBeforeContactSelected(isFromUnknownSearchKey: Boolean, recipientId: Optional<RecipientId>, number: String?, chatType: Optional<ChatType>, callback: Consumer<Boolean>) {
    if (recipientId.isPresent) {
      if (includeChatsMode) {
        viewModel.addIncludedChat(recipientId.get())
      } else {
        viewModel.addExcludedChat(recipientId.get())
      }
      callback.accept(true)
    } else if (chatType.isPresent) {
      viewModel.addChatType(chatType.get())
      callback.accept(true)
    } else {
      callback.accept(false)
    }
    updateEnabledButton()
  }

  override fun onContactDeselected(recipientId: Optional<RecipientId>, number: String?, chatType: Optional<ChatType>) {
    if (recipientId.isPresent) {
      if (includeChatsMode) {
        viewModel.removeIncludedChat(recipientId.get())
      } else {
        viewModel.removeExcludedChat(recipientId.get())
      }
    } else if (chatType.isPresent) {
      viewModel.removeChatType(chatType.get())
    }
    updateEnabledButton()
  }

  override fun onSelectionChanged() = Unit

  private fun getDefaultDisplayMode(): Int {
    return ContactSelectionDisplayMode.FLAG_PUSH or
      ContactSelectionDisplayMode.FLAG_ACTIVE_GROUPS or
      ContactSelectionDisplayMode.FLAG_HIDE_NEW or
      ContactSelectionDisplayMode.FLAG_GROUPS_AFTER_CONTACTS or
      ContactSelectionDisplayMode.FLAG_HIDE_GROUPS_V1 or
      ContactSelectionDisplayMode.FLAG_SELF
  }

  private fun updateEnabledButton() {
    doneButton.isEnabled = viewModel.enableButton()
  }

  companion object {
    private val TAG = Log.tag(ChooseChatsFragment::class.java)
    private val KEY_INCLUDE_CHATS = "include_chats"
  }
}
