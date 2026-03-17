/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.labs

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R

class LabsSettingsFragment : ComposeFragment() {

  private val viewModel: LabsSettingsViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()

    LabsSettingsContent(
      state = state,
      onEvent = viewModel::onEvent,
      onNavigationClick = { findNavController().popBackStack() }
    )
  }
}

@Composable
private fun LabsSettingsContent(
  state: LabsSettingsState,
  onEvent: (LabsSettingsEvents) -> Unit,
  onNavigationClick: () -> Unit
) {
  Scaffolds.Settings(
    title = "Labs",
    navigationContentDescription = "Go back",
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    onNavigationClick = onNavigationClick
  ) { contentPadding ->
    LazyColumn(
      contentPadding = contentPadding
    ) {
      item {
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
            painter = painterResource(R.drawable.symbol_info_fill_24),
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

      item {
        Rows.ToggleRow(
          checked = state.individualChatPlaintextExport,
          text = "Individual Chat Plaintext Export",
          label = "Enable exporting individual chats as a collection of human-readable plaintext files. New entry in the three-dot menu in the chat screen.",
          onCheckChanged = { onEvent(LabsSettingsEvents.ToggleIndividualChatPlaintextExport(it)) }
        )
      }

      item {
        Rows.ToggleRow(
          checked = state.storyArchive,
          text = "Story Archive",
          label = "Keep your own stories for longer and view them later. A new button on the toolbar on the stories tab.",
          onCheckChanged = { onEvent(LabsSettingsEvents.ToggleStoryArchive(it)) }
        )
      }
    }
  }
}
