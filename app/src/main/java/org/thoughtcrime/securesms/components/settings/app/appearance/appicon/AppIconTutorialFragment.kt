/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance.appicon

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.appicon.AppIconTutorialEvent
import org.signal.appsettings.appicon.AppIconTutorialScreen
import org.signal.core.ui.compose.ComposeFragment

class AppIconTutorialFragment : ComposeFragment() {

  @Composable
  override fun FragmentContent() {
    AppIconTutorialScreen(onEvent = ::onEvent)
  }

  private fun onEvent(event: AppIconTutorialEvent) {
    when (event) {
      AppIconTutorialEvent.NavigateBackClicked -> findNavController().popBackStack()
    }
  }
}
