/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.compose.rememberFragmentState
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Fragments
import org.thoughtcrime.securesms.ContactSelectionListFragment
import org.thoughtcrime.securesms.components.ContactFilterView
import org.thoughtcrime.securesms.contacts.paged.ChatType
import org.thoughtcrime.securesms.contacts.selection.ContactSelectionArguments
import org.thoughtcrime.securesms.recipients.PhoneNumber
import org.thoughtcrime.securesms.recipients.RecipientId
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
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
    )

    RecipientSearchResultsList(
      searchQuery = searchQuery,
      enableCreateNewGroup = enableCreateNewGroup,
      enableFindByUsername = enableFindByUsername,
      enableFindByPhoneNumber = enableFindByPhoneNumber,
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
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val wrappedView = remember {
    ContactFilterView(context, null, 0).apply {
      hintText?.let { setHint(it) }
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
        enableFindByPhoneNumber = enableFindByPhoneNumber
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
}

private fun ContactSelectionListFragment.setUpCallbacks(
  callbacks: RecipientPickerCallbacks,
  enableCreateNewGroup: Boolean,
  enableFindByUsername: Boolean,
  enableFindByPhoneNumber: Boolean
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
}

@DayNightPreviews
@Composable
private fun RecipientPickerPreview() {
  RecipientPicker(
    enableCreateNewGroup = true,
    enableFindByUsername = true,
    enableFindByPhoneNumber = true,
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
  fun onInviteToSignal()

  object Empty : RecipientPickerCallbacks {
    override fun onCreateNewGroup() = Unit
    override fun onFindByUsername() = Unit
    override fun onFindByPhoneNumber() = Unit
    override fun shouldAllowSelection(id: RecipientId): Boolean = true
    override fun onRecipientSelected(id: RecipientId?, phone: PhoneNumber?) = Unit
    override fun onMessage(id: RecipientId) = Unit
    override fun onInviteToSignal() = Unit
  }
}
