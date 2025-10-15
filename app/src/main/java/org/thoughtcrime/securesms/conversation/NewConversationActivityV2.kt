/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.rx3.await
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.compose.ScreenTitlePane
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.conversation.NewConversationUiState.UserMessage
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupActivity
import org.thoughtcrime.securesms.recipients.PhoneNumber
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.findby.FindByActivity
import org.thoughtcrime.securesms.recipients.ui.findby.FindByMode
import org.thoughtcrime.securesms.window.AppScaffoldWithTopBar
import org.thoughtcrime.securesms.window.WindowSizeClass
import org.thoughtcrime.securesms.window.rememberAppScaffoldNavigator

/**
 * Allows the user to start a new conversation by selecting a recipient.
 *
 * A modernized compose-based replacement for [org.thoughtcrime.securesms.NewConversationActivity].
 */
class NewConversationActivityV2 : PassphraseRequiredActivity() {
  companion object {
    @JvmStatic
    fun createIntent(context: Context): Intent = Intent(context, NewConversationActivityV2::class.java)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    val navigateBack = onBackPressedDispatcher::onBackPressed

    setContent {
      SignalTheme {
        NewConversationScreen(
          activityIntent = intent,
          closeScreen = navigateBack
        )
      }
    }
  }
}

@Composable
private fun NewConversationScreen(
  viewModel: NewConversationViewModel = viewModel { NewConversationViewModel() },
  activityIntent: Intent,
  closeScreen: () -> Unit
) {
  val context = LocalContext.current

  val createGroupLauncher: ActivityResultLauncher<Intent> = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
    onResult = { result ->
      if (result.resultCode == RESULT_OK) {
        closeScreen()
      }
    }
  )

  val findByLauncher: ActivityResultLauncher<FindByMode> = rememberLauncherForActivityResult(
    contract = FindByActivity.Contract(),
    onResult = { recipientId ->
      if (recipientId != null) {
        viewModel.onMessage(recipientId)
      }
    }
  )

  val callbacks = remember {
    object : Callbacks {
      override fun onCreateNewGroup() = createGroupLauncher.launch(CreateGroupActivity.newIntent(context))
      override fun onFindByUsername() = findByLauncher.launch(FindByMode.USERNAME)
      override fun onFindByPhoneNumber() = findByLauncher.launch(FindByMode.PHONE_NUMBER)
      override fun shouldAllowSelection(id: RecipientId): Boolean = true
      override fun onRecipientSelected(id: RecipientId?, phone: PhoneNumber?) = viewModel.onRecipientSelected(id, phone)
      override fun onMessage(id: RecipientId) = viewModel.onMessage(id)
      override fun onInviteToSignal() = context.startActivity(AppSettingsActivity.invite(context))
      override fun onUserMessageDismissed(userMessage: UserMessage) = viewModel.onUserMessageDismissed()
      override fun onBackPressed() = closeScreen()
    }
  }

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(uiState.pendingDestination) {
    uiState.pendingDestination?.let { recipientId ->
      openConversation(context, recipientId, activityIntent, onComplete = closeScreen)
    }
  }

  NewConversationScreenUi(
    uiState = uiState,
    callbacks = callbacks
  )
}

private suspend fun openConversation(
  context: Context,
  recipientId: RecipientId,
  activityIntent: Intent? = null,
  onComplete: () -> Unit = {}
) {
  val intent: Intent = ConversationIntents.createBuilder(context, recipientId, -1L)
    .map { builder ->
      if (activityIntent != null) {
        builder
          .withDraftText(activityIntent.getStringExtra(Intent.EXTRA_TEXT))
          .withDataUri(activityIntent.data)
          .withDataType(activityIntent.type)
          .build()
      } else {
        builder.build()
      }
    }
    .await()

  context.startActivity(intent)
  onComplete()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun NewConversationScreenUi(
  uiState: NewConversationUiState,
  callbacks: Callbacks
) {
  val windowSizeClass = WindowSizeClass.rememberWindowSizeClass()
  val isSplitPane = windowSizeClass.isSplitPane(forceSplitPaneOnCompactLandscape = uiState.forceSplitPaneOnCompactLandscape)

  AppScaffoldWithTopBar(
    topBarContent = {
      Scaffolds.DefaultTopAppBar(
        title = if (!isSplitPane) stringResource(R.string.NewConversationActivity__new_message) else "",
        titleContent = { _, title -> Text(text = title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
        navigationContentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description),
        onNavigationClick = callbacks::onBackPressed
      )
    },

    secondaryContent = {
      if (isSplitPane) {
        ScreenTitlePane(
          title = stringResource(R.string.NewConversationActivity__new_message),
          modifier = Modifier.fillMaxSize()
        )
      } else {
        NewConversationRecipientPicker(
          callbacks = callbacks
        )
      }
    },

    primaryContent = {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
      ) {
        NewConversationRecipientPicker(
          callbacks = callbacks,
          modifier = Modifier.widthIn(max = windowSizeClass.detailPaneMaxContentWidth)
        )
      }
    },

    navigator = rememberAppScaffoldNavigator(
      isSplitPane = isSplitPane
    )
  )

  UserMessagesHost(
    userMessage = uiState.userMessage,
    onDismiss = callbacks::onUserMessageDismissed
  )

  if (uiState.isRefreshingRecipient) {
    Dialogs.IndeterminateProgressDialog()
  }
}

private interface Callbacks : RecipientPickerCallbacks {
  fun onUserMessageDismissed(userMessage: UserMessage)
  fun onBackPressed()

  object Empty : Callbacks {
    override fun onCreateNewGroup() = Unit
    override fun onFindByUsername() = Unit
    override fun onFindByPhoneNumber() = Unit
    override fun shouldAllowSelection(id: RecipientId): Boolean = true
    override fun onRecipientSelected(id: RecipientId?, phone: PhoneNumber?) = Unit
    override fun onMessage(id: RecipientId) = Unit
    override fun onInviteToSignal() = Unit
    override fun onUserMessageDismissed(userMessage: UserMessage) = Unit
    override fun onBackPressed() = Unit
  }
}

@Composable
private fun NewConversationRecipientPicker(
  callbacks: Callbacks,
  modifier: Modifier = Modifier
) {
  RecipientPicker(
    enableCreateNewGroup = true,
    enableFindByUsername = true,
    enableFindByPhoneNumber = true,
    callbacks = callbacks,
    modifier = modifier
      .fillMaxSize()
      .padding(vertical = 12.dp)
  )
}

@Composable
private fun UserMessagesHost(
  userMessage: UserMessage?,
  onDismiss: (UserMessage) -> Unit
) {
  when (userMessage) {
    null -> {}

    UserMessage.NetworkError -> Dialogs.SimpleMessageDialog(
      message = stringResource(R.string.NetworkFailure__network_error_check_your_connection_and_try_again),
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onDismiss(userMessage) }
    )

    is UserMessage.RecipientNotSignalUser -> Dialogs.SimpleMessageDialog(
      message = stringResource(R.string.NewConversationActivity__s_is_not_a_signal_user, userMessage.phone!!.displayText),
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onDismiss(userMessage) }
    )
  }
}

@AllDevicePreviews
@Composable
private fun NewConversationScreenPreview() {
  Previews.Preview {
    NewConversationScreenUi(
      uiState = NewConversationUiState(
        forceSplitPaneOnCompactLandscape = false
      ),
      callbacks = Callbacks.Empty
    )
  }
}
