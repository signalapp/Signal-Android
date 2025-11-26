/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.rememberFragmentState
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Fragments
import org.signal.core.util.DimensionUnit
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.ContactSelectionListFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode
import org.thoughtcrime.securesms.contacts.SelectedContact
import org.thoughtcrime.securesms.contacts.paged.ChatType
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.contacts.selection.ContactSelectionArguments
import org.thoughtcrime.securesms.conversation.RecipientSearchBar
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.recipients.PhoneNumber
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.RecipientPicker.DisplayMode.Companion.flag
import java.util.Optional
import java.util.function.Consumer

/**
 * Provides a recipient search and selection UI.
 */
@Suppress("KotlinConstantConditions")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipientPicker(
  searchQuery: String,
  displayModes: Set<RecipientPicker.DisplayMode> = setOf(RecipientPicker.DisplayMode.ALL),
  selectionLimits: SelectionLimits? = ContactSelectionArguments.Defaults.SELECTION_LIMITS,
  isRefreshing: Boolean,
  focusAndShowKeyboard: Boolean = LocalConfiguration.current.screenHeightDp.dp > 600.dp,
  preselectedRecipients: Set<RecipientId> = emptySet(),
  pendingRecipientSelections: Set<RecipientId> = emptySet(),
  shouldResetContactsList: Boolean = false,
  listBottomPadding: Dp? = null,
  clipListToPadding: Boolean = ContactSelectionArguments.Defaults.RECYCLER_CHILD_CLIPPING,
  callbacks: RecipientPickerCallbacks,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
  ) {
    val focusRequester = remember { FocusRequester() }
    var shouldRequestFocus by rememberSaveable { mutableStateOf(focusAndShowKeyboard) }

    LaunchedEffect(Unit) {
      if (shouldRequestFocus) {
        focusRequester.requestFocus()
      }
    }

    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
      shouldRequestFocus = isImeVisible
    }

    RecipientSearchBar(
      query = searchQuery,
      onQueryChange = { filter -> callbacks.listActions.onSearchQueryChanged(query = filter) },
      onSearch = {},
      modifier = Modifier
        .focusRequester(focusRequester)
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
    )

    RecipientSearchResultsList(
      displayModes = displayModes,
      selectionLimits = selectionLimits,
      searchQuery = searchQuery,
      isRefreshing = isRefreshing,
      preselectedRecipients = preselectedRecipients,
      pendingRecipientSelections = pendingRecipientSelections,
      shouldResetContactsList = shouldResetContactsList,
      bottomPadding = listBottomPadding,
      clipListToPadding = clipListToPadding,
      callbacks = callbacks,
      modifier = Modifier
        .fillMaxSize()
        .padding(top = 8.dp)
    )
  }
}

