/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.discoverability

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.registration.R
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.test.TestTags

@Composable
fun PhoneNumberDiscoverabilityScreen(
  state: PhoneNumberDiscoverabilityState,
  onEvent: (PhoneNumberDiscoverabilityScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  RegistrationScaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.PHONE_NUMBER_DISCOVERABILITY_SCREEN),
    topBar = {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = { onEvent(PhoneNumberDiscoverabilityScreenEvents.BackClicked) },
          modifier = Modifier.testTag(TestTags.PHONE_NUMBER_DISCOVERABILITY_BACK_BUTTON)
        ) {
          Icon(
            imageVector = SignalIcons.ArrowStart.imageVector,
            contentDescription = stringResource(R.string.PhoneNumberDiscoverabilityScreen__back)
          )
        }
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        Text(
          text = stringResource(R.string.WhoCanSeeMyPhoneNumberFragment__who_can_find_me_by_number),
          style = MaterialTheme.typography.titleLarge
        )
      }
    },
    content = {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 8.dp)
      ) {
        DiscoverabilityOption(
          label = stringResource(R.string.PhoneNumberPrivacy_everyone),
          selected = state.discoverable,
          onClick = { onEvent(PhoneNumberDiscoverabilityScreenEvents.EveryoneSelected) },
          modifier = Modifier.testTag(TestTags.PHONE_NUMBER_DISCOVERABILITY_EVERYONE_OPTION)
        )

        DiscoverabilityOption(
          label = stringResource(R.string.PhoneNumberPrivacy_nobody),
          selected = !state.discoverable,
          onClick = { onEvent(PhoneNumberDiscoverabilityScreenEvents.NobodySelected) },
          modifier = Modifier.testTag(TestTags.PHONE_NUMBER_DISCOVERABILITY_NOBODY_OPTION)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = stringResource(
            if (state.discoverable) {
              R.string.WhoCanSeeMyPhoneNumberFragment__anyone_who_has_your
            } else {
              R.string.WhoCanSeeMyPhoneNumberFragment__nobody_will_be_able
            }
          ),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp)
        )
      }
    },
    footer = {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
      ) {
        Buttons.LargeTonal(
          onClick = { onEvent(PhoneNumberDiscoverabilityScreenEvents.SaveClicked) },
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 320.dp)
            .testTag(TestTags.PHONE_NUMBER_DISCOVERABILITY_SAVE_BUTTON)
        ) {
          Text(stringResource(R.string.PhoneNumberDiscoverabilityScreen__save))
        }
      }
    }
  )

  if (state.showNobodyConfirmation) {
    AlertDialog(
      onDismissRequest = { onEvent(PhoneNumberDiscoverabilityScreenEvents.NobodyDismissed) },
      title = { Text(stringResource(R.string.PhoneNumberPrivacySettingsFragment__nobody_can_find_me_warning_title)) },
      text = { Text(stringResource(R.string.PhoneNumberPrivacySettingsFragment__nobody_can_find_me_warning_message)) },
      dismissButton = {
        TextButton(onClick = { onEvent(PhoneNumberDiscoverabilityScreenEvents.NobodyDismissed) }) {
          Text(stringResource(R.string.PhoneNumberPrivacySettingsFragment__cancel))
        }
      },
      confirmButton = {
        TextButton(onClick = { onEvent(PhoneNumberDiscoverabilityScreenEvents.NobodyConfirmed) }) {
          Text(stringResource(android.R.string.ok))
        }
      }
    )
  }
}

@Composable
private fun DiscoverabilityOption(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Start
  ) {
    RadioButton(selected = selected, onClick = onClick)
    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
    Text(text = label, style = MaterialTheme.typography.bodyLarge)
  }
}

@AllDevicePreviews
@Composable
private fun PhoneNumberDiscoverabilityScreenEveryonePreview() {
  Previews.Preview {
    PhoneNumberDiscoverabilityScreen(
      state = PhoneNumberDiscoverabilityState(discoverable = true),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun PhoneNumberDiscoverabilityScreenNobodyPreview() {
  Previews.Preview {
    PhoneNumberDiscoverabilityScreen(
      state = PhoneNumberDiscoverabilityState(discoverable = false),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun PhoneNumberDiscoverabilityScreenConfirmPreview() {
  Previews.Preview {
    PhoneNumberDiscoverabilityScreen(
      state = PhoneNumberDiscoverabilityState(discoverable = true, showNobodyConfirmation = true),
      onEvent = {}
    )
  }
}
