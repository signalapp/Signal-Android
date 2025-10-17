/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.compose.rememberFragmentState
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Fragments
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.ContactSelectionListFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ContactFilterView
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.contacts.paged.ChatType
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.contacts.selection.ContactSelectionArguments
import org.thoughtcrime.securesms.recipients.PhoneNumber
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.ViewUtil
import java.util.Optional
import java.util.function.Consumer

/**
 * Provides a recipient search and selection UI.
 */
@Composable
fun RecipientPicker(
  enableCreateNewGroup: Boolean,
  enableFindByUsername: Boolean,
  enableFindByPhoneNumber: Boolean,
  isRefreshing: Boolean,
  focusAndShowKeyboard: Boolean = LocalConfiguration.current.screenHeightDp.dp > 600.dp,
  shouldResetContactsList: Boolean,
  callbacks: RecipientPickerCallbacks,
  modifier: Modifier = Modifier
) {
  var searchQuery by rememberSaveable { mutableStateOf("") }

  Column(
    modifier = modifier
  ) {
    RecipientSearchField(
      onFilterChanged = { filter ->
        searchQuery = filter
      },
      focusAndShowKeyboard = focusAndShowKeyboard,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
    )

    RecipientSearchResultsList(
      searchQuery = searchQuery,
      enableCreateNewGroup = enableCreateNewGroup,
      enableFindByUsername = enableFindByUsername,
      enableFindByPhoneNumber = enableFindByPhoneNumber,
      isRefreshing = isRefreshing,
      shouldResetContactsList = shouldResetContactsList,
      callbacks = callbacks,
      modifier = Modifier
        .fillMaxSize()
        .padding(top = 8.dp)
    )
  }
}

/**
 * A search input field for finding recipients.
 *
 * Intended to be a compose-based replacement for [ContactFilterView].
 */
@Composable
private fun RecipientSearchField(
  onFilterChanged: (String) -> Unit,
  @StringRes hintText: Int? = null,
  focusAndShowKeyboard: Boolean = false,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val wrappedView = remember {
    ContactFilterView(context, null, 0).apply {
      hintText?.let { setHint(it) }
    }
  }

  // TODO [jeff] This causes the keyboard to re-open on rotation, which doesn't match the existing behavior of ContactFilterView. To fix this,
  //  RecipientSearchField needs to be converted to compose so we can use FocusRequestor.
  LaunchedEffect(focusAndShowKeyboard) {
    if (focusAndShowKeyboard) {
      wrappedView.focusAndShowKeyboard()
    } else {
      wrappedView.clearFocus()
      ViewUtil.hideKeyboard(wrappedView.context, wrappedView)
    }
  }

  DisposableEffect(onFilterChanged) {
    wrappedView.setOnFilterChangedListener { filter -> onFilterChanged(filter) }
    onDispose {
      wrappedView.setOnFilterChangedListener(null)
    }
  }

  AndroidView(
    factory = { wrappedView },
    modifier = modifier
  )
}

