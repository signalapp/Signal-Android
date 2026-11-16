/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.paged

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.RecipientSearchBar
import org.thoughtcrime.securesms.recipients.ui.SelectedRecipientChips

/**
 * A search bar, a row of chips for what has been picked, and a [ContactSearch] list to pick from, all driven by one
 * [viewModel] created for multiple selection. The selection lives in [ContactSearchViewModel.selectionState], where
 * hosts read it from. Contacts already chosen elsewhere belong in the view model's fixed contacts, which show picked,
 * can't be unpicked, and stay out of the chips and the selection.
 *
 * @param mapStateToConfiguration Which sections the list shows for the current query.
 * @param listContentPadding Padding inside the list, such as room at the bottom for a button laid over it.
 */
@Composable
fun ContactSearchMultiSelect(
  viewModel: ContactSearchViewModel,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
  modifier: Modifier = Modifier,
  searchHint: String = stringResource(R.string.RecipientSearchBar__search_name_or_number),
  listContentPadding: PaddingValues = PaddingValues()
) {
  val query by viewModel.query.collectAsStateWithLifecycle()
  val selection by viewModel.selectionState.collectAsStateWithLifecycle()
  val selectedRecipients = remember(selection) {
    selection.filterIsInstance<ContactSearchKey.RecipientSearchKey>().filterNot { it.isStory }
  }

  Column(modifier = modifier) {
    RecipientSearchBar(
      hint = searchHint,
      query = query.orEmpty(),
      onQueryChange = viewModel::setQuery,
      onSearch = viewModel::setQuery,
      modifier = Modifier
        .fillMaxWidth()
        .horizontalGutters()
        .padding(vertical = 12.dp)
    )

    SelectedRecipientChips(
      recipientIds = selectedRecipients.map { it.recipientId },
      onRemove = { recipientId ->
        viewModel.setKeysNotSelected(selectedRecipients.filter { it.recipientId == recipientId }.toSet())
      }
    )

    ContactSearch(
      viewModel = viewModel,
      mapStateToConfiguration = mapStateToConfiguration,
      displayOptions = remember {
        ContactSearchAdapter.DisplayOptions(
          displayCheckBox = true,
          displaySecondaryInformation = ContactSearchAdapter.DisplaySecondaryInformation.ALWAYS
        )
      },
      contentPadding = listContentPadding,
      modifier = Modifier.weight(1f)
    )
  }
}
