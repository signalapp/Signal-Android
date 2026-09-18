/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.deleteaccount

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.signal.appsettings.R
import org.signal.appsettings.deleteaccount.DeleteAccountState.Dialog
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.TextFields
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object DeleteAccountTestTags {
  const val SCROLLER = "scroller"
  const val ROW_COUNTRY_PICKER = "row-country-picker"
  const val FIELD_COUNTRY_CODE = "field-country-code"
  const val FIELD_NUMBER = "field-number"
  const val BUTTON_DELETE = "button-delete"
  const val DIALOG_NUMBER_DOES_NOT_MATCH = "dialog-number-does-not-match"
  const val DIALOG_CONFIRM_DELETION = "dialog-confirm-deletion"
  const val DIALOG_DELETION_FAILED = "dialog-deletion-failed"
  const val DIALOG_LOCAL_DATA_DELETION_FAILED = "dialog-local-data-deletion-failed"
  const val DIALOG_PROGRESS = "dialog-progress"
}

@Composable
fun DeleteAccountScreen(
  state: DeleteAccountState,
  onEvent: (DeleteAccountEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences__delete_account),
    onNavigationClick = { onEvent(DeleteAccountEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    modifier = modifier
  ) { contentPadding ->
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .padding(contentPadding)
        .imePadding()
        .verticalScroll(rememberScrollState())
        .testTag(DeleteAccountTestTags.SCROLLER)
    ) {
      Icon(
        imageVector = ImageVector.vectorResource(R.drawable.ic_delete_account_warning_40),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier
          .padding(top = 16.dp)
          .size(40.dp)
      )

      Text(
        text = stringResource(R.string.DeleteAccountFragment__deleting_your_account_will),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .padding(top = 16.dp)
      )

      Bullets(
        walletBalance = state.walletBalance,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp)
      )

      Text(
        text = stringResource(R.string.DeleteAccountFragment__enter_your_phone_number),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 30.dp)
      )

      CountryPickerRow(
        countryDisplayName = state.countryDisplayName,
        onClick = { onEvent(DeleteAccountEvent.CountryPickerClicked) },
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 32.dp)
          .padding(top = 16.dp)
          .testTag(DeleteAccountTestTags.ROW_COUNTRY_PICKER)
      )

      PhoneNumberInputFields(
        state = state,
        onEvent = onEvent,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 32.dp)
          .padding(top = 12.dp)
      )

      DeleteButton(
        onClick = { onEvent(DeleteAccountEvent.DeleteAccountClicked) },
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 32.dp)
          .padding(top = 16.dp, bottom = 16.dp)
      )
    }

    DeleteAccountDialogs(dialog = state.dialog, onEvent = onEvent)
  }
}

@Composable
private fun Bullets(
  walletBalance: String?,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier) {
    Bullet(text = stringResource(R.string.DeleteAccountFragment__delete_your_account_info_and_profile_photo))
    Bullet(text = stringResource(R.string.DeleteAccountFragment__delete_all_your_messages))

    if (walletBalance != null) {
      Bullet(text = stringResource(R.string.DeleteAccountFragment__delete_s_in_your_payments_account, walletBalance))
    }
  }
}

@Composable
private fun Bullet(text: String) {
  Row {
    Text(
      text = "•",
      style = MaterialTheme.typography.bodyMedium
    )

    Spacer(modifier = Modifier.width(8.dp))

    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium
    )
  }
}

@Composable
private fun CountryPickerRow(
  countryDisplayName: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .clickable(onClick = onClick)
      .padding(16.dp)
  ) {
    Text(
      text = countryDisplayName.ifEmpty { stringResource(R.string.RegistrationActivity_select_your_country) },
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f)
    )

    Icon(
      imageVector = SignalIcons.ArrowDropDown.imageVector,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.size(24.dp)
    )
  }
}

@Composable
private fun PhoneNumberInputFields(
  state: DeleteAccountState,
  onEvent: (DeleteAccountEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  var numberFieldValue by remember { mutableStateOf(TextFieldValue(state.formattedNumber)) }
  val numberInteractionSource = remember { MutableInteractionSource() }

  LaunchedEffect(state.formattedNumber) {
    if (numberFieldValue.text != state.formattedNumber) {
      numberFieldValue = TextFieldValue(text = state.formattedNumber, selection = TextRange(state.formattedNumber.length))
    }
  }

  Row(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.Top,
    modifier = modifier
  ) {
    TextField(
      value = state.countryCode,
      onValueChange = { onEvent(DeleteAccountEvent.CountryCodeChanged(it)) },
      prefix = { Text(text = "+") },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
      colors = deleteAccountTextFieldColors(),
      modifier = Modifier
        .width(96.dp)
        .testTag(DeleteAccountTestTags.FIELD_COUNTRY_CODE)
    )

    TextField(
      value = numberFieldValue,
      onValueChange = { newValue ->
        numberFieldValue = newValue
        onEvent(DeleteAccountEvent.NationalNumberChanged(newValue.text))
      },
      label = { TextFields.Label(stringResource(R.string.RegistrationActivity_phone_number_description), numberFieldValue.text.isNotEmpty(), numberInteractionSource) },
      interactionSource = numberInteractionSource,
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(onDone = { onEvent(DeleteAccountEvent.DeleteAccountClicked) }),
      colors = deleteAccountTextFieldColors(),
      modifier = Modifier
        .weight(1f)
        .testTag(DeleteAccountTestTags.FIELD_NUMBER)
    )
  }
}

@Composable
private fun deleteAccountTextFieldColors() = TextFieldDefaults.colors(
  unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
  focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
)

@Composable
private fun DeleteButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Button(
    onClick = onClick,
    colors = ButtonDefaults.buttonColors(
      containerColor = MaterialTheme.colorScheme.error,
      contentColor = MaterialTheme.colorScheme.onError
    ),
    modifier = modifier.testTag(DeleteAccountTestTags.BUTTON_DELETE)
  ) {
    Text(text = stringResource(R.string.DeleteAccountFragment__delete_account))
  }
}