@Composable
private fun RecipientSearchResultsList(
  searchQuery: String,
  enableCreateNewGroup: Boolean,
  enableFindByUsername: Boolean,
  enableFindByPhoneNumber: Boolean,
  isRefreshing: Boolean,
  shouldResetContactsList: Boolean,
  callbacks: RecipientPickerCallbacks,
  modifier: Modifier = Modifier
) {
  val fragmentArgs = ContactSelectionArguments(
    enableCreateNewGroup = enableCreateNewGroup,
    enableFindByUsername = enableFindByUsername,
    enableFindByPhoneNumber = enableFindByPhoneNumber
  ).toArgumentBundle()

  val fragmentState = rememberFragmentState()
  var currentFragment by remember { mutableStateOf<ContactSelectionListFragment?>(null) }
  val coroutineScope = rememberCoroutineScope()

  Fragments.Fragment<ContactSelectionListFragment>(
    arguments = fragmentArgs,
    fragmentState = fragmentState,
    onUpdate = { fragment ->
      currentFragment = fragment
      fragment.view?.setPadding(0, 0, 0, 0)
      fragment.setUpCallbacks(
        callbacks = callbacks,
        enableCreateNewGroup = enableCreateNewGroup,
        enableFindByUsername = enableFindByUsername,
        enableFindByPhoneNumber = enableFindByPhoneNumber,
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

  LaunchedEffect(shouldResetContactsList) {
    if (shouldResetContactsList) {
      currentFragment?.reset()
      callbacks.onContactsListReset()
    }
  }
}

private fun ContactSelectionListFragment.setUpCallbacks(
  callbacks: RecipientPickerCallbacks,
  enableCreateNewGroup: Boolean,
  enableFindByUsername: Boolean,
  enableFindByPhoneNumber: Boolean,
  coroutineScope: CoroutineScope
) {
  val fragment: ContactSelectionListFragment = this

  if (enableCreateNewGroup) {
    fragment.setNewConversationCallback(object : ContactSelectionListFragment.NewConversationCallback {
      override fun onInvite() = callbacks.onInviteToSignal()
      override fun onNewGroup(forceV1: Boolean) = callbacks.onCreateNewGroup()
    })
  }

  if (enableFindByUsername || enableFindByPhoneNumber) {
    fragment.setFindByCallback(object : ContactSelectionListFragment.FindByCallback {
      override fun onFindByUsername() = callbacks.onFindByUsername()
      override fun onFindByPhoneNumber() = callbacks.onFindByPhoneNumber()
    })
  }

  fragment.setOnContactSelectedListener(object : ContactSelectionListFragment.OnContactSelectedListener {
    override fun onBeforeContactSelected(
      isFromUnknownSearchKey: Boolean,
      recipientId: Optional<RecipientId?>,
      number: String?,
      chatType: Optional<ChatType?>,
      resultConsumer: Consumer<Boolean?>
    ) {
      val recipientId = recipientId.get()
      val shouldAllowSelection = callbacks.shouldAllowSelection(recipientId)
      if (shouldAllowSelection) {
        callbacks.onRecipientSelected(
          id = recipientId,
          phone = number?.let(::PhoneNumber)
        )
      }
      resultConsumer.accept(shouldAllowSelection)
    }

    override fun onContactDeselected(recipientId: Optional<RecipientId?>, number: String?, chatType: Optional<ChatType?>) = Unit
    override fun onSelectionChanged() = Unit
  })

  fragment.setOnItemLongClickListener { anchorView, contactSearchKey, recyclerView ->
    coroutineScope.launch { showItemContextMenu(anchorView, contactSearchKey, recyclerView, callbacks) }
    true
  }

  fragment.setOnRefreshListener(callbacks::onRefresh)
  fragment.setScrollCallback {
    fragment.view?.let { view -> ViewUtil.hideKeyboard(view.context, view) }
  }
}

private suspend fun showItemContextMenu(anchorView: View, contactSearchKey: ContactSearchKey, recyclerView: RecyclerView, callbacks: RecipientPickerCallbacks) {
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
    enableCreateNewGroup = true,
    enableFindByUsername = true,
    enableFindByPhoneNumber = true,
    isRefreshing = false,
    shouldResetContactsList = false,
    callbacks = RecipientPickerCallbacks.Empty
  )
}

interface RecipientPickerCallbacks {
  fun onCreateNewGroup()
  fun onFindByUsername()
  fun onFindByPhoneNumber()

  /**
   * Validates whether the selection of [RecipientId] should be allowed. Return true if the selection can proceed, false otherwise.
   *
   * This is called before [onRecipientSelected] to provide a chance to prevent the selection.
   */
  fun shouldAllowSelection(id: RecipientId): Boolean
  fun onRecipientSelected(id: RecipientId?, phone: PhoneNumber?)
  fun onMessage(id: RecipientId)
  fun onVoiceCall(recipient: Recipient)
  fun onVideoCall(recipient: Recipient)
  fun onRemove(recipient: Recipient)
  fun onBlock(recipient: Recipient)
  fun onInviteToSignal()
  fun onRefresh()
  fun onContactsListReset()

  object Empty : RecipientPickerCallbacks {
    override fun onCreateNewGroup() = Unit
    override fun onFindByUsername() = Unit
    override fun onFindByPhoneNumber() = Unit
    override fun shouldAllowSelection(id: RecipientId): Boolean = true
    override fun onRecipientSelected(id: RecipientId?, phone: PhoneNumber?) = Unit
    override fun onMessage(id: RecipientId) = Unit
    override fun onVoiceCall(recipient: Recipient) = Unit
    override fun onVideoCall(recipient: Recipient) = Unit
    override fun onRemove(recipient: Recipient) = Unit
    override fun onBlock(recipient: Recipient) = Unit
    override fun onInviteToSignal() = Unit
    override fun onRefresh() = Unit
    override fun onContactsListReset() = Unit
  }
}
