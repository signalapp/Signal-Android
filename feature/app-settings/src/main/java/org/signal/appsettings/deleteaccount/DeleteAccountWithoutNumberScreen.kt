/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.deleteaccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.signal.appsettings.R
import org.signal.appsettings.deleteaccount.DeleteAccountState.Dialog
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons

/**
 * Lets a user with no phone number delete their account. There's no number for them to key in, so they confirm by
 * ticking a box in the dialog instead. Their username is worked into the copy when they have one, since it goes away
 * with the account.
 */
@Composable
fun DeleteAccountWithoutNumberScreen(
  state: DeleteAccountState,
  onEvent: (DeleteAccountEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = "",
    onNavigationClick = { onEvent(DeleteAccountEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    modifier = modifier
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp)
          .testTag(DeleteAccountTestTags.NUMBERLESS_SCROLLER)
      ) {
        Icon(
          imageVector = ImageVector.vectorResource(R.drawable.ic_delete_account_warning_40),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier
            .padding(top = 24.dp)
            .size(40.dp)
        )

        Text(
          text = stringResource(R.string.DeleteAccountFragment__delete_account),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(top = 16.dp)
        )

        Text(
          text = if (state.username != null) {
            stringResource(R.string.DeleteAccountScreen__deleting_your_account_with_username_s_will, state.username)
          } else {
            stringResource(R.string.DeleteAccountFragment__deleting_your_account_will)
          },
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 12.dp)
        )

        Bullets(
          username = state.username,
          walletBalance = state.walletBalance,
          modifier = Modifier.padding(top = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
      }

      Buttons.LargePrimary(
        onClick = { onEvent(DeleteAccountEvent.DeleteAccountClicked) },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.error,
          contentColor = MaterialTheme.colorScheme.onError
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 40.dp)
          .padding(top = 16.dp, bottom = 32.dp)
          .testTag(DeleteAccountTestTags.NUMBERLESS_BUTTON_DELETE)
      ) {
        Text(text = stringResource(R.string.DeleteAccountFragment__delete_account))
      }
    }

    DeleteAccountDialogs(dialog = state.dialog, onEvent = onEvent)

    val dialog = state.dialog
    if (dialog is Dialog.ConfirmNumberlessDeletion) {
      ConfirmDeletionDialog(
        confirmationChecked = dialog.confirmationChecked,
        username = state.username,
        onEvent = onEvent
      )
    }
  }
}

@Composable
private fun Bullets(
  username: String?,
  walletBalance: String?,
  modifier: Modifier = Modifier
) {
  Column(
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = modifier
  ) {
    Bullet(
      text = if (username != null) {
        stringResource(R.string.DeleteAccountScreen__delete_your_account_info_and_profile_photo_including_your_username_s, username)
      } else {
        stringResource(R.string.DeleteAccountFragment__delete_your_account_info_and_profile_photo)
      }
    )

    Bullet(text = stringResource(R.string.DeleteAccountFragment__delete_all_your_messages))

    if (walletBalance != null) {
      Bullet(text = stringResource(R.string.DeleteAccountFragment__delete_s_in_your_payments_account, walletBalance))
    }

    Bullet(text = stringResource(R.string.DeleteAccountScreen__this_action_can_not_be_undone))
  }
}

@Composable
private fun Bullet(text: String) {
  Row {
    Text(
      text = "•",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.width(12.dp))

    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

/**
 * Asks the user to confirm the deletion, which they can only go ahead with once they've ticked the box.
 */
@Composable
private fun ConfirmDeletionDialog(
  confirmationChecked: Boolean,
  username: String?,
  onEvent: (DeleteAccountEvent) -> Unit
) {
  Dialogs.BaseAlertDialog(
    onDismissRequest = { onEvent(DeleteAccountEvent.DialogDismissed) },
    title = { Text(text = stringResource(R.string.DeleteAccountFragment__are_you_sure)) },
    text = {
      Column {
        Text(
          text = if (username != null) {
            stringResource(R.string.DeleteAccountScreen__this_will_delete_your_signal_account_with_username_s, username)
          } else {
            stringResource(R.string.DeleteAccountFragment__this_will_delete_your_signal_account)
          }
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .toggleable(
              value = confirmationChecked,
              role = Role.Checkbox,
              onValueChange = { onEvent(DeleteAccountEvent.ConfirmationCheckedChanged(it)) }
            )
            .padding(vertical = 8.dp)
            .testTag(DeleteAccountTestTags.NUMBERLESS_ROW_CONFIRMATION)
        ) {
          Checkbox(
            checked = confirmationChecked,
            onCheckedChange = null
          )

          Text(
            text = stringResource(R.string.DeleteAccountScreen__yes_delete_my_account),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp)
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = confirmationChecked,
        onClick = { onEvent(DeleteAccountEvent.DeletionConfirmed) },
        modifier = Modifier.testTag(DeleteAccountTestTags.NUMBERLESS_BUTTON_CONFIRM)
      ) {
        Text(text = stringResource(R.string.DeleteAccountFragment__delete_account))
      }
    },
    dismissButton = {
      TextButton(onClick = { onEvent(DeleteAccountEvent.DialogDismissed) }) {
        Text(text = stringResource(android.R.string.cancel))
      }
    },
    modifier = Modifier.testTag(DeleteAccountTestTags.NUMBERLESS_DIALOG_CONFIRM_DELETION)
  )
}

@DayNightPreviews
@Composable
private fun DeleteAccountWithoutNumberScreenPreview() {
  Previews.Preview {
    DeleteAccountWithoutNumberScreen(
      state = DeleteAccountState(hasPhoneNumber = false),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun DeleteAccountWithoutNumberScreenUsernamePreview() {
  Previews.Preview {
    DeleteAccountWithoutNumberScreen(
      state = DeleteAccountState(hasPhoneNumber = false, username = "alice.01"),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun DeleteAccountWithoutNumberScreenConfirmDeletionPreview() {
  Previews.Preview {
    DeleteAccountWithoutNumberScreen(
      state = DeleteAccountState(hasPhoneNumber = false, dialog = Dialog.ConfirmNumberlessDeletion(confirmationChecked = true)),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun DeleteAccountWithoutNumberScreenConfirmDeletionUsernamePreview() {
  Previews.Preview {
    DeleteAccountWithoutNumberScreen(
      state = DeleteAccountState(hasPhoneNumber = false, username = "alice.01", dialog = Dialog.ConfirmNumberlessDeletion()),
      onEvent = {}
    )
  }
}
