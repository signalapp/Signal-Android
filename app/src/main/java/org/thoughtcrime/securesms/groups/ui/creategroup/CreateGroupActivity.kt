/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.compose.ScreenTitlePane
import org.thoughtcrime.securesms.contacts.SelectedContact
import org.thoughtcrime.securesms.conversation.RecipientPicker
import org.thoughtcrime.securesms.conversation.RecipientPickerCallbacks
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupUiState.NavTarget
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupUiState.UserMessage
import org.thoughtcrime.securesms.groups.ui.creategroup.details.AddGroupDetailsActivity
import org.thoughtcrime.securesms.recipients.PhoneNumber
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.findby.FindByActivity
import org.thoughtcrime.securesms.recipients.ui.findby.FindByMode
import org.thoughtcrime.securesms.window.AppScaffold
import org.thoughtcrime.securesms.window.detailPaneMaxContentWidth
import org.thoughtcrime.securesms.window.isSplitPane
import org.thoughtcrime.securesms.window.rememberAppScaffoldNavigator
import java.text.NumberFormat

/**
 * Allows creation of a Signal group by selecting from a list of recipients.
 */
class CreateGroupActivity : PassphraseRequiredActivity() {
  companion object {
    @JvmStatic
    fun createIntent(context: Context): Intent {
      return Intent(context, CreateGroupActivity::class.java)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState, ready)

    val navigateBack = onBackPressedDispatcher::onBackPressed

    setContent {
      SignalTheme {
        CreateGroupScreen(
          closeScreen = { resultCode ->
            resultCode?.let(::setResult)
            navigateBack()
          }
        )
      }
    }
  }
}

