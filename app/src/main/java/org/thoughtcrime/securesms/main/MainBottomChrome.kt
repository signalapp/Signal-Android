/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.annotation.ColorRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.Snackbars
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.megaphone.Megaphone
import org.thoughtcrime.securesms.megaphone.MegaphoneActionController
import org.thoughtcrime.securesms.megaphone.Megaphones
import org.thoughtcrime.securesms.window.WindowSizeClass

data class SnackbarState(
  val message: String,
  val actionState: ActionState? = null,
  val showProgress: Boolean = false,
  val duration: SnackbarDuration = SnackbarDuration.Long
) {
  data class ActionState(
    val action: String,
    @ColorRes val color: Int = R.color.core_white,
    val onActionClick: () -> Unit
  )
}

interface MainBottomChromeCallback : MainFloatingActionButtonsCallback {
  fun onMegaphoneVisible(megaphone: Megaphone)
  fun onSnackbarDismissed()

  object Empty : MainBottomChromeCallback {
    override fun onNewChatClick() = Unit
    override fun onNewCallClick() = Unit
    override fun onCameraClick(destination: MainNavigationListLocation) = Unit
    override fun onMegaphoneVisible(megaphone: Megaphone) = Unit
    override fun onSnackbarDismissed() = Unit
  }
}

data class MainBottomChromeState(
  val destination: MainNavigationListLocation = MainNavigationListLocation.CHATS,
  val megaphoneState: MainMegaphoneState = MainMegaphoneState(),
  val snackbarState: SnackbarState? = null,
  val mainToolbarMode: MainToolbarMode = MainToolbarMode.FULL
)

/**
 * Stack of bottom chrome components:
 * - The Floating Action buttons
 * - The megaphone view
 * - The snackbar
 */
@Composable
fun MainBottomChrome(
  state: MainBottomChromeState,
  callback: MainBottomChromeCallback,
  megaphoneActionController: MegaphoneActionController,
  modifier: Modifier = Modifier
) {
  val windowSizeClass = WindowSizeClass.rememberWindowSizeClass()

  Column(
    modifier = modifier
      .fillMaxWidth()
      .animateContentSize()
  ) {
    if (state.mainToolbarMode == MainToolbarMode.FULL && windowSizeClass.isCompact()) {
      Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier.fillMaxWidth()
      ) {
        MainFloatingActionButtons(
          destination = state.destination,
          callback = callback
        )
      }

      MainMegaphoneContainer(
        state = state.megaphoneState,
        controller = megaphoneActionController,
        onMegaphoneVisible = callback::onMegaphoneVisible
      )
    }

    val windowSizeClass = WindowSizeClass.rememberWindowSizeClass()
    val snackBarModifier = if (windowSizeClass.isCompact() && state.mainToolbarMode == MainToolbarMode.BASIC) {
      Modifier.navigationBarsPadding()
    } else {
      Modifier
    }

    MainSnackbar(
      snackbarState = state.snackbarState,
      onDismissed = callback::onSnackbarDismissed,
      modifier = snackBarModifier
    )
  }
}

@Composable
private fun MainSnackbar(
  snackbarState: SnackbarState?,
  onDismissed: () -> Unit,
  modifier: Modifier = Modifier
) {
  val hostState = remember { SnackbarHostState() }

  Snackbars.Host(
    hostState,
    modifier = modifier
  )

  if (snackbarState?.showProgress == true) {
    Dialogs.IndeterminateProgressDialog()
  }

  LaunchedEffect(snackbarState) {
    if (snackbarState != null) {
      val result = hostState.showSnackbar(
        message = snackbarState.message,
        actionLabel = snackbarState.actionState?.action,
        duration = snackbarState.duration
      )

      when (result) {
        SnackbarResult.Dismissed -> Unit
        SnackbarResult.ActionPerformed -> snackbarState.actionState?.onActionClick?.invoke()
      }

      onDismissed()
    }
  }
}

@SignalPreview
@Composable
fun MainBottomChromePreview() {
  Previews.Preview {
    val megaphone = remember {
      Megaphone.Builder(Megaphones.Event.ONBOARDING, Megaphone.Style.ONBOARDING).build()
    }

    Box(
      contentAlignment = Alignment.BottomCenter,
      modifier = Modifier.fillMaxSize()
    ) {
      MainBottomChrome(
        state = MainBottomChromeState(
          megaphoneState = MainMegaphoneState(
            megaphone = megaphone
          ),
          snackbarState = SnackbarState(
            message = "Test Message",
            actionState = SnackbarState.ActionState(
              action = "Ok",
              onActionClick = {}
            )
          )
        ),
        callback = MainBottomChromeCallback.Empty,
        megaphoneActionController = EmptyMegaphoneActionController
      )
    }
  }
}
