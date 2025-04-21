/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.conversation.springboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment

/**
 * Configuration fragment for the internal conversation test fragment.
 */
class InternalConversationSpringboardFragment : ComposeFragment() {

  private val viewModel: InternalConversationSpringboardViewModel by navGraphViewModels(R.id.app_settings)

  @Composable
  override fun FragmentContent() {
    Content(this::navigateBack, this::launchTestFragment, viewModel.hasWallpaper)
  }

  private fun navigateBack() {
    findNavController().popBackStack()
  }

  private fun launchTestFragment() {
    findNavController().navigate(
      InternalConversationSpringboardFragmentDirections
        .actionInternalConversationSpringboardFragmentToInternalConversationTestFragment()
    )
  }
}

@Preview
@Composable
private fun ContentPreview() {
  val hasWallpaper = remember { mutableStateOf(false) }

  SignalTheme(isDarkMode = true) {
    Content(onBackPressed = {}, onLaunchTestFragment = {}, hasWallpaper = hasWallpaper)
  }
}

@Composable
private fun Content(
  onBackPressed: () -> Unit,
  onLaunchTestFragment: () -> Unit,
  hasWallpaper: MutableState<Boolean>
) {
  Scaffolds.Settings(
    title = "Conversation Test Springboard",
    onNavigationClick = onBackPressed,
    navigationIconPainter = rememberVectorPainter(ImageVector.vectorResource(id = R.drawable.symbol_arrow_start_24))
  ) {
    Column(modifier = Modifier.padding(it)) {
      Rows.TextRow(
        text = "Launch Conversation Test Fragment",
        onClick = onLaunchTestFragment
      )

      Rows.ToggleRow(
        checked = hasWallpaper.value,
        text = "Enable Wallpaper",
        onCheckChanged = { hasWallpaper.value = it }
      )
    }
  }
}
