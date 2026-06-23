/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.mutiselect.forward

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentHashMapOf
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearch
import org.thoughtcrime.securesms.contacts.paged.ContactSearchAdapter
import org.thoughtcrime.securesms.contacts.paged.ContactSearchCallbacks
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState
import org.thoughtcrime.securesms.contacts.paged.ContactSearchViewModel
import org.thoughtcrime.securesms.conversation.RecipientSearchBar
import org.thoughtcrime.securesms.util.adapter.mapping.compose.MappingEntryProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiselectForwardScreen(
  isSplitPane: Boolean,
  args: MultiselectForwardFragmentArgs,
  contactSearchViewModel: ContactSearchViewModel,
  callback: MultiselectForwardFragment.Callback,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
  additionalEntries: MappingEntryProvider<Any> = persistentHashMapOf(),
  contactSearchCallbacks: ContactSearchCallbacks,
  bottomContentPadding: Dp = 0.dp
) {
  if (args.isWrappedInBottomSheet) {
    MultiselectForwardContent(
      isSplitPane = isSplitPane,
      args = args,
      contactSearchViewModel = contactSearchViewModel,
      callback = callback,
      mapStateToConfiguration = mapStateToConfiguration,
      contactSearchCallbacks = contactSearchCallbacks,
      additionalEntries = additionalEntries,
      bottomContentPadding = bottomContentPadding
    )
  } else {
    Scaffold(
      topBar = {
        if (args.isToolbarVisible) {
          TopAppBar(
            title = {
              if (!isSplitPane) {
                Text(text = stringResource(args.title))
              }
            },
            navigationIcon = {
              IconButton(
                onClick = {
                  callback.navigateUp()
                }
              ) {
                Icon(
                  imageVector = SignalIcons.ArrowStart.imageVector,
                  contentDescription = stringResource(R.string.DSLSettingsToolbar__navigate_up)
                )
              }
            }
          )
        }
      }
    ) {
      MultiselectForwardContent(
        isSplitPane = isSplitPane,
        args = args,
        contactSearchViewModel = contactSearchViewModel,
        callback = callback,
        mapStateToConfiguration = mapStateToConfiguration,
        contactSearchCallbacks = contactSearchCallbacks,
        modifier = Modifier.padding(it),
        additionalEntries = additionalEntries,
        bottomContentPadding = bottomContentPadding
      )
    }
  }
}

@Composable
private fun MultiselectForwardContent(
  isSplitPane: Boolean,
  args: MultiselectForwardFragmentArgs,
  contactSearchViewModel: ContactSearchViewModel,
  callback: MultiselectForwardFragment.Callback,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
  contactSearchCallbacks: ContactSearchCallbacks,
  additionalEntries: MappingEntryProvider<Any>,
  bottomContentPadding: Dp = 0.dp,
  modifier: Modifier = Modifier
) {
  Row(modifier = modifier) {
    if (isSplitPane) {
      Box(
        modifier = Modifier
          .weight(1f)
          .horizontalGutters()
          .padding(top = 4.dp)
      ) {
        Text(
          text = stringResource(args.title),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground
        )
      }
    }

    Column(
      modifier = Modifier
        .weight(1f)
        .padding(bottom = bottomContentPadding)
    ) {
      if (args.isSearchEnabled) {
        val query by contactSearchViewModel.query.collectAsStateWithLifecycle()
        RecipientSearchBar(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .horizontalGutters(),
          query = query ?: "",
          onQueryChange = {
            contactSearchViewModel.setQuery(it)
          },
          onSearch = {
            contactSearchViewModel.setQuery(it)
          },
          onFocusChanged = {
            if (it) {
              callback.onSearchInputFocused()
            }
          }
        )
      }

      ContactSearch(
        viewModel = contactSearchViewModel,
        mapStateToConfiguration = mapStateToConfiguration,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        additionalEntries = additionalEntries,
        displayOptions = remember {
          ContactSearchAdapter.DisplayOptions(
            displayCheckBox = !args.selectSingleRecipient,
            displaySecondaryInformation = ContactSearchAdapter.DisplaySecondaryInformation.NEVER,
            displayStoryRing = true
          )
        },
        callbacks = contactSearchCallbacks
      )
    }
  }
}
