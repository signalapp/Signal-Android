/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.selectrecipients

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.CircularProgressWrapper
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.thoughtcrime.securesms.R

@VisibleForTesting
object SelectRecipientsTestTags {
  const val PICKER = "picker"
  const val BUTTON_ADD = "button-add"
}

/**
 * Picks the people and groups a notification profile allows through.
 *
 * @param contactPicker The searchable contact list to pick from, padded inside its list by the padding it is given so
 *   its last rows can scroll clear of the add button.
 */
@Composable
fun SelectRecipientsScreen(
  state: SelectRecipientsState,
  onEvent: (SelectRecipientsEvent) -> Unit,
  modifier: Modifier = Modifier,
  contactPicker: @Composable (listContentPadding: PaddingValues, modifier: Modifier) -> Unit = { _, _ -> }
) {
  Scaffolds.Settings(
    title = stringResource(R.string.AddAllowedMembers__allowed_notifications),
    onNavigationClick = { onEvent(SelectRecipientsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    modifier = modifier
  ) { contentPadding ->
    Box(
      modifier = Modifier
        .padding(contentPadding)
        .consumeWindowInsets(contentPadding)
        .imePadding()
        .fillMaxSize()
    ) {
      contactPicker(
        PaddingValues(bottom = 80.dp),
        Modifier
          .fillMaxSize()
          .testTag(SelectRecipientsTestTags.PICKER)
      )

      CircularProgressWrapper(
        isLoading = state.isSaving,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp)
      ) {
        Buttons.LargeTonal(
          onClick = { onEvent(SelectRecipientsEvent.AddClicked) },
          enabled = state.canSave,
          modifier = Modifier.testTag(SelectRecipientsTestTags.BUTTON_ADD)
        ) {
          Text(text = stringResource(R.string.SelectRecipientsFragment__add))
        }
      }
    }
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun SelectRecipientsScreenPreview() {
  SelectRecipientsScreen(
    state = SelectRecipientsState(selection = setOf(RecipientId.from(1L))),
    onEvent = {}
  )
}
