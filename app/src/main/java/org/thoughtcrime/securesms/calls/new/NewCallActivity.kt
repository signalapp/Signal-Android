/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.new

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.new.NewCallUiState.CallType
import org.thoughtcrime.securesms.calls.new.NewCallUiState.UserMessage
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.compose.SignalTheme
import org.thoughtcrime.securesms.recipients.ui.RecipientLookupFailureMessage
import org.thoughtcrime.securesms.recipients.ui.RecipientPicker
import org.thoughtcrime.securesms.recipients.ui.RecipientPickerCallbacks
import org.thoughtcrime.securesms.recipients.ui.RecipientPickerScaffold
import org.thoughtcrime.securesms.recipients.ui.RecipientSelection
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * Allows the user to start a new call by selecting a recipient.
 */
class NewCallActivity : PassphraseRequiredActivity() {
  companion object {
    @JvmStatic
    fun createIntent(context: Context): Intent {
      return Intent(context, NewCallActivity::class.java)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState, ready)

    val navigateBack = onBackPressedDispatcher::onBackPressed

    setContent {
      SignalTheme {
        NewCallScreen(
          closeScreen = navigateBack
        )
      }
    }
  }
}

@Composable
private fun NewCallScreen(
  viewModel: NewCallViewModel = viewModel { NewCallViewModel() },
  closeScreen: () -> Unit
) {
  val context = LocalContext.current as FragmentActivity

  val callbacks = remember {
    object : UiCallbacks {
      override fun onSearchQueryChanged(query: String) = viewModel.onSearchQueryChanged(query)
      override fun onRecipientSelected(selection: RecipientSelection) = viewModel.startCall(selection)
      override fun onInviteToSignal() = context.startActivity(AppSettingsActivity.invite(context))
      override fun onRefresh() = viewModel.refresh()
      override fun onUserMessageDismissed(userMessage: UserMessage) = viewModel.clearUserMessage()
      override fun onBackPressed() = closeScreen()
    }
  }

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(uiState.pendingCall) {
    val pendingCall = uiState.pendingCall ?: return@LaunchedEffect
    when (pendingCall) {
      is CallType.Video -> CommunicationActions.startVideoCall(context, pendingCall.recipient, viewModel::showUserAlreadyInACall)
      is CallType.Voice -> CommunicationActions.startVoiceCall(context, pendingCall.recipient, viewModel::showUserAlreadyInACall)
    }
    viewModel.clearPendingCall()
  }

  NewCallScreenUi(
    uiState = uiState,
    callbacks = callbacks
  )
}

private interface UiCallbacks :
  RecipientPickerCallbacks.ListActions,
  RecipientPickerCallbacks.Refresh,
  RecipientPickerCallbacks.NewCall {

  override suspend fun shouldAllowSelection(selection: RecipientSelection): Boolean = true
  fun onUserMessageDismissed(userMessage: UserMessage)
  fun onBackPressed()

  object Empty : UiCallbacks {
    override fun onSearchQueryChanged(query: String) = Unit
    override fun onRecipientSelected(selection: RecipientSelection) = Unit
    override fun onInviteToSignal() = Unit
    override fun onRefresh() = Unit
    override fun onUserMessageDismissed(userMessage: UserMessage) = Unit
    override fun onBackPressed() = Unit
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun NewCallScreenUi(
  uiState: NewCallUiState,
  callbacks: UiCallbacks
) {
  val snackbarHostState = remember { SnackbarHostState() }

  RecipientPickerScaffold(
    title = stringResource(R.string.NewCallActivity__new_call),
    forceSplitPane = uiState.forceSplitPane,
    onNavigateUpClick = callbacks::onBackPressed,
    topAppBarActions = { TopAppBarActions(callbacks) },
    snackbarHostState = snackbarHostState,
    primaryContent = {
      RecipientPicker(
        searchQuery = uiState.searchQuery,
        displayModes = setOf(RecipientPicker.DisplayMode.PUSH, RecipientPicker.DisplayMode.ACTIVE_GROUPS, RecipientPicker.DisplayMode.GROUP_MEMBERS),
        isRefreshing = uiState.isRefreshingContacts,
        callbacks = remember(callbacks) {
          RecipientPickerCallbacks(
            listActions = callbacks,
            refresh = callbacks,
            newCall = callbacks
          )
        },
        modifier = Modifier.fillMaxSize()
      )

      UserMessagesHost(
        userMessage = uiState.userMessage,
        onDismiss = callbacks::onUserMessageDismissed,
        snackbarHostState = snackbarHostState
      )

      if (uiState.isLookingUpRecipient) {
        Dialogs.IndeterminateProgressDialog()
      }
    }
  )
}

@Composable
private fun TopAppBarActions(callbacks: UiCallbacks) {
  val menuController = remember { DropdownMenus.MenuController() }
  IconButton(
    onClick = { menuController.show() },
    modifier = Modifier.padding(horizontal = 8.dp)
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.symbol_more_vertical),
      contentDescription = stringResource(R.string.NewConversationActivity__accessibility_open_top_bar_menu)
    )
  }

  DropdownMenus.Menu(
    controller = menuController,
    offsetX = 24.dp,
    modifier = Modifier
  ) {
    DropdownMenus.Item(
      text = { Text(text = stringResource(R.string.new_conversation_activity__refresh)) },
      onClick = {
        callbacks.onRefresh()
        menuController.hide()
      }
    )

    DropdownMenus.Item(
      text = { Text(text = stringResource(R.string.text_secure_normal__invite_friends)) },
      onClick = {
        callbacks.onInviteToSignal()
        menuController.hide()
      }
    )
  }
}

@Composable
private fun UserMessagesHost(
  userMessage: UserMessage?,
  onDismiss: (UserMessage) -> Unit,
  snackbarHostState: SnackbarHostState
) {
  val context = LocalContext.current

  when (userMessage) {
    null -> {}

    is UserMessage.RecipientLookupFailed -> {
      RecipientLookupFailureMessage(
        failure = userMessage.failure,
        onDismissed = { onDismiss(userMessage) }
      )
    }

    is UserMessage.UserAlreadyInAnotherCall -> LaunchedEffect(userMessage) {
      snackbarHostState.showSnackbar(
        message = context.getString(R.string.CommunicationActions__you_are_already_in_a_call)
      )
      onDismiss(userMessage)
    }
  }
}

@AllDevicePreviews
@Composable
private fun NewCallScreenPreview() {
  Previews.Preview {
    NewCallScreenUi(
      uiState = NewCallUiState(
        forceSplitPane = false
      ),
      callbacks = UiCallbacks.Empty
    )
  }
}
