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
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Provides a recipient search and selection UI.
 */
@Composable
fun RecipientPicker(
  showFindByUsernameAndPhoneOptions: Boolean,
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
      showFindByUsernameAndPhoneOptions = showFindByUsernameAndPhoneOptions,
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
  showFindByUsernameAndPhoneOptions: Boolean,
  callbacks: RecipientPickerCallbacks,
  modifier: Modifier = Modifier
) {
  val fragmentState = rememberFragmentState()
  var currentFragment by remember { mutableStateOf<ContactSelectionListFragment?>(null) }

  Fragments.Fragment<ContactSelectionListFragment>(
    fragmentState = fragmentState,
    onUpdate = { fragment ->
      currentFragment = fragment
      currentFragment?.view?.setPadding(0, 0, 0, 0)

      if (showFindByUsernameAndPhoneOptions) {
        fragment.showFindByUsernameAndPhoneOptions(object : ContactSelectionListFragment.FindByCallback {
          override fun onFindByUsername() = callbacks.onFindByUsernameClicked()
          override fun onFindByPhoneNumber() = callbacks.onFindByPhoneNumberClicked()
        })
      }
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

@DayNightPreviews
@Composable
private fun RecipientPickerPreview() {
  RecipientPicker(
    showFindByUsernameAndPhoneOptions = true,
    callbacks = RecipientPickerCallbacks.Empty
  )
}

interface RecipientPickerCallbacks {
  fun onFindByUsernameClicked()
  fun onFindByPhoneNumberClicked()
  fun onRecipientClicked(id: RecipientId)

  object Empty : RecipientPickerCallbacks {
    override fun onFindByUsernameClicked() = Unit
    override fun onFindByPhoneNumberClicked() = Unit
    override fun onRecipientClicked(id: RecipientId) = Unit
  }
}
