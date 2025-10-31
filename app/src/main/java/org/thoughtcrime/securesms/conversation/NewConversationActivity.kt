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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.BlockUnblockDialog
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.compose.ScreenTitlePane
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.conversation.NewConversationUiState.UserMessage
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupActivity
import org.thoughtcrime.securesms.recipients.PhoneNumber
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.findby.FindByActivity
import org.thoughtcrime.securesms.recipients.ui.findby.FindByMode
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.window.AppScaffold
import org.thoughtcrime.securesms.window.detailPaneMaxContentWidth
import org.thoughtcrime.securesms.window.isSplitPane
import org.thoughtcrime.securesms.window.rememberAppScaffoldNavigator

/**
 * Allows the user to start a new conversation by selecting a recipient.
 */
class NewConversationActivity : PassphraseRequiredActivity() {
  companion object {
    @JvmOverloads
    @JvmStatic
    fun createIntent(context: Context, draftMessage: String? = null): Intent {
      return Intent(context, NewConversationActivity::class.java).apply {
        putExtra(Intent.EXTRA_TEXT, draftMessage)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    enableEdgeToEdge()
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
  val context = LocalContext.current as FragmentActivity

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
        viewModel.openConversation(recipientId)
      }
    }
  )

  val coroutineScope = rememberCoroutineScope()
  val callbacks = remember {
    object : UiCallbacks {
      override fun onSearchQueryChanged(query: String) = viewModel.onSearchQueryChanged(query)
      override fun onCreateNewGroup() = createGroupLauncher.launch(CreateGroupActivity.createIntent(context))
      override fun onFindByUsername() = findByLauncher.launch(FindByMode.USERNAME)
      override fun onFindByPhoneNumber() = findByLauncher.launch(FindByMode.PHONE_NUMBER)
      override suspend fun shouldAllowSelection(id: RecipientId?, phone: PhoneNumber?): Boolean = true
      override fun onRecipientSelected(id: RecipientId?, phone: PhoneNumber?) = viewModel.openConversation(id, phone)
      override fun onPendingRecipientSelectionsConsumed() = Unit
      override fun onMessage(id: RecipientId) = viewModel.openConversation(id)
      override fun onVoiceCall(recipient: Recipient) = CommunicationActions.startVoiceCall(context, recipient, viewModel::showUserAlreadyInACall)
      override fun onVideoCall(recipient: Recipient) = CommunicationActions.startVideoCall(context, recipient, viewModel::showUserAlreadyInACall)

      override fun onRemove(recipient: Recipient) = viewModel.showRemoveConfirmation(recipient)
      override fun onRemoveConfirmed(recipient: Recipient) {
        coroutineScope.launch { viewModel.removeRecipient(recipient) }
      }

      override fun onBlock(recipient: Recipient) = viewModel.showBlockConfirmation(recipient)
      override fun onBlockConfirmed(recipient: Recipient) {
        coroutineScope.launch { viewModel.blockRecipient(recipient) }
      }

      override fun onInviteToSignal() = context.startActivity(AppSettingsActivity.invite(context))
      override fun onRefresh() = viewModel.refresh()
      override fun onUserMessageDismissed(userMessage: UserMessage) = viewModel.clearUserMessage()
      override fun onContactsListReset() = viewModel.clearShouldResetContactsList()
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
  callbacks: UiCallbacks
) {
  val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
  val isSplitPane = windowSizeClass.isSplitPane(forceSplitPane = uiState.forceSplitPaneOnCompactLandscape)
  val snackbarHostState = remember { SnackbarHostState() }

  AppScaffold(
    topBarContent = {
      Scaffolds.DefaultTopAppBar(
        title = if (!isSplitPane) stringResource(R.string.NewConversationActivity__new_message) else "",
        titleContent = { _, title -> Text(text = title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
        navigationContentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description),
        onNavigationClick = callbacks::onBackPressed,
        actions = { TopAppBarActions(callbacks) }
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
          uiState = uiState,
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
          uiState = uiState,
          callbacks = callbacks,
          modifier = Modifier.widthIn(max = windowSizeClass.detailPaneMaxContentWidth)
        )
      }
    },

    snackbarHost = {
      SnackbarHost(snackbarHostState)
    },

    navigator = rememberAppScaffoldNavigator(
      isSplitPane = isSplitPane
    )
  )

  UserMessagesHost(
    userMessage = uiState.userMessage,
    onDismiss = callbacks::onUserMessageDismissed,
    onRemoveConfirmed = callbacks::onRemoveConfirmed,
    onBlockConfirmed = callbacks::onBlockConfirmed,
    snackbarHostState = snackbarHostState
  )

  if (uiState.isLookingUpRecipient) {
    Dialogs.IndeterminateProgressDialog()
  }
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
    offsetY = 0.dp,
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
      text = { Text(text = stringResource(R.string.text_secure_normal__menu_new_group)) },
      onClick = {
        callbacks.onCreateNewGroup()
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

private interface UiCallbacks :
  RecipientPickerCallbacks.ListActions,
  RecipientPickerCallbacks.Refresh,
  RecipientPickerCallbacks.ContextMenu,
  RecipientPickerCallbacks.NewConversation,
  RecipientPickerCallbacks.FindByUsername,
  RecipientPickerCallbacks.FindByPhoneNumber {

  fun onRemoveConfirmed(recipient: Recipient)
  fun onBlockConfirmed(recipient: Recipient)
  fun onUserMessageDismissed(userMessage: UserMessage)
  fun onBackPressed()

  object Empty : UiCallbacks {
    override fun onSearchQueryChanged(query: String) = Unit
    override fun onCreateNewGroup() = Unit
    override fun onFindByUsername() = Unit
    override fun onFindByPhoneNumber() = Unit
    override suspend fun shouldAllowSelection(id: RecipientId?, phone: PhoneNumber?): Boolean = true
    override fun onRecipientSelected(id: RecipientId?, phone: PhoneNumber?) = Unit
    override fun onPendingRecipientSelectionsConsumed() = Unit
    override fun onMessage(id: RecipientId) = Unit
    override fun onVoiceCall(recipient: Recipient) = Unit
    override fun onVideoCall(recipient: Recipient) = Unit
    override fun onRemove(recipient: Recipient) = Unit
    override fun onRemoveConfirmed(recipient: Recipient) = Unit
    override fun onBlock(recipient: Recipient) = Unit
    override fun onBlockConfirmed(recipient: Recipient) = Unit
    override fun onInviteToSignal() = Unit
    override fun onRefresh() = Unit
    override fun onContactsListReset() = Unit
    override fun onUserMessageDismissed(userMessage: UserMessage) = Unit
    override fun onBackPressed() = Unit
  }
}

@Composable
private fun NewConversationRecipientPicker(
  uiState: NewConversationUiState,
  callbacks: UiCallbacks,
  modifier: Modifier = Modifier
) {
  RecipientPicker(
    searchQuery = uiState.searchQuery,
    isRefreshing = uiState.isRefreshingContacts,
    shouldResetContactsList = uiState.shouldResetContactsList,
    callbacks = RecipientPickerCallbacks(
      listActions = callbacks,
      refresh = callbacks,
      contextMenu = callbacks,
      newConversation = callbacks,
      findByUsername = callbacks,
      findByPhoneNumber = callbacks
    ),
    modifier = modifier.fillMaxSize()
  )
}

@Composable
private fun UserMessagesHost(
  userMessage: UserMessage?,
  onDismiss: (UserMessage) -> Unit,
  onBlockConfirmed: (Recipient) -> Unit,
  onRemoveConfirmed: (Recipient) -> Unit,
  snackbarHostState: SnackbarHostState
) {
  val context = LocalContext.current

  when (userMessage) {
    null -> {}

    is UserMessage.Info.RecipientRemoved -> LaunchedEffect(userMessage) {
      snackbarHostState.showSnackbar(
        message = context.getString(R.string.NewConversationActivity__s_has_been_removed, userMessage.recipient.getDisplayName(context))
      )
      onDismiss(userMessage)
    }

    is UserMessage.Info.RecipientBlocked -> LaunchedEffect(userMessage) {
      snackbarHostState.showSnackbar(
        message = context.getString(R.string.NewConversationActivity__s_has_been_blocked, userMessage.recipient.getDisplayName(context))
      )
      onDismiss(userMessage)
    }

    is UserMessage.Info.NetworkError -> Dialogs.SimpleMessageDialog(
      message = stringResource(R.string.NetworkFailure__network_error_check_your_connection_and_try_again),
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onDismiss(userMessage) }
    )

    is UserMessage.Info.RecipientNotSignalUser -> Dialogs.SimpleMessageDialog(
      message = stringResource(R.string.NewConversationActivity__s_is_not_a_signal_user, userMessage.phone!!.displayText),
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onDismiss(userMessage) }
    )

    is UserMessage.Info.UserAlreadyInAnotherCall -> LaunchedEffect(userMessage) {
      snackbarHostState.showSnackbar(
        message = context.getString(R.string.CommunicationActions__you_are_already_in_a_call)
      )
      onDismiss(userMessage)
    }

    is UserMessage.Prompt.ConfirmRemoveRecipient -> Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.NewConversationActivity__remove_s, userMessage.recipient.getShortDisplayName(context)),
      body = stringResource(R.string.NewConversationActivity__you_wont_see_this_person),
      confirm = stringResource(R.string.NewConversationActivity__remove),
      dismiss = stringResource(android.R.string.cancel),
      onConfirm = { onRemoveConfirmed(userMessage.recipient) },
      onDismiss = { onDismiss(userMessage) }
    )

    is UserMessage.Prompt.ConfirmBlockRecipient -> {
      val lifecycle = LocalLifecycleOwner.current.lifecycle
      LaunchedEffect(userMessage.recipient) {
        BlockUnblockDialog.showBlockFor(context, lifecycle, userMessage.recipient) {
          onBlockConfirmed(userMessage.recipient)
        }
      }
    }
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
      callbacks = UiCallbacks.Empty
    )
  }
}