@Suppress("KotlinConstantConditions")
@Composable
private fun RecipientSearchResultsList(
  displayModes: Set<RecipientPicker.DisplayMode>,
  searchQuery: String,
  isRefreshing: Boolean,
  preselectedRecipients: Set<RecipientId>,
  pendingRecipientSelections: Set<RecipientId>,
  shouldResetContactsList: Boolean,
  selectionLimits: SelectionLimits? = ContactSelectionArguments.Defaults.SELECTION_LIMITS,
  bottomPadding: Dp? = null,
  clipListToPadding: Boolean = ContactSelectionArguments.Defaults.RECYCLER_CHILD_CLIPPING,
  callbacks: RecipientPickerCallbacks,
  modifier: Modifier = Modifier
) {
  val fragmentArgs = ContactSelectionArguments(
    displayMode = displayModes.flag,
    isRefreshable = callbacks.refresh != null,
    enableCreateNewGroup = callbacks.newConversation != null,
    enableFindByUsername = callbacks.findByUsername != null,
    enableFindByPhoneNumber = callbacks.findByPhoneNumber != null,
    showCallButtons = callbacks.newCall != null,
    currentSelection = preselectedRecipients,
    selectionLimits = selectionLimits,
    recyclerPadBottom = with(LocalDensity.current) { bottomPadding?.toPx()?.toInt() ?: ContactSelectionArguments.Defaults.RECYCLER_PADDING_BOTTOM },
    recyclerChildClipping = clipListToPadding
  ).toArgumentBundle()

  val fragmentState = rememberFragmentState()
  var currentFragment by remember { mutableStateOf<ContactSelectionListFragment?>(null) }
  val coroutineScope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current

  Fragments.Fragment<ContactSelectionListFragment>(
    arguments = fragmentArgs,
    fragmentState = fragmentState,
    onUpdate = { fragment ->
      currentFragment = fragment
      fragment.view?.setPadding(0, 0, 0, 0)
      fragment.setUpCallbacks(
        callbacks = callbacks,
        clearFocus = { focusManager.clearFocus() },
        coroutineScope = coroutineScope
      )
    },
    modifier = modifier
  )

  var previousQueryText by rememberSaveable { mutableStateOf("") }
  LaunchedEffect(searchQuery) {
    if (previousQueryText != searchQuery) {
      if (searchQuery.isNotBlank()) {
        currentFragment?.setQueryFilter(searchQuery)
      } else {
        currentFragment?.resetQueryFilter()
      }
      previousQueryText = searchQuery
    }
  }

  var wasRefreshing by rememberSaveable { mutableStateOf(isRefreshing) }
  LaunchedEffect(isRefreshing) {
    currentFragment?.isRefreshing = isRefreshing
    if (wasRefreshing && !isRefreshing) {
      currentFragment?.onDataRefreshed()
    }
    wasRefreshing = isRefreshing
  }

  LaunchedEffect(pendingRecipientSelections) {
    if (pendingRecipientSelections.isNotEmpty()) {
      currentFragment?.let { fragment ->
        pendingRecipientSelections.forEach { recipientId ->
          currentFragment?.addRecipientToSelectionIfAble(recipientId)
        }
        callbacks.listActions.onPendingRecipientSelectionsConsumed()

        callbacks.listActions.onSelectionChanged(
          newSelections = fragment.selectedContacts,
          totalMembersCount = fragment.totalMemberCount
        )
      }
    }
  }

  LaunchedEffect(shouldResetContactsList) {
    if (shouldResetContactsList) {
      currentFragment?.reset()
      callbacks.listActions.onContactsListReset()
    }
  }
}

private fun ContactSelectionListFragment.setUpCallbacks(
  callbacks: RecipientPickerCallbacks,
  clearFocus: () -> Unit,
  coroutineScope: CoroutineScope
) {
  val fragment: ContactSelectionListFragment = this

  if (callbacks.newConversation != null) {
    fragment.setNewConversationCallback(object : ContactSelectionListFragment.NewConversationCallback {
      override fun onInvite() = callbacks.newConversation.onInviteToSignal()
      override fun onNewGroup(forceV1: Boolean) = callbacks.newConversation.onCreateNewGroup()
    })
  } else {
    fragment.setNewConversationCallback(null)
  }

  if (callbacks.newCall != null) {
    fragment.setNewCallCallback { callbacks.newCall.onInviteToSignal() }
  } else {
    fragment.setNewCallCallback(null)
  }

  if (callbacks.findByUsername != null || callbacks.findByPhoneNumber != null) {
    fragment.setFindByCallback(object : ContactSelectionListFragment.FindByCallback {
      override fun onFindByUsername() = callbacks.findByUsername?.onFindByUsername() ?: Unit
      override fun onFindByPhoneNumber() = callbacks.findByPhoneNumber?.onFindByPhoneNumber() ?: Unit
    })
  } else {
    fragment.setFindByCallback(null)
  }

  fragment.setOnContactSelectedListener(object : ContactSelectionListFragment.OnContactSelectedListener {
    override fun onBeforeContactSelected(
      isFromUnknownSearchKey: Boolean,
      recipientId: Optional<RecipientId?>,
      number: String?,
      chatType: Optional<ChatType?>,
      resultConsumer: Consumer<Boolean?>
    ) {
      val id = recipientId.orNull()
      val phone = number?.let(::PhoneNumber)

      val selection = when {
        id != null && phone != null -> RecipientSelection.WithIdAndPhone(id, phone)
        id != null -> RecipientSelection.WithId(id)
        phone != null -> RecipientSelection.WithPhone(phone)
        else -> error("Either RecipientId or PhoneNumber must be non-null")
      }

      coroutineScope.launch {
        val shouldAllowSelection = callbacks.listActions.shouldAllowSelection(selection)
        if (shouldAllowSelection) {
          callbacks.listActions.onRecipientSelected(selection)
        }
        resultConsumer.accept(shouldAllowSelection)
      }
    }

    override fun onContactDeselected(recipientId: Optional<RecipientId?>, number: String?, chatType: Optional<ChatType?>) = Unit

    override fun onSelectionChanged() {
      callbacks.listActions.onSelectionChanged(
        newSelections = fragment.selectedContacts,
        totalMembersCount = fragment.totalMemberCount
      )
    }
  })

  fragment.setOnItemLongClickListener { anchorView, contactSearchKey, recyclerView ->
    if (callbacks.contextMenu != null) {
      coroutineScope.launch { showItemContextMenu(anchorView, contactSearchKey, recyclerView, callbacks.contextMenu) }
      true
    }
    return@setOnItemLongClickListener false
  }

  fragment.setOnRefreshListener { callbacks.refresh?.onRefresh() }
  fragment.setScrollCallback { clearFocus() }
}

