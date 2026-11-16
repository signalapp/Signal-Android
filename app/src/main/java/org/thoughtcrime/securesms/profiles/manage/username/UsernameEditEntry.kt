/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.username

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.UsernameEditMode
import org.signal.appsettings.usernameedit.UsernameEditAction
import org.signal.appsettings.usernameedit.UsernameEditScreen
import org.signal.core.ui.compose.CollectActions
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * Hosts [UsernameEditScreen] and carries out its [UsernameEditAction]s. The view model navigates by sending events to
 * [parentEventEmitter], so this works from whichever host owns the back stack.
 */
@Composable
fun UsernameEditEntry(
  mode: UsernameEditMode,
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val viewModel: UsernameEditViewModel = viewModel { UsernameEditViewModel(mode, parentEventEmitter, createSavedStateHandle()) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val activity = LocalActivity.current

  CollectActions(viewModel.actions) { action ->
    when (action) {
      is UsernameEditAction.OpenBrowserLink -> CommunicationActions.openBrowserLink(context, action.url)
      UsernameEditAction.UsernameSaved -> activity?.setResult(Activity.RESULT_OK)
      UsernameEditAction.UsernameDeleted -> Toast.makeText(context, R.string.UsernameEditFragment_successfully_removed_username, Toast.LENGTH_SHORT).show()
      UsernameEditAction.Finish -> activity?.finish()
    }
  }

  UsernameEditScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}
