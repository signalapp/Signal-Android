/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.addtogroup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.SignalTheme
import org.thoughtcrime.securesms.contacts.SelectedContact
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.groups.ui.GroupErrors
import org.thoughtcrime.securesms.groups.ui.addtogroup.AddToGroupsUiState.UserMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.RecipientPicker
import org.thoughtcrime.securesms.recipients.ui.RecipientPickerCallbacks
import org.thoughtcrime.securesms.recipients.ui.RecipientPickerScaffold
import org.thoughtcrime.securesms.recipients.ui.RecipientSelection

/**
 * Allows the user to add a recipient to a group.
 */
class AddToGroupsActivity : PassphraseRequiredActivity() {
  companion object {
    private const val EXTRA_RECIPIENT_ID = "recipient_id"
    private const val EXTRA_SELECTION_LIMITS = "selection_limits"
    private const val EXTRA_PRESELECTED_GROUPS = "preselected_groups"

    @JvmOverloads
    @JvmStatic
    fun createIntent(
      context: Context,
      recipientId: RecipientId,
      existingGroupMemberships: List<RecipientId>,
      selectionLimits: SelectionLimits? = null
    ): Intent {
      return Intent(context, AddToGroupsActivity::class.java).apply {
        putExtra(EXTRA_RECIPIENT_ID, recipientId)
        putExtra(EXTRA_SELECTION_LIMITS, selectionLimits)
        putParcelableArrayListExtra(EXTRA_PRESELECTED_GROUPS, ArrayList(existingGroupMemberships))
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState, ready)

    val navigateBack = onBackPressedDispatcher::onBackPressed

    setContent {
      SignalTheme {
        AddToGroupsScreen(
          viewModel = viewModel {
            AddToGroupsViewModel(
              recipientId = intent.getParcelableExtraCompat(EXTRA_RECIPIENT_ID, RecipientId::class.java)!!,
              selectionLimits = intent.getParcelableExtraCompat(EXTRA_SELECTION_LIMITS, SelectionLimits::class.java),
              existingGroupMemberships = intent.getParcelableArrayListExtraCompat(EXTRA_PRESELECTED_GROUPS, RecipientId::class.java)!!.toSet()
            )
          },
          closeScreen = navigateBack
        )
      }
    }
  }
}

@Composable
private fun AddToGroupsScreen(
  viewModel: AddToGroupsViewModel,
  closeScreen: () -> Unit
) {
  val callbacks = remember {
    object : UiCallbacks {
      override fun onSearchQueryChanged(query: String) = viewModel.onSearchQueryChanged(query)
      override fun onSelectionChanged(newSelections: List<SelectedContact>, totalMembersCount: Int) = viewModel.selectGroups(newSelections)
      override fun addToSelectedGroups() = viewModel.addToSelectedGroups()
      override fun onAddConfirmed(groupRecipient: Recipient) = viewModel.addToGroups(listOf(groupRecipient))
      override fun onUserMessageDismissed(userMessage: UserMessage) = viewModel.clearUserMessage()
      override fun onBackPressed() = closeScreen()
    }
  }

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  AddToGroupsScreenUi(
    uiState = uiState,
    callbacks = callbacks
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun AddToGroupsScreenUi(
  uiState: AddToGroupsUiState,
  callbacks: UiCallbacks
) {
  val title = if (uiState.isMultiSelectEnabled) {
    stringResource(R.string.AddToGroupActivity_add_to_groups)
  } else {
    stringResource(R.string.AddToGroupActivity_add_to_a_group)
  }

  RecipientPickerScaffold(
    title = title,
    forceSplitPane = uiState.forceSplitPane,
    onNavigateUpClick = callbacks::onBackPressed,
    topAppBarActions = {},
    snackbarHostState = remember { SnackbarHostState() },
    primaryContent = {
      AddToGroupsRecipientPicker(
        uiState = uiState,
        callbacks = callbacks
      )

      UserMessagesHost(
        userMessage = uiState.userMessage,
        onAddConfirmed = { groupRecipient -> callbacks.onAddConfirmed(groupRecipient) },
        onDismiss = callbacks::onUserMessageDismissed,
        closeScreen = callbacks::onBackPressed
      )
    },
    floatingActionButton = getDoneButton(uiState, callbacks)
  )
}

private fun getDoneButton(
  uiState: AddToGroupsUiState,
  callbacks: UiCallbacks
): (@Composable () -> Unit)? {
  return if (uiState.isMultiSelectEnabled) {
    {
      Buttons.MediumTonal(
        enabled = uiState.newSelections.isNotEmpty(),
        onClick = callbacks::addToSelectedGroups
      ) {
        Text(text = stringResource(R.string.AddMembersActivity__done))
      }
    }
  } else {
    null
  }
}

@Composable
private fun AddToGroupsRecipientPicker(
  uiState: AddToGroupsUiState,
  callbacks: UiCallbacks,
  modifier: Modifier = Modifier
) {
  RecipientPicker(
    searchQuery = uiState.searchQuery,
    displayModes = setOf(RecipientPicker.DisplayMode.ACTIVE_GROUPS, RecipientPicker.DisplayMode.GROUPS_AFTER_CONTACTS),
    selectionLimits = uiState.selectionLimits,
    preselectedRecipients = uiState.existingGroupMemberships,
    includeRecents = true,
    isRefreshing = false,
    listBottomPadding = 64.dp,
    clipListToPadding = false,
    callbacks = RecipientPickerCallbacks(
      listActions = callbacks
    ),
    modifier = modifier.fillMaxSize()
  )
}

private interface UiCallbacks : RecipientPickerCallbacks.ListActions {
  override suspend fun shouldAllowSelection(selection: RecipientSelection): Boolean = true
  override fun onRecipientSelected(selection: RecipientSelection) = Unit
  override fun onPendingRecipientSelectionsConsumed() = Unit
  fun addToSelectedGroups()
  fun onAddConfirmed(groupRecipient: Recipient)
  fun onUserMessageDismissed(userMessage: UserMessage)
  fun onBackPressed()

  object Empty : UiCallbacks {
    override fun onSearchQueryChanged(query: String) = Unit
    override fun addToSelectedGroups() = Unit
    override fun onAddConfirmed(groupRecipient: Recipient) = Unit
    override fun onUserMessageDismissed(userMessage: UserMessage) = Unit
    override fun onBackPressed() = Unit
  }
}

@Composable
private fun UserMessagesHost(
  userMessage: UserMessage?,
  onAddConfirmed: (Recipient) -> Unit,
  onDismiss: (UserMessage) -> Unit,
  closeScreen: () -> Unit
) {
  val context = LocalContext.current
  when (userMessage) {
    null -> {}

    is UserMessage.ConfirmAddToGroup -> {
      AddToGroupConfirmationDialog(
        message = userMessage,
        onAddConfirmed = onAddConfirmed,
        onDismiss = onDismiss
      )
    }

    is UserMessage.AddedRecipientToGroup -> {
      val toastMessage = stringResource(
        R.string.AddToGroupActivity_s_added_to_s,
        userMessage.recipient.getDisplayName(context),
        userMessage.targetGroup.getDisplayName(context)
      )
      Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
      onDismiss(userMessage)
      closeScreen()
    }

    is UserMessage.CantAddRecipientToLegacyGroup -> {
      Toast.makeText(context, stringResource(R.string.AddToGroupActivity_this_person_cant_be_added_to_legacy_groups), Toast.LENGTH_SHORT).show()
      onDismiss(userMessage)
    }

    is UserMessage.GroupUpdateError -> {
      val toastMessage = stringResource(GroupErrors.getUserDisplayMessage(userMessage.failureReason))
      Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
      onDismiss(userMessage)
    }
  }
}

@Composable
private fun AddToGroupConfirmationDialog(
  message: UserMessage.ConfirmAddToGroup,
  onAddConfirmed: (Recipient) -> Unit,
  onDismiss: (UserMessage) -> Unit
) {
  val context = LocalContext.current
  val bodyText: String = stringResource(
    R.string.AddToGroupActivity_add_s_to_s,
    message.recipientToAdd.getDisplayName(context),
    message.targetGroup.getDisplayName(context)
  )

  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.AddToGroupActivity_add_member),
    body = bodyText,
    confirm = stringResource(R.string.AddToGroupActivity_add),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = { onAddConfirmed(message.targetGroup) },
    onDismiss = { onDismiss(message) }
  )
}

@AllDevicePreviews
@Composable
private fun AddToSingleGroupScreenPreview() {
  Previews.Preview {
    AddToGroupsScreenUi(
      uiState = AddToGroupsUiState(
        forceSplitPane = false,
        selectionLimits = null
      ),
      callbacks = UiCallbacks.Empty
    )
  }
}

@AllDevicePreviews
@Composable
private fun AddToMultipleGroupsScreenPreview() {
  Previews.Preview {
    AddToGroupsScreenUi(
      uiState = AddToGroupsUiState(
        forceSplitPane = false,
        selectionLimits = SelectionLimits.NO_LIMITS
      ),
      callbacks = UiCallbacks.Empty
    )
  }
}
