/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.selectrecipients

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.selectrecipients.SelectRecipientsEvent
import org.signal.appsettings.selectrecipients.SelectRecipientsScreen
import org.signal.core.models.recipients.RecipientId
import org.signal.remoteconfig.SelectionLimits
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.contacts.paged.ContactSearchMultiSelect
import org.thoughtcrime.securesms.contacts.paged.ContactSearchPagedDataSourceRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearchRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState
import org.thoughtcrime.securesms.contacts.paged.ContactSearchViewModel
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.search.SearchRepository

/**
 * Picking who notification profile [profileId] lets through, on top of [currentMembers], wired to its view model and to
 * the contact search it picks from.
 */
@Composable
fun SelectRecipientsEntry(
  profileId: Long,
  currentMembers: Set<RecipientId>,
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val context = LocalContext.current
  val noteToSelf = stringResource(R.string.note_to_self)
  val viewModel: SelectRecipientsViewModel = viewModel { SelectRecipientsViewModel(profileId, currentMembers, parentEventEmitter) }

  // The contact search resolves "Note to Self" when it's created, so a language change needs a new one.
  val contactSearchViewModel: ContactSearchViewModel = viewModel(
    key = "contact-search:$noteToSelf",
    factory = remember(currentMembers, noteToSelf) {
      ContactSearchViewModel.Factory(
        selectionLimits = SelectionLimits.NO_LIMITS,
        isMultiSelect = true,
        repository = ContactSearchRepository(),
        performSafetyNumberChecks = false,
        arbitraryRepository = null,
        searchRepository = SearchRepository(noteToSelf),
        contactSearchPagedDataSourceRepository = ContactSearchPagedDataSourceRepository(context, noteToSelf),
        fixedContacts = currentMembers.map { ContactSearchKey.RecipientSearchKey(it, isStory = false) }.toSet()
      )
    }
  )

  val state by viewModel.state.collectAsStateWithLifecycle()
  val selection by contactSearchViewModel.selectionState.collectAsStateWithLifecycle()

  LaunchedEffect(selection) {
    val recipients = selection.filterIsInstance<ContactSearchKey.RecipientSearchKey>().map { it.recipientId }.toSet()
    viewModel.onEvent(SelectRecipientsEvent.SelectionChanged(recipients))
  }

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
