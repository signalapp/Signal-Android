/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.addmembers

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.util.getParcelableArrayListExtraCompat
import org.signal.core.util.getParcelableExtraCompat
import org.signal.core.util.nullIfBlank
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.PushContactSelectionActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsEvent
import org.thoughtcrime.securesms.compose.SignalTheme
import org.thoughtcrime.securesms.contacts.SelectedContact
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.groups.ui.addmembers.AddMembersUiState.UserMessage
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.RecipientLookupFailureMessage
import org.thoughtcrime.securesms.recipients.ui.RecipientPicker
import org.thoughtcrime.securesms.recipients.ui.RecipientPickerCallbacks
import org.thoughtcrime.securesms.recipients.ui.RecipientPickerScaffold
import org.thoughtcrime.securesms.recipients.ui.RecipientSelection
import org.thoughtcrime.securesms.recipients.ui.findby.FindByActivity
import org.thoughtcrime.securesms.recipients.ui.findby.FindByMode
import java.text.NumberFormat

/**
 * Allows members to be added to an existing Signal group by selecting from a list of recipients.
 */
class AddMembersActivity : PassphraseRequiredActivity() {
  companion object {
    private const val EXTRA_GROUP_ID = "group_id"
    private const val EXTRA_SELECTION_LIMITS = "selection_limits"
    private const val EXTRA_PRESELECTED_RECIPIENTS = "preselected_recipients"

    fun createIntent(
      context: Context,
      event: ConversationSettingsEvent.AddMembersToGroup
    ): Intent {
      return Intent(context, AddMembersActivity::class.java).apply {
        putExtra(EXTRA_GROUP_ID, event.groupId)
        putExtra(EXTRA_SELECTION_LIMITS, event.selectionLimits)
        putParcelableArrayListExtra(EXTRA_PRESELECTED_RECIPIENTS, ArrayList(event.groupMembersWithoutSelf))
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState, ready)

    setContent {
      SignalTheme {
        AddMembersScreen(
          viewModel = viewModel {
            AddMembersViewModel(
              groupId = intent.getParcelableExtraCompat(EXTRA_GROUP_ID, GroupId::class.java)!!,
              existingMembersMinusSelf = intent.getParcelableArrayListExtraCompat(EXTRA_PRESELECTED_RECIPIENTS, RecipientId::class.java)!!.toSet(),
              selectionLimits = intent.getParcelableExtraCompat(EXTRA_SELECTION_LIMITS, SelectionLimits::class.java)!!
            )
          },
          activityIntent = intent,
          closeScreen = { result ->
            setResult(result.resultCode, result.data)
            onBackPressedDispatcher.onBackPressed()
          }
        )
      }
    }
  }
}

@Composable
private fun AddMembersScreen(
  viewModel: AddMembersViewModel,
  activityIntent: Intent,
  closeScreen: (result: ActivityResult) -> Unit
) {
  val findByLauncher: ActivityResultLauncher<FindByMode> = rememberLauncherForActivityResult(
    contract = FindByActivity.Contract(),
    onResult = { id -> id?.let(viewModel::selectRecipient) }
  )

  val callbacks = remember {
    object : UiCallbacks {
      override fun onSearchQueryChanged(query: String) = viewModel.onSearchQueryChanged(query)
      override fun onFindByUsername() = findByLauncher.launch(FindByMode.USERNAME)
      override fun onFindByPhoneNumber() = findByLauncher.launch(FindByMode.PHONE_NUMBER)
      override suspend fun shouldAllowSelection(selection: RecipientSelection): Boolean = viewModel.shouldAllowSelection(selection)
      override fun onSelectionChanged(newSelections: List<SelectedContact>, totalMembersCount: Int) = viewModel.onSelectionChanged(newSelections)
      override fun onPendingRecipientSelectionsConsumed() = viewModel.clearPendingRecipientSelections()
      override fun onDoneClicked() = viewModel.addSelectedMembers()
      override fun onAddConfirmed(recipientIds: Set<RecipientId>) {
        val resultIntent = activityIntent.apply {
          putParcelableArrayListExtra(
            PushContactSelectionActivity.KEY_SELECTED_RECIPIENTS,
            ArrayList(recipientIds.toList())
          )
        }
        closeScreen(ActivityResult(RESULT_OK, resultIntent))
      }

      override fun onUserMessageDismissed(userMessage: UserMessage) = viewModel.clearUserMessage()
      override fun onBackPressed() = closeScreen(ActivityResult(RESULT_CANCELED, null))
    }
  }

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  AddMembersScreenUi(
    uiState = uiState,
    callbacks = callbacks
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun AddMembersScreenUi(
  uiState: AddMembersUiState,
  callbacks: UiCallbacks
) {
  val title = if (uiState.totalMembersCount > 0) {
    pluralStringResource(
      id = R.plurals.CreateGroupActivity__s_members,
      count = uiState.totalMembersCount,
      NumberFormat.getInstance().format(uiState.totalMembersCount)
    )
  } else {
    stringResource(R.string.AddMembersActivity__add_members)
  }

  RecipientPickerScaffold(
    title = title,
    forceSplitPane = uiState.forceSplitPane,
    onNavigateUpClick = callbacks::onBackPressed,
    topAppBarActions = {},
    snackbarHostState = remember { SnackbarHostState() },
    primaryContent = {
      AddMembersRecipientPicker(
        uiState = uiState,
        callbacks = callbacks
      )

      UserMessagesHost(
        userMessage = uiState.userMessage,
        onAddConfirmed = callbacks::onAddConfirmed,
        onDismiss = callbacks::onUserMessageDismissed
      )

      if (uiState.isLookingUpRecipient) {
        Dialogs.IndeterminateProgressDialog()
      }
    }
  )
}

@Composable
private fun AddMembersRecipientPicker(
  uiState: AddMembersUiState,
  callbacks: UiCallbacks,
  modifier: Modifier = Modifier
) {
  Box(modifier = modifier) {
    RecipientPicker(
      searchQuery = uiState.searchQuery,
      displayModes = setOf(RecipientPicker.DisplayMode.PUSH),
      selectionLimits = uiState.selectionLimits,
      preselectedRecipients = uiState.existingMembersMinusSelf,
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

    Box(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
      Buttons.MediumTonal(
        enabled = uiState.newSelections.isNotEmpty(),
        onClick = callbacks::onDoneClicked
      ) {
        Text(text = stringResource(R.string.AddMembersActivity__done))
      }
    }
  }
}

private interface UiCallbacks :
  RecipientPickerCallbacks.ListActions,
  RecipientPickerCallbacks.FindByUsername,
  RecipientPickerCallbacks.FindByPhoneNumber {

  override fun onRecipientSelected(selection: RecipientSelection) = Unit
  fun onDoneClicked()
  fun onAddConfirmed(recipientIds: Set<RecipientId>)
  fun onUserMessageDismissed(userMessage: UserMessage)
  fun onBackPressed()

  object Empty : UiCallbacks {
    override fun onSearchQueryChanged(query: String) = Unit
    override fun onFindByUsername() = Unit
    override fun onFindByPhoneNumber() = Unit
    override suspend fun shouldAllowSelection(selection: RecipientSelection): Boolean = true
    override fun onDoneClicked() = Unit
    override fun onAddConfirmed(recipientIds: Set<RecipientId>) = Unit
    override fun onUserMessageDismissed(userMessage: UserMessage) = Unit
    override fun onBackPressed() = Unit
  }
}

@Composable
private fun UserMessagesHost(
  userMessage: UserMessage?,
  onAddConfirmed: (Set<RecipientId>) -> Unit,
  onDismiss: (UserMessage) -> Unit
) {
  when (userMessage) {
    null -> {}

    is UserMessage.RecipientLookupFailed -> {
      RecipientLookupFailureMessage(
        failure = userMessage.failure,
        onDismissed = { onDismiss(userMessage) }
      )
    }

    is UserMessage.CantAddRecipientToLegacyGroup -> {
      Toast.makeText(LocalContext.current, R.string.AddMembersActivity__this_person_cant_be_added_to_legacy_groups, Toast.LENGTH_SHORT).show()
      onDismiss(userMessage)
    }

    is UserMessage.GroupAddConfirmation -> {
      GroupAddConfirmationDialog(
        message = userMessage,
        onAddConfirmed = onAddConfirmed,
        onDismiss = onDismiss
      )
    }
  }
}

@Composable
private fun GroupAddConfirmationDialog(
  message: UserMessage.GroupAddConfirmation,
  onAddConfirmed: (Set<RecipientId>) -> Unit,
  onDismiss: (UserMessage) -> Unit
) {
  val context: Context = LocalContext.current
  val bodyText: String = when (message) {
    is UserMessage.ConfirmAddMember -> {
      stringResource(
        id = R.string.AddMembersActivity__add_member_to_s,
        message.recipient.getDisplayName(context),
        message.group.getDisplayTitle(context)
      )
    }

    is UserMessage.ConfirmAddMembers -> {
      pluralStringResource(
        id = R.plurals.AddMembersActivity__add_d_members_to_s,
        message.recipientIds.size,
        message.recipientIds.size,
        message.group.getDisplayTitle(context)
      )
    }
  }

  Dialogs.SimpleAlertDialog(
    title = "",
    body = bodyText,
    confirm = stringResource(R.string.AddMembersActivity__add),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = { onAddConfirmed(message.recipientIds) },
    onDismiss = { onDismiss(message) }
  )
}

private fun GroupRecord.getDisplayTitle(context: Context): String {
  return this.title.nullIfBlank() ?: context.getString(R.string.Recipient_unknown)
}

@AllDevicePreviews
@Composable
private fun AddMembersScreenPreview() {
  Previews.Preview {
    AddMembersScreenUi(
      uiState = AddMembersUiState(
        forceSplitPane = false,
        selectionLimits = SelectionLimits.NO_LIMITS
      ),
      callbacks = UiCallbacks.Empty
    )
  }
}
