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
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Configuration fragment for the internal conversation test fragment.
 */
class InternalConversationSpringboardFragment : ComposeFragment() {

  private val viewModel: InternalConversationSpringboardViewModel by activityViewModels()
  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  @Composable
  override fun FragmentContent() {
    Content(this::navigateBack, this::launchTestFragment, viewModel.hasWallpaper)
  }

  private fun navigateBack() {
    (appSettingsRouter::onEvent).navigateBack()
  }

  private fun launchTestFragment() {
    (appSettingsRouter::onEvent).navigateTo(AppSettingsRoute.InternalRoute.ConversationTestFragment)
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      AppSettingsRoute.InternalRoute.ConversationTestFragment -> findNavController().safeNavigate(
        InternalConversationSpringboardFragmentDirections.actionInternalConversationSpringboardFragmentToInternalConversationTestFragment()
      )
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}

@NightPreview
@Composable
private fun ContentPreview() {
  val hasWallpaper = remember { mutableStateOf(false) }

  Previews.Preview {
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
    navigationIcon = SignalIcons.ArrowStart.imageVector
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
