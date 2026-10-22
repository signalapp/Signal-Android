/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.labs

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object LabsSettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_INDIVIDUAL_CHAT_PLAINTEXT_EXPORT = "row-individual-chat-plaintext-export"
  const val ROW_STORY_ARCHIVE = "row-story-archive"
  const val ROW_INCOGNITO = "row-incognito"
  const val ROW_BETTER_SEARCH = "row-better-search"
  const val ROW_STARRED_MESSAGES = "row-starred-messages"
  const val ROW_IMPROVED_MESSAGE_DELETION = "row-improved-message-deletion"
}

/**
 * Toggles for internal-only pre-release features. The text is deliberately not translated.
 */
@Composable
fun LabsSettingsScreen(
  state: LabsSettingsState,
  onEvent: (LabsSettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = "Labs",
    navigationContentDescription = "Go back",
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    onNavigationClick = { onEvent(LabsSettingsEvent.NavigateBackClicked) },
    modifier = modifier
  ) { contentPadding ->
    LazyColumn(
      contentPadding = contentPadding,
      modifier = Modifier.testTag(LabsSettingsTestTags.SCROLLER)
    ) {
      item {
        Disclaimer()
      }

      item {
        Rows.ToggleRow(
          checked = state.individualChatPlaintextExport,
          text = "Individual Chat Plaintext Export",
          label = "Enable exporting individual chats as a collection of human-readable plaintext files. New entry in the three-dot menu in the chat screen.",
          onCheckChanged = { onEvent(LabsSettingsEvent.IndividualChatPlaintextExportToggled(it)) },
          modifier = Modifier.testTag(LabsSettingsTestTags.ROW_INDIVIDUAL_CHAT_PLAINTEXT_EXPORT)
        )
      }

      item {
        Rows.ToggleRow(
          checked = state.storyArchive,
          text = "Story Archive",
          label = "Keep your own stories for longer and view them later. A new button on the toolbar on the stories tab.",
          onCheckChanged = { onEvent(LabsSettingsEvent.StoryArchiveToggled(it)) },
          modifier = Modifier.testTag(LabsSettingsTestTags.ROW_STORY_ARCHIVE)
        )
      }

      item {
        Rows.ToggleRow(
          checked = state.incognito,
          text = "Incognito Mode",
          label = "Adds an option to long-press a conversation to open it in incognito mode. Messages will not be marked as read and no read receipts will be sent.",
          onCheckChanged = { onEvent(LabsSettingsEvent.IncognitoToggled(it)) },
          modifier = Modifier.testTag(LabsSettingsTestTags.ROW_INCOGNITO)
        )
      }

      item {
        Rows.ToggleRow(
          checked = state.betterSearch,
          text = "Better Search",
          label = "Filter search results by date range and author. Adds a filter button to the search toolbar.",
          onCheckChanged = { onEvent(LabsSettingsEvent.BetterSearchToggled(it)) },
          modifier = Modifier.testTag(LabsSettingsTestTags.ROW_BETTER_SEARCH)
        )
      }

      item {
        Rows.ToggleRow(
          checked = state.starredMessages,
          text = "Starred Messages",
          label = "Enables starring messages for later reference. Adds star/unstar to the long-press menu and a starred messages screen accessible from conversation settings and the main menu.",
          onCheckChanged = { onEvent(LabsSettingsEvent.StarredMessagesToggled(it)) },
          modifier = Modifier.testTag(LabsSettingsTestTags.ROW_STARRED_MESSAGES)
        )
      }

      item {
        Rows.ToggleRow(
          checked = state.improvedMessageDeletion,
          text = "Improved message deletion",
          label = "Show a temporary tombstone when you delete a message, allowing you to promote it to a remote deletion.",
          onCheckChanged = { onEvent(LabsSettingsEvent.ImprovedMessageDeletionToggled(it)) },
          modifier = Modifier.testTag(LabsSettingsTestTags.ROW_IMPROVED_MESSAGE_DELETION)
        )
      }
    }
  }
}

@Composable
private fun Disclaimer() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters()
      .padding(vertical = 16.dp)
      .background(
        color = SignalTheme.colors.colorSurface2,
        shape = RoundedCornerShape(12.dp)
      )
      .padding(16.dp)
  ) {
    Icon(
      painter = painterResource(CoreUiR.drawable.symbol_info_fill_24),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(20.dp)
    )
    Text(
      text = "These are internal-only pre-release features. They are all in various unfinished states. They may need more polish, finalized designs, or cross-client compatibility before they can be released.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 12.dp)
    )
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun LabsSettingsScreenPreview() {
  LabsSettingsScreen(
    state = LabsSettingsState(storyArchive = true, starredMessages = true),
    onEvent = {}
  )
}