@Composable
private fun CreateGroupScreen(
  viewModel: CreateGroupViewModel = viewModel { CreateGroupViewModel() },
  closeScreen: (resultCode: Int?) -> Unit
) {
  val findByLauncher: ActivityResultLauncher<FindByMode> = rememberLauncherForActivityResult(
    contract = FindByActivity.Contract(),
    onResult = { id -> id?.let(viewModel::selectRecipient) }
  )

  val addDetailsLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
    onResult = { result: ActivityResult ->
      if (result.resultCode == Activity.RESULT_OK) {
        closeScreen(Activity.RESULT_OK)
      }
    }
  )

  val callbacks = remember {
    object : UiCallbacks {
      override fun onSearchQueryChanged(query: String) = viewModel.onSearchQueryChanged(query)
      override fun onFindByUsername() = findByLauncher.launch(FindByMode.USERNAME)
      override fun onFindByPhoneNumber() = findByLauncher.launch(FindByMode.PHONE_NUMBER)
      override suspend fun shouldAllowSelection(id: RecipientId?, phone: PhoneNumber?): Boolean = viewModel.shouldAllowSelection(id, phone)
      override fun onSelectionChanged(newSelections: List<SelectedContact>, totalMembersCount: Int) = viewModel.onSelectionChanged(newSelections, totalMembersCount)
      override fun onPendingRecipientSelectionsConsumed() = viewModel.clearPendingRecipientSelections()
      override fun onNextClicked(): Unit = viewModel.continueToGroupDetails()
      override fun onUserMessageDismissed(userMessage: UserMessage) = viewModel.clearUserMessage()
      override fun onPendingDestinationConsumed() = viewModel.clearPendingDestination()
      override fun onBackPressed() = closeScreen(null)
    }
  }

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  LaunchedEffect(uiState.pendingDestination) {
    when (val pendingDestination = uiState.pendingDestination) {
      is NavTarget.AddGroupDetails -> {
        addDetailsLauncher.launch(AddGroupDetailsActivity.newIntent(context, pendingDestination.recipientIds))
        callbacks.onPendingDestinationConsumed()
      }

      null -> Unit
    }
  }

  CreateGroupScreenUi(
    uiState = uiState,
    callbacks = callbacks
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun CreateGroupScreenUi(
  uiState: CreateGroupUiState,
  callbacks: UiCallbacks
) {
  val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
  val isSplitPane = windowSizeClass.isSplitPane(forceSplitPane = uiState.forceSplitPane)
  val snackbarHostState = remember { SnackbarHostState() }

  val titleText = if (uiState.newSelections.isNotEmpty()) {
    pluralStringResource(
      id = R.plurals.CreateGroupActivity__s_members,
      count = uiState.totalMembersCount,
      NumberFormat.getInstance().format(uiState.totalMembersCount)
    )
  } else {
    stringResource(R.string.CreateGroupActivity__select_members)
  }

  AppScaffold(
    topBarContent = {
      Scaffolds.DefaultTopAppBar(
        title = if (!isSplitPane) titleText else "",
        titleContent = { _, title -> Text(text = title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
        navigationContentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description),
        onNavigationClick = callbacks::onBackPressed
      )
    },

    secondaryContent = {
      if (isSplitPane) {
        ScreenTitlePane(
          title = titleText,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        CreateGroupRecipientPicker(
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
        CreateGroupRecipientPicker(
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
    onDismiss = callbacks::onUserMessageDismissed
  )

  if (uiState.isLookingUpRecipient) {
    Dialogs.IndeterminateProgressDialog()
  }
}

@Composable
private fun CreateGroupRecipientPicker(
  uiState: CreateGroupUiState,
  callbacks: UiCallbacks,
  modifier: Modifier = Modifier
) {
  Box(modifier = modifier) {
    RecipientPicker(
      searchQuery = uiState.searchQuery,
      displayModes = setOf(RecipientPicker.DisplayMode.PUSH),
      selectionLimits = uiState.selectionLimits,
      pendingRecipientSelections = uiState.pendingRecipientSelections,
      isRefreshing = false,
      listBottomPadding = 64.dp,
      clipListToPadding = false,
      callbacks = RecipientPickerCallbacks(
        listActions = callbacks,
        findByUsername = callbacks,
        findByPhoneNumber = callbacks
      ),
      modifier = modifier.fillMaxSize()
    )

    AnimatedContent(
      targetState = uiState.newSelections.isNotEmpty(),
      transitionSpec = {
        ContentTransform(
          targetContentEnter = EnterTransition.None,
          initialContentExit = ExitTransition.None
        ) using SizeTransform(sizeAnimationSpec = { _, _ -> tween(300) })
      },
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) { hasSelectedContacts ->
      if (hasSelectedContacts) {
        FilledTonalIconButton(
          onClick = callbacks::onNextClicked,
          content = {
            Icon(
              imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_end_24),
              contentDescription = stringResource(R.string.CreateGroupActivity__accessibility_next)
            )
          }
        )
      } else {
        Buttons.MediumTonal(
          onClick = callbacks::onNextClicked
        ) {
          Text(text = stringResource(R.string.CreateGroupActivity__skip))
        }
      }
    }
  }
}

private interface UiCallbacks :
  RecipientPickerCallbacks.ListActions,
  RecipientPickerCallbacks.FindByUsername,
  RecipientPickerCallbacks.FindByPhoneNumber {

  override fun onRecipientSelected(id: RecipientId?, phone: PhoneNumber?) = Unit
  fun onNextClicked()
  fun onUserMessageDismissed(userMessage: UserMessage)
  fun onBackPressed()
  fun onPendingDestinationConsumed()

  object Empty : UiCallbacks {
    override fun onSearchQueryChanged(query: String) = Unit
    override fun onFindByUsername() = Unit
    override fun onFindByPhoneNumber() = Unit
    override suspend fun shouldAllowSelection(id: RecipientId?, phone: PhoneNumber?): Boolean = true
    override fun onPendingRecipientSelectionsConsumed() = Unit
    override fun onNextClicked() = Unit
    override fun onUserMessageDismissed(userMessage: UserMessage) = Unit
    override fun onBackPressed() = Unit
    override fun onPendingDestinationConsumed() = Unit
  }
}

@Composable
private fun UserMessagesHost(
  userMessage: UserMessage?,
  onDismiss: (UserMessage) -> Unit
) {
  val context: Context = LocalContext.current
  when (userMessage) {
    null -> {}

    is UserMessage.Info.NetworkError -> Dialogs.SimpleMessageDialog(
      message = stringResource(R.string.NetworkFailure__network_error_check_your_connection_and_try_again),
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onDismiss(userMessage) }
    )

    is UserMessage.Info.RecipientNotSignalUser -> Dialogs.SimpleMessageDialog(
      message = stringResource(R.string.NewConversationActivity__s_is_not_a_signal_user, userMessage.phone.displayText),
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onDismiss(userMessage) }
    )

    is UserMessage.Info.RecipientsNotSignalUsers -> Dialogs.SimpleMessageDialog(
      message = pluralStringResource(
        id = R.plurals.CreateGroupActivity_not_signal_users,
        count = userMessage.recipients.size,
        userMessage.recipients.joinToString(", ") { it.getDisplayName(context) }
      ),
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onDismiss(userMessage) }
    )
  }
}

@AllDevicePreviews
@Composable
private fun CreateGroupScreenPreview() {
  Previews.Preview {
    CreateGroupScreenUi(
      uiState = CreateGroupUiState(
        forceSplitPane = false,
        selectionLimits = SelectionLimits.NO_LIMITS
      ),
      callbacks = UiCallbacks.Empty
    )
  }
}
