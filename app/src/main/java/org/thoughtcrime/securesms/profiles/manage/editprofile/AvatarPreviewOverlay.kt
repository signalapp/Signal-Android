/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.editprofile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.signal.appsettings.editprofile.AvatarPreviewEvent
import org.signal.appsettings.editprofile.AvatarPreviewScreen
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.rememberRecipientField

/**
 * Shows the user's avatar full screen over edit profile, grown out of the one it shows. Hiding the toolbar hides the
 * system bars with it.
 *
 * @param animatedVisibilityScope the scope this is shown and hidden in, which the avatar animates with.
 */
@Composable
fun AvatarPreviewOverlay(
  sharedTransitionScope: SharedTransitionScope,
  animatedVisibilityScope: AnimatedVisibilityScope,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val window = LocalActivity.current?.window
  val self = remember { Recipient.self() }
  val title by rememberRecipientField(self) { getDisplayName(context) }
  var toolbarVisible by rememberSaveable { mutableStateOf(true) }

  BackHandler(enabled = animatedVisibilityScope.transition.targetState == EnterExitState.Visible, onBack = onDismiss)

  if (window != null) {
    val insetsController = remember(window) { WindowCompat.getInsetsController(window, window.decorView) }

    DisposableEffect(insetsController) {
      val lightStatusBars = insetsController.isAppearanceLightStatusBars
      val lightNavigationBars = insetsController.isAppearanceLightNavigationBars
      insetsController.isAppearanceLightStatusBars = false
      insetsController.isAppearanceLightNavigationBars = false
      insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

      onDispose {
        insetsController.isAppearanceLightStatusBars = lightStatusBars
        insetsController.isAppearanceLightNavigationBars = lightNavigationBars
        insetsController.show(WindowInsetsCompat.Type.systemBars())
      }
    }

    LaunchedEffect(insetsController, toolbarVisible) {
      if (toolbarVisible) {
        insetsController.show(WindowInsetsCompat.Type.systemBars())
      } else {
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
      }
    }
  }

  SignalTheme(isDarkMode = true) {
    AvatarPreviewScreen(
      title = title,
      toolbarVisible = toolbarVisible,
      onEvent = { event ->
        when (event) {
          AvatarPreviewEvent.NavigateBackClicked,
          AvatarPreviewEvent.AvatarDraggedAway -> onDismiss()
          AvatarPreviewEvent.AvatarClicked -> toolbarVisible = !toolbarVisible
        }
      },
      avatar = { modifier ->
        SelfAvatarImage(
          shape = rememberAvatarPreviewShape(animatedVisibilityScope),
          modifier = modifier.selfAvatarSharedBounds(sharedTransitionScope, animatedVisibilityScope)
        )
      }
    )
  }
}