private suspend fun showItemContextMenu(
  anchorView: View,
  contactSearchKey: ContactSearchKey,
  recyclerView: RecyclerView,
  callbacks: RecipientPickerCallbacks.ContextMenu
) {
  val context = anchorView.context
  val recipient = withContext(Dispatchers.IO) {
    Recipient.resolved(contactSearchKey.requireRecipientSearchKey().recipientId)
  }

  val actions = buildList {
    val messageItem = ActionItem(
      iconRes = R.drawable.ic_chat_message_24,
      title = context.getString(R.string.NewConversationActivity__message),
      tintRes = R.color.signal_colorOnSurface,
      action = { callbacks.onMessage(recipient.id) }
    )
    add(messageItem)

    if (!recipient.isSelf && !recipient.isGroup && recipient.isRegistered) {
      val voiceCallItem = ActionItem(
        iconRes = R.drawable.ic_phone_right_24,
        title = context.getString(R.string.NewConversationActivity__audio_call),
        tintRes = R.color.signal_colorOnSurface,
        action = { callbacks.onVoiceCall(recipient) }
      )
      add(voiceCallItem)
    }

    if (!recipient.isSelf && !recipient.isMmsGroup && recipient.isRegistered) {
      val videoCallItem = ActionItem(
        iconRes = R.drawable.ic_video_call_24,
        title = context.getString(R.string.NewConversationActivity__video_call),
        tintRes = R.color.signal_colorOnSurface,
        action = { callbacks.onVideoCall(recipient) }
      )
      add(videoCallItem)
    }

    if (!recipient.isSelf && !recipient.isGroup) {
      val removeItem = ActionItem(
        iconRes = R.drawable.ic_minus_circle_20,
        title = context.getString(R.string.NewConversationActivity__remove),
        tintRes = R.color.signal_colorOnSurface,
        action = { callbacks.onRemove(recipient) }
      )
      add(removeItem)
    }

    if (!recipient.isSelf) {
      val blockItem = ActionItem(
        iconRes = R.drawable.ic_block_tinted_24,
        title = context.getString(R.string.NewConversationActivity__block),
        tintRes = R.color.signal_colorError,
        action = { callbacks.onBlock(recipient) }
      )
      add(blockItem)
    }
  }

  SignalContextMenu.Builder(anchorView, anchorView.getRootView() as ViewGroup)
    .preferredVerticalPosition(SignalContextMenu.VerticalPosition.BELOW)
    .preferredHorizontalPosition(SignalContextMenu.HorizontalPosition.START)
    .offsetX(DimensionUnit.DP.toPixels(12f).toInt())
    .offsetY(DimensionUnit.DP.toPixels(12f).toInt())
    .onDismiss { recyclerView.suppressLayout(false) }
    .show(actions)

  recyclerView.suppressLayout(true)
}

