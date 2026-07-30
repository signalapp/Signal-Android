/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.remotebackuprestore

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.KeepScreenOnEffect
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.screens.shared.ContactSupportDialog
import org.signal.registration.screens.shared.RestoreProgressDialog
import org.signal.registration.test.TestTags
import java.util.Date

@Composable
fun RemoteRestoreScreen(
  state: RemoteBackupRestoreState,
  onEvent: (RemoteBackupRestoreScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  if (state.restoreState == RemoteBackupRestoreState.RestoreState.InProgress) {
    KeepScreenOnEffect()
  }

  when (state.loadState) {
    RemoteBackupRestoreState.LoadState.Loading -> {
      Dialogs.IndeterminateProgressDialog(
        message = stringResource(R.string.RemoteRestoreScreen__fetching_backup_details)
      )
    }

    RemoteBackupRestoreState.LoadState.Loaded -> {
      when (val layoutParams = RegistrationScaffold.rememberLayoutParams()) {
        is RegistrationScaffold.Params.OnePane -> OnePaneLayout(layoutParams, state, onEvent, modifier)
        is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(layoutParams, state, onEvent, modifier)
      }
    }

    RemoteBackupRestoreState.LoadState.NotFound -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.RemoteRestoreScreen__backup_not_found),
        body = stringResource(R.string.RemoteRestoreScreen__no_backup_was_found),
        confirm = stringResource(android.R.string.ok),
        onConfirm = { onEvent(RemoteBackupRestoreScreenEvents.Cancel) },
        onDismiss = { onEvent(RemoteBackupRestoreScreenEvents.Cancel) }
      )
    }

    RemoteBackupRestoreState.LoadState.Failure -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.RemoteRestoreScreen__cant_restore_backup),
        body = stringResource(R.string.RemoteRestoreScreen__your_backup_cant_be_restored_right_now),
        confirm = stringResource(R.string.RemoteRestoreScreen__try_again),
        dismiss = stringResource(android.R.string.cancel),
        onConfirm = { onEvent(RemoteBackupRestoreScreenEvents.Retry) },
        onDeny = { onEvent(RemoteBackupRestoreScreenEvents.Cancel) },
        onDismissRequest = {}
      )
    }
  }
}

@Composable
private fun OnePaneLayout(
  params: RegistrationScaffold.Params.OnePane,
  state: RemoteBackupRestoreState,
  onEvent: (RemoteBackupRestoreScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()

  OnePaneRegistrationScaffold(
    modifier = modifier.testTag(TestTags.REMOTE_BACKUP_RESTORE_SCREEN),
    params = params,
    content = { paddingValues ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .padding(paddingValues)
      ) {
        BackupInfoContent(state = state)
      }

      RestoreStateDialogs(state = state, onEvent = onEvent)
    },
    footer = {
      RegistrationScaffold.FooterSurface(
        isElevated = scrollState.canScrollForward
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
        ) {
          RestoreButton(onEvent, Modifier.fillMaxWidth())
          CancelButton(onEvent, Modifier.fillMaxWidth())
        }
      }
    }
  )
}

@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  state: RemoteBackupRestoreState,
  onEvent: (RemoteBackupRestoreScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()

  TwoPaneRegistrationScaffold(
    modifier = modifier.testTag(TestTags.REMOTE_BACKUP_RESTORE_SCREEN),
    params = params,
    firstPane = { paddingValues ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .verticalScroll(firstPaneScrollState)
          .padding(paddingValues)
      ) {
        BackupInfoHeading(twoPane = true)
      }
    },
    secondPane = { paddingValues ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .verticalScroll(secondPaneScrollState)
          .padding(paddingValues)
      ) {
        BackupInfoDetails(state = state, twoPane = true)
      }

      RestoreStateDialogs(state = state, onEvent = onEvent)
    },
    footer = {
      RegistrationScaffold.FooterSurface(
        isElevated = firstPaneScrollState.canScrollForward || secondPaneScrollState.canScrollForward
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
          horizontalArrangement = Arrangement.End
        ) {
          CancelButton(onEvent, Modifier)
          Spacer(modifier = Modifier.size(8.dp))
          RestoreButton(onEvent, Modifier)
        }
      }
    }
  )
}

@Composable
private fun BackupInfoContent(
  state: RemoteBackupRestoreState
) {
  BackupInfoHeading()
  BackupInfoDetails(
    state = state,
    modifier = Modifier.padding(top = 16.dp)
  )
}

