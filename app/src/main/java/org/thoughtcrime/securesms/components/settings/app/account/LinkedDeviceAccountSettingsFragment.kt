/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.ServiceUtil
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.account.LinkedDeviceAccountSettingsState.OneTimeEvent
import org.thoughtcrime.securesms.compose.rememberStatusBarColorNestedScrollModifier
import org.thoughtcrime.securesms.dependencies.AppDependencies

/**
 * Account settings shown when the current device is a linked (non-primary) device. Account
 * management lives on the primary device, so this screen only surfaces an informational callout
 * and the ability to delete the Signal data stored on this device.
 */
class LinkedDeviceAccountSettingsFragment : ComposeFragment() {

  private val viewModel: LinkedDeviceAccountSettingsViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.oneTimeEvent) {
      viewModel.onEvent(LinkedDeviceAccountSettingsEvent.ConsumeOneTimeEvent)
      when (state.oneTimeEvent) {
        OneTimeEvent.OpenLearnMore -> LinkedDeviceAccountLearnMoreBottomSheet.show(childFragmentManager)
        OneTimeEvent.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
        OneTimeEvent.WipeData -> {
          if (!ServiceUtil.getActivityManager(AppDependencies.application).clearApplicationUserData()) {
            viewModel.onEvent(LinkedDeviceAccountSettingsEvent.DataWipeFailed)
          }
        }
        OneTimeEvent.DeleteFailed -> Toast.makeText(requireContext(), R.string.preferences_account_delete_all_data_failed, Toast.LENGTH_LONG).show()
        null -> Unit
      }
    }

    LinkedDeviceAccountSettingsScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }
}

@Composable
private fun LinkedDeviceAccountSettingsScreen(
  state: LinkedDeviceAccountSettingsState,
  onEvent: (LinkedDeviceAccountSettingsEvent) -> Unit
) {
  Scaffolds.Settings(
    title = stringResource(R.string.AccountSettingsFragment__account),
    onNavigationClick = { onEvent(LinkedDeviceAccountSettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .then(rememberStatusBarColorNestedScrollModifier())
    ) {
      item {
        LinkedDeviceCallout(onLearnMoreClick = { onEvent(LinkedDeviceAccountSettingsEvent.LearnMoreClicked) })
      }

      item {
        Dividers.Default()
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.LinkedDeviceAccountSettingsFragment__delete_app_data),
          label = stringResource(R.string.LinkedDeviceAccountSettingsFragment__this_will_remove_all_data),
          onClick = { onEvent(LinkedDeviceAccountSettingsEvent.DeleteAppDataClicked) }
        )
      }
    }
  }

  if (state.showDeleteConfirmationDialog) {
    DeleteAppDataConfirmationDialog(
      onConfirm = { onEvent(LinkedDeviceAccountSettingsEvent.DeleteConfirmed) },
      onDismiss = { onEvent(LinkedDeviceAccountSettingsEvent.DeleteDismissed) }
    )
  }

  if (state.deleting) {
    Dialogs.IndeterminateProgressDialog()
  }
}

@Composable
private fun LinkedDeviceCallout(
  onLearnMoreClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .padding(horizontal = 24.dp, vertical = 12.dp)
      .fillMaxWidth()
      .background(color = SignalTheme.colors.colorSurface2, shape = RoundedCornerShape(12.dp))
      .padding(20.dp)
  ) {
    Icon(
      imageVector = SignalIcons.Devices.imageVector,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurface
    )

    Spacer(modifier = Modifier.width(24.dp))

    Column {
      Text(
        text = stringResource(R.string.LinkedDeviceAccountSettingsFragment__this_is_a_linked_device),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = stringResource(R.string.LinkedDeviceAccountSettingsFragment__to_manage_your_account_settings),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(12.dp))

      Buttons.Small(
        onClick = onLearnMoreClick,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
      ) {
        Text(text = stringResource(R.string.LearnMoreTextView_learn_more))
      }
    }
  }
}

@Composable
private fun DeleteAppDataConfirmationDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.LinkedDeviceAccountSettingsFragment__delete_app_data_question),
    body = stringResource(R.string.LinkedDeviceAccountSettingsFragment__this_will_delete_all_data_and_messages),
    confirm = stringResource(R.string.delete),
    confirmColor = MaterialTheme.colorScheme.error,
    onConfirm = onConfirm,
    dismiss = stringResource(android.R.string.cancel),
    onDismiss = onDismiss
  )
}

@DayNightPreviews
@Composable
private fun LinkedDeviceAccountSettingsScreenPreview() {
  Previews.Preview {
    LinkedDeviceAccountSettingsScreen(
      state = LinkedDeviceAccountSettingsState(),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun LinkedDeviceAccountSettingsScreenDeleteConfirmationPreview() {
  Previews.Preview {
    LinkedDeviceAccountSettingsScreen(
      state = LinkedDeviceAccountSettingsState(showDeleteConfirmationDialog = true),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun LinkedDeviceAccountSettingsScreenDeletingPreview() {
  Previews.Preview {
    LinkedDeviceAccountSettingsScreen(
      state = LinkedDeviceAccountSettingsState(deleting = true),
      onEvent = {}
    )
  }
}