@Composable
private fun DeleteAccountDialogs(
  dialog: Dialog,
  onEvent: (DeleteAccountEvent) -> Unit
) {
  when (dialog) {
    Dialog.None -> Unit

    Dialog.NumberDoesNotMatch -> {
      Dialogs.SimpleMessageDialog(
        message = stringResource(R.string.DeleteAccountFragment__the_phone_number),
        dismiss = stringResource(android.R.string.ok),
        onDismiss = { onEvent(DeleteAccountEvent.DialogDismissed) },
        modifier = Modifier.testTag(DeleteAccountTestTags.DIALOG_NUMBER_DOES_NOT_MATCH)
      )
    }

    Dialog.ConfirmDeletion -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.DeleteAccountFragment__are_you_sure),
        body = stringResource(R.string.DeleteAccountFragment__this_will_delete_your_signal_account),
        confirm = stringResource(R.string.DeleteAccountFragment__delete_account),
        dismiss = stringResource(android.R.string.cancel),
        confirmColor = MaterialTheme.colorScheme.error,
        onConfirm = { onEvent(DeleteAccountEvent.DeletionConfirmed) },
        onDismiss = { onEvent(DeleteAccountEvent.DialogDismissed) },
        modifier = Modifier.testTag(DeleteAccountTestTags.DIALOG_CONFIRM_DELETION)
      )
    }

    Dialog.DeletionFailed -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.DeleteAccountFragment__account_not_deleted),
        body = stringResource(R.string.DeleteAccountFragment__there_was_a_problem),
        confirm = stringResource(android.R.string.ok),
        dismiss = stringResource(android.R.string.cancel),
        onConfirm = { onEvent(DeleteAccountEvent.DeletionConfirmed) },
        onDismiss = { onEvent(DeleteAccountEvent.DialogDismissed) },
        modifier = Modifier.testTag(DeleteAccountTestTags.DIALOG_DELETION_FAILED)
      )
    }

    Dialog.LocalDataDeletionFailed -> {
      Dialogs.SimpleMessageDialog(
        message = stringResource(R.string.DeleteAccountFragment__failed_to_delete_local_data),
        dismiss = stringResource(R.string.DeleteAccountFragment__launch_app_settings),
        onDismiss = { onEvent(DeleteAccountEvent.LaunchAppSettingsClicked) },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        modifier = Modifier.testTag(DeleteAccountTestTags.DIALOG_LOCAL_DATA_DELETION_FAILED)
      )
    }

    Dialog.CancelingSubscription -> {
      ProgressDialog(
        title = stringResource(R.string.DeleteAccountFragment__deleting_account),
        message = stringResource(R.string.DeleteAccountFragment__canceling_your_subscription),
        progress = null
      )
    }

    is Dialog.LeavingGroups -> {
      ProgressDialog(
        title = stringResource(R.string.DeleteAccountFragment__leaving_groups),
        message = stringResource(R.string.DeleteAccountFragment__depending_on_the_number_of_groups),
        progress = if (dialog.totalCount > 0) dialog.leaveCount.toFloat() / dialog.totalCount else null
      )
    }

    Dialog.DeletingAccount -> {
      ProgressDialog(
        title = stringResource(R.string.DeleteAccountFragment__deleting_account),
        message = stringResource(R.string.DeleteAccountFragment__deleting_all_user_data_and_resetting),
        progress = null
      )
    }
  }
}

/**
 * Non-dismissable spinner shown for the length of the deletion, which reports what part of it is underway.
 */
@Composable
private fun ProgressDialog(
  title: String,
  message: String,
  progress: Float?
) {
  Dialogs.BaseAlertDialog(
    onDismissRequest = {},
    confirmButton = {},
    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    text = {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
      ) {
        Spacer(modifier = Modifier.height(24.dp))

        if (progress == null) {
          CircularProgressIndicator(modifier = Modifier.size(48.dp))
        } else {
          CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(horizontal = dimensionResource(CoreUiR.dimen.gutter))
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = dimensionResource(CoreUiR.dimen.gutter))
        )

        Spacer(modifier = Modifier.height(24.dp))
      }
    },
    modifier = Modifier.testTag(DeleteAccountTestTags.DIALOG_PROGRESS)
  )
}

@DayNightPreviews
@Composable
private fun DeleteAccountScreenPreview() {
  Previews.Preview {
    DeleteAccountScreen(
      state = DeleteAccountState(),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun DeleteAccountScreenFilledPreview() {
  Previews.Preview {
    DeleteAccountScreen(
      state = DeleteAccountState(
        regionCode = "US",
        countryDisplayName = "United States",
        countryCode = "1",
        nationalNumber = "6105550103",
        formattedNumber = "(610) 555-0103",
        walletBalance = "0.1000 MOB"
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun DeleteAccountScreenConfirmDeletionPreview() {
  Previews.Preview {
    DeleteAccountScreen(
      state = DeleteAccountState(dialog = Dialog.ConfirmDeletion),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun DeleteAccountScreenLeavingGroupsPreview() {
  Previews.Preview {
    DeleteAccountScreen(
      state = DeleteAccountState(dialog = Dialog.LeavingGroups(totalCount = 10, leaveCount = 3)),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun DeleteAccountScreenDeletionFailedPreview() {
  Previews.Preview {
    DeleteAccountScreen(
      state = DeleteAccountState(dialog = Dialog.DeletionFailed),
      onEvent = {}
    )
  }
}