@Composable
private fun BackupInfoHeading(twoPane: Boolean = false) {
  Icon(
    imageVector = SignalIcons.Backup.imageVector,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.primary,
    modifier = Modifier
      .size(64.dp)
      .background(color = SignalTheme.colors.colorSurface2, shape = CircleShape)
      .padding(12.dp)
  )

  Spacer(modifier = Modifier.height(16.dp))

  Text(
    text = stringResource(R.string.RemoteRestoreScreen__restore_from_backup),
    style = if (twoPane) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )
}

@Composable
private fun BackupInfoDetails(state: RemoteBackupRestoreState, modifier: Modifier = Modifier, twoPane: Boolean = false) {
  Column(
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = modifier
  ) {
    if (state.backupTime > 0) {
      val context = LocalContext.current
      val (dateStr, timeStr) = remember(context, state.backupTime) {
        val date = Date(state.backupTime)
        val dateFormatted = DateFormat.getMediumDateFormat(context).format(date)
        val timeFormatted = DateFormat.getTimeFormat(context).format(date)
        dateFormatted to timeFormatted
      }

      Text(
        text = stringResource(R.string.RemoteRestoreScreen__your_last_backup_was_made_on_s_at_s, dateStr, timeStr),
        style = if (twoPane) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal) else MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )
    }

    Text(
      text = stringResource(R.string.RemoteRestoreScreen__your_media_will_restore_in_the_background),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@Composable
private fun RestoreButton(
  onEvent: (RemoteBackupRestoreScreenEvents) -> Unit,
  modifier: Modifier
) {
  Buttons.LargeTonal(
    onClick = { onEvent(RemoteBackupRestoreScreenEvents.BackupRestoreBackup) },
    modifier = modifier.testTag(TestTags.REMOTE_BACKUP_RESTORE_RESTORE_BUTTON)
  ) {
    Text(text = stringResource(R.string.RemoteRestoreScreen__restore_backup))
  }
}

@Composable
private fun CancelButton(
  onEvent: (RemoteBackupRestoreScreenEvents) -> Unit,
  modifier: Modifier
) {
  TextButton(
    onClick = { onEvent(RemoteBackupRestoreScreenEvents.Cancel) },
    modifier = modifier.testTag(TestTags.REMOTE_BACKUP_RESTORE_CANCEL_BUTTON)
  ) {
    Text(text = stringResource(android.R.string.cancel))
  }
}

@Composable
private fun RestoreStateDialogs(
  state: RemoteBackupRestoreState,
  onEvent: (RemoteBackupRestoreScreenEvents) -> Unit
) {
  if (state.showContactSupportDialog) {
    ContactSupportDialog(
      subject = R.string.RemoteRestoreScreen__contact_support_email_subject,
      filter = R.string.RemoteRestoreScreen__contact_support_email_filter,
      onDismiss = { onEvent(RemoteBackupRestoreScreenEvents.DismissContactSupport) }
    )
  }

  when (state.restoreState) {
    RemoteBackupRestoreState.RestoreState.None -> Unit
    RemoteBackupRestoreState.RestoreState.InProgress -> {
      RestoreProgressDialog(restoreProgress = state.restoreProgress)
    }

    RemoteBackupRestoreState.RestoreState.Restored -> Unit
    RemoteBackupRestoreState.RestoreState.NetworkFailure -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.RemoteRestoreScreen__couldnt_finish_restore),
        body = stringResource(R.string.RemoteRestoreScreen__error_connecting),
        confirm = stringResource(android.R.string.ok),
        onConfirm = { onEvent(RemoteBackupRestoreScreenEvents.DismissError) },
        onDismiss = { onEvent(RemoteBackupRestoreScreenEvents.DismissError) }
      )
    }

    RemoteBackupRestoreState.RestoreState.InvalidBackupVersion -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.RemoteRestoreScreen__couldnt_restore_this_backup),
        body = stringResource(R.string.RemoteRestoreScreen__update_latest),
        confirm = stringResource(R.string.RemoteRestoreScreen__update_signal),
        dismiss = stringResource(R.string.RemoteRestoreScreen__not_now),
        onConfirm = { onEvent(RemoteBackupRestoreScreenEvents.DismissError) },
        onDismiss = { onEvent(RemoteBackupRestoreScreenEvents.DismissError) }
      )
    }

    RemoteBackupRestoreState.RestoreState.PermanentSvrBFailure -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.RemoteRestoreScreen__cant_restore_this_backup),
        body = stringResource(R.string.RemoteRestoreScreen__your_backup_is_not_recoverable),
        confirm = stringResource(R.string.RemoteRestoreScreen__contact_support),
        dismiss = stringResource(android.R.string.ok),
        onConfirm = {
          onEvent(RemoteBackupRestoreScreenEvents.ContactSupport)
          onEvent(RemoteBackupRestoreScreenEvents.DismissError)
        },
        onDismiss = { onEvent(RemoteBackupRestoreScreenEvents.DismissError) }
      )
    }

    RemoteBackupRestoreState.RestoreState.Failed -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.RemoteRestoreScreen__couldnt_finish_restore),
        body = stringResource(R.string.RemoteRestoreScreen__error_occurred),
        confirm = stringResource(android.R.string.ok),
        onConfirm = { onEvent(RemoteBackupRestoreScreenEvents.DismissError) },
        onDismiss = { onEvent(RemoteBackupRestoreScreenEvents.DismissError) }
      )
    }
  }
}

