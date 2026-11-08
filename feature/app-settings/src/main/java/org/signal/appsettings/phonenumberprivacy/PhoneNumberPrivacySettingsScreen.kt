/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.phonenumberprivacy

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.R
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.horizontalGutters

@VisibleForTesting
object PhoneNumberPrivacySettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_EVERYONE_CAN_SEE = "row-everyone-can-see"
  const val ROW_NOBODY_CAN_SEE = "row-nobody-can-see"
  const val ROW_EVERYONE_CAN_FIND = "row-everyone-can-find"
  const val ROW_NOBODY_CAN_FIND = "row-nobody-can-find"
}

/**
 * Lets the user choose who can see their phone number and who can find them by it.
 */
@Composable
fun PhoneNumberPrivacySettingsScreen(
  state: PhoneNumberPrivacySettingsState,
  onEvent: (PhoneNumberPrivacySettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences_app_protection__phone_number),
    onNavigationClick = { onEvent(PhoneNumberPrivacySettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    snackbarHost = {
      Snackbars.Host(
        message = state.snackbar.message(),
        onDismissed = { onEvent(PhoneNumberPrivacySettingsEvent.SnackbarDismissed) }
      )
    },
    modifier = modifier
  ) { paddingValues ->
    AppSettingsLazyColumn(
      scaffoldPadding = paddingValues,
      modifier = Modifier.testTag(PhoneNumberPrivacySettingsTestTags.SCROLLER)
    ) {
      item {
        Texts.SectionHeader(text = stringResource(R.string.PhoneNumberPrivacySettingsFragment_who_can_see_my_number_heading))
      }

      item {
        Rows.RadioRow(
          selected = state.phoneNumberSharing,
          text = stringResource(R.string.PhoneNumberPrivacy_everyone),
          modifier = Modifier
            .clickable { onEvent(PhoneNumberPrivacySettingsEvent.EveryoneCanSeeMyNumberClicked) }
            .testTag(PhoneNumberPrivacySettingsTestTags.ROW_EVERYONE_CAN_SEE)
        )
      }

      item {
        Rows.RadioRow(
          selected = !state.phoneNumberSharing,
          text = stringResource(R.string.PhoneNumberPrivacy_nobody),
          modifier = Modifier
            .clickable { onEvent(PhoneNumberPrivacySettingsEvent.NobodyCanSeeMyNumberClicked) }
            .testTag(PhoneNumberPrivacySettingsTestTags.ROW_NOBODY_CAN_SEE)
        )
      }

      item {
        Description(text = state.sharingDescription)
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(text = stringResource(R.string.PhoneNumberPrivacySettingsFragment_who_can_find_me_by_number_heading))
      }

      item {
        Rows.RadioRow(
          selected = state.discoverableByPhoneNumber,
          text = stringResource(R.string.PhoneNumberPrivacy_everyone),
          modifier = Modifier
            .clickable { onEvent(PhoneNumberPrivacySettingsEvent.EveryoneCanFindMeByNumberClicked) }
            .testTag(PhoneNumberPrivacySettingsTestTags.ROW_EVERYONE_CAN_FIND)
        )
      }

      item {
        Rows.RadioRow(
          enabled = !state.phoneNumberSharing,
          selected = !state.discoverableByPhoneNumber,
          text = stringResource(R.string.PhoneNumberPrivacy_nobody),
          modifier = Modifier
            .clickable { onEvent(PhoneNumberPrivacySettingsEvent.NobodyCanFindMeByNumberClicked) }
            .testTag(PhoneNumberPrivacySettingsTestTags.ROW_NOBODY_CAN_FIND)
        )
      }

      item {
        Description(text = state.discoveryDescription)
      }
    }

    when (state.dialog) {
      PhoneNumberPrivacySettingsState.Dialog.None -> Unit
      PhoneNumberPrivacySettingsState.Dialog.ConfirmNotDiscoverable -> {
        Dialogs.SimpleAlertDialog(
          title = stringResource(R.string.PhoneNumberPrivacySettingsFragment__nobody_can_find_me_warning_title),
          body = stringResource(R.string.PhoneNumberPrivacySettingsFragment__nobody_can_find_me_warning_message),
          confirm = stringResource(android.R.string.ok),
          dismiss = stringResource(R.string.PhoneNumberPrivacySettingsFragment__cancel),
          onConfirm = { onEvent(PhoneNumberPrivacySettingsEvent.NobodyCanFindMeByNumberConfirmed) },
          onDeny = { onEvent(PhoneNumberPrivacySettingsEvent.DialogDismissed) },
          onDismissRequest = { onEvent(PhoneNumberPrivacySettingsEvent.DialogDismissed) }
        )
      }
    }
  }
}

@Composable
private fun PhoneNumberPrivacySettingsState.Snackbar.message(): String? {
  return when (this) {
    PhoneNumberPrivacySettingsState.Snackbar.None -> null
    PhoneNumberPrivacySettingsState.Snackbar.HideNumberFirst -> stringResource(R.string.PhoneNumberPrivacySettingsFragment__to_change_this_setting)
  }
}

@Composable
private fun Description(@StringRes text: Int) {
  Text(
    text = stringResource(text),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
      .horizontalGutters()
      .padding(vertical = 16.dp)
  )
}

private val PhoneNumberPrivacySettingsState.sharingDescription: Int
  @StringRes get() = when {
    phoneNumberSharing -> R.string.PhoneNumberPrivacySettingsFragment_sharing_on_description
    discoverableByPhoneNumber -> R.string.PhoneNumberPrivacySettingsFragment_sharing_off_discovery_on_description
    else -> R.string.PhoneNumberPrivacySettingsFragment_sharing_off_discovery_off_description
  }

private val PhoneNumberPrivacySettingsState.discoveryDescription: Int
  @StringRes get() = if (discoverableByPhoneNumber) {
    R.string.PhoneNumberPrivacySettingsFragment_discovery_on_description
  } else {
    R.string.PhoneNumberPrivacySettingsFragment_discovery_off_description
  }

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PhoneNumberPrivacySettingsScreenSharingPreview() {
  PhoneNumberPrivacySettingsScreen(
    state = PhoneNumberPrivacySettingsState(phoneNumberSharing = true, discoverableByPhoneNumber = true),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PhoneNumberPrivacySettingsScreenDiscoverablePreview() {
  PhoneNumberPrivacySettingsScreen(
    state = PhoneNumberPrivacySettingsState(phoneNumberSharing = false, discoverableByPhoneNumber = true),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PhoneNumberPrivacySettingsScreenNotDiscoverablePreview() {
  PhoneNumberPrivacySettingsScreen(
    state = PhoneNumberPrivacySettingsState(phoneNumberSharing = false, discoverableByPhoneNumber = false),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PhoneNumberPrivacySettingsScreenConfirmPreview() {
  PhoneNumberPrivacySettingsScreen(
    state = PhoneNumberPrivacySettingsState(dialog = PhoneNumberPrivacySettingsState.Dialog.ConfirmNotDiscoverable),
    onEvent = {}
  )
}
