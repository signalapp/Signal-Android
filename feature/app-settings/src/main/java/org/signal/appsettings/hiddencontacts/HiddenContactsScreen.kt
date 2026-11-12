/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.hiddencontacts

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.R
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object HiddenContactsTestTags {
  const val SCROLLER = "hidden-contacts-scroller"
  const val TEXT_EMPTY = "hidden-contacts-text-empty"
  const val TEXT_CONTACT_NAME = "hidden-contacts-text-contact-name"
  const val ROW_CONTACT = "hidden-contacts-row-contact-"
  const val DIALOG_CONFIRM_UNHIDE = "hidden-contacts-dialog-confirm-unhide"
}

/**
 * Lists everyone the user has hidden, and lets them unhide each one.
 *
 * @param avatar Draws the avatar of the given contact into a 40dp box at the start of its row.
 */
@Composable
fun HiddenContactsScreen(
  state: HiddenContactsState,
  onEvent: (HiddenContactsEvent) -> Unit,
  modifier: Modifier = Modifier,
  avatar: @Composable (RecipientId) -> Unit = {}
) {
  LifecycleResumeEffect(Unit) {
    onEvent(HiddenContactsEvent.ScreenResumed)
    onPauseOrDispose {}
  }

  Scaffolds.Settings(
    title = stringResource(R.string.HiddenContactsScreen__hidden_contacts),
    onNavigationClick = { onEvent(HiddenContactsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    modifier = modifier
  ) { paddingValues ->
    AppSettingsLazyColumn(
      scaffoldPadding = paddingValues,
      modifier = Modifier.testTag(HiddenContactsTestTags.SCROLLER)
    ) {
      item {
        Text(
          text = stringResource(R.string.HiddenContactsScreen__hidden_contacts_description),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
      }

      if (state.showEmptyState) {
        item {
          Text(
            text = stringResource(R.string.HiddenContactsScreen__no_hidden_contacts),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
              .padding(horizontal = 24.dp, vertical = 16.dp)
              .testTag(HiddenContactsTestTags.TEXT_EMPTY)
          )
        }
      }

      items(state.contacts, key = { it.id.toLong() }) { contact ->
        Rows.TextRow(
          text = {
            Text(
              text = contact.name,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.testTag(HiddenContactsTestTags.TEXT_CONTACT_NAME)
            )
          },
          icon = {
            Box(modifier = Modifier.size(40.dp)) {
              avatar(contact.id)
            }
          },
          onClick = { onEvent(HiddenContactsEvent.ContactClicked(contact.id)) },
          modifier = Modifier.testTag(HiddenContactsTestTags.ROW_CONTACT + contact.id.toLong())
        )
      }
    }

    when (val dialog = state.dialog) {
      HiddenContactsState.Dialog.None -> Unit
      is HiddenContactsState.Dialog.ConfirmUnhide -> ConfirmUnhideDialog(dialog = dialog, onEvent = onEvent)
    }
  }
}

@Composable
private fun ConfirmUnhideDialog(
  dialog: HiddenContactsState.Dialog.ConfirmUnhide,
  onEvent: (HiddenContactsEvent) -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.HiddenContactsScreen__unhide_s, dialog.contact.name),
    body = stringResource(R.string.HiddenContactsScreen__unhide_body),
    confirm = stringResource(R.string.HiddenContactsScreen__unhide),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = { onEvent(HiddenContactsEvent.UnhideConfirmed) },
    onDeny = { onEvent(HiddenContactsEvent.DialogDismissed) },
    onDismissRequest = { onEvent(HiddenContactsEvent.DialogDismissed) },
    modifier = Modifier.testTag(HiddenContactsTestTags.DIALOG_CONFIRM_UNHIDE)
  )
}

private val previewContacts = listOf(
  HiddenContactsState.Contact(RecipientId.from(1), "Peter Parker"),
  HiddenContactsState.Contact(RecipientId.from(2), "Miles Morales"),
  HiddenContactsState.Contact(RecipientId.from(3), "Gwen Stacy")
)

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun HiddenContactsScreenPreview() {
  HiddenContactsScreen(
    state = HiddenContactsState(contacts = previewContacts, isLoading = false),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun HiddenContactsScreenEmptyPreview() {
  HiddenContactsScreen(
    state = HiddenContactsState(isLoading = false),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun HiddenContactsScreenConfirmUnhidePreview() {
  HiddenContactsScreen(
    state = HiddenContactsState(
      contacts = previewContacts,
      isLoading = false,
      dialog = HiddenContactsState.Dialog.ConfirmUnhide(previewContacts.first())
    ),
    onEvent = {}
  )
}