@AllDevicePreviews
@Composable
private fun RemoteRestoreScreenLoadedPreview() {
  Previews.Preview {
    RemoteRestoreScreen(
      state = RemoteBackupRestoreState(
        aep = AccountEntropyPool("0000000000000000000000000000000000000000000000000000000000000000"),
        loadState = RemoteBackupRestoreState.LoadState.Loaded,
        backupTime = System.currentTimeMillis(),
        backupSize = 1234567
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun RemoteRestoreScreenLoadedNoTimePreview() {
  Previews.Preview {
    RemoteRestoreScreen(
      state = RemoteBackupRestoreState(
        aep = AccountEntropyPool("0000000000000000000000000000000000000000000000000000000000000000"),
        loadState = RemoteBackupRestoreState.LoadState.Loaded,
        backupSize = 1234567
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun RemoteRestoreScreenLoadingPreview() {
  Previews.Preview {
    RemoteRestoreScreen(
      state = RemoteBackupRestoreState(
        aep = AccountEntropyPool("0000000000000000000000000000000000000000000000000000000000000000"),
        loadState = RemoteBackupRestoreState.LoadState.Loading
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun RemoteRestoreScreenNotFoundPreview() {
  Previews.Preview {
    RemoteRestoreScreen(
      state = RemoteBackupRestoreState(
        aep = AccountEntropyPool("0000000000000000000000000000000000000000000000000000000000000000"),
        loadState = RemoteBackupRestoreState.LoadState.NotFound
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun RemoteRestoreScreenFailurePreview() {
  Previews.Preview {
    RemoteRestoreScreen(
      state = RemoteBackupRestoreState(
        aep = AccountEntropyPool("0000000000000000000000000000000000000000000000000000000000000000"),
        loadState = RemoteBackupRestoreState.LoadState.Failure
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun RemoteRestoreScreenRestoringPreview() {
  Previews.Preview {
    RemoteRestoreScreen(
      state = RemoteBackupRestoreState(
        aep = AccountEntropyPool("0000000000000000000000000000000000000000000000000000000000000000"),
        loadState = RemoteBackupRestoreState.LoadState.Loaded,
        backupTime = System.currentTimeMillis(),
        backupSize = 1234567,
        restoreState = RemoteBackupRestoreState.RestoreState.InProgress
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun RemoteRestoreScreenNetworkFailurePreview() {
  Previews.Preview {
    RemoteRestoreScreen(
      state = RemoteBackupRestoreState(
        aep = AccountEntropyPool("0000000000000000000000000000000000000000000000000000000000000000"),
        loadState = RemoteBackupRestoreState.LoadState.Loaded,
        backupTime = System.currentTimeMillis(),
        restoreState = RemoteBackupRestoreState.RestoreState.NetworkFailure
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun RemoteRestoreScreenRestoreFailedPreview() {
  Previews.Preview {
    RemoteRestoreScreen(
      state = RemoteBackupRestoreState(
        aep = AccountEntropyPool("0000000000000000000000000000000000000000000000000000000000000000"),
        loadState = RemoteBackupRestoreState.LoadState.Loaded,
        backupTime = System.currentTimeMillis(),
        restoreState = RemoteBackupRestoreState.RestoreState.Failed
      ),
      onEvent = {}
    )
  }
}
