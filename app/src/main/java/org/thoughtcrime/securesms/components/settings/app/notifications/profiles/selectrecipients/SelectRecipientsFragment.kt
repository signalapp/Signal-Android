/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.selectrecipients

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.viewModel
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.contacts.paged.ContactSearchMultiSelect
import org.thoughtcrime.securesms.contacts.paged.ContactSearchPagedDataSourceRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearchRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState
import org.thoughtcrime.securesms.contacts.paged.ContactSearchViewModel
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.search.SearchRepository

/**
 * Contact selection for choosing who a notification profile allows through.
 */
class SelectRecipientsFragment : ComposeFragment() {

  private val args by lazy { SelectRecipientsFragmentArgs.fromBundle(requireArguments()) }

  private val currentMembers by lazy { args.currentSelection.orEmpty().toSet() }

  private val viewModel: SelectRecipientsViewModel by viewModel {
    SelectRecipientsViewModel(args.profileId, currentMembers)
  }

  private val contactSearchViewModel: ContactSearchViewModel by viewModels {
    ContactSearchViewModel.Factory(
      selectionLimits = SelectionLimits.NO_LIMITS,
      isMultiSelect = true,
      repository = ContactSearchRepository(),
      performSafetyNumberChecks = false,
      arbitraryRepository = null,
      searchRepository = SearchRepository(requireContext().getString(R.string.note_to_self)),
      contactSearchPagedDataSourceRepository = ContactSearchPagedDataSourceRepository(requireContext()),
      fixedContacts = currentMembers.map { ContactSearchKey.RecipientSearchKey(it, isStory = false) }.toSet()
    )
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection by contactSearchViewModel.selectionState.collectAsStateWithLifecycle()

    LaunchedEffect(selection) {
      val recipients = selection.filterIsInstance<ContactSearchKey.RecipientSearchKey>().map { it.recipientId }.toSet()
      viewModel.onEvent(SelectRecipientsEvent.SelectionChanged(recipients))
    }

    CollectActions(viewModel.actions) { handleAction(it) }

    SelectRecipientsScreen(
      state = state,
      onEvent = viewModel::onEvent,
      contactPicker = { listContentPadding, modifier ->
        ContactSearchMultiSelect(
          viewModel = contactSearchViewModel,
          mapStateToConfiguration = ::mapStateToConfiguration,
          listContentPadding = listContentPadding,
          modifier = modifier
        )
      }
    )
  }

  private fun handleAction(action: SelectRecipientsAction) {
    when (action) {
      SelectRecipientsAction.NavigateBack -> findNavController().navigateUp()
    }
  }

  /** Recent chats while nothing is searched for, then Signal contacts, then active groups. */
  private fun mapStateToConfiguration(state: ContactSearchState): ContactSearchConfiguration {
    val hasQuery = !state.query.isNullOrEmpty()

    return ContactSearchConfiguration.build {
      query = state.query

      if (!hasQuery) {
        addSection(ContactSearchConfiguration.Section.Recents(includeHeader = false))
      }

      addSection(
        ContactSearchConfiguration.Section.Individuals(
          includeSelfMode = RecipientTable.IncludeSelfMode.Exclude,
          transportType = ContactSearchConfiguration.TransportType.PUSH,
          includeHeader = true
        )
      )

      addSection(ContactSearchConfiguration.Section.Groups(includeHeader = true))
    }
  }
}