@DayNightPreviews
@Composable
private fun RecipientPickerPreview() {
  RecipientPicker(
    searchQuery = "",
    isRefreshing = false,
    shouldResetContactsList = false,
    callbacks = RecipientPickerCallbacks(
      listActions = RecipientPickerCallbacks.ListActions.Empty
    )
  )
}

class RecipientPickerCallbacks(
  val listActions: ListActions,
  val refresh: Refresh? = null,
  val contextMenu: ContextMenu? = null,
  val newConversation: NewConversation? = null,
  val newCall: NewCall? = null,
  val findByUsername: FindByUsername? = null,
  val findByPhoneNumber: FindByPhoneNumber? = null
) {
  interface ListActions {
    /**
     * Validates whether the selection of [RecipientId] should be allowed. Return true if the selection can proceed, false otherwise.
     *
     * This is called before [onRecipientSelected] to provide a chance to prevent the selection.
     */
    fun onSearchQueryChanged(query: String)
    suspend fun shouldAllowSelection(selection: RecipientSelection): Boolean
    fun onRecipientSelected(selection: RecipientSelection)
    fun onSelectionChanged(newSelections: List<SelectedContact>, totalMembersCount: Int) = Unit
    fun onPendingRecipientSelectionsConsumed() = Unit
    fun onContactsListReset() = Unit

    object Empty : ListActions {
      override fun onSearchQueryChanged(query: String) = Unit
      override suspend fun shouldAllowSelection(selection: RecipientSelection): Boolean = true
      override fun onRecipientSelected(selection: RecipientSelection) = Unit
      override fun onPendingRecipientSelectionsConsumed() = Unit
      override fun onContactsListReset() = Unit
    }
  }

  interface Refresh {
    fun onRefresh()
  }

  interface ContextMenu {
    fun onMessage(id: RecipientId)
    fun onVoiceCall(recipient: Recipient)
    fun onVideoCall(recipient: Recipient)
    fun onRemove(recipient: Recipient)
    fun onBlock(recipient: Recipient)
  }

  interface NewConversation {
    fun onCreateNewGroup()
    fun onInviteToSignal()
  }

  interface NewCall {
    fun onInviteToSignal()
  }

  interface FindByUsername {
    fun onFindByUsername()
  }

  interface FindByPhoneNumber {
    fun onFindByPhoneNumber()
  }
}

object RecipientPicker {
  /**
   * Enum wrapper for [org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode].
   */
  enum class DisplayMode(val flag: Int) {
    PUSH(flag = ContactSelectionDisplayMode.FLAG_PUSH),
    SMS(flag = ContactSelectionDisplayMode.FLAG_SMS),
    ACTIVE_GROUPS(flag = ContactSelectionDisplayMode.FLAG_ACTIVE_GROUPS),
    INACTIVE_GROUPS(flag = ContactSelectionDisplayMode.FLAG_INACTIVE_GROUPS),
    SELF(flag = ContactSelectionDisplayMode.FLAG_SELF),
    BLOCK(flag = ContactSelectionDisplayMode.FLAG_BLOCK),
    HIDE_GROUPS_V1(flag = ContactSelectionDisplayMode.FLAG_HIDE_GROUPS_V1),
    HIDE_NEW(flag = ContactSelectionDisplayMode.FLAG_HIDE_NEW),
    HIDE_RECENT_HEADER(flag = ContactSelectionDisplayMode.FLAG_HIDE_RECENT_HEADER),
    GROUPS_AFTER_CONTACTS(flag = ContactSelectionDisplayMode.FLAG_GROUPS_AFTER_CONTACTS),
    GROUP_MEMBERS(flag = ContactSelectionDisplayMode.FLAG_GROUP_MEMBERS),
    ALL(flag = ContactSelectionDisplayMode.FLAG_ALL);

    companion object {
      val Set<DisplayMode>.flag: Int
        get() = fold(initial = 0) { acc, displayMode -> acc or displayMode.flag }
    }
  }
}
