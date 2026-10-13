/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.deleteaccount

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.signal.appsettings.R
import org.signal.appsettings.deleteaccount.DeleteAccountState.Dialog
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.TextFields

/**
 * Lets a user with a phone number delete their account, which they confirm by keying that number back in.
 */
@Composable
fun DeleteAccountWithNumberScreen(
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

    if (state.dialog == Dialog.ConfirmDeletion) {
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

@DayNightPreviews
@Composable
private fun DeleteAccountWithNumberScreenPreview() {
  Previews.Preview {
    DeleteAccountWithNumberScreen(
      state = DeleteAccountState(),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun DeleteAccountWithNumberScreenFilledPreview() {
  Previews.Preview {
    DeleteAccountWithNumberScreen(
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
private fun DeleteAccountWithNumberScreenConfirmDeletionPreview() {
  Previews.Preview {
    DeleteAccountWithNumberScreen(
      state = DeleteAccountState(dialog = Dialog.ConfirmDeletion),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun DeleteAccountWithNumberScreenLeavingGroupsPreview() {
  Previews.Preview {
    DeleteAccountWithNumberScreen(
      state = DeleteAccountState(dialog = Dialog.LeavingGroups(totalCount = 10, leaveCount = 3)),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun DeleteAccountWithNumberScreenDeletionFailedPreview() {
  Previews.Preview {
    DeleteAccountWithNumberScreen(
      state = DeleteAccountState(dialog = Dialog.DeletionFailed),
      onEvent = {}
    )
  }
}
